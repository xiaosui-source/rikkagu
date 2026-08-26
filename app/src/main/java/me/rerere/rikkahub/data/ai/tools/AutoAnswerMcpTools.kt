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