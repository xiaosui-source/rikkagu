/*
 * 灵犀 Lingxi
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 表情包渲染器 —— 极简零配置版
 *
 * 把表情图片放进 /sdcard/Download/sticker，AI 就会自动用 <meme>/<sticker> 标签发出来。
 * 无需任何配置页/开关。可选：filesDir/sticker_external.txt 每行 `名字: url` 追加外链表情。
 */

package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.net.Uri
import android.util.Log
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

    /** 默认表情包目录（为了便捷】可直接把图丢这） */
    val DEFAULT_DIRS: List<String> = listOf(
        "/sdcard/Download/sticker",
        "/storage/emulated/0/Download/sticker",
    )

    private var indexCache: Map<String, String>? = null
    private var indexStamp = 0L

    private fun stickerDirs(): List<File> {
        val dirs = mutableListOf<File>()
        DEFAULT_DIRS.forEach { p -> runCatching { File(p) }.getOrNull()?.let { dirs.add(it) } }
        // 兼容内置 app 私有目录下的 sticker 目录（手动放入也可）
        runCatching {
            val ctx: Context = getKoin().get()
            val internalSticker = File(ctx.filesDir, "sticker")
            if (internalSticker.exists() || internalSticker.mkdirs()) dirs.add(internalSticker)
        }
        return dirs
    }

    private fun scanIndex(): Map<String, String> {
        val now = System.currentTimeMillis()
        if (indexCache != null && now - indexStamp < INDEX_TTL_MS) return indexCache!!
        val map = LinkedHashMap<String, String>()
        val dirs = stickerDirs()
        dirs.forEachIndexed { dirIndex, dir ->
            if (!dir.exists()) return@forEachIndexed
            val files = runCatching { dir.listFiles()?.toList() ?: emptyList() }.getOrDefault(emptyList())
            files.forEach { file ->
                val ext = file.extension.lowercase()
                if (file.isFile && ext in IMAGE_EXTS) {
                    val name = file.nameWithoutExtension
                    if (dirs.size > 1) map["$dirIndex-$name"] = file.absolutePath
                    map.putIfAbsent(name, file.absolutePath)
                }
            }
        }
        // 外链（可选）
        parseExternalRecords().forEach { (name, url) ->
            map.putIfAbsent(name, url)
            map.putIfAbsent("EL-$name", url)
        }
        indexCache = map
        indexStamp = now
        return map
    }

    private fun resolveSticker(name: String): String? {
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
            if (!message.parts.any { it is UIMessagePart.Text }) return@map message
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

    private fun parseExternalRecords(): Map<String, String> {
        return runCatching {
            val ctx: Context = getKoin().get()
            val f = File(ctx.filesDir, "sticker_external.txt")
            if (!f.exists()) return@runCatching emptyMap()
            val map = LinkedHashMap<String, String>()
            f.readLines().forEach { raw ->
                val line = raw.trim()
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
            map
        }.getOrDefault(emptyMap())
    }

    /** 检测是否有可用表情 */
    suspend fun hasStickers(): Boolean = scanIndex().isNotEmpty()

    /** 生成提示词：列出所有可用表情名，指导 AI 用标签输出（无需配置） */
    suspend fun buildPrompt(): String {
        val names = scanIndex().keys.filterNot { it.startsWith("EL-") }.distinct()
        val nameLine = names.joinToString(" | ")
        val sb = StringBuilder()
        sb.appendLine("### 表情包")
        sb.appendLine("[Valid names]")
        if (nameLine.isNotBlank()) sb.appendLine(nameLine)
        sb.appendLine("当你想发表情时，用 <meme>名字</meme> 或 <sticker>名字</sticker> 标签输出，表情会自动渲染成图片。")
        sb.appendLine("只使用上面 [Valid names] 里存在的名字，每条回复最多 2 个。")
        sb.appendLine("不要把标签包进 Markdown 或代码块。")
        return sb.toString()
    }

    /** 重建索引 */
    fun rebuildIndex() {
        indexCache = null
        indexStamp = 0L
        Log.d(TAG, "sticker index rebuilt")
    }
}