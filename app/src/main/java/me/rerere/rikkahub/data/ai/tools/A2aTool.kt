/*
 * 灵犀 Lingxi
 * 参考自 Operit AI (https://github.com/AAswordman/Operit) 的 A2A 协议
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** A2A 任务对象（对齐 Operit A2aTaskManager / Agent Card 协议的 JSON 结构） */
@Serializable
data class A2aTask(
    @SerialName("id") val id: String = "",
    @SerialName("message") val message: String = "",
    @SerialName("status") val status: String = "submitted",
    @SerialName("artifact") val artifact: Map<String, String> = emptyMap(),
)

/**
 * A2A（Agent-to-Agent）工具（参考 Operit A2aHttpHandler）：
 * - a2a_send_task: 把一个任务以 JSON 发送到远程 Agent 的 A2A HTTP 端点，返回对方状态
 * - a2a_parse_message: 从一段文本中解析出可执行的任务描述（结构化）
 *
 * 协议：向远端 /a2a/tasks 发送 {"id","message","status"} JSON，符合 Agent Card / A2A 简化规范。
 */
fun createA2aTools(): List<Tool> {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    return listOf(
        Tool(
            name = "a2a_send_task",
            description = "Send a task to another AI agent via the A2A (Agent-to-Agent) HTTP protocol. " +
                "The remote endpoint should accept a JSON task {id, message, status}. " +
                "Use when delegating work to another agent service, or when the user points to an A2A endpoint.",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("endpoint", buildJsonObject {
                            put("type", "string")
                            put("description", "Remote A2A endpoint URL, e.g. https://host/a2a/tasks")
                        })
                        put("message", buildJsonObject {
                            put("type", "string")
                            put("description", "The task/message to send to the remote agent")
                        })
                    },
                    required = listOf("endpoint", "message"),
                )
            },
            execute = { input ->
                val endpoint = input.jsonObject["endpoint"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"endpoint required"}"""))
                val message = input.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"message required"}"""))
                val task = A2aTask(id = java.util.UUID.randomUUID().toString(), message = message)
                val json = kotlinx.serialization.json.Json.encodeToString(A2aTask.serializer(), task)
                val result = runCatching {
                    val request = Request.Builder()
                        .url(endpoint)
                        .post(json.toRequestBody("application/json".toMediaType()))
                        .header("Content-Type", "application/json")
                        .build()
                    client.newCall(request).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        buildJsonObject {
                            put("http_code", resp.code)
                            put("response", kotlinx.serialization.json.JsonPrimitive(body.take(2000)))
                        }.toString()
                    }
                }.getOrElse { e ->
                    buildJsonObject {
                        put("error", "A2A send failed: ${e.message}")
                    }.toString()
                }
                listOf(UIMessagePart.Text(result))
            }
        ),

        Tool(
            name = "a2a_parse_message",
            description = "Parse a raw message/task string into a structured A2A task (id, message, status). " +
                "Use to normalize an incoming agent message into a form that can be tracked or forwarded.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("text", buildJsonObject {
                            put("type", "string")
                            put("description", "The raw task text to parse")
                        })
                    },
                    required = listOf("text"),
                )
            },
            execute = { input ->
                val text = input.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val trimmed = text.trim()
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("id", java.util.UUID.randomUUID().toString())
                    put("message", trimmed.take(2000))
                    put("status", "submitted")
                    put("length", trimmed.length)
                }.toString()))
            }
        ),
    )
}