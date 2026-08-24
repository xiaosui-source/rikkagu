/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.event

sealed class AppEvent {
    data class Speak(val text: String) : AppEvent()
    data class EmojiSelected(val emoji: String) : AppEvent()

    /**
     * AI 在文字聊天中主动请求发起语音通话.
     * 由 request_voice_call 工具发出, RouteActivity 监听后弹出来电界面.
     */
    data class RequestVoiceCall(val conversationId: String) : AppEvent()

    /** MCP OAuth 授权完成后经 deep link 回传的结果。 */
    data class McpOAuthCallback(
        val state: String?,
        val code: String?,
        val error: String?,
    ) : AppEvent()

    /**
     * 请求在 App 内置 WebView 中打开指定 URL。
     * 由 douyin_open_login 等 MCP 内置工具发出，RouteActivity 监听后导航到内置浏览器页，
     * 避免跳转到外部浏览器。
     */
    data class OpenWebView(val url: String) : AppEvent()
}
