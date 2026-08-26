/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.ai.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.EmbeddingGenerationResult
import me.rerere.ai.provider.ImageEditParams
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageChoice
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

/**
 * 本地规则模型 Provider（离线小助手）
 *
 * 完全本地运行，不依赖网络、API Key 或任何模型文件。
 * 直接在代码里用规则生成回复，适合无网络场景下的简单对话。
 */
class LocalRuleProvider : Provider<ProviderSetting.LocalRule> {

    override suspend fun listModels(providerSetting: ProviderSetting.LocalRule): List<Model> {
        return listOf(
            Model(
                modelId = "local-rule",
                displayName = "离线小助手",
            )
        )
    }

    /** 根据用户输入生成回复文本（纯本地规则） */
    private fun buildReply(messages: List<UIMessage>): String {
        val userText = messages.lastOrNull { it.role == MessageRole.USER }
            ?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("") { it.text }
            ?.trim()
            .orEmpty()

        if (userText.isBlank()) {
            return "你好，我是离线小助手，完全在本地运行（无需网络）。有什么可以帮你的？"
        }

        val lower = userText.lowercase()
        return when {
            listOf("你好", "hi", "hello", "在吗", "您好").any { lower.contains(it) } ->
                "你好！我是离线小助手，随时为你服务。"

            listOf("时间", "几点", "日期", "今天").any { userText.contains(it) } ->
                "抱歉，作为纯本地助手我无法直接读取系统时间，你可以查看手机状态栏的时间。"

            listOf("谢谢", "感谢", "thanks", "thank you").any { lower.contains(it) } ->
                "不客气，很高兴能帮到你！"

            listOf("再见", "拜拜", "bye").any { lower.contains(it) } ->
                "再见，祝你一切顺利！"

            userText.endsWith("？") || userText.endsWith("?") ->
                "这是个好问题。作为离线小助手，我只能基于本地规则回答，暂时无法联网查询。你可以换个方式描述，或切换到联网模型获取更准确的答案。"

            else ->
                "我已收到你的消息：「$userText」。我是完全离线运行的小助手，可以进行简单对话；如需更强的能力，请在设置里切换到联网大模型。"
        }
    }

    private fun buildChunk(text: String): MessageChunk {
        return MessageChunk(
            id = Uuid.random().toString(),
            model = "local-rule",
            choices = listOf(
                UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text(text))
                    ),
                    finishReason = "stop"
                )
            )
        )
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.LocalRule,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = buildChunk(buildReply(messages))

    override suspend fun streamText(
        providerSetting: ProviderSetting.LocalRule,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = flow {
        emit(buildChunk(buildReply(messages)))
    }

    override suspend fun generateEmbedding(
        providerSetting: ProviderSetting.LocalRule,
        params: EmbeddingGenerationParams,
    ): EmbeddingGenerationResult = error("离线小助手不支持向量嵌入")

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): ImageGenerationResult = error("离线小助手不支持图像生成")

    override suspend fun editImage(
        providerSetting: ProviderSetting,
        params: ImageEditParams,
    ): ImageGenerationResult = error("离线小助手不支持图像编辑")
}
