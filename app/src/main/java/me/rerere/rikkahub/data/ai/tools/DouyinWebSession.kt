/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 抖音隐形 WebView 会话 —— AI 的"自动化浏览器"。
 *
 * 原理：App 内置 WebView 在后台加载抖音页面，自动执行抖音 JS
 * （生成 X-Bogus/a_bogus 签名、ttwid cookie），保持有效会话。
 * AI 通过 JS 桥在 WebView 内执行 fetch 请求抖音 API，
 * 全程软件内自动完成，不需要用户手机任何操作。
 *
 * 不依赖失效的 passport HTTP API，不依赖工作区/外部服务。
 */
@SuppressLint("SetJavaScriptEnabled")
class DouyinWebSession(private val context: Context) {

    private var webView: WebView? = null
    private var ready = false
    private var pending: CompletableDeferred<String?>? = null
    private var bridgeAttached = false

    /** JS 桥（只挂载一次）：JS 侧 fetch 完成后把结果转给当前挂起的请求 */
    private val bridge = object {
        @JavascriptInterface
        fun postResult(data: String) {
            pending?.complete(data)
            pending = null
        }
    }

    /** 在后台加载抖音首页建立会话（首次调用时） */
    private suspend fun ensureReady() {
        if (ready) return
        withContext(Dispatchers.Main) {
            if (webView == null) {
                val view = WebView(context.applicationContext)
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                view.settings.loadWithOverviewMode = true
                view.settings.useWideViewPort = true
                view.setBackgroundColor(android.graphics.Color.WHITE)
                view.layout(0, 0, 1, 1)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1, View.MeasureSpec.EXACTLY)
                )
                // 用 onPageFinished 标志可靠地等待页面加载完成
                val pageLoaded = CompletableDeferred<Unit>()
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (!pageLoaded.isCompleted) pageLoaded.complete(Unit)
                    }
                }
                if (!bridgeAttached) {
                    view.addJavascriptInterface(bridge, "AndroidBridge")
                    bridgeAttached = true
                }
                webView = view
                view.loadUrl("https://www.douyin.com/")
                // 等待页面加载（最多 15 秒）
                withTimeoutOrNull(15000) { pageLoaded.await() }
                // 再等 3 秒让签名 JS 初始化
                delay(3000)
            }
        }
        ready = true
    }

    /**
     * 在 WebView 会话内执行抖音 API 请求（fetch 同源请求，自动带签名/cookie）。
     * @param path 相对路径，如 /aweme/v1/web/search/item/
     * @param query 查询参数串，如 keyword=美食&count=10
     * @return 接口返回的 JSON 字符串
     */
    suspend fun api(path: String, query: String = ""): String {
        return request(path, query, method = "GET", bodyJson = null)
    }

    /** POST JSON 请求（用于登录后操作：评论/点赞/发布等） */
    suspend fun post(path: String, bodyJson: String): String {
        return request(path, "", method = "POST", bodyJson = bodyJson)
    }

    private suspend fun request(path: String, query: String, method: String, bodyJson: String?): String {
        ensureReady()
        val deferred = CompletableDeferred<String?>()
        pending = deferred
        withContext(Dispatchers.Main) {
            val q = if (query.isNotBlank()) "?$query" else ""
            val bodyJs = if (bodyJson != null) {
                "body: '$bodyJson', headers:{'Accept':'application/json','Content-Type':'application/json'}"
            } else {
                "headers:{'Accept':'application/json'}"
            }
            val target = if (path.startsWith("http")) path else "$path$q"
            val js = """
                (function(){
                  try {
                    fetch('$target', {method:'$method', credentials:'include', $bodyJs})
                      .then(function(r){ return r.text(); })
                      .then(function(t){ AndroidBridge.postResult(t); })
                      .catch(function(e){ AndroidBridge.postResult('{"error":"' + e.message + '"}'); });
                  } catch(e) {
                    AndroidBridge.postResult('{"error":"' + e.message + '"}');
                  }
                })()
            """.trimIndent()
            webView?.evaluateJavascript(js, null)
        }
        // 等待 JS 桥回调结果（最多 20 秒）
        return withTimeoutOrNull(20000) { deferred.await() } ?: """{"error":"timeout"}"""
    }

    /** 检测当前会话是否已登录（WebView cookie 中是否有 sessionid） */
    suspend fun isLoggedIn(): Boolean {
        ensureReady()
        return withContext(Dispatchers.Main) {
            val cookies = android.webkit.CookieManager.getInstance()
                .getCookie("https://www.douyin.com") ?: ""
            cookies.contains("sessionid") || cookies.contains("sessionid_ss")
        }
    }

    /** 获取当前会话 cookie（用于持久化/检查） */
    suspend fun getCookie(): String {
        ensureReady()
        return withContext(Dispatchers.Main) {
            android.webkit.CookieManager.getInstance()
                .getCookie("https://www.douyin.com") ?: ""
        }
    }

    /** 释放 WebView 资源 */
    suspend fun destroy() {
        withContext(Dispatchers.Main) {
            webView?.destroy()
            webView = null
            ready = false
            bridgeAttached = false
        }
    }
}
