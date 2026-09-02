package me.rerere.rikkahub.plugins.workflow

me.rerere.rikkahub.data.repository.WorkflowRepository
me.rerere.OperitPlugin
me.rerere.lifecycle.AppLifecycleEvent
me.rerere.lifecycle.AppLifecycleHookParams
me.rerere.lifecycle.AppLifecycleHookPlugin
me.rerere.lifecycle.AppLifecycleHookPluginRegistry
android.util.Log

private object WorkflowAppLifecycleHookPlugin : AppLifecycleHookPlugin {
    private const val TAG = "WorkflowLifecyclePlugin"
    @Volatile
    private var firstActivityStartHandled = false

    override val id: String = "builtin.workflow.app-lifecycle"

    override suspend fun onEvent(
        event: AppLifecycleEvent,
        params: AppLifecycleHookParams
    ) {
        try {
            when (event) {
                AppLifecycleEvent.APPLICATION_CREATE -> {
                    firstActivityStartHandled = false
                }

                AppLifecycleEvent.ACTIVITY_START -> {
                    if (firstActivityStartHandled) {
                        return
                    }
                    firstActivityStartHandled = true
                    WorkflowRepository(params.context.applicationContext)
                        .triggerWorkflowsByColdStartAppOpen(
                            extras =
                                params.extras.mapValues { (_, value) ->
                                    value?.toString().orEmpty()
                                }
                        )
                }

                else -> Unit
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process workflow app-open trigger: ${event.wireName}", e)
        }
    }
}

object WorkflowLifecyclePlugin : OperitPlugin {
    override val id: String = "builtin.workflow.lifecycle"

    override fun register() {
        AppLifecycleHookPluginRegistry.register(WorkflowAppLifecycleHookPlugin)
    }
}
