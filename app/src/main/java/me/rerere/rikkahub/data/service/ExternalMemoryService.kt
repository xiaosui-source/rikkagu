/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.service

import android.util.Log
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.ExternalMemory
import me.rerere.rikkahub.data.sync.webdav.WebDavClient
import org.koin.java.KoinJavaComponent

/**
 * 外置记忆库（进阶记忆）——WebDAV 实现
 *
 * 记忆数据存到用户配置的 WebDAV 服务器（设置 → 备份 → WebDAV），
 * 目录：<webdav_path>/external_memories/<记忆库id>/
 *   messages_<assistantId>.json   （聊天消息）
 *   summaries_<assistantId>.json   （日记/记忆摘要）
 *
 * 真正"外置"，多设备共享，无需 Supabase。
 */
class ExternalMemoryService(
    private val config: ExternalMemory,
) {
    companion object {
        private const val TAG = "ExternalMemoryService"
        private val JSON = Json { ignoreUnknownKeys = true }
    }

    private val settingsStore: SettingsStore by lazy {
        KoinJavaComponent.getKoin().get()
    }
    private val httpClient: HttpClient by lazy {
        KoinJavaComponent.getKoin().get()
    }

    private fun davConfig() = settingsStore.settingsFlow.value.webDavConfig

    private fun baseDir(): String {
        val cfg = davConfig()
        val root = cfg.path.trim('/')
        val sub = config.webdavPath.ifBlank { "external_memories/${config.id}" }.trim('/')
        return if (root.isEmpty()) sub else "$root/$sub"
    }

    private fun messagesPath(assistantId: String) = "${baseDir()}/messages_$assistantId.json"
    private fun summariesPath(assistantId: String) = "${baseDir()}/summaries_$assistantId.json"

    private fun client(): WebDavClient = WebDavClient(davConfig(), httpClient)

    private fun parseMessages(jsonText: String?): List<ExternalMemoryMessage> =
        if (jsonText.isNullOrBlank()) emptyList()
        else runCatching {
            JSON.decodeFromString(ListSerializer(ExternalMemoryMessage.serializer()), jsonText)
        }.getOrDefault(emptyList())

    private fun parseSummaries(jsonText: String?): List<ExternalMemorySummary> =
        if (jsonText.isNullOrBlank()) emptyList()
        else runCatching {
            JSON.decodeFromString(ListSerializer(ExternalMemorySummary.serializer()), jsonText)
        }.getOrDefault(emptyList())

    private fun encodeMessages(list: List<ExternalMemoryMessage>): String =
        JSON.encodeToString(ListSerializer(ExternalMemoryMessage.serializer()), list)

    private fun encodeSummaries(list: List<ExternalMemorySummary>): String =
        JSON.encodeToString(ListSerializer(ExternalMemorySummary.serializer()), list)

    private suspend fun readRemote(path: String): String? {
        val result = client().get(path)
        return result.getOrNull()?.toString(Charsets.UTF_8)
    }

    private suspend fun writeRemote(path: String, content: String): Boolean {
        runCatching { client().ensureCollectionExists(baseDir()) }
        return client().put(path, content.toByteArray(Charsets.UTF_8), "application/json").isSuccess
    }

    /**
     * 保存聊天消息到外置记忆库（WebDAV）
     */
    suspend fun saveMessage(
        assistantId: String,
        conversationId: String,
        role: String,
        content: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val path = messagesPath(assistantId)
            val list = parseMessages(readRemote(path)).toMutableList()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            list.add(
                ExternalMemoryMessage(
                    id = list.size + 1,
                    assistantId = assistantId,
                    conversationId = conversationId,
                    role = role,
                    content = content,
                    createdAt = sdf.format(java.util.Date()),
                )
            )
            val ok = writeRemote(path, encodeMessages(list))
            if (!ok) throw Exception("WebDAV 写入失败: $path")
            Log.d(TAG, "Saved message to WebDAV memory for assistant $assistantId (total ${list.size})")
        }.map { }
    }

    /**
     * 查询最新 N 条消息
     */
    suspend fun queryLatestMessages(
        assistantId: String,
        limit: Int = 10,
    ): Result<List<ExternalMemoryMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            parseMessages(readRemote(messagesPath(assistantId))).takeLast(limit)
        }
    }

    /**
     * 关键词搜索消息
     */
    suspend fun searchMessages(
        assistantId: String,
        keyword: String,
        limit: Int = 10,
    ): Result<List<ExternalMemoryMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            parseMessages(readRemote(messagesPath(assistantId)))
                .filter { it.content.contains(keyword, ignoreCase = true) }
                .takeLast(limit)
        }
    }

    /**
     * 保存日记摘要到外置记忆库（WebDAV）
     */
    suspend fun saveDiarySummary(
        assistantId: String,
        content: String,
        embedding: List<Float>? = null,
        targetDate: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val path = summariesPath(assistantId)
            val list = parseSummaries(readRemote(path)).toMutableList()
            // 同一天已存在则覆盖，否则追加
            val existingIndex = list.indexOfLast { it.createdAt.startsWith(targetDate) }
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val summary = ExternalMemorySummary(
                id = list.size + 1,
                assistantId = assistantId,
                content = content,
                createdAt = sdf.format(java.util.Date()),
                embedding = embedding ?: emptyList(),
            )
            if (existingIndex >= 0) {
                list[existingIndex] = summary.copy(id = list[existingIndex].id)
            } else {
                list.add(summary)
            }
            val ok = writeRemote(path, encodeMessages(list))
            if (!ok) throw Exception("WebDAV 写入失败: $path")
            Log.d(TAG, "Saved diary summary to WebDAV memory for assistant $assistantId")
        }.map { }
    }

    /**
     * 按日期查询消息（全部助手）
     */
    suspend fun queryMessagesByDate(
        dateStr: String,
    ): Result<List<ExternalMemoryMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            // 列出 baseDir 下所有 messages_*.json 并过滤
            val resources = client().list(baseDir()).getOrNull().orEmpty()
            val files = resources.filter { it.displayName.startsWith("messages_") && it.displayName.endsWith(".json") }
            val all = files.flatMap { f ->
                parseMessages(readRemote("${baseDir()}/${f.displayName}"))
            }
            all.filter { it.createdAt.startsWith(dateStr) }
        }
    }

    /**
     * 按日期查询摘要
     */
    suspend fun querySummariesByDate(
        assistantId: String,
        dateStr: String,
    ): Result<List<ExternalMemorySummary>> = withContext(Dispatchers.IO) {
        runCatching {
            parseSummaries(readRemote(summariesPath(assistantId)))
                .filter { it.createdAt.startsWith(dateStr) }
        }
    }

    /**
     * 查询最新 N 条摘要
     */
    suspend fun queryLatestSummaries(
        assistantId: String,
        limit: Int = 5,
    ): Result<List<ExternalMemorySummary>> = withContext(Dispatchers.IO) {
        runCatching {
            parseSummaries(readRemote(summariesPath(assistantId))).takeLast(limit)
        }
    }

    /**
     * 查询全部摘要
     */
    suspend fun queryAllSummaries(
        assistantId: String,
    ): Result<List<ExternalMemorySummary>> = withContext(Dispatchers.IO) {
        runCatching {
            parseSummaries(readRemote(summariesPath(assistantId)))
        }
    }

    /**
     * 向量召回摘要（余弦相似度）
     */
    suspend fun vectorRecallSummaries(
        queryEmbedding: List<Float>,
        assistantId: String,
        count: Int = 5,
    ): Result<List<ExternalMemorySummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val all = parseSummaries(readRemote(summariesPath(assistantId)))
            if (queryEmbedding.isEmpty() || all.isEmpty()) return@runCatching all.takeLast(count)
            all.sortedByDescending { summary ->
                cosineSimilarity(queryEmbedding, summary.embedding)
            }.take(count)
        }
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    }
}

@Serializable
data class ExternalMemoryMessage(
    val id: Int = 0,
    val assistantId: String = "",
    val conversationId: String = "",
    val role: String = "",
    val content: String = "",
    val createdAt: String = "",
)

@Serializable
data class ExternalMemorySummary(
    val id: Int = 0,
    val assistantId: String = "",
    val content: String = "",
    val createdAt: String = "",
    val embedding: List<Float> = emptyList(),
)
