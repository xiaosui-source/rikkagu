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

/** 内置 Files MCP：简单的路径读写工具（封装 File 操作，AI 可直接使用） */
fun buildFilesMcpTools(): List<Tool> = listOf(
    Tool(
        name = "file_read",
        description = "Read a text file from the device filesystem. Path is absolute or relative to the app workspace.",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "file path") })
                },
                required = listOf("path")
            )
        },
        execute = { args ->
            val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
            val f = File(path)
            if (!f.exists()) return@Tool listOf(UIMessagePart.Text("{\"error\":\"file not found\"}"))
            val content = runCatching { f.readText().take(6000) }.getOrDefault("")
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true); put("path", path); put("content", content)
            }.toString()))
        },
    ),
    Tool(
        name = "file_write",
        description = "Write text content to a file.",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "file path") })
                    put("content", buildJsonObject { put("type", "string"); put("description", "content") })
                },
                required = listOf("path", "content")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val path = o["path"]?.jsonPrimitive?.contentOrNull ?: error("path required")
            val content = o["content"]?.jsonPrimitive?.contentOrNull ?: ""
            val f = File(path)
            f.parentFile?.mkdirs()
            runCatching { f.writeText(content) }.onFailure { return@Tool listOf(UIMessagePart.Text("{\"error\":\"${it.message}\"}")) }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true); put("path", path); put("bytes", content.length)
            }.toString()))
        },
    ),
    Tool(
        name = "file_list",
        description = "List files in a directory.",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "directory path") })
                },
                required = listOf("path")
            )
        },
        execute = { args ->
            val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: "."
            val f = File(path)
            if (!f.isDirectory) return@Tool listOf(UIMessagePart.Text("{\"error\":\"not a directory\"}"))
            val names = f.listFiles()?.map { if (it.isDirectory) it.name + "/" else it.name } ?: emptyList()
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true); put("path", path); put("files", JsonArray(names.map { JsonPrimitive(it) }))
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
