/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.toLocalDate

internal fun buildMemoryPrompt(memories: List<AssistantMemory>) =
    buildString {
        appendLine()
        append("**Memories**")
        appendLine()
        append("These are memories stored via the memory_tool that you can reference in future conversations.")
        appendLine()
        val json = buildJsonArray {
            memories
                .sortedWith(
                    compareByDescending<AssistantMemory> { it.isHabit }
                        .thenByDescending { it.importance }
                        .thenByDescending { it.lastTriggeredAt }
                )
                .forEach { memory ->
                    add(buildJsonObject {
                        put("id", memory.id)
                        put("content", memory.content)
                        if (memory.title.isNotBlank()) put("title", memory.title)
                        if (memory.isHabit) put("state", "habit")
                    })
                }
        }
        append(JsonInstantPretty.encodeToString(json))
        appendLine()
    }

/**
 * OmbreBrain 联想召回版记忆 prompt：
 * 当提供当前用户消息时，用 AssociationEngine + ForgettingCurve 计算每条记忆的关联度，
 * 只注入最相关的 top-N 条，并把"重要/固化"记忆标出来 —— 趋近于人脑"触景生情"式回忆。
 * 无用户消息时退化为全部注入（保持原行为）。
 */
internal fun buildMemoryPrompt(
    memories: List<AssistantMemory>,
    userMessage: String?,
    maxResults: Int = me.rerere.rikkahub.data.ai.memory.ombrebrain.AssociationEngine.DEFAULT_TOP_K,
) = buildString {
    appendLine()
    append("**Memories**")
    appendLine()
    append("These are memories recalled from your long-term store per the user's current context. Reference the most relevant ones naturally in your reply.")
    appendLine()
    appendLine()

    val ranked = if (userMessage.isNullOrBlank()) {
        // 无上下文：按重要度/固化排序后取 top-N
        memories
            .sortedWith(
                compareByDescending<AssistantMemory> { it.isHabit }.thenByDescending { it.importance }
            )
            .take(maxResults)
    } else {
        // 联想召回：结合关联度 × 有效重要度排序
        runCatching {
            val engine = me.rerere.rikkahub.data.ai.memory.ombrebrain.AssociationEngine()
            val curve = me.rerere.rikkahub.data.ai.memory.ombrebrain.ForgettingCurve()
            val ctx = me.rerere.rikkahub.data.ai.memory.ombrebrain.AssociationEngine.ContextInfo(userMessage = userMessage)
            val brainMems = memories.map { m ->
                me.rerere.rikkahub.data.ai.memory.ombrebrain.BrainMemory(
                    id = m.id, title = m.title, content = m.content, importance = m.importance,
                    sentiment = m.sentiment, tags = m.tags, createdAt = m.createdAt,
                    lastTriggeredAt = m.lastTriggeredAt, triggerCount = m.triggerCount,
                    isActive = m.isActive, isHabit = m.isHabit, source = m.source,
                )
            }
            val rankedMem = engine.matchMemories(brainMems, ctx, curve, maxResults = maxResults)
            // 保留原始 AssistantMemory 顺序，取被召回的 id
            val ids = rankedMem.map { it.memory.id }.toSet()
            memories.filter { it.id in ids }
        }.getOrElse {
            memories.take(maxResults)
        }
    }

    val json = buildJsonArray {
        ranked.forEach { memory ->
            add(buildJsonObject {
                put("id", memory.id)
                put("content", memory.content)
                if (memory.title.isNotBlank()) put("title", memory.title)
                if (memory.isHabit) put("state", "habit")
                put("importance", memory.importance)
            })
        }
    }
    append(JsonInstantPretty.encodeToString(json))
    appendLine()
}

internal suspend fun buildRecentChatsPrompt(
    assistant: Assistant,
    conversationRepo: ConversationRepository
): String {
    val recentConversations = conversationRepo.getRecentConversations(
        assistantId = assistant.id,
        limit = 10,
    )
    if (recentConversations.isNotEmpty()) {
        return buildString {
            appendLine()
            append("**Recent Chats**")
            appendLine()
            append("These are some of the user's recent conversations. You can use them to understand user preferences:")
            appendLine()
            val json = buildJsonArray {
                recentConversations.forEach { conversation ->
                    add(buildJsonObject {
                        put("title", conversation.title)
                        put("last_chat", conversation.updateAt.toLocalDate())
                    })
                }
            }
            append(JsonInstantPretty.encodeToString(json))
            appendLine()
        }
    }
    return ""
}
