package me.rerere.rikkahub.data.ai.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val DY = "https://www.douyin.com"
private val http = OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).followRedirects(true).build()
private val COMMON = mapOf("device_platform" to "webapp","aid" to "6383","channel" to "channel_pc_web","version_code" to "170400","version_name" to "17.4.0","cookie_enabled" to "true","platform" to "PC")

private fun hdrs(cookie: String, ref: String = DY) = Headers.Builder()
    .add("Accept","application/json").add("Accept-Language","zh-CN,zh;q=0.9")
    .add("Cookie",cookie).add("Referer",ref)
    .add("User-Agent","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/135.0.0.0 Safari/537.36").build()

private fun api(path: String, params: MutableMap<String,Any?>, cookie: String, ref: String = DY): String {
    params.putAll(COMMON)
    val qs = params.filterValues{it!=null}.map{(k,v)->"$k=${java.net.URLEncoder.encode(v.toString(),"UTF-8")}"}.joinToString("&")
    return try {
        http.newCall(Request.Builder().url("$DY$path?$qs").headers(hdrs(cookie,ref)).get().build())
            .execute().use { it.body?.string()?.take(8000) ?: "{}" }
    } catch(e: Exception) { """{"error":"${e.message?.take(200)}"}""" }
}

private suspend fun fetch(url: String, cookie: String = ""): String {
    val b = Request.Builder().url(url).apply {
        if(cookie.isNotBlank()) header("Cookie",cookie)
    }.get().build()
    return try { http.newCall(b).execute().use { it.body?.string()?.take(10000) ?: "" } }
    catch(e: Exception) { """{"error":"${e.message?.take(200)}"}""" }
}

private fun aid(input: String) = Regex("""(\d{15,20})""").find(input)?.groupValues?.get(1) ?: input

/** 将参数 Map 转为 URL 查询字符串 */
private fun q(params: Map<String, Any?>): String =
    params.filterValues { it != null }
        .map { (k, v) -> "$k=${java.net.URLEncoder.encode(v.toString(), "UTF-8")}" }
        .joinToString("&")

