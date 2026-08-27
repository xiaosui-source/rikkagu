/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * 用户技术水平自适应分层。
 *
 * 目标：对「什么都不会的小白」静默使用方法论、全程自动、用大白话交互、不暴露技能/方法论术语；
 * 对「懂行开发者」保留现有的技能+方法论辅助模式。
 *
 * 核心原则：检测要「保守」，只在用户明确表露小白特征时才走小白通道，避免误伤懂行用户体验。
 */
object UserHelpLevel {

    /** 小白信号：用户表达「不会 / 不懂 / 直接帮我做 / 说得简单」等 WHERE 描述或朴素的求助式问法。 */
    private val BEGINNER_HINTS = listOf(
        "不会", "不懂", "不知道怎么", "什么都不会", "我是小白", "第一次用",
        "帮我弄", "帮我做", "帮我直接", "帮我搞定", "教教我", "帮我设置",
        "简单点", "说人话", "用大白话", "不要太复杂", "别用专业术语",
        "我看不懂", "听不懂", "这是什么", "怎么弄", "怎么用",
    )

    /**
     * 判定最近用户消息是否应走「小白」交互通道。
     * 只读用户消息文本，忽略工具结果/系统片段。取最近一条用户消息做判断。
     * @param messages 当前对话的消息列表
     * @return true=按小白方式交互；false=按普通/懂行方式
     */
    fun isBeginner(messages: List<UIMessage>): Boolean {
        val userText = messages.asReversed()
            .firstNotNullOfOrNull { msg ->
                val text = buildString {
                    msg.parts.forEach { part ->
                        if (part is UIMessagePart.Text) append(part.text).append(' ')
                    }
                }
                text.takeIf { it.isNotBlank() }
            }
            ?: return false
        val needle = userText.trim()
        if (needle.isBlank()) return false
        // 仅当一句话式求助/明显无技术含量时判定为小白，避免误伤
        val lower = needle.lowercase()
        if (lower.length <= 30) {
            // 很短的问题句，且疑似求助式 → 倾向于小白（保守但合理）
            if (BEGINNER_HINTS.any { lower.contains(it) }) return true
        } else {
            // 长句：需要命中较明确的小白信号才判小白
            if (BEGINNER_HINTS.count { needle.contains(it) } >= 1 && isPlainLanguage(needle)) return true
        }
        return false
    }

    /** 粗略判断是否「日常口语/非技术」表述：不含明显技术术语。 */
    private fun isPlainLanguage(text: String): Boolean {
        val techMarkers = listOf(
            "代码", "实现", "接口", "重构", "编译", "bug", "debug", "部署",
            "数据库", "api", "函数", "类", "依赖", "git", "npm", "sdk",
            "function", "class", "python", "java", "kotlin", "frontend", "backend",
        )
        val lower = text.lowercase()
        return techMarkers.none { lower.contains(it) }
    }
}
