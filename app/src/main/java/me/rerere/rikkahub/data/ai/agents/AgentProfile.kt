/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.agents

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 智能体（Agent）配置
 *
 * 一个智能体 = 一个「角色/专长」+ 绑定一个现有 Assistant（复用其模型、密钥、参数）。
 * 主助手（A）在对话中可以把子任务通过工具调用转交给其他智能体（B）处理。
 */
@Serializable
data class AgentProfile(
    /** 唯一标识（同时用作工具名后缀，仅允许字母数字与下划线横杠） */
    val id: String = Uuid.random().toString(),
    /** 显示名称，例如「写作助手」「翻译助手」 */
    val name: String,
    /** 能力描述：写入模型可见的工具描述，说明它擅长什么、何时应该转交 */
    val description: String = "",
    /** 绑定的 Assistant id（复用其模型/温度/密钥等配置） */
    val assistantId: Uuid = Uuid.random(),
    /** 可选：覆盖 Assistant 的系统提示词（不填则用 Assistant 自带的） */
    val systemPrompt: String = "",
    /** 可选：覆盖采样温度 */
    val temperature: Float? = null,
    /** 可选：覆盖最大输出 token */
    val maxTokens: Int? = null,
    /** 是否启用（关闭后不再注入工具、模型看不到它） */
    val enabled: Boolean = true,
)
