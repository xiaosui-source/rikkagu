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
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import kotlin.uuid.Uuid

// 默认模型占位 ID：不内置任何模型，用户添加并选择模型后才生效
// （未选择模型时聊天会引导用户先添加/选择可用模型）
val DEFAULT_NVIDIA_MODEL_ID = Uuid.parse("6d3b9c2e-7a4f-4c8d-b2e1-9e0f1a2b3c4d")

val DEFAULT_PROVIDERS = listOf(
    ProviderSetting.OpenAI(
        id = Uuid.parse("7a3f1c2e-9d4b-4c8a-8f1e-0d5b3a7c92f1"),
        name = "NVIDIA",
        baseUrl = "https://integrate.api.nvidia.com/v1",
        apiKey = "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",
        enabled = true,
        builtIn = true,
        // 默认模型：NVIDIA DeepSeek V4 Flash（开箱即用，其他模型用户自己选）
        models = listOf(
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("6d3b9c2e-7a4f-4c8d-b2e1-9e0f1a2b3c4d"),
                modelId = "deepseek-ai/deepseek-v4-flash-0731",
                displayName = "DeepSeek V4 Flash",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
        ),
        description = {
            Text(
                text = "NVIDIA NIM 平台，提供 Llama、DeepSeek、Nemotron、Qwen 等最新开源大模型的托管推理服务。OpenAI 兼容接口。"
            )
        },
        shortDescription = {
            Text(
                text = "NVIDIA NIM：Llama/DeepSeek/Nemotron 等模型"
            )
        },
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("1eeea727-9ee5-4cae-93e6-6fb01a4d051e"),
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        apiKey = "",
        builtIn = true,
        models = listOf(
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("6a7dc319b6277e51-0000-4000-8000-000000000000"),
                modelId = "gpt-4o",
                displayName = "GPT-4o",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("6859b16ceaa9acb8-0000-4000-8000-000000000001"),
                modelId = "gpt-4o-mini",
                displayName = "GPT-4o mini",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("301bb9c112eba4fa-0000-4000-8000-000000000002"),
                modelId = "gpt-4.1",
                displayName = "GPT-4.1",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("3d31f67e6804ef23-0000-4000-8000-000000000003"),
                modelId = "gpt-4.1-mini",
                displayName = "GPT-4.1 mini",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("5a817f1599aed882-0000-4000-8000-000000000004"),
                modelId = "o3-mini",
                displayName = "o3 mini",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("6c099d64d168a2e1-0000-4000-8000-000000000005"),
                modelId = "gpt-4.1-nano",
                displayName = "GPT-4.1 nano",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
        ),
    ),
    ProviderSetting.Google(
        id = Uuid.parse("6ab18148-c138-4394-a46f-1cd8c8ceaa6d"),
        name = "Gemini",
        apiKey = "",
        enabled = true,
        builtIn = true,
        models = listOf(
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("32b20c746a6f9d1-0000-4000-8000-000000000000"),
                modelId = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("96e8deb6f96e945-0000-4000-8000-000000000001"),
                modelId = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("73ef247ea32cd14a-0000-4000-8000-000000000002"),
                modelId = "gemini-2.0-flash",
                displayName = "Gemini 2.0 Flash",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("6443f9c30ed11d43-0000-4000-8000-000000000003"),
                modelId = "gemini-1.5-pro",
                displayName = "Gemini 1.5 Pro",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("4e22afbc558dccaa-0000-4000-8000-000000000004"),
                modelId = "gemini-2.5-flash-lite",
                displayName = "Gemini 2.5 Flash-Lite",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
        ),
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("56a94d29-c88b-41c5-8e09-38a7612d6cf8"),
        name = "硅基流动",
        baseUrl = "https://api.siliconflow.cn/v1",
        apiKey = "",
        builtIn = true,
        models = listOf(
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("618d38a1c3175fe4-0000-4000-8000-000000000000"),
                modelId = "Qwen/Qwen2.5-72B-Instruct",
                displayName = "Qwen2.5 72B",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("334099de2fe1e6a9-0000-4000-8000-000000000001"),
                modelId = "deepseek-ai/DeepSeek-V3",
                displayName = "DeepSeek V3",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("7b8954bd611bb86e-0000-4000-8000-000000000002"),
                modelId = "deepseek-ai/DeepSeek-R1",
                displayName = "DeepSeek R1",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("4f7e2ababcf16553-0000-4000-8000-000000000003"),
                modelId = "THUDM/glm-4-9b-chat",
                displayName = "GLM-4 9B",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("2bb92698f8b3c19f-0000-4000-8000-000000000004"),
                modelId = "Qwen/Qwen3-235B-A22B",
                displayName = "Qwen3 235B",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
        ),
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("f099ad5b-ef03-446d-8e78-7e36787f780b"),
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        apiKey = "",
        builtIn = true,
        models = listOf(
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("2157f60702acefba-0000-4000-8000-000000000000"),
                modelId = "deepseek-chat",
                displayName = "DeepSeek Chat (V3)",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("63f78979bc192b8a-0000-4000-8000-000000000001"),
                modelId = "deepseek-reasoner",
                displayName = "DeepSeek Reasoner (R1)",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("17a4d1bb8b472f6f-0000-4000-8000-000000000002"),
                modelId = "deepseek-v3.2",
                displayName = "DeepSeek V3.2",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
        ),
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
        models = listOf(
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("27a3823359a3ca99-0000-4000-8000-000000000000"),
                modelId = "qwen-plus",
                displayName = "Qwen Plus",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("70a4934c94154092-0000-4000-8000-000000000001"),
                modelId = "qwen-turbo",
                displayName = "Qwen Turbo",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("2b8f0dae980d0dca-0000-4000-8000-000000000002"),
                modelId = "qwen-max",
                displayName = "Qwen Max",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("426d488517b58b27-0000-4000-8000-000000000003"),
                modelId = "qwen-long",
                displayName = "Qwen Long",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("5414646d94f3af8e-0000-4000-8000-000000000004"),
                modelId = "deepseek-v3",
                displayName = "DeepSeek V3",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
        ),
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("3dfd6f9b-f9d9-417f-80c1-ff8d77184191"),
        name = "火山引擎",
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        apiKey = "",
        enabled = false,
        builtIn = true,
        models = listOf(
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("7114ee98e93e0747-0000-4000-8000-000000000000"),
                modelId = "doubao-1-5-pro-32k-250115",
                displayName = "豆包 1.5 Pro 32K",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("39abc5a0f181f030-0000-4000-8000-000000000001"),
                modelId = "doubao-1-5-lite-32k-250115",
                displayName = "豆包 1.5 Lite 32K",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("493dcac99fc82a78-0000-4000-8000-000000000002"),
                modelId = "deepseek-v3",
                displayName = "DeepSeek V3",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("60db0bb177ecaaf2-0000-4000-8000-000000000003"),
                modelId = "deepseek-r1",
                displayName = "DeepSeek R1",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
        ),
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("3bc40dc1-b11a-46fa-863b-6306971223be"),
        name = "智谱AI开放平台",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        apiKey = "",
        enabled = false,
        builtIn = true,
        models = listOf(
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("76e33fa142a6c15-0000-4000-8000-000000000000"),
                modelId = "glm-4.5",
                displayName = "GLM-4.5",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("7ea5b4403e0fc3f9-0000-4000-8000-000000000001"),
                modelId = "glm-4-plus",
                displayName = "GLM-4 Plus",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("7726af8f7d59bf0d-0000-4000-8000-000000000002"),
                modelId = "glm-4-air",
                displayName = "GLM-4 Air",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("5276eb61280d0bc7-0000-4000-8000-000000000003"),
                modelId = "glm-4-flash",
                displayName = "GLM-4 Flash",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("1c952a9e14d61191-0000-4000-8000-000000000004"),
                modelId = "glm-4-long",
                displayName = "GLM-4 Long",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
        ),
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("ef5d149b-8e34-404b-818c-6ec242e5c3c5"),
        name = "腾讯Hunyuan",
        baseUrl = "https://api.hunyuan.cloud.tencent.com/v1",
        apiKey = "",
        enabled = false,
        builtIn = true,
        models = listOf(
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("7ca1abd2fc3b9600-0000-4000-8000-000000000000"),
                modelId = "hunyuan-turbo",
                displayName = "混元 Turbo",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("6a121544e2c6b138-0000-4000-8000-000000000001"),
                modelId = "hunyuan-pro",
                displayName = "混元 Pro",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("ebeb2a6d5a539ce-0000-4000-8000-000000000002"),
                modelId = "hunyuan-standard",
                displayName = "混元 Standard",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("158e44ed0dc2cec9-0000-4000-8000-000000000003"),
                modelId = "hunyuan-lite",
                displayName = "混元 Lite",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("1a467b1e152a1b2f-0000-4000-8000-000000000004"),
                modelId = "hunyuan-t1",
                displayName = "混元 T1",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
        ),
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("a1b2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d"),
        name = "讯飞星火",
        baseUrl = "https://spark-api-open.xf-yun.com/v1",
        apiKey = "",
        enabled = false,
        builtIn = true,
        models = listOf(
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("2a0bf83f0697b3ce-0000-4000-8000-000000000000"),
                modelId = "generalv4.0",
                displayName = "星火 4.0",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("5e2f30e1e50fe60a-0000-4000-8000-000000000001"),
                modelId = "generalv3.5",
                displayName = "星火 3.5",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("2d4de4bd56421f0d-0000-4000-8000-000000000002"),
                modelId = "xdeepseekv3",
                displayName = "DeepSeek V3",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("7422d2fe57c053f0-0000-4000-8000-000000000003"),
                modelId = "xdeepseekr1",
                displayName = "DeepSeek R1",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("5fb04f483e53cc16-0000-4000-8000-000000000004"),
                modelId = "general",
                displayName = "星火通用",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
        ),
        // 不内置任何模型：模型全部由用户自己添加（API 拉取或手动添加）
    ),
)