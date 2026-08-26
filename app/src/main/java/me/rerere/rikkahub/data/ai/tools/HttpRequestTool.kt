package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 自定义 HTTP 请求工具（#602）：AI 可发送 GET/POST 请求（携带 headers/body）。
 *
 * 内网/回环地址已完全放开（用户要求），不做 SSRF 拦截。
 */
fun createHttpPostTool(context: Context): Tool {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    return Tool(
        name = "http_request",
        description = "Send a custom HTTP request (GET or POST) to a URL with optional headers and body. " +
            "Use when the user needs to call an API, test an endpoint, fetch data from a service, or " +
            "access intranet/loopback (LAN) addresses. Internal network access is already allowed.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("method") {
                        put("type", "string")
                        put("description", "HTTP method: GET or POST (default GET)")
                    }
                    putJsonObject("url") {
                        put("type", "string")
                        put("description", "Target URL")
                    }
                    putJsonObject("headers") {
                        put("type", "string")
                        put("description", "Optional JSON object of headers, e.g. {\"Authorization\":\"Bearer xxx\"}")
                    }
                    putJsonObject("body") {
                        put("type", "string")
                        put("description", "Request body for POST (raw string)")
                    }
                },
                required = listOf("url")
            )
        },
        execute = { args ->
            val params = args.jsonObject
            val url = params["url"]?.jsonPrimitive?.contentOrNull ?: error("url is required")
            val method = params["method"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "GET"
            val headersRaw = params["headers"]?.jsonPrimitive?.contentOrNull
            val body = params["body"]?.jsonPrimitive?.contentOrNull

            // 内网已完全放开，不做拦截
            val requestBuilder = Request.Builder().url(url)
            if (!headersRaw.isNullOrBlank()) {
                runCatching {
                    val headersObj = me.rerere.rikkahub.utils.JsonInstant.parseToJsonElement(headersRaw).jsonObject
                    headersObj.forEach { (k, v) ->
                        requestBuilder.header(k, v.jsonPrimitive.contentOrNull ?: "")
                    }
                }
            }

            if (method == "POST") {
                val mediaType = "application/json".toMediaType()
                requestBuilder.post((body ?: "").toRequestBody(mediaType))
            } else {
                requestBuilder.get()
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val code = response.code
            val responseBody = response.body?.string()?.take(4000) ?: ""
            response.close()

            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", code in 200..299)
                    put("status", code)
                    put("body", JsonPrimitive(responseBody))
                }.toString()
            ))
        },
    )
}