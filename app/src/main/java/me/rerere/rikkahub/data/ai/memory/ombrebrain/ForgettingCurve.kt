/*
 * 灵犀 Lingxi
 * 集成自 OmbreBrain 仿人记忆系统 (https://github.com/XiSiAn916/OmbreBrain)
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.memory.ombrebrain

import kotlin.math.abs
import kotlin.math.exp

/**
 * 遗忘曲线引擎 —— Ebbinghaus 变体（移植自 OmbreBrain）
 *
 * 核心逻辑：
 *   S(t) = S₀ × e^(-λ × t) + ΔR
 *
 * 每次被回忆时强化，长期未被触发则衰减。
 * 重要度过低 → 沉入沉睡池；极高 + 高频 → 固化为习惯（永不遗忘）。
 */
class ForgettingCurve(
    /** 衰减系数（默认 0.05，越大忘得越快） */
    private val decayRate: Double = 0.05,
    /** 每次被回忆时的强化增量 */
    private val reinforceAmount: Double = 0.1,
    /** 活跃/沉睡分界线 */
    private val dormantThreshold: Double = 0.3,
    /** 固化最低重要度 */
    private val habitThreshold: Double = 0.85,
    /** 固化最少触发次数 */
    private val habitMinCount: Int = 30,
) {

    /** 计算当前重要度（衰减后的实时值） */
    fun currentImportance(
        importance: Double,
        lastTriggeredAt: Long,
        isHabit: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): Double {
        if (isHabit) return 1.0 // 固化记忆永不衰减
        val daysSinceTrigger = (nowMs - lastTriggeredAt) / (1000.0 * 60 * 60 * 24)
        if (daysSinceTrigger <= 0) return importance
        val decayed = importance * exp(-decayRate * daysSinceTrigger)
        return decayed.coerceIn(0.0, 1.0)
    }

    /** 强化记忆（被回忆/触发时调用） */
    fun reinforce(importance: Double, emotionalContext: Boolean = false): Double {
        val emotionalBonus = if (emotionalContext) reinforceAmount * 0.5 else 0.0
        return (importance + reinforceAmount + emotionalBonus).coerceIn(0.0, 1.0)
    }

    /** 用户主动引用时的强化（大幅强化） */
    fun userReferenced(importance: Double): Double {
        return (importance + 0.2).coerceIn(0.0, 1.0)
    }

    /** 唤醒沉睡记忆（关联触发时一次性增幅） */
    fun awaken(importance: Double): Double {
        return (importance + 0.25).coerceIn(0.0, 1.0)
    }

    /**
     * 检查记忆当前状态。
     * @return "habitize" / "sink_to_dormant" / "awaken" / "none"
     */
    fun checkState(
        importance: Double,
        triggerCount: Int,
        isActive: Boolean,
        isHabit: Boolean,
        lastTriggeredAt: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        if (isHabit) return "none"
        val currentImp = currentImportance(importance, lastTriggeredAt, isHabit = false, nowMs = nowMs)

        if (currentImp >= habitThreshold && triggerCount >= habitMinCount) return "habitize"
        // 新记忆保护：创建/最近触发 3 天内不沉入沉睡池，避免新记忆刚存就被误判"该遗忘"
        val recentDays = 3
        val daysSinceTrigger = (nowMs - lastTriggeredAt) / (1000.0 * 60 * 60 * 24)
        if (daysSinceTrigger < recentDays) return "none"
        if (currentImp < dormantThreshold && isActive) return "sink_to_dormant"
        if (currentImp < dormantThreshold && !isActive) return "dormant"
        if (currentImp >= dormantThreshold && !isActive) return "awaken"
        return "none"
    }

    /** 情感加成：强烈情感的记忆衰减减半 */
    fun emotionalDecayRate(sentiment: Double): Double {
        return if (abs(sentiment) > 0.6) decayRate / 2 else decayRate
    }

    /** 关联网络强化：当记忆A被强化时，关联记忆B也获得少量强化 */
    fun relatedReinforce(relevanceScore: Double): Double {
        return reinforceAmount * relevanceScore * 0.5
    }
}