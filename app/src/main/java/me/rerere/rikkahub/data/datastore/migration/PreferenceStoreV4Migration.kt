/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant

/**
 * V4 迁移：为存量助手的 localTools 补充「屏幕自动化」(screen_automation)。
 *
 * 背景：默认助手已默认启用 ScreenAutomation（配合无障碍代下棋等场景），
 * 但已保存的助手配置没有该选项，导致 AI 缺少 take_screenshot/tap/find_node 工具。
 * 本迁移幂等：仅当 localTools 数组中不存在 {"type":"screen_automation"} 时追加。
 */
class PreferenceStoreV4Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 4
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        val assistantsJson = prefs[SettingsStore.ASSISTANTS] ?: "[]"
        val migrated = runCatching {
            val arr = JsonInstant.parseToJsonElement(assistantsJson).jsonArray
            val newArr = JsonArray(
                arr.map { el ->
                    val obj = el.jsonObject
                    val localTools = obj["localTools"]?.jsonArray ?: JsonArray(emptyList())
                    val hasScreenAutomation = localTools.any { tool ->
                        runCatching {
                            tool.jsonObject["type"]?.jsonPrimitive?.content == "screen_automation"
                        }.getOrDefault(false)
                    }
                    if (hasScreenAutomation) {
                        obj
                    } else {
                        JsonObject(
                            obj.toMutableMap().apply {
                                put(
                                    "localTools",
                                    JsonArray(
                                        localTools + JsonObject(
                                            mapOf("type" to JsonPrimitive("screen_automation"))
                                        )
                                    )
                                )
                            }
                        )
                    }
                }
            )
            JsonInstant.encodeToString(newArr)
        }.getOrDefault(assistantsJson)

        prefs[SettingsStore.ASSISTANTS] = migrated
        prefs[SettingsStore.VERSION] = 4
        return prefs
    }

    override suspend fun cleanUp() = Unit
}
