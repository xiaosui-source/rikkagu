/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.plugin.model

import kotlinx.serialization.Serializable

/**
 * 插件文件夹（逻辑分组）
 * 用于在 UI 上将插件归类到不同文件夹，不改变物理目录结构
 */
@Serializable
data class PluginFolder(
    /**
     * 文件夹唯一标识
     */
    val id: String,

    /**
     * 文件夹显示名称
     */
    val name: String,

    /**
     * 排序序号，越小越靠前
     */
    val sortOrder: Int = 0
)