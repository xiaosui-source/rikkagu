/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * PentAGI MCP —— 纯本地渗透测试工具集（移植自 vxcontrol/pentagi 概念）。
 *
 * 设计约束（用户要求）：
 * - 不用 API：不调用任何 AI API / 外部服务 API
 * - 不用本地模型：不依赖任何本地 LLM
 * - 不用工作区：不依赖 workspace 命令执行
 *
 * 全部工具使用纯 Kotlin + Java 标准库在应用内直接运行：
 * - HTTP 请求：OkHttp
 * - 端口扫描：java.net.Socket
 * - DNS / IP：java.net.InetAddress
 * - 分析：纯算法
 */
private val pentagiHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .followRedirects(false)
    .build()

private const val PENTAGI_UA = "Mozilla/5.0 (Linux; Android 14) RikkaHub-PentAGI/1.0"

private data class HttpResp(val code: Int, val headers: Map<String, List<String>>, val body: String)

private suspend fun httpGet(url: String, timeoutMs: Long = 8000): HttpResp? = withContext(Dispatchers.IO) {
    runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", PENTAGI_UA)
            .header("Accept", "*/*")
            .get()
            .build()
        pentagiHttpClient.newCall(req).execute().use { resp ->
            val headers = resp.headers.toMultimap()
            val body = resp.body?.string()?.take(20000) ?: ""
            HttpResp(resp.code, headers, body)
        }
    }.getOrNull()
}

private fun normalizeTarget(raw: String): String {
    var t = raw.trim()
    if (!t.startsWith("http://") && !t.startsWith("https://")) t = "https://$t"
    return t.trimEnd('/')
}

private fun parseHost(url: String): String {
    return runCatching {
        val u = java.net.URI(url)
        u.host ?: ""
    }.getOrDefault("")
}

private fun schemeOf(url: String): String = if (url.startsWith("https")) "https" else "http"

