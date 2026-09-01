/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.utils

import kotlin.text.RegexOption

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
 * 从消息列表中提取最后一个 assistant 消息
 */
fun List<UIMessagePart>.lastAssistantText(): String? {
    return lastOrNull { it is UIMessagePart.Text }?.toText()?.trim()
}

/**
 * 检查消息是否包含工具调用
 */
fun List<UIMessagePart>.hasToolCall(): Boolean {
    return any { it is UIMessagePart.Tool }
}

/**
 * 将消息列表中的工具调用提取为工具列表
 */
fun List<UIMessagePart>.extractTools(): List<UIMessagePart.Tool> {
    return filterIsInstance<UIMessagePart.Tool>()
}
