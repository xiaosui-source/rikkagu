/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.ai.tools

/**
 * 全局强制技能（用户不可见、不可关闭、默认开启）。
 *
 * 内置 obra/superpowers 的工程/生产力方法论（灵犀整理为「Superpowers技能合集」）。
 * 希望**任何智能体的任何对话**都默认具备这份能力，从而让 AI 遇到复杂任务时
 * 主动用更专业的方法论（先 brainstorm、先 TDD、先找根因、先验证），而不是套用平庸常识。
 *
 * 设计要点：
 *  - 不占用用户技能开关：即使 assistant.enabledSkills 为空，这些技能也默认生效。
 *  - 省 token：只向 system 注入一小段「技能名 + 一句话用途」的极简声明，
 *    AI 真正要用时再通过 use_skill 工具加载完整 SKILL.md。
 *  - 用户 UI 不可见、不可关闭：不作为用户可编辑技能暴露，也不提供任何开关。
 */
object ForcedHiddenSkills {

    /**
     * 被强制启用、全局注入的内置技能名集合。
     * 同时在 UI 各展示点隐藏（用户在该技能选择/管理界面不可见、无法手动开关）。
     */
    val globalSkillNames: Set<String> = setOf(
        "Superpowers技能合集",
    )

    /**
     * 始终装配到 use_skill、供 AI 自动决定的默认技能合集（默认开启、对用户隐藏）。
     *
     * 设计：只保留 obra/superpowers 作为默认自动主动框架（自动接管流程），
     * 对用户 UI 不可见但 AI 默认可用。mattpocock/skills（万能技能合集）已移除。
     */
    val defaultAlwaysEnabled: Set<String> = setOf(
        "Superpowers技能合集",
    )

    /** 注入到系统提示的精简声明（省 token，只给名字+用途，全文靠 use_skill 加载）。 */
    val SYSTEM_PROMPT_INJECT: String = """
重要：你默认具备一套完整的工程/生产力方法论合集（Superpowers 技能合集），这是你的底层素养的一部分，不需要用户开启。当任务命中下列能力场景时，你必须主动用 use_skill(name="Superpowers技能合集") 加载对应方法论并严格执行，用专业流程办事，不要用平庸的通用常识应付：
  · brainstorming：动手实现任何功能前先澄清需求、形成设计，获得认可后再实现。
  · systematic-debugging：遇到任何 bug/异常，先系统性找根因（root cause）再修，禁止"症状修复"。
  · test-driven-development：任何功能/修复前先写会失败的测试，再看它失败、写最小实现使其通过。
  · writing-plans：多步骤大任务先写 bite-sized 实施计划再执行。
  · executing-plans / verification-before-completion：执行计划并"先验证后断言"，声称完成前必须亲眼确认输出。
  · dispatching-parallel-agents：多个真正独立的任务并行拆解处理。
  · requesting-code-review / receiving-code-review：代码评审用技术严谨核实，不盲从不敷衍。
  · finishing-a-development-branch：完成测试通过后干净地收尾合入。
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