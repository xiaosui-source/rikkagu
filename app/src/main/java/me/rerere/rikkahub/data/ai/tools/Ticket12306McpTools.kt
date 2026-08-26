package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

private const val K12306 = "https://kyfw.12306.cn"
private val tHttp = OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
    .followRedirects(true).cookieJar(object : CookieJar {
        private val store = ConcurrentHashMap<String,List<Cookie>>()
        override fun loadForRequest(url: HttpUrl) = store[url.host] ?: emptyList()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { store[url.host] = cookies }
    }).build()

private var tStations: Map<String,Pair<String,String>>? = null

private suspend fun initStations() {
    if (tStations != null) return
    try {
        val html = tHttp.newCall(Request.Builder().url("https://www.12306.cn/index/").get().build()).execute().use { it.body?.string() ?: "" }
        val jsPath = Regex("""(/script/core/common/station_name.+?\.js)""").find(html)?.value ?: return
        val js = tHttp.newCall(Request.Builder().url("https://www.12306.cn$jsPath").get().build()).execute().use { it.body?.string() ?: "" }
        val raw = js.replace("var station_names ='", "").replace("';", "")
        val map = mutableMapOf<String,Pair<String,String>>()
        raw.split("@").forEach { item -> val parts = item.split("|"); if (parts.size >= 3) map[parts[2]] = parts[1] to parts[0] }
        tStations = map
    } catch (e: Exception) {
        // 不静默吞异常:车站数据加载失败会影响所有车票查询,记录原因便于排查
        android.util.Log.w("Ticket12306", "initStations failed: ${e.message}")
    }
}

private fun resolveCode(input: String): String {
    val s = tStations ?: return input
    if (input.length == 3 && input.all { it.isUpperCase() }) return input
    return s.entries.find { it.value.first == input.removeSuffix("站") }?.key ?: input
}

private fun call(path: String, params: Map<String,String>): String {
    val qs = params.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value,"UTF-8")}" }
    val url = if(path.startsWith("http")) "$path?$qs" else "$K12306$path?$qs"
    return try {
        tHttp.newCall(Request.Builder().url(url).header("User-Agent","Mozilla/5.0").header("Accept-Language","zh-CN").get().build())
            .execute().use { it.body?.string()?.take(15000) ?: "{}" }
    } catch(e: Exception) { """{"error":"${e.message?.take(200)}"}""" }
}

private fun fmtTickets(raw: String, mapJson: String): String {
    try {
        val map = Json.parseToJsonElement(mapJson).jsonObject
        val lines = raw.split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return "未查询到车次"
        val sb = StringBuilder("车次(train_no)|出发→到达|时间|历时|票务\n")
        for (line in lines.take(30)) {
            val f = line.split("|"); if (f.size < 35) continue
            val code = f[3]; val tno = f[2]
            val from = map[f[6]]?.jsonPrimitive?.content ?: f[6]
            val to = map[f[7]]?.jsonPrimitive?.content ?: f[7]
            val seats = listOf("swz" to "商务座","zy" to "一等座","ze" to "二等座","rw" to "软卧","yw" to "硬卧","yz" to "硬座","wz" to "无座")
            val tix = seats.mapNotNull { (k,v) ->
                val idx = when(k){"swz"->32;"zy"->31;"ze"->30;"rw"->23;"yw"->28;"yz"->29;"wz"->26;else->-1}
                if (idx in f.indices && f[idx].isNotBlank() && f[idx] != "" && f[idx] != "*") "$v:${f[idx]}" else null
            }.joinToString(" ")
            sb.append("$code($tno)|$from→$to|${f[8]}→${f[9]}|${f[10]}|$tix\n")
        }
        return sb.toString()
    } catch(e: Exception) { return raw.take(3000) }
}

