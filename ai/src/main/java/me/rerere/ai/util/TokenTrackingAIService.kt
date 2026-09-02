/*
 * Token Tracking Service - 适配RikkaHub
 */
package me.rerere.ai.util

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.ai.provider.*
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage

class TokenTrackingAIService {
    companion object {
        private const val TAG = "TokenTrackingAIService"
    }

    // Token跟踪服务简化实现
    suspend fun trackTokenUsage(
        providerSetting: ProviderSetting,
        messages: List<UIMessage>,
        params: TextGenerationParams,
        result: MessageChunk
    ): TokenUsageRecord {
        Log.d(TAG, "trackTokenUsage called")
        return TokenUsageRecord(
            model = params.model.modelId,
            inputTokens = estimateTokenCount(messages),
            outputTokens = 0L
        )
    }

    private fun estimateTokenCount(messages: List<UIMessage>): Long {
        var count = 0L
        for (msg in messages) {
            count += msg.parts.sumOf { part ->
                when (part) {
                    is me.rerere.ai.ui.UIMessagePart.Text -> me.rerere.ai.util.estimateTokenCount(part.text)
                    is me.rerere.ai.ui.UIMessagePart.Tool -> me.rerere.ai.util.estimateTokenCount(part.toolName)
                    else -> 0L
                }
            }
        }
        return count
    }
}

data class TokenUsageRecord(
    val model: String,
    val inputTokens: Long,
    val outputTokens: Long
)
