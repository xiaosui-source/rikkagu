/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 本文件由 APK 反编译逆向还原（ApiExplorer：自动发现网页/JS 中的 API 接口）
 */

package me.rerere.rikkahub.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * 发现到的 API 端点.
 */
data class ApiEndpoint(
    val method: String,
    val url: String,
    val source: String,
    val status: Int?,
    val responsePreview: String,
    val contentType: String,
    val isJson: Boolean,
)

/**
 * 一次页面探索的结果.
 */
data class ApiExploreResult(
    val pageUrl: String,
    val pageTitle: String,
    val endpoints: List<ApiEndpoint>,
    val pageTextPreview: String,
    val error: String,
    val externalScripts: Int,
)

/**
 * API 探索器：输入一个网址，抓取页面并自动发现其中的 API 接口.
 */
object ApiExplorer {
    private val pool = Executors.newFixedThreadPool(4)

    private val FORM_REGEX = Pattern.compile("(?is)<form[^>]*action=[\"']([^\"']+)[\"']")
    private val SCRIPT_REGEX = Pattern.compile("(?is)<script.*?</script>")
    private val STYLE_REGEX = Pattern.compile("(?is)<style.*?</style>")
    private val TAG_REGEX = Pattern.compile("<[^>]+>")
    private val FETCH_REGEX = Pattern.compile("fetch\\s*\\(\\s*[\"'`]([^\"'`]+)[\"'`]([^)]*)\\)")
    private val AXIOS_REGEX = Pattern.compile("axios\\.(get|post|put|delete|patch)\\s*\\(\\s*[\"'`]([^\"'`]+)[\"'`]")
    private val JQUERY_REGEX = Pattern.compile("\\$\\s*\\.(get|post|ajax)\\s*\\(\\s*[\"'`]([^\"'`]+)[\"'`]")
    private val OPEN_REGEX = Pattern.compile("\\.open\\s*\\(\\s*[\"']([A-Za-z]+)[\"']\\s*,\\s*[\"'`]([^\"'`]+)[\"'`]")
    private val URL_PROP_REGEX =
        Pattern.compile("url\\s*:\\s*[\"'`]([^\"'`]+)[\"'`][^}]*?(?:type|method)\\s*:\\s*[\"']([A-Za-z]+)[\"']")
    private val METHOD_PROP_REGEX = Pattern.compile("method\\s*:\\s*[\"']([A-Za-z]+)[\"']")
    private val ASSET_REGEX = Pattern.compile("\\.(js|css|png|jpg|jpeg|gif|svg|webp|ico|woff2?|ttf|map|txt|html?)$")
    private val API_KEYWORD = "(api|json|data|feed|graphql|rest|service|rpc|search|query|list|detail|info|get|login|upload|download|token|auth|user|order|pay)"

    private val API_URL_REGEX = Pattern.compile("[\"'`](https?://[a-zA-Z0-9._\\-/]*(?:api|json|data|feed|graphql|rest|service|rpc)[a-zA-Z0-9_\\-/{}?=&]*)[\"'`]")
    private val API_PATH_REGEX = Pattern.compile("[\"'`](/api/[a-zA-Z0-9_\\-./{}?=&]+)[\"'`]")
    private val API_VPATH_REGEX = Pattern.compile("[\"'`](/v\\d+/[a-zA-Z0-9_\\-./{}?=&]+)[\"'`]")
    private val API_QUERY_REGEX =
        Pattern.compile("[\"'`](/[a-zA-Z0-9_\\-/]*(?:api|json|data|feed|graphql|rest|service|rpc)[a-zA-Z0-9_\\-/{}?=&]*|\\?[a-zA-Z0-9_\\-=&#]+)[\"'`]")
    private val API_GENERIC_REGEX = Pattern.compile("[\"'`](/[a-zA-Z0-9_\\-/]*(?:api|json|data|feed|graphql)[a-zA-Z0-9_\\-/]*|https?://[a-zA-Z0-9._\\-/]*(?:api|json|data|feed|graphql)[a-zA-Z0-9_\\-/]*)[\"'`]")

    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0"

    /**
     * 抓取并分析一个页面，返回发现的 API 端点.
     */
    suspend fun explore(url: String, timeout: Int): ApiExploreResult = withContext(Dispatchers.IO) {
        // 验证 URL 合法性
        val trimmed = url.trim()
        if (trimmed.isBlank() || !trimmed.contains("://") || trimmed.substringAfter("://").isBlank()) {
            return@withContext ApiExploreResult(
                pageUrl = trimmed,
                pageTitle = "",
                endpoints = emptyList(),
                pageTextPreview = "",
                error = "请输入正确的网址（以 http:// 或 https:// 开头，如 https://example.com）",
                externalScripts = 0,
            )
        }
        val (status, body) = httpGet(trimmed, timeout)
        if (status !in 200..299) {
            return@withContext ApiExploreResult(
                pageUrl = url,
                pageTitle = "",
                endpoints = emptyList(),
                pageTextPreview = "",
                error = "HTTP $status",
                externalScripts = 0,
            )
        }

        val base = normalizeBase(url)
        val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(body)?.groupValues?.getOrNull(1)?.trim() ?: ""
        val scripts = Regex("(?is)<script[^>]*src=[\"']([^\"']+)[\"']").findAll(body).count()

        val endpoints = extractFromHtml(body, base, url)
        ApiExploreResult(
            pageUrl = url,
            pageTitle = title,
            endpoints = endpoints,
            pageTextPreview = stripHtml(body).trim().take(500),
            error = "",
            externalScripts = scripts,
        )
    }