// 获取某车次的停站集合
private fun getTrainStops(trainNo: String, date: String): Set<String> {
    try {
        val resp = call("/otn/queryTrainInfo/query", mapOf("leftTicketDTO.train_no" to trainNo,"leftTicketDTO.train_date" to date,"rand_code" to ""))
        val data = Json.parseToJsonElement(resp).jsonObject["data"]?.jsonObject?.get("data")?.jsonArray ?: return emptySet()
        return data.map { it.jsonObject["station_name"]?.jsonPrimitive?.content ?: "" }.filter { it.isNotBlank() }.toSet()
    } catch (e: Exception) {
        android.util.Log.w("Ticket12306", "getTrainStops failed: ${e.message}")
        return emptySet()
    }
}

fun buildTicket12306McpTools(): List<Tool> = buildList {

    add(Tool(name="ticket_search",
        description="查询12306火车票。Params: from(出发站), to(到达站), date(yyyy-MM-dd), filter(G/D/Z/T/K可选)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("from",buildJsonObject{put("type","string");put("description","出发站名")})
            put("to",buildJsonObject{put("type","string");put("description","到达站名")})
            put("date",buildJsonObject{put("type","string");put("description","日期")})
            put("filter",buildJsonObject{put("type","string");put("description","G/D/Z/T/K")})
        },required=listOf("from","to","date")) },
        execute={ args ->
            val o=args.jsonObject; initStations()
            val from=resolveCode(o["from"]?.jsonPrimitive?.contentOrNull?:error("from"))
            val to=resolveCode(o["to"]?.jsonPrimitive?.contentOrNull?:error("to"))
            val date=o["date"]?.jsonPrimitive?.contentOrNull?:error("date")
            call("/otn/leftTicket/init", mapOf())
            val resp=call("/otn/leftTicket/queryZ", mapOf("leftTicketDTO.train_date" to date,"leftTicketDTO.from_station" to from,"leftTicketDTO.to_station" to to,"purpose_codes" to "ADULT"))
            try {
                val json=Json.parseToJsonElement(resp).jsonObject
                val result=json["data"]?.jsonObject?.get("result")?.jsonArray?.joinToString("\n"){ it.jsonPrimitive.content } ?: ""
                val map=json["data"]?.jsonObject?.get("map")?.toString() ?: "{}"
                val flt=o["filter"]?.jsonPrimitive?.contentOrNull ?: ""
                var tickets=fmtTickets(result,map)
                if(flt.isNotBlank()) tickets=tickets.lines().filter{it.isBlank()||flt.any{c->it.startsWith(c)}}.joinToString("\n")
                tickets+="\n---\n💡 查跨越站：ticket_skipped_stations('车次', '日期')"
                listOf(UIMessagePart.Text(tickets.ifBlank{"未查询到车次"}))
            } catch(e: Exception) { listOf(UIMessagePart.Text(resp.take(2000))) }
        },
    ))

    add(Tool(name="ticket_interline",
        description="查询12306中转换乘方案。Params: from, to, date。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("from",buildJsonObject{put("type","string");put("description","出发站")})
            put("to",buildJsonObject{put("type","string");put("description","到达站")})
            put("date",buildJsonObject{put("type","string");put("description","日期")})
        },required=listOf("from","to","date")) },
        execute={ args ->
            val o=args.jsonObject; initStations()
            call("/otn/leftTicket/init", mapOf())
            val html=call("/otn/lcQuery/init", mapOf())
            val lcPath=Regex("""var lc_search_url = '(.+?)'""").find(html)?.groupValues?.get(1) ?: "lcQuery/queryG"
            val params=mapOf("train_date" to o["date"]?.jsonPrimitive?.contentOrNull!!,"from_station_telecode" to resolveCode(o["from"]?.jsonPrimitive?.contentOrNull!!),"to_station_telecode" to resolveCode(o["to"]?.jsonPrimitive?.contentOrNull!!),"middle_station" to "","result_index" to "0","can_query" to "Y","isShowWZ" to "N","purpose_codes" to "00","channel" to "E")
            listOf(UIMessagePart.Text(call("/otn/$lcPath", params).take(5000)))
        },
    ))

    add(Tool(name="ticket_station_code",
        description="查询火车站代码。Params: name(站名，多个用|分隔)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{put("name",buildJsonObject{put("type","string");put("description","站名，多个用|分隔")})},required=listOf("name")) },
        execute={ args ->
            initStations()
            val s=tStations?:return@Tool listOf(UIMessagePart.Text("""{"error":"车站数据加载失败"}"""))
            val result=buildJsonObject{ args.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.split("|")?.forEach{ n->
                val clean=n.removeSuffix("站"); val m=s.entries.find{it.value.first==clean||it.key==clean}
                if(m!=null) put(clean,buildJsonObject{put("code",m.key);put("name",m.value.first)}) else put(clean,buildJsonObject{put("error","未找到")})
            }}
            listOf(UIMessagePart.Text(result.toString()))
        },
    ))

    add(Tool(name="ticket_train_route",
        description="查询列车完整停靠路线。Params: train_no(车次), date(日期)。返回所有停靠站及时间。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("train_no",buildJsonObject{put("type","string");put("description","车次号")})
            put("date",buildJsonObject{put("type","string");put("description","日期")})
        },required=listOf("train_no","date")) },
        execute={ args ->
            val o=args.jsonObject; initStations()
            call("/otn/leftTicket/init", mapOf())
            val sResp=call("https://search.12306.cn/search/v1/train/search", mapOf("keyword" to o["train_no"]?.jsonPrimitive?.contentOrNull!!,"date" to o["date"]?.jsonPrimitive?.contentOrNull!!.replace("-","")))
            val trainNo=Regex(""""train_no"\s*:\s*"([^"]+)"""").find(sResp)?.groupValues?.get(1)?:return@Tool listOf(UIMessagePart.Text("未找到车次"))
            val stops=getTrainStops(trainNo,o["date"]?.jsonPrimitive?.content!!)
            val resp=call("/otn/queryTrainInfo/query", mapOf("leftTicketDTO.train_no" to trainNo,"leftTicketDTO.train_date" to o["date"]?.jsonPrimitive?.content!!,"rand_code" to ""))
            try {
                val data=Json.parseToJsonElement(resp).jsonObject["data"]?.jsonObject?.get("data")?.jsonArray
                if(data!=null&&data.isNotEmpty()){ var t="停靠站（${stops.size}站）：\n"; data.forEachIndexed{i,item->val obj=item.jsonObject; t+="${i+1}. ${obj["station_name"]?.jsonPrimitive?.content} ${obj["arrive_time"]?.jsonPrimitive?.content?:""}→${obj["start_time"]?.jsonPrimitive?.content?:""}\n"}; listOf(UIMessagePart.Text(t)) }
                else listOf(UIMessagePart.Text(resp.take(3000)))
            } catch(e: Exception) { listOf(UIMessagePart.Text(resp.take(3000))) }
        },
    ))

    // === 跨越站（不停站）查询 ===
    add(Tool(name="ticket_skipped_stations",
        description="查询某车次不停靠的站（跨越站）。对比同线路其他车次，找出该车次跳过不听的站。Params: train_no(车次), date(日期), from(起点站), to(终点站)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("train_no",buildJsonObject{put("type","string");put("description","车次号")})
            put("date",buildJsonObject{put("type","string");put("description","日期")})
            put("from",buildJsonObject{put("type","string");put("description","起点站")})
            put("to",buildJsonObject{put("type","string");put("description","终点站")})
        },required=listOf("train_no","date","from","to")) },
        execute={ args ->
            val o=args.jsonObject; initStations()
            val train=o["train_no"]?.jsonPrimitive?.contentOrNull?:error("train_no")
            val date=o["date"]?.jsonPrimitive?.contentOrNull?:error("date")
            val from=resolveCode(o["from"]?.jsonPrimitive?.contentOrNull?:error("from"))
            val to=resolveCode(o["to"]?.jsonPrimitive?.contentOrNull?:error("to"))
            call("/otn/leftTicket/init", mapOf())
            // 1. 获取目标列车的train_no和停站
            val sResp=call("https://search.12306.cn/search/v1/train/search", mapOf("keyword" to train,"date" to date.replace("-","")))
            val trainNo=Regex(""""train_no"\s*:\s*"([^"]+)"""").find(sResp)?.groupValues?.get(1)?:return@Tool listOf(UIMessagePart.Text("未找到车次"))
            val myStops=getTrainStops(trainNo,date)
            // 2. 查询同线路所有车次
            val resp=call("/otn/leftTicket/queryZ", mapOf("leftTicketDTO.train_date" to date,"leftTicketDTO.from_station" to from,"leftTicketDTO.to_station" to to,"purpose_codes" to "ADULT"))
            try {
                val json=Json.parseToJsonElement(resp).jsonObject
                val result=json["data"]?.jsonObject?.get("result")?.jsonArray?.joinToString("\n"){ it.jsonPrimitive.content } ?: ""
                // 3. 收集所有车次的停站并集
                val allStops=mutableSetOf<String>()
                result.split("\n").take(20).forEach{ line->
                    val f=line.split("|"); if(f.size<3) return@forEach
                    val otherNo=f[2]
                    if(otherNo!=trainNo) allStops.addAll(getTrainStops(otherNo,date))
                }
                // 4. 差集 = 跨越站
                val skipped=allStops subtract myStops
                val st=tStations?:emptyMap()
                val sb=StringBuilder("🚄 $train 跨越站（同线路其他车停但本车不停，共${skipped.size}站）：\n")
                skipped.take(20).forEach{ code-> sb.append("- ${st[code]?.first ?: code} ($code)\n") }
                if(skipped.isEmpty()) sb.append("未发现跨越站（本车可能停靠所有主要站点）\n")
                sb.append("\n本车停靠站：${myStops.map{st[it]?.first?:it}.joinToString(" → ")}")
                listOf(UIMessagePart.Text(sb.toString()))
            } catch(e: Exception) { listOf(UIMessagePart.Text(resp.take(3000))) }
        },
    ))

    add(Tool(name="ticket_station_trains",
        description="查询经过某站的所有车次。Params: station(站名), date(可选)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("station",buildJsonObject{put("type","string");put("description","站名")})
            put("date",buildJsonObject{put("type","string");put("description","日期(可选)")})
        },required=listOf("station")) },
        execute={ args ->
            initStations()
            val st=args.jsonObject["station"]?.jsonPrimitive?.contentOrNull?:error("station")
            val date=args.jsonObject["date"]?.jsonPrimitive?.contentOrNull?:""
            val code=resolveCode(st)
            val s=tStations?:return@Tool listOf(UIMessagePart.Text("""{"error":"车站数据未加载"}"""))
            call("/otn/leftTicket/init", mapOf())
            val params= mutableMapOf("train_station_code" to code); if(date.isNotBlank()) params["train_start_date"]=date
            val resp=call("/otn/czxx/query", params)
            var text=resp.take(5000)
            try {
                val data=Json.parseToJsonElement(resp).jsonObject["data"]?.jsonObject?.get("data")?.jsonArray
                if(data!=null&&data.isNotEmpty()){ text="【${s[code]?.first?:st}】车次：\n车次|始发→终到|出发|到达\n"; data.take(30).forEach{ val obj=it.jsonObject; text+="${obj["station_train_code"]?.jsonPrimitive?.content}|${obj["start_station_name"]?.jsonPrimitive?.content}→${obj["end_station_name"]?.jsonPrimitive?.content}|${obj["start_time"]?.jsonPrimitive?.content}|${obj["arrive_time"]?.jsonPrimitive?.content}\n" } }
            } catch(e: Exception){ android.util.Log.w("Ticket12306","station_trains parse failed: ${e.message}") }
            listOf(UIMessagePart.Text(text))
        },
    ))
}
