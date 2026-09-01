/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.utils

import kotlin.text.RegexOption
import me.rerere.ai.ui.UIMessagePart

/**
 * 移除内容中的思考标签（think/thinking/search）
 * 参考 Operit EnhancedAIService 的 removeThinkingContent
 */
fun removeThinkingContent(content: String): String {
    // 使用正则表达式匹配 <think>...</think> 和 <search>...</search> 标签及其内容
    val thinkPattern = "<think(?:ing)?>.*?(</think(?:ing)?>|\\z)".toRegex(RegexOption.DOT_MATCHES_ALL)
    val searchPattern = "<search>.*?(</search>|\\z)".toRegex(RegexOption.DOT_MATCHES_ALL)
    return content.replace(thinkPattern, "").replace(searchPattern, "").trim()
}

/**
 * 移除内容中的思考标签，并返回 (移除后的内容, 思考内容)
 * 参考 Operit ChatUtils.extractThinkingContent
 */
fun extractThinkingContent(content: String): Pair<String, String> {
    val thinkPattern = "<think(?:ing)?>([\\s\\S]*?)</think(?:ing)?>".toRegex(RegexOption.DOT_MATCHES_ALL)
    val thinkMatches = thinkPattern.findAll(content)
    
    val extractedThinking = thinkMatches.mapNotNull { it.groupValues[1] }.joinToString("")
    val withoutThinking = content.replace(thinkPattern, "").trim()
    
    return withoutThinking to extractedThinking
}

/**
 * 检测内容是否是纯思考输出（移除思考标签后为空）
 */
fun isPureThinking(content: String): Boolean {
    return removeThinkingContent(content).isEmpty()
}

/**
 * 将内容中的思考标签转换为纯文本思考（用于显示）
 */
fun formatThinkingContent(content: String): String {
    val (withoutThinking, thinking) = extractThinkingContent(content)
    return if (thinking.isNotEmpty()) {
        "$withoutThinking\n\n[思考过程]\n$thinking"
    } else {
        content
    }
}

/**
 * 将文本拆分成气泡片段（保护代码块和表格内部的换行）
 */
fun String.splitIntoBubbleSegments(): List<String> {
    val segments = mutableListOf<String>()
    val lines = this.split("\n")
    var inCodeBlock = false
    var buffer = StringBuilder()
    
    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                buffer.append(line).append("\n")
                segments.add(buffer.toString())
                buffer = StringBuilder()
            }
            inCodeBlock = !inCodeBlock
            buffer.append(line).append("\n")
        } else if (inCodeBlock) {
            buffer.append(line).append("\n")
        } else {
            if (line.isEmpty()) {
                if (buffer.isNotEmpty()) {
                    segments.add(buffer.toString().trim())
                    buffer = StringBuilder()
                }
            } else {
                buffer.append(line).append("\n")
            }
        }
    }
    
    if (buffer.isNotEmpty()) {
        segments.add(buffer.toString().trim())
    }
    
    return segments.filter { it.isNotEmpty() }
}

/**
 * 从思考内容中提取标题
 */
fun String.extractThinkingTitle(): String? {
    val lines = this.split("\n").filter { it.isNotEmpty() }
    return if (lines.isNotEmpty()) lines.firstOrNull()?.trim() else null
}

/**
 * 移除 Markdown 格式标记
 */
fun String.stripMarkdown(): String {
    return this
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")  // **bold**
        .replace(Regex("\\*(.*?)\\*"), "$1")        // *italic*
        .replace(Regex("__(.*?)__"), "$1")          // __bold__
        .replace(Regex("_ (.*?)_"), "$1")           // _italic_
        .replace(Regex("`([^`]+)`"), "$1")         // `code`
        .replace(Regex("\\[([^]]+)\\]\\([^)]+\\)"), "$1")  // [text](url)
        .replace(Regex("!\\[[^]]*\\]\\([^)]+\\)"), "")  // ![alt](url)
        .replace(Regex("^#{1,6}\\s+"), "")         // headings
        .replace(Regex("^[-*+]\\s+"), "")          // lists
        .replace(Regex("^>\\s+"), "")              // quotes
        .replace(Regex("^---+$"), "")              // horizontal rules
        .replace(Regex("\\n{3,}"), "\n\n")         // 压缩多余换行
        .trim()
}