fun buildDouyinMcpTools(
    context: android.content.Context,
    getCookie: () -> String,
    workspaceRepository: me.rerere.rikkahub.data.repository.WorkspaceRepository,
    appEventBus: me.rerere.rikkahub.data.event.AppEventBus,
): List<Tool> = buildList {
    // WebView 隐形会话（AI 自动化浏览器）：数据抓取全部走此会话，签名有效
    val s = session(context)

    // ===== 登录 =====
        add(Tool(name="douyin_login",
            description="【抖音登录工具】当用户要求登录抖音/发视频/发评论/点赞但未登录时，调用此工具自动生成登录二维码（直接显示图片），用户扫码后自动登录。绝不要求用户打开浏览器或手动复制Cookie。",
            needsApproval=false,
            parameters={ InputSchema.Obj(properties=buildJsonObject{}) },
            execute={
                // 方案：App 内置 WebView 加载抖音登录页（抖音网页 JS 自动生成有效二维码签名），
                // 提取二维码图片直接显示在对话中。不依赖已失效的 passport API，不落盘。
                // 直接显示二维码图片（不打开浏览器）
                val qrImage = extractQrFromPage(context, "$DY/login")
                if (qrImage != null) {
                    listOf(
                        UIMessagePart.Text(buildJsonObject{
                            put("action","👉 请用手机抖音APP扫描下方二维码登录")
                            put("step1","打开手机抖音")
                            put("step2","点击右上角『扫一扫』图标")
                            put("step3","扫描下方二维码，手机确认后自动完成登录")
                            put("tip","扫码后自动登录，登录状态会自动保存")
                        }.toString()),
                        UIMessagePart.Image(url = qrImage),
                    )
                } else {
                    // 不打开浏览器：再重试一次提取
                    val retry = extractQrFromPage(context, "$DY/login")
                    if (retry != null) {
                        listOf(
                            UIMessagePart.Text(buildJsonObject{
                                put("action","👉 请用手机抖音APP扫描下方二维码登录")
                                put("tip","扫码后自动登录")
                            }.toString()),
                            UIMessagePart.Image(url = retry),
                        )
                    } else {
                        listOf(UIMessagePart.Text(
                            "⚠️ 二维码生成失败（抖音接口暂时不可用），请稍后重试 douyin_login。"
                        ))
                    }
                }
            },
        ))

        add(Tool(name="douyin_open_page",
            description="自动打开抖音的任意页面（支持抖音任何 URL），若页面需要登录会自动提取登录二维码图片直接显示；无二维码则返回页面截图。Params: url(抖音页面地址，如 https://www.douyin.com/video/xxxx 或留空用首页)",
            needsApproval=false,
            parameters={ InputSchema.Obj(properties=buildJsonObject{
                put("url",buildJsonObject{put("type","string");put("description","抖音页面 URL（可选，默认 https://www.douyin.com/）")})
            }) },
            execute={ args ->
                val o = args.jsonObject
                val rawUrl = o["url"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
                val target = if (rawUrl.isBlank()) "$DY/" else rawUrl

                // 1. 先尝试提取页面二维码（登录墙时页面会渲染二维码）
                val qr = extractQrFromPage(context, target)
                if (qr != null) {
                    listOf(
                        UIMessagePart.Text(buildJsonObject{
                            put("page", target)
                            put("action","👉 页面需要登录，请用手机抖音APP扫描下方二维码登录")
                            put("step","扫描后自动登录，登录状态自动保存")
                        }.toString()),
                        UIMessagePart.Image(url = qr),
                    )
                } else {
                    // 2. 无二维码：返回提示（不打开浏览器）
                    listOf(UIMessagePart.Text(buildJsonObject{
                        put("page", target)
                        put("status","页面无需登录，未提取到二维码")
                        put("tip","该页面可直接访问，如需登录可调用 douyin_login")
                    }.toString()))
                }
            },
        ))

        add(Tool(name="douyin_open_login",
            description="在 App 内置浏览器(WebView)中打开抖音登录页，无需跳转外部浏览器。用户扫码确认后调用 douyin_check_login 自动检测登录。",
            needsApproval=false,
            parameters={ InputSchema.Obj(properties=buildJsonObject{}) },
            execute={
                // 通过 AppEventBus 触发内置 WebView 打开抖音登录页
                runCatching {
                    appEventBus.emit(
                        me.rerere.rikkahub.data.event.AppEvent.OpenWebView("$DY/login")
                    )
                }
                listOf(UIMessagePart.Text(
                    "📱 已在内置浏览器打开抖音登录页，请用手机抖音扫码确认。\n\n" +
                    "扫描登录后，在该二维码确认后调用 **douyin_check_login** 即自动完成登录，**无需手动复制Cookie**。"
                ))
            },
        ))

        add(Tool(name="douyin_check_login",
            description="检查抖音扫码登录状态。登录二维码由 douyin_login 生成，扫描后调用此工具确认是否已登录并自动保存Cookie。",
            needsApproval=false,
            parameters={ InputSchema.Obj(properties=buildJsonObject{} ) },
            execute={
                // 用 WebView 会话检测登录态（cookie 含 sessionid）
                val loggedIn = s.isLoggedIn()
                listOf(UIMessagePart.Text(buildJsonObject{
                    put("logged_in", loggedIn)
                    put("status", if (loggedIn) "已登录，AI 可自动评论/点赞/发布" else "未登录，请调用 douyin_login 扫码一次")
                }.toString()))
            },
        ))

        add(Tool(name="douyin_set_cookie",
            description="手动设置抖音Cookie（备用方案，推荐使用 douyin_login 扫码自动登录）。Params: cookie(完整Cookie字符串，需含sessionid)。",
            needsApproval=true,
            parameters={ InputSchema.Obj(properties=buildJsonObject{
                put("cookie",buildJsonObject{put("type","string");put("description","浏览器复制的完整Cookie")})
            },required=listOf("cookie")) },
            execute={ args ->
                val c = args.jsonObject["cookie"]?.jsonPrimitive?.contentOrNull ?: error("cookie required")
                // 保存到 App 内部存储（不依赖工作区）
                try {
                    val dir = java.io.File(context.filesDir, "douyinmcp").apply { mkdirs() }
                    java.io.File(dir, "cookies.txt").writeText(c)
                } catch(e: Exception) {}
                listOf(UIMessagePart.Text(buildJsonObject{
                    put("saved",true)
                    put("length",c.length)
                    put("has_sessionid",c.contains("sessionid"))
                    put("message","Cookie已保存")
                }.toString()))
            },
        ))

    // ===== 搜索 =====
    add(Tool(name="douyin_search_videos",
        description="搜索抖音视频。Params: keyword(关键词), count(默认10), sort_type(0综合/1点赞最多/2最新), publish_time(0不限/1一天/7一周/180半年)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("keyword",buildJsonObject{put("type","string");put("description","关键词")})
            put("count",buildJsonObject{put("type","integer");put("description","数量默认10")})
            put("sort_type",buildJsonObject{put("type","integer");put("description","0综合/1点赞/2最新")})
            put("publish_time",buildJsonObject{put("type","integer");put("description","0不限/1一天/7一周/180半年")})
        },required=listOf("keyword")) },
        execute={ args ->
            val o=args.jsonObject; val kw=o["keyword"]?.jsonPrimitive?.contentOrNull?:error("kw")
            val cnt=o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:10
                        val params=mutableMapOf<String,Any?>("keyword" to kw,"count" to cnt,"offset" to 0,
                "search_channel" to 0,"sort_type" to (o["sort_type"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:0),
                "publish_time" to (o["publish_time"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:0),
                "verifyFp" to "verify_","fp" to "verify_","enable_history" to "1","search_source" to "tab_search")
            listOf(UIMessagePart.Text(s.api("/aweme/v1/web/general/search/single/", q(params))))
        },
    ))

    // ===== 视频详情 =====
    add(Tool(name="douyin_get_video_detail",
        description="获取抖音视频详情。Params: aweme_id(视频ID或分享链接)。返回标题/点赞/评论/分享/收藏/时长/作者/下载链接。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("aweme_id",buildJsonObject{put("type","string");put("description","视频ID或分享链接")})
        },required=listOf("aweme_id")) },
        execute={ args ->
            val id=aid(args.jsonObject["aweme_id"]?.jsonPrimitive?.contentOrNull?:error("id"))
                        val params=mutableMapOf<String,Any?>("aweme_id" to id,"verifyFp" to "verify_","fp" to "verify_")
            listOf(UIMessagePart.Text(s.api("/aweme/v1/web/aweme/detail/", q(params))))
        },
    ))

    // ===== 评论 =====
    add(Tool(name="douyin_get_video_comments",
        description="获取抖音视频评论。Params: aweme_id, cursor(默认0), count(默认20)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("aweme_id",buildJsonObject{put("type","string");put("description","视频ID")})
            put("cursor",buildJsonObject{put("type","integer");put("description","分页游标")})
            put("count",buildJsonObject{put("type","integer");put("description","数量")})
        },required=listOf("aweme_id")) },
        execute={ args ->
            val o=args.jsonObject; val id=aid(o["aweme_id"]?.jsonPrimitive?.contentOrNull?:error("id"))
                        val params=mutableMapOf<String,Any?>("aweme_id" to id,"cursor" to (o["cursor"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:0),
                "count" to (o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:20),"item_type" to 0,"verifyFp" to "verify_","fp" to "verify_")
            listOf(UIMessagePart.Text(s.api("/aweme/v1/web/comment/list/", q(params))))
        },
    ))

    add(Tool(name="douyin_get_sub_comments",
        description="获取评论回复(子评论)。Params: comment_id(父评论ID), cursor(默认0), count(默认20)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("comment_id",buildJsonObject{put("type","string");put("description","父评论ID")})
            put("cursor",buildJsonObject{put("type","integer");put("description","分页游标")})
            put("count",buildJsonObject{put("type","integer");put("description","数量")})
        },required=listOf("comment_id")) },
        execute={ args ->
            val o=args.jsonObject; val cid=o["comment_id"]?.jsonPrimitive?.contentOrNull?:error("id")
                        val params=mutableMapOf<String,Any?>("comment_id" to cid,"cursor" to (o["cursor"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:0),
                "count" to (o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:20),"item_type" to 0,"verifyFp" to "verify_","fp" to "verify_")
            listOf(UIMessagePart.Text(s.api("/aweme/v1/web/comment/list/reply/", q(params))))
        },
    ))

    // ===== 用户 =====
    add(Tool(name="douyin_get_user_info",
        description="获取抖音用户资料。Params: sec_user_id(用户安全ID，以MS4wLjAB开头)。返回昵称/头像/粉丝/关注/获赞/作品数。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("sec_user_id",buildJsonObject{put("type","string");put("description","用户安全ID")})
        },required=listOf("sec_user_id")) },
        execute={ args ->
            val uid=args.jsonObject["sec_user_id"]?.jsonPrimitive?.contentOrNull?:error("id")
                        val params=mutableMapOf<String,Any?>("sec_user_id" to uid,"verifyFp" to "verify_","fp" to "verify_")
            listOf(UIMessagePart.Text(s.api("/aweme/v1/web/user/profile/other/", q(params))))
        },
    ))

    add(Tool(name="douyin_get_user_posts",
        description="获取抖音用户作品列表。Params: sec_user_id, max_cursor(默认0), count(默认18)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("sec_user_id",buildJsonObject{put("type","string");put("description","用户安全ID")})
            put("max_cursor",buildJsonObject{put("type","string");put("description","分页游标")})
            put("count",buildJsonObject{put("type","integer");put("description","数量")})
        },required=listOf("sec_user_id")) },
        execute={ args ->
            val o=args.jsonObject; val uid=o["sec_user_id"]?.jsonPrimitive?.contentOrNull?:error("id")
                        val params=mutableMapOf<String,Any?>("sec_user_id" to uid,"max_cursor" to (o["max_cursor"]?.jsonPrimitive?.contentOrNull?:"0"),
                "count" to (o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:18),"locate_query" to "false","verifyFp" to "verify_","fp" to "verify_")
            listOf(UIMessagePart.Text(s.api("/aweme/v1/web/aweme/post/", q(params))))
        },
    ))

    // ===== 推荐流 =====
    add(Tool(name="douyin_get_homefeed",
        description="获取抖音推荐视频流。Params: count(默认20), refresh_index(默认0)。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("count",buildJsonObject{put("type","integer");put("description","数量")})
            put("refresh_index",buildJsonObject{put("type","integer");put("description","刷新索引")})
        }) },
        execute={ args ->
            val o=args.jsonObject
                        val params=mutableMapOf<String,Any?>("refresh_index" to (o["refresh_index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:0),
                "count" to (o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:20),"video_type_select" to 0)
            listOf(UIMessagePart.Text(s.api("/aweme/v1/web/tab/feed/", q(params))))
        },
    ))

    // ===== 链接解析 =====
    add(Tool(name="douyin_resolve_share_url",
        description="解析抖音分享短链接(https://v.douyin.com/xxx)，获取视频ID和详情。Params: share_url。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("share_url",buildJsonObject{put("type","string");put("description","分享短链接")})
        },required=listOf("share_url")) },
        execute={ args ->
            val url=args.jsonObject["share_url"]?.jsonPrimitive?.contentOrNull?:error("url")
                        // 跟随重定向获取真实URL
            val client = OkHttpClient.Builder().followRedirects(false).build()
            val resp = client.newCall(Request.Builder().url(url).header("User-Agent","Mozilla/5.0").build()).execute()
            val loc = resp.header("Location") ?: ""
            val aid = Regex("""video/(\d+)""").find(loc)?.groupValues?.get(1) ?: ""
            listOf(UIMessagePart.Text(buildJsonObject{
                put("share_url",url); put("resolved_url",loc); put("aweme_id",aid)
                if(aid.isNotBlank()){ put("tip","使用 douyin_get_video_detail 获取详情"); put("aweme_id",aid) }
            }.toString()))
        },
    ))

    // ===== 下载视频 =====
    add(Tool(name="douyin_download_video",
        description="获取抖音视频无水印下载链接和完整信息。Params: aweme_id。返回下载链接、文件信息、统计数据。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("aweme_id",buildJsonObject{put("type","string");put("description","视频ID")})
        },required=listOf("aweme_id")) },
        execute={ args ->
            val id=aid(args.jsonObject["aweme_id"]?.jsonPrimitive?.contentOrNull?:error("id"))
                        // 获取详情→提取下载链接
            val params=mutableMapOf<String,Any?>("aweme_id" to id,"verifyFp" to "verify_","fp" to "verify_")
            val detail = s.api("/aweme/v1/web/aweme/detail/", q(params))
            listOf(UIMessagePart.Text(buildJsonObject{
                put("aweme_id",id)
                put("detail",detail.take(5000))
                put("tip","从返回的 aweme_detail.video.play_addr.url_list 中获取无水印下载链接。下载: 使用 workspace_shell 执行 curl -L '链接' -o video.mp4")
            }.toString()))
        },
    ))

    // ===== 语音转文字 =====
    add(Tool(name="douyin_transcribe_video",
        description="获取抖音视频信息用于语音转文字。Params: aweme_id。返回视频详情和下载链接，AI可用 workspace_shell 下载后转写。",
        needsApproval=true,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("aweme_id",buildJsonObject{put("type","string");put("description","视频ID")})
        },required=listOf("aweme_id")) },
        execute={ args ->
            val id=aid(args.jsonObject["aweme_id"]?.jsonPrimitive?.contentOrNull?:error("id"))
                        val params=mutableMapOf<String,Any?>("aweme_id" to id,"verifyFp" to "verify_","fp" to "verify_")
            val detail = s.api("/aweme/v1/web/aweme/detail/", q(params))
            listOf(UIMessagePart.Text(buildJsonObject{
                put("aweme_id",id); put("detail",detail.take(5000))
                put("transcribe_howto","1.从detail中提取视频下载链接(video.play_addr.url_list) 2.用workspace_shell下载: curl -L '链接' -o /tmp/video.mp4 3.用workspace_shell提取音频: ffmpeg -i /tmp/video.mp4 -vn /tmp/audio.mp3 4.如有ASR服务(OpenAI Whisper等)，上传转写")
            }.toString()))
        },
    ))

    // ===== 批量转写 =====
    add(Tool(name="douyin_batch_transcribe",
        description="批量搜索并准备转写视频。Params: keyword, count(默认3)。搜索后返回视频列表及下载链接。",
        needsApproval=true,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("keyword",buildJsonObject{put("type","string");put("description","搜索关键词")})
            put("count",buildJsonObject{put("type","integer");put("description","数量默认3")})
        },required=listOf("keyword")) },
        execute={ args ->
            val o=args.jsonObject; val kw=o["keyword"]?.jsonPrimitive?.contentOrNull?:error("kw")
            val cnt=o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?:3
                        val params=mutableMapOf<String,Any?>("keyword" to kw,"count" to cnt,"offset" to 0,
                "search_channel" to 0,"sort_type" to 1,"publish_time" to 0,"verifyFp" to "verify_","fp" to "verify_",
                "enable_history" to "1","search_source" to "tab_search")
            listOf(UIMessagePart.Text(s.api("/aweme/v1/web/general/search/single/", q(params))))
        },
    ))

    // ===== 下载图文 =====
    add(Tool(name="douyin_download_images",
        description="获取抖音图文作品的图片链接列表。Params: aweme_id。返回所有图片URL。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("aweme_id",buildJsonObject{put("type","string");put("description","作品ID")})
        },required=listOf("aweme_id")) },
        execute={ args ->
            val id=aid(args.jsonObject["aweme_id"]?.jsonPrimitive?.contentOrNull?:error("id"))
                        val params=mutableMapOf<String,Any?>("aweme_id" to id,"verifyFp" to "verify_","fp" to "verify_")
            val detail = s.api("/aweme/v1/web/aweme/detail/", q(params))
            listOf(UIMessagePart.Text(buildJsonObject{
                put("aweme_id",id); put("detail",detail.take(5000))
                put("tip","图文作品的图片在 aweme_detail.images[] 数组中。下载: curl -L '图片URL' -o image.jpg")
            }.toString()))
        },
    ))

    // ===== OCR图文 =====
    add(Tool(name="douyin_ocr_images",
        description="获取图文作品图片用于OCR识别。Params: aweme_id。返回图片链接，AI可用RikkaHub的多模态能力直接识别图中文字。",
        needsApproval=false,
        parameters={ InputSchema.Obj(properties=buildJsonObject{
            put("aweme_id",buildJsonObject{put("type","string");put("description","作品ID")})
        },required=listOf("aweme_id")) },
        execute={ args ->
            val id=aid(args.jsonObject["aweme_id"]?.jsonPrimitive?.contentOrNull?:error("id"))
                        val params=mutableMapOf<String,Any?>("aweme_id" to id,"verifyFp" to "verify_","fp" to "verify_")
            val detail = s.api("/aweme/v1/web/aweme/detail/", q(params))
            listOf(UIMessagePart.Text(buildJsonObject{
                put("aweme_id",id); put("detail",detail.take(5000))
                put("ocr_tip","RikkaHub的AI可以直接识别图片中的文字。请从返回的images数组中获取图片URL，AI即可读取图中文字。")
            }.toString()))
        },
    ))
}

