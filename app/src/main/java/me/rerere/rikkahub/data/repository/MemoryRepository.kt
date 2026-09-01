/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.model.AssistantMemory

class MemoryRepository(private val memoryDAO: MemoryDAO) {
    companion object {
        const val GLOBAL_MEMORY_ID = "__global__"
    }

    private fun MemoryEntity.toAssistantMemory(): AssistantMemory = AssistantMemory(
        id = id,
        content = content,
        title = title,
        importance = importance,
        sentiment = sentiment,
        tags = tags.decodeCsv(),
        createdAt = createdAt,
        lastTriggeredAt = lastTriggeredAt,
        triggerCount = triggerCount,
        isActive = isActive,
        isHabit = isHabit,
        source = source,
    )

    fun getMemoriesOfAssistantFlow(assistantId: String): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(assistantId)
            .map { entities -> entities.filterNot { it.isSummaryMemory() }.map { it.toAssistantMemory() } }

    suspend fun getMemoriesOfAssistant(assistantId: String): List<AssistantMemory> {
        return memoryDAO.getMemoriesOfAssistant(assistantId)
            .filterNot { it.isSummaryMemory() }
            .map { it.toAssistantMemory() }
    }

    fun getGlobalMemoriesFlow(): Flow<List<AssistantMemory>> =
        memoryDAO.getMemoriesOfAssistantFlow(GLOBAL_MEMORY_ID)
            .map { entities -> entities.filterNot { it.isSummaryMemory() }.map { it.toAssistantMemory() } }

    suspend fun getGlobalMemories(): List<AssistantMemory> {
        return memoryDAO.getMemoriesOfAssistant(GLOBAL_MEMORY_ID)
            .filterNot { it.isSummaryMemory() }
            .map { it.toAssistantMemory() }
    }

    /** 供引擎用：取某助手全部记忆实体（含摘要，引擎会自行过滤） */
    suspend fun getRawMemoriesOfAssistant(assistantId: String): List<MemoryEntity> =
        memoryDAO.getMemoriesOfAssistant(assistantId)

    suspend fun getRawGlobalMemories(): List<MemoryEntity> =
        memoryDAO.getMemoriesOfAssistant(GLOBAL_MEMORY_ID)

    suspend fun getEntityById(id: Int): MemoryEntity? = memoryDAO.getMemoryById(id)

    private fun MemoryEntity.isSummaryMemory(): Boolean {
        return content.startsWith("[daily_summary]") ||
            content.startsWith("[phase_summary]") ||
            content.startsWith("[auto_summary]")
    }

    suspend fun deleteMemoriesOfAssistant(assistantId: String) {
        memoryDAO.deleteMemoriesOfAssistant(assistantId)
    }

    suspend fun updateContent(id: Int, content: String): AssistantMemory {
        val old = memoryDAO.getMemoryById(id) ?: error("Memory record #$id not found")
        val newMemory = old.copy(content = content)
        memoryDAO.updateMemory(newMemory)
        return newMemory.toAssistantMemory()
    }

    /** 引擎用：更新整条记忆实体 */
    suspend fun updateEntity(entity: MemoryEntity) {
        memoryDAO.updateMemory(entity)
    }

    /** 引擎用：插入实体，返回 id */
    suspend fun insertEntity(entity: MemoryEntity): Int =
        memoryDAO.insertMemory(entity).toInt()

    /** 关联记忆（参考 Operit memory link）：把 source 的 relatedIds 追加 targetId，双向关联 */
    suspend fun linkMemories(sourceId: Int, targetId: Int): AssistantMemory? {
        val source = memoryDAO.getMemoryById(sourceId) ?: return null
        val target = memoryDAO.getMemoryById(targetId) ?: return null
        val srcRelated = source.relatedIds.decodeCsv().toMutableList()
        if (targetId.toString() !in srcRelated) srcRelated.add(targetId.toString())
        val tgtRelated = target.relatedIds.decodeCsv().toMutableList()
        if (sourceId.toString() !in tgtRelated) tgtRelated.add(sourceId.toString())
        memoryDAO.updateMemory(source.copy(relatedIds = srcRelated.encodeCsv()))
        memoryDAO.updateMemory(target.copy(relatedIds = tgtRelated.encodeCsv()))
        return source.copy(relatedIds = srcRelated.encodeCsv()).toAssistantMemory()
    }

    suspend fun addMemory(assistantId: String, content: String, title: String = "", sentiment: Double = 0.0, tags: List<String> = emptyList(), importance: Double = 0.3): AssistantMemory {
        val id = memoryDAO.insertMemory(
            MemoryEntity(
                assistantId = assistantId,
                content = content,
                title = title,
                importance = importance,
                sentiment = sentiment,
                tags = tags.encodeCsv(),
                createdAt = System.currentTimeMillis(),
                lastTriggeredAt = System.currentTimeMillis(),
                triggerCount = 1,
                isActive = true,
                isHabit = false,
                source = "ai",
                relatedIds = "",
            )
        ).toInt()
        return AssistantMemory(
            id = id,
            content = content,
            title = title,
            importance = importance,
            sentiment = sentiment,
            tags = tags,
            createdAt = System.currentTimeMillis(),
            lastTriggeredAt = System.currentTimeMillis(),
        )
    }

    suspend fun deleteMemory(id: Int) {
        memoryDAO.deleteMemory(id)
    }

    // ===== CSV 工具（Room 无 List TypeConverter，用逗号分隔字符串存储）=====
    private fun List<String>.encodeCsv(): String = joinToString(",")

    private fun String.decodeCsv(): List<String> =
        if (isBlank()) emptyList() else split(",").map { it.trim() }.filter { it.isNotEmpty() }
}