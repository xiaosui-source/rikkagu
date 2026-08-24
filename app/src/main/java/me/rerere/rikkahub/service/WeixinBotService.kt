/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.weixin.WeixinBotClient
import me.rerere.rikkahub.data.weixin.WeixinMessageType
import me.rerere.rikkahub.data.weixin.extractInboundText
import org.koin.core.component.inject
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * 微信 Bot 后台长轮询服务 (支持多个 bot).
 *
 * 监听 settings.wechatBotSettings 列表, 每个 enabled 且已登录的 bot 启动独立长轮询协程
 * (各自维护 getUpdates 游标). 设置里新增/删除/禁用 bot 时自动增删轮询; 全部失效后自动停止.
 * 某个 bot token 过期只禁用它自己, 不影响其他 bot.
 */
class WeixinBotService : Service(), org.koin.core.component.KoinComponent {
    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val chatService: ChatService by inject()
    private val client: WeixinBotClient by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var managerJob: Job? = null
    private val botJobs = ConcurrentHashMap<String, Job>()

    @Volatile
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        stopping = false
        if (managerJob?.isActive != true) {
            managerJob = scope.launch { runManager() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private suspend fun runManager() {
        settingsStore.settingsFlow
            .map { settings ->
                settings.wechatBotSettings.filter { it.enabled && it.botToken.isNotBlank() }
            }
            .distinctUntilChanged()
            .collect { activeBots ->
                val activeIds = activeBots.map { it.id }.toSet()

                botJobs.keys.filter { !activeIds.contains(it) }.forEach { id ->
                    Log.i(TAG, "bot $id disabled/removed, stopping its poll")
                    botJobs.remove(id)?.cancel()
                }

                activeBots.forEach { bot ->
                    if (!botJobs.containsKey(bot.id)) {
                        Log.i(TAG, "starting poll for bot ${bot.id}")
                        botJobs[bot.id] = scope.launch { runPollLoop(bot.id) }
                    }
                }

                if (activeBots.isEmpty() && !stopping) {
                    stopping = true
                    Log.i(TAG, "no active bot, stopping service")
                    stopSelf()
                }
            }
    }

    private suspend fun runPollLoop(botId: String) {
        var getUpdatesBuf = ""
        Log.i(TAG, "poll loop started for bot $botId")
        while (true) {
            val settings = settingsStore.settingsFlow.first()
            val bot = settings.wechatBotSettings.find { it.id == botId }
            if (bot == null || !bot.enabled || bot.botToken.isBlank()) {
                Log.w(TAG, "bot $botId disabled/removed, poll loop exits")
                return
            }
            try {
                val result = client.getUpdates(
                    token = bot.botToken,
                    baseUrl = bot.baseUrl,
                    getUpdatesBuf = getUpdatesBuf,
                )
                getUpdatesBuf = result.getUpdatesBuf

                for (msg in result.msgs) {
                    val msgType = msg["message_type"]?.jsonPrimitive?.content?.toIntOrNull()
                    if (msgType != WeixinMessageType.INBOUND) continue

                    val fromUserId = msg["from_user_id"]?.jsonPrimitive?.content
                    val contextToken = msg["context_token"]?.jsonPrimitive?.content
                    if (fromUserId.isNullOrBlank() || contextToken.isNullOrBlank()) continue

                    val text = extractInboundText(msg.jsonObject)
                    Log.i(TAG, "bot=$botId inbound from=$fromUserId text=${text.take(80)}")

                    val reply = handleInboundMessage(text, bot.assistantId)
                    client.sendTextMessage(
                        token = bot.botToken,
                        baseUrl = bot.baseUrl,
                        toUserId = fromUserId,
                        text = reply,
                        contextToken = contextToken,
                    )
                    Log.i(TAG, "bot=$botId replied to=$fromUserId len=${reply.length}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                Log.e(TAG, "bot=$botId poll error: $msg", e)
                if (msg.contains("session timeout", ignoreCase = true) ||
                    msg.contains("-14") || msg.contains("401")
                ) {
                    Log.w(TAG, "bot=$botId token expired, disabling this bot only")
                    notifyTokenExpired()
                    settingsStore.update { s ->
                        s.copy(
                            wechatBotSettings = s.wechatBotSettings.map { b ->
                                if (b.id == botId) b.copy(enabled = false) else b
                            }
                        )
                    }
                    return
                }
                delay(3000)
            }
        }
    }

    private suspend fun handleInboundMessage(text: String, assistantIdStr: String): String {
        val settings = settingsStore.settingsFlow.first()
        val assistant = if (assistantIdStr.isBlank()) {
            settings.getCurrentAssistant()
        } else {
            settings.assistants.find { it.id.toString() == assistantIdStr }
                ?: settings.getCurrentAssistant()
        }

        val recent = conversationRepository.getRecentConversations(assistant.id, limit = 1)
        val conversationId = recent.firstOrNull()?.id ?: Uuid.random()

        chatService.addConversationReference(conversationId)
        chatService.sendMessage(
            conversationId = conversationId,
            content = listOf(UIMessagePart.Text(text)),
            answer = true,
        )

        val success = withTimeoutOrNull(REPLY_TIMEOUT_MS) {
            chatService.generationDoneFlow.first { it == conversationId }
        }
        if (success == null) return "（思考超时, 请稍后再试）"

        val conversation = chatService.getConversationFlow(conversationId).value
        val lastAssistant = conversation.currentMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
        val replyText = lastAssistant?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("\n") { it.text }
            ?.trim()
        return replyText?.takeIf { it.isNotBlank() } ?: "（无回复）"
    }

    private fun startForegroundCompat() {
        val notification: Notification = NotificationCompat.Builder(this, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("微信 Bot 运行中")
            .setContentText("正在监听微信消息")
            .setSmallIcon(me.rerere.rikkahub.R.drawable.small_icon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        try {
            androidx.core.app.ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyTokenExpired() {
        try {
            val notification = NotificationCompat.Builder(this, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("微信 Bot 已断开")
                .setContentText("登录已过期, 请到设置页重新扫码登录")
                .setSmallIcon(me.rerere.rikkahub.R.drawable.small_icon)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(NOTIFICATION_ID + 1, notification)
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "WeixinBotService"
        private const val NOTIFICATION_ID = 20010
        private const val REPLY_TIMEOUT_MS = 120_000L

        fun start(context: Context) {
            val intent = Intent(context, WeixinBotService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (_: Exception) {
                try { context.startService(intent) } catch (_: Exception) {}
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, WeixinBotService::class.java))
            } catch (_: Exception) {}
        }
    }
}
