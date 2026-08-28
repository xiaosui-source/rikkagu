/*
 * 灵犀 Lingxi
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 表情包渲染器配置模型 —— 对齐 Operit "洛玑表情包渲染器" 的完整配置项
 */

package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

/** 单个角色/全局的表情包配置 */
@Serializable
data class StickerProfile(
    val id: String = "",
    val name: String = "表情包配置",
    /** 绑定角色卡 ID，空 = 全局默认 */
    val characterCardId: String = "",
    val characterCardName: String = "",
    /** 本地表情包目录列表 */
    val dirs: List<String> = emptyList(),
    /** 外链表情列表原始文本（每行 名字: url） */
    val externalText: String = "",
)

/** 表情包渲染器总配置 */
@Serializable
data class StickerSettings(
    /** 是否开启自动注入系统提示词 */
    val autoInject: Boolean = true,
    /** 每条回复最多表情数 */
    val maxPerReply: Int = 2,
    /** 附加自定义规则 */
    val extraRules: String = "",
    /** 各角色配置（含全局默认，characterCardId 为空的那个） */
    val profiles: List<StickerProfile> = emptyList(),
    /** 默认本地目录（无配置时的兜底） */
    val defaultDirs: String = "/sdcard/Download/sticker",
)