/*
 * 灵犀 Lingxi
 * 参考自 Operit AI (https://github.com/AAswordman/Operit) 的 WebSession 工具体系
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "BrowserTools"
private const val DEFAULT_TIMEOUT_MS = 15_000L

/**
 * 网页会话工具（参考 Operit WebSession 轻量实现）：
 * - browser_navigate: 打开 URL，等待加载完成
 * - browser_evaluate: 在当前页面执行任意 JS 并返回结果
 * - browser_snapshot: 获取页面文本内容（标题 + 正文摘要）
 * - browser_click: 通过 CSS 选择器点击元素
 * - browser_fill: 通过 CSS 选择器填表单
 *
 * 全部基于 Android 原生 WebView（headless，不可见），主线程操作 + CountDownLatch 同步。
 */
fun createBrowserTools(context: Context): List<Tool> {
    val webViewRef = AtomicReference<WebView?>()
    val mainHandler = Handler(Looper.getMainLooper())

    /** 确保 WebView 在主线程创建 */
    fun ensureWebView(): WebView {
        webViewRef.get()?.let { return it }
        val latch = CountDownLatch(1)
        mainHandler.post {
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onResult(value: String) {
                        Log.d(TAG, "JS result: ${value.take(200)}")
                    }
                }, "AndroidBridge")
            }
            webViewRef.set(wv)
            latch.countDown()
        }
        latch.await(5, TimeUnit.SECONDS)
        return webViewRef.get()!!
    }

    /** 在主线程执行并等待结果 */
    fun runOnMainSync(timeoutMs: Long = DEFAULT_TIMEOUT_MS, block: (WebView) -> Unit): Boolean {
        val wv = ensureWebView()
        val latch = CountDownLatch(1)
        mainHandler.post {
            block(wv)
            latch.countDown()
        }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /** 在页面执行 JS 并同步获取返回值 */
    fun evaluateJs(js: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String {
        val resultRef = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        mainHandler.post {
            val wv = ensureWebView()
            wv.evaluateJavascript(js) { value ->
                resultRef.set(value)
                latch.countDown()
            }
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return resultRef.get() ?: ""
    }

    return listOf(
        // 1. browser_navigate
        Tool(
            name = "browser_navigate",
            description = "Navigate the built-in headless browser to a URL and wait for the page to load. " +
                "Use when the AI needs to open a webpage for scraping, interaction, or reading content.",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("url", buildJsonObject {
                            put("type", "string")
                            put("description", "The URL to navigate to")
                        })
                    },
                    required = listOf("url")
                )
            },
            execute = { input ->
                val url = input.jsonObject["url"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"url required"}"""))
                val latch = CountDownLatch(1)
                mainHandler.post {
                    val wv = ensureWebView()
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            latch.countDown()
                        }
                    }
                    wv.loadUrl(url)
                }
                val loaded = latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                val title = evaluateJs("document.title")
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", loaded)
                    put("url", url)
                    put("title", title.trim('"'))
                }.toString()))
            }
        ),

        // 2. browser_evaluate
        Tool(
            name = "browser_evaluate",
            description = "Evaluate arbitrary JavaScript in the current browser page and return the result. " +
                "Use for DOM manipulation, data extraction, clicking elements, filling forms, etc.",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("script", buildJsonObject {
                            put("type", "string")
                            put("description", "JavaScript code to execute. Use return to get a value.")
                        })
                    },
                    required = listOf("script")
                )
            },
            execute = { input ->
                val script = input.jsonObject["script"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"script required"}"""))
                val wrapped = "(function(){ try { $script } catch(e) { return 'ERROR: '+e.message } })()"
                val result = evaluateJs(wrapped)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("result", JsonPrimitive(result))
                }.toString()))
            }
        ),

        // 3. browser_snapshot
        Tool(
            name = "browser_snapshot",
            description = "Get a text snapshot of the current page: title, URL, visible text content, and a list of clickable elements.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {})
            },
            execute = {
                val title = evaluateJs("document.title")
                val url = evaluateJs("location.href")
                val text = evaluateJs("document.body.innerText.substring(0, 5000)")
                val links = evaluateJs("""
                    JSON.stringify(Array.from(document.querySelectorAll('a,button,input,select,textarea')).slice(0,50).map(function(e){
                        return {tag:e.tagName, id:e.id||'', class:e.className||'', text:(e.innerText||e.value||'').substring(0,80), href:e.href||''}
                    }))
                """)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("title", title.trim('"'))
                    put("url", url.trim('"'))
                    put("text", JsonPrimitive(text.trim('"')))
                    put("elements", JsonPrimitive(links.trim('"')))
                }.toString()))
            }
        ),

        // 4. browser_click
        Tool(
            name = "browser_click",
            description = "Click an element in the browser page by CSS selector.",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("selector", buildJsonObject {
                            put("type", "string")
                            put("description", "CSS selector of the element to click, e.g. '#login-btn' or '.submit'")
                        })
                    },
                    required = listOf("selector")
                )
            },
            execute = { input ->
                val selector = input.jsonObject["selector"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"selector required"}"""))
                val result = evaluateJs("""
                    (function(){
                        var el = document.querySelector('$selector');
                        if(!el) return 'NOT_FOUND';
                        el.click();
                        return 'CLICKED';
                    })()
                """)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("result", JsonPrimitive(result.trim('"')))
                    put("selector", selector)
                }.toString()))
            }
        ),

        // 5. browser_fill
        Tool(
            name = "browser_fill",
            description = "Fill a form field in the browser page by CSS selector.",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("selector", buildJsonObject {
                            put("type", "string")
                            put("description", "CSS selector of the input field")
                        })
                        put("value", buildJsonObject {
                            put("type", "string")
                            put("description", "Value to fill in")
                        })
                    },
                    required = listOf("selector", "value")
                )
            },
            execute = { input ->
                val selector = input.jsonObject["selector"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"selector required"}"""))
                val value = input.jsonObject["value"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"value required"}"""))
                val escaped = value.replace("\\", "\\\\").replace("'", "\\'")
                val result = evaluateJs("""
                    (function(){
                        var el = document.querySelector('$selector');
                        if(!el) return 'NOT_FOUND';
                        el.value = '$escaped';
                        el.dispatchEvent(new Event('input', {bubbles:true}));
                        el.dispatchEvent(new Event('change', {bubbles:true}));
                        return 'FILLED';
                    })()
                """)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("result", JsonPrimitive(result.trim('"')))
                    put("selector", selector)
                }.toString()))
            }
        ),

        // 6. browser_wait_for
        Tool(
            name = "browser_wait_for",
            description = "Wait until a CSS selector element appears in the browser page (or a fixed delay). " +
                "Use after navigate or click when the page is loading dynamically.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("selector", buildJsonObject {
                            put("type", "string")
                            put("description", "CSS selector to wait for (may be empty to just wait a delay)")
                        })
                        put("timeout_ms", buildJsonObject {
                            put("type", "integer")
                            put("description", "Max wait in milliseconds (default 8000)")
                        })
                    },
                    required = emptyList(),
                )
            },
            execute = { input ->
                val selector = input.jsonObject["selector"]?.jsonPrimitive?.contentOrNull
                val timeoutMs = input.jsonObject["timeout_ms"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 8000
                val start = System.currentTimeMillis()
                var found = false
                while (System.currentTimeMillis() - start < timeoutMs) {
                    if (selector.isNullOrBlank()) break
                    val check = evaluateJs("!!document.querySelector('$selector')")
                    if (check.trim('"') == "true") { found = true; break }
                    Thread.sleep(200)
                }
                if (selector.isNullOrBlank()) {
                    Thread.sleep(timeoutMs.coerceAtMost(5000).toLong())
                }
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("selector", selector ?: "")
                    put("found", found)
                    put("waited_ms", System.currentTimeMillis() - start)
                }.toString()))
            }
        ),

        // 7. browser_current_url
        Tool(
            name = "browser_current_url",
            description = "Get the current URL and title of the browser page. Identical to a lightweight snapshot.",
            needsApproval = false,
            parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
            execute = {
                val url = evaluateJs("location.href")
                val title = evaluateJs("document.title")
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("url", url.trim('"'))
                    put("title", title.trim('"'))
                }.toString()))
            }
        ),
    )
}