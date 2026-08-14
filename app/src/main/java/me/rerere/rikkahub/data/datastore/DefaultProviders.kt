/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
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

val DEFAULT_NVIDIA_MODEL_ID = Uuid.parse("6d3b9c2e-7a4f-4c8d-b2e1-9e0f1a2b3c4d")

val DEFAULT_PROVIDERS = listOf(
    ProviderSetting.OpenAI(
        id = Uuid.parse("7a3f1c2e-9d4b-4c8a-8f1e-0d5b3a7c92f1"),
        name = "NVIDIA",
        baseUrl = "https://integrate.api.nvidia.com/v1",
        apiKey = "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",
        enabled = true,
        builtIn = true,
        models = listOf(
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("6d3b9c2e-7a4f-4c8d-b2e1-9e0f1a2b3c4d"),
            modelId = "deepseek-ai/deepseek-r1",
            displayName = "DeepSeek R1",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000002"),
            modelId = "deepseek-ai/deepseek-v3",
            displayName = "DeepSeek V3",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000003"),
            modelId = "qwen/qwen-2.5-72b-instruct",
            displayName = "Qwen 2.5 72B",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000004"),
            modelId = "meta/llama-3.3-70b-instruct",
            displayName = "Llama 3.3 70B",
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
        builtIn = true
        models = listOf(
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000011"),
            modelId = "gpt-4o",
            displayName = "GPT-4o",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000012"),
            modelId = "gpt-4o-mini",
            displayName = "GPT-4o mini",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000013"),
            modelId = "gpt-4-turbo",
            displayName = "GPT-4 Turbo",
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
        builtIn = true
        models = listOf(
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000021"),
            modelId = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000022"),
            modelId = "gemini-1.5-pro",
            displayName = "Gemini 1.5 Pro",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000023"),
            modelId = "gemini-1.5-flash",
            displayName = "Gemini 1.5 Flash",
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
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000031"),
            modelId = "Qwen/Qwen2.5-72B-Instruct",
            displayName = "Qwen2.5 72B",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000032"),
            modelId = "deepseek-ai/DeepSeek-V3",
            displayName = "DeepSeek V3",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000033"),
            modelId = "THUDM/glm-4-9b-chat",
            displayName = "GLM-4 9B",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        ),
        description = {
            MarkdownBlock(
                content = """
                    ${stringResource(R.string.silicon_flow_description)}
                    ${stringResource(R.string.silicon_flow_website)}
                """.trimIndent()
            )
        },
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/user/info",
            resultPath = "data.totalBalance",
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
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000041"),
            modelId = "deepseek-chat",
            displayName = "DeepSeek Chat",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000042"),
            modelId = "deepseek-reasoner",
            displayName = "DeepSeek Reasoner",
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
        builtIn = true
        models = listOf(
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000051"),
            modelId = "qwen-plus",
            displayName = "通义千问 Plus",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000052"),
            modelId = "qwen-max",
            displayName = "通义千问 Max",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000053"),
            modelId = "qwen-turbo",
            displayName = "通义千问 Turbo",
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
        builtIn = true
        models = listOf(
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000061"),
            modelId = "doubao-pro-32k",
            displayName = "豆包 Pro 32K",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000062"),
            modelId = "doubao-lite-32k",
            displayName = "豆包 Lite 32K",
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
        builtIn = true
        models = listOf(
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000071"),
            modelId = "glm-4-plus",
            displayName = "GLM-4 Plus",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000072"),
            modelId = "glm-4-flash",
            displayName = "GLM-4 Flash",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000073"),
            modelId = "glm-4-air",
            displayName = "GLM-4 Air",
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
        builtIn = true
        models = listOf(
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000081"),
            modelId = "hunyuan-turbo",
            displayName = "混元 Turbo",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000082"),
            modelId = "hunyuan-pro",
            displayName = "混元 Pro",
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
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000091"),
            modelId = "spark-lite",
            displayName = "星火 Lite",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000092"),
            modelId = "spark-pro",
            displayName = "星火 Pro",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000093"),
            modelId = "spark-max",
            displayName = "星火 Max",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        me.rerere.ai.provider.Model(
            id = kotlin.uuid.Uuid.parse("00000000-0000-0000-6000-000000000094"),
            modelId = "spark-4.0-ultra",
            displayName = "星火 4.0 Ultra",
            inputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
            abilities = listOf(me.rerere.ai.provider.ModelAbility.TOOL, me.rerere.ai.provider.ModelAbility.REASONING),
        ),
        ),
        // 内置模型：星火 Lite/Pro/Max/4.0 Ultra（Lite 为轻量模型，自动走提示词式工具调用）
    ),
)