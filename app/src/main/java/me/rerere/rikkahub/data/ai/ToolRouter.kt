/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Kotlin 工具调度器：纯 Kotlin 规则匹配，无需 JS / 提示词式。
 *
 * 根据用户消息自动判断要调用哪个工具、提取参数。
 * 弱模型不需要输出工具调用文本，由本调度器在生成前自动调用工具
 * （结构化 Tool 执行），工具结果注入上下文后模型基于结果回答。
 */
object ToolRouter {

    /**
     * 根据用户消息路由到工具。
     * @return (工具名, 参数JSON字符串)；不匹配返回 null
     */
    fun route(userMessage: String): Pair<String, String>? {
        val msg = userMessage.lowercase()

        // ===== 抖音搜索 =====
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
        // ===== 热搜 =====
        if (msg.contains("热搜") || msg.contains("热门榜")) {
            return "douyin_web_hot_search" to buildArgs()
        }
        // ===== 视频详情 =====
        if (msg.contains("详情") || msg.contains("v.douyin") || msg.contains("douyin.com/video") || msg.contains("aweme_id")) {
            val id = Regex("\\d{15,20}").find(msg)?.value ?: ""
            return "douyin_web_video_detail" to buildArgs("aweme_id" to id)
        }
        // ===== 登录 =====
        if (msg.contains("登录") || msg.contains("扫码") || msg.contains("登陆")) {
            return "douyin_login" to buildArgs()
        }
        // ===== 推荐流 =====
        if (msg.contains("推荐") || msg.contains("热门视频") || msg.contains("首页视频")) {
            return "douyin_web_feed" to buildArgs()
        }
        // ===== 用户主页 =====
        if (msg.contains("用户") || msg.contains("主页") || msg.contains("关注的人")) {
            val uid = Regex("MS4w[\\w-]+").find(msg)?.value ?: ""
            return "douyin_web_user_profile" to buildArgs("sec_user_id" to uid)
        }
        // ===== 评论 =====
        if (msg.contains("评论")) {
            val vid = Regex("\\d{15,20}").find(msg)?.value ?: ""
            return "douyin_web_comment" to buildArgs("aweme_id" to vid, "text" to "")
        }
        // ===== 点赞 =====
        if (msg.contains("点赞")) {
            val vid2 = Regex("\\d{15,20}").find(msg)?.value ?: ""
            return "douyin_web_like" to buildArgs("aweme_id" to vid2)
        }
        // ===== 发布视频 =====
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
