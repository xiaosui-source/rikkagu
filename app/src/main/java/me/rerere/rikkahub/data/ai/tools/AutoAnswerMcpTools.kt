/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonArray
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
import java.util.concurrent.TimeUnit

/**
 * 自动答题 MCP：AI 通过 HTTP 请求自行搜索题目答案并作答。
 *
 * 全部平台通用：不绑定任何具体答题平台，凡是有「题目 → 查答案」需求的场合，
 * AI 调用这些工具完成搜索 + 提炼答案。
 *
 * - search_exam_answer: 输入题目/关键词，请求多个公开搜索引擎，返回相关答案片段
 * - auto_answer: 通用自动答题，AI 自行判断并给出原题/答案/解析
 */
fun createAutoAnswerMcpTools(): List<Tool> {
    val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    return listOf(
        Tool(
            name = "search_exam_answer",
            description = "自动搜题：输入题目或关键词，通过网络请求在公开搜索引擎查找相关资料/答案片段。" +
                "返回多个候选结果(来源/摘要)，AI 据此提炼正确答案。适用于各种答题/作业平台的查题场景。",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("query", buildJsonObject {
                            put("type", "string")
                            put("description", "题目内容或搜索关键词，尽量完整粘贴原题")
                        })
                    },
                    required = listOf("query")
                )
            },
            execute = { args ->
                val query = args.jsonObject["query"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"query required"}"""))
                val result = runCatching { doSearch(query, client) }
                    .getOrElse { e -> buildJsonObject { put("error", "搜索失败: ${e.message}") }.toString() }
                listOf(UIMessagePart.Text(result))
            }
        ),
        Tool(
            name = "auto_answer",
            description = "通用自动答题：把题目发给 AI，AI 结合内置知识与可用的网络搜题能力，直接给出答案、解析与" +
                "答题思路。适用于数学/语文/英语/常识/题库等各类题目。",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("question", buildJsonObject {
                            put("type", "string")
                            put("description", "完整的题目内容")
                        })
                        put("options", buildJsonObject {
                            put("type", "string")
                            put("description", "可选的选项列表，用换行分隔(如 A.xxx\nB.xxx)")
                        })
                    },
                    required = listOf("question")
                )
            },
            execute = { args ->
                val json = args.jsonObject
                val question = json["question"]?.jsonPrimitive?.contentOrNull ?: ""
                val options = json["options"]?.jsonPrimitive?.contentOrNull ?: ""
                buildJsonObject {
                    put("question", question)
                    put("options", options)
                    put("hint", "请根据题目结合你的知识给出答案与解析；如需外部资料，用 search_exam_answer 搜索后再作答。")
                }.toString().let { listOf(UIMessagePart.Text(it)) }
            }
        ),
        Tool(
            name = "exam_fetch_questions",
            description = "全自动拉取题目：AI 通过 HTTP 请求指定的题目来源 URL，解析并提取其中的题目列表。" +
                "适用于从题库 API/网页自动获取题目，供随后逐题搜题作答。",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("url", buildJsonObject {
                            put("type", "string")
                            put("description", "题目来源 URL(返回 JSON/HTML/纯文本均可)")
                        })
                        put("question_selector", buildJsonObject {
                            put("type", "string")
                            put("description", "可选：JSON 解析路径或 HTML 选择器；留空则 AI 自动识别题目")
                        })
                    },
                    required = listOf("url")
                )
            },
            execute = { args ->
                val url = args.jsonObject["url"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"url required"}"""))
                val result = runCatching { fetchPage(url, client) }
                    .getOrElse { e -> buildJsonObject { put("error", "请求失败: ${e.message}") }.toString() }
                listOf(UIMessagePart.Text(result))
            }
        ),
        Tool(
            name = "auto_exam",
            description = "全自动答题闭环：给 AI 一个题目来源(URL 或 JSON 题目数组 或直接贴题)。AI 自动" +
                "执行：拉取题目 → 逐题搜索答案 → 汇总输出全部题目的答案与解析。全程自动，无需用户逐题发送。" +
                "将题目来源放到 source 参数；如 source 是 URL，AI 会先请求拉题再逐题作答。",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("source", buildJsonObject {
                            put("type", "string")
                            put("description", "题目来源：可填 URL(自动请求拉题)，或直接粘贴题目/JSON 数组")
                        })
                    },
                    required = listOf("source")
                )
            },
            execute = { args ->
                val source = args.jsonObject["source"]?.jsonPrimitive?.contentOrNull ?: ""
                buildJsonObject {
                    put("source", source)
                    put("plan", "1. 若 source 是 URL，调用 exam_fetch_questions 拉取题目; 2. 对每道题调用 search_exam_answer 搜题; 3. 汇总所有题目答案与解析输出")
                }.toString().let { listOf(UIMessagePart.Text(it)) }
            }
        )
    )
}

