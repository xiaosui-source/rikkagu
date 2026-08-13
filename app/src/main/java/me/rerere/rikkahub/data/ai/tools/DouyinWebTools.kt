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
    )
}
