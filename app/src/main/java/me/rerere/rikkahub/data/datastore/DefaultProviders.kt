/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.datastore

import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.providers.local.LocalLLMProvider
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import kotlin.uuid.Uuid

// 默认模型占位 ID：不内置任何模型，用户添加并选择模型后才生效
// （未选择模型时聊天会引导用户先添加/选择可用模型）
val DEFAULT_NVIDIA_MODEL_ID = Uuid.parse("6d3b9c2e-7a4f-4c8d-b2e1-9e0f1a2b3c4d")

val DEFAULT_PROVIDERS = listOf(
    ProviderSetting.OpenAI(
        id = Uuid.parse("7a3f1c2e-9d4b-4c8a-8f1e-0d5b3a7c92f1"),
        name = "喵喵喵",
        baseUrl = "https://api.gemai.cc/v1",
        apiKey = "sk-u0UXZ3XdnrqzYSUoWcJZ94EDDGxXYTwbRBQ18AQK7uGw6ZdX",
        enabled = true,
        builtIn = true,
        // 默认模型：DeepSeek V4 Flash（开箱即用）
        models = listOf(
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("6d3b9c2e-7a4f-4c8d-b2e1-9e0f1a2b3c4d"),
                modelId = "deepseek-v4-flash",
                displayName = "DeepSeek V4 Flash",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
        ),
        description = {
            Text(
                text = "喵喵喵：DeepSeek V4 Flash 等最新大模型，OpenAI 兼容接口。"
            )
        },
        shortDescription = {
            Text(
                text = "喵喵喵：DeepSeek V4 Flash"
            )
        },
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("1eeea727-9ee5-4cae-93e6-6fb01a4d051e"),
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        apiKey = "",
        builtIn = true,
        models = emptyList(),
    ),
    ProviderSetting.Google(
        id = Uuid.parse("6ab18148-c138-4394-a46f-1cd8c8ceaa6d"),
        name = "Gemini",
        apiKey = "",
        enabled = true,
        builtIn = true,
        models = emptyList(),
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("56a94d29-c88b-41c5-8e09-38a7612d6cf8"),
        name = "硅基流动",
        baseUrl = "https://api.siliconflow.cn/v1",
        apiKey = "",
        builtIn = true,
        models = emptyList(),
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("f099ad5b-ef03-446d-8e78-7e36787f780b"),
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        apiKey = "",
        builtIn = true,
        models = emptyList(),
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/user/balance",
            resultPath = "balance_infos[0].total_balance"
        )
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("f76cae46-069a-4334-ab8e-224e4979e58c"),
        name = "阿里云百炼",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        apiKey = "",
        enabled = false,
        builtIn = true,
        models = emptyList(),
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("3dfd6f9b-f9d9-417f-80c1-ff8d77184191"),
        name = "火山引擎",
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        apiKey = "",
        enabled = false,
        builtIn = true,
        models = emptyList(),
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("3bc40dc1-b11a-46fa-863b-6306971223be"),
        name = "智谱AI开放平台",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        apiKey = "",
        enabled = false,
        builtIn = true,
        models = emptyList(),
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("ef5d149b-8e34-404b-818c-6ec242e5c3c5"),
        name = "腾讯Hunyuan",
        baseUrl = "https://api.hunyuan.cloud.tencent.com/v1",
        apiKey = "",
        enabled = false,
        builtIn = true,
        models = emptyList(),
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("a1b2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d"),
        name = "讯飞星火",
        baseUrl = "https://spark-api-open.xf-yun.com/v1",
        apiKey = "",
        enabled = false,
        builtIn = true,
        models = emptyList(),
        // 不内置任何模型：模型全部由用户自己添加（API 拉取或手动添加）
    ),
    // 本地模型支持（llama.cpp GGUF / MNN / ONNX）
    ProviderSetting.LocalLLM(
        id = Uuid.parse("9c8b7a6f-5e4d-3c2b-1a0f-9e8d7c6b5a4f"),
        name = "本地模型 (llama.cpp)",
        engineType = ProviderSetting.LocalLLM.EngineType.LLAMA,
        enabled = false,
        builtIn = true,
        models = LocalLLMProvider.DEFAULT_MODELS,
        description = {
            Text(
                text = "本地运行大语言模型，无需联网。支持 llama.cpp (GGUF 格式)、MNN、ONNX Runtime。可在设置中指定模型文件路径。"
            )
        },
        shortDescription = {
            Text(
                text = "本地 GGUF 模型推理"
            )
        },
    ),
)