fun buildPentagiMcpTools(): List<Tool> = listOf(
    // ========== 1. URL 侦察 ==========
    Tool(
        name = "pentagi_url_recon",
        description = "PentAGI — URL 侦察：解析目标 URL 的协议/主机/端口/路径/查询参数，提取攻击面关键信息（参数列表、路径结构、是否 HTTPS）。纯本地，不联网。Params: url",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("url", buildJsonObject { put("type", "string"); put("description", "目标 URL，如 https://example.com/path?a=1&b=2") })
            }, required = listOf("url"))
        },
        execute = { args ->
            val o = args.jsonObject
            val raw = o["url"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"url required"}"""))
            val url = normalizeTarget(raw)
            val host = parseHost(url)
            val uri = runCatching { java.net.URI(url) }.getOrNull()
            val result = buildJsonObject {
                put("url", url)
                put("host", host)
                put("scheme", uri?.scheme ?: schemeOf(url))
                put("port", uri?.port ?: if (url.startsWith("https")) 443 else 80)
                put("path", uri?.path ?: "/")
                put("query", uri?.rawQuery ?: "")
                put("fragment", uri?.fragment ?: "")
                put("is_https", url.startsWith("https"))
                // 提取参数
                val params = uri?.rawQuery?.split("&")?.mapNotNull { kv ->
                    val idx = kv.indexOf("=")
                    if (idx > 0) kv.substring(0, idx) else if (kv.isNotBlank()) kv else null
                }?.distinct() ?: emptyList()
                put("parameters", kotlinx.serialization.json.JsonArray(params.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                put("parameter_count", params.size)
            }
            listOf(UIMessagePart.Text(result.toString()))
        },
    ),

    // ========== 2. DNS / IP 查询 ==========
    Tool(
        name = "pentagi_dns_lookup",
        description = "PentAGI — DNS/IP 查询：解析域名对应的 IPv4/IPv6 地址（使用系统 DNS）。纯本地。Params: host",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("host", buildJsonObject { put("type", "string"); put("description", "域名或主机名，如 example.com") })
            }, required = listOf("host"))
        },
        execute = { args ->
            val o = args.jsonObject
            val host = o["host"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"host required"}"""))
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val addrs = InetAddress.getAllByName(host)
                    buildJsonObject {
                        put("host", host)
                        put("ip_count", addrs.size)
                        put("addresses", kotlinx.serialization.json.JsonArray(addrs.map { kotlinx.serialization.json.JsonPrimitive(it.hostAddress ?: "") }))
                        put("canonical_name", addrs.firstOrNull()?.canonicalHostName ?: "")
                    }
                }.getOrElse { e ->
                    buildJsonObject {
                        put("host", host)
                        put("error", e.message ?: "DNS 解析失败")
                    }
                }
            }
            listOf(UIMessagePart.Text(result.toString()))
        },
    ),

    // ========== 3. 端口扫描 ==========
    Tool(
        name = "pentagi_port_scan",
        description = "PentAGI — 端口扫描：对目标主机常见端口（21/22/23/25/53/80/110/143/443/445/993/995/1433/1521/3306/3389/5432/6379/8080/8443/8888/9000/27017）做 TCP 连接探测。纯本地 socket。Params: host, optional ports(逗号分隔)",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("host", buildJsonObject { put("type", "string"); put("description", "目标主机名或 IP") })
                put("ports", buildJsonObject { put("type", "string"); put("description", "可选：自定义端口，逗号分隔，如 80,443,8080") })
            }, required = listOf("host"))
        },
        execute = { args ->
            val o = args.jsonObject
            val host = o["host"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"host required"}"""))
            val defaultPorts = listOf(21, 22, 23, 25, 53, 80, 110, 143, 443, 445, 993, 995, 1433, 1521, 3306, 3389, 5432, 6379, 8080, 8443, 8888, 9000, 27017)
            val ports = o["ports"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
                ?: defaultPorts

            val openPorts = mutableListOf<Pair<Int, String>>()
            val portNames = mapOf(
                21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS", 80 to "HTTP",
                110 to "POP3", 143 to "IMAP", 443 to "HTTPS", 445 to "SMB", 993 to "IMAPS",
                995 to "POP3S", 1433 to "MSSQL", 1521 to "Oracle", 3306 to "MySQL", 3389 to "RDP",
                5432 to "PostgreSQL", 6379 to "Redis", 8080 to "HTTP-Alt", 8443 to "HTTPS-Alt",
                8888 to "HTTP-Alt2", 9000 to "Portainer/HTTP", 27017 to "MongoDB",
            )

            withContext(Dispatchers.IO) {
                ports.forEach { port ->
                    runCatching {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(host, port), 1500)
                        socket.close()
                        openPorts.add(port to (portNames[port] ?: "unknown"))
                    }
                }
            }

            listOf(UIMessagePart.Text(buildJsonObject {
                put("host", host)
                put("scanned", ports.size)
                put("open_count", openPorts.size)
                put("open_ports", kotlinx.serialization.json.JsonArray(openPorts.map { (p, n) ->
                    kotlinx.serialization.json.buildJsonObject {
                        put("port", p)
                        put("service", n)
                    }
                }))
            }.toString()))
        },
    ),

    // ========== 4. HTTP 探测 ==========
    Tool(
        name = "pentagi_http_probe",
        description = "PentAGI — HTTP 探测：向目标发送 GET 请求，获取状态码、响应头（Server/X-Powered-By/Set-Cookie 等）和页面片段。纯本地 HTTP。Params: url, optional method(GET/HEAD)",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("url", buildJsonObject { put("type", "string"); put("description", "目标 URL") })
                put("method", buildJsonObject { put("type", "string"); put("description", "可选：GET 或 HEAD，默认 GET") })
            }, required = listOf("url"))
        },
        execute = { args ->
            val o = args.jsonObject
            val raw = o["url"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"url required"}"""))
            val url = normalizeTarget(raw)
            val method = o["method"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "GET"

            val resp = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", PENTAGI_UA)
                        .method(if (method == "HEAD") "HEAD" else "GET", null)
                        .build()
                    pentagiHttpClient.newCall(req).execute().use { r ->
                        val headers = r.headers.toMultimap()
                        val body = if (method == "HEAD") "" else (r.body?.string()?.take(5000) ?: "")
                        buildJsonObject {
                            put("url", url)
                            put("status_code", r.code)
                            put("server", headers["Server"]?.firstOrNull() ?: "")
                            put("powered_by", headers["X-Powered-By"]?.firstOrNull() ?: "")
                            put("content_type", headers["Content-Type"]?.firstOrNull() ?: "")
                            put("set_cookie", headers["Set-Cookie"]?.firstOrNull() ?: "")
                            put("content_length", headers["Content-Length"]?.firstOrNull() ?: "")
                            put("body_preview", body.take(500))
                        }
                    }
                }.getOrElse { e ->
                    buildJsonObject {
                        put("url", url)
                        put("error", e.message ?: "请求失败")
                    }
                }
            }
            listOf(UIMessagePart.Text(resp.toString()))
        },
    ),

    // ========== 5. 安全头审计 ==========
    Tool(
        name = "pentagi_security_headers_audit",
        description = "PentAGI — 安全响应头审计：检查目标站点的安全头（CSP/X-Frame-Options/HSTS/X-Content-Type-Options/Referrer-Policy/Permissions-Policy 等）是否缺失或配置不当。纯本地 HTTP。Params: url",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("url", buildJsonObject { put("type", "string"); put("description", "目标 URL") })
            }, required = listOf("url"))
        },
        execute = { args ->
            val o = args.jsonObject
            val raw = o["url"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"url required"}"""))
            val url = normalizeTarget(raw)
            val resp = httpGet(url)
            if (resp == null) {
                return@Tool listOf(UIMessagePart.Text("""{"error":"无法连接 $url"}"""))
            }

            val checks = mapOf(
                "Content-Security-Policy" to "缺少 CSP，存在 XSS 风险",
                "X-Frame-Options" to "缺少 X-Frame-Options，存在点击劫持风险",
                "Strict-Transport-Security" to "缺少 HSTS（仅 HTTPS 站点建议开启）",
                "X-Content-Type-Options" to "缺少 X-Content-Type-Options，存在 MIME 嗅探风险",
                "Referrer-Policy" to "缺少 Referrer-Policy，可能泄露来源信息",
                "Permissions-Policy" to "缺少 Permissions-Policy，未限制浏览器特性",
            )

            val findings = mutableListOf<kotlinx.serialization.json.JsonObject>()
            checks.forEach { (header, risk) ->
                val value = resp.headers[header]?.firstOrNull()
                findings.add(buildJsonObject {
                    put("header", header)
                    put("present", value != null)
                    put("value", value ?: "")
                    put("risk", if (value == null) risk else "已配置")
                })
            }

            listOf(UIMessagePart.Text(buildJsonObject {
                put("url", url)
                put("status_code", resp.code)
                put("findings", kotlinx.serialization.json.JsonArray(findings))
                put("missing_count", findings.count { it["present"]?.jsonPrimitive?.contentOrNull == "false" || it["present"] == null })
            }.toString()))
        },
    ),

    // ========== 6. 敏感路径扫描 ==========
    Tool(
        name = "pentagi_common_paths_scan",
        description = "PentAGI — 敏感路径扫描：探测常见敏感路径/文件（/.git/HEAD、/.env、/admin、/api、/robots.txt、/wp-admin 等），检测是否存在泄露。纯本地 HTTP。Params: base_url(如 https://example.com)",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("base_url", buildJsonObject { put("type", "string"); put("description", "目标基础 URL，如 https://example.com") })
            }, required = listOf("base_url"))
        },
        execute = { args ->
            val o = args.jsonObject
            val raw = o["base_url"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"base_url required"}"""))
            val base = normalizeTarget(raw)

            val paths = listOf(
                "/robots.txt", "/sitemap.xml", "/.git/HEAD", "/.git/config", "/.env", "/.env.local",
                "/admin", "/admin/", "/api", "/api/", "/login", "/wp-admin", "/wp-login.php",
                "/config", "/config.php", "/backup", "/backup.zip", "/phpinfo.php", "/info.php",
                "/server-status", "/.htaccess", "/.DS_Store", "/web.config", "/crossdomain.xml",
                "/swagger", "/swagger-ui.html", "/v1", "/health", "/actuator", "/actuator/health",
            )

            val results = mutableListOf<kotlinx.serialization.json.JsonObject>()
            withContext(Dispatchers.IO) {
                paths.forEach { path ->
                    val url = base + path
                    runCatching {
                        val req = Request.Builder().url(url).header("User-Agent", PENTAGI_UA).get().build()
                        pentagiHttpClient.newCall(req).execute().use { r ->
                            val body = r.body?.string()?.take(200) ?: ""
                            val interesting = r.code != 404 && r.code != 403
                            results.add(buildJsonObject {
                                put("path", path)
                                put("status", r.code)
                                put("interesting", interesting)
                                put("preview", if (interesting) body else "")
                            })
                        }
                    }
                }
            }

            listOf(UIMessagePart.Text(buildJsonObject {
                put("base_url", base)
                put("scanned", paths.size)
                put("interesting_found", results.count { it["interesting"]?.jsonPrimitive?.contentOrNull == "true" })
                put("results", kotlinx.serialization.json.JsonArray(results))
            }.toString()))
        },
    ),

    // ========== 7. SQL 注入检测 ==========
    Tool(
        name = "pentagi_sql_injection_scan",
        description = "PentAGI — SQL 注入基础检测：对 URL 的查询参数注入常见 payload（单引号、OR 1=1、UNION SELECT 等），对比响应差异判断是否存在注入点。纯本地 HTTP。Params: url(带参数的 URL)",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("url", buildJsonObject { put("type", "string"); put("description", "带参数的 URL，如 https://example.com/item?id=1") })
            }, required = listOf("url"))
        },
        execute = { args ->
            val o = args.jsonObject
            val raw = o["url"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"url required"}"""))
            val url = normalizeTarget(raw)

            val payloads = listOf(
                "'" to "单引号",
                "\"" to "双引号",
                "' OR '1'='1" to "OR 恒真",
                "' OR 1=1--" to "OR 1=1 注释",
                "' AND 1=1--" to "AND 1=1",
                "' AND 1=2--" to "AND 1=2",
                "' UNION SELECT NULL--" to "UNION SELECT",
                "' ORDER BY 1--" to "ORDER BY",
            )

            val findings = mutableListOf<kotlinx.serialization.json.JsonObject>()
            withContext(Dispatchers.IO) {
                payloads.forEach { (payload, name) ->
                    val testUrl = injectPayload(url, payload)
                    val resp = runCatching {
                        val req = Request.Builder().url(testUrl).header("User-Agent", PENTAGI_UA).get().build()
                        pentagiHttpClient.newCall(req).execute().use { r ->
                            r.code to (r.body?.string()?.take(3000) ?: "")
                        }
                    }.getOrNull()
                    if (resp != null) {
                        val (code, body) = resp
                        val lower = body.lowercase()
                        val suspicious = lower.contains("sql") || lower.contains("syntax") ||
                            lower.contains("mysql") || lower.contains("postgresql") ||
                            lower.contains("oracle") || lower.contains("microsoft") ||
                            lower.contains("odbc") || lower.contains("unclosed") ||
                            lower.contains("quotation") || lower.contains("error")
                        findings.add(buildJsonObject {
                            put("payload", payload)
                            put("test", name)
                            put("status", code)
                            put("response_length", body.length)
                            put("error_signature", suspicious)
                        })
                    }
                }
            }

            listOf(UIMessagePart.Text(buildJsonObject {
                put("url", url)
                put("tested", payloads.size)
                put("suspicious_count", findings.count { it["error_signature"]?.jsonPrimitive?.contentOrNull == "true" })
                put("findings", kotlinx.serialization.json.JsonArray(findings))
                put("note", "基础检测：仅提示可能的注入点，需人工验证")
            }.toString()))
        },
    ),

    // ========== 8. XSS 检测 ==========
    Tool(
        name = "pentagi_xss_scan",
        description = "PentAGI — 反射型 XSS 检测：对 URL 参数注入 XSS payload，检测响应中是否原样反射（反射则可能存在 XSS 点）。纯本地 HTTP。Params: url(带参数的 URL)",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("url", buildJsonObject { put("type", "string"); put("description", "带参数的 URL，如 https://example.com/search?q=test") })
            }, required = listOf("url"))
        },
        execute = { args ->
            val o = args.jsonObject
            val raw = o["url"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"url required"}"""))
            val url = normalizeTarget(raw)

            val payloads = listOf(
                "<script>alert(1)</script>" to "script 标签",
                "<img src=x onerror=alert(1)>" to "img onerror",
                "javascript:alert(1)" to "javascript 协议",
                "\" onmouseover=\"alert(1)" to "属性注入",
            )

            val findings = mutableListOf<kotlinx.serialization.json.JsonObject>()
            withContext(Dispatchers.IO) {
                payloads.forEach { (payload, name) ->
                    val testUrl = injectPayload(url, payload)
                    val resp = runCatching {
                        val req = Request.Builder().url(testUrl).header("User-Agent", PENTAGI_UA).get().build()
                        pentagiHttpClient.newCall(req).execute().use { r ->
                            r.code to (r.body?.string()?.take(5000) ?: "")
                        }
                    }.getOrNull()
                    if (resp != null) {
                        val (code, body) = resp
                        val reflected = body.contains(payload) ||
                            body.contains(payload.replace("<", "%3C").replace(">", "%3E")) ||
                            body.contains(payload.replace("<", "&lt;").replace(">", "&gt;"))
                        findings.add(buildJsonObject {
                            put("payload", payload)
                            put("test", name)
                            put("status", code)
                            put("reflected", reflected)
                        })
                    }
                }
            }

            listOf(UIMessagePart.Text(buildJsonObject {
                put("url", url)
                put("tested", payloads.size)
                put("reflected_count", findings.count { it["reflected"]?.jsonPrimitive?.contentOrNull == "true" })
                put("findings", kotlinx.serialization.json.JsonArray(findings))
                put("note", "基础检测：反射不一定可利用，需结合上下文验证")
            }.toString()))
        },
    ),

    // ========== 9. 技术栈识别 ==========
    Tool(
        name = "pentagi_tech_detect",
        description = "PentAGI — Web 技术栈识别：根据响应头（Server/X-Powered-By）和页面特征（框架关键词）识别目标使用的技术栈（Nginx/Apache/WordPress/Next.js/React 等）。纯本地 HTTP。Params: url",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("url", buildJsonObject { put("type", "string"); put("description", "目标 URL") })
            }, required = listOf("url"))
        },
        execute = { args ->
            val o = args.jsonObject
            val raw = o["url"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"url required"}"""))
            val url = normalizeTarget(raw)
            val resp = httpGet(url)
            if (resp == null) {
                return@Tool listOf(UIMessagePart.Text("""{"error":"无法连接 $url"}"""))
            }

            val server = resp.headers["Server"]?.firstOrNull() ?: ""
            val poweredBy = resp.headers["X-Powered-By"]?.firstOrNull() ?: ""
            val body = resp.body.lowercase()

            val detected = mutableListOf<String>()
            if (server.contains("nginx", true)) detected.add("Nginx")
            if (server.contains("apache", true)) detected.add("Apache")
            if (server.contains("iis", true)) detected.add("IIS")
            if (server.contains("openresty", true)) detected.add("OpenResty")
            if (server.contains("caddy", true)) detected.add("Caddy")
            if (server.contains("cloudflare", true)) detected.add("Cloudflare")
            if (poweredBy.contains("php", true)) detected.add("PHP")
            if (poweredBy.contains("asp.net", true) || poweredBy.contains("express", true)) detected.add("ASP.NET/Express")
            if (body.contains("wp-content") || body.contains("wordpress")) detected.add("WordPress")
            if (body.contains("__next")) detected.add("Next.js")
            if (body.contains("__nuxt")) detected.add("Nuxt.js")
            if (body.contains("_next/static")) detected.add("Next.js")
            if (body.contains("data-reactroot")) detected.add("React")
            if (body.contains("ng-version")) detected.add("Angular")
            if (body.contains("vue") || body.contains("data-v-")) detected.add("Vue.js")
            if (body.contains("jquery")) detected.add("jQuery")
            if (body.contains("bootstrap")) detected.add("Bootstrap")
            if (body.contains("laravel") || body.contains("csrf-token")) detected.add("Laravel/PHP")

            listOf(UIMessagePart.Text(buildJsonObject {
                put("url", url)
                put("server", server)
                put("powered_by", poweredBy)
                put("technologies", kotlinx.serialization.json.JsonArray(detected.distinct().map { kotlinx.serialization.json.JsonPrimitive(it) }))
                put("technologies_count", detected.distinct().size)
            }.toString()))
        },
    ),

    // ========== 10. 报告生成 ==========
    Tool(
        name = "pentagi_report_generate",
        description = "PentAGI — 生成渗透测试报告：将前面扫描的发现汇总为结构化 Markdown 安全报告（目标信息/开放端口/安全头问题/路径发现/SQLi/XSS 结果）。纯本地。Params: target(目标描述), findings(JSON 字符串，可选)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("target", buildJsonObject { put("type", "string"); put("description", "目标 URL 或描述") })
                put("findings", buildJsonObject { put("type", "string"); put("description", "可选：扫描结果 JSON 字符串") })
            }, required = listOf("target"))
        },
        execute = { args ->
            val o = args.jsonObject
            val target = o["target"]?.jsonPrimitive?.contentOrNull ?: ""
            val findingsRaw = o["findings"]?.jsonPrimitive?.contentOrNull ?: ""

            val sb = StringBuilder()
            sb.appendLine("# 渗透测试报告")
            sb.appendLine()
            sb.appendLine("## 1. 目标信息")
            sb.appendLine()
            sb.appendLine("- **目标**: $target")
            sb.appendLine("- **测试时间**: ${java.time.Instant.now()}")
            sb.appendLine("- **工具**: PentAGI MCP（纯本地，无 API / 无本地模型 / 无工作区）")
            sb.appendLine()
            sb.appendLine("## 2. 扫描发现")
            sb.appendLine()
            if (findingsRaw.isNotBlank()) {
                sb.appendLine("```json")
                sb.appendLine(findingsRaw.take(5000))
                sb.appendLine("```")
            } else {
                sb.appendLine("（未提供详细扫描结果，可先执行 pentagi_* 扫描工具后再生成报告）")
            }
            sb.appendLine()
            sb.appendLine("## 3. 修复建议")
            sb.appendLine()
            sb.appendLine("1. 及时更新服务器软件与依赖版本")
            sb.appendLine("2. 修复安全响应头缺失（CSP / X-Frame-Options / HSTS）")
            sb.appendLine("3. 对用户输入做严格校验与输出编码（防 SQLi / XSS）")
            sb.appendLine("4. 移除暴露的敏感文件（/.git、/.env、备份文件等）")
            sb.appendLine("5. 限制不必要的开放端口与暴露的服务")
            sb.appendLine()
            sb.appendLine("> ⚠️ 本报告基于自动化基础检测，需人工复核确认")

            listOf(UIMessagePart.Text(buildJsonObject {
                put("report", sb.toString())
                put("format", "markdown")
            }.toString()))
        },
    ),
)

/** 将 payload 注入到 URL 的最后一个查询参数值 */
private fun injectPayload(url: String, payload: String): String {
    val encoded = java.net.URLEncoder.encode(payload, "UTF-8")
    val qIdx = url.indexOf("?")
    if (qIdx < 0) return "$url?q=$encoded"
    val base = url.substring(0, qIdx)
    val query = url.substring(qIdx + 1)
    val parts = query.split("&")
    if (parts.isEmpty()) return "$base?q=$encoded"
    val last = parts.last()
    val eqIdx = last.indexOf("=")
    val newLast = if (eqIdx >= 0) last.substring(0, eqIdx + 1) + encoded else last + "=$encoded"
    val newQuery = (parts.dropLast(1) + newLast).joinToString("&")
    return "$base?$newQuery"
}
