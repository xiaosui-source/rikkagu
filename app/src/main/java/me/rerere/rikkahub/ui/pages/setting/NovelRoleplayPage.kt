/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.pages.setting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.BookOpen01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.novel.NovelParser
import me.rerere.rikkahub.data.novel.NovelScene
import me.rerere.rikkahub.data.novel.NovelStore
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject
import java.io.File
import kotlin.uuid.Uuid

/**
 * 小说导入 · 角色扮演
 * 导入 txt/epub 小说 → 自动切章、提取角色 → 选择角色开始扮演对话
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelRoleplayPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val novelStore = koinInject<NovelStore>()
    val settingsStore = koinInject<SettingsStore>()
    val scenes by novelStore.scenes.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    var importing by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var editingSynopsis by remember { mutableStateOf<NovelScene?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            importing = true
            scope.launch {
                try {
                    val scene = withContext(Dispatchers.IO) {
                        importNovel(context.contentResolver.openInputStream(uri), uri)
                    }
                    if (scene != null) {
                        novelStore.add(scene)
                        toaster.show("已导入《${scene.title}》：${scene.chapters.size} 章，${scene.characters.size} 个候选角色")
                    } else {
                        toaster.show("解析失败：文件为空或格式不支持")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    toaster.show("导入失败：${e.message ?: e.toString()}")
                } finally {
                    importing = false
                }
            }
        }
    }

    fun startRoleplay(scene: NovelScene, character: String) {
        scope.launch {
            val settings = settingsStore.settingsFlow.value
            val base = settings.getCurrentAssistant()
            val prompt = buildRoleplayPrompt(scene, character)
            val roleplayAssistant = base.copy(
                id = Uuid.random(),
                name = "角色扮演·${scene.title}·$character",
                systemPrompt = prompt,
            )
            settingsStore.update { it.copy(assistants = it.assistants + roleplayAssistant) }
            settingsStore.updateAssistant(roleplayAssistant.id)
            navController.clearAndNavigate(
                Screen.Chat(
                    id = Uuid.random().toString(),
                    text = "我是${character}。${scene.synopsis.ifBlank { "让我们开始吧" }}",
                )
            )
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("小说角色扮演") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding + PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CardGroup {
                item(
                    leadingContent = { Icon(HugeIcons.BookOpen01, null) },
                    headlineContent = { Text("小说导入 · 角色扮演") },
                    supportingContent = {
                        Text(
                            "导入 TXT / EPUB 小说，自动切分章节、提取角色。选择角色后即可与 AI 进行原著角色扮演对话。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                )
                item(
                    onClick = { pickerLauncher.launch(arrayOf("text/plain", "text/*", "application/epub+zip")) },
                    leadingContent = { Icon(HugeIcons.AiMagic, null) },
                    headlineContent = { Text(if (importing) "正在解析小说…" else "导入小说文件") },
                    supportingContent = {
                        Text(
                            "支持 .txt / .epub，自动切章 + 提取角色",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                )
            }

            CardGroup(
                title = { Text("我的场景（${scenes.size}）") },
            ) {
                if (scenes.isEmpty()) {
                    item {
                        Text(
                            "还没有导入小说。点击上方「导入小说文件」开始。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                scenes.forEach { scene ->
                    val expanded = expandedId == scene.id
                    item(
                        onClick = { expandedId = if (expanded) null else scene.id },
                        leadingContent = { Icon(HugeIcons.BookOpen01, null) },
                        headlineContent = { Text(scene.title) },
                        supportingContent = {
                            Text(
                                "${scene.chapters.size} 章 · ${scene.characters.size} 个角色" +
                                    (if (scene.synopsis.isNotBlank()) " · ${scene.synopsis.take(24)}…" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch { novelStore.remove(scene.id) }
                            }) {
                                Icon(HugeIcons.Delete02, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                    if (expanded) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "角色列表（点击开始扮演）：",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (scene.characters.isEmpty()) {
                                    Text("未提取到角色，可编辑简介或换一本对话较多的小说试试。")
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    scene.characters.take(10).forEach { character ->
                                        FilterChip(
                                            selected = false,
                                            onClick = { startRoleplay(scene, character) },
                                            label = { Text(character) }
                                        )
                                    }
                                }
                                TextButton(onClick = { editingSynopsis = scene }) {
                                    Text("编辑简介（可选，注入 AI 设定）")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingSynopsis?.let { scene ->
        var synopsis by remember { mutableStateOf(scene.synopsis) }
        AlertDialog(
            onDismissRequest = { editingSynopsis = null },
            title = { Text("剧情简介（可选）") },
            text = {
                OutlinedTextField(
                    value = synopsis,
                    onValueChange = { synopsis = it },
                    placeholder = { Text("例：少年在修仙界逆袭的故事，主角性格坚韧…") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch { novelStore.update(scene.copy(synopsis = synopsis.trim())) }
                    editingSynopsis = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingSynopsis = null }) { Text("取消") }
            }
        )
    }
}

/** 从 URI 导入并解析小说 */
private fun importNovel(stream: java.io.InputStream?, uri: Uri): NovelScene? {
    if (stream == null) return null
    val ext = uri.lastPathSegment?.substringAfterLast('.', "txt")?.lowercase() ?: "txt"
    val tmp = File.createTempFile("novel_import", ".$ext")
    return stream.use { input ->
        tmp.outputStream().use { out -> input.copyTo(out) }
        val parsed = NovelParser.parse(tmp)
        if (parsed.chapters.isEmpty()) null
        else NovelScene(
            title = parsed.title,
            sourceFileName = uri.lastPathSegment ?: "",
            characters = parsed.characters,
            chapters = parsed.chapters,
        )
    }.also { tmp.delete() }
}

/** 构建角色扮演系统提示词 */
private fun buildRoleplayPrompt(scene: NovelScene, character: String): String = buildString {
    appendLine("你现在扮演小说《${scene.title}》中的角色「$character」。")
    appendLine()
    if (scene.synopsis.isNotBlank()) {
        appendLine("【剧情简介】")
        appendLine(scene.synopsis)
        appendLine()
    }
    appendLine("【小说情节（节选，用于把握世界观与人物）】")
    scene.chapters.take(3).forEachIndexed { index, chapter ->
        appendLine("—— 第 ${index + 1} 节 ——")
        appendLine(chapter.take(1500))
        appendLine()
    }
    appendLine("【扮演规则】")
    appendLine("1. 完全以「$character」的身份、性格、语气和口吻说话，绝不跳出角色。")
    appendLine("2. 用户将扮演另一个角色或读者与你对话，请自然互动并推动剧情。")
    appendLine("3. 严格遵循原著世界观与人物关系，不偏离设定。")
    appendLine("4. 回复自然、口语化，长度适中（100~300 字），必要时可描写动作与神态。")
    appendLine("5. 若用户请求超出当前情节，可基于原著风格合理演绎。")
}
