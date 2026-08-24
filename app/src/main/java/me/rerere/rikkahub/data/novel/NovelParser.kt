/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.novel

import me.rerere.document.EpubParser
import java.io.File

/**
 * 小说解析器：支持 txt / epub
 * - 按章节切分
 * - 启发式提取候选角色（"XX说道/XX笑道" 前缀词频）
 */
object NovelParser {

    data class ParsedNovel(
        val title: String,
        val chapters: List<String>,
        val characters: List<String>,
    )

    /** 单章最大保存长度（避免 JSON 过大，超出截断） */
    private const val MAX_CHAPTER_LENGTH = 6000
    /** 最多保存章节数 */
    private const val MAX_CHAPTERS = 80
    /** 参与角色统计的最大字符数 */
    private const val MAX_SCAN_CHARS = 400_000

    private val chapterRegex = Regex(
        "(第[0-9零一二三四五六七八九十百千万两]+[章节回卷部集篇][^\n]{0,30})|" +
            "([Cc]hapter\\s*[0-9]+)|([Pp]rologue)|([Ee]pilogue)"
    )

    private val speechRegex = Regex(
        "([\\u4e00-\\u9fa5]{2,4})" +
            "(?:说道|笑着说|笑道|说|道|问|问道|答道|回答道|喊道|叫道|低声道|轻声说|" +
            "冷冷地说|开口道|开口|接口|回应道|解释|解释道|告诉|对着|看向|看向我)"
    )

    fun parse(file: File): ParsedNovel {
        val raw = when (file.extension.lowercase()) {
            "epub" -> EpubParser.parse(file)
            else -> file.readText(Charsets.UTF_8)
        }.replace("\uFEFF", "")

        val text = raw.replace("\r\n", "\n").replace("\r", "\n")
        val chapters = splitChapters(text)
        val title = file.nameWithoutExtension.ifBlank { "未命名小说" }
        val characters = extractCharacters(text)
        return ParsedNovel(
            title = title,
            chapters = chapters,
            characters = characters,
        )
    }

    /** 按章节标题切分；没有标题则按空行分块 */
    fun splitChapters(text: String): List<String> {
        val matches = chapterRegex.findAll(text).toList()
        if (matches.isEmpty()) {
            // 无章节标题：按段落分块（每块约 3000 字）
            return text.split(Regex("\\n\\s*\\n"))
                .map { it.trim() }
                .filter { it.length >= 50 }
                .chunked(3) { it.joinToString("\n\n") }
                .take(MAX_CHAPTERS)
                .map { it.take(MAX_CHAPTER_LENGTH) }
        }
        val result = mutableListOf<String>()
        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val chapter = text.substring(start, end).trim()
            if (chapter.isNotBlank()) {
                result.add(chapter.take(MAX_CHAPTER_LENGTH))
            }
            if (result.size >= MAX_CHAPTERS) break
        }
        return result
    }

    /** 提取候选角色：统计"XX说/道/笑道"前的 2-4 字人名，取高频前 20 */
    fun extractCharacters(text: String, topN: Int = 20): List<String> {
        val scanned = text.take(MAX_SCAN_CHARS)
        val freq = HashMap<String, Int>()
        val blacklist = setOf(
            "不是", "没有", "不要", "一个", "什么", "怎么", "这个", "那个", "自己",
            "你们", "我们", "他们", "她们", "已经", "还是", "就是", "如果", "知道",
            "以后", "现在", "这里", "那里", "然后", "可以", "因为", "所以", "但是",
            "不过", "突然", "终于", "一下", "这时", "时候", "心里", "脸上", "眼睛",
            "声音", "语气", "目光", "淡淡", "微微", "轻轻", "好好", "那人", "只见",
        )
        for (m in speechRegex.findAll(scanned)) {
            val name = m.groupValues[1]
            if (name.length !in 2..4) continue
            if (name in blacklist) continue
            // 名字里不应带明显动词/助词
            if (name.any { it in "的了着过把被将就在和与或" }) continue
            freq[name] = (freq[name] ?: 0) + 1
        }
        return freq.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .take(topN)
            .map { it.key }
    }
}
