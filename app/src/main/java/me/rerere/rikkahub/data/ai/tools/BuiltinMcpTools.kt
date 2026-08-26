package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** 内置 Memory MCP：会话内键值存储（进程级） */
private val memoryStore = ConcurrentHashMap<String, String>()

/** 内置 Memory MCP 工具：AI 可存取会话内临时键值数据。 */
fun buildMemoryMcpTools(): List<Tool> = listOf(
    Tool(
        name = "memory_set",
        description = "Store a key-value pair in the built-in memory MCP. Values persist during the session.",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("key", buildJsonObject { put("type", "string"); put("description", "key") })
                    put("value", buildJsonObject { put("type", "string"); put("description", "value") })
                },
                required = listOf("key", "value")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val key = o["key"]?.jsonPrimitive?.contentOrNull ?: error("key required")
            val value = o["value"]?.jsonPrimitive?.contentOrNull ?: ""
            memoryStore[key] = value
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true); put("key", key); put("value", value)
            }.toString()))
        },
    ),
    Tool(
        name = "memory_get",
        description = "Get a value from the built-in memory MCP by key.",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("key", buildJsonObject { put("type", "string"); put("description", "key") })
                },
                required = listOf("key")
            )
        },
        execute = { args ->
            val key = args.jsonObject["key"]?.jsonPrimitive?.contentOrNull ?: error("key required")
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true); put("key", key); put("value", memoryStore[key] ?: "")
            }.toString()))
        },
    ),
    Tool(
        name = "memory_delete",
        description = "Delete a key from the built-in memory MCP.",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("key", buildJsonObject { put("type", "string"); put("description", "key") })
                },
                required = listOf("key")
            )
        },
        execute = { args ->
            val key = args.jsonObject["key"]?.jsonPrimitive?.contentOrNull ?: error("key required")
            memoryStore.remove(key)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true); put("deleted", key)
            }.toString()))
        },
    ),
    Tool(
        name = "memory_list",
        description = "List all keys in the built-in memory MCP.",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
        execute = {
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("keys", JsonArray(memoryStore.keys.map { JsonPrimitive(it) }))
            }.toString()))
        },
    ),
)

/** 内置 MCP 服务器信息（用于 MCP 管理界面显示） */
data class BuiltinMcpServerInfo(
    val id: String,
    val name: String,
    val description: String,
    val toolCount: Int,
)
