package me.rerere.rikkahub.ui.pages.token.webview

android.util.Log
import android.webkit.JavascriptInterface

/** JavaScript接口，用于从WebView中接收回调 */
class DeepseekJsInterface(
        private val onKeysReceived: (String) -> Unit,
        private val onKeyCreated: (String) -> Unit,
        private val onKeyDeleted: (Boolean) -> Unit,
        private val onError: (String) -> Unit
) {
    private val TAG = "DeepseekJsInterface"

    @JavascriptInterface
    fun onKeysReceived(json: String) {
        try {
            Log.d(TAG, "Received keys JSON from JavaScript: ${json.take(100)}...")
            onKeysReceived.invoke(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing keys in JS interface: ${e.message}", e)
            onError.invoke("Error processing keys: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onKeyCreated(key: String) {
        try {
            Log.d(TAG, "Key created: $key")
            onKeyCreated.invoke(key)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing created key: ${e.message}", e)
            onError.invoke("Error processing created key: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onKeyDeleted(success: Boolean) {
        try {
            Log.d(TAG, "Key deleted: $success")
            onKeyDeleted.invoke(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing key deletion: ${e.message}", e)
            onError.invoke("Error processing key deletion: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onError(error: String) {
        try {
            Log.e(TAG, "JS Error: $error")
            onError.invoke(error)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing JS error: ${e.message}", e)
        }
    }
}
