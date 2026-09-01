/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 参考 Operit AIToolHook
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.core.tools.hook

import me.rerere.ai.core.Tool
import me.rerere.rikkahub.core.tools.ToolResult

/**
 * 工具调用生命周期钩子决策。
 */
sealed class AIToolHookDecision {
    /** 允许执行 */
    object Allow : AIToolHookDecision()
    
    /** 拦截并阻止执行 */
    data class Block(
        val reason: String,
        val customResult: ToolResult? = null,
    ) : AIToolHookDecision()
}

/**
 * 工具生命周期钩子接口。
 * 允许在工具调用各阶段插入自定义逻辑。
 */
interface AIToolHook {
    /**
     * 工具调用请求时触发（在执行前）。
     * @return 是否允许继续执行
     */
    suspend fun onToolCallRequested(tool: Tool): AIToolHookDecision = AIToolHookDecision.Allow
    
    /**
     * 工具执行开始前触发。
     */
    suspend fun onToolExecutionStarted(tool: Tool) {}
    
    /**
     * 工具执行结果返回时触发。
     */
    suspend fun onToolExecutionResult(tool: Tool, result: ToolResult) {}
    
    /**
     * 工具执行错误时触发。
     */
    suspend fun onToolExecutionError(tool: Tool, error: Throwable) {}
    
    /**
     * 工具执行完成时触发（无论成功失败）。
     */
    suspend fun onToolExecutionFinished(tool: Tool) {}
}

/**
 * 工具权限检查决策。
 */
sealed class ToolPermissionDecision {
    /** 已授权 */
    object Granted : ToolPermissionDecision()
    
    /** 已拒绝 */
    data class Denied(
        val reason: String,
    ) : ToolPermissionDecision()
}
