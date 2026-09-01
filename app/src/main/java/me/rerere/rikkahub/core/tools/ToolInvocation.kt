/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 参考 Operit ToolInvocation
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.core.tools

import kotlinx.serialization.Serializable
import me.rerere.ai.core.Tool

/**
 * 工具调用表示。
 * 包装一次工具调用的完整信息。
 */
@Serializable
data class ToolInvocation(
    val tool: Tool,
    val rawText: String,
    val responseLocationStart: Int = 0,
    val responseLocationEnd: Int = 0,
) {
    val toolName get() = tool.name
}

/**
 * 工具执行结果。
 */
@Serializable
data class ToolResult(
    val toolName: String,
    val success: Boolean,
    val result: String,
    val error: String? = null,
)
