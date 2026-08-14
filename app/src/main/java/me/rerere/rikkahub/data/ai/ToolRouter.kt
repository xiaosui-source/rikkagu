/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 工具路由（纯客户端兜底）：弱模型不支持原生 tool_calls 时，
 * 根据用户消息匹配工具并自动调用（不注入任何系统提示/消息提醒）。
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
        // ===== 12306 火车票 =====
        if (msg.contains("火车票") || msg.contains("车次") || msg.contains("高铁") ||
            msg.contains("列车") || msg.contains("12306") || msg.contains("查票")
        ) {
            val from = (Regex("(?:从|出发地)[:：\s]*([\u4e00-\u9fa5]{2,4})").find(userMessage)?.groupValues?.get(1)) ?: ""
            val to = (Regex("(?:到|目的地)[:：\s]*([\u4e00-\u9fa5]{2,4})").find(userMessage)?.groupValues?.get(1)) ?: ""
            return "ticket_search" to buildArgs("from" to from, "to" to to)
        }
        // ===== HTTP 请求 =====
        if (msg.contains("http_execute") || msg.contains("请求") && msg.contains("http") ||
            msg.contains("访问") && msg.contains("http") || msg.contains("抓取") && msg.contains("http")
        ) {
            val url = (Regex("https?://[^\s]+").find(msg)?.value) ?: ""
            return "http_execute" to buildArgs("url" to url)
        }
        // ===== 记忆 =====
        if (msg.contains("记住") || msg.contains("记忆") || msg.contains("回忆") ||
            msg.contains("忘了") || msg.contains("记一下")
        ) {
            return "memory_set" to buildArgs("content" to userMessage.take(200))
        }
        // ===== APK 逆向 =====
        if (msg.contains("apk") || msg.contains("逆向") || msg.contains("反编译") || msg.contains("apk工具")) {
            return "apk_decode" to buildArgs()
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
