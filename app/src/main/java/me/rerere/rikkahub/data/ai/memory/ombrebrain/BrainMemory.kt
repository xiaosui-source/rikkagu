/*
 * 灵犀 Lingxi
 * 集成自 OmbreBrain 仿人记忆系统 (https://github.com/XiSiAn916/OmbreBrain)
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.memory.ombrebrain

/**
 * 记忆的运行期视图（算法层用）。
 *
 * 与 `MemoryEntity` 解耦：算法引擎只依赖这个纯数据类，便于测试和不影响 DB schema。
 * `OmbreMemoryEngine` 负责在 `MemoryEntity` 与 `BrainMemory` 之间转换。
 */
data class BrainMemory(
    val id: Int,
    /** 记忆标题 —— 一句话概括 */
    val title: String = "",
    /** 记忆全文内容 */
    val content: String = "",
    /** 重要度 0.0 ~ 1.0（为兼容现有初始值默认 0.3） */
    val importance: Double = 0.3,
    /** 情感强度 -1.0(负面) ~ 1.0(正面) */
    val sentiment: Double = 0.0,
    /** 标签列表 */
    val tags: List<String> = emptyList(),
    /** 创建时间 (Unix ms) */
    val createdAt: Long = System.currentTimeMillis(),
    /** 最后触发/回忆时间 (Unix ms) */
    val lastTriggeredAt: Long = System.currentTimeMillis(),
    /** 触发/回忆次数 */
    val triggerCount: Int = 1,
    /** 是否处于活跃池 */
    val isActive: Boolean = true,
    /** 是否已固化为习惯（永不遗忘） */
    val isHabit: Boolean = false,
    /** 记忆来源：ai / user_interaction / system */
    val source: String = "ai",
    /** 关联的记忆 ID 列表 */
    val relatedIds: List<Long> = emptyList(),
) {
    /** 决策用的重要度（合并衰减后的当前值，由调用方填充） */
    var effectiveImportance: Double = importance
}