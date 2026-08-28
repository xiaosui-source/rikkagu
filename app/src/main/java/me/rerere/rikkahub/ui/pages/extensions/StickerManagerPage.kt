/*
 * 灵犀 Lingxi
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 表情包渲染器管理页 —— 对齐 Operit "洛玑表情包渲染器" 管理界面
 * 配置本地目录/外链列表/自动注入/最大表情数/附加规则，支持重建索引与生成提示词
 */

package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.StickerProfile
import me.rerere.rikkahub.data.model.StickerSettings
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.java.KoinJavaComponent.getKoin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerManagerPage() {
    val settingsStore: SettingsStore = getKoin().get()
    var stickerSettings by remember { mutableStateOf<StickerSettings?>(null) }
    var status by remember { mutableStateOf("") }
    var generatedPrompt by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        stickerSettings = settingsStore.settingsFlow.value?.stickerSettings
    }

    val ss = stickerSettings
    val globalProfile = ss?.profiles?.firstOrNull { it.characterCardId.isBlank() }
    var dirsText by remember(ss) { mutableStateOf(globalProfile?.dirs?.joinToString("\n") ?: ss?.defaultDirs ?: "/sdcard/Download/sticker") }
    var externalText by remember(ss) { mutableStateOf(globalProfile?.externalText ?: "") }
    var autoInject by remember(ss) { mutableStateOf(ss?.autoInject ?: false) }
    var maxCount by remember(ss) { mutableStateOf(ss?.maxPerReply ?: 2) }
    var extraRules by remember(ss) { mutableStateOf(ss?.extraRules ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("表情包渲染器") },
                navigationIcon = { BackButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("本地表情包路径", style = MaterialTheme.typography.titleMedium)
                    Text("每行一个目录，例如 /sdcard/Download/sticker", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = dirsText, onValueChange = { dirsText = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
            }

            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("外链表情包列表", style = MaterialTheme.typography.titleMedium)
                    Text("每行 名字: url 或 名字：url，支持空行与 # 注释", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = externalText, onValueChange = { externalText = it }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                }
            }

            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("自动注入系统提示词", style = MaterialTheme.typography.titleMedium)
                        Switch(checked = autoInject, onCheckedChange = { autoInject = it })
                    }
                    Text("单次最多表情数", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = maxCount.toString(), onValueChange = { maxCount = it.toIntOrNull() ?: maxCount }, modifier = Modifier.fillMaxWidth())
                    Text("附加规则（可选）", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = extraRules, onValueChange = { extraRules = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val profile = StickerProfile(
                        id = globalProfile?.id ?: "global",
                        name = "全局默认",
                        characterCardId = "",
                        dirs = dirsText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                        externalText = externalText
                    )
                    val newProfiles = (ss?.profiles?.filterNot { it.characterCardId.isBlank() } ?: emptyList()) + profile
                    val newSettings = (ss ?: StickerSettings()).copy(
                        autoInject = autoInject,
                        maxPerReply = maxCount,
                        extraRules = extraRules,
                        profiles = newProfiles
                    )
                    me.rerere.rikkahub.data.ai.transformers.StickerRenderTransformer.rebuildIndex()
                    scope.launch { settingsStore.update { it.copy(stickerSettings = newSettings) } }
                    stickerSettings = newSettings
                    status = "已保存"
                }) { Text("保存配置") }

                Button(onClick = {
                    me.rerere.rikkahub.data.ai.transformers.StickerRenderTransformer.rebuildIndex()
                    status = "已重建索引"
                }) { Text("重建索引") }

                OutlinedButton(onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        generatedPrompt = me.rerere.rikkahub.data.ai.transformers.StickerRenderTransformer
                            .buildValidNamesPrompt(maxCount, extraRules)
                    }
                }) { Text("生成提示词") }
            }

            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (generatedPrompt.isNotBlank()) {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("可用表情名提示词", style = MaterialTheme.typography.titleMedium)
                        Text(generatedPrompt, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text(
                "说明：<meme>名字</meme> / <sticker>名字</sticker> 标签会被渲染为表情图。本地优先，外链用 EL- 前缀。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}