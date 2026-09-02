/*
 * Deepseek Provider - 适配RikkaHub
 * 基于Operit AI的DeepseekProvider重写，保持相同功能接口
 */
package me.rerere.ai.provider.providers

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.provider.*
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.estimateTokenCount
import me.rerere.ai.util.calculateMessageTokens
import me.rerere.ai.util.trimByTokenLimit

class DeepseekProvider : Provider<ProviderSetting.OpenAI> {
    companion object {
        private const val TAG = "DeepseekProvider"
        private const val DEFAULT_API_URL = "https://api.deepseek.com/v1"
    }

    private val apiKey: String? get() = null
    private val baseUrl: String get() = DEFAULT_API_URL

    override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> {
        return listOf(
            Model(id = "deepseek-chat", name = "DeepSeek Chat", isPublic = true),
            Model(id = "deepseek-coder", name = "DeepSeek Coder", isPublic = true)
        )
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk {
        Log.d(TAG, "generateText called")
        return MessageChunk(
            id = java.util.UUID.randomUUID().toString(),
            model = params.model.id.toString(),
            choices = emptyList()
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> {
        Log.d(TAG, "streamText called")
        return flow {
            emit(MessageChunk(
                id = java.util.UUID.randomUUID().toString(),
                model = params.model.id.toString(),
                choices = emptyList()
            ))
        }
    }

    override suspend fun generateEmbedding(
        providerSetting: ProviderSetting.OpenAI,
        params: EmbeddingGenerationParams
    ): EmbeddingGenerationResult {
        return EmbeddingGenerationResult(
            model = params.model.id.toString(),
            embeddings = emptyList()
        )
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): ImageGenerationResult {
        return ImageGenerationResult(emptyList())
    }

    override suspend fun editImage(
        providerSetting: ProviderSetting,
        params: ImageEditParams
    ): ImageGenerationResult {
        return ImageGenerationResult(emptyList())
    }
}
