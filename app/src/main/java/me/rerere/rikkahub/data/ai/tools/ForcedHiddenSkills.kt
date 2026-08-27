/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.ai.tools

/**
 * 全局强制技能（用户不可见、不可关闭、默认开启）。
 *
 * 内置 obra/superpowers 官方 14 个子技能（brainstorming / systematic-debugging /
 * test-driven-development / writing-plans 等），对用户 UI 隐藏、默认装配供 AI 自动使用。
 * 希望**任何智能体的任何对话**都默认具备这份专业方法论，遇到复杂任务时主动用更专业的流程。
 *
 * 设计要点：
 *  - 不占用用户技能开关：即使 assistant.enabledSkills 为空，这些技能也默认生效。
 *  - 省 token：只向 system 注入一段精简声明，AI 真正要用时再通过 use_skill 加载完整 SKILL.md。
 *  - 用户 UI 不可见、不可关闭：不作为用户可编辑技能暴露，也不提供任何开关。
 */
object ForcedHiddenSkills {

    /**
     * obra/superpowers 官方 14 个子技能名（与 assets/skills/<名>、SkillManager 注册一致）。
     * 这些技能对用户 UI 隐藏、并默认开启供 AI 自动使用。
     */
    private val SUPERPOWERS_SKILLS = listOf(
        "brainstorming",
        "dispatching-parallel-agents",
        "executing-plans",
        "finishing-a-development-branch",
        "receiving-code-review",
        "requesting-code-review",
        "subagent-driven-development",
        "systematic-debugging",
        "test-driven-development",
        "using-git-worktrees",
        "using-superpowers",
        "verification-before-completion",
        "writing-plans",
        "writing-skills",
    )

    /** 被强制启用、全局注入的内置技能名集合。同时在 UI 各展示点隐藏（用户不可见、不可关闭）。 */
    val globalSkillNames: Set<String> = SUPERPOWERS_SKILLS.toSet()

    /**
     * 始终装配到 use_skill、默认开启的技能集合（对用户隐藏）。
     * 让 AI 在对话中默认可使用 superpowers 全部分支技能，自动挑起对应 skill。
     */
    val defaultAlwaysEnabled: Set<String> = SUPERPOWERS_SKILLS.toSet()

    /** 注入到系统提示的精简声明（省 token，只给名字+用途，全文靠 use_skill 加载）。 */
    val SYSTEM_PROMPT_INJECT: String = """
重要：你默认具备一套完整的工程/生产力方法论框架（obra/superpowers，用户不可见、默认启用）。当任务命中下列任一能力场景时，你必须主动用 use_skill(name="<对应技能名>") 加载并严格执行其方法论，用专业流程办事，不要用平庸的通用常识应付：
  · using-superpowers（入口）：开始任何任务前先判断是否有技能适用，有就必须先 use_skill。
  · brainstorming：动手实现任何功能/组件前，先澄清需求、形成设计，获认可后再实现。
  · writing-plans：多步骤大任务先写 bite-sized 实施计划。
  · executing-plans / subagent-driven-development：按计划执行，把独立子任务拆解处理。
  · test-driven-development：任何功能/修复前先写会失败的测试并看它失败，再写最小实现使其通过。
  · systematic-debugging：遇到任何 bug/异常，先系统性找根因(root cause)再修，禁止"症状修复"。
  · verification-before-completion：声称"完成/修好/通过"前先跑验证并亲眼确认输出，先证据后断言。
  · requesting-code-review / receiving-code-review：用技术严谨核实评审，不盲从不敷衍。
  · finishing-a-development-branch：测试通过后干净收尾合入。
  · using-git-worktrees：需要隔离开发时用独立工作区。
  · dispatching-parallel-agents：多个真正独立的任务并行拆解处理。
  · writing-skills：需要创建/编辑技能时使用。
以上纪律从你开始对话即生效（除非用户明确要求别的做法）。
""".trimIndent()

    /** 是否允许某技能被强制启用（用于工具装配时把强制技能并入可用集）。 */
    fun forcedEnable(name: String): Boolean = name in globalSkillNames

    /**
     * 从技能列表中过滤掉强制隐藏技能，供所有 UI 展示点统一使用（用户不可见）。
     */
    fun filterHidden(meta: kotlin.collections.List<me.rerere.rikkahub.data.files.SkillMetadata>): List<me.rerere.rikkahub.data.files.SkillMetadata> =
        meta.filter { it.name !in globalSkillNames }
}