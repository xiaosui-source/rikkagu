/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 记忆实体 —— 集成 OmbreBrain 仿人记忆系统字段
 *
 * 在原有 (id, content) 基础上扩展：
 * - importance:  重要度 0.0~1.0（遗忘曲线/评分用）
 * - sentiment:   情感强度 -1.0~1.0
 * - tags:        标签（JSON 序列化的逗号分隔字符串，因 Room 无 List TypeConverter 简化存储）
 * - trigger_count / last_triggered_at: 遗忘曲线强化用
 * - is_habit / is_active: 记忆状态（活跃/沉睡/固化）
 * - source: 记忆来源
 */
@Entity
data class MemoryEntity(
    @PrimaryKey(true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("content")
    val content: String = "",
    /** 记忆标题（可选，用于联想关键词语义） */
    @ColumnInfo("title")
    val title: String = "",
    /** 重要度 0.0 ~ 1.0 */
    @ColumnInfo("importance")
    val importance: Double = 0.3,
    /** 情感强度 -1.0 ~ 1.0 */
    @ColumnInfo("sentiment")
    val sentiment: Double = 0.0,
    /** 标签（逗号分隔，空串=无标签） */
    @ColumnInfo("tags")
    val tags: String = "",
    /** 创建时间 (Unix ms) */
    @ColumnInfo("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    /** 最后触发/回忆时间 (Unix ms) */
    @ColumnInfo("last_triggered_at")
    val lastTriggeredAt: Long = System.currentTimeMillis(),
    /** 触发/回忆次数 */
    @ColumnInfo("trigger_count")
    val triggerCount: Int = 1,
    /** 是否活跃 */
    @ColumnInfo("is_active")
    val isActive: Boolean = true,
    /** 是否固化（永不遗忘） */
    @ColumnInfo("is_habit")
    val isHabit: Boolean = false,
    /** 记忆来源：ai / user_interaction / system */
    @ColumnInfo("source")
    val source: String = "ai",
    /** 关联记忆 ID（逗号分隔） */
    @ColumnInfo("related_ids")
    val relatedIds: String = "",
)