/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonElement
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
 * 数据源：中央气象台台风网（NMC）——仅保留【实测返回 200】的接口，404 的源一律不用。
 *  - 列表：`https://typhoon.nmc.cn/weatherservice/typhoon/jsons/list_default`（200）
 *  - 详情：`https://typhoon.nmc.cn/weatherservice/typhoon/jsons/view_{id}`（200）
 *
 * 返回 JSONP（`typhoon_xxx((...))`），解析器精确适配 nmc 的「数组」结构：
 *  - 台风对象 = `[id, enname, namecn, code1, code2, typhoonCode, meaning, status]`
 *    status: "start"=活跃 / "stop"=已停止
 *  - 路径点 = `[id, "YYMMDDHHMM", epochMillis, 级别, 经度, 纬度, 中心气压, 风速, 移向, 移速, ...]`
 *    级别: TD=热带低压, TS=热带风暴, STS=强热带风暴, TY=台风, STY=强台风, SuperTY=超强台风
 *
 * 若网络不可达/接口异常，会返回明确错误信息，绝不静默失败。
 */
private const val TF_UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"

// 仅实测 200 的源。列表接口（detail 通过 view_{id} 动态拼接）。
private val DEFAULT_LIST_SOURCES = listOf(
    "https://typhoon.nmc.cn/weatherservice/typhoon/jsons/list_default",
)

private val tfHttp: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

/** 发起 GET；仅接受 2xx 响应（404 等直接判失败，不误当数据），返回剥离 JSONP 后的 JSON 原文。 */
private fun tfGet(url: String): String? = try {
    tfHttp.newCall(
        Request.Builder().url(url)
            .header("User-Agent", TF_UA)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .get().build()
    ).execute().use { resp ->
        if (!resp.isSuccessful) return null // 非 2xx（404/5xx）一律返回 null，交由调用方报错
        val body = resp.body?.string() ?: return null
        jsonpStrip(body).take(600000)
    }
} catch (e: Exception) {
    android.util.Log.w("TyphoonMcp", "GET fail ${url}: ${e.message}")
    null
}

/** 剥离 JSONP 包裹还原纯 JSON。直接用大括号定位（兼容 `func(({...}))` 双括号形式）。 */
private fun jsonpStrip(body: String): String {
    val raw = body.trim()
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    return if (start >= 0 && end > start) raw.substring(start, end + 1) else raw.removeSuffix(";").trimEnd()
}

/** 解析 JSON 字符串为 JsonElement；失败返回 null。 */
private fun parseJson(text: String): JsonElement? = try {
    kotlinx.serialization.json.Json.parseToJsonElement(text)
} catch (e: Exception) {
    android.util.Log.w("TyphoonMcp", "JSON parse fail: ${e.message}")
    null
}

private fun elStr(e: JsonElement?): String = (e as? JsonPrimitive)?.contentOrNull ?: ""

