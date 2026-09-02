/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 参考 Operit KimiProvider
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.ai.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Kimi Provider
 * 
 * 完全对齐 Operit AI KimiProvider
 */
class KimiProvider : Provider<ProviderSetting.OpenAI> {
    
    companion object {
        private const val BASE_URL = "https://api.moonshot.cn/v1"
        private const val TAG = "KimiProvider"
    }
    
    override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<me.rerere.ai.provider.Model> {
        // TODO: 实现模型列表获取
        return emptyList()
    }
    
    override suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk {
        // TODO: 实现文本生成
        return MessageChunk(
            id = java.util.UUID.randomUUID().toString(),
            model = params.model.id.toString(),
            choices = emptyList()
        )
    }
    
    override suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> {
        return flow {
            // TODO: 实现流式文本生成
            emit(MessageChunk(
                id = java.util.UUID.randomUUID().toString(),
                model = params.model.id.toString(),
                choices = emptyList()
            ))
        }
    }
    
    /**
     * 构建请求体
     */
    private fun buildRequestBody(
        apiKey: String,
        model: String,
        messages: List<UIMessage>,
        params: TextGenerationParams,
        stream: Boolean = false
    ): String {
        val json = buildJsonObject {
            put("model", model)
            put("messages", buildMessagesArray(messages))
            if (params.temperature != null) put("temperature", params.temperature)
            if (params.topP != null) put("top_p", params.topP)
            if (params.maxTokens != null) put("max_tokens", params.maxTokens)
            if (stream) put("stream", true)
            if (params.tools.isNotEmpty()) {
                put("tools", buildToolsArray(params.tools))
                put("tool_choice", "auto")
            }
        }
        return json.toString()
    }
    
    /**
     * 构建消息数组
     */
    private fun buildMessagesArray(messages: List<UIMessage>): JSONArray {
        val array = JSONArray()
        for (msg in messages) {
            val jsonObject = JSONObject()
            jsonObject.put("role", msg.role.name.lowercase())
            
            val partsJson = JSONArray()
            for (part in msg.parts) {
                if (part is me.rerere.ai.ui.UIMessagePart.Text) {
                    val partJson = JSONObject()
                    partJson.put("type", "text")
                    partJson.put("text", part.text)
                    partsJson.put(partJson)
                }
            }
            jsonObject.put("content", partsJson)
            array.put(jsonObject)
        }
        return array
    }
    
    /**
     * 构建工具数组
     */
    private fun buildToolsArray(tools: List<Tool>): JSONArray {
        val array = JSONArray()
        for (tool in tools) {
            val toolJson = JSONObject()
            toolJson.put("type", "function")
            val functionJson = JSONObject()
            functionJson.put("name", tool.name)
            functionJson.put("description", tool.description)
            toolJson.put("function", functionJson)
            array.put(toolJson)
        }
        return array
    }
    
    /**
     * 发送请求
     */
    private suspend fun sendRequest(
        apiKey: String,
        url: String,
        body: String
    ): String {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        
        return try {
            client.newCall(request).execute().body?.string() ?: ""
        } catch (e: IOException) {
            throw RuntimeException("Request failed: ${e.message}", e)
        }
    }
}
