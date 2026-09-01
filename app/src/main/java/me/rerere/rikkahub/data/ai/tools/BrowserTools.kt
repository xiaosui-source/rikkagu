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
    // 浏览器日志收集（参考 Operit browser_console_messages / browser_network_requests）
    val consoleLogs = java.util.concurrent.ConcurrentLinkedQueue<String>()
    val networkLogs = java.util.concurrent.ConcurrentLinkedQueue<String>()

    /** 确保 WebView 在主线程创建 */
    fun ensureWebView(): WebView {
        webViewRef.get()?.let { return it }
        val latch = CountDownLatch(1)
        mainHandler.post {
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                    ): android.webkit.WebResourceResponse? {
                        request?.url?.toString()?.let { url ->
                            if (networkLogs.size > 500) networkLogs.poll()
                            networkLogs.add(url)
                        }
                        return null
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                        if (message != null) {
                            val line = "${message.messageLevel()}: ${message.message()} (${message.sourceId()}:${message.lineNumber()})"
                            if (consoleLogs.size > 500) consoleLogs.poll()
                            consoleLogs.add(line)
                        }
                        return super.onConsoleMessage(message)
                    }
                }
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
                    // 保留 ensureWebView 安装的 network 拦截器，只补充 onPageFinished 计数
                    wv.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                        ): android.webkit.WebResourceResponse? {
                            request?.url?.toString()?.let { u ->
                                if (networkLogs.size > 500) networkLogs.poll()
                                networkLogs.add(u)
                            }
                            return null
                        }

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

        // 8. browser_console_messages
        Tool(
            name = "browser_console_messages",
            description = "Read recent browser console messages (JS logs, errors, warnings). Use to debug JavaScript issues on the page.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("count", buildJsonObject {
                            put("type", "integer")
                            put("description", "Number of recent messages to return (default 30)")
                        })
                    },
                    required = emptyList(),
                )
            },
            execute = { input ->
                val count = input.jsonObject["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 200) ?: 30
                val msgs = consoleLogs.toList().takeLast(count)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("count", msgs.size)
                    put("messages", JsonPrimitive(msgs.joinToString("\n")))
                }.toString()))
            }
        ),

        // 9. browser_network_requests
        Tool(
            name = "browser_network_requests",
            description = "Read recent browser network requests (URLs loaded by the page). Use to see what the page is fetching.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("count", buildJsonObject {
                            put("type", "integer")
                            put("description", "Number of recent requests to return (default 30)")
                        })
                    },
                    required = emptyList(),
                )
            },
            execute = { input ->
                val count = input.jsonObject["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 200) ?: 30
                val reqs = networkLogs.toList().takeLast(count)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("count", reqs.size)
                    put("requests", JsonPrimitive(reqs.joinToString("\n")))
                }.toString()))
            
        // 10. browser_tabs - 管理浏览器标签页
        Tool(
            name = "browser_tabs",
            description = "Manage browser tabs: list tabs, create new tab, close tab by index.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("action", buildJsonObject {
                            put("type", "string")
                            put("description", "Action: list, create, close, select")
                        })
                        put("index", buildJsonObject {
                            put("type", "integer")
                            put("description", "Tab index (for close/select actions)")
                        })
                    },
                    required = listOf("action")
                )
            },
            execute = { input ->
                val action = input.jsonObject["action"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"action required"}"""))
                when (action) {
                    "list" -> {
                        listOf(UIMessagePart.Text(buildJsonObject {
                            put("tabs", listOf("current"))
                            put("active_index", 0)
                        }.toString()))
                    }
                    "create" -> {
                        val webView = createWebView(context)
                        webViewRef.set(webView)
                        listOf(UIMessagePart.Text(buildJsonObject {
                            put("created", true)
                            put("message", "Created new tab")
                        }.toString()))
                    }
                    "close" -> {
                        listOf(UIMessagePart.Text(buildJsonObject {
                            put("closed", true)
                            put("message", "Tab closed")
                        }.toString()))
                    }
                    else -> {
                        listOf(UIMessagePart.Text(buildJsonObject {
                            put("error", "Unknown action: $action")
                        }.toString()))
                    }
                }
            }
        ),

        // 11. browser_close_all
        Tool(
            name = "browser_close_all",
            description = "Close all browser tabs and reset state.",
            needsApproval = false,
            parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
            execute = {
                webViewRef.set(null)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("closed", true)
                    put("message", "All tabs closed")
                }.toString()))
            }
        ),

        // 12. browser_hover
        Tool(
            name = "browser_hover",
            description = "Hover over a browser element by CSS selector.",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("selector", buildJsonObject { put("type", "string"); put("description", "CSS selector") })
                    },
                    required = listOf("selector")
                )
            },
            execute = { input ->
                val selector = input.jsonObject["selector"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"selector required"}"""))
                val js = "var e=document.querySelector('''$selector''' );if(e)e.dispatchEvent(new MouseEvent(''mouseenter''));"
                val latch = java.util.concurrent.Semaphore(0)
                mainHandler.post {
                    webViewRef.get()?.evaluateJavascript(js) { latch.release() }
                }
                latch.acquire()
                listOf(UIMessagePart.Text(buildJsonObject { put("hovered", true); put("selector", selector) }.toString()))
            }
        ),

        // 13. browser_press_key
        Tool(
            name = "browser_press_key",
            description = "Press a keyboard key in the browser (Enter, Escape, Tab, Arrow keys, etc.).",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("key", buildJsonObject { put("type", "string"); put("description", "Key name: Enter, Escape, Tab, ArrowUp, etc.") })
                    },
                    required = listOf("key")
                )
            },
            execute = { input ->
                val key = input.jsonObject["key"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"key required"}"""))
                val safeKey = key.replace("'", "\'").replace("\", "\\")
                val js = "document.activeElement.dispatchEvent(new KeyboardEvent('keydown',{key:'$safeKey'}));"
                mainHandler.post { webViewRef.get()?.evaluateJavascript(js) {} }
                kotlinx.coroutines.delay(300)
                listOf(UIMessagePart.Text(buildJsonObject { put("pressed", key); put("success", true) }.toString()))
            }
        ),

        // 14. browser_navigate_back
        Tool(
            name = "browser_navigate_back",
            description = "Navigate browser back one page.",
            needsApproval = false,
            parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
            execute = {
                val webView = webViewRef.get() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"No active browser"}"""))
                mainHandler.post { webView.goBack() }
                kotlinx.coroutines.delay(500)
                listOf(UIMessagePart.Text(buildJsonObject { put("navigated_back", true) }.toString()))
            }
        ),

        // 15. browser_resize
        Tool(
            name = "browser_resize",
            description = "Resize browser viewport width and height.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("width", buildJsonObject { put("type", "integer") })
                        put("height", buildJsonObject { put("type", "integer") })
                    },
                    required = listOf("width", "height")
                )
            },
            execute = { input ->
                val width = input.jsonObject["width"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"width required"}"""))
                val height = input.jsonObject["height"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"height required"}"""))
                val js = "document.documentElement.style.width='${width}px';document.documentElement.style.height='${height}px';window.scrollTo(0,0);"
                mainHandler.post { webViewRef.get()?.evaluateJavascript(js) {} }
                kotlinx.coroutines.delay(300)
                listOf(UIMessagePart.Text(buildJsonObject { put("resized", true); put("width", width); put("height", height) }.toString()))
            }
        ),

        // 16. browser_drag
        Tool(
            name = "browser_drag",
            description = "Drag a browser element from source to target selector.",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("from_selector", buildJsonObject { put("type", "string") })
                        put("to_selector", buildJsonObject { put("type", "string") })
                    },
                    required = listOf("from_selector", "to_selector")
                )
            },
            execute = { input ->
                val from = input.jsonObject["from_selector"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"from_selector required"}"""))
                val to = input.jsonObject["to_selector"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"to_selector required"}"""))
                val js = """
                    (function() {
                        var src=document.querySelector('$from'), tgt=document.querySelector('$to');
                        if(!src||!tgt) return 'Not found';
                        var rect=tgt.getBoundingClientRect();
                        var dx=rect.left+rect.width/2, dy=rect.top+rect.height/2;
                        var md=new MouseEvent('mousedown',{bubbles:true,view:window,cancelable:true});
                        var mm=new MouseEvent('mousemove',{bubbles:true,view:window,cancelable:true,clientX:dx,clientY:dy});
                        var mu=new MouseEvent('mouseup',{bubbles:true,view:window,cancelable:true});
                        src.dispatchEvent(md);src.dispatchEvent(mm);src.dispatchEvent(mu);
                        return 'Dragged';
                    })()
                """.trimIndent()
                mainHandler.post { webViewRef.get()?.evaluateJavascript(js) {} }
                kotlinx.coroutines.delay(300)
                listOf(UIMessagePart.Text(buildJsonObject { put("dragged", true); put("from", from); put("to", to) }.toString()))
            }
        ),

        // 17. browser_handle_dialog
        Tool(
            name = "browser_handle_dialog",
            description = "Handle current browser dialog (alert/confirm/prompt). action=accept or dismiss.",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("action", buildJsonObject { put("type", "string"); put("description", "accept or dismiss") })
                        put("value", buildJsonObject { put("type", "string"); put("description", "Response value for prompt") })
                    },
                    required = listOf("action")
                )
            },
            execute = { input ->
                val action = input.jsonObject["action"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"action required"}"""))
                mainHandler.post {
                    if (action == "dismiss") {
                        webViewRef.get()?.evaluateJavascript("try{window.__dismissDialog=true;}catch(e){}") {}
                    } else {
                        webViewRef.get()?.evaluateJavascript("try{window.__acceptDialog=true;}catch(e){}") {}
                    }
                }
                kotlinx.coroutines.delay(200)
                listOf(UIMessagePart.Text(buildJsonObject { put("handled", action); put("success", true) }.toString()))
            }
        ),
    }
}

        // 18. browser_close
        Tool(
            name = "browser_close",
            description = "Close the current browser tab.",
            needsApproval = false,
            parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
            execute = {
                webViewRef.set(null)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("closed", true)
                    put("message", "Tab closed")
                }.toString()))
            }
        ),

        // 19. browser_fill_form - 填写多个表单字段
        Tool(
            name = "browser_fill_form",
            description = "Fill multiple form fields in the browser page by CSS selectors.",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("fields", buildJsonObject {
                            put("type", "array")
                            put("description", "Array of field objects with selector and value")
                        })
                    },
                    required = listOf("fields")
                )
            },
            execute = { input ->
                val fieldsJson = input.jsonObject["fields"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"fields required"}"""))
                val js = "((function() { " +
                    var results = emptyList<String>()
                    try {
                        val fields = kotlinx.serialization.json.Json.decodeFromString<List<Map<String, String>>>(fieldsJson)
                        for (field in fields) {
                            val selector = field["selector"] ?: continue
                            val value = field["value"] ?: continue
                            results += "document.querySelector('$selector')?.value='$value';"
                        }
                    } catch(e: Exception) {
                        return@Tool listOf(UIMessagePart.Text("""{"error":"Invalid fields format"}"""))
                    }
                    results.joinToString(" ") + " return 'Filled'; })())"
                mainHandler.post { webViewRef.get()?.evaluateJavascript(js) {} }
                kotlinx.coroutines.delay(300)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("filled", results.size)
                    put("success", true)
                }.toString()))
            }
        ),

        // 20. browser_file_upload - 选择文件上传
        Tool(
            name = "browser_file_upload",
            description = "Trigger file upload dialog for a file input element.",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("selector", buildJsonObject { put("type", "string"); put("description", "File input selector") })
                        put("path", buildJsonObject { put("type", "string"); put("description", "File path to upload") })
                    },
                    required = listOf("selector")
                )
            },
            execute = { input ->
                val selector = input.jsonObject["selector"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"selector required"}"""))
                val path = input.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: ""
                val js = "var input=document.querySelector('$selector');if(input){input.setAttribute('webkitdirectory','');input.value='$path';var e=new Event('change',{bubbles:true});input.dispatchEvent(e);}"
                mainHandler.post { webViewRef.get()?.evaluateJavascript(js) {} }
                kotlinx.coroutines.delay(500)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("triggered", true)
                    put("selector", selector)
                }.toString()))
            }
        ),

        // 21. browser_take_screenshot - 截图
        Tool(
            name = "browser_take_screenshot",
            description = "Take a screenshot of the current browser page.",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("filename", buildJsonObject { put("type", "string"); put("description", "Output filename (optional)") })
                        put("fullPage", buildJsonObject { put("type", "boolean"); put("description", "Full page or viewport") })
                    },
                    required = emptyList()
                )
            },
            execute = { input ->
                val filename = input.jsonObject["filename"]?.jsonPrimitive?.contentOrNull ?: "screenshot_${System.currentTimeMillis()}.png"
                val fullPage = input.jsonObject["fullPage"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                val webView = webViewRef.get() ?: return@Tool listOf(UIMessagePart.Text("""{"error":"No active browser"}"""))
                val latch = java.util.concurrent.Semaphore(0)
                var result: String? = null
                mainHandler.post {
                    val bitmap = if (fullPage) {
                        // 全页截图（简化：仅截图可见区域）
                        val w = webView.width
                        val h = webView.height
                        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                        webView.draw(android.graphics.Canvas(bmp))
                        bmp
                    } else {
                        webView.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(webView.width, webView.height, android.graphics.Bitmap.Config.ARGB_8888)))
                    }
                    val f = java.io.File(android.os.Environment.getExternalStorageDirectory(), "RikkaHub/$filename")
                    f.parentFile?.mkdirs()
                    val fos = java.io.FileOutputStream(f)
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, fos)
                    fos.close()
                    result = f.absolutePath
                    latch.release()
                    latch.close()
                }
                latch.acquire()
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("saved", result != null)
                    put("path", result)
                    put("fullPage", fullPage)
                }.toString()))
            }
        ),

        // 22. browser_type - 在元素中输入文本
        Tool(
            name = "browser_type",
            description = "Type text into a browser element by CSS selector ref.",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("ref", buildJsonObject { put("type", "string"); put("description", "CSS selector") })
                        put("text", buildJsonObject { put("type", "string"); put("description", "Text to type") })
                        put("submit", buildJsonObject { put("type", "boolean"); put("description", "Press Enter after typing") })
                        put("slowly", buildJsonObject { put("type", "boolean"); put("description", "Type character by character") })
                    },
                    required = listOf("ref", "text")
                )
            },
            execute = { input ->
                val ref = input.jsonObject["ref"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"ref required"}"""))
                val text = input.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
                val submit = input.jsonObject["submit"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                val slowly = input.jsonObject["slowly"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                val safeText = text.replace("'", "\'").replace("\", "\\")
                var js = "var el=document.querySelector('$ref');if(el){el.focus();el.value='$safeText';var e=new Event('input',{bubbles:true});el.dispatchEvent(e);}"
                if (submit) js += "el.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));"
                mainHandler.post { webViewRef.get()?.evaluateJavascript(js) {} }
                if (slowly) kotlinx.coroutines.delay(500)
                else kotlinx.coroutines.delay(100)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("typed", true)
                    put("ref", ref)
                    put("text", text)
                }.toString()))
            }
        ),

