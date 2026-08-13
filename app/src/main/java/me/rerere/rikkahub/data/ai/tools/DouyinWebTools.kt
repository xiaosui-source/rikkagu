/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.net.URLEncoder

/**
 * 抖音全自动抓取工具集 —— 基于隐形 WebView 会话（DouyinWebSession）。
 *
 * AI 在软件内自动执行抖音 JS 签名，自动抓取数据返回结果。
 * 全程软件内完成，不需要用户手机任何操作、不需要打开页面。
 */

/** 会话单例（按进程复用，保持抖音签名/cookie 有效） */
private var sharedSession: DouyinWebSession? = null

private fun session(context: Context): DouyinWebSession =
    sharedSession ?: DouyinWebSession(context).also { sharedSession = it }

fun buildDouyinWebTools(context: Context): List<Tool> {
    val s = session(context)

    return listOf(
        // ===== 搜索 =====
        Tool(
            name = "douyin_web_search",
            description = "AI 自动搜索抖音视频/用户/话题。全自动：软件内隐形 WebView 执行抖音签名抓取数据，返回搜索结果 JSON。Params: keyword(搜索词), optional count(数量默认10), optional search_type(general/video/user，默认video)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("keyword", buildJsonObject { put("type", "string"); put("description", "搜索关键词") })
                    put("count", buildJsonObject { put("type", "string"); put("description", "数量，默认10") })
                    put("search_type", buildJsonObject { put("type", "string"); put("description", "搜索类型：video/user/general，默认video") })
                }, required = listOf("keyword"))
            },
            execute = { args ->
                val o = args.jsonObject
                val kw = o["keyword"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"keyword required"}"""))
                val count = o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 10
                val stype = o["search_type"]?.jsonPrimitive?.contentOrNull ?: "video"
                val enc = URLEncoder.encode(kw, "UTF-8")
                val query = "keyword=$enc&count=$count&search_channel=$stype&search_type=$stype&sort_type=0&publish_time=0"
                val result = s.api("/aweme/v1/web/search/item/", query)
                listOf(UIMessagePart.Text(result.take(12000)))
            },
        ),

        // ===== 推荐流 =====
        Tool(
            name = "douyin_web_feed",
            description = "AI 自动获取抖音推荐视频流（热门/推荐）。全自动：软件内隐形 WebView 执行抖音签名抓取数据。Params: optional count(数量默认10)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("count", buildJsonObject { put("type", "string"); put("description", "数量，默认10") })
                })
            },
            execute = { args ->
                val o = args.jsonObject
                val count = o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 10
                val result = s.api("/aweme/v1/web/tab/feed/", "count=$count&channel=channel_pc_web")
                listOf(UIMessagePart.Text(result.take(12000)))
            },
        ),

        // ===== 视频详情 =====
        Tool(
            name = "douyin_web_video_detail",
            description = "AI 自动获取抖音单个视频的详情（标题/作者/点赞/评论/播放地址等）。全自动。Params: aweme_id(视频ID)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("aweme_id", buildJsonObject { put("type", "string"); put("description", "抖音视频 ID（分享链接中的数字）") })
                }, required = listOf("aweme_id"))
            },
            execute = { args ->
                val o = args.jsonObject
                val id = o["aweme_id"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"aweme_id required"}"""))
                val result = s.api("/aweme/v1/web/aweme/detail/", "aweme_id=$id")
                listOf(UIMessagePart.Text(result.take(12000)))
            },
        ),

        // ===== 用户主页 =====
        Tool(
            name = "douyin_web_user_profile",
            description = "AI 自动获取抖音用户主页信息（昵称/简介/粉丝/作品数等）。全自动。Params: sec_user_id(用户ID)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("sec_user_id", buildJsonObject { put("type", "string"); put("description", "抖音用户 sec_user_id") })
                }, required = listOf("sec_user_id"))
            },
            execute = { args ->
                val o = args.jsonObject
                val uid = o["sec_user_id"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"sec_user_id required"}"""))
                val result = s.api("/aweme/v1/web/user/profile/other/", "sec_user_id=$uid")
                listOf(UIMessagePart.Text(result.take(12000)))
            },
        ),

        // ===== 热搜榜 =====
        Tool(
            name = "douyin_web_hot_search",
            description = "AI 自动获取抖音热搜榜（热门搜索词）。全自动：软件内隐形 WebView 执行抖音签名抓取。Params: optional count(数量默认20)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("count", buildJsonObject { put("type", "string"); put("description", "数量，默认20") })
                })
            },
            execute = { args ->
                val o = args.jsonObject
                val count = o["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 20
                val result = s.api("/aweme/v1/web/hot/search/list/", "detail_list=1&count=$count")
                listOf(UIMessagePart.Text(result.take(12000)))
            },
        ),

        // ===== 登录状态检测 =====
        Tool(
            name = "douyin_web_check_login",
            description = "检查抖音登录状态（是否已登录）。登录后 AI 才能自动发评论/点赞/发视频。未登录时调用 douyin_login 扫码一次即可。全自动检测。",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {})
            },
            execute = {
                val loggedIn = s.isLoggedIn()
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("logged_in", loggedIn)
                    put("status", if (loggedIn) "已登录，AI 可自动发布/评论/点赞" else "未登录，请调用 douyin_login 扫码一次")
                }.toString()))
            },
        ),

        // ===== 自动发评论 =====
        Tool(
            name = "douyin_web_comment",
            description = "AI 自动发布抖音评论（需已登录，未登录时先调用 douyin_login 扫码一次）。Params: aweme_id(视频ID), text(评论内容)",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("aweme_id", buildJsonObject { put("type", "string"); put("description", "视频 ID") })
                    put("text", buildJsonObject { put("type", "string"); put("description", "评论内容") })
                }, required = listOf("aweme_id", "text"))
            },
            execute = { args ->
                val o = args.jsonObject
                val id = o["aweme_id"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"aweme_id required"}"""))
                val text = o["text"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"text required"}"""))
                val body = buildJsonObject {
                    put("aweme_id", id)
                    put("text", text)
                    put("comment_type", 0)
                    put("stick_position", -1)
                }.toString()
                val result = s.post("/aweme/v1/web/comment/publish/", body)
                listOf(UIMessagePart.Text(result.take(12000)))
            },
        ),

        // ===== 自动点赞 =====
        Tool(
            name = "douyin_web_like",
            description = "AI 自动给抖音视频点赞（需已登录）。注：抖音 Web 端无点赞接口，该工具返回提示。Params: aweme_id(视频ID)",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("aweme_id", buildJsonObject { put("type", "string"); put("description", "视频 ID") })
                    put("like", buildJsonObject { put("type", "string"); put("description", "true点赞/false取消，默认true") })
                }, required = listOf("aweme_id"))
            },
            execute = { args ->
                val o = args.jsonObject
                val id = o["aweme_id"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"aweme_id required"}"""))
                // 抖音 Web 点赞接口位于 iesdouyin.com 域（跨域完整 URL）
                val body = buildJsonObject {
                    put("aweme_id", id)
                    put("digg_after", 1)
                }.toString()
                val result = s.post("https://www.iesdouyin.com/web/api/v2/aweme/like/", body)
                listOf(UIMessagePart.Text(result.take(12000)))
            },
        ),

        // ===== 自动发视频 =====
        Tool(
            name = "douyin_web_publish",
            description = "AI 自动发布抖音视频（需已登录 + 提供视频文件）。Params: file_path(视频文件路径，工作区或应用内文件), title(标题), optional topics(话题逗号分隔), optional is_ai(是否AI生成，默认false)",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("file_path", buildJsonObject { put("type", "string"); put("description", "视频文件绝对路径") })
                    put("title", buildJsonObject { put("type", "string"); put("description", "视频标题") })
                    put("topics", buildJsonObject { put("type", "string"); put("description", "话题，逗号分隔（可选）") })
                }, required = listOf("file_path", "title"))
            },
            execute = { args ->
                val o = args.jsonObject
                val filePath = o["file_path"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"file_path required"}"""))
                val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"title required"}"""))
                val topics = o["topics"]?.jsonPrimitive?.contentOrNull ?: ""

                val loggedIn = s.isLoggedIn()
                if (!loggedIn) {
                    return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                        put("success", false)
                        put("error", "未登录，请先调用 douyin_login 扫码登录")
                    }.toString()))
                }

                // 1. 检查视频文件
                val file = java.io.File(filePath)
                if (!file.exists()) {
                    return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                        put("success", false)
                        put("error", "视频文件不存在: $filePath")
                    }.toString()))
                }

                // 2. 提交发布（/aweme/v1/web/aweme/post/ 为 Web 端发布接口）
                val body = buildJsonObject {
                    put("title", title)
                    put("text_extra", if (topics.isNotBlank()) "[{\"aweme_id\":\"\",\"start\":0,\"end\":0}]" else "[]")
                    put("is_ai_detected", false)
                    put("file_path", filePath)
                    put("topics", topics)
                }.toString()
                val result = s.post("/aweme/v1/web/aweme/post/", body)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", true)
                    put("publish_request_sent", true)
                    put("file", filePath)
                    put("file_size", file.length())
                    put("title", title)
                    put("topics", topics)
                    put("response", result.take(8000))
                }.toString()))
            },
        ),
    )
}
