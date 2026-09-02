/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 参考 Operit TokenCacheManager
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.ai.util

import android.util.Log

/**
 * Token缓存管理器，用于优化重复对话历史的token计算
 * 通过缓存之前计算过的对话历史token数量，避免重复计算相同的内容
 * 
 * 完全对齐 Operit AI TokenCacheManager
 */
class TokenCacheManager {
    private val TAG = "TokenCacheManager"
    
    // 上一次的聊天历史
    private var previousChatHistory: List<Pair<String, String>> = emptyList()
    // 对应于previousChatHistory的token数量
    private var previousHistoryTokenCount = 0L
    
    // 缓存的输入token数量（对应于previousChatHistory的公共前缀）
    private var _cachedInputTokenCount = 0L
    
    // 当前请求的新增token数量
    private var _currentInputTokenCount = 0L
    
    // 当前输出token数量
    private var _outputTokenCount = 0L
    
    /**
     * 获取缓存的输入token数量
     */
    val cachedInputTokenCount: Long
        get() = _cachedInputTokenCount
    
    /**
     * 获取当前请求的输入token数量（不包括缓存）
     */
    val currentInputTokenCount: Long
        get() = _currentInputTokenCount
    
    /**
     * 获取总输入token数量（缓存 + 当前）
     */
    val totalInputTokenCount: Long
        get() = _cachedInputTokenCount + _currentInputTokenCount
    
    /**
     * 获取输出token数量
     */
    val outputTokenCount: Long
        get() = _outputTokenCount
    
    /**
     * 重置所有token计数和缓存
     */
    fun resetTokenCounts() {
        previousChatHistory = emptyList()
        previousHistoryTokenCount = 0L
        _cachedInputTokenCount = 0L
        _currentInputTokenCount = 0L
        _outputTokenCount = 0L
    }
    
    /**
     * 增加输出token数量
     */
    fun addOutputTokens(tokens: Long) {
        _outputTokenCount += tokens
    }

    /**
     * 使用API返回的实际输出token数量覆盖当前估算值
     */
    fun setOutputTokens(tokens: Long) {
        _outputTokenCount = tokens.coerceAtLeast(0L)
    }
    
    /**
     * 使用API返回的实际token数据更新计数
     * 用于Gemini等支持服务端缓存统计的API
     * 
     * @param actualInput 实际的输入token数量（不包括缓存）
     * @param cachedInput 缓存命中的token数量
     */
    fun updateActualTokens(actualInput: Long, cachedInput: Long) {
        _currentInputTokenCount = actualInput.coerceAtLeast(0L)
        _cachedInputTokenCount = cachedInput.coerceAtLeast(0L)
    }
    
    /**
     * 计算输入token数量，利用缓存优化重复计算
     * 
     * @param chatHistory 完整的聊天历史（必须已包含本次最新输入）
     * @param toolsJson 工具定义的JSON字符串（可选）
     * @return 总的输入token数量
     */
    fun calculateInputTokens(
        chatHistory: List<Pair<String, String>>,
        toolsJson: String? = null,
        updateState: Boolean = true
    ): Long {
        // 构建包含工具定义的历史记录列表
        val historyWithTools = if (!toolsJson.isNullOrEmpty()) {
            val mutableHistory = chatHistory.toMutableList()
            val systemIndex = mutableHistory.indexOfFirst { it.first == "system" }
            
            if (systemIndex != -1) {
                val originalSystem = mutableHistory[systemIndex]
                mutableHistory[systemIndex] = originalSystem.copy(second = toolsJson + "\n" + originalSystem.second)
            } else {
                mutableHistory.add(0, "system" to toolsJson)
            }
            mutableHistory.toList()
        } else {
            chatHistory
        }

        // 找到与之前历史的公共前缀长度
        val commonPrefixLength = findCommonPrefixLength(historyWithTools, previousChatHistory)
        
        Log.d(TAG, "聊天历史比较: 当前=${historyWithTools.size}, 之前=${previousChatHistory.size}, 公共前缀=${commonPrefixLength}")
        
        val cachedTokens: Long
        val newTokens: Long

        if (commonPrefixLength > 0) {
            cachedTokens = if (commonPrefixLength == previousChatHistory.size) {
                previousHistoryTokenCount
            } else {
                val commonPrefix = historyWithTools.take(commonPrefixLength)
                calculateTokensForHistory(commonPrefix)
            }
            
            val newPart = historyWithTools.drop(commonPrefixLength)
            newTokens = calculateTokensForHistory(newPart)
        } else {
            val historyTokens = calculateTokensForHistory(historyWithTools)
            cachedTokens = 0L
            newTokens = historyTokens
        }

        if (updateState) {
            _cachedInputTokenCount = cachedTokens
            _currentInputTokenCount = newTokens
            previousHistoryTokenCount = cachedTokens + newTokens

            if (chatHistory.isNotEmpty()) {
                previousChatHistory = historyWithTools
            }

            if (cachedTokens > 0) {
                Log.d(TAG, "使用token缓存: 缓存=${_cachedInputTokenCount}, 新增=${_currentInputTokenCount}")
            } else {
                Log.d(TAG, "重新计算所有tokens: ${_currentInputTokenCount}")
            }
        } else {
            if (cachedTokens > 0) {
                Log.d(TAG, "只读预估token缓存: 缓存=$cachedTokens, 新增=$newTokens")
            } else {
                Log.d(TAG, "只读预估所有tokens: $newTokens")
            }
        }

        return cachedTokens + newTokens
    }
    
    /**
     * 找到两个聊天历史列表的公共前缀长度
     */
    private fun findCommonPrefixLength(
        current: List<Pair<String, String>>,
        previous: List<Pair<String, String>>
    ): Int {
        val minLength = minOf(current.size, previous.size)
        var commonLength = 0
        
        for (i in 0 until minLength) {
            if (current[i] == previous[i]) {
                commonLength++
            } else {
                break
            }
        }
        
        return commonLength
    }
    
    /**
     * 计算聊天历史的token数量
     */
    private fun calculateTokensForHistory(history: List<Pair<String, String>>): Long {
        return history.fold(0L) { acc, (_, content) ->
            acc + ChatUtils.estimateTokenCount(content)
        }
    }
}