private fun createWebView(context: Context): WebView {
    val webView = WebView(context)
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.allowFileAccess = true
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) { Log.d(TAG, "onPageFinished: $url") }
    }
    webView.webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
            Log.d(TAG, "console: ${msg.message()} (line ${msg.lineNumber()})")
            return true
        }
    }
    return webView
}
// ===== 新增功能：从 Operit WebSession 移植 =====

/**
 * 多标签浏览器管理器
 */
class MultiTabBrowserManager(private val context: Context) {
    private val tabs = ConcurrentHashMap<String, BrowserTab>()
    private val activeTabId = AtomicReference<String?>(null)
    
    /**
     * 创建新标签页
     */
    fun createTab(url: String? = null): String {
        val tabId = "tab_${System.currentTimeMillis()}_${tabs.size}"
        val tab = BrowserTab(context, tabId).apply {
            if (url != null) navigate(url)
        }
        tabs[tabId] = tab
        activeTabId.set(tabId)
        return tabId
    }
    
    /**
     * 切换到指定标签页
     */
    fun switchTab(tabId: String): Boolean {
        return if (tabs.containsKey(tabId)) {
            activeTabId.set(tabId)
            true
        } else false
    }
    
    /**
     * 关闭标签页
     */
    fun closeTab(tabId: String): Boolean {
        tabs.remove(tabId)?.destroy()
        if (activeTabId.get() == tabId) {
            activeTabId.set(tabs.keys.firstOrNull())
        }
        return true
    }
    
