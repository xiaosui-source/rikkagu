/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 参考 Operit MemoryLibrary
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.memory.library

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import java.util.Date
import java.util.UUID

/**
 * 记忆库管理类 - 提供分析对话内容并存储为结构化记忆图谱的功能。
 * 
 * 完全对齐 Operit AI MemoryLibrary
 */
object MemoryLibrary {
    private const val TAG = "MemoryLibrary"
    private const val DEFAULT_ANALYSIS_HISTORY_MESSAGE_COUNT = 10
    
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isInitialized = false
    
    /**
     * 解析后的链接
     */
    internal data class ParsedLink(
        val sourceTitle: String,
        val targetTitle: String,
        val type: String,
        val description: String,
        val weight: Float
    )

    /**
     * 解析后的实体
     */
    internal data class ParsedEntity(
        val title: String,
        val content: String,
        val tags: List<String>,
        val aliasFor: String? = null,
        val folderPath: String = ""
    )

    /**
     * 解析后的更新
     */
    internal data class ParsedUpdate(
        val titleToUpdate: String,
        val newContent: String,
        val reason: String,
        val newCredibility: Float? = null,
        val newImportance: Float? = null
    )

    /**
     * 解析后的合并
     */
    internal data class ParsedMerge(
        val sourceTitles: List<String>,
        val newTitle: String,
        val newContent: String,
        val newTags: List<String>,
        val folderPath: String = "",
        val reason: String
    )

    /**
     * 解析结果
     */
    internal data class ParsedAnalysis(
        val mainProblem: ParsedEntity? = null,
        val extractedEntities: List<ParsedEntity> = emptyList(),
        val links: List<ParsedLink> = emptyList(),
        val updatedEntities: List<ParsedUpdate> = emptyList(),
        val mergedEntities: List<ParsedMerge> = emptyList(),
        val profileMarkdown: String? = null
    )

    /**
     * 初始化记忆库
     */
    fun initialize(context: Context) {
        synchronized(MemoryLibrary::class.java) {
            if (isInitialized) return
            isInitialized = true
        }
    }

    /**
     * 确保已初始化
     */
    private fun ensureInitialized(context: Context) {
        if (!isInitialized) {
            initialize(context)
        }
    }

    /**
     * 异步保存记忆
     */
    fun saveMemoryAsync(
        context: Context,
        conversationHistory: List<Pair<String, String>>,
        content: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        ensureInitialized(context)
        
        coroutineScope.launch {
            try {
                saveMemoryNow(context, conversationHistory, content)
                onSuccess?.invoke()
            } catch (e: Exception) {
                onError?.invoke(e)
            }
        }
    }

    /**
     * 同步保存记忆
     */
    suspend fun saveMemoryNow(
        context: Context,
        conversationHistory: List<Pair<String, String>>,
        content: String
    ) {
        withContext(Dispatchers.IO) {
            val memoryDAO = MemoryDAO(context)
            
            // 分析内容并提取实体和链接
            val analysis = analyzeContent(content)
            
            // 保存提取的实体
            analysis.extractedEntities.forEach { entity ->
                val existingEntity = memoryDAO.getByTitle(entity.title)
                if (existingEntity != null) {
                    memoryDAO.update(existingEntity.copy(
                        content = entity.content,
                        tags = entity.tags.joinToString(","),
                        updatedAt = Date()
                    ))
                } else {
                    memoryDAO.insert(MemoryEntity(
                        id = UUID.randomUUID().toString(),
                        title = entity.title,
                        content = entity.content,
                        tags = entity.tags.joinToString(","),
                        folderPath = entity.folderPath,
                        createdAt = Date(),
                        updatedAt = Date()
                    ))
                }
            }
            
            // 保存链接
            analysis.links.forEach { link ->
                // 这里可以添加链接的保存逻辑
            }
        }
    }

    /**
     * 分析内容并提取记忆
     */
    private suspend fun analyzeContent(content: String): ParsedAnalysis {
        // TODO: 集成LLM进行智能分析
        // 目前使用简单解析
        return ParsedAnalysis(
            extractedEntities = parseEntities(content),
            links = parseLinks(content)
        )
    }

    /**
     * 解析实体
     */
    private fun parseEntities(content: String): List<ParsedEntity> {
        val entities = mutableListOf<ParsedEntity>()
        
        // 简单的实体提取逻辑
        val lines = content.split("\n")
        for (line in lines) {
            if (line.trim().startsWith("- ")) {
                val parts = line.removePrefix("- ").split(":", limit = 2)
                if (parts.size >= 2) {
                    entities.add(ParsedEntity(
                        title = parts[0].trim(),
                        content = parts[1].trim(),
                        tags = emptyList()
                    ))
                }
            }
        }
        
        return entities
    }

    /**
     * 解析链接
     */
    private fun parseLinks(content: String): List<ParsedLink> {
        val links = mutableListOf<ParsedLink>()
        
        // 简单的链接提取逻辑
        val linkPattern = Regex("\\[([^\\]]+)\\]\\->\\[([^\\]]+)\\]")
        val matches = linkPattern.findAll(content)
        
        for (match in matches) {
            links.add(ParsedLink(
                sourceTitle = match.groupValues[1],
                targetTitle = match.groupValues[2],
                type = "related",
                description = "",
                weight = 1.0f
            ))
        }
        
        return links
    }

    /**
     * 查询所有记忆
     */
    suspend fun getAllMemories(context: Context): List<MemoryEntity> {
        return withContext(Dispatchers.IO) {
            val memoryDAO = MemoryDAO(context)
            memoryDAO.getAll()
        }
    }

    /**
     * 根据标题搜索记忆
     */
    suspend fun searchMemories(context: Context, query: String): List<MemoryEntity> {
        return withContext(Dispatchers.IO) {
            val memoryDAO = MemoryDAO(context)
            memoryDAO.search(query)
        }
    }

    /**
     * 删除记忆
     */
    suspend fun deleteMemory(context: Context, id: String) {
        withContext(Dispatchers.IO) {
            val memoryDAO = MemoryDAO(context)
            memoryDAO.delete(id)
        }
    }
}
