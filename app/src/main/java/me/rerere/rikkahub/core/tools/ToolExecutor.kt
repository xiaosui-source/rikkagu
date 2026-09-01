/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 参考 Operit ToolExecutor
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.core.tools

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.core.Tool

/**
 * 工具执行器抽象接口。
 * 所有工具实现此接口以支持生命周期钩子和流式执行。
 */
interface ToolExecutor {
    /** 工具名称 */
    val name: String
    
    /** 工具描述 */
    val description: String
    
    /**
     * 执行工具调用。
     * @param invocation 工具调用信息
     * @return 执行结果
     */
    suspend fun execute(invocation: ToolInvocation): ToolResult
    
    /**
     * 流式执行工具调用（可选实现）。
     * 默认实现为非流式版本。
     */
    fun executeAndStream(invocation: ToolInvocation): Flow<ToolResult> = flow {
        emit(execute(invocation))
    }
    
    /**
     * 验证工具参数。
     * @return 验证结果，null 表示无需验证或验证通过
     */
    fun validateParameters(invocation: ToolInvocation): ToolValidationResult? = null
}

/**
 * 工具参数验证结果。
 */
data class ToolValidationResult(
    val valid: Boolean,
    val errorMessage: String = "",
)

/**
 * 简单工具执行器适配器。
 * 将 [me.rerere.ai.core.Tool] 适配为 [ToolExecutor]。
 */
class SimpleToolExecutor(private val tool: me.rerere.ai.core.Tool) : ToolExecutor {
    override val name: String get() = tool.name
    override val description: String get() = tool.description
    
    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        return try {
            val args = kotlinx.serialization.json.Json.decodeFromString<
                kotlinx.serialization.json.JsonElement>(invocation.rawText)
            val parts = tool.execute(args)
            val result = parts.joinToString("\n") { part ->
                when (part) {
                    is me.rerere.ai.ui.UIMessagePart.Text -> part.toString()
                    else -> part.toString()
                }
            }
            ToolResult(
                toolName = tool.name,
                success = true,
                result = result,
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = e.message ?: "Unknown error",
            )
        }
    }
}