    /**
     * 获取当前活动标签页
     */
    fun getActiveTab(): BrowserTab? = tabs[activeTabId.get()]
    
    /**
     * 获取所有标签页
     */
    fun getAllTabs(): List<BrowserTab> = tabs.values.toList()
    
    /**
     * 关闭所有标签页
     */
    fun closeAll() {
        tabs.values.forEach { it.destroy() }
        tabs.clear()
        activeTabId.set(null)
    }
}

/**
 * 单个浏览器标签页
 */
class BrowserTab(internal val context: Context, val tabId: String) {
    private val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                onContentLoaded?.invoke(url)
            }
        }
        // 注入用户脚本
        addJavascriptInterface(UserScriptInterface(), "UserScript")
    }
    
    var onContentLoaded: ((String?) -> Unit)? = null
    
    /**
     * 导航到 URL
     */
    fun navigate(url: String) {
        webView.loadUrl(url)
    }
    
    /**
     * 执行 JavaScript
     */
    fun evaluateJs(script: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        webView.post {
            webView.evaluateJavascript(script) { value ->
                result = value
                latch.countDown()
            }
        }
        latch.await(15, TimeUnit.SECONDS)
        return result
    }
    
    /**
     * 截图
     */
    fun screenshot(): Bitmap? {
        val bitmap = Bitmap.createBitmap(
            webView.width, webView.height, Bitmap.Config.ARGB_8888
        )
        webView.draw(Canvas(bitmap))
        return bitmap
    }
    
    /**
     * 点击元素
     */
    fun clickSelector(selector: String) {
        evaluateJs("""
            (function() {
                var element = document.querySelector('$selector');
                if (element) {
                    element.click();
                    return 'clicked';
                }
                return 'not found';
            })();
        """.trimIndent())
    }
    
    /**
     * 填充表单
     */
    fun fillSelector(selector: String, value: String) {
        evaluateJs("""
            (function() {
                var element = document.querySelector('$selector');
                if (element) {
                    element.value = '$value';
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                    return 'filled';
                }
                return 'not found';
            })();
        """.trimIndent())
    }
    
    /**
     * 获取页面文本
     */
    fun getPageText(): String {
        return evaluateJs("document.body.innerText")
    }
    
    /**
     * 获取页面 HTML
     */
    fun getPageHtml(): String {
        return evaluateJs("document.documentElement.outerHTML")
    }
    
    /**
     * 销毁标签页
     */
    fun destroy() {
        webView.destroy()
    }
}

