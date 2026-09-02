/*
 * MNN Provider - 适配RikkaHub
 * 基于Operit AI的MNNProvider重写，保持相同功能接口
 */
package me.rerere.ai.provider.providers

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.provider.*
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.AppLogger

class MNNProvider : Provider<ProviderSetting.OpenAI> {
    companion object {
        private const val TAG = "MNNProvider"
    }

    override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> {
        return emptyList()
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk {
        AppLogger.d(TAG, "generateText called")
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
            model = params.model.modelId,
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
