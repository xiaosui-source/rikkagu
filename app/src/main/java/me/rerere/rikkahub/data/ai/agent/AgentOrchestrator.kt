/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.agent

/**
 * Agent 编排（#2 规划 + #5 反思）：
 * 在现有工具调用循环之上注入"先规划、后执行、再反思修正"的结构化引导。
 *
 * 设计原则：
 * - 不改变 UIMessage 结构、不新增工具、不动消息回填逻辑。
 * - 仅通过注入内部 system 提示（internalForcePrompt 类似机制）给模型施加压力，
 *   完全向后兼容：用户可对每个助手单独开关。
 * - 判断"是否复杂任务/是否需要规划"采用启发式规则，避免每次小任务都过度编排。
 */
object AgentOrchestrator {
    private const val TAG = "AgentOrchestrator"

    /**
     * 判断是否需要进入"规划-执行-反思"的 agent 编排流水线。
     * 只在任务确实复杂时才做前缀规划，简单问答/单次工具调用不打断模型正常节奏。
     */
    fun needsPlanning(
        // 最近一条用户消息文本
        userText: String,
        // 当前已有工具调用（已有工具调用说明已在执行中，跳过重复规划）
        hasToolActivity: Boolean,
        // 全局开关
        enablePlanning: Boolean,
    ): Boolean {
        if (!enablePlanning) return false
        if (userText.isBlank() || hasToolActivity) return false
        val lower = userText.lowercase()
        // 需要规划的信号词：多步骤/复杂任务/项目/编排/自动化/流程
        val planningSignals = listOf(
            "这一步", "下一步", "然后", "依次", "多个", "逐个",
            "做一个", "搭建", "设置", "配置", "安装", "批量", "整个",
            "帮我安排", "规划", "计划", "项目", "流程", "自动化",
            "整理", "迁移", "下载", "爬取", "批量下载",
            "step 1", "step1", "steps", "todo", "list the steps",
        )
        // 长度启发式：较长的任务指令更可能需规划
        val longTask = userText.length >= 28
        return longTask && planningSignals.any { lower.contains(it) }
    }

    /**
     * 判断是否需要反思：只要出现了工具执行结果（无论成功失败），就值得反思一次。
     * 反思注入的时机由调用方控制（仅在首次出现工具结果且尚未反思过时注入一次）。
     */
    fun needsReflection(
        enableReflection: Boolean,
        hasToolResults: Boolean,
        alreadyReflected: Boolean,
    ): Boolean {
        return enableReflection && hasToolResults && !alreadyReflected
    }

    /** 规划提示：注入到 system 末尾，要求模型先给出行动计划再执行。 */
    fun buildPlannerPrompt(): String = buildString {
        appendLine("### Agent 规划指令")
        appendLine("这次任务比较复杂、需要多步协作。请严格按以下步骤执行：")
        appendLine("1. 先输出你的《执行计划》：用简短的编号列表列出接下来要完成的子任务（每项一句话，标明依赖哪个工具的哪个结果）以及大致的完成标志。")
        appendLine("2. 计划只作内部导航，不需要向用户炫耀；不要输出冗长的解释，2~6 项即可。")
        appendLine("3. 随后立即开始执行第一个子任务：调用对应工具。每完成一个子任务就继续下一步，直到计划全部完成。")
        appendLine("4. 如果执行中发现计划不合理，可以中途调整，但请在工具调用之间的间隙用一句话说明你调整了什么。")
        appendLine("5. 全部完成后，给用户一个干净的总结（做了什么、结果如何、下一步建议）。")
    }

    /** 反思提示：注入到 system 末尾，要求模型对上一步工具结果自评并决定修正或前进。 */
    fun buildReflectionPrompt(): String = buildString {
        appendLine("### Agent 反思指令")
        appendLine("你刚刚执行了工具，现在必须做一次简短的自我反思（不要在正文向用户展示，用来决定下一步）：")
        appendLine("1. 上一步工具结果是否符合预期？是否成功？")
        appendLine("2. 如果失败或结果不理想：明确说出失败原因，然后决定是【重试】还是【调整参数】还是【换一种工具/方法】——并立即执行修正动作。")
        appendLine("3. 如果成功：确认本子任务已完成，继续计划中的下一个子任务，或进入收尾。")
        appendLine("4. 切勿带着错误结果继续硬做，也不要反复尝试同一种失败的方式超过 2 次。")
    }
}