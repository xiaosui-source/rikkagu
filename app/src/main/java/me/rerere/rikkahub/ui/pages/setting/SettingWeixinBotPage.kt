/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.pages.setting

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.*
import me.rerere.hugeicons.stroke.MessageMultiple01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.WechatBotSetting
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.weixin.WeixinBotClient
import me.rerere.rikkahub.service.WeixinBotService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.RiskConfirmDialog
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

/**
 * 微信 Bot 设置页 (支持多个 bot).
 * 顶部 bot 列表 (添加/切换/删除), 每个 bot 独立扫码登录与启停.
 */
@Composable
fun SettingWeixinBotPage(vm: SettingVM = koinViewModel()) {
    val context = LocalContext.current
    val client: WeixinBotClient = koinInject()
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var bots by remember(settings) { mutableStateOf(settings.wechatBotSettings) }
    var selectedIndex by remember { mutableStateOf(0) }
    LaunchedEffect(settings) { bots = settings.wechatBotSettings }
    if (selectedIndex >= bots.size && bots.isNotEmpty()) {
        selectedIndex = bots.size - 1
    }

    var loginJob by remember { mutableStateOf<Job?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrContent by remember { mutableStateOf<String?>(null) }
    var loginStatus by remember { mutableStateOf("未登录") }
    var isLoggingIn by remember { mutableStateOf(false) }
    var showEnableRiskDialog by remember { mutableStateOf(false) }

    val selectedBot = bots.getOrNull(selectedIndex)

    fun switchTo(index: Int) {
        loginJob?.cancel()
        loginJob = null
        qrBitmap = null; qrContent = null; loginStatus = "未登录"; isLoggingIn = false
        selectedIndex = index
    }

    fun persist(newList: List<WechatBotSetting>) {
        bots = newList
        vm.updateSettings(settings.copy(wechatBotSettings = newList))
    }

    fun updateBot(index: Int, newSetting: WechatBotSetting) {
        persist(bots.toMutableList().also { it[index] = newSetting })
    }

    fun addBot() {
        persist(bots + WechatBotSetting(id = Uuid.random().toString()))
        switchTo(bots.size - 1)
    }

    fun deleteBot(index: Int) {
        if (bots[index].enabled) WeixinBotService.stop(context)
        persist(bots.toMutableList().also { it.removeAt(index) })
        switchTo((selectedIndex - 1).coerceAtLeast(0))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (showEnableRiskDialog && selectedBot != null) {
        RiskConfirmDialog(
            title = stringResource(R.string.risk_weixin_bot_title),
            message = stringResource(R.string.risk_weixin_bot_message),
            onConfirm = {
                showEnableRiskDialog = false
                updateBot(selectedIndex, selectedBot.copy(enabled = true))
                WeixinBotService.start(context)
            },
            onDismiss = { showEnableRiskDialog = false }
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("微信 Bot") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 机器人列表
            item {
                CardGroup(
                    title = { Text("机器人列表") },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bots.forEachIndexed { index, bot ->
                            FilterChip(
                                selected = index == selectedIndex,
                                onClick = { switchTo(index) },
                                label = {
                                    Text(
                                        buildString {
                                            append(if (bot.botId.isBlank()) "Bot ${index + 1}" else bot.botId)
                                            if (bot.enabled) append(" ●")
                                        }
                                    )
                                }
                            )
                        }
                        FilledTonalButton(onClick = { addBot() }) { Text("＋ 添加") }
                    }
                    if (bots.isEmpty()) {
                        item(
                            headlineContent = { Text("还没有微信机器人") },
                            supportingContent = { Text("点「＋ 添加」创建一个, 每个机器人 = 一个微信号通道") }
                        )
                    } else if (selectedBot != null) {
                        item(
                            headlineContent = { Text("当前机器人") },
                            supportingContent = {
                                Text(
                                    "ID: ${selectedBot.id.take(8)}…   " +
                                        (if (selectedBot.botToken.isNotBlank()) "已登录 (Bot: ${selectedBot.botId.ifBlank { "未知" }})" else "未登录")
                                )
                            },
                            trailingContent = {
                                TextButton(onClick = { deleteBot(selectedIndex) }) { Text("删除") }
                            }
                        )
                    }
                }
            }

            if (selectedBot == null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = HugeIcons.MessageMultiple01,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("点「＋ 添加」创建你的第一个微信机器人", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                return@LazyColumn
            }

            // 说明
            item {
                CardGroup(
                    title = { Text("说明") },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    item(
                        leadingContent = { Icon(imageVector = HugeIcons.MessageMultiple01, contentDescription = null) },
                        headlineContent = { Text("微信 Bot 是什么") },
                        supportingContent = { Text("把你的微信号变成 AI 入口: 别人(或你自己)给这个微信号发消息, 会由关联的助手回复. 支持添加多个机器人, 每个独立运行.") }
                    )
                }
            }

            // 扫码登录 (当前选中的 bot)
            item {
                CardGroup(
                    title = { Text("登录 (${if (selectedBot.botId.isBlank()) "Bot ${selectedIndex + 1}" else selectedBot.botId})") },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    item(
                        headlineContent = { Text("登录状态") },
                        supportingContent = {
                            Text(
                                if (selectedBot.botToken.isNotBlank()) {
                                    "已登录 (Bot: ${selectedBot.botId.ifBlank { "未知" }})"
                                } else {
                                    "未登录"
                                }
                            )
                        },
                        trailingContent = {
                            FilledTonalButton(
                                enabled = !isLoggingIn,
                                onClick = {
                                    val botIndex = selectedIndex
                                    val bot = selectedBot
                                    loginJob = scope.launch {
                                        isLoggingIn = true
                                        loginStatus = "获取二维码..."
                                        try {
                                            val qr = client.getQrcode(bot.baseUrl)
                                            qrContent = qr.qrcodeImgContent
                                            loginStatus = "请用微信扫码"
                                            qrBitmap = withContext(Dispatchers.Default) {
                                                try {
                                                    renderQrCode(qr.qrcodeImgContent, 480)
                                                } catch (re: Exception) {
                                                    loginStatus = "二维码渲染失败: ${re.message}"
                                                    null
                                                }
                                            }
                                            val deadline = System.currentTimeMillis() + 5 * 60_000
                                            var currentQrcode = qr.qrcode
                                            var refreshCount = 0
                                            var confirmed = false
                                            while (System.currentTimeMillis() < deadline && !confirmed) {
                                                val st = client.getQrcodeStatus(currentQrcode, bot.baseUrl)
                                                when (st.status) {
                                                    "confirmed" -> {
                                                        updateBot(
                                                            botIndex,
                                                            selectedBot.copy(
                                                                botToken = st.botToken ?: "",
                                                                baseUrl = st.baseUrl ?: selectedBot.baseUrl,
                                                                botId = st.botId ?: "",
                                                            )
                                                        )
                                                        loginStatus = "登录成功!"
                                                        confirmed = true
                                                    }
                                                    "scaned" -> loginStatus = "已扫码, 请在微信确认..."
                                                    "expired" -> {
                                                        refreshCount++
                                                        if (refreshCount > 3) { loginStatus = "二维码多次过期, 请重试"; break }
                                                        loginStatus = "二维码过期, 刷新中..."
                                                        val newQr = client.getQrcode(selectedBot.baseUrl)
                                                        currentQrcode = newQr.qrcode
                                                        qrBitmap = withContext(Dispatchers.Default) {
                                                            try { renderQrCode(newQr.qrcodeImgContent, 480) }
                                                            catch (re: Exception) { loginStatus = "二维码渲染失败: ${re.message}"; null }
                                                        }
                                                    }
                                                    else -> loginStatus = "等待扫码..."
                                                }
                                                delay(1000)
                                            }
                                            if (!confirmed && loginStatus == "等待扫码...") loginStatus = "登录超时"
                                            qrBitmap = null
                                        } catch (e: Exception) {
                                            loginStatus = "登录失败: ${e.message ?: e::class.simpleName}"
                                        } finally {
                                            isLoggingIn = false
                                        }
                                    }
                                }
                            ) {
                                Text(if (isLoggingIn) "登录中..." else if (selectedBot.botToken.isNotBlank()) "重新登录" else "扫码登录")
                            }
                        }
                    )
                    if (loginStatus.isNotBlank() && loginStatus != "未登录") {
                        item(headlineContent = { Text("状态") }, supportingContent = { Text(loginStatus) })
                    }
                    if (selectedBot.botToken.isNotBlank()) {
                        item(
                            headlineContent = { Text("退出登录") },
                            trailingContent = {
                                FilledTonalButton(onClick = {
                                    updateBot(selectedIndex, selectedBot.copy(botToken = "", botId = ""))
                                    loginStatus = "已退出"
                                }) { Text("退出") }
                            }
                        )
                    }
                }
            }

            // 二维码区
            if (qrContent != null || qrBitmap != null || (loginStatus.isNotBlank() && loginStatus != "未登录" && loginStatus != "已退出")) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = loginStatus,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        qrBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "微信登录二维码",
                                modifier = Modifier
                                    .size(240.dp)
                                    .background(ComposeColor.White)
                                    .padding(12.dp)
                            )
                        }
                        qrContent?.let { url ->
                            Text("如果二维码不显示, 点按钮用浏览器打开:", style = MaterialTheme.typography.bodySmall)
                            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            FilledTonalButton(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }) { Text("用浏览器打开二维码链接") }
                        }
                    }
                }
            }

            // 运行
            item {
                CardGroup(
                    title = { Text("运行") },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    item(
                        headlineContent = { Text("启用此机器人") },
                        supportingContent = { Text("开启后为此机器人启动后台长轮询. 需先扫码登录.") },
                        trailingContent = {
                            Switch(
                                checked = selectedBot.enabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        showEnableRiskDialog = true
                                    } else {
                                        updateBot(selectedIndex, selectedBot.copy(enabled = false))
                                    }
                                }
                            )
                        }
                    )
                    item(
                        headlineContent = { Text("关联助手") },
                        supportingContent = {
                            Text("固定使用当前助手: ${settings.getCurrentAssistant().name.ifBlank { "未命名" }}")
                        }
                    )
                    if (selectedBot.enabled && selectedBot.botToken.isBlank()) {
                        item(
                            headlineContent = { Text("⚠ 尚未登录") },
                            supportingContent = { Text("服务需要登录后才能收发消息, 请先扫码登录") }
                        )
                    }
                }
            }
        }
    }
}

/** 用 ZXing 把字符串渲染成二维码 Bitmap. */
private fun renderQrCode(content: String, sizePx: Int): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
    return bmp
}
