/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.ai.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage

// 提供商实现
// 采用无状态设计，使用时除了需要传入需要的参数外，还需要传入provider setting作为参数
interface Provider<T : ProviderSetting> {
    suspend fun listModels(providerSetting: T): List<Model>

    suspend fun getBalance(providerSetting: T): String {
        return "TODO"
    }

    suspend fun generateText(
        providerSetting: T,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk

    suspend fun streamText(
        providerSetting: T,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk>

    suspend fun generateEmbedding(
        providerSetting: T,
        params: EmbeddingGenerationParams,
    ): EmbeddingGenerationResult {
        error("Embedding generation is not supported")
    }

    suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams,
    ): ImageGenerationResult

    suspend fun editImage(
        providerSetting: ProviderSetting,
        params: ImageEditParams,
    ): ImageGenerationResult {
        error("Image edit is not supported")
    }
}

@Serializable
data class TextGenerationParams(
    val model: Model,
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
    val tools: List<Tool> = emptyList(),
    val reasoningLevel: ReasoningLevel = ReasoningLevel.OFF,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBody: List<CustomBody> = emptyList(),
    val sessionId: String? = null,
)

@Serializable
data class ImageGenerationParams(
    val model: Model,
    val prompt: String,
    val numOfImages: Int = 1,
    val aspectRatio: ImageAspectRatio = ImageAspectRatio.SQUARE,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBody: List<CustomBody> = emptyList(),
)

@Serializable
data class ImageEditParams(
    val model: Model,
    val prompt: String,
    val images: List<String>,
    val numOfImages: Int = 1,
    val aspectRatio: ImageAspectRatio = ImageAspectRatio.SQUARE,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBody: List<CustomBody> = emptyList(),
)

@Serializable
data class EmbeddingGenerationParams(
    val model: Model,
    val input: List<String>,
    val dimensions: Int? = null,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBody: List<CustomBody> = emptyList(),
)

@Serializable
data class EmbeddingGenerationResult(
    val model: String,
    val embeddings: List<List<Float>>,
)

@Serializable
data class CustomHeader(
    val name: String,
    val value: String
)

@Serializable
data class CustomBody(
    val key: String,
    val value: JsonElement
)

// Token估算接口，用于检测上下文是否超限
interface TokenEstimatable {
    suspend fun estimateInputTokens(
        messages: List<UIMessage>,
        tools: List<Tool> = emptyList()
    ): Long
}

/**
 * 估算文本的token数量（简单估算，中文每个字约1.5个token，英文每4个字符约1个token）
 */
fun estimateTokenCount(text: String): Long {
    val chineseCharCount = text.count { it.code in 0x4E00..0x9FFF }
    val otherCharCount = text.length - chineseCharCount
    return (chineseCharCount * 1.5 + otherCharCount * 0.25).toLong()
}

/**
 * 计算消息列表的总token数量
 */
fun calculateMessageTokens(messages: List<UIMessage>): Long {
    return messages.sumOf { message ->
        message.parts.sumOf { part ->
            when (part) {
                is me.rerere.ai.ui.UIMessagePart.Text -> estimateTokenCount(part.text)
                is me.rerere.ai.ui.UIMessagePart.Tool -> {
                    estimateTokenCount(part.tool.toolName) +
                    estimateTokenCount(part.tool.toolName) +
                    (part.tool.input?.toString()?.let { estimateTokenCount(it) } ?: 0L)
                }
                is me.rerere.ai.ui.UIMessagePart.ToolResult -> {
                    estimateTokenCount(part.content.toString())
                }
                else -> 0L
            }
        }
    }.toLong()
}

/**
 * 根据token限制截断消息列表
 * 从最早的消息开始移除，直到总token数在限制范围内
 */
fun List<UIMessage>.trimByTokenLimit(maxTokens: Int): List<UIMessage> {
    if (maxTokens <= 0) return this
    
    // 计算当前总token数
    var totalTokens = calculateMessageTokens(this)
    
    // 如果已经满足限制，直接返回
    if (totalTokens <= maxTokens) return this
    
    // 从最早的消息开始移除
    val result = mutableListOf<UIMessage>()
    for (i in indices.reversed()) {
        result.add(this[i])
        totalTokens = calculateMessageTokens(result)
        if (totalTokens <= maxTokens) {
            return result.reversed()
        }
    }
    
    // 如果所有消息都超过限制，至少保留最新消息
    return listOf(lastOrNull() ?: UIMessage.user("请重新描述你的问题"))
}
