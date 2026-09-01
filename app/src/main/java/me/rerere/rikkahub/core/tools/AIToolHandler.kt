/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 参考 Operit AIToolHandler
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.core.tools

import android.content.Context
import kotlinx.coroutines.flow.Flow
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.core.tools.hook.AIToolHook
import me.rerere.rikkahub.core.tools.hook.AIToolHookDecision
import me.rerere.rikkahub.core.tools.permission.ToolPermissionDecision
import me.rerere.rikkahub.core.tools.permission.ToolPermissionSystem
import java.util.concurrent.ConcurrentHashMap

/**
 * AI 工具处理器。
 * 管理工具的注册、执行和生命周期钩子。
 *
 * 参考 Operit 的 AIToolHandler 实现。
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
    
    // 可用工具注册表
    private val availableTools = ConcurrentHashMap<String, Tool>()
    private val toolExecutors = ConcurrentHashMap<String, ToolExecutor>()
    private val toolHooks = mutableListOf<AIToolHook>()
    
    // 权限系统
    private val toolPermissionSystem = ToolPermissionSystem.getInstance(context)
    
    /**
     * 注册工具。
     */
    fun registerTool(tool: Tool, executor: ToolExecutor? = null) {
        availableTools[tool.name] = tool
        executor?.let { toolExecutors[tool.name] = it }
        ToolExecutionManager.registerTool(tool, executor ?: SimpleToolExecutor(tool))
    }
    
    /**
     * 批量注册工具。
     */
    fun registerTools(tools: List<Tool>, executorProvider: ((Tool) -> ToolExecutor)? = null) {
        tools.forEach { tool ->
            registerTool(tool, executorProvider?.invoke(tool))
        }
    }
    
    /**
     * 注销工具。
     */
    fun unregisterTool(toolName: String) {
        availableTools.remove(toolName)
        toolExecutors.remove(toolName)
    }
    
    /**
     * 添加工具生命周期钩子。
     */
    fun addToolHook(hook: AIToolHook) {
        if (!toolHooks.contains(hook)) {
            toolHooks.add(hook)
            ToolExecutionManager.addHook(hook)
        }
    }
    
    /**
     * 移除工具生命周期钩子。
     */
    fun removeToolHook(hook: AIToolHook) {
        toolHooks.remove(hook)
        ToolExecutionManager.removeHook(hook)
    }
    
    /**
     * 清除所有钩子。
     */
    fun clearToolHooks() {
        toolHooks.clear()
        ToolExecutionManager.reset()
    }
    
    /**
     * 获取工具权限系统。
     */
    fun getToolPermissionSystem(): ToolPermissionSystem = toolPermissionSystem
    
    /**
     * 检查工具是否存在。
     */
    fun hasTool(toolName: String): Boolean = availableTools.containsKey(toolName)
    
    /**
     * 获取所有已注册工具。
     */
    fun getRegisteredTools(): List<Tool> = availableTools.values.toList()
    
    /**
     * 执行工具调用。
     * @return 工具执行结果
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
        
        // 通知钩子
        notifyHooks("onToolCallRequested") { hook -> hook.onToolCallRequested(tool) }
        
        // 检查拦截
        val interception = checkToolInterception(tool)
        if (interception is AIToolHookDecision.Block) {
            val interceptedResult = ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = interception.reason,
            )
            notifyHooks("onToolExecutionResult") { hook -> hook.onToolExecutionResult(tool, interceptedResult) }
            notifyHooks("onToolExecutionFinished") { hook -> hook.onToolExecutionFinished(tool) }
            return interceptedResult
        }
        
        // 执行工具
        return try {
            notifyHooks("onToolExecutionStarted") { hook -> hook.onToolExecutionStarted(tool) }
            
            val result = ToolExecutionManager.executeInvocation(invocation)
            
            notifyHooks("onToolExecutionResult") { hook -> hook.onToolExecutionResult(tool, result) }
            result
        } catch (e: Exception) {
            notifyHooks("onToolExecutionError") { hook -> hook.onToolExecutionError(tool, e) }
            throw e
        } finally {
            notifyHooks("onToolExecutionFinished") { hook -> hook.onToolExecutionFinished(tool) }
        }
    }
    
    /**
     * 流式执行工具。
     */
    fun executeToolAndStream(invocation: ToolInvocation): Flow<ToolResult> {
        return ToolExecutionManager.executeAndStream(invocation)
    }
    
    /**
     * 通知所有钩子。
     */
    private fun notifyHooks(eventName: String, action: (AIToolHook) -> Unit) {
        toolHooks.forEach { hook ->
            try {
                action(hook)
            } catch (e: Exception) {
                // 忽略钩子执行异常
            }
        }
    }
    
    /**
     * 检查工具拦截。
     */
    private fun checkToolInterception(tool: Tool): AIToolHookDecision {
        return toolHooks.firstNotNullOfOrNull { hook ->
            hook.onToolCallRequested(tool)
        } ?: AIToolHookDecision.Allow
    }
}