private fun elStrOrNull(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull

/** 取数组的第 idx 个元素（越界返回 null）。 */
private fun arrIdx(arr: kotlinx.serialization.json.JsonArray, idx: Int): JsonElement? =
    arr.getOrNull(idx)

private fun arrStr(arr: kotlinx.serialization.json.JsonArray, idx: Int): String = elStr(arrIdx(arr, idx))

private fun arrStrOrNull(arr: kotlinx.serialization.json.JsonArray, idx: Int): String? = elStrOrNull(arrIdx(arr, idx))

/**
 * 解析 list_default 返回的台风列表。
 * 返回列表行文本；解析失败返回 null。
 */
private fun parseNmcList(raw: String): String? {
    val root = parseJson(jsonpStrip(raw)) ?: return null
    val rootObj = root as? kotlinx.serialization.json.JsonObject ?: return null
    val list = rootObj["typhoonList"] as? kotlinx.serialization.json.JsonArray ?: return null
    if (list.isEmpty()) return "当前无台风（台风网暂无活跃记录）"
    val lines = mutableListOf<String>()
    for (item in list) {
        val a = item as? kotlinx.serialization.json.JsonArray ?: continue
        // [id, enname, namecn, code1, code2, typhoonCode, meaning, status]
        val id = arrStr(a, 0)
        val en = arrStrOrNull(a, 1)
        val cn = arrStrOrNull(a, 2)
        val code3 = arrStr(a, 3)
        val code = if (code3.isNotBlank()) code3 else arrStrOrNull(a, 5)
        val status = arrStr(a, 7)
        val name = cn?.takeIf { it.isNotBlank() } ?: en ?: "未知"
        val active = (status == "start")
        val sb = StringBuilder(name.take(20))
        if (en != null && en.isNotBlank() && cn?.isNotBlank() == true) sb.append("(").append(en.take(20)).append(")")
        if (code != null && code.isNotBlank()) sb.append(" #").append(code)
        sb.append(if (active) " 🌪活跃" else " ℹ️已停止")
        lines += sb.toString()
    }
    if (lines.isEmpty()) return null
    return "台风列表（NMC）：\n" + lines.joinToString("\n")
}

/** 台风级别代号 → 中文说明 */
private fun gradeName(code: String): String = when (code.uppercase()) {
    "TD" -> "热带低压"
    "TS" -> "热带风暴"
    "STS" -> "强热带风暴"
    "TY" -> "台风"
    "STY" -> "强台风"
    "SUPERTY" -> "超强台风"
    else -> code
}

/**
 * 解析 view_{id} 返回的台风详细路径。
 * typhoon[0:8] = 元信息，typhoon[8] = 路径点数组。
 */
private fun parseNmcTrack(raw: String): String? {
    val root = parseJson(jsonpStrip(raw)) ?: return null
    val rootObj = root as? kotlinx.serialization.json.JsonObject ?: return null
    val tArr = rootObj["typhoon"] as? kotlinx.serialization.json.JsonArray ?: return null
    if (tArr.isEmpty()) return null

    // 元信息
    val en = arrStrOrNull(tArr, 1)
    val cn = arrStrOrNull(tArr, 2)
    val code = arrStrOrNull(tArr, 3) ?: arrStrOrNull(tArr, 4)
    val status = arrStr(tArr, 7)
    val name = cn?.takeIf { it.isNotBlank() } ?: en ?: "台风"

    val sb = StringBuilder()
    sb.append("台风「").append(name.take(20)).append("」")
    if (en != null && en.isNotBlank()) sb.append(" (英文:").append(en.take(20)).append(")")
    if (code != null && code.isNotBlank()) sb.append(" 编号:").append(code)
    sb.append(if (status == "start") " 🌪活跃" else " ℹ️已停止").append("\n")

    // 路径点 [id, "YYMMDDHHMM", epoch, 级别, 经度, 纬度, 气压, 风速, 移向, 移速, ...]
    val track = tArr.getOrNull(8) as? kotlinx.serialization.json.JsonArray
    if (track != null && track.isNotEmpty()) {
        sb.append("历史轨迹/预测（按时间）：\n")
        track.forEachIndexed { i, pt ->
            val a = pt as? kotlinx.serialization.json.JsonArray ?: return@forEachIndexed
            val timeStr = arrStr(a, 1)
            val grade = gradeName(arrStr(a, 3))
            val lon = arrStr(a, 4)
            val lat = arrStr(a, 5)
            val pressure = arrStr(a, 6)
            val wind = arrStr(a, 7)
            val moveDir = arrStrOrNull(a, 8)
            val moveSpd = arrStr(a, 9)
            val seg = StringBuilder()
            if (timeStr.isNotBlank()) seg.append(timeStr.take(12))
            if (grade.isNotBlank()) seg.append(" ").append(grade)
            if (lon.isNotBlank() && lat.isNotBlank()) seg.append(" ").append(lon).append("°E,").append(lat).append("°N")
            if (pressure.isNotBlank()) seg.append(" 气压").append(pressure).append("hPa")
            if (wind.isNotBlank()) seg.append(" 风速").append(wind).append("m/s")
            if (!moveDir.isNullOrBlank()) seg.append(" 移向").append(moveDir)
            if (moveSpd.isNotBlank()) seg.append(" 移速").append(moveSpd).append("km/h")
            sb.append("· ").append(seg.toString().trim()).append("\n")
            if (i >= 40) { sb.append("…（更多）\n"); return@forEachIndexed }
        }
    } else {
        sb.append("（暂无路径数据）")
    }
    return sb.toString().trimEnd()
}

/** 从 list_default 里按名称/编号查找台风 id。 */
private fun findTyphoonId(raw: String?): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val root = raw?.let { parseJson(jsonpStrip(it)) } as? kotlinx.serialization.json.JsonObject ?: return map
    val list = root["typhoonList"] as? kotlinx.serialization.json.JsonArray ?: return map
    for (item in list) {
        val a = item as? kotlinx.serialization.json.JsonArray ?: continue
        val id = arrStr(a, 0)
        val en = arrStrOrNull(a, 1)
        val cn = arrStrOrNull(a, 2)
        val code = arrStrOrNull(a, 3) ?: arrStrOrNull(a, 5)
        seqMap(map, en, id)
        seqMap(map, cn, id)
        seqMap(map, code, id)
    }
    return map
}

