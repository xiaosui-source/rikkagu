package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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
 * 安全：复用 web_browse 的 SSRF 防护，禁止访问内网/回环地址。
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
            "Use when the user needs to call an API, test an endpoint, or fetch data from a service. " +
            "Public network works by default. For intranet/loopback addresses, set allow_intranet=true and " +
            "explain the purpose to gain access (default blocked).",
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
                    putJsonObject("allow_intranet") {
                        put("type", "boolean")
                        put("description", "Optional. Set true to allow accessing intranet/loopback addresses (must also provide purpose)")
                    }
                    putJsonObject("purpose") {
                        put("type", "string")
                        put("description", "Optional. Explain the purpose of accessing an intranet address so it can be approved")
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
            val allowIntranet = params["allow_intranet"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
            val purpose = params["purpose"]?.jsonPrimitive?.contentOrNull

            // SSRF 防护：内网/回环地址默认拦截，按需放行（需 allow_intranet=true 且用途合理）
            val gate = intranetAccessGate(url, allowIntranet, purpose)
            if (gate != null) {
                return@Tool listOf(UIMessagePart.Text(gate))
            }

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

/** SSRF 防护：判断 URL 是否指向内网/回环地址 */
internal suspend fun isPrivateNetworkUrl(url: String): Boolean = withContext(Dispatchers.IO) {
    val host = runCatching { java.net.URI(url).host }.getOrNull()
    if (host == null) {
        // 无法解析 host 视为不安全
        return@withContext true
    }
    runCatching {
        val addr = java.net.InetAddress.getByName(host)
        val raw = addr.address
        addr.isLoopbackAddress ||
            addr.isSiteLocalAddress ||
            addr.isLinkLocalAddress ||
            (raw.size == 16 && (raw[0].toInt() and 0xfe) == 0xfc) || // IPv6 ULA fc00::/7
            (raw.size == 4 && raw[0].toInt() == 169 && raw[1].toInt() == 254) // 169.254.x.x
    }.getOrDefault(true)
}

/**
 * 内网访问「按需放行」门控。
 *
 * 规则：
 *  - 目标是公网地址 → 直接放行（返回 null）。
 *  - 目标是内网/回环地址，且未声明放行（allowIntranet=false）→ 拦截，提示需声明用途。
 *  - 目标是内网地址、已声明放行但未给用途 → 拦截，提示需说明用途。
 *  - 目标是内网地址、已放行且有用途，但用途命中安全敏感特征（漏洞利用等）→ 拦截。
 *  - 其余（内网 + 放行 + 合理用途）→ 放行（返回 null）。
 *
 * @return null=允许访问；非 null=拦截原因文本。
 */
internal suspend fun intranetAccessGate(
    url: String,
    allowIntranet: Boolean,
    purpose: String?,
): String? {
    val isPrivate = isPrivateNetworkUrl(url)
    if (!isPrivate) return null // 公网，放行

    if (!allowIntranet) {
        return "目标地址为内网/回环地址，默认已拦截。如确需访问，请传 allow_intranet=true 并说明用途(purpose)。"
    }
    val p = purpose?.trim().orEmpty()
    if (p.isBlank()) {
        return "已请求放行内网访问，但缺少用途说明(purpose)。请说明访问用途再重试。"
    }
    // 安全敏感特征词（疑似漏洞利用/攻击），命中即拦截
    val suspicious = listOf(
        "注入", "injection", "sql", "shell", "反弹", "反连", "connectback", "爆破",
        "暴力破解", "提权", "privilege", "越权", "escalation", "webshell", "cmd.exe",
        "reverse", "exploit", "漏洞利用", "bypass", "绕过", "scan", "扫描", "bruteforce",
        "密码", "password", "admin破解", "getshell", "payload",
    )
    if (suspicious.any { p.contains(it, ignoreCase = true) }) {
        return "内网访问用途「$p」命中安全敏感特征，已拦截。"
    }
    return null // 合理用途，放行
}
