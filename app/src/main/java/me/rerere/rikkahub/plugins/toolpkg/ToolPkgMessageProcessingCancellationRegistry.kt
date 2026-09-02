package me.rerere.rikkahub.plugins.toolpkg

me.rerere.rikkahub.chat.plugins.MessageProcessingController
android.util.Log
import java.util.concurrent.ConcurrentHashMap

object ToolPkgMessageProcessingCancellationRegistry {
    private const val TAG = "ToolPkgMsgCancelRegistry"

    private val controllers = ConcurrentHashMap<String, MessageProcessingController>()
    private val pendingCancels = ConcurrentHashMap.newKeySet<String>()

    @JvmStatic
    fun register(executionId: String, controller: MessageProcessingController): Boolean {
        val key = executionId.trim()
        if (key.isBlank()) {
            return false
        }

        controllers[key] = controller
        if (pendingCancels.remove(key)) {
            controllers.remove(key, controller)
            runCatching {
                controller.cancel()
            }.onFailure { error ->
                Log.e(TAG, "Failed to cancel pending execution on register: $key", error)
            }
            return false
        }
        return true
    }

    @JvmStatic
    fun unregister(executionId: String): Boolean {
        val key = executionId.trim()
        if (key.isBlank()) {
            return false
        }
        val removedController = controllers.remove(key)
        val removedPending = pendingCancels.remove(key)
        return removedController != null || removedPending
    }

    @JvmStatic
    fun cancel(executionId: String): Boolean {
        val key = executionId.trim()
        if (key.isBlank()) {
            return false
        }

        val controller = controllers.remove(key)
        if (controller == null) {
            pendingCancels.add(key)
            return false
        }

        runCatching {
            controller.cancel()
        }.onFailure { error ->
            Log.e(TAG, "Failed to cancel registered execution: $key", error)
        }
        return true
    }
}
