/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai

import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * JS 工具调度器：用 QuickJS 执行 JS 规则脚本，
 * 根据用户消息自动判断要调用哪个工具、提取参数。
 *
 * 解决弱模型提示词式工具调用混乱的问题：
 * 模型不再需要输出工具调用 JSON/XML（弱模型搞乱），
 * 而是由 JS 规则在生成前自动调度工具（结构化 Tool 执行），
 * 工具结果注入上下文后，模型只需基于结果正常回答。
 */
object ToolRouterJs {

    private val routerJs = """
        function route(input) {
            var msg = input.toLowerCase();
            var out = null;
            function set(tool, args) { out = {tool: tool, args: args}; return true; }

            // ===== 抖音 =====
            // 搜索
            if (/douyin_web_search|搜索|查找|搜一下|帮我搜/.test(msg) && !/详情|评论|点赞|发布/.test(msg)) {
                var kw = msg.replace(/.*(?:搜索|查找|帮我搜|搜一下)[:：\s]*/, '').trim();
                if (!kw) kw = '热门';
                set('douyin_web_search', {keyword: kw});
            }
            // 热搜
            else if (/热搜|热门榜/.test(msg)) {
                set('douyin_web_hot_search', {});
            }
            // 视频详情（含链接或ID）
            else if (/(?:视频)?详情|v\.douyin|douyin\.com\/video|aweme_id/.test(msg)) {
                var id = (msg.match(/\d{15,20}/) || [''])[0];
                set('douyin_web_video_detail', {aweme_id: id});
            }
            // 登录
            else if (/登录|扫码|登陆/.test(msg)) {
                set('douyin_login', {});
            }
            // 推荐流
            else if (/推荐|热门视频|首页视频/.test(msg)) {
                set('douyin_web_feed', {});
            }
            // 用户主页
            else if (/用户|主页|关注的人/.test(msg)) {
                var uid = (msg.match(/MS4w[\w-]+/) || [''])[0];
                set('douyin_web_user_profile', {sec_user_id: uid});
            }
            // 评论
            else if (/评论/.test(msg)) {
                var vid = (msg.match(/\d{15,20}/) || [''])[0];
                set('douyin_web_comment', {aweme_id: vid, text: ''});
            }
            // 点赞
            else if (/点赞/.test(msg)) {
                var vid2 = (msg.match(/\d{15,20}/) || [''])[0];
                set('douyin_web_like', {aweme_id: vid2});
            }
            // 发布视频
            else if (/发布|上传.*视频|发个视频|发视频/.test(msg)) {
                set('douyin_web_publish', {file_path: '', title: msg.replace(/.*(?:发布|上传|发个|发)[:：\s]*/, '').slice(0, 50)});
            }
            return out;
        }
        route(input);
    """

    /**
     * 根据用户消息路由到工具。
     * @return (工具名, 参数JSON字符串)；不匹配返回 null
     */
    fun route(userMessage: String): Pair<String, String>? {
        val safeInput = userMessage
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .take(200)
        return try {
            val context = QuickJSContext.create()
            try {
                val result = context.evaluate("(function(input){$routerJs})(JSON.parse('\"$safeInput\"'))")
                val jsonStr = result.toString()
                if (jsonStr.isBlank() || jsonStr == "null" || jsonStr == "undefined") return null
                val obj = Json.parseToJsonElement(jsonStr).jsonObject
                val tool = obj["tool"]?.jsonPrimitive?.contentOrNull ?: return null
                val args = obj["args"]?.jsonObject ?: JsonObject(emptyMap())
                tool to args.toString()
            } finally {
                context.destroy()
            }
        } catch (e: Exception) {
            null
        }
    }
}
