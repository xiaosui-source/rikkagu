/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.extractContextForMatching
import me.rerere.rikkahub.data.model.isTriggered

/**
 * 提示词注入转换器
 *
 * 根据 Assistant 关联的 ModeInjection 和 Lorebook 进行提示词注入
 */
object PromptInjectionTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return transformMessages(
            messages = messages,
            assistant = ctx.assistant,
            modeInjections = ctx.settings.modeInjections,
            lorebooks = ctx.settings.lorebooks
        )
    }
}

/**
 * 核心注入逻辑（可测试的纯函数）
 */
internal fun transformMessages(
    messages: List<UIMessage>,
    assistant: Assistant,
    modeInjections: List<PromptInjection.ModeInjection>,
    lorebooks: List<Lorebook>
): List<UIMessage> {
    // 收集所有需要注入的内容
    val injections = collectInjections(
        messages = messages,
        assistant = assistant,
        modeInjections = modeInjections,
        lorebooks = lorebooks
    )

    if (injections.isEmpty()) {
        return messages
    }

    // 按位置和优先级分组
    val byPosition = injections
        .sortedByDescending { it.priority }
        .groupBy { it.position }

    // 应用注入
    return applyInjections(messages, byPosition)
}

/**
 * 收集需要注入的内容
 */
internal fun collectInjections(
    messages: List<UIMessage>,
    assistant: Assistant,
    modeInjections: List<PromptInjection.ModeInjection>,
    lorebooks: List<Lorebook>
): List<PromptInjection> {
    val injections = mutableListOf<PromptInjection>()

    // 1. 获取关联的 ModeInjection
    modeInjections
        .filter { it.enabled && assistant.modeInjectionIds.contains(it.id) }
        .forEach { injections.add(it) }

    // 2. 获取关联的 Lorebook 中被触发的 RegexInjection
    val enabledLorebooks = lorebooks.filter {
        it.enabled && assistant.lorebookIds.contains(it.id)
    }
    if (enabledLorebooks.isNotEmpty()) {
        // 提取上下文用于匹配（只取非 SYSTEM 消息）
        val nonSystemMessages = messages.filter { it.role != MessageRole.SYSTEM }

        enabledLorebooks.forEach { lorebook ->
            lorebook.entries
                .filter { entry ->
                    val context = extractContextForMatching(nonSystemMessages, entry.scanDepth)
                    entry.isTriggered(context)
                }
                .forEach { injections.add(it) }
        }
    }

    return injections
}

/**
 * 应用注入到消息列表
 *
 * 缓存友好设计：所有位置的注入都合并进 System 消息文本，
 * 不在对话中插入新消息，保持消息前缀稳定，最大化 prompt cache 命中率。
 *
 * System 文本组装顺序：
 *   [BEFORE_SYSTEM_PROMPT] + [原 System 文本] + [AFTER_SYSTEM_PROMPT]
 *   + [TOP_OF_CHAT] + [BOTTOM_OF_CHAT] + [AT_DEPTH]
 */
internal fun applyInjections(
    messages: List<UIMessage>,
    byPosition: Map<InjectionPosition, List<PromptInjection>>
): List<UIMessage> {
    val result = messages.toMutableList()

    // 收集所有需要合并进 System 的内容
    val beforeContent = byPosition[InjectionPosition.BEFORE_SYSTEM_PROMPT]
        ?.joinToString("\n") { it.content } ?: ""
    val afterContent = byPosition[InjectionPosition.AFTER_SYSTEM_PROMPT]
        ?.joinToString("\n") { it.content } ?: ""
    val topContent = byPosition[InjectionPosition.TOP_OF_CHAT]
        ?.joinToString("\n") { it.content } ?: ""
    val bottomContent = byPosition[InjectionPosition.BOTTOM_OF_CHAT]
        ?.joinToString("\n") { it.content } ?: ""
    val atDepthContent = byPosition[InjectionPosition.AT_DEPTH]
        ?.joinToString("\n") { it.content } ?: ""

    // 组装新的 System 文本
    val extraContent = buildString {
        if (topContent.isNotEmpty()) {
            appendLine(topContent)
        }
        if (bottomContent.isNotEmpty()) {
            appendLine(bottomContent)
        }
        if (atDepthContent.isNotEmpty()) {
            appendLine(atDepthContent)
        }
    }.trimEnd()

    // 找到系统消息的索引（通常是第一条）
    val systemIndex = result.indexOfFirst { it.role == MessageRole.SYSTEM }

    if (systemIndex >= 0) {
        val systemMessage = result[systemIndex]
        val originalText = systemMessage.parts
            .filterIsInstance<UIMessagePart.Text>()
            .joinToString("") { it.text }

        val newText = buildString {
            if (beforeContent.isNotEmpty()) {
                append(beforeContent)
                appendLine()
            }
            append(originalText)
            if (afterContent.isNotEmpty()) {
                appendLine()
                append(afterContent)
            }
            if (extraContent.isNotEmpty()) {
                appendLine()
                append(extraContent)
            }
        }

        if (newText != originalText) {
            result[systemIndex] = systemMessage.copy(
                parts = listOf(UIMessagePart.Text(newText))
            )
        }
    } else {
        // 没有系统消息时，创建一个新的系统消息
        val combinedContent = buildString {
            if (beforeContent.isNotEmpty()) append(beforeContent)
            if (afterContent.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                append(afterContent)
            }
            if (extraContent.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                append(extraContent)
            }
        }

        if (combinedContent.isNotEmpty()) {
            result.add(0, UIMessage.system(combinedContent))
        }
    }

    return result
}

/**
 * 将同一 role 的注入合并成消息列表
 * 按 role 分组后合并内容，返回合并后的消息列表
 */
private fun createMergedInjectionMessages(injections: List<PromptInjection>): List<UIMessage> {
    return injections
        .groupBy { it.role }
        .map { (role, grouped) ->
            val mergedContent = grouped.joinToString("\n") { it.content }
            when (role) {
                MessageRole.ASSISTANT -> UIMessage.assistant(mergedContent)
                else -> UIMessage.user(mergedContent)
            }
        }
}

/**
 * 查找安全的插入位置，避免注入到 USER → ASSISTANT(含Tool) 之间
 *
 * 某些提供商（如 deepseek）要求 USER 之后紧跟带工具的 ASSISTANT，
 * 在两者之间插入消息会导致报错或破坏推理连续性。
 */
internal fun findSafeInsertIndex(messages: List<UIMessage>, targetIndex: Int): Int {
    var index = targetIndex.coerceIn(0, messages.size)

    // 向前查找，直到找到一个安全的位置
    while (index > 0) {
        val prevMessage = messages.getOrNull(index - 1)
        val currentMessage = messages.getOrNull(index)

        // 不能插入到 USER → ASSISTANT(含Tool) 之间
        val isPrevUser = prevMessage?.role == MessageRole.USER
        val isCurrentAssistantWithTools = currentMessage?.role == MessageRole.ASSISTANT
            && currentMessage.getTools().isNotEmpty()

        if (isPrevUser && isCurrentAssistantWithTools) {
            index--
        } else {
            break
        }
    }

    return index
}
