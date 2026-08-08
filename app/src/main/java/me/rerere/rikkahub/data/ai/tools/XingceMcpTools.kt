package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

private val xingceJson = Json { ignoreUnknownKeys = true; isLenient = true }
private var cachedCards: List<JsonObject>? = null

private fun loadCards(context: Context): List<JsonObject> {
    cachedCards?.let { return it }
    return try {
        val text = context.assets.open("xingce/all_cards.jsonl").bufferedReader().use { it.readText() }
        val cards = text.lines().filter { it.isNotBlank() }.map { xingceJson.parseToJsonElement(it).jsonObject }
        cachedCards = cards
        cards
    } catch (e: Exception) { emptyList() }
}

private fun cardSummary(card: JsonObject): String {
    val id = card["id"]?.jsonPrimitive?.contentOrNull ?: ""
    val module = card["module"]?.jsonPrimitive?.contentOrNull ?: ""
    val method = card["method_name"]?.jsonPrimitive?.contentOrNull ?: ""
    val qType = card["question_type"]?.jsonPrimitive?.contentOrNull ?: ""
    val subType = card["sub_type"]?.jsonPrimitive?.contentOrNull ?: ""
    val formulas = card["formulas"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    val steps = card["steps"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    val triggers = card["trigger_conditions"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    val pitfalls = card["pitfalls"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    val sb = StringBuilder()
    sb.append("📋 $method\n")
    sb.append("模块: $module | 题型: $qType | 子类: $subType\n")
    sb.append("ID: $id\n")
    if (triggers.isNotEmpty()) {
        sb.append("\n✅ 适用条件:\n")
        triggers.take(3).forEach { sb.append("  • $it\n") }
    }
    if (steps.isNotEmpty()) {
        sb.append("\n📝 解题步骤:\n")
        steps.forEachIndexed { i, s -> sb.append("  ${i+1}. $s\n") }
    }
    if (formulas.isNotEmpty()) {
        sb.append("\n📐 公式:\n")
        formulas.forEach { sb.append("  • $it\n") }
    }
    if (pitfalls.isNotEmpty()) {
        sb.append("\n⚠️ 易错点:\n")
        pitfalls.take(3).forEach { sb.append("  • $it\n") }
    }
    return sb.toString()
}

fun buildXingceMcpTools(context: Context): List<Tool> = buildList {
    add(Tool(
        name = "xingce_search_methods",
        description = "搜索花生十三行测解题方法卡片。442张卡片覆盖资料分析/数量关系/判断推理/言语理解。Params: keyword(关键词如'增长率'、'图形推理'、'工程问题'), module(模块名可选), limit(返回数量默认5)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("keyword", buildJsonObject { put("type","string"); put("description","搜索关键词") })
                    put("module", buildJsonObject { put("type","string"); put("description","模块: 资料分析/数量关系/判断推理/言语理解") })
                    put("limit", buildJsonObject { put("type","integer"); put("description","返回数量默认5") })
                },
                required = listOf("keyword")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val kw = o["keyword"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
            val module = o["module"]?.jsonPrimitive?.contentOrNull ?: ""
            val limit = o["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 5
            val cards = loadCards(context)
            val results = cards.filter { card ->
                val mod = card["module"]?.jsonPrimitive?.contentOrNull ?: ""
                val matchesModule = module.isBlank() || mod.contains(module, true)
                val matchesKw = kw.isBlank() || run {
                    val searchText = listOf(
                        card["method_name"]?.jsonPrimitive?.contentOrNull,
                        card["question_type"]?.jsonPrimitive?.contentOrNull,
                        card["sub_type"]?.jsonPrimitive?.contentOrNull,
                        card["tags"]?.jsonArray?.joinToString(" ") { it.jsonPrimitive.content },
                        card["trigger_conditions"]?.jsonArray?.joinToString(" ") { it.jsonPrimitive.content },
                    ).joinToString(" ").lowercase()
                    searchText.contains(kw)
                }
                matchesModule && matchesKw
            }.take(limit)
            val text = if (results.isEmpty()) "未找到匹配的方法卡片" else {
                results.joinToString("\n---\n") { cardSummary(it) }
            }
            listOf(UIMessagePart.Text(text))
        },
    ))

    add(Tool(
        name = "xingce_get_method",
        description = "获取方法卡片的完整详情。Params: method_id(卡片ID如'da_growth_rate_general_001')。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("method_id", buildJsonObject { put("type","string"); put("description","卡片ID") })
                },
                required = listOf("method_id")
            )
        },
        execute = { args ->
            val id = args.jsonObject["method_id"]?.jsonPrimitive?.contentOrNull ?: error("method_id required")
            val cards = loadCards(context)
            val card = cards.find { it["id"]?.jsonPrimitive?.contentOrNull == id }
            listOf(UIMessagePart.Text(card?.let { cardSummary(it) } ?: "未找到ID: $id"))
        },
    ))

    add(Tool(
        name = "xingce_classify",
        description = "识别行测题目属于哪个模块和题型。Params: question(题目文本)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("question", buildJsonObject { put("type","string"); put("description","题目文本") })
                },
                required = listOf("question")
            )
        },
        execute = { args ->
            val q = args.jsonObject["question"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: error("question required")
            val cards = loadCards(context)
            val scored = cards.filter { card ->
                val triggers = card["trigger_conditions"]?.jsonArray?.map { it.jsonPrimitive.content.lowercase() } ?: emptyList()
                var score = 0
                triggers.forEach { trigger ->
                    val keywords = trigger.split("、", "，", "；", " ").filter { it.length > 1 }
                    keywords.forEach { kw -> if (q.contains(kw)) score++ }
                }
                score > 0
            }.sortedByDescending { it["module"]?.jsonPrimitive?.contentOrNull?.length ?: 0 }.take(5)
            val text = if (scored.isEmpty()) {
                "未能自动分类，请提供更多题目信息"
            } else {
                buildString {
                    append("🔍 可能的题型匹配（按匹配度排序）：\n\n")
                    scored.forEachIndexed { i, card ->
                        val mod = card["module"]?.jsonPrimitive?.contentOrNull ?: ""
                        val method = card["method_name"]?.jsonPrimitive?.contentOrNull ?: ""
                        val id = card["id"]?.jsonPrimitive?.contentOrNull ?: ""
                        append("${i+1}. [$mod] $method\n   ID: $id\n   用 xingce_get_method 查看完整方法\n")
                    }
                }
            }
            listOf(UIMessagePart.Text(text))
        },
    ))

    add(Tool(
        name = "xingce_list_modules",
        description = "列出花生十三行测知识库的所有模块和方法数量。",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject {}) },
        execute = {
            val cards = loadCards(context)
            val modules = cards.groupBy { it["module"]?.jsonPrimitive?.contentOrNull ?: "其他" }
                .map { (mod, list) -> mod to list.size }
                .sortedByDescending { it.second }
            val text = buildString {
                append("📚 花生十三行测知识库（共${cards.size}张方法卡片）\n\n")
                modules.forEach { (mod, count) ->
                    append("  $mod: $count 张\n")
                }
                append("\n用 xingce_search_methods 搜索具体方法\n")
                append("用 xingce_classify 自动识别题目类型\n")
            }
            listOf(UIMessagePart.Text(text))
        },
    ))

    add(Tool(
        name = "xingce_solve",
        description = "行测解题引导：根据题目自动匹配方法卡片，返回解题步骤和公式。Params: question(题目), module(模块提示,可选)。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("question", buildJsonObject { put("type","string"); put("description","题目文本") })
                    put("module", buildJsonObject { put("type","string"); put("description","模块提示(可选)") })
                },
                required = listOf("question")
            )
        },
        execute = { args ->
            val o = args.jsonObject
            val q = o["question"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: error("question required")
            val moduleHint = o["module"]?.jsonPrimitive?.contentOrNull ?: ""
            val cards = loadCards(context)
            val scored = cards.filter { card ->
                val mod = card["module"]?.jsonPrimitive?.contentOrNull ?: ""
                moduleHint.isBlank() || mod.contains(moduleHint, true)
            }.filter { card ->
                val triggers = card["trigger_conditions"]?.jsonArray?.map { it.jsonPrimitive.content.lowercase() } ?: emptyList()
                var score = 0
                triggers.forEach { trigger ->
                    trigger.split("、", "，", "；", " ").filter { it.length > 1 }.forEach { kw ->
                        if (q.contains(kw)) score++
                    }
                }
                score > 0
            }.sortedByDescending { it["module"]?.jsonPrimitive?.contentOrNull?.length ?: 0 }.take(3)
            val text = if (scored.isEmpty()) {
                "未能匹配到解题方法，请用 xingce_search_methods 手动搜索"
            } else {
                buildString {
                    append("🎯 解题引导\n\n")
                    scored.forEach { card ->
                        append(cardSummary(card))
                        append("\n---\n")
                    }
                    append("\n请根据以上方法步骤分析题目并给出答案。")
                }
            }
            listOf(UIMessagePart.Text(text))
        },
    ))
}
