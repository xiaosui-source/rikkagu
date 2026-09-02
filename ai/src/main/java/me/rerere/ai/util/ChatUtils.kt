/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 参考 Operit ChatUtils
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.ai.util

/**
 * 聊天工具类，提供token估算等功能
 * 
 * 完全对齐 Operit AI ChatUtils
 */
object ChatUtils {
    /**
     * 估算文本的token数量
     * 简单估算：中文每个字约1.5个token，英文每4个字符约1个token
     * 
     * @param text 要估算token的文本
     * @return 估算的token数量
     */
    fun estimateTokenCount(text: String): Long {
        val chineseCharCount = text.count { it.code in 0x4E00..0x9FFF }
        val otherCharCount = text.length - chineseCharCount
        return (chineseCharCount * 1.5 + otherCharCount * 0.25).toLong()
    }

    /**
     * 从 AI 响应中提取 JSON 对象部分
     * AI 可能会在 JSON 前后添加说明文字或使用 ```json 代码块，需要提取出纯净的 JSON
     */
    fun extractJson(response: String): String {
        var text = response.trim()
        
        // 处理 markdown 代码块格式 ```json ... ```
        if (text.startsWith("```")) {
            val lines = text.lines()
            text = lines.drop(1).dropLast(1).joinToString("\n").trim()
        }
        
        // 寻找第一个 { 和最后一个 }
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        
        return if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
            text.substring(firstBrace, lastBrace + 1)
        } else {
            text
        }
    }

    /**
     * 移除内容中的思考标签（think/thinking/search）
     */
    fun removeThinkingContent(content: String): String {
        val thinkPattern = "<think(?:ing)?>.*?(</think(?:ing)?>|\\z)".toRegex(RegexOption.DOT_MATCHES_ALL)
        val searchPattern = "<search>.*?(</search>|\\z)".toRegex(RegexOption.DOT_MATCHES_ALL)
        return content.replace(thinkPattern, "").replace(searchPattern, "").trim()
    }

    /**
     * 检测内容是否是纯思考输出（移除思考标签后为空）
     */
    fun isPureThinking(content: String): Boolean {
        return removeThinkingContent(content).isEmpty()
    }
}
