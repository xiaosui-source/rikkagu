/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.agents

import android.util.Log
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider

/**
 * 智能体执行器：为被调起的智能体（B）建立独立子会话并调用其绑定模型，
 * 结果作为工具输出返回给主助手（A）继续处理。
 *
 * 子会话不写回主会话数据库、不注入任何工具（禁止嵌套转交，防止死循环）。
 */
class AgentRunner(
    private val providerManager: ProviderManager,
) {
    private val tag = "AgentRunner"

    /**
     * @param agent   被调起的智能体配置
     * @param task    主助手交给它的任务
     * @param context 可选的情境信息（当前对话摘要等）
     * @return 智能体 B 的完整回复文本（失败时返回带「转交失败」的错误说明，供 A 模型感知）
     */
    suspend fun run(
        settings: Settings,
        agent: AgentProfile,
        task: String,
        context: String?,
    ): String {
        val assistant = settings.assistants.firstOrNull { it.id == agent.assistantId }
            ?: return "【转交失败】智能体「${agent.name}」未绑定任何助手配置，请在设置中重新绑定。"

        val modelId = assistant.chatModelId ?: settings.chatModelId
        val model = settings.providers
            .flatMap { it.models }
            .firstOrNull { it.id == modelId }
            ?: return "【转交失败】助手「${assistant.name}」未绑定有效模型，请先在模型设置中配置。"

        val provider = model.findProvider(settings.providers)
            ?: return "【转交失败】找不到模型对应的服务商配置。"
        val providerImpl = providerManager.getProviderByType(provider)

        val systemPrompt = buildString {
            append(
                agent.systemPrompt.ifBlank { assistant.systemPrompt }.ifBlank {
                    "你是一位专业的智能体助手，请认真完成用户交给你的任务。"
                }
            )
            if (!context.isNullOrBlank()) {
                append("\n\n以下是调用方提供的情境信息（供你理解背景）：\n").append(context)
            }
        }

        val messages = listOf(
            UIMessage.system(systemPrompt),
            UIMessage.user(task),
        )

        val params = TextGenerationParams(
            model = model,
            temperature = (agent.temperature ?: assistant.temperature),
            topP = assistant.topP,
            maxTokens = agent.maxTokens ?: assistant.maxTokens,
            tools = emptyList(), // 子智能体内不允许再调工具/转交，防止嵌套死循环
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = assistant.customHeaders,
            customBody = assistant.customBodies,
        )

        return try {
            val chunk = providerImpl.generateText(
                providerSetting = provider,
                messages = messages,
                params = params,
            )
            val message = chunk.choices.firstOrNull()?.message
                ?: chunk.choices.firstOrNull()?.delta
            val text = message?.parts
                ?.filterIsInstance<UIMessagePart.Text>()
                ?.joinToString("") { it.text }
                ?.trim() ?: ""
            if (text.isEmpty()) "（${agent.name} 未返回任何内容）" else text
        } catch (e: Exception) {
            Log.e(tag, "agent call failed: ${agent.name}", e)
            "【转交失败】${e.message ?: e.toString()}"
        }
    }
}
