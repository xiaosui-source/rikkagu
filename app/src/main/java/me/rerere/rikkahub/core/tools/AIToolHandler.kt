/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 参考 Operit AIToolHandler
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.core.tools

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.core.tools.hook.AIToolHook
import me.rerere.rikkahub.core.tools.hook.AIToolHookDecision
import me.rerere.rikkahub.core.tools.hook.ToolPermissionDecision
import me.rerere.rikkahub.core.tools.permission.ToolPermissionSystem
import java.util.concurrent.ConcurrentHashMap

/**
 * AI 工具处理器。
 */
class AIToolHandler private constructor(private val context: Context) {
    companion object {
        private const val TAG = "AIToolHandler"
        
        @Volatile
        private var INSTANCE: AIToolHandler? = null
        
        fun getInstance(context: Context): AIToolHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AIToolHandler(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val availableTools = ConcurrentHashMap<String, Tool>()
    private val toolExecutors = ConcurrentHashMap<String, ToolExecutor>()
    private val toolHooks = mutableListOf<AIToolHook>()
    private val toolPermissionSystem = ToolPermissionSystem.getInstance(context)
    
    fun registerTool(tool: Tool, executor: ToolExecutor? = null) {
        availableTools[tool.name] = tool
        executor?.let { toolExecutors[tool.name] = it }
        ToolExecutionManager.registerTool(tool, executor ?: SimpleToolExecutor(tool))
    }
    
    fun registerTools(tools: List<Tool>, executorProvider: ((Tool) -> ToolExecutor)? = null) {
        tools.forEach { tool ->
            registerTool(tool, executorProvider?.invoke(tool))
        }
    }
    
    fun unregisterTool(toolName: String) {
        availableTools.remove(toolName)
        toolExecutors.remove(toolName)
    }
    
    fun addToolHook(hook: AIToolHook) {
        if (!toolHooks.contains(hook)) {
            toolHooks.add(hook)
            ToolExecutionManager.addHook(hook)
        }
    }
    
    fun removeToolHook(hook: AIToolHook) {
        toolHooks.remove(hook)
        ToolExecutionManager.removeHook(hook)
    }
    
    fun clearToolHooks() {
        toolHooks.clear()
        ToolExecutionManager.reset()
    }
    
    fun getToolPermissionSystem(): ToolPermissionSystem = toolPermissionSystem
    
    fun hasTool(toolName: String): Boolean = availableTools.containsKey(toolName)
    
    fun getRegisteredTools(): List<Tool> = availableTools.values.toList()
    
    /**
     * 执行工具调用。
     */
    suspend fun executeTool(invocation: ToolInvocation): ToolResult {
        val tool = invocation.tool
        
        // 检查权限
        val permission = toolPermissionSystem.checkToolPermission(tool)
        if (permission is ToolPermissionDecision.Denied) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Tool permission denied: ${permission.reason}",
            )
        }
        
        // 检查拦截
        val interception = checkToolInterception(tool)
        if (interception is AIToolHookDecision.Block) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = interception.reason,
            )
        }
        
        // 执行工具
        return try {
            val result = ToolExecutionManager.executeInvocation(invocation)
            result
        } catch (e: Exception) {
            throw e
        }
    }
    
    /**
     * 流式执行工具。
     */
    fun executeToolAndStream(invocation: ToolInvocation): Flow<ToolResult> {
        return ToolExecutionManager.executeAndStream(invocation)
    }
    
    /**
     * 检查工具拦截。
     */
    private suspend fun checkToolInterception(tool: Tool): AIToolHookDecision {
        return toolHooks.firstNotNullOfOrNull { hook ->
            hook.onToolCallRequested(tool)
        } ?: AIToolHookDecision.Allow
    }
}
