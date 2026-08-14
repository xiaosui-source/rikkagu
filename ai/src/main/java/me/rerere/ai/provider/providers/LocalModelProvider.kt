/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.ai.provider.providers

import ai.onnxruntime.genai.Generator
import ai.onnxruntime.genai.GeneratorParams
import ai.onnxruntime.genai.Model
import ai.onnxruntime.genai.Sequences
import ai.onnxruntime.genai.Tokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.io.File

/**
 * 本地模型提供商：使用 onnxruntime-genai 在设备本地推理 ONNX LLM。
 * 不依赖 Ollama / 外部服务 / 云端 / API Key / 工作区。
 * 模型文件放 files/models/{modelDir}/ 下（.onnx + tokenizer.json 等）。
 */
class LocalModelProvider : Provider<ProviderSetting.LocalModel> {

    override suspend fun listModels(providerSetting: ProviderSetting.LocalModel): List<me.rerere.ai.provider.Model> {
        // 模型目录下每个子目录视为一个模型
        val dir = File(providerSetting.modelDir)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name }
            ?.map { me.rerere.ai.provider.Model(modelId = it.name, displayName = it.name) }
            ?: emptyList()
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.LocalModel,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): MessageChunk = withContext(Dispatchers.Default) {
        val prompt = buildPrompt(messages)
        val output = localGenerate(providerSetting.modelDir, prompt)
        MessageChunk(
            choices = listOf(
                me.rerere.ai.ui.MessageChoice(
                    message = UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text(output)),
                        modelId = params.model.id,
                    ),
                    finishReason = "stop",
                )
            ),
        )
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.LocalModel,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<MessageChunk> = flow {
        val prompt = buildPrompt(messages)
        val output = localGenerate(providerSetting.modelDir, prompt)
        // 按字符块流式输出
        var chunk = ""
        output.forEach { ch ->
            chunk += ch
            if (chunk.length >= 8 || ch == '\n') {
                emit(
                    MessageChunk(
                        choices = listOf(
                            me.rerere.ai.ui.MessageChoice(
                                message = UIMessage(
                                    role = MessageRole.ASSISTANT,
                                    parts = listOf(UIMessagePart.Text(chunk)),
                                    modelId = params.model.id,
                                ),
                                finishReason = null,
                            )
                        )
                    )
                )
                chunk = ""
            }
        }
        if (chunk.isNotBlank()) {
            emit(
                MessageChunk(
                    choices = listOf(
                        me.rerere.ai.ui.MessageChoice(
                            message = UIMessage(
                                role = MessageRole.ASSISTANT,
                                parts = listOf(UIMessagePart.Text(chunk)),
                                modelId = params.model.id,
                            ),
                            finishReason = "stop",
                        )
                    )
                )
            )
        }
    }

    /** 本地 ONNX LLM 生成（onnxruntime-genai） */
    private fun localGenerate(modelDir: String, prompt: String): String {
        val dir = File(modelDir)
        if (!dir.exists() || !dir.isDirectory) {
            return "[本地模型不可用：模型目录不存在 $modelDir]"
        }
        return try {
            val model = Model(dir.absolutePath)
            try {
                val tokenizer = Tokenizer(model)
                val params = GeneratorParams(model)
                params.setSearchOption("max_length", 4096L)
                val inputIds = tokenizer.encode(prompt)
                params.inputSequences = Sequences(inputIds)
                val generator = Generator(model, params)
                try {
                    val output = StringBuilder()
                    while (!generator.isDone) {
                        generator.computeLogits()
                        val token = generator.generateNextToken()
                        output.append(tokenizer.decode(token))
                    }
                    output.toString().trim()
                } finally {
                    generator.close()
                }
            } finally {
                model.close()
            }
        } catch (e: Exception) {
            "[本地模型推理失败: ${e.message}]"
        }
    }

    /** 简单 prompt 拼接（对话 → 提示词） */
    private fun buildPrompt(messages: List<UIMessage>): String = buildString {
        messages.forEach { msg ->
            val role = when (msg.role) {
                MessageRole.SYSTEM -> "system"
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                else -> "user"
            }
            val text = msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString(" ") { it.text }
            if (text.isNotBlank()) append("<$role>$text</$role>\n")
        }
        append("<assistant>")
    }
}
