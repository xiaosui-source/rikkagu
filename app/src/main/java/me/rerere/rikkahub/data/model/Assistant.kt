/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.model
 
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import kotlin.uuid.Uuid
 
@Serializable
data class Assistant(
    val id: Uuid = Uuid.random(),
    val chatModelId: Uuid? = null, // 如果为null, 使用全局默认模型
    val name: String = "",
    val avatar: Avatar = Avatar.Dummy,
    val useAssistantAvatar: Boolean = false, // 使用助手头像替代模型头像
    val tags: List<Uuid> = emptyList(),
    val systemPrompt: String = "",
    val temperature: Float? = null,
    val topP: Float? = null,
    val contextMessageSize: Int = 0,
    val streamOutput: Boolean = true,
    val enableMemory: Boolean = false,
    val memoryReadOnly: Boolean = false,
    val useGlobalMemory: Boolean = false, // 使用全局共享记忆而非助手隔离记忆
    val enableRecentChatsReference: Boolean = false,
    val messageTemplate: String = "{{ message }}",
    val presetMessages: List<UIMessage> = emptyList(),
    val quickMessageIds: Set<Uuid> = emptySet(),
    val regexes: List<AssistantRegex> = emptyList(),
    // 对话渐变背景（上游新增）
    val useGradientBackground: Boolean = false,
    // 允许会话级提示注入（上游新增）
    val allowConversationPromptInjection: Boolean = false,
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val maxTokens: Int? = null,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val mcpServers: Set<Uuid> = emptySet(),
    // 启用的内置 MCP 工具组 id（全部默认启用，剩余 MCP 全开）
    val builtinMcpIds: Set<String> = setOf(
        "memory", "12306", "context7", "filesystem", "imagegps", "taifeng",
    ),
    val localTools: List<LocalToolOption> = listOf(LocalToolOption.TimeInfo),
    val workspaceId: Uuid? = null,
    val background: String? = null,
    val backgroundOpacity: Float = 1.0f,
    val modeInjectionIds: Set<Uuid> = emptySet(),      // 关联的模式注入 ID
    val lorebookIds: Set<Uuid> = emptySet(),            // 关联的 Lorebook ID
    val enabledSkills: Set<String> = emptySet(),        // 启用的 skill 名称列表
    val enableTimeReminder: Boolean = false,            // 时间间隔提醒注入
    val allowConversationSystemPrompt: Boolean = false, // 允许对话单独重写 system prompt
    val allowSkipReply: Boolean = false,
    val externalMemoryIds: Set<Uuid> = emptySet(),      // 关联的外置记忆库 ID
    val splitBubbleByLine: Boolean = false,             // 按模型自己写的换行拆分成多个独立气泡
    val splitUserBubbleByLine: Boolean = false,         // 用户消息按换行拆分成多个独立气泡
    // ===== Agent 编排（#2 规划 + #5 反思）=====
    // 长程自主 Agent：复杂任务先规划子任务清单再逐个执行；每次工具执行后反思自评、修正路线。
    val enableAgentPlanning: Boolean = true,            // 复杂任务前先输出行动计划（Planner）
    val enableAgentReflection: Boolean = true,          // 工具执行后反思自评，失败则修正/重试
    // 对话引擎增强（对齐 Operit EnhancedAIService）
    val disablePureThinkingWarning: Boolean = false,      // 禁止纯思考输出：移除思考后正文为空时，是否发出专用告警并回传给 AI 继续生成
    val enableTruncatedToolRepair: Boolean = true,       // 启用工具 XML 截断修复（detectAndRepairTruncatedToolRound）
    val enableDeadLoopDetection: Boolean = true,         // 启用死循环/重复检测（静默纠偏或停止）
    val enablePureThinkingDetection: Boolean = true,     // 启用纯思考检测（静默催答或回传告警）
)

@Serializable
data class QuickMessage(
    val id: Uuid = Uuid.random(),
    val title: String = "",
    val content: String = "",
)
 
@Serializable
data class AssistantMemory(
    val id: Int,
    val content: String = "",
    // ===== OmbreBrain 仿人记忆字段（默认值保证向后兼容）=====
    val title: String = "",
    val importance: Double = 0.3,
    val sentiment: Double = 0.0,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long = System.currentTimeMillis(),
    val triggerCount: Int = 1,
    val isActive: Boolean = true,
    val isHabit: Boolean = false,
    val source: String = "ai",
)
 
@Serializable
enum class AssistantAffectScope {
    USER,
    ASSISTANT,
}
 
@Serializable
data class AssistantRegex(
    val id: Uuid,
    val name: String = "",
    val enabled: Boolean = true,
    val findRegex: String = "", // 正则表达式
    val replaceString: String = "", // 替换字符串
    val affectingScope: Set<AssistantAffectScope> = setOf(),
    val visualOnly: Boolean = false, // 是否仅在视觉上影响
)
 