    private fun extractFromHtml(html: String, base: String, pageUrl: String): List<ApiEndpoint> {
        val result = mutableListOf<ApiEndpoint>()

        // 1. 表单 action
        FORM_REGEX.matcher(html).let { m ->
            while (m.find()) {
                val action = m.group(1) ?: continue
                result += ApiEndpoint(
                    method = "POST",
                    url = normalize(action, base),
                    source = "form",
                    status = null,
                    responsePreview = "",
                    contentType = "form",
                    isJson = false,
                )
            }
        }

        // 2. 内联 JS 中的 API 调用
        val jsBlocks = StringBuilder()
        SCRIPT_REGEX.matcher(html).let { m ->
            while (m.find()) jsBlocks.append(m.group()).append('\n')
        }
        result += extractFromJs(jsBlocks.toString(), base, pageUrl)

        return result.distinctBy { it.url + it.method }.toList()
    }

    private fun extractFromJs(js: String, base: String, pageUrl: String): List<ApiEndpoint> {
        val result = mutableListOf<ApiEndpoint>()

        fun addEndpoint(method: String, rawUrl: String, source: String) {
            val clean = rawUrl.trim().trim('"', '\'', '`')
            if (clean.isEmpty()) return
            if (clean.startsWith("javascript:") || clean.startsWith("data:") || clean.startsWith("mailto:")) return
            if (clean.startsWith("http") && !clean.startsWith("http://") && !clean.startsWith("https://")) return
            val normalized = normalize(clean, base)
            if (normalized == pageUrl) return
            if (ASSET_REGEX.matcher(normalized).find()) return
            if (result.any { it.url == normalized && it.method == method }) return
            result += ApiEndpoint(
                method = method,
                url = normalized,
                source = source,
                status = null,
                responsePreview = "",
                contentType = "",
                isJson = false,
            )
        }

        // fetch(...)
        FETCH_REGEX.matcher(js).let { m ->
            while (m.find()) {
                val url = m.group(1) ?: continue
                val opts = m.group(2) ?: ""
                val method = METHOD_PROP_REGEX.matcher(opts).let { if (it.find()) (it.group(1) ?: "GET").uppercase() else "GET" }
                addEndpoint(method, url, "fetch")
            }
        }
        // axios.get/post/...
        AXIOS_REGEX.matcher(js).let { m ->
            while (m.find()) addEndpoint((m.group(1) ?: "get").uppercase(), m.group(2) ?: continue, "axios")
        }
        // $.get/$.post/$.ajax
        JQUERY_REGEX.matcher(js).let { m ->
            while (m.find()) addEndpoint((m.group(1) ?: "get").uppercase(), m.group(2) ?: continue, "jquery")
        }
        // XMLHttpRequest.open(method, url)
        OPEN_REGEX.matcher(js).let { m ->
            while (m.find()) addEndpoint((m.group(1) ?: "GET").uppercase(), m.group(2) ?: continue, "xhr")
        }
        // { url: "...", type/method: "..." }
        URL_PROP_REGEX.matcher(js).let { m ->
            while (m.find()) addEndpoint((m.group(2) ?: "GET").uppercase(), m.group(1) ?: continue, "config")
        }
        // 通用 API 路径/URL 匹配
        listOf(API_URL_REGEX, API_PATH_REGEX, API_VPATH_REGEX, API_QUERY_REGEX, API_GENERIC_REGEX).forEach { re ->
            re.matcher(js).let { m ->
                while (m.find()) addEndpoint("GET", m.group(1) ?: continue, "url")
            }
        }

        return result
    }

    /**
     * 探测端点：逐个发起 GET 请求，获取状态码/响应预览.
     */
    private fun probeEndpoints(endpoints: List<ApiEndpoint>, timeout: Int): List<ApiEndpoint> {
        return endpoints.map { ep ->
            try {
                val (status, body) = httpGet(ep.url, timeout)
                val contentType = ""
                val isJson = body.trimStart().startsWith("{") || body.trimStart().startsWith("[")
                ep.copy(
                    status = status,
                    responsePreview = body.take(300),
                    contentType = contentType,
                    isJson = isJson,
                )
            } catch (e: Exception) {
                ep.copy(status = null, responsePreview = "")
            }
        }
    }

    private fun normalizeBase(url: String): String {
        return runCatching {
            val uri = URI(url)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return@runCatching url
            "$scheme://$host"
        }.getOrDefault(url)
    }

    private fun normalize(raw: String, base: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "$base$trimmed"
            else -> {
                val b = base.trimEnd('/')
                "$b/$trimmed"
            }
        }
    }

    private fun stripHtml(html: String): String {
        var text = SCRIPT_REGEX.matcher(html).replaceAll(" ")
        text = STYLE_REGEX.matcher(text).replaceAll(" ")
        text = TAG_REGEX.matcher(text).replaceAll(" ")
        return text.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * 简单 HTTP GET，返回 (状态码, 响应体).
     */
    private fun httpGet(url: String, timeout: Int): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/javascript,*/*")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to body
        } finally {
            connection.disconnect()
        }
    }
}
