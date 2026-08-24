/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 本文件由 APK 反编译逆向还原（ScheduledMessageService：定时消息发送前台服务）
 */

package me.rerere.rikkahub.data.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import me.rerere.rikkahub.utils.NotificationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.NotificationUtil
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

/**
 * 定时消息发送前台服务.
 *
 * 由 [ScheduledMessageReceiver] 启动，从 Intent Extra 读取：
 *  - scheduled_conversation_id: 目标会话 ID
 *  - scheduled_message_text:   要发送的消息内容
 *  - scheduled_assistant_id:   可选的助手 ID
 */
class ScheduledMessageService : Service(), KoinComponent {

    private val chatService: ChatService by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val conversationIdStr = intent?.getStringExtra(EXTRA_CONVERSATION_ID)
        val messageText = intent?.getStringExtra(EXTRA_MESSAGE_TEXT)
        val assistantIdStr = intent?.getStringExtra(EXTRA_ASSISTANT_ID)

        if (conversationIdStr.isNullOrBlank() || messageText.isNullOrBlank()) {
            Log.w(TAG, "Missing conversation_id or message_text, stopping")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // 前台通知
        val notifConfig = NotificationConfig()
        notifConfig.title = "发送消息中..."
        notifConfig.content = messageText.take(50)
        notifConfig.smallIcon = R.drawable.small_icon
        notifConfig.ongoing = true
        val notification = NotificationUtil.buildNotification(
            context = this,
            channelId = "chat_completed",
            config = notifConfig
        ).build()
        startForeground(NOTIFICATION_ID, notification)

        scope.launch {
            try {
                val conversationId = runCatching { Uuid.parse(conversationIdStr) }.getOrNull()
                if (conversationId == null) {
                    Log.w(TAG, "Invalid conversation_id: $conversationIdStr")
                    stopSelf(startId)
                    return@launch
                }

                chatService.addConversationReference(conversationId)
                chatService.sendMessage(
                    conversationId = conversationId,
                    content = listOf(UIMessagePart.Text(messageText)),
                    answer = true,
                )

                // 等待生成完成（最多 5 分钟）
                withTimeoutOrNull(5 * 60 * 1000L) {
                    chatService.generationDoneFlow.first { it == conversationId }
                }

                NotificationUtil.notify(
                    context = this@ScheduledMessageService,
                    channelId = "chat_completed",
                    notificationId = 1002,
                    config = {
                        title = "消息已发送"
                        content = messageText.take(50)
                        smallIcon = R.drawable.small_icon
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "send scheduled message failed", e)
                NotificationUtil.notify(
                    context = this@ScheduledMessageService,
                    channelId = "chat_completed",
                    notificationId = 1003,
                    config = {
                        title = "消息发送失败"
                        content = e.message ?: "未知错误"
                        smallIcon = R.drawable.small_icon
                    }
                )
            } finally {
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScheduledMsgService"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_CONVERSATION_ID = "scheduled_conversation_id"
        const val EXTRA_MESSAGE_TEXT = "scheduled_message_text"
        const val EXTRA_ASSISTANT_ID = "scheduled_assistant_id"

        fun buildIntent(
            context: android.content.Context,
            conversationId: String,
            messageText: String,
            assistantId: String? = null,
        ): Intent = Intent(context, ScheduledMessageService::class.java).apply {
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_MESSAGE_TEXT, messageText)
            if (assistantId != null) putExtra(EXTRA_ASSISTANT_ID, assistantId)
        }
    }
}