fun String.replaceRegexes(
    assistant: Assistant?,
    scope: AssistantAffectScope,
    visual: Boolean = false
): String {
    if (assistant == null) return this
    if (assistant.regexes.isEmpty()) return this
    return assistant.regexes.fold(this) { acc, regex ->
        if (regex.enabled && regex.visualOnly == visual && regex.affectingScope.contains(scope)) {
            try {
                val result = acc.replace(
                    regex = Regex(regex.findRegex),
                    replacement = regex.replaceString,
                )
                // println("Regex: ${regex.findRegex} -> ${result}")
                result
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果正则表达式格式错误，返回原字符串
                acc
            }
        } else {
            acc
        }
    }
}
 
/**
 * 注入位置
 */
@Serializable
enum class InjectionPosition {
    @SerialName("before_system_prompt")
    BEFORE_SYSTEM_PROMPT,   // 合并进 System 文本前部

    @SerialName("after_system_prompt")
    AFTER_SYSTEM_PROMPT,    // 合并进 System 文本后部（最常用）

    @SerialName("top_of_chat")
    TOP_OF_CHAT,            // 合并进 System 文本尾部（缓存友好，不再插入消息）

    @SerialName("bottom_of_chat")
    BOTTOM_OF_CHAT,         // 合并进 System 文本尾部（缓存友好，不再插入消息）

    @SerialName("at_depth")
    AT_DEPTH,               // 合并进 System 文本尾部（缓存友好，不再插入消息）
}
 
/**
 * 提示词注入
 *
 * - ModeInjection: 基于模式开关的注入（如学习模式）
 * - RegexInjection: 基于正则匹配的注入（Lorebook）
 */
@Serializable
sealed class PromptInjection {
    abstract val id: Uuid
    abstract val name: String
    abstract val enabled: Boolean
    abstract val priority: Int
    abstract val position: InjectionPosition
    abstract val content: String
    abstract val injectDepth: Int  // 当 position 为 AT_DEPTH 时使用，表示从最新消息往前数的位置
    abstract val role: MessageRole  // 注入角色：USER 或 ASSISTANT
 
    /**
     * 模式注入 - 基于开关状态触发
     */
    @Serializable
    @SerialName("mode")
    data class ModeInjection(
        override val id: Uuid = Uuid.random(),
        override val name: String = "",
        override val enabled: Boolean = true,
        override val priority: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val content: String = "",
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
    ) : PromptInjection()
 
    /**
     * 正则注入 - 基于内容匹配触发（世界书）
     */
    @Serializable
    @SerialName("regex")
    data class RegexInjection(
        override val id: Uuid = Uuid.random(),
        override val name: String = "",
        override val enabled: Boolean = true,
        override val priority: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val content: String = "",
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
        val keywords: List<String> = emptyList(),  // 触发关键词
        val useRegex: Boolean = false,             // 是否使用正则匹配
        val caseSensitive: Boolean = false,        // 大小写敏感
        val scanDepth: Int = 4,                    // 扫描最近N条消息
        val constantActive: Boolean = false,       // 常驻激活（无需匹配）
    ) : PromptInjection()
}
 
/**
 * Lorebook - 组织管理多个 RegexInjection
 */
@Serializable
data class Lorebook(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val entries: List<PromptInjection.RegexInjection> = emptyList(),
)
 
/**
 * 检查 RegexInjection 是否被触发
 *
 * @param context 要扫描的上下文文本
 * @return 是否触发
 */
fun PromptInjection.RegexInjection.isTriggered(context: String): Boolean {
    if (!enabled) return false
    if (constantActive) return true
    if (keywords.isEmpty()) return false
 
    return keywords.any { keyword ->
        if (useRegex) {
            try {
                val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(keyword, options).containsMatchIn(context)
            } catch (e: Exception) {
                false
            }
        } else {
            if (caseSensitive) {
                context.contains(keyword)
            } else {
                context.contains(keyword, ignoreCase = true)
            }
        }
    }
}
 
/**
 * 从消息列表中提取用于匹配的上下文文本
 *
 * @param messages 消息列表
 * @param scanDepth 扫描深度（最近N条消息）
 * @return 拼接的文本内容
 */
fun extractContextForMatching(
    messages: List<UIMessage>,
    scanDepth: Int
): String {
    return messages
        .takeLast(scanDepth)
        .joinToString("\n") { it.toText() }
}
 
/**
 * 获取所有被触发的注入，按优先级排序
 *
 * @param injections 所有注入规则
 * @param context 上下文文本
 * @return 被触发的注入列表，按优先级降序排列
 */
fun getTriggeredInjections(
    injections: List<PromptInjection.RegexInjection>,
    context: String
): List<PromptInjection.RegexInjection> {
    return injections
        .filter { it.isTriggered(context) }
        .sortedByDescending { it.priority }
}