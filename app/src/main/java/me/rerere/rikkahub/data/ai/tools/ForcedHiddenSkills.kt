/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.ai.tools

/**
 * 全局强制技能辅助（当前为空态）。
 *
 * 用于「无条件注入 / UI 隐藏」技能的能力。当前未内置任何强制技能：
 *  - globalSkillNames 为空：不强制注入、也不对任何技能做 UI 隐藏。
 *  - defaultAlwaysEnabled 为空：不为 use_skill 默认装配任何技能。
 * 技能由用户在设置中自行启用后，AI 通过 use_skill 使用即可。
 */
object ForcedHiddenSkills {

    /**
     * 被强制启用、全局注入的内置技能名集合。
     * 当前为空：不强制注入/隐藏任何技能，由用户在设置中自行启用需要的技能。
     */
    val globalSkillNames: Set<String> = emptySet()

    /**
     * 始终装配到 use_skill、默认开启的技能集合。
     * 当前为空：不默认注入任何技能覆盖。use_skill 仅在用户启用了技能时可用。
     */
    val defaultAlwaysEnabled: Set<String> = emptySet()

    /** 注入到系统提示的精简声明（当前为空：不注入任何强制技能提示）。 */
    val SYSTEM_PROMPT_INJECT: String = ""

    /** 是否允许某技能被强制启用（用于工具装配时把强制技能并入可用集）。 */
    fun forcedEnable(name: String): Boolean = name in globalSkillNames

    /**
     * 从技能列表中过滤掉强制隐藏技能，供所有 UI 展示点统一使用（用户不可见）。
     */
    fun filterHidden(meta: kotlin.collections.List<me.rerere.rikkahub.data.files.SkillMetadata>): List<me.rerere.rikkahub.data.files.SkillMetadata> =
        meta.filter { it.name !in globalSkillNames }
}