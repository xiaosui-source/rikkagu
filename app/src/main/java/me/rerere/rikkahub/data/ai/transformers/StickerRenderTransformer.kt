/*
 * 灵犀 Lingxi
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 表情包渲染器 —— 移植自 Operit "洛玑表情包渲染器" (com.loki.sticker_renderer) 的核心能力
 *
 * 在 ai 输出文本中把 <meme>名字</meme> 或 <sticker>名字</sticker> 标签渲染为表情包图片。
 * - 本地表情包目录扫描：gif/png/jpg/jpeg/webp
 * - 多目录，重名自动加 1-/2- 数字前缀
 * - 外链表情列表（名字: url），本地优先，外链自动加 EL- 前缀
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

/**
 * 表情包渲染 Transformer。
 *
 * 通过 Koin 惰性读取 Context；表情包目录从 Android 外部存储读取。
 * 默认目录：/sdcard/Download/sticker
 */
object StickerRenderTransformer : OutputMessageTransformer {
    private const val TAG = "StickerRenderTransformer"

    private val extRegex = Regex("""<(meme|sticker)>([\s\S]*?)</(meme|sticker)>""", RegexOption.DOT_MATCHES_ALL)

    /** 支持的表情文件后缀 */
    private val IMAGE_EXTS = setOf("gif", "png", "jpg", "jpeg", "webp")

    /** 外链表情列表：名字 → URL（可为空） */
    private val externalStickers: Map<String, String> by lazy {
        parseExternalRecords()
    }

    /** 本地表情包目录（默认 + 可配置） */
    private fun stickerDirs(): List<File> {
        // 默认目录；后续可从设置读取
        return listOf(File("/sdcard/Download/sticker"))
    }

    // 索引缓存：名字 → 文件路径
    private var indexCache: Map<String, String>? = null
    private var indexCacheStamp = 0L
    private val INDEX_TTL_MS = 5 * 60 * 1000L

    private fun scanIndexLocked(): Map<String, String> {
        val now = System.currentTimeMillis()
        if (indexCache != null && now - indexCacheStamp < INDEX_TTL_MS) return indexCache!!
        val map = LinkedHashMap<String, String>()
        val dirs = stickerDirs()
        dirs.forEachIndexed { dirIndex, dir ->
            val files = runCatching { dir.listFiles()?.toList() ?: emptyList() }.getOrDefault(emptyList())
            val byName = HashMap<String, File>()
            files.forEach { f ->
                val ext = f.extension.lowercase()
                if (f.isFile && ext in IMAGE_EXTS) {
                    byName[f.nameWithoutExtension] = f
                }
            }
            byName.forEach { (name, file) ->
                if (dirs.size > 1) map["$dirIndex-$name"] = file.absolutePath
                map.putIfAbsent(name, file.absolutePath)
            }
        }
        indexCache = map
        indexCacheStamp = now
        return map
    }

    private fun resolveSticker(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        scanIndexLocked().let { idx ->
            idx[trimmed]?.let { return it }
            if (trimmed.startsWith("EL-")) idx[trimmed.removePrefix("EL-")]?.let { return it }
        }
        externalStickers[trimmed]?.let { return it }
        if (trimmed.startsWith("EL-")) externalStickers[trimmed.removePrefix("EL-")]?.let { return it }
        return null
    }

    /** 转成可加载的 URL（本地文件→file://） */
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

    private fun renderStickersInText(text: String): List<UIMessagePart> {
        val result = mutableListOf<UIMessagePart>()
        var lastIndex = 0
        var anyMatched = false
        for (match in extRegex.findAll(text)) {
            if (match.range.first > lastIndex) {
                result.add(UIMessagePart.Text(text.substring(lastIndex, match.range.first)))
            }
            val tag = match.groupValues[1] // meme / sticker
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
        if (!anyMatched) {
            // 全部未解析成功：保留原文本
            return listOf(UIMessagePart.Text(text))
        }
        return result
    }

    private fun parseExternalRecords(): Map<String, String> {
        return runCatching {
            val context: Context = getKoin().get()
            val f = File(context.filesDir, "sticker_external.txt")
            if (!f.exists()) return@runCatching emptyMap()
            val map = LinkedHashMap<String, String>()
            f.readLines().forEach { rawLine ->
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
            map
        }.getOrDefault(emptyMap())
    }

    /** 供管理用：清空索引缓存（文件变化后调用） */
    fun rebuildIndex() {
        indexCache = null
        indexCacheStamp = 0L
        Log.d(TAG, "sticker index rebuilt")
    }
}