/** 用多个公开搜索引擎源搜索题目，返回可读结果 */
private fun doSearch(query: String, client: OkHttpClient): String {
    val items = mutableListOf<kotlinx.serialization.json.JsonObject>()
    runCatching {
        val url = "https://www.bing.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
        val resp = client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/122 Mobile")
                .header("Accept-Language", "zh-CN,zh;q=0.9").build()
        ).execute()
        resp.use {
            val text = htmlToPlainText(it.body?.string().orEmpty())
            if (text.isNotBlank()) items.add(buildJsonObject { put("source", "bing"); put("snippet", text.take(3000)) })
        }
    }
    runCatching {
        val url = "https://www.baidu.com/s?wd=" + java.net.URLEncoder.encode(query, "UTF-8")
        val resp = client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/122 Mobile")
                .header("Accept-Language", "zh-CN,zh;q=0.9").build()
        ).execute()
        resp.use {
            val text = htmlToPlainText(it.body?.string().orEmpty())
            if (text.isNotBlank()) items.add(buildJsonObject { put("source", "baidu"); put("snippet", text.take(3000)) })
        }
    }
    runCatching {
        val url = "https://www.sogou.com/web?query=" + java.net.URLEncoder.encode(query, "UTF-8")
        val resp = client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/122 Mobile")
                .header("Accept-Language", "zh-CN,zh;q=0.9").build()
        ).execute()
        resp.use {
            val text = htmlToPlainText(it.body?.string().orEmpty())
            if (text.isNotBlank()) items.add(buildJsonObject { put("source", "sogou"); put("snippet", text.take(3000)) })
        }
    }

    return buildJsonObject {
        put("query", query)
        put("results", buildJsonArray { items.forEach { add(it) } })
    }.toString()
}

/** 极简 HTML 转纯文本(去脚本/样式/标签)，供 AI 读结果 */
private fun htmlToPlainText(html: String): String {
    return html
        .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/** 请求任意 URL，返回可读内容(JSON 原样 / HTML 转纯文本) */
private fun fetchPage(url: String, client: OkHttpClient): String {
    val req = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/122 Mobile")
        .header("Accept", "application/json,text/html,text/plain,*/*")
        .header("Accept-Language", "zh-CN,zh;q=0.9")
        .build()
    val resp = client.newCall(req).execute()
    resp.use {
        val raw = it.body?.string().orEmpty()
        val contentType = it.header("Content-Type").orEmpty().lowercase()
        val text = if (contentType.contains("json") || raw.trimStart().startsWith("{") || raw.trimStart().startsWith("[")) {
            raw.take(6000)  // JSON 原样返回，AI 自行解析
        } else {
            htmlToPlainText(raw).take(6000)
        }
        buildJsonObject {
            put("url", url)
            put("status", it.code)
            put("content", text)
            put("tip", "若为 HTML 已转为纯文本；若是 JSON 请按字段解析题目。")
        }.toString()
    }
}