/**
 * 在 App 内置 WebView 中加载抖音登录页，提取二维码图片（base64 data URI）。
 * 抖音网页自身执行 JS 签名，二维码有效；不依赖失效的 passport API，不落盘。
 */
private suspend fun extractQrFromPage(context: android.content.Context, url: String): String? {
    return withContext(Dispatchers.Main) {
        runCatching {
            val webView = WebView(context.applicationContext)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.loadWithOverviewMode = true
            webView.settings.useWideViewPort = true
            webView.setBackgroundColor(Color.WHITE)

            val deferred = CompletableDeferred<String?>()
            var destroyed = false
            fun destroySafe() {
                if (!destroyed) {
                    destroyed = true
                    webView.destroy()
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // 多次尝试提取二维码（等待渲染 + 重试），提高成功率
                    view?.postDelayed({
                        var attempts = 0
                        val tryExtract = object : Runnable {
                            override fun run() {
                                attempts++
                                view.evaluateJavascript(DOUYIN_QR_JS) { result ->
                                    val clean = result?.removeSurrounding("\"")
                                        ?.replace("\\u002F", "/")
                                        ?.replace("\\/", "/")
                                        ?.trim()
                                    if (!clean.isNullOrBlank() && clean != "null" && (clean.startsWith("data:") || clean.startsWith("http"))) {
                                        deferred.complete(clean)
                                        destroySafe()
                                    } else if (attempts < 3) {
                                        // 未提取到，2秒后重试（最多3次）
                                        view.postDelayed(this, 2000)
                                    } else {
                                        deferred.complete(null)
                                        destroySafe()
                                    }
                                }
                            }
                        }
                        tryExtract.run()
                    }, 4000)
                }
            }

            webView.loadUrl(url)

            // 等待提取结果（最多 25 秒）
            val result = withTimeoutOrNull(25000) { deferred.await() }
            destroySafe()
            result
        }.getOrNull()
    }
}

/** 在抖音登录页 DOM 中查找二维码图片（img 或 canvas） */
private const val DOUYIN_QR_JS = """
(function(){
  try {
    // 1. 找 img 标签（data URI 或较长的二维码图 URL）
    var imgs = document.querySelectorAll('img');
    for (var i = 0; i < imgs.length; i++) {
      var s = imgs[i].src || '';
      if (s.indexOf('data:') === 0 || s.length > 200) return s;
    }
    // 2. 找 canvas 二维码
    var canvases = document.querySelectorAll('canvas');
    for (var j = 0; j < canvases.length; j++) {
      try {
        var d = canvases[j].toDataURL('image/png');
        if (d && d.length > 100) return d;
      } catch(e) {}
    }
  } catch(e) {}
  return '';
})()
"""
