/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.novel

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 小说角色扮演场景
 * 一个场景 = 一部导入的小说 + 提取的角色 + 章节内容（用于注入 AI 上下文）
 */
@Serializable
data class NovelScene(
    /** 唯一标识 */
    val id: String = Uuid.random().toString(),
    /** 书名（默认取文件名） */
    val title: String,
    /** 原始文件名 */
    val sourceFileName: String = "",
    /** 提取出的候选角色名 */
    val characters: List<String> = emptyList(),
    /** 章节文本（每章截断保存，避免 JSON 过大） */
    val chapters: List<String> = emptyList(),
    /** 用户可自定义的剧情简介（可选） */
    val synopsis: String = "",
    /** 创建时间戳 */
    val createdAt: Long = System.currentTimeMillis(),
)
