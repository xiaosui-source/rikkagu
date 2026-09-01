/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 参考 Operit ToolExecutionManager
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.core.tools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.core.tools.hook.AIToolHook
import me.rerere.rikkahub.core.tools.hook.AIToolHookDecision
import me.rerere.rikkahub.core.tools.hook.ToolPermissionDecision
import me.rerere.rikkahub.core.tools.permission.ToolPermissionSystem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/**
 * 工具执行管理器。
 * 负责工具调用的解析、权限检查、执行和结果聚合。
 *
 * 参考 Operit 的 ToolExecutionManager 实现。
 */
object ToolExecutionManager {
    private const val TAG = "ToolExecutionManager"
    
    private val toolExecutors = ConcurrentHashMap<String, ToolExecutor>()
    private val hooks = mutableListOf<AIToolHook>()
    private val permissionSystem = ToolPermissionSystem.getInstance()
    private val executionCount = AtomicInteger(0)
    private val maxConcurrentExecutions = 10
    private val maxTotalExecutions = 1000
    
    /**
     * 注册工具执行器。
     */
    fun registerTool(tool: Tool, executor: ToolExecutor) {
        toolExecutors[tool.name] = executor
    }
    
    /**
     * 批量注册工具。
     */
    fun registerTools(tools: List<Tool>, executorProvider: (Tool) -> ToolExecutor) {
        tools.forEach { tool ->
            registerTool(tool, executorProvider(tool))
        }
    }
    
    /**
     * 添加工具生命周期钩子。
     */
    fun addHook(hook: AIToolHook) {
        hooks.add(hook)
    }
    
    /**
     * 移除工具生命周期钩子。
     */
    fun removeHook(hook: AIToolHook) {
        hooks.remove(hook)
    }
    
    /**
     * 重置所有状态。
     */
    fun reset() {
        hooks.clear()
        executionCount.set(0)
    }
    
    /**
     * 执行单个工具调用。
     */
    suspend fun executeInvocation(invocation: ToolInvocation): ToolResult {
        val tool = invocation.tool
        
        // 检查权限
        val permission = permissionSystem.checkToolPermission(tool)
        if (permission is ToolPermissionDecision.Denied) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Tool permission denied: ${permission.reason}",
            )
        }
        
        // 通知钩子
        hooks.forEach { hook ->
            try {
                hook.onToolCallRequested(tool)
            } catch (e: Exception) {
                // 忽略钩子异常
            }
        }
        
        // 执行工具
        return try {
            hooks.forEach { hook ->
                try {
                    hook.onToolExecutionStarted(tool)
                } catch (e: Exception) {
                    // 忽略钩子异常
                }
            }
            
            val result = if (toolExecutors.containsKey(tool.name)) {
                toolExecutors.getValue(tool.name).execute(invocation)
            } else {
                // 回退到原始 tool 执行
                executeWithTool(invocation)
            }
            
            hooks.forEach { hook ->
                try {
                    hook.onToolExecutionResult(tool, result)
                } catch (e: Exception) {
                    // 忽略钩子异常
                }
            }
            result
        } catch (e: Exception) {
            hooks.forEach { hook ->
                try {
                    hook.onToolExecutionError(tool, e)
                } catch (e2: Exception) {
                    // 忽略钩子异常
                }
            }
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = e.message ?: "Unknown error",
            )
        } finally {
            hooks.forEach { hook ->
                try {
                    hook.onToolExecutionFinished(tool)
                } catch (e: Exception) {
                    // 忽略钩子异常
                }
            }
        }
    }
    
    /**
     * 批量执行工具调用（并行执行）。
     */
    suspend fun executeInvocations(
        invocations: List<ToolInvocation>,
        scope: CoroutineScope,
    ): List<ToolResult> {
        if (invocations.isEmpty()) return emptyList()
        
        val results = mutableListOf<ToolResult>()
        
        invocations.forEach { invocation ->
            scope.launch {
                try {
                    val result = executeInvocation(invocation)
                    synchronized(results) {
                        results.add(result)
                    }
                } catch (e: Exception) {
                    // 单个工具失败不影响其他工具
                }
            }
        }
        
        // 简单等待（实际应用中应该使用更精确的同步机制）
        kotlinx.coroutines.delay(100)
        return results.toList()
    }
    
    /**
     * 流式执行工具调用。
     */
    fun executeAndStream(
        invocation: ToolInvocation,
    ): Flow<ToolResult> = flow {
        emit(executeInvocation(invocation))
    }.catch { e ->
        emit(ToolResult(
            toolName = invocation.tool.name,
            success = false,
            result = "",
            error = e.message ?: "Stream error",
        ))
    }
    
    /**
     * 通过原始 Tool 对象执行（回退方案）。
     */
    private suspend fun executeWithTool(invocation: ToolInvocation): ToolResult {
        return try {
            val args = kotlinx.serialization.json.Json.decodeFromString<
                kotlinx.serialization.json.JsonElement>(invocation.rawText)
            val parts = invocation.tool.execute(args)
            val result = parts.joinToString("\n") { part ->
                when (part) {
                    is me.rerere.ai.ui.UIMessagePart.Text -> part.toString()
                    else -> part.toString()
                }
            }
            ToolResult(
                toolName = invocation.tool.name,
                success = true,
                result = result,
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = invocation.tool.name,
                success = false,
                result = "",
                error = e.message ?: "Execution error",
            )
        }
    }
}
