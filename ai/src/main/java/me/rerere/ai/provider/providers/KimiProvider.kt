package me.rerere.ai.provider.providers

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.provider.*
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage

class KimiProvider : Provider<ProviderSetting.OpenAI> {
    companion object {
        private const val TAG = "KimiProvider"
        private const val DEFAULT_API_URL = "https://api.moonshot.cn/v1"
    }

    override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> {
        return listOf(
            Model(modelId = "kimi-latest", displayName = "Kimi Latest"),
            Model(modelId = "kimi-2024-06", displayName = "Kimi 2024-06")
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
