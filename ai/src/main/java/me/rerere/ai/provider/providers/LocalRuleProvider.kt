package me.rerere.ai.provider.providers

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageChunk
import me.rerere.ai.core.Model
import me.rerere.ai.core.UIMessage
import me.rerere.ai.core.UIMessageChoice
import me.rerere.ai.core.UIMessagePart
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.models.ModelInfo
import me.rerere.ai.ui.MessageRole
import me.rerere.ai.ui.ToolApprovalState

import kotlin.uuid.Uuid

/**
 * 本地规则模型 Provider（离线小助手）
 * 完全本地运行，不依赖网络或模型文件。
 * 通过简单关键字匹配调用已有工具，实现 "普通对话 + 本地工具" 功能。
 */
class LocalRuleProvider : Provider<ProviderSetting.LocalRule> {
    override suspend fun listModels(providerSetting: ProviderSetting.LocalRule): List<Model> {
        // 返回最小化的 Model 对象，仅包含 ID 与名称
        return listOf(
            Model(
                modelId = "local-rule",
                displayName = "离线小助手"
            )
        )
    }

    // 文本流式生成 – 只返回一条消息（文字或工具调用）
    override suspend fun streamText(
        providerSetting: ProviderSetting.LocalRule,
        messages: List<UIMessage>,
        params: me.rerere.ai.core.TextGenerationParams,
    ): Flow<MessageChunk> = flow {
        // 取用户最新的文字内容（忽略 system/assistant）
        val userMsg = messages.lastOrNull { it.role == MessageRole.USER }?.parts?.firstOrNull { it is UIMessagePart.Text } as? UIMessagePart.Text
        val content = userMsg?.text?.trim() ?: ""
        // 这里直接返回普通文字回复（不调用任何工具）
                val responseText = "好的，我已收到：$content"
                val chunk = MessageChunk(
                    id = Uuid.random().toString(),
                    model = "local-rule",
                    choices = listOf(
                        UIMessageChoice(
                            index = 0,
                            delta = null,
                            message = UIMessage(
                                role = MessageRole.ASSISTANT,
                                parts = listOf(UIMessagePart.Text(responseText))
                            ),
                            finishReason = "stop"
                        )
                    )
                )
        emit(chunk)
    }

    // 文本（非流式）直接调用流式实现
    override suspend fun generateText(
        providerSetting: ProviderSetting.LocalRule,
        messages: List<UIMessage>,
        params: me.rerere.ai.core.TextGenerationParams,
    ): MessageChunk = streamText(providerSetting, messages, params).single()

    // 其它接口保持不实现（不需要）
    override suspend fun generateEmbedding(
        providerSetting: ProviderSetting.LocalRule,
        params: me.rerere.ai.core.EmbeddingGenerationParams,
    ) = throw UnsupportedOperationException("Embedding not supported for LocalRuleProvider")

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: me.rerere.ai.core.ImageGenerationParams,
    ) = throw UnsupportedOperationException("Image generation not supported for LocalRuleProvider")

    override suspend fun editImage(
        providerSetting: ProviderSetting,
        params: me.rerere.ai.core.ImageEditParams,
    ) = throw UnsupportedOperationException("Image edit not supported for LocalRuleProvider")
}
