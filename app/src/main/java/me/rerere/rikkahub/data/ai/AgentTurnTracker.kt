/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai

import android.util.Log
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Agent 回合跟踪器（补实自上游的 no-op stub）。
 *
 * 职责：
 *  1. **自动化审计**：把每次屏幕自动化动作（tap / swipe / scroll / find_node / global_action）
 *     写入 [me.rerere.rikkahub.data.security.SecurityAuditRepository]（category = "automation"），
 *     让"AI 在屏幕上做过什么"可追溯、可观测。
 *  2. **安全限流**：对高频自动化动作做滚动窗口限流，防止 AI 失控式乱点/刷屏；
 *     超过阈值本回合强制暂停并记录 blocked，返回 false 让调用方决定是否中断。
 *
 * 设计：用 Koin 惰性解析 SecurityAuditRepository（与 WorkflowEngine 等保持一致的注入方式），
 * 对既有 6 处 `recordAutomationAction()` 无参调用完全向后兼容（默认 action="gesture"）。
 */
object AgentTurnTracker {
    private const val TAG = "AgentTurnTracker"

    // ===== 安全限流参数 =====
    /** 时间窗口（毫秒）：在此窗口内累计的自动化动作不能超过 [MAX_AUTOMATION_WINDOW_COUNT] */
    private const val WINDOW_MS = 30_000L
    /** 每个时间窗口内允许的最大自动化动作次数（防失控） */
    private const val MAX_AUTOMATION_WINDOW_COUNT = 120

    // 滚动时间戳队列（线程安全），用于窗口计数
    private val recentAutomationTimestamps = ConcurrentLinkedDeque<Long>()

    /**
     * 记录一次自动化动作，并返回是否被许可继续执行。
     *
     * @param action 动作类型（tap / swipe / scroll / find_node / global_action / gesture 等）
     * @param detail 附加信息（如坐标/选择器，可选）
     * @return true=允许并已审计；false=已达安全限流，动作被拦截（未执行）
     */
    fun recordAutomationAction(
        action: String = "gesture",
        detail: String = "",
    ): Boolean {
        val now = System.currentTimeMillis()

        // 清理窗口内过期的时间戳
        purgeExpired(now)

        val allowed = recentAutomationTimestamps.size < MAX_AUTOMATION_WINDOW_COUNT
        if (!allowed) {
            logEvent(
                action = action,
                detail = "自动化动作已达安全限流阈值($MAX_AUTOMATION_WINDOW_COUNT/$WINDOW_MS ms)${
                    if (detail.isNotBlank()) " · $detail" else ""
                }",
                status = "blocked",
            )
            Log.w(TAG, "Automation rate-limited: $action (reached $MAX_AUTOMATION_WINDOW_COUNT/$WINDOW_MS ms)")
            return false
        }

        // 记录本次动作时间戳 + 写审计日志
        recentAutomationTimestamps.addLast(now)
        logEvent(action = action, detail = detail, status = "success")
        Log.d(TAG, "automation action: $action (count=${recentAutomationTimestamps.size}/$MAX_AUTOMATION_WINDOW_COUNT in window)")
        return true
    }

    /** 查询当前时间窗口内是否已接近/达到限流阈值。 */
    fun isRateLimited(): Boolean {
        purgeExpired(System.currentTimeMillis())
        return recentAutomationTimestamps.size >= MAX_AUTOMATION_WINDOW_COUNT
    }

    /** 重置限流窗口（每回合/每个会话开始时调用，若需要更严格的按回合限流可调用）。 */
    fun resetWindow() {
        recentAutomationTimestamps.clear()
    }

    // ===== 私有工具 =====

    /** 清理滚动窗口内已过期的时间戳。 */
    private fun purgeExpired(now: Long) {
        while (recentAutomationTimestamps.isNotEmpty()) {
            val oldest = recentAutomationTimestamps.peekFirst() ?: break
            if (now - oldest > WINDOW_MS) {
                recentAutomationTimestamps.pollFirst()
            } else {
                break
            }
        }
    }

    /** 写入安全审计日志（自动处理 DI 解析失败，保证不影响主流程）。 */
    private fun logEvent(action: String, detail: String, status: String) {
        runCatching {
            val repo: me.rerere.rikkahub.data.security.SecurityAuditRepository =
                org.koin.java.KoinJavaComponent.getKoin().get()
            repo.log(
                category = "automation",
                action = action,
                target = "screen_automation",
                detail = detail,
                status = status,
            )
        }.onFailure { e ->
            Log.w(TAG, "Failed to log automation audit: ${e.message}")
        }
    }
}