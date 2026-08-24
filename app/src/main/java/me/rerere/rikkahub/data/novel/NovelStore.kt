/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.novel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 小说角色扮演场景存储（独立 JSON 文件）
 */
class NovelStore(
    context: Context,
    private val json: Json,
) {
    private val file = File(context.filesDir, "novel_scenes.json")

    private val _scenes = MutableStateFlow(load())
    val scenes: StateFlow<List<NovelScene>> = _scenes

    private fun load(): List<NovelScene> = runCatching {
        if (file.exists()) {
            json.decodeFromString(ListSerializer(NovelScene.serializer()), file.readText())
        } else emptyList()
    }.getOrDefault(emptyList())

    private suspend fun persist(list: List<NovelScene>) = withContext(Dispatchers.IO) {
        runCatching {
            file.writeText(json.encodeToString(ListSerializer(NovelScene.serializer()), list))
        }
        _scenes.value = list
    }

    suspend fun add(scene: NovelScene) {
        persist(_scenes.value + scene)
    }

    suspend fun update(scene: NovelScene) {
        persist(_scenes.value.map { if (it.id == scene.id) scene else it })
    }

    suspend fun remove(id: String) {
        persist(_scenes.value.filterNot { it.id == id })
    }

    fun get(id: String): NovelScene? = _scenes.value.firstOrNull { it.id == id }
}
