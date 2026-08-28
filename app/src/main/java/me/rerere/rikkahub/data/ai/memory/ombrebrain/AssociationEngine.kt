/*
 * 灵犀 Lingxi
 * 集成自 OmbreBrain 仿人记忆系统 (https://github.com/XiSiAn916/OmbreBrain)
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.memory.ombrebrain

/**
 * 联想引擎 —— 触景生情（移植自 OmbreBrain）
 *
 * 将当前上下文（用户说的话、时间、天气等）与记忆库中的记忆做匹配，
 * 计算关联度，只召回最相关的记忆。
 *
 * 关联度计算公式：
 *   score = tagMatch × 0.4 + keywordMatch × 0.3 + timeMatch × 0.2 + contextMatch × 0.1
 */
class AssociationEngine {

    companion object {
        /** 触发"闪现想法"的关联度阈值 */
        const val FLASH_THRESHOLD = 0.5
        /** 直接注入上下文的关联度阈值 */
        const val DIRECT_INJECT_THRESHOLD = 0.6
        /** 默认召回条数 */
        const val DEFAULT_TOP_K = 8
    }

    /** 上下文信息 —— AI 在当前对话中感知到的环境 */
    data class ContextInfo(
        val userMessage: String,
        val timeOfDay: String = "",        // "morning" / "afternoon" / "evening" / "night"
        val currentApp: String = "",
        val listeningTo: String = "",
        val weather: String = "",
        val userEmotion: String = "",
    )

    /** 匹配结果 */
    data class MatchResult(
        val memory: BrainMemory,
        val relevanceScore: Double,
        val shouldFlash: Boolean,
        val shouldInject: Boolean,
    )

    /** 计算一条记忆与当前上下文的关联度 */
    fun calculateRelevance(memory: BrainMemory, context: ContextInfo): Double {
        val userMsg = context.userMessage.lowercase()

        // 1. 标签匹配 (0.4)
        val tagScore = if (memory.tags.isNotEmpty()) {
            val matchedTags = memory.tags.count { tag -> userMsg.contains(tag.lowercase()) }
            (matchedTags.toDouble() / memory.tags.size).coerceIn(0.0, 1.0)
        } else 0.0

        // 2. 关键词匹配 (0.3)：标题 与 内容开头 分词后匹配
        val titleKeywords = buildList {
            addAll(memory.title.lowercase().split(" ", "，", "。", "！", "？", "、", "；"))
            // 也取内容前若干词作为关键词
            addAll(contentKeywords(memory.content))
        }.distinct()
        val keywordScore = if (titleKeywords.isNotEmpty()) {
            val matchedKeywords = titleKeywords.count { kw ->
                kw.length > 1 && userMsg.contains(kw)
            }
            (matchedKeywords.toDouble() / titleKeywords.size).coerceIn(0.0, 1.0)
        } else 0.0

        // 3. 时间上下文匹配 (0.2)
        val timeScore = if (context.timeOfDay.isNotEmpty()) {
            if (memory.tags.contains(context.timeOfDay) || memory.title.contains(context.timeOfDay)) 0.8 else 0.2
        } else 0.3

        // 4. 全局上下文匹配 (0.1)
        val contextScore = calculateContextMatch(memory, context)

        return (tagScore * 0.4 + keywordScore * 0.3 + timeScore * 0.2 + contextScore * 0.1)
            .coerceIn(0.0, 1.0)
    }

    /**
     * 对一组记忆进行关联匹配，返回按关联度排序后的结果。
     * 结合 [forgettingCurve] 的当前有效重要度，避免召回已沉入沉睡池的过时记忆。
     */
    fun matchMemories(
        memories: List<BrainMemory>,
        context: ContextInfo,
        forgettingCurve: ForgettingCurve? = null,
        nowMs: Long = System.currentTimeMillis(),
        maxResults: Int = DEFAULT_TOP_K,
    ): List<MatchResult> {
        val reusableCurve = forgettingCurve ?: ForgettingCurve()
        return memories
            .asSequence()
            .map { memory ->
                // 计算当前有效重要度（含遗忘衰减）
                val eff = reusableCurve.currentImportance(
                    importance = memory.importance,
                    lastTriggeredAt = memory.lastTriggeredAt,
                    isHabit = memory.isHabit,
                    nowMs = nowMs,
                )
                memory.effectiveImportance = eff
                val baseScore = calculateRelevance(memory, context)
                // 关联度 × (0.5 + 有效重要度×0.5)：既看相关性，也看重不重要
                val finalScore = baseScore * (0.5 + eff * 0.5)
                MatchResult(
                    memory = memory,
                    relevanceScore = finalScore,
                    shouldFlash = finalScore >= FLASH_THRESHOLD && finalScore < DIRECT_INJECT_THRESHOLD,
                    shouldInject = finalScore >= DIRECT_INJECT_THRESHOLD,
                )
            }
            .filter { it.memory.isActive || it.memory.isHabit } // 只召回活跃和固化记忆
            .sortedByDescending { it.relevanceScore }
            .take(maxResults)
            .toList()
    }

    /** 从内容抽取关键词（去停用词，粗粒度） */
    private fun contentKeywords(content: String, limit: Int = 8): List<String> {
        val stopWords = setOf(
            "我", "你", "他", "她", "它", "我们", "你们", "他们", "这个", "那个",
            "一个", "的", "了", "是", "在", "有", "和", "就", "不", "都", "也",
            "很", "to", "the", "and", "is", "of", "a", "an", "in",
        )
        val tokens = content.split(" ", "，", "。", "！", "？", "、", "；", "：", "\n", "——", "。")
            .map { it.trim() }
            .filter { it.length > 1 && it.lowercase() !in stopWords }
        return tokens.distinct().take(limit)
    }

    /** 全局上下文匹配（时间、音乐、应用等环境信息） */
    private fun calculateContextMatch(memory: BrainMemory, context: ContextInfo): Double {
        var score = 0.0
        var factors = 0

        if (context.timeOfDay.isNotEmpty() &&
            (memory.tags.contains(context.timeOfDay) || memory.title.contains(context.timeOfDay))
        ) {
            score += 0.3; factors++
        }
        if (context.listeningTo.isNotEmpty() &&
            (memory.content.contains(context.listeningTo) || memory.tags.contains("music"))
        ) {
            score += 0.3; factors++
        }
        if (context.currentApp.isNotEmpty() && memory.tags.contains(context.currentApp)) {
            score += 0.2; factors++
        }

        return if (factors > 0) (score / factors).coerceIn(0.0, 1.0) else 0.0
    }
}