/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.agents

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings

/**
 * 为每个启用的智能体生成一个工具（agent_call_<id>）。
 * 主助手模型可以通过原生 tool_calls / 纯文本 XML 调用它，把子任务转交出去。
 */
fun buildAgentTools(
    settings: Settings,
    agentStore: AgentStore,
    agentRunner: AgentRunner,
): List<Tool> = agentStore.agents.value
    .filter { it.enabled }
    .map { agent -> buildAgentTool(settings, agent, agentRunner) }

fun buildAgentTool(
    settings: Settings,
    agent: AgentProfile,
    agentRunner: AgentRunner,
): Tool {
    val assistantName = settings.assistants
        .firstOrNull { it.id == agent.assistantId }?.name ?: "未绑定助手"
    val description = buildString {
        append("把子任务转交给「${agent.name}」智能体（${assistantName}）处理")
        if (agent.description.isNotBlank()) {
            append("。它擅长：").append(agent.description)
        }
        append("。当用户的需求属于它的专长、或你处理不了时，把任务交给它，然后等它返回结果后继续。")
    }

    return Tool(
        name = "agent_call_${agent.id}",
        description = description,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("task", buildJsonObject {
                        put("type", "string")
                        put("description", "要交给「${agent.name}」的具体任务/指令，写清楚要求")
                    })
                    put("context", buildJsonObject {
                        put("type", "string")
                        put("description", "可选：任务相关的情境信息，帮助该智能体理解背景")
                    })
                },
                required = listOf("task")
            )
        },
        // 系统提示注入：告知主模型何时该转交
        systemPrompt = { _, _ ->
            "如果用户任务属于「${agent.name}」的专长领域（${agent.description.ifBlank { "见其工具描述" }}），" +
                "优先调用 agent_call_${agent.id} 工具把子任务转交给它，不要自己硬做。"
        },
        needsApproval = false,
        execute = { args ->
            val obj = (args as? JsonObject) ?: JsonObject(emptyMap())
            val task = obj["task"]?.jsonPrimitive?.contentOrNull
                ?: "请处理我交给你的任务"
            val context = obj["context"]?.jsonPrimitive?.contentOrNull
            val output = agentRunner.run(settings, agent, task, context)
            listOf(UIMessagePart.Text(output))
        }
    )
}
