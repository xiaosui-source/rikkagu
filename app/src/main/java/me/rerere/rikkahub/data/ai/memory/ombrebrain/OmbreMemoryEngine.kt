/*
 * 灵犀 Lingxi
 * 集成自 OmbreBrain 仿人记忆系统 (https://github.com/XiSiAn916/OmbreBrain)
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.memory.ombrebrain

import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.repository.MemoryRepository

/**
 * OmbreBrain 记忆引擎 —— 主控模块（移植自 OmbreBrain 的 MemoryEngine，适配 rikkahub）
 *
 * 负责：
 * 1. 编码新记忆：按标题去重 + 初始重要度评分
 * 2. 联想召回：按当前上下文只召回最相关记忆
 * 3. 回顾强化：被召回时 reinforce
 * 4. 每日 tick：下沉沉睡 / 唤醒 / 固化
 */
class OmbreMemoryEngine(
    private val memoryRepository: MemoryRepository,
    private val importanceScorer: ImportanceScorer = ImportanceScorer(),
    private val forgettingCurve: ForgettingCurve = ForgettingCurve(),
    private val associationEngine: AssociationEngine = AssociationEngine(),
) {

    /** 编码一条新记忆（标题去重，重复则强化更新） */
    suspend fun encodeMemory(
        assistantId: String,
        title: String,
        content: String,
        sentiment: Double = 0.0,
        tags: List<String> = emptyList(),
        source: String = "ai",
    ): Int {
        val existing = takeUnless { title.isBlank() }?.let {
            memoryRepository.getRawMemoriesOfAssistant(assistantId).filter { m -> title in m.title }
        }?.firstOrNull()

        if (existing != null) {
            // 更新已有记忆：强化
            val updatedImportance = forgettingCurve.reinforce(existing.importance, kotlin.math.abs(sentiment) > 0.5)
            val updated = existing.copy(
                content = content,
                importance = updatedImportance,
                title = title,
                sentiment = sentiment,
                lastTriggeredAt = System.currentTimeMillis(),
                triggerCount = existing.triggerCount + 1,
                tags = (existing.tags.decodeTags() + tags).distinct().encodeTags(),
            )
            memoryRepository.updateEntity(updated)
            return updated.id
        }

        // 新建
        val initialImportance = importanceScorer.initialScore(sentiment, content.length, tags.size).coerceIn(0.2, 1.0)
        return memoryRepository.insertEntity(
            MemoryEntity(
                assistantId = assistantId,
                content = content,
                title = title,
                importance = initialImportance,
                sentiment = sentiment,
                tags = tags.encodeTags(),
                createdAt = System.currentTimeMillis(),
                lastTriggeredAt = System.currentTimeMillis(),
                triggerCount = 1,
                isActive = true,
                isHabit = false,
                source = source,
            )
        )
    }

    /** 联想召回：按当前上下文返回最相关的记忆（Entity 形式，含有效重要度） */
    suspend fun recallEntities(
        assistantId: String,
        context: AssociationEngine.ContextInfo,
        maxResults: Int = AssociationEngine.DEFAULT_TOP_K,
    ): List<AssociationEngine.MatchResult> {
        val memories = memoryRepository.getRawMemoriesOfAssistant(assistantId)
            .map { it.toBrainMemory() }
        return associationEngine.matchMemories(memories, context, forgettingCurve, maxResults = maxResults)
    }

    /** 被召回的强化：召回时更新 lastTriggeredAt + triggerCount + 强化重要度 */
    suspend fun reinforce(
        assistantId: String,
        memoryId: Int,
        userReferenced: Boolean = false,
        sentiment: Double = 0.0,
    ) {
        val entity = memoryRepository.getEntityById(memoryId) ?: return
        if (entity.isHabit) return
        val newImp = if (userReferenced) {
            forgettingCurve.userReferenced(entity.importance)
        } else {
            forgettingCurve.reinforce(entity.importance, kotlin.math.abs(sentiment) > 0.5)
        }
        memoryRepository.updateEntity(
            entity.copy(
                importance = newImp,
                lastTriggeredAt = System.currentTimeMillis(),
                triggerCount = entity.triggerCount + 1,
            )
        )
    }

    /** 每日 tick：检查所有记忆状态，执行下沉/唤醒/固化 */
    suspend fun dailyTick(assistantId: String) {
        val now = System.currentTimeMillis()
        val all = memoryRepository.getRawMemoriesOfAssistant(assistantId) + memoryRepository.getRawGlobalMemories()
        for (entity in all) {
            val state = forgettingCurve.checkState(
                importance = entity.importance,
                triggerCount = entity.triggerCount,
                isActive = entity.isActive,
                isHabit = entity.isHabit,
                lastTriggeredAt = entity.lastTriggeredAt,
                nowMs = now,
            )
            when (state) {
                "habitize", "sink_to_dormant", "awaken" -> {
                    val eff = forgettingCurve.currentImportance(
                        entity.importance, entity.lastTriggeredAt, entity.isHabit, now
                    )
                    val newEntity = when (state) {
                        "habitize" -> entity.copy(isHabit = true, isActive = true, importance = 1.0)
                        "sink_to_dormant" -> entity.copy(isActive = false, importance = eff)
                        "awaken" -> entity.copy(isActive = true, importance = forgettingCurve.awaken(entity.importance))
                        else -> entity
                    }
                    memoryRepository.updateEntity(newEntity)
                }
            }
        }
    }

    // ===== 转换工具 =====
    private fun MemoryEntity.toBrainMemory(): BrainMemory = BrainMemory(
        id = id,
        title = title,
        content = content,
        importance = importance,
        sentiment = sentiment,
        tags = tags.decodeTags(),
        createdAt = createdAt,
        lastTriggeredAt = lastTriggeredAt,
        triggerCount = triggerCount,
        isActive = isActive,
        isHabit = isHabit,
        source = source,
        relatedIds = if (relatedIds.isBlank()) emptyList() else relatedIds.split(",").mapNotNull { it.trim().toLongOrNull() },
    )

    private fun List<String>.encodeTags(): String = joinToString(",")
    private fun String.decodeTags(): List<String> =
        if (isBlank()) emptyList() else split(",").map { it.trim() }.filter { it.isNotEmpty() }
}