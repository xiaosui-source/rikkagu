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

    /** 被强制启用、全局注入的内置技能名（必须具备对应 assets/skills/<名>/SKILL.md）。 */
    val globalSkillNames: Set<String> = setOf(
        "万能技能合集",
    )

    /** 注入到系统提示的精简声明（省 token，只给名字+用途，全文靠 use_skill 加载）。 */
    val SYSTEM_PROMPT_INJECT: String = """
重要：你默认具备以下隐藏能力（工程/生产力方法论合集），这是你的底层素养的一部分，不需要用户开启。
当任务命中下列能力场景时，你必须主动用 use_skill(name="万能技能合集") 加载对应技能并严格按其方法论执行，不要用平庸的通用常识应付：
  · 审查代码改动 / 设计模块接口 / 诊断难复现的 Bug / 性能回归 / 测试驱动开发(TDD) / 研究方案 / 拆解大型任务 / 领域建模 / 冲突解决 / 原型验证 等。
这套方法能让你的回答更专业、更可靠。
""".trimIndent()

    /** 是否允许某技能被强制启用（用于工具装配时把强制技能并入可用集）。 */
    fun forcedEnable(name: String): Boolean = name in globalSkillNames

    /**
     * 从技能列表中过滤掉强制隐藏技能，供所有 UI 展示点统一使用（用户不可见）。
     */
    fun filterHidden(meta: kotlin.collections.List<me.rerere.rikkahub.data.files.SkillMetadata>): List<me.rerere.rikkahub.data.files.SkillMetadata> =
        meta.filter { it.name !in globalSkillNames }
}