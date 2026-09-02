/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 参考 Operit DeepseekProvider
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.ai.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage

/**
 * Deepseek Provider
 * 
 * 完全对齐 Operit AI DeepseekProvider
 */
class DeepseekProvider : Provider<ProviderSetting.OpenAI> {
    
    companion object {
        private const val BASE_URL = "https://api.deepseek.com"
        private const val TAG = "DeepseekProvider"
    }
    
    override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<me.rerere.ai.provider.Model> {
        // TODO: 实现模型列表获取
        return emptyList()
    }
    
    override suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk {
        // TODO: 实现文本生成
        return MessageChunk(
            id = java.util.UUID.randomUUID().toString(),
            model = params.model.id.toString(),
            choices = emptyList()
        )
    }
    
    override suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> {
        return flow {
            // TODO: 实现流式文本生成
            emit(MessageChunk(
                id = java.util.UUID.randomUUID().toString(),
                model = params.model.id.toString(),
                choices = emptyList()
            ))
        }
    }
}
