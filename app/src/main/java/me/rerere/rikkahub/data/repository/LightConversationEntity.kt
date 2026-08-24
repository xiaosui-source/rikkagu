/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.repository

/**
 * 轻量级的会话查询结果，不包含 nodes 和 suggestions 字段。
 *
 * 独立成文件：该类型被 `ConversationDAO` 引用，若定义在 `ConversationRepository.kt` 内，
 * 会形成 DAO ↔ Repository 的循环依赖，导致 Room KSP 在处理 DAO 时无法解析该类型
 * （`MissingType` / `RoomKspProcessor was unable to process`）。
 */
data class LightConversationEntity(
    val id: String,
    val assistantId: String,
    val title: String,
    val isPinned: Boolean,
    val createAt: Long,
    val updateAt: Long,
    val folderId: String = "",
)
