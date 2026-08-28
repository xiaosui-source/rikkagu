/*
 * 灵犀 Lingxi
 * 集成自 OmbreBrain 仿人记忆系统 (https://github.com/XiSiAn916/OmbreBrain)
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.memory.ombrebrain

import kotlin.math.abs
import kotlin.math.ln

/**
 * 重要度评分器（移植自 OmbreBrain）
 *
 * 综合多个维度计算一条记忆的重要度 (0.0 ~ 1.0)
 *
 * 权重分配：
 * - 频次权重 20%：被提及/触发的次数
 * - 情感权重 30%：情感强度绝对值
 * - 交互深度 30%：消息长度、标签数、关联数
 * - 用户引用 20%：用户主动提及/引用该记忆
 */
class ImportanceScorer {

    companion object {
        private const val FREQUENCY_WEIGHT = 0.20
        private const val SENTIMENT_WEIGHT = 0.30
        private const val DEPTH_WEIGHT = 0.30
        private const val REFERENCE_WEIGHT = 0.20

        private const val FREQUENCY_SATURATION = 50.0
    }

    /** 计算重要度 */
    fun calculate(memory: BrainMemory, userReferenced: Boolean = false): Double {
        val frequencyScore = calculateFrequencyScore(memory.triggerCount)
        val sentimentScore = calculateSentimentScore(memory.sentiment)
        val depthScore = calculateDepthScore(memory)
        val referenceScore = if (userReferenced) 1.0 else 0.0

        return (frequencyScore * FREQUENCY_WEIGHT +
            sentimentScore * SENTIMENT_WEIGHT +
            depthScore * DEPTH_WEIGHT +
            referenceScore * REFERENCE_WEIGHT)
            .coerceIn(0.0, 1.0)
    }

    /** 快速计算新记忆的初始重要度 */
    fun initialScore(sentiment: Double, contentLength: Int, tagCount: Int = 0): Double {
        val sentimentContribution = abs(sentiment) * 0.4
        val lengthContribution = (contentLength.toDouble() / 500.0).coerceIn(0.0, 0.3)
        val tagContribution = (tagCount.toDouble() / 10.0).coerceIn(0.0, 0.1)
        return (0.2 + sentimentContribution + lengthContribution + tagContribution).coerceIn(0.0, 1.0)
    }

    /** 频次得分：对数增长，逐渐饱和 */
    private fun calculateFrequencyScore(count: Int): Double {
        if (count <= 0) return 0.0
        return (ln(count.toDouble() + 1.0) / ln(FREQUENCY_SATURATION + 1.0)).coerceIn(0.0, 1.0)
    }

    /** 情感得分：情感越强烈（无论正负），得分越高 */
    private fun calculateSentimentScore(sentiment: Double): Double {
        return abs(sentiment).coerceIn(0.0, 1.0)
    }

    /** 深度得分：基于内容复杂度和关联记忆数 */
    private fun calculateDepthScore(memory: BrainMemory): Double {
        val contentScore = (memory.content.length.toDouble() / 1000.0).coerceIn(0.0, 0.5)
        val tagScore = (memory.tags.size.toDouble() / 10.0).coerceIn(0.0, 0.3)
        val relationScore = (memory.relatedIds.size.toDouble() / 20.0).coerceIn(0.0, 0.2)
        return (contentScore + tagScore + relationScore).coerceIn(0.0, 1.0)
    }
}