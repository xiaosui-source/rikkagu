/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.ai.provider.providers.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage

/**
 * 本地模型 Provider
 * 
 * TODO: 需要集成原生库后才能实现完整功能
 */
class LocalLLMProvider(private val context: android.content.Context) : Provider<ProviderSetting.LocalLLM> {
    
    override suspend fun listModels(providerSetting: ProviderSetting.LocalLLM): List<Model> {
        return providerSetting.models
    }
    
    override suspend fun generateText(
        providerSetting: ProviderSetting.LocalLLM,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk {
        return MessageChunk(
            id = java.util.UUID.randomUUID().toString(),
            model = params.model.id.toString(),
            choices = listOf(
                me.rerere.ai.ui.UIMessageChoice(
                    index = 0,
                    delta = null,
                    message = UIMessage(
                        role = me.rerere.ai.core.MessageRole.ASSISTANT,
                        parts = listOf(
                            me.rerere.ai.ui.UIMessagePart.Text(
                                "本地模型推理功能待实现"
                            )
                        )
                    ),
                    finishReason = null
                )
            ),
        )
    }
    
    override suspend fun streamText(
        providerSetting: ProviderSetting.LocalLLM,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> {
        return flow {
            emit(MessageChunk(
                id = java.util.UUID.randomUUID().toString(),
                model = params.model.id.toString(),
                choices = listOf(
                    me.rerere.ai.ui.UIMessageChoice(
                        index = 0,
                        delta = null,
                        message = UIMessage(
                            role = me.rerere.ai.core.MessageRole.ASSISTANT,
                            parts = listOf(
                                me.rerere.ai.ui.UIMessagePart.Text(
                                    "本地模型推理功能待实现"
                                )
                            )
                        ),
                        finishReason = null
                    )
                ),
            ))
        }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    }
    
    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: me.rerere.ai.provider.ImageGenerationParams,
    ): ImageGenerationResult {
        return ImageGenerationResult(emptyList())
    }
    
    override suspend fun generateEmbedding(
        providerSetting: ProviderSetting.LocalLLM,
        params: me.rerere.ai.provider.EmbeddingGenerationParams,
    ): me.rerere.ai.provider.EmbeddingGenerationResult {
        return me.rerere.ai.provider.EmbeddingGenerationResult(
            model = params.model.id.toString(),
            embeddings = emptyList()
        )
    }
    
    override suspend fun editImage(
        providerSetting: ProviderSetting,
        params: me.rerere.ai.provider.ImageEditParams,
    ): ImageGenerationResult {
        return ImageGenerationResult(emptyList())
    }
}

    companion object {
        val DEFAULT_MODELS = listOf(
            Model(
                id = kotlin.uuid.Uuid.random(),
                modelId = "llama-3.2-1b",
                displayName = "Llama 3.2 1B (GGUF)",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL),
            ),
        )
    }
