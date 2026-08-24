/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.agents

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 智能体配置存储（独立 JSON 文件，不侵入主 Settings DataStore schema）
 */
class AgentStore(
    context: Context,
    private val json: Json,
) {
    private val file = File(context.filesDir, "agents.json")

    private val _agents = MutableStateFlow(load())
    val agents: StateFlow<List<AgentProfile>> = _agents

    private fun load(): List<AgentProfile> = runCatching {
        if (file.exists()) {
            json.decodeFromString(ListSerializer(AgentProfile.serializer()), file.readText())
        } else emptyList()
    }.getOrDefault(emptyList())

    private suspend fun persist(list: List<AgentProfile>) = withContext(Dispatchers.IO) {
        runCatching {
            file.writeText(json.encodeToString(ListSerializer(AgentProfile.serializer()), list))
        }
        _agents.value = list
    }

    suspend fun add(agent: AgentProfile) {
        persist(_agents.value + agent)
    }

    suspend fun update(agent: AgentProfile) {
        persist(_agents.value.map { if (it.id == agent.id) agent else it })
    }

    suspend fun remove(id: String) {
        persist(_agents.value.filterNot { it.id == id })
    }

    fun get(id: String): AgentProfile? = _agents.value.firstOrNull { it.id == id }
}
