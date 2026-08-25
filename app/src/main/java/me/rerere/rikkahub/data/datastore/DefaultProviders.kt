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
                id = kotlin.uuid.Uuid.parse("00000001-0000-4000-8000-000000000001"),
                modelId = "gpt-4o",
                displayName = "GPT-4o",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000002-0000-4000-8000-000000000002"),
                modelId = "gpt-4o-mini",
                displayName = "GPT-4o mini",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000003-0000-4000-8000-000000000003"),
                modelId = "gpt-4.1",
                displayName = "GPT-4.1",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000004-0000-4000-8000-000000000004"),
                modelId = "gpt-4.1-mini",
                displayName = "GPT-4.1 mini",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000005-0000-4000-8000-000000000005"),
                modelId = "o3-mini",
                displayName = "o3 mini",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000006-0000-4000-8000-000000000006"),
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
                id = kotlin.uuid.Uuid.parse("00000007-0000-4000-8000-000000000007"),
                modelId = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000008-0000-4000-8000-000000000008"),
                modelId = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000009-0000-4000-8000-000000000009"),
                modelId = "gemini-2.0-flash",
                displayName = "Gemini 2.0 Flash",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("0000000a-0000-4000-8000-00000000000a"),
                modelId = "gemini-1.5-pro",
                displayName = "Gemini 1.5 Pro",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("0000000b-0000-4000-8000-00000000000b"),
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
                id = kotlin.uuid.Uuid.parse("0000000c-0000-4000-8000-00000000000c"),
                modelId = "Qwen/Qwen2.5-72B-Instruct",
                displayName = "Qwen2.5 72B",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("0000000d-0000-4000-8000-00000000000d"),
                modelId = "deepseek-ai/DeepSeek-V3",
                displayName = "DeepSeek V3",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("0000000e-0000-4000-8000-00000000000e"),
                modelId = "deepseek-ai/DeepSeek-R1",
                displayName = "DeepSeek R1",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("0000000f-0000-4000-8000-00000000000f"),
                modelId = "THUDM/glm-4-9b-chat",
                displayName = "GLM-4 9B",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000010-0000-4000-8000-000000000010"),
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
                id = kotlin.uuid.Uuid.parse("00000011-0000-4000-8000-000000000011"),
                modelId = "deepseek-chat",
                displayName = "DeepSeek Chat (V3)",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000012-0000-4000-8000-000000000012"),
                modelId = "deepseek-reasoner",
                displayName = "DeepSeek Reasoner (R1)",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000013-0000-4000-8000-000000000013"),
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
                id = kotlin.uuid.Uuid.parse("00000014-0000-4000-8000-000000000014"),
                modelId = "qwen-plus",
                displayName = "Qwen Plus",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000015-0000-4000-8000-000000000015"),
                modelId = "qwen-turbo",
                displayName = "Qwen Turbo",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000016-0000-4000-8000-000000000016"),
                modelId = "qwen-max",
                displayName = "Qwen Max",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000017-0000-4000-8000-000000000017"),
                modelId = "qwen-long",
                displayName = "Qwen Long",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000018-0000-4000-8000-000000000018"),
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
                id = kotlin.uuid.Uuid.parse("00000019-0000-4000-8000-000000000019"),
                modelId = "doubao-1-5-pro-32k-250115",
                displayName = "豆包 1.5 Pro 32K",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("0000001a-0000-4000-8000-00000000001a"),
                modelId = "doubao-1-5-lite-32k-250115",
                displayName = "豆包 1.5 Lite 32K",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("0000001b-0000-4000-8000-00000000001b"),
                modelId = "deepseek-v3",
                displayName = "DeepSeek V3",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("0000001c-0000-4000-8000-00000000001c"),
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
                id = kotlin.uuid.Uuid.parse("0000001d-0000-4000-8000-00000000001d"),
                modelId = "glm-4.5",
                displayName = "GLM-4.5",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("0000001e-0000-4000-8000-00000000001e"),
                modelId = "glm-4-plus",
                displayName = "GLM-4 Plus",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("0000001f-0000-4000-8000-00000000001f"),
                modelId = "glm-4-air",
                displayName = "GLM-4 Air",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000020-0000-4000-8000-000000000020"),
                modelId = "glm-4-flash",
                displayName = "GLM-4 Flash",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000021-0000-4000-8000-000000000021"),
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
                id = kotlin.uuid.Uuid.parse("00000022-0000-4000-8000-000000000022"),
                modelId = "hunyuan-turbo",
                displayName = "混元 Turbo",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000023-0000-4000-8000-000000000023"),
                modelId = "hunyuan-pro",
                displayName = "混元 Pro",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000024-0000-4000-8000-000000000024"),
                modelId = "hunyuan-standard",
                displayName = "混元 Standard",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000025-0000-4000-8000-000000000025"),
                modelId = "hunyuan-lite",
                displayName = "混元 Lite",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000026-0000-4000-8000-000000000026"),
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
                id = kotlin.uuid.Uuid.parse("00000027-0000-4000-8000-000000000027"),
                modelId = "generalv4.0",
                displayName = "星火 4.0",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000028-0000-4000-8000-000000000028"),
                modelId = "generalv3.5",
                displayName = "星火 3.5",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("00000029-0000-4000-8000-000000000029"),
                modelId = "xdeepseekv3",
                displayName = "DeepSeek V3",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("0000002a-0000-4000-8000-00000000002a"),
                modelId = "xdeepseekr1",
                displayName = "DeepSeek R1",
                inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
            ),
            me.rerere.ai.provider.Model(
                id = kotlin.uuid.Uuid.parse("0000002b-0000-4000-8000-00000000002b"),
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