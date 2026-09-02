package me.rerere.rikkahub.workflow

import android.content.Context
android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
me.rerere.rikkahub.data.repository.WorkflowRepository

/**
 * WorkManager Worker for executing workflows in the background
 * 
 * This worker is scheduled by WorkflowScheduler to execute workflows
 * at specified times or intervals.
 */
class WorkflowWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "WorkflowWorker"
        const val KEY_WORKFLOW_ID = "workflow_id"
        const val KEY_TRIGGER_NODE_ID = "trigger_node_id"
    }

    override suspend fun doWork(): Result {
        val workflowId = inputData.getString(KEY_WORKFLOW_ID)
        val triggerNodeId = inputData.getString(KEY_TRIGGER_NODE_ID)
        
        if (workflowId.isNullOrBlank()) {
            Log.e(TAG, "Workflow ID is missing from input data")
            return Result.failure()
        }

        Log.d(TAG, "Executing scheduled workflow: $workflowId, trigger: $triggerNodeId")

        return try {
            val repository = WorkflowRepository(applicationContext)
            val result = repository.triggerWorkflow(workflowId, triggerNodeId)
            
            if (result.isSuccess) {
                Log.d(TAG, "Workflow execution succeeded: ${result.getOrNull()}")
                Result.success()
            } else if (result.exceptionOrNull() is WorkflowExecutionRetryableException) {
                Log.w(TAG, "Workflow runtime is not ready; WorkManager will retry: ${result.exceptionOrNull()?.message}")
                Result.retry()
            } else {
                Log.e(TAG, "Workflow execution failed: ${result.exceptionOrNull()?.message}")
                Result.failure()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error executing workflow", e)
            Result.failure()
        }
    }
}

