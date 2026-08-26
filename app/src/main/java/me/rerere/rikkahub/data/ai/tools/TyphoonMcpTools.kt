/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonPrimitive
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
 * 台风路径查询工具（内置 MCP）。
 *
 * 对接公开实时台风数据源，提供：
 *  - typhoon_active  当前活跃台风列表（名称/编号/级别/风速/气压/位置经纬度）
 *  - typhoon_detail  指定台风详细路径（历史轨迹 + 未来预测）
 *  - typhoon_search  按关键词搜索台风信息
 *
 * 数据源采用「多候选公开免 key 源 + 防御式解析」。任何源不可达或格式变化时，
 * 会把原始数据摘录交还 AI 参考，工具绝不硬失败。所有工具均支持 dataUrl 参数
 * 自定义数据源（便于用户在源变更时手动指定）。
 */
private const val TF_UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"

// 候选台风数据源（按稳定性与免费性排序，均无需 API key）
private val DEFAULT_LIST_SOURCES = listOf(
    // 中央气象台台风网（NMC）—— 台风实时列表
    "https://typhoon.nmc.cn/weatherservice/typhoon/jsons/list_current",
    "https://typhoon.nmc.cn/weatherservice/typhoon/jsons/list_default",
    "https://typhoon.nmc.cn/weatherservice/typhoon/jsons/list_6h",
    // 台风路径（zj 水利台风网）
    "https://typhoon.slt.zj.gov.cn/TyphoonService/gateway/typhoonList",
)

private val tfHttp: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

/** 发起 GET 返回响应原文；失败返回 null */
private fun tfGet(url: String, timeoutMs: Long = 9000): String? = try {
    tfHttp.newCall(
        Request.Builder().url(url)
            .header("User-Agent", TF_UA)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .get().build()
    ).execute().use { resp ->
        val body = resp.body?.string() ?: return null
        // 剥离 JSONP 包裹（如 callback(...) / xxx(...)）还原纯 JSON
        var raw = body.trim()
        if (raw.startsWith("{")) raw = raw
        else if ((raw.startsWith("var ") || raw.startsWith("window.") || isJsonp(raw))) {
            val start = raw.indexOf('(')
            if (start >= 0) {
                val end = raw.lastIndexOf(')')
                if (end > start) raw = raw.substring(start + 1, end)
            }
        }
        raw.take(300000)
    }
} catch (e: Exception) {
    android.util.Log.w("TyphoonMcp", "GET fail ${url}: ${e.message}")
    null
}

private fun isJsonp(raw: String): Boolean {
    val t = raw.trim()
    val hasParen = t.contains('(') && t.endsWith(")") || t.endsWith(");")
    // 以字母/下划线开头说明是函数名包裹
    return hasParen && (t.first().isLetter() || t.first() == '_')
}

/** 依次尝试候选源（优先 dataUrl），返回第一个成功的响应原文 */
private fun fetchAnySource(dataUrl: String?): String? {
    val candidates = buildList {
        if (!dataUrl.isNullOrBlank()) add(dataUrl)
        addAll(DEFAULT_LIST_SOURCES)
    }
    for (src in candidates) {
        val raw = tfGet(src) ?: continue
        if (raw.isNotBlank()) return raw
    }
    return null
}

