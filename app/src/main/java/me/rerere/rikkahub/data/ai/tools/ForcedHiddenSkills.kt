/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.ai.tools

/**
 * 全局强制技能（用户不可见、不可关闭）。
 *
 * 这些技能方法的本质是「让 AI 不傻」的工程/生产力方法论套路（源自 mattpocock/skills，
 * 灵犀已内置为「万能技能合集」）。我们希望**任何智能体的任何对话**都默认具备这份能力，
 * 从而让 AI 遇到复杂任务时主动用更专业的方法论（先拆解、先 TDD、先诊断反馈环、
 * 先做深度模块设计等），而不是套用平庸常识。
 *
 * 设计要点：
 *  - 不占用用户技能开关：即使 assistant.enabledSkills 为空，这些技能也强制生效。
 *  - 省 token：只向 system 注入一小段「技能名 + 一句话用途」的极简声明（约百字），
 *    AI 真正要用时再通过 use_skill 工具加载完整 SKILL.md。
 *  - 用户 UI 不可见、不可关闭：不作为用户可编辑技能暴露，也不提供任何开关。
 */
object ForcedHiddenSkills {

    /**
     * 被强制启用、全局注入的内置技能名集合。
     * 默认保持为空，不会自动注入隐藏技能，
     * 如需启用请手动在这里加入技能名称，或在设置中将对应技能加入 `assistant.enabledSkills`。
     */
    val globalSkillNames: Set<String> = emptySet()

    /**
     * 始终装配到 use_skill、供 AI 自动决定的默认技能合集名。
     * 这些技能在不要求用户手动开启的情况下，也会出现在 AI 的可用技能清单里，
     * 由 AI 根据任务自动判断是否 use_skill 加载。等同于「全局默认可用」但不强制注入系统提示。
     */
    val defaultAlwaysEnabled: Set<String> = setOf(
        "万能技能合集",
        "Superpowers技能合集",
    )

    /** 注入到系统提示的精简声明（省 token，只给名字+用途，全文靠 use_skill 加载）。 */
    val SYSTEM_PROMPT_INJECT: String = """
重要：你默认具备两套工程/生产力方法论合集（这是你的底层素养，不需要用户开启），当任务命中下列能力场景时，你必须主动用 use_skill(name="万能技能合集" 或 "Superpowers技能合集") 加载对应方法论并严格执行，不要用平庸的通用常识应付：
  · 万能技能合集：审查代码 / 设计模块接口 / 诊断难复现的 Bug / 性能回归 / 测试驱动开发(TDD) / 研究方案 / 拆解大型任务 / 领域建模 / 原型验证 / 教学讲解 等。
  · Superpowers技能合集：头脑风暴设计(superpowers:brainstorming) / 系统化调试(find root cause first) / TDD(先失败测试) / 写实施计划 / 执行计划 / 代码评审 / 完成前验证(先证据后断言) / 并行任务拆解 等。
这两套方法能让你的回答更专业、更可靠。若两者都匹配，以更具体的为准。
""".trimIndent()

    /** 是否允许某技能被强制启用（用于工具装配时把强制技能并入可用集）。 */
    fun forcedEnable(name: String): Boolean = name in globalSkillNames

    /**
     * 从技能列表中过滤掉强制隐藏技能，供所有 UI 展示点统一使用（用户不可见）。
     */
    fun filterHidden(meta: kotlin.collections.List<me.rerere.rikkahub.data.files.SkillMetadata>): List<me.rerere.rikkahub.data.files.SkillMetadata> =
        meta.filter { it.name !in globalSkillNames }
}