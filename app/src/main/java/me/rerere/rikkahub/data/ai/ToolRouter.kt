/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 工具路由：模型未通过原生 tool_calls 调用工具时，
 * 由客户端检测用户意图并补上工具执行（展示与原生 tool_calls 完全一致）。
 */
object ToolRouter {

    fun route(userMessage: String): Pair<String, String>? {
        val msg = userMessage.lowercase()

        if ((msg.contains("douyin_web_search") || msg.contains("搜索") || msg.contains("查找") ||
                msg.contains("搜一下") || msg.contains("帮我搜")) &&
            !(msg.contains("详情") || msg.contains("评论") || msg.contains("点赞") || msg.contains("发布"))
        ) {
            val kw = userMessage
                .replace(Regex(".*(?:搜索|查找|帮我搜|搜一下)[:：\\s]*"), "")
                .trim()
                .ifBlank { "热门" }
            return "douyin_web_search" to buildArgs("keyword" to kw)
        }
        if (msg.contains("热搜") || msg.contains("热门榜")) {
            return "douyin_web_hot_search" to buildArgs()
        }
        if (msg.contains("详情") || msg.contains("v.douyin") || msg.contains("douyin.com/video") || msg.contains("aweme_id")) {
            val id = Regex("\\d{15,20}").find(msg)?.value ?: ""
            return "douyin_web_video_detail" to buildArgs("aweme_id" to id)
        }
        if (msg.contains("登录") || msg.contains("扫码") || msg.contains("登陆")) {
            return "douyin_login" to buildArgs()
        }
        if (msg.contains("推荐") || msg.contains("热门视频") || msg.contains("首页视频")) {
            return "douyin_web_feed" to buildArgs()
        }
        if (msg.contains("用户") || msg.contains("主页") || msg.contains("关注的人")) {
            val uid = Regex("MS4w[\\w-]+").find(msg)?.value ?: ""
            return "douyin_web_user_profile" to buildArgs("sec_user_id" to uid)
        }
        if (msg.contains("评论")) {
            val vid = Regex("\\d{15,20}").find(msg)?.value ?: ""
            return "douyin_web_comment" to buildArgs("aweme_id" to vid, "text" to "")
        }
        if (msg.contains("点赞")) {
            val vid2 = Regex("\\d{15,20}").find(msg)?.value ?: ""
            return "douyin_web_like" to buildArgs("aweme_id" to vid2)
        }
        if (msg.contains("发布") || msg.contains("上传") && msg.contains("视频") ||
            msg.contains("发个视频") || msg.contains("发视频")
        ) {
            val title = userMessage
                .replace(Regex(".*(?:发布|上传|发个|发)[:：\\s]*"), "")
                .take(50)
            return "douyin_web_publish" to buildArgs("file_path" to "", "title" to title)
        }
        return null
    }

    private fun buildArgs(vararg pairs: Pair<String, String>): String {
        if (pairs.isEmpty()) return "{}"
        return buildJsonObject {
            pairs.forEach { (k, v) -> put(k, v) }
        }.toString()
    }
}