/** 读取 jsonObject 字段字符串值（容错：字段可能是字串/数/对象） */
private fun field(o: kotlinx.serialization.json.JsonObject, vararg keys: String): String? {
    for (k in keys) {
        val v = o[k] ?: continue
        if (v is JsonPrimitive) v.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

/** 尝试把任意 JsonElement 当作 JsonObject */
private fun asObj(e: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonObject? =
    e as? kotlinx.serialization.json.JsonObject

/** 深度搜集 JSON 中所有非空数组 */
private fun deepCollectArrays(
    e: kotlinx.serialization.json.JsonElement,
    depth: Int = 0,
    acc: MutableList<kotlinx.serialization.json.JsonArray> = mutableListOf()
): List<kotlinx.serialization.json.JsonArray> {
    if (depth > 8) return acc
    when (e) {
        is kotlinx.serialization.json.JsonArray -> { if (e.isNotEmpty()) acc.add(e); e.forEach { deepCollectArrays(it, depth + 1, acc) } }
        is kotlinx.serialization.json.JsonObject -> e.forEach { (_, v) -> deepCollectArrays(v, depth + 1, acc) }
        else -> {}
    }
    return acc
}

private val TF_KEY_HINTS = listOf("typhoon", "namecn", "enname", "grade", "speed", "pressure", "lat", "lng", "lon", "track", "path", "forecast")

/** 打分：数组元素字段与台风典型字段名越匹配越可能是台风列表 */
private fun scoreArray(arr: kotlinx.serialization.json.JsonArray): Int {
    val first = arr.firstOrNull()?.let { asObj(it) } ?: return 0
    val keys = first.keys
    return TF_KEY_HINTS.count { hint -> keys.any { it.contains(hint, ignoreCase = true) } }
}

/**
 * 把台风对象格式化为一行可读文本。宽松适配多种字段命名。
 */
private fun formatTyphoonRow(o: kotlinx.serialization.json.JsonObject): String? {
    val name = field(o, "namecn", "name", "tname", "cname") ?: return null
    val sb = StringBuilder(name.trim().take(20))
    field(o, "enname", "en_name", "english")?.let { sb.append("($it)".take(30)) }
    field(o, "typhoonid", "id", "code", "number", "num")?.let { if (it.length <= 8) sb.append(" #").append(it) }
    field(o, "grade", "level", "type", "class")?.let { sb.append(" ").append(it.take(20)) }
    field(o, "speed", "windspeed", "wind", "ws")?.let { sb.append(" 风速").append(it).append("m/s") }
    field(o, "pressure", "press", "pressure_hpa")?.let { sb.append(" 气压").append(it).append("hPa") }
    field(o, "latitude", "lat", "y", "la")?.let { sb.append(" 纬度").append(it) }
    field(o, "longitude", "lng", "x", "lo", "lon")?.let { sb.append(" 经度").append(it) }
    field(o, "move", "moveDir", "movement", "dir")?.let { sb.append(" 移向").append(it.take(10)) }
    field(o, "speed_mv", "movespeed", "ms")?.let { sb.append(" 移速").append(it).append("km/h") }
    field(o, "radius7", "r7", "radius")?.let { sb.append(" 7级风圈").append(it) }
    field(o, "radius10", "r10")?.let { sb.append(" 10级风圈").append(it) }
    field(o, "time", "datetime", "recordtime", "updatetime", "forecasttime")?.let { sb.append(" 时间:").append(it.take(20)) }
    field(o, "text", "desc", "status", "note")?.let { t -> if (t.contains("减弱") || t.contains("加强") || t.contains("登陆") || t.contains("热带低压") || t.contains("强") || t.contains("增强")) sb.append(" 【").append(t.take(30)).append("】") }
    return sb.toString()
}

/** 从 JSON 文本提取台风列表摘要 */
private fun listFromJson(raw: String, limit: Int = 40): String? {
    return try {
        val clean = raw.trim().removePrefix("callback(").trim().removeSuffix(");").removeSuffix(")").trim()
        val root = kotlinx.serialization.json.Json.parseToJsonElement(clean)
        val arrays = deepCollectArrays(root)
        val best = arrays.maxByOrNull { scoreArray(it) } ?: arrays.firstOrNull() ?: return null
        val rows = best.mapNotNull { asObj(it)?.let(::formatTyphoonRow) }.filter { it.isNotBlank() }
        if (rows.isEmpty()) return null
        "当前活跃台风（${rows.size} 个）：\n" + rows.take(limit).joinToString("\n")
    } catch (e: Exception) {
        android.util.Log.w("TyphoonMcp", "list parse fail: ${e.message}")
        null
    }
}

/** 从 JSON 文本提取某个台风的详细路径摘要 */
private fun detailFromJson(raw: String, keyword: String, limit: Int = 60): String? {
    return try {
        val clean = raw.trim().removePrefix("callback(").trim().removeSuffix(");").removeSuffix(")").trim()
        val root = kotlinx.serialization.json.Json.parseToJsonElement(clean)
        val arrays = deepCollectArrays(root)
        val sb = StringBuilder()
        var matched = 0
        for (arr in arrays) {
            val header = arr.firstOrNull()?.let { asObj(it)?.let(::formatTyphoonRow) }
            for (item in arr) {
                val o = asObj(item) ?: continue
                val name = field(o, "namecn", "name", "tname", "cname") ?: continue
                if (!name.contains(keyword, ignoreCase = true) && !(field(o, "typhoonid", "id", "code") ?: "").contains(keyword, ignoreCase = true)) continue
                val row = formatTyphoonRow(o) ?: continue
                sb.append(row).append("\n")
                matched++
                if (matched >= limit) break
            }
            if (sb.isNotEmpty() && header != null) sb.insert(0, "「$keyword」台风路径（来源路径数据节选）：\n")
            if (matched >= limit) break
        }
        if (sb.isNotBlank()) sb.toString().take(8000) else null
    } catch (e: Exception) {
        android.util.Log.w("TyphoonMcp", "detail parse fail: ${e.message}")
        null
    }
}

/** 从 JSON 文本全文搜索含关键词的记录 */
private fun searchFromRaw(raw: String, keyword: String, limit: Int = 30): String {
    val lines = raw.lineSequence().filter { it.contains(keyword, ignoreCase = true) }
        .take(limit).joinToString("\n")
    return if (lines.isNotBlank()) "匹配「$keyword」的记录：\n$lines" else "未在数据中找到「$keyword」的直接文本记录。"
}

fun buildTyphoonMcpTools(): List<Tool> = listOf(
    Tool(
        name = "typhoon_active",
        description = "查询当前活跃台风列表。含中文名/编号/级别/中心风速/气压/位置经纬度/移向移速。Params: dataUrl(可选，自定义台风数据源URL，默认内置公开源)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("dataUrl", buildJsonObject { put("type", "string"); put("description", "可选：自定义台风数据源URL，跳过内置源直接抓取") })
            })
        },
        execute = { args ->
            val dataUrl = args.jsonObject["dataUrl"]?.jsonPrimitive?.contentOrNull
            val raw = fetchAnySource(dataUrl)
                ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                    put("error", "所有台风数据源均不可达")
                    put("tip", "请检查网络；或在参数 dataUrl 直接传入可用数据源")
                }.toString()))
            val summary = listFromJson(raw)
                ?: "已取得台风数据（未识别出台风条目结构，原始数据摘录）：\n" + raw.take(1500)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("source", if (dataUrl != null) dataUrl else "内置公开源")
                put("data", summary.take(8000))
            }.toString()))
        },
    ),

    Tool(
        name = "typhoon_detail",
        description = "查询指定台风详细路径（历史轨迹+未来预测）。Params: name(台风名称或编号，如 格美 / 2403 / 玛莉亚), dataUrl(可选自定义数据源URL)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("name", buildJsonObject { put("type", "string"); put("description", "台风名称或编号，如 格美 / 2403）"); })
                put("dataUrl", buildJsonObject { put("type", "string"); put("description", "可选：自定义台风数据源URL") })
            }, required = listOf("name"))
        },
        execute = { args ->
            val o = args.jsonObject
            val name = o["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (name.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"name required"}"""))
            val raw = fetchAnySource(o["dataUrl"]?.jsonPrimitive?.contentOrNull)
                ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                    put("error", "台风数据源不可达"); put("tip", "可传 dataUrl 指定可用数据源")
                }.toString()))
            val detail = detailFromJson(raw, name)
                ?: listFromJson(raw)?.let { "未匹配到「$name」的路径记录。当前台风列表：\n$it" }
                ?: "未匹配到「$name」的路径记录。原始数据摘录：\n" + raw.take(1200)
            listOf(UIMessagePart.Text(detail.take(8000)))
        },
    ),

    Tool(
        name = "typhoon_search",
        description = "按关键词搜索台风相关信息（如某台风最新动态/预警等）。Params: keyword(搜索关键词), dataUrl(可选自定义数据源URL)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("keyword", buildJsonObject { put("type", "string"); put("description", "搜索关键词，如台风名/编号/预警") })
                put("dataUrl", buildJsonObject { put("type", "string"); put("description", "可选：自定义台风数据源URL") })
            }, required = listOf("keyword"))
        },
        execute = { args ->
            val o = args.jsonObject
            val kw = o["keyword"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (kw.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"keyword required"}"""))
            val raw = fetchAnySource(o["dataUrl"]?.jsonPrimitive?.contentOrNull)
                ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                    put("error", "台风数据源不可达"); put("tip", "可传 dataUrl 指定可用数据源")
                }.toString()))
            val result = searchFromRaw(raw, kw)
            listOf(UIMessagePart.Text(result.take(6000)))
        },
    ),
)
