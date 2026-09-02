/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 参考 Operit TokenTrackingAIService
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.ai.util

/**
 * Token追踪服务，用于监控token使用情况
 * 
 * 完全对齐 Operit AI TokenTrackingAIService
 */
class TokenTrackingService {
    private val tokenCacheManager = TokenCacheManager()
    
    // 累计输入token数
    private var totalInputTokens = 0L
    
    // 累计输出token数
    private var totalOutputTokens = 0L
    
    // 本次请求的输入token数
    private var currentRequestInputTokens = 0L
    
    // 本次请求的输出token数
    private var currentRequestOutputTokens = 0L
    
    /**
     * 获取缓存的输入token数
     */
    fun getCachedInputTokens(): Long = tokenCacheManager.cachedInputTokenCount
    
    /**
     * 获取当前请求的输入token数
     */
    fun getCurrentRequestInputTokens(): Long = tokenCacheManager.currentInputTokenCount
    
    /**
     * 获取总输入token数
     */
    fun getTotalInputTokens(): Long = tokenCacheManager.totalInputTokenCount
    
    /**
     * 获取总输出token数
     */
    fun getTotalOutputTokens(): Long = totalOutputTokens
    
    /**
     * 计算并更新token计数
     */
    fun calculateAndTrackTokens(
        chatHistory: List<Pair<String, String>>,
        toolsJson: String? = null
    ): Long {
        val tokens = tokenCacheManager.calculateInputTokens(chatHistory, toolsJson)
        totalInputTokens += tokens
        return tokens
    }
    
    /**
     * 设置输出token数
     */
    fun setOutputTokens(tokens: Long) {
        totalOutputTokens += tokens
        tokenCacheManager.setOutputTokens(tokens)
    }
    
    /**
     * 重置token计数
     */
    fun reset() {
        tokenCacheManager.resetTokenCounts()
        totalInputTokens = 0L
        totalOutputTokens = 0L
    }
    
    /**
     * 获取token使用报告
     */
    fun getTokenUsageReport(): String {
        return """
            Token使用报告:
            - 累计输入: $totalInputTokens tokens
            - 累计输出: $totalOutputTokens tokens
            - 总使用: ${totalInputTokens + totalOutputTokens} tokens
        """.trimIndent()
    }
}

// 全局单例
val tokenTrackingService = TokenTrackingService()
