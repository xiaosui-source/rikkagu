/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 外置记忆库配置（进阶记忆）
 * 每个记忆库对应 WebDAV 服务器上的一个目录（设置 → 备份 → WebDAV 配置全局复用）
 */
@Serializable
data class ExternalMemory(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    /** WebDAV 下的目录路径（相对 WebDAV path 根），空则用 external_memories/<id> */
    val webdavPath: String = "",
    /** 已废弃：旧版 Supabase 字段，保留兼容旧配置 */
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val tableName: String = "chat_messages",
    val summariesTableName: String = "memory_summaries",
    val enabled: Boolean = true,
    val autoSaveMessages: Boolean = true,
    val autoSaveDiarySummary: Boolean = false,
    val recallCount: Int = 5,
    val embeddingModelId: Uuid? = null,
)