private fun seqMap(map: MutableMap<String, String>, key: String?, id: String) {
    if (!key.isNullOrBlank()) map[key.lowercase()] = id
}

fun buildTyphoonMcpTools(): List<Tool> = listOf(
    Tool(
        name = "typhoon_active",
        description = "查询当前台风列表（中央气象台 NMC）。含中文/英文名、编号、活跃状态。Params: dataUrl(可选，自定义数据源URL，默认内置NMC公开源)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("dataUrl", buildJsonObject { put("type", "string"); put("description", "可选：自定义台风数据源URL") })
            })
        },
        execute = { args ->
            val dataUrl = args.jsonObject["dataUrl"]?.jsonPrimitive?.contentOrNull
            val url = if (!dataUrl.isNullOrBlank()) dataUrl else DEFAULT_LIST_SOURCES.first()
            val raw = tfGet(url)
                ?: return@Tool listOf(UIMessagePart.Text("台风数据获取失败：接口不可达或非2xx响应。请稍后重试，或传 dataUrl 指定可用源。"))
            val summary = parseNmcList(raw)
                ?: "已取得台风数据但解析失败（接口格式可能有变，原始摘录）：\n" + raw.take(1500)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("source", url)
                put("data", summary.take(8000))
            }.toString()))
        },
    ),

    Tool(
        name = "typhoon_detail",
        description = "查询指定台风详细路径（历史轨迹+预测）。Params: name(台风中文名/英文名/编号，如 艾莎尼 / ATSANI / 2621)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("name", buildJsonObject { put("type", "string"); put("description", "台风中文名/英文名/编号，如 艾莎尼 / ATSANI / 2621") })
            }, required = listOf("name"))
        },
        execute = { args ->
            val o = args.jsonObject
            val name = o["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (name.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"name required"}"""))

            // 1) 先拉列表建 name→id 映射
            val listRaw = tfGet(DEFAULT_LIST_SOURCES.first())
            val idMap = findTyphoonId(listRaw)
            val id = idMap[name.lowercase()]

            // 2) 用 id 拉详情
            val trackRaw = if (id != null) {
                tfGet("https://typhoon.nmc.cn/weatherservice/typhoon/jsons/view_$id")
            } else null
            if (trackRaw == null) {
                // 列表里没找到或详情接口异常
                val fallback = listRaw?.let { parseNmcList(it) }
                return@Tool listOf(UIMessagePart.Text(
                    (fallback?.let { "未找到台风「$name」，当前台风列表：\n$it" }
                        ?: "未找到台风「$name」，且列表接口不可达。请核对名称后重试。").take(6000)
                ))
            }
            val detail = parseNmcTrack(trackRaw)
                ?: "已取得「$name」数据但解析失败（原始摘录）：\n" + trackRaw.take(1500)
            listOf(UIMessagePart.Text(detail.take(10000)))
        },
    ),
)