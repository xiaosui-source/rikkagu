/*
 * 灵犀 Lingxi
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 表情包渲染器 —— 对齐 Operit "洛玑表情包渲染器" (com.loki.sticker_renderer)
 *
 * 在 ai 输出文本中把 <meme>名字</meme> 或 <sticker>名字</sticker> 标签渲染为表情包图片。
 * 配置读取自 SettingsStore.stickerSettings：
 * - local dirs（多目录、可配置）
 * - external list（外链表情，本地优先 EL- 前缀）
 * - maxPerReply（提示词中限定的单次表情数）
 */

package me.rerere.rikkahub.data.ai.transformers

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.first
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.koin.java.KoinJavaComponent.getKoin
import java.io.File

object StickerRenderTransformer : OutputMessageTransformer {
    private const val TAG = "StickerRenderTransformer"

    private val extRegex = Regex("""<(meme|sticker)>([\s\S]*?)</(meme|sticker)>""", RegexOption.DOT_MATCHES_ALL)
    private val IMAGE_EXTS = setOf("gif", "png", "jpg", "jpeg", "webp")
    private val INDEX_TTL_MS = 5 * 60 * 1000L

    // 索引缓存：目录组合签名 -> (名字 -> 路径)
    private var indexSignature = ""
    private var indexCache: Map<String, String> = emptyMap()
    private var indexStamp = 0L

    /** 从 Settings 读取当前目录列表（全局默认 profile 的 dirs） */
    private suspend fun currentDirs(): List<String> {
        return runCatching {
            val settingsStore: me.rerere.rikkahub.data.datastore.SettingsStore = getKoin().get()
            val s = settingsStore.settingsFlow.first().stickerSettings
            val dirs = s.profiles.firstOrNull { it.characterCardId.isBlank() }?.dirs?.filter { it.isNotBlank() }
                ?: listOf(s.defaultDirs)
            dirs.ifEmpty { listOf(s.defaultDirs) }
        }.getOrElse { listOf("/sdcard/Download/sticker") }
    }

    /** 从 Settings 读取当前外链表情原始文本 */
    private suspend fun currentExternalText(): String {
        return runCatching {
            val settingsStore: me.rerere.rikkahub.data.datastore.SettingsStore = getKoin().get()
            settingsStore.settingsFlow.first().stickerSettings.profiles
                .firstOrNull { it.characterCardId.isBlank() }?.externalText ?: ""
        }.getOrElse { "" }
    }

    private suspend fun scanIndex(): Map<String, String> {
        val dirs = currentDirs()
        val externalText = currentExternalText()
        val signature = "$dirs|$externalText"
        val now = System.currentTimeMillis()
        if (signature == indexSignature && now - indexStamp < INDEX_TTL_MS) return indexCache

        val map = LinkedHashMap<String, String>()
        dirs.forEachIndexed { dirIndex, dir ->
            val f = File(dir)
            val files = runCatching { f.listFiles()?.toList() ?: emptyList() }.getOrDefault(emptyList())
            files.forEach { file ->
                val ext = file.extension.lowercase()
                if (file.isFile && ext in IMAGE_EXTS) {
                    val name = file.nameWithoutExtension
                    if (dirs.size > 1) map["$dirIndex-$name"] = file.absolutePath
                    map.putIfAbsent(name, file.absolutePath)
                }
            }
        }
        parseExternalRecords(externalText).forEach { (name, url) ->
            map.putIfAbsent(name, url)
            val key = "EL-$name"
            map.putIfAbsent(key, url)
        }

        indexSignature = signature
        indexCache = map
        indexStamp = now
        return map
    }

    private suspend fun resolveSticker(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val idx = scanIndex()
        idx[trimmed]?.let { return it }
        if (trimmed.startsWith("EL-")) idx[trimmed.removePrefix("EL-")]?.let { return it }
        return null
    }

    private fun toRenderUrl(raw: String): String {
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("/") -> Uri.fromFile(File(raw)).toString()
            else -> raw
        }
    }

    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT) return@map message
            val hasText = message.parts.any { it is UIMessagePart.Text }
            if (!hasText) return@map message
            message.copy(
                parts = message.parts.flatMap { part ->
                    if (part is UIMessagePart.Text && extRegex.containsMatchIn(part.text)) {
                        renderStickersInText(part.text)
                    } else {
                        listOf(part)
                    }
                }
            )
        }
    }

    private suspend fun renderStickersInText(text: String): List<UIMessagePart> {
        val result = mutableListOf<UIMessagePart>()
        var lastIndex = 0
        var anyMatched = false
        for (match in extRegex.findAll(text)) {
            if (match.range.first > lastIndex) {
                result.add(UIMessagePart.Text(text.substring(lastIndex, match.range.first)))
            }
            val tag = match.groupValues[1]
            val name = match.groupValues[2].trim()
            val resolved = resolveSticker(name)
            if (resolved != null) {
                result.add(UIMessagePart.Image(url = toRenderUrl(resolved)))
                anyMatched = true
            } else {
                result.add(UIMessagePart.Text("[$tag:未找到表情 $name]"))
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            result.add(UIMessagePart.Text(text.substring(lastIndex)))
        }
        return if (anyMatched) result else listOf(UIMessagePart.Text(text))
    }

    private fun parseExternalRecords(text: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        text.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val sep = line.indexOfFirst { it == ':' || it == '：' }
            if (sep > 0) {
                val name = line.substring(0, sep).trim()
                val url = line.substring(sep + 1).trim()
                if (name.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    map[name] = url
                }
            }
        }
        return map
    }

    /** 重建索引 */
    fun rebuildIndex() {
        indexSignature = ""
        indexCache = emptyMap()
        indexStamp = 0L
        Log.d(TAG, "sticker index rebuilt")
    }

    /** 生成可复制/注入的提示词片段（供管理界面展示） */
    suspend fun buildValidNamesPrompt(maxPerReply: Int, extraRules: String): String {
        val names = scanIndex().keys.filter { !it.startsWith("EL-") && !it.contains("-") }
            .distinct()
        val nameLine = names.joinToString(" | ") { it }
        val sb = StringBuilder()
        sb.appendLine("### 表情包")
        sb.appendLine("[Valid names]")
        if (nameLine.isNotBlank()) sb.appendLine(nameLine)
        sb.appendLine("你可以使用 <meme>名字</meme> 或 <sticker>名字</sticker> 标签输出表情，每条回复最多 $maxPerReply 个。")
        sb.appendLine("不要把标签包进 Markdown 或代码块。")
        val extra = extraRules.trim()
        if (extra.isNotEmpty()) {
            sb.appendLine("附加规则：$extra")
        }
        return sb.toString()
    }
}