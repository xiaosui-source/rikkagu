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

    fun parse(file: File): ParsedNovel {
        val raw = when (file.extension.lowercase()) {
            "epub" -> EpubParser.parse(file)
            else -> file.readText(Charsets.UTF_8)
        }.replace("\uFEFF", "")
        return parseText(raw, file.nameWithoutExtension.ifBlank { "未命名小说" })
    }

    /** 从纯文本直接解析（用于网页抓取等场景） */
    fun parseText(raw: String, title: String): ParsedNovel {
        val text = raw.replace("\r\n", "\n").replace("\r", "\n")
        val chapters = splitChapters(text)
        val characters = extractCharacters(text)
        return ParsedNovel(
            title = title.ifBlank { "未命名小说" },
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

    /** 提取候选角色：找"XX说道/笑道"动词，取动词前 2-3 个汉字，过滤常见词，取高频前 N */
    fun extractCharacters(text: String, topN: Int = 20): List<String> {
        val scanned = text.take(MAX_SCAN_CHARS)
        // 动词优先匹配长词（说道>道），避免把"说"吞进名字
        val verbRegex = Regex(
            "(说道|笑着说|笑了笑说|笑道|问道|答道|回答道|喊道|叫道|低声道|轻声说|" +
                "冷冷地说|开口道|接口|回应道|解释道|嘟囔|嘀咕|叹道|喃喃|附和道|" +
                "沉声道|缓缓道|想了想说|说|道|问|答|告诉|解释|开口)"
        )
        val freq = HashMap<String, Int>()
        val blacklist = setOf(
            "不是", "没有", "不要", "一个", "什么", "怎么", "这个", "那个", "自己",
            "你们", "我们", "他们", "她们", "已经", "还是", "就是", "如果", "知道",
            "以后", "现在", "这里", "那里", "然后", "可以", "因为", "所以", "但是",
            "不过", "突然", "终于", "一下", "这时", "时候", "心里", "脸上", "眼睛",
            "声音", "语气", "目光", "淡淡", "微微", "轻轻", "好好", "那人", "只见",
            "他说", "她说", "你说", "我说", "他说", "它说", "我", "他", "她", "你",
            "只见他", "只见她", "看着他", "看着她", "对", "在", "被", "这本", "这本",
            "我的", "他的", "她的", "你的", "大家", "有人", "别人", "我们", "他们",
            "忍不住", "不由", "不禁", "连忙", "赶紧", "急忙", "立刻", "直接", "反而",
            "似乎", "好像", "仿佛", "终于", "总算", "只能", "只得", "只好", "便", "就",
            "开口", "说道", "问道", "答道", "笑着", "笑了笑",
        )
        for (m in verbRegex.findAll(scanned)) {
            val verbStart = m.range.first
            var nameStart = verbStart
            var count = 0
            // 取动词前连续 2-3 个汉字作为名字候选
            while (nameStart > 0 && count < 3 && scanned[nameStart - 1].isCjk()) {
                nameStart--
                count++
            }
            if (count !in 2..3) continue
            val name = scanned.substring(nameStart, verbStart)
            if (name in blacklist) continue
            // 名字不应以常见代词/虚词结尾
            if (name.last() in "的了着过把被将就在和与或向我你他她它") continue
            freq[name] = (freq[name] ?: 0) + 1
        }
        return freq.entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { it.key }
    }

    private fun Char.isCjk(): Boolean = this in '\u4e00'..'\u9fff'
}