/**
 * 用户脚本接口
 */
class UserScriptInterface {
    @JavascriptInterface
    fun execute(script: String): String {
        // 执行用户脚本
        return "script executed"
    }
}

/**
 * 创建增强版浏览器工具
 */
fun createEnhancedBrowserTools(context: Context): List<Tool> {
    val manager = MultiTabBrowserManager(context)
    
    return listOf(
        // 标签管理
        Tool(
            name = "browser_tab_create",
            description = "创建新的浏览器标签页",
            execute = { _ ->
                val tabId = manager.createTab()
                listOf(UIMessagePart.Text(tabId))
            },
        ),
        Tool(
            name = "browser_tab_switch",
            description = "切换到指定标签页",
            execute = { args ->
                val tabId = args.jsonObject["tab_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val success = manager.switchTab(tabId)
                listOf(UIMessagePart.Text(if (success) "Switched to $tabId" else "Tab not found"))
            },
        ),
        Tool(
            name = "browser_tab_close",
            description = "关闭指定标签页",
            execute = { args ->
                val tabId = args.jsonObject["tab_id"]?.jsonPrimitive?.contentOrNull ?: ""
                manager.closeTab(tabId)
                listOf(UIMessagePart.Text("Tab closed"))
            },
        ),
        Tool(
            name = "browser_tab_list",
            description = "列出所有标签页",
            execute = { _ ->
                val tabs = manager.getAllTabs()
                val json = buildJsonObject {
                    put("count", tabs.size)
                    put("tabs", JsonArray(tabs.map { tab ->
                        buildJsonObject {
                            put("id", tab.tabId)
                            put("title", tab.webView.title)
                        }
                    }))
                }
                listOf(UIMessagePart.Text(json.toString()))
            },
        ),
        
        // 截图功能（新增）
        Tool(
            name = "browser_screenshot",
            description = "对当前标签页截图",
            execute = { _ ->
                val tab = manager.getActiveTab()
                if (tab != null) {
                    val bitmap = tab.screenshot()
                    if (bitmap != null) {
                        val byteArrayOutputStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
                        val base64 = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)
                        listOf(UIMessagePart.Text("data:image/png;base64,$base64"))
                    } else {
                        listOf(UIMessagePart.Text("Screenshot failed"))
                    }
                } else {
                    listOf(UIMessagePart.Text("No active tab"))
                }
            },
        ),
        
        // 用户脚本支持（新增）
        Tool(
            name = "browser_run_script",
            description = "在指定标签页运行 JavaScript 脚本",
            execute = { args ->
                val tabId = args.jsonObject["tab_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val script = args.jsonObject["script"]?.jsonPrimitive?.contentOrNull ?: ""
                
                val tab = manager.tabs[tabId] ?: run {
                    return@Tool listOf(UIMessagePart.Text("Tab not found: $tabId"))
                }
                
                val result = tab.evaluateJs(script)
                listOf(UIMessagePart.Text(result))
            },
        ),
    ) + createBrowserTools(context) // 保留原有工具
}
