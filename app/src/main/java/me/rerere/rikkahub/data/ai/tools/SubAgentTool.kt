/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.util.Log
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant

private const val TAG = "SubAgentTool"

/**
 * 创建一个 `sub_agent` 工具，让主 agent 可以把可拆分的长任务/独立子任务委派给
 * 一个隔离的子 agent 协程去深入完成，再把结果带回来给主 agent 汇总。
 *
 * 核心价值（#2 子 Agent 并行委派）：
 * - **隔离上下文**：子 agent 使用独立的 prompt 和消息，不污染主对话上下文，避免长任务把主上下文撑爆。
 * - **聚焦深入**：子 agent 专注一个子任务，输出结构化结果；主 agent 负责规划/协调/汇总。
 * - **分层 agent**：主 agent（Planner/Coordinator）→ 子 agent（Worker），是长程自主的纵向扩展。
 *
 * 实现：通过 Koin 惰性解析 ProviderManager，用非流式生成对独立子任务 prompt 做一次推理，
 * 返回最终文本给主 agent 消费。
 */
fun createSubAgentTool(
    settings: Settings,
    assistant: Assistant,
): Tool = Tool(
    name = "sub_agent",
    description = """
        Delegate a focused sub-task to a separate sub-agent that works independently and returns its final result.
        Use when the overall task has a clearly separable sub-task (research, computation, text generation, analysis)
        that would bloat the main context or is better done in isolation. Pass a CLEAR, self-contained instruction.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("task", buildJsonObject {
                    put("type", "string")
                    put("description", "A self-contained instruction for the sub-agent, including all context/facts it needs. Be specific about the expected output format.")
                })
                put("max_tokens", buildJsonObject {
                    put("type", "integer")
                    put("description", "Optional. Max output tokens for the sub-agent (default 1024).")
                })
            },
            required = listOf("task"),
        )
    },
    needsApproval = true,
    execute = { input ->
        val obj = input.jsonObject
        val task = obj["task"]?.jsonPrimitive?.contentOrNull?.trim()
            ?: error("sub_agent: missing 'task' parameter")
        val maxTokens = obj["max_tokens"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1024

        // 子 agent 独立 system prompt：限定执行者身份，保证聚焦且不越权
        val subSystemPrompt = buildString {
            appendLine("你是一个专注于单一任务的子代理（sub-agent）。")
            appendLine("你的唯一职责是高质量完成用户委托给的这个子任务，并给出可直接使用的最终结果。")
            appendLine("不要发散、不要节外生枝、不要向用户提问；如果信息不足，基于常识给出最佳合理推断，并注明假设。")
            appendLine("输出要简洁、结构化、可直接被主代理单步消费。")
        }
        val subMessages = listOf(
            UIMessage.system(subSystemPrompt),
            UIMessage.user("子任务：$task\n\n请直接给出该子任务的最终结果。"),
        )

        Log.i(TAG, "启动子代理: task=${task.take(80)}")
        var resultText = ""

        runCatching {
            val providerManager: me.rerere.ai.provider.ProviderManager =
                org.koin.java.KoinJavaComponent.getKoin().get()

            val model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId)
                ?: settings.providers.asSequence().flatMap { it.models }.firstOrNull()
                ?: error("sub_agent: 无可用的模型")
            val provider = model.findProvider(settings.providers)
                ?: error("sub_agent: 未找到 provider: ${model.id}")
            val providerImpl = providerManager.getProviderByType(provider)

            Log.i(TAG, "子代理使用模型: ${model.displayName}")
            var messages = subMessages
            val chunk = providerImpl.generateText(
                providerSetting = provider,
                messages = messages,
                params = me.rerere.ai.provider.TextGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.fromBudgetTokens(0),
                    maxTokens = maxTokens,
                ),
            )
            // 用与主对话一致的方式折叠 chunk → 提取最终文本
            messages = messages.handleMessageChunk(chunk = chunk, model = model)
            resultText = messages.lastOrNull()?.toText()?.trim() ?: ""
            Log.i(TAG, "子代理完成: 结果${resultText.length}字符")
        }.onFailure { e ->
            Log.e(TAG, "子代理执行失败: ${e.message}", e)
            resultText = "子代理执行失败：${e.message}"
        }

        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("success", resultText.isNotBlank())
                    put(
                        "result",
                        if (resultText.isBlank()) "(子代理未产生文本输出)" else resultText,
                    )
                }.toString()
            )
        )
    },
)