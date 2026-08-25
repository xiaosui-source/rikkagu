/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.datastore

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import me.rerere.rikkahub.data.ai.tools.LocalToolOption

import android.content.Context
import android.util.Log
import androidx.datastore.core.IOException
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV1Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV2Migration
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV3Migration
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.LEARNING_MODE_PROMPT
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.ExternalMemory
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.MiniApp
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.data.model.Tag
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.ui.theme.CustomTheme
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.toMutableStateFlow
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.uuid.Uuid
/** 安全反序列化：失败返回默认值（类型从 default 参数推断） */
private inline fun <reified T> String?.decodeOrNull(default: T): T =
    this?.let { runCatching { JsonInstant.decodeFromString<T>(it) }.getOrNull() } ?: default


private const val TAG = "PreferencesStore"

/**
 * 微信 Bot 设置解码: 优先读多 bot 列表 (JSON 数组), 不存在则兼容旧版单 bot (JSON 对象).
 * 每个 bot 保证唯一 id.
 */
private fun decodeWechatBotSettings(listJson: String?, oldSingleJson: String?): List<WechatBotSetting> {
    val raw = listJson ?: oldSingleJson ?: return emptyList()
    val list = try {
        JsonInstant.decodeFromString<List<WechatBotSetting>>(raw)
    } catch (_: Exception) {
        try {
            listOf(JsonInstant.decodeFromString<WechatBotSetting>(raw))
        } catch (_: Exception) {
            emptyList()
        }
    }
    return list.map { bot ->
        if (bot.id.isBlank()) bot.copy(id = Uuid.random().toString()) else bot
    }
}


private val Context.settingsStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            PreferenceStoreV1Migration(),
            PreferenceStoreV2Migration(),
            PreferenceStoreV3Migration()
        )
    }
)

class SettingsStore(
    context: Context,
    scope: AppScope,
) : KoinComponent {
    companion object {
        // 版本号
        val VERSION = intPreferencesKey("data_version")

        // UI设置
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_ID = stringPreferencesKey("theme_id")
        val CUSTOM_THEMES = stringPreferencesKey("custom_themes")
        val DISPLAY_SETTING = stringPreferencesKey("display_setting")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")

        // 免责声明与用户协议
        val DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
        val DISCLAIMER_ACCEPTED_AT = intPreferencesKey("disclaimer_accepted_at")

        // 模型选择
        val ENABLE_WEB_SEARCH = booleanPreferencesKey("enable_web_search")
        val FAVORITE_MODELS = stringPreferencesKey("favorite_models")
        val SELECT_MODEL = stringPreferencesKey("chat_model")
        val TITLE_MODEL = stringPreferencesKey("title_model")
        val TRANSLATE_MODEL = stringPreferencesKey("translate_model")
        val SUGGESTION_MODEL = stringPreferencesKey("suggestion_model")
        val IMAGE_GENERATION_MODEL = stringPreferencesKey("image_generation_model")
        val TITLE_PROMPT = stringPreferencesKey("title_prompt")
        val TRANSLATION_PROMPT = stringPreferencesKey("translation_prompt")
        val TRANSLATE_THINKING_BUDGET = intPreferencesKey("translate_thinking_budget")
        val SUGGESTION_PROMPT = stringPreferencesKey("suggestion_prompt")
        val OCR_MODEL = stringPreferencesKey("ocr_model")
        val OCR_PROMPT = stringPreferencesKey("ocr_prompt")
        val COMPRESS_MODEL = stringPreferencesKey("compress_model")
        val COMPRESS_PROMPT = stringPreferencesKey("compress_prompt")

        // 提供商
        val PROVIDERS = stringPreferencesKey("providers")

        // 助手
        val SELECT_ASSISTANT = stringPreferencesKey("select_assistant")
        val ASSISTANTS = stringPreferencesKey("assistants")
        val ASSISTANT_TAGS = stringPreferencesKey("assistant_tags")

        // 搜索
        val SEARCH_SERVICES = stringPreferencesKey("search_services")
        val SEARCH_COMMON = stringPreferencesKey("search_common")
        val SEARCH_SELECTED = intPreferencesKey("search_selected")

        // 消息桥 (Message Bridge)
        val PENDING_MESSAGES = stringPreferencesKey("pending_messages")
        val POLLING_TASKS = stringPreferencesKey("polling_tasks")

        // MCP
        val MCP_SERVERS = stringPreferencesKey("mcp_servers")

        // WebDAV
        val WEBDAV_CONFIG = stringPreferencesKey("webdav_config")

        // S3
        val S3_CONFIG = stringPreferencesKey("s3_config")

        // TTS
        val TTS_PROVIDERS = stringPreferencesKey("tts_providers")
        val SELECTED_TTS_PROVIDER = stringPreferencesKey("selected_tts_provider")

        // ASR
        val ASR_PROVIDERS = stringPreferencesKey("asr_providers")
        val SELECTED_ASR_PROVIDER = stringPreferencesKey("selected_asr_provider")

        // 知识库（RAG 文档检索）
        val KNOWLEDGE_DOCS = stringPreferencesKey("knowledge_docs")

        // 待办事项（#1113）
        val TODOS = stringPreferencesKey("todos")

        // 内置 GitHub MCP

        // 提示词注入
        val MODE_INJECTIONS = stringPreferencesKey("mode_injections")
        val LOREBOOKS = stringPreferencesKey("lorebooks")
        val QUICK_MESSAGES = stringPreferencesKey("quick_messages")

        // 备份提醒
        val BACKUP_REMINDER_CONFIG = stringPreferencesKey("backup_reminder_config")

        // 统计
        val LAUNCH_COUNT = intPreferencesKey("launch_count")

        // 赞助提醒

        // 系统工具设置
        val SYSTEM_TOOLS_SETTING = stringPreferencesKey("system_tools_setting")
        val LOCAL_WORKSPACE_URI = stringPreferencesKey("local_workspace_uri")


        // 保活服务设置
        val KEEP_ALIVE_ENABLED = booleanPreferencesKey("keep_alive_enabled")
        val OFFLINE_OCR_ENABLED = booleanPreferencesKey("offline_ocr_enabled")

        // 外部记忆库
        val EXTERNAL_MEMORIES = stringPreferencesKey("external_memories")

        // 微信 Bot (iLink 协议)
        val WECHAT_BOT_SETTING = stringPreferencesKey("wechat_bot_setting")
        // 多 Bot: 一个 JSON 数组, 支持添加多个微信机器人
        val WECHAT_BOT_SETTINGS = stringPreferencesKey("wechat_bot_settings")

        // QQ Bot (API v2, WebSocket 网关)
        // Mini App
        val MINI_APPS = stringPreferencesKey("mini_apps")

        // 强制确认所有工具调用
        val FORCE_CONFIRM_TOOL_CALLS = booleanPreferencesKey("force_confirm_tool_calls")

        // 后台触发工作流时拦截敏感工具（needsApproval=true）
        val WORKFLOW_HEADLESS_BLOCK_SENSITIVE = booleanPreferencesKey("workflow_headless_block_sensitive")

        // 自动批准所有工具调用（懒人模式）
        val AUTO_APPROVE_ALL_TOOLS = booleanPreferencesKey("auto_approve_all_tools")
    }

    internal val dataStore = context.settingsStore

    // 用于检测 assistants 列表是否真正变化，避免无关设置写入触发 Pebble 模板缓存清空
    @Volatile
    private var lastAssistantsForCacheInvalidation: List<Assistant>? = null

    val settingsFlowRaw = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            runCatching {
            Settings(
                disclaimerAccepted = preferences[DISCLAIMER_ACCEPTED] == true,
                disclaimerAcceptedAt = preferences[DISCLAIMER_ACCEPTED_AT] ?: 0,
                enableWebSearch = preferences[ENABLE_WEB_SEARCH] == true,
                favoriteModels = preferences[FAVORITE_MODELS].decodeOrNull(emptyList()),
                chatModelId = preferences[SELECT_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_NVIDIA_MODEL_ID,
                titleModelId = preferences[TITLE_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_NVIDIA_MODEL_ID,
                translateModeId = preferences[TRANSLATE_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_NVIDIA_MODEL_ID,
                suggestionModelId = preferences[SUGGESTION_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_NVIDIA_MODEL_ID,
                imageGenerationModelId = preferences[IMAGE_GENERATION_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                titlePrompt = preferences[TITLE_PROMPT] ?: DEFAULT_TITLE_PROMPT,
                translatePrompt = preferences[TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
                translateThinkingBudget = preferences[TRANSLATE_THINKING_BUDGET] ?: 0,
                suggestionPrompt = preferences[SUGGESTION_PROMPT] ?: DEFAULT_SUGGESTION_PROMPT,
                ocrModelId = preferences[OCR_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                ocrPrompt = preferences[OCR_PROMPT] ?: DEFAULT_OCR_PROMPT,
                compressModelId = preferences[COMPRESS_MODEL]?.let { Uuid.parse(it) } ?: DEFAULT_NVIDIA_MODEL_ID,
                compressPrompt = preferences[COMPRESS_PROMPT] ?: DEFAULT_COMPRESS_PROMPT,
                assistantId = preferences[SELECT_ASSISTANT]?.let { Uuid.parse(it) }
                    ?: DEFAULT_ASSISTANT_ID,
                assistantTags = preferences[ASSISTANT_TAGS].decodeOrNull(emptyList()),
                providers = runCatching {
                    JsonInstant.decodeFromString<List<ProviderSetting>>(preferences[PROVIDERS] ?: "[]")
                }.getOrDefault(emptyList()),
                assistants = runCatching {
                    JsonInstant.decodeFromString<List<me.rerere.rikkahub.data.model.Assistant>>(preferences[ASSISTANTS] ?: "[]")
                }.getOrDefault(emptyList()),
                dynamicColor = preferences[DYNAMIC_COLOR] != false,
                themeId = preferences[THEME_ID] ?: PresetThemes[0].id,
                customThemes = preferences[CUSTOM_THEMES].decodeOrNull(emptyList()),
                developerMode = preferences[DEVELOPER_MODE] == true,
                displaySetting = runCatching {
                    JsonInstant.decodeFromString<DisplaySetting>(preferences[DISPLAY_SETTING] ?: "{}")
                }.getOrDefault(DisplaySetting()),
                searchServices = preferences[SEARCH_SERVICES].decodeOrNull(SearchServiceOptions.allDefaults()),
                searchCommonOptions = preferences[SEARCH_COMMON].decodeOrNull(SearchCommonOptions()),
                searchServiceSelected = preferences[SEARCH_SELECTED] ?: 0,
                mcpServers = preferences[MCP_SERVERS].decodeOrNull(emptyList()),
                webDavConfig = preferences[WEBDAV_CONFIG].decodeOrNull(WebDavConfig()),
                s3Config = preferences[S3_CONFIG].decodeOrNull(S3Config()),
                ttsProviders = preferences[TTS_PROVIDERS].decodeOrNull(emptyList()),
                selectedTTSProviderId = preferences[SELECTED_TTS_PROVIDER]?.let { Uuid.parse(it) }
                    ?: DEFAULT_SYSTEM_TTS_ID,
                asrProviders = preferences[ASR_PROVIDERS].decodeOrNull(DEFAULT_ASR_PROVIDERS),
                selectedASRProviderId = preferences[SELECTED_ASR_PROVIDER]?.let { Uuid.parse(it) }
                    ?: DEFAULT_SYSTEM_ASR_ID,
                modeInjections = preferences[MODE_INJECTIONS].decodeOrNull(emptyList()),
                lorebooks = preferences[LOREBOOKS].decodeOrNull(emptyList()),
                quickMessages = preferences[QUICK_MESSAGES].decodeOrNull(emptyList()),
                backupReminderConfig = preferences[BACKUP_REMINDER_CONFIG].decodeOrNull(BackupReminderConfig()),
                launchCount = preferences[LAUNCH_COUNT] ?: 0,
                systemToolsSetting = preferences[SYSTEM_TOOLS_SETTING].decodeOrNull(SystemToolsSetting()),
                localWorkspaceUri = preferences[LOCAL_WORKSPACE_URI],
                todos = preferences[TODOS]?.let {
                    runCatching { JsonInstant.decodeFromString<List<me.rerere.rikkahub.data.ai.tools.TodoItem>>(it) }.getOrDefault(emptyList())
                } ?: emptyList(),
                knowledgeDocs = preferences[KNOWLEDGE_DOCS]?.let {
                    runCatching { JsonInstant.decodeFromString<List<KnowledgeDoc>>(it) }.getOrDefault(emptyList())
                } ?: emptyList(),
                wechatBotSettings = decodeWechatBotSettings(
                    listJson = preferences[WECHAT_BOT_SETTINGS],
                    oldSingleJson = preferences[WECHAT_BOT_SETTING],
                ),
                keepAliveEnabled = preferences[KEEP_ALIVE_ENABLED] == true,
                offlineOcrEnabled = preferences[OFFLINE_OCR_ENABLED] != false,
                externalMemories = preferences[EXTERNAL_MEMORIES].decodeOrNull(emptyList()),
                miniApps = preferences[MINI_APPS].decodeOrNull(emptyList()),
                forceConfirmToolCalls = preferences[FORCE_CONFIRM_TOOL_CALLS] != false,
                workflowHeadlessBlockSensitive = preferences[WORKFLOW_HEADLESS_BLOCK_SENSITIVE] != false,
                autoApproveAllTools = preferences[AUTO_APPROVE_ALL_TOOLS] == true,
            )
            }.getOrElse { Settings(providers = DEFAULT_PROVIDERS.map { it.copyProvider(models = emptyList()) }) }
        }
        .map {
            var providers = it.providers.ifEmpty { DEFAULT_PROVIDERS }.toMutableList()
            DEFAULT_PROVIDERS.forEach { defaultProvider ->
                if (providers.none { it.id == defaultProvider.id }) {
                    // 预置模型不自动添加：只作为"添加模型"候选，用户自己勾选
                    providers.add(defaultProvider.copyProvider(models = emptyList()))
                }
            }
            providers = providers.map { provider ->
                val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
                if (defaultProvider != null) {
                    // 预置模型不自动添加：迁移时移除配置中已有的预置模型（仅作为"添加模型"候选），
                    // 用户自己勾选才添加；用户自定义添加的模型（非预置 modelId）保留
                    val builtinModelIds = defaultProvider.models.map { it.modelId }.toSet()
                    provider.copyProvider(
                        builtIn = defaultProvider.builtIn,
                        description = defaultProvider.description,
                        shortDescription = defaultProvider.shortDescription,
                        models = if (builtinModelIds.isEmpty()) {
                            provider.models
                        } else {
                            provider.models.filterNot { it.modelId in builtinModelIds }
                        },
                    )
                } else provider
            }.toMutableList()
            val assistants = it.assistants.ifEmpty { DEFAULT_ASSISTANTS }.toMutableList()
            DEFAULT_ASSISTANTS.forEach { defaultAssistant ->
                if (assistants.none { it.id == defaultAssistant.id }) {
                    assistants.add(defaultAssistant.copy())
                }
            }
            // TTS 提供商列表：不自动填充全部默认 TTS（Edge/百度/有道/Google），
            // 只保证系统 TTS 存在，其他由用户手动添加选择
            val ttsProviders = it.ttsProviders.toMutableList()
            if (ttsProviders.none { provider -> provider.id == DEFAULT_SYSTEM_TTS_ID }) {
                ttsProviders.add(TTSProviderSetting.SystemTTS(id = DEFAULT_SYSTEM_TTS_ID, name = ""))
            }
            it.copy(
                providers = providers,
                assistants = assistants,
                ttsProviders = ttsProviders,
            )
        }
        .map { settings ->
            // 去重并清理无效引用
            val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
            val validModeInjectionIds = settings.modeInjections.map { it.id }.toSet()
            val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
            val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
            val asrProviders = settings.asrProviders.distinctBy { it.id }
            settings.copy(
                providers = settings.providers.distinctBy { it.id }.map { provider ->
                    when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(
                            models = provider.models.distinctBy { model -> model.modelId }
                        )

                        is ProviderSetting.Google -> provider.copy(
                            models = provider.models.distinctBy { model -> model.modelId }
                        )

                        is ProviderSetting.Claude -> provider.copy(
                            models = provider.models.distinctBy { model -> model.modelId }
                        )

                        else -> provider
                    }
                },
                assistants = settings.assistants.distinctBy { it.id }.map { assistant ->
                    assistant.copy(
                        // 过滤掉不存在的 MCP 服务器 ID
                        mcpServers = assistant.mcpServers.filter { serverId ->
                            serverId in validMcpServerIds
                        }.toSet(),
                        // 过滤掉不存在的模式注入 ID
                        modeInjectionIds = assistant.modeInjectionIds.filter { id ->
                            id in validModeInjectionIds
                        }.toSet(),
                        // 过滤掉不存在的 Lorebook ID
                        lorebookIds = assistant.lorebookIds.filter { id ->
                            id in validLorebookIds
                        }.toSet(),
                        // 过滤掉不存在的快捷消息 ID
                        quickMessageIds = assistant.quickMessageIds.filter { id ->
                            id in validQuickMessageIds
                        }.toSet()
                    )
                },
                ttsProviders = settings.ttsProviders.distinctBy { it.id },
                asrProviders = asrProviders,
                selectedASRProviderId = settings.selectedASRProviderId
                    ?.takeIf { id -> asrProviders.any { provider -> provider.id == id } }
                    ?: asrProviders.firstOrNull()?.id,
                favoriteModels = settings.favoriteModels.filter { uuid ->
                    settings.providers.flatMap { it.models }.any { it.id == uuid }
                },
                modeInjections = settings.modeInjections.distinctBy { it.id },
                lorebooks = settings.lorebooks.distinctBy { it.id },
                quickMessages = settings.quickMessages.distinctBy { it.id },
                miniApps = settings.miniApps.distinctBy { it.id },
            )
        }
        .onEach { settings ->
            // 只在助手列表变化时才清空 Pebble 模板缓存（assistant 的 messageTemplate 字段决定模板内容）
            // 避免无关设置变化（如显示设置、provider 设置等）触发不必要的模板重新编译
            if (settings.assistants != lastAssistantsForCacheInvalidation) {
                lastAssistantsForCacheInvalidation = settings.assistants
                get<PebbleEngine>().templateCache.invalidateAll()
            }
        }

    val settingsFlow = settingsFlowRaw
        .distinctUntilChanged()
        .toMutableStateFlow(scope, Settings.dummy())

    suspend fun update(settings: Settings) {
        if(settings.init) {
            Log.w(TAG, "Cannot update dummy settings")
            return
        }
        settingsFlow.value = settings
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = settings.dynamicColor
            preferences[THEME_ID] = settings.themeId
            preferences[CUSTOM_THEMES] = JsonInstant.encodeToString(settings.customThemes)
            preferences[DEVELOPER_MODE] = settings.developerMode
            preferences[DISPLAY_SETTING] = JsonInstant.encodeToString(settings.displaySetting)

            preferences[DISCLAIMER_ACCEPTED] = settings.disclaimerAccepted
            preferences[DISCLAIMER_ACCEPTED_AT] = settings.disclaimerAcceptedAt

            preferences[ENABLE_WEB_SEARCH] = settings.enableWebSearch
            preferences[FAVORITE_MODELS] = JsonInstant.encodeToString(settings.favoriteModels)
            preferences[SELECT_MODEL] = settings.chatModelId.toString()
            preferences[TITLE_MODEL] = settings.titleModelId.toString()
            preferences[TRANSLATE_MODEL] = settings.translateModeId.toString()
            preferences[SUGGESTION_MODEL] = settings.suggestionModelId.toString()
            preferences[IMAGE_GENERATION_MODEL] = settings.imageGenerationModelId.toString()
            preferences[TITLE_PROMPT] = settings.titlePrompt
            preferences[TRANSLATION_PROMPT] = settings.translatePrompt
            preferences[TRANSLATE_THINKING_BUDGET] = settings.translateThinkingBudget
            preferences[SUGGESTION_PROMPT] = settings.suggestionPrompt
            preferences[OCR_MODEL] = settings.ocrModelId.toString()
            preferences[OCR_PROMPT] = settings.ocrPrompt
            preferences[COMPRESS_MODEL] = settings.compressModelId.toString()
            preferences[COMPRESS_PROMPT] = settings.compressPrompt

            preferences[PROVIDERS] = JsonInstant.encodeToString(settings.providers)

            preferences[ASSISTANTS] = JsonInstant.encodeToString(settings.assistants)
            preferences[SELECT_ASSISTANT] = settings.assistantId.toString()
            preferences[ASSISTANT_TAGS] = JsonInstant.encodeToString(settings.assistantTags)

            preferences[SEARCH_SERVICES] = JsonInstant.encodeToString(settings.searchServices)
            preferences[SEARCH_COMMON] = JsonInstant.encodeToString(settings.searchCommonOptions)
            preferences[SEARCH_SELECTED] = settings.searchServiceSelected.coerceIn(0, settings.searchServices.size - 1)

            preferences[MCP_SERVERS] = JsonInstant.encodeToString(settings.mcpServers)
            preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(settings.webDavConfig)
            preferences[S3_CONFIG] = JsonInstant.encodeToString(settings.s3Config)
            preferences[TTS_PROVIDERS] = JsonInstant.encodeToString(settings.ttsProviders)
            settings.selectedTTSProviderId?.let {
                preferences[SELECTED_TTS_PROVIDER] = it.toString()
            } ?: preferences.remove(SELECTED_TTS_PROVIDER)
            preferences[ASR_PROVIDERS] = JsonInstant.encodeToString(settings.asrProviders)
            settings.selectedASRProviderId?.let {
                preferences[SELECTED_ASR_PROVIDER] = it.toString()
            } ?: preferences.remove(SELECTED_ASR_PROVIDER)
            preferences[MODE_INJECTIONS] = JsonInstant.encodeToString(settings.modeInjections)
            preferences[LOREBOOKS] = JsonInstant.encodeToString(settings.lorebooks)
            preferences[QUICK_MESSAGES] = JsonInstant.encodeToString(settings.quickMessages)
            preferences[BACKUP_REMINDER_CONFIG] = JsonInstant.encodeToString(settings.backupReminderConfig)
            preferences[LAUNCH_COUNT] = settings.launchCount
            preferences[SYSTEM_TOOLS_SETTING] = JsonInstant.encodeToString(settings.systemToolsSetting)
            if (settings.localWorkspaceUri != null) {
                preferences[LOCAL_WORKSPACE_URI] = settings.localWorkspaceUri
            } else {
                preferences.remove(LOCAL_WORKSPACE_URI)
            }
            preferences[KNOWLEDGE_DOCS] = JsonInstant.encodeToString(settings.knowledgeDocs)
            preferences[TODOS] = JsonInstant.encodeToString(settings.todos)
            preferences[WECHAT_BOT_SETTINGS] = JsonInstant.encodeToString(settings.wechatBotSettings)
            // 写新列表时清理旧单 bot 键, 避免后续迁移重复
            preferences.remove(WECHAT_BOT_SETTING)
            preferences[KEEP_ALIVE_ENABLED] = settings.keepAliveEnabled
            preferences[OFFLINE_OCR_ENABLED] = settings.offlineOcrEnabled
            preferences[EXTERNAL_MEMORIES] = JsonInstant.encodeToString(settings.externalMemories)
            preferences[MINI_APPS] = JsonInstant.encodeToString(settings.miniApps)
            preferences[FORCE_CONFIRM_TOOL_CALLS] = settings.forceConfirmToolCalls
            preferences[WORKFLOW_HEADLESS_BLOCK_SENSITIVE] = settings.workflowHeadlessBlockSensitive
            preferences[AUTO_APPROVE_ALL_TOOLS] = settings.autoApproveAllTools
        }
    }

    suspend fun update(fn: (Settings) -> Settings) {
        update(fn(settingsFlow.value))
    }

    suspend fun updateAssistant(assistantId: Uuid) {
        dataStore.edit { preferences ->
            preferences[SELECT_ASSISTANT] = assistantId.toString()
        }
    }

    suspend fun updateAssistantModel(assistantId: Uuid, modelId: Uuid) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(chatModelId = modelId)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantReasoningLevel(assistantId: Uuid, reasoningLevel: ReasoningLevel) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(reasoningLevel = reasoningLevel)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantMcpServers(assistantId: Uuid, mcpServers: Set<Uuid>) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(mcpServers = mcpServers)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantInjections(
        assistantId: Uuid,
        modeInjectionIds: Set<Uuid>,
        lorebookIds: Set<Uuid>,
        quickMessageIds: Set<Uuid> = emptySet(),
    ) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(
                            modeInjectionIds = modeInjectionIds,
                            lorebookIds = lorebookIds,
                            quickMessageIds = quickMessageIds,
                        )
                    } else {
                        assistant
                    }
                }
            )
        }
    }
}

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val disclaimerAccepted: Boolean = false,
    val disclaimerAcceptedAt: Int = 0,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val customThemes: List<CustomTheme> = emptyList(),
    val developerMode: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val enableWebSearch: Boolean = false,
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = DEFAULT_NVIDIA_MODEL_ID,
    val titleModelId: Uuid = DEFAULT_NVIDIA_MODEL_ID,
    val imageGenerationModelId: Uuid = DEFAULT_NVIDIA_MODEL_ID,
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    // #34: 标题总结默认走本地规则（不依赖网络模型、不消耗 token）
    val localTitleGeneration: Boolean = true,
    val translateModeId: Uuid = DEFAULT_NVIDIA_MODEL_ID,
    val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val translateThinkingBudget: Int = 0,
    val suggestionModelId: Uuid = DEFAULT_NVIDIA_MODEL_ID,
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    val ocrModelId: Uuid = DEFAULT_NVIDIA_MODEL_ID,
    val ocrPrompt: String = DEFAULT_OCR_PROMPT,
    val compressModelId: Uuid = DEFAULT_NVIDIA_MODEL_ID,
    val compressPrompt: String = DEFAULT_COMPRESS_PROMPT,
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS.map { it.copy(enabledSkills = setOf("all-skills", "tarot-extreme-accuracy")) },
    val assistantTags: List<Tag> = emptyList(),
    val searchServices: List<SearchServiceOptions> = SearchServiceOptions.allDefaults(),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    // TTS 提供商列表（默认只含系统 TTS，其余由用户手动添加）
    val ttsProviders: List<TTSProviderSetting> = listOf(
        TTSProviderSetting.SystemTTS(id = DEFAULT_SYSTEM_TTS_ID, name = ""),
    ),
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val asrProviders: List<ASRProviderSetting> = DEFAULT_ASR_PROVIDERS,
    val selectedASRProviderId: Uuid? = DEFAULT_SYSTEM_ASR_ID,
    val modeInjections: List<PromptInjection.ModeInjection> = DEFAULT_MODE_INJECTIONS,
    val lorebooks: List<Lorebook> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    val backupReminderConfig: BackupReminderConfig = BackupReminderConfig(),
    val launchCount: Int = 0,
    val systemToolsSetting: SystemToolsSetting = SystemToolsSetting(),
    // 本地文件夹工作区（SAF 授权 uri）：AI 可读写此文件夹作为项目工作区
    val localWorkspaceUri: String? = null,
    val knowledgeDocs: List<KnowledgeDoc> = emptyList(),
    val todos: List<me.rerere.rikkahub.data.ai.tools.TodoItem> = emptyList(),
    val wechatBotSettings: List<WechatBotSetting> = emptyList(),
    // 默认开启保活: 切后台时 AI 生成继续运行, 不被系统回收进程
    val keepAliveEnabled: Boolean = true,
    // 免费离线 OCR (ML Kit) 优先; 失败时回退到 AI 视觉模型
    val offlineOcrEnabled: Boolean = true,
    val externalMemories: List<ExternalMemory> = emptyList(),
    val miniApps: List<MiniApp> = emptyList(),
    val forceConfirmToolCalls: Boolean = true,
    val workflowHeadlessBlockSensitive: Boolean = true,
    val autoApproveAllTools: Boolean = false,
) {
    companion object {
        // 构造一个用于初始化的settings, 但它不能用于保存，防止使用初始值存储
        fun dummy() = Settings(init = true)
    }
}

@Serializable
enum class ChatFontFamily {
    @SerialName("default")
    DEFAULT,
    @SerialName("serif")
    SERIF,
    @SerialName("monospace")
    MONOSPACE,
    @SerialName("custom")
    CUSTOM,
}

@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val useAppIconStyleLoadingIndicator: Boolean = true,
    val showUserAvatar: Boolean = true,
    val showAssistantBubble: Boolean = false,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showDateBelowName: Boolean = false,
    val showTokenUsage: Boolean = true,
    val showThinkingContent: Boolean = true,
    val autoCloseThinking: Boolean = true,
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val enableMessageGenerationHapticEffect: Boolean = false,
    val skipCropImage: Boolean = false,
    val enableNotificationOnMessageGeneration: Boolean = false,
    val enableLiveUpdateNotification: Boolean = false,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val ttsOnlyReadQuoted: Boolean = false,
    val autoPlayTTSAfterGeneration: Boolean = false,
    val pasteLongTextAsFile: Boolean = false,
    val pasteLongTextThreshold: Int = 1000,
    val sendOnEnter: Boolean = false,
    val enableAutoScroll: Boolean = true,
    val enableLatexRendering: Boolean = true,
    val enableBlurEffect: Boolean = false,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 1.0f,
    val chatBubbleTransparency: Float = 0f,
    val thinkingChainTransparency: Float = 0f,
    // 自定义字体
    val customFontPath: String = "",
    // 输入框自定义背景
    val inputBackgroundPath: String = "",
    // 头像框（QQ挂件风格）
    val userAvatarFramePath: String = "",
    val aiAvatarFramePath: String = "",
    val userAvatarFrameOffsetX: Float = 0f,
    val userAvatarFrameOffsetY: Float = 0f,
    val userAvatarFrameScale: Float = 1f,
    val aiAvatarFrameOffsetX: Float = 0f,
    val aiAvatarFrameOffsetY: Float = 0f,
    val aiAvatarFrameScale: Float = 1f,
    // 侧边栏背景
    val drawerBackgroundPath: String = "",
    // 侧边栏元素透明度
    val drawerItemAlpha: Float = 1f,
    // 颜色自定义
    val chatTextColor: Long? = null,
    val globalTextColor: Long? = null,
    val userBubbleColor: Long? = null,
    val assistantBubbleColor: Long? = null,
    val thinkingFontSizeRatio: Float = 1.0f,
    val bubbleOpacity: Float = 1.0f,
    val showDateTimeInMessage: Boolean = false,
    val ttsOnlyReadOutsideBrackets: Boolean = false,
    val chatCustomFontPath: String = "",
    val chatCustomFontName: String = "",
    val showThinkingTime: Boolean = true,
    val thinkingBubbleColor: Long? = null,
    val chatBackgroundColor: Long? = null,
    val primaryColor: Long? = null,
    val inputFieldColor: Long? = null,
    // 气泡背景图 & 圆角
    val userBubbleImagePath: String = "",
    val assistantBubbleImagePath: String = "",
    val bubbleCornerRadius: Float = 16f,
    val bubbleImageOverlayEnabled: Boolean = false, // 关=纯图片, 开=图片+主题色遮罩
)

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "rikkahub_backups",
    val items: List<BackupItem> = listOf(
        BackupItem.DATABASE,
        BackupItem.FILES
    ),
) {
    @Serializable
    enum class BackupItem {
        DATABASE,
        FILES,
    }
}

@Serializable
data class BackupReminderConfig(
    val enabled: Boolean = false,
    val intervalDays: Int = 7,
    val lastBackupTime: Long = 0L,
)

fun Settings.isNotConfigured(): Boolean {
    // 已配置 = 任意提供商有 API Key 或有模型（不要求模型列表非空，Key 才是关键）
    return providers.none { provider ->
        val hasKey = when (provider) {
            is me.rerere.ai.provider.ProviderSetting.OpenAI -> provider.apiKey.isNotBlank()
            is me.rerere.ai.provider.ProviderSetting.Google -> provider.apiKey.isNotBlank()
            is me.rerere.ai.provider.ProviderSetting.Claude -> provider.apiKey.isNotBlank()
            else -> false
        }
        hasKey || provider.models.isNotEmpty()
    }
}

fun Settings.findModelById(uuid: Uuid): Model? {
    return this.providers.findModelById(uuid)
}

fun List<ProviderSetting>.findModelById(uuid: Uuid): Model? {
    this.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == uuid) {
                return model
            }
        }
    }
    return null
}

fun Settings.getCurrentChatModel(): Model? {
    return findModelById(this.getCurrentAssistant().chatModelId ?: this.chatModelId)
}

fun Settings.getCurrentAssistant(): Assistant {
    return this.assistants.find { it.id == assistantId } ?: this.assistants.first()
}

fun Settings.getAssistantById(id: Uuid): Assistant? {
    return this.assistants.find { it.id == id }
}

fun Settings.getQuickMessagesOfAssistant(assistant: Assistant) =
    quickMessages.filter { it.id in assistant.quickMessageIds }

fun Settings.getSelectedTTSProvider(): TTSProviderSetting? {
    return selectedTTSProviderId?.let { id ->
        ttsProviders.find { it.id == id }
    } ?: ttsProviders.firstOrNull()
}

fun Settings.getSelectedASRProvider(): ASRProviderSetting? {
    return selectedASRProviderId?.let { id ->
        asrProviders.find { it.id == id }
    } ?: asrProviders.firstOrNull()
}

fun Model.findProvider(providers: List<ProviderSetting>, checkOverwrite: Boolean = true): ProviderSetting? {
    val provider = findModelProviderFromList(providers) ?: return null
    val providerOverwrite = this.providerOverwrite
    if (checkOverwrite && providerOverwrite != null) {
        return providerOverwrite.copyProvider(models = emptyList())
    }
    return provider
}

private fun Model.findModelProviderFromList(providers: List<ProviderSetting>): ProviderSetting? {
    providers.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == this.id) {
                return setting
            }
        }
    }
    return null
}


val DEFAULT_SYSTEM_TTS_ID = Uuid.parse("026a01a2-c3a0-4fd5-8075-80e03bdef200")
private val DEFAULT_TTS_PROVIDERS = listOf(
    TTSProviderSetting.SystemTTS(
        id = DEFAULT_SYSTEM_TTS_ID,
        name = "",
    ),
    TTSProviderSetting.EdgeTTS(
        id = Uuid.parse("e36b22ef-ca82-40ab-9e70-60cad861911c"),
        name = "Edge TTS (免费)",
        voice = "zh-CN-XiaoxiaoNeural",
    ),
    TTSProviderSetting.BaiduTTS(
        id = Uuid.parse("5a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"),
        name = "百度 TTS (免费)",
        speed = 5,
    ),
    TTSProviderSetting.YoudaoTTS(
        id = Uuid.parse("6b2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e"),
        name = "有道 TTS (免费)",
        speed = 50,
    ),
    TTSProviderSetting.GoogleFreeTTS(
        id = Uuid.parse("7c3d4e5f-6a7b-4c8d-8e9f-1a2b3c4d5e6f"),
        name = "Google TTS (免费)",
        lang = "zh-CN",
    ),
)

val DEFAULT_SYSTEM_ASR_ID = Uuid.parse("4d2e9c1a-9d24-4a5e-b1c3-7e9f2a6d8b01")
private val DEFAULT_ASR_PROVIDERS = listOf(
    ASRProviderSetting.SystemAsr(
        id = DEFAULT_SYSTEM_ASR_ID,
        name = "系统语音识别 (免费)",
    )
)

internal val DEFAULT_ASSISTANT_ID = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
internal val DEFAULT_ASSISTANTS = listOf(
    Assistant(
        id = DEFAULT_ASSISTANT_ID,
        name = "",
        systemPrompt = "",
        localTools = listOf(
            LocalToolOption.TimeInfo,
            LocalToolOption.WebFetch,
            LocalToolOption.Clipboard,
            LocalToolOption.JavascriptEngine,
            LocalToolOption.ListZipContents,
            LocalToolOption.AskUser,
            LocalToolOption.CheckTokenUsage,
            LocalToolOption.AllowSkipReply,
        )
    ),
)
internal val DEFAULT_ASSISTANTS_IDS = DEFAULT_ASSISTANTS.map { it.id }

val DEFAULT_MODE_INJECTIONS = listOf(
    PromptInjection.ModeInjection(
        id = Uuid.parse("b87eaf16-f5cd-4ac1-9e4f-b11ae3a61d74"),
        content = LEARNING_MODE_PROMPT,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        name = "Learning Mode"
    )
)


// ---- 消息桥 (Message Bridge) ----

@Serializable
data class PendingMessage(
    val id: String = kotlin.uuid.Uuid.random().toString(),
    val source: String,
    val content: String,
    val direction: String = "incoming",
    val createdAt: Long = System.currentTimeMillis(),
    val readAt: Long? = null,
    val status: String = "pending",
)

@Serializable
data class KnowledgeDoc(
    val id: String = kotlin.uuid.Uuid.random().toString(),
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class PollingTask(
    val source: String,
    val url: String,
    val intervalSeconds: Int,
    val headers: String? = null,
)

suspend fun SettingsStore.getPendingMessages(): List<PendingMessage> = try {
    val raw = dataStore.data.first()[SettingsStore.PENDING_MESSAGES] ?: "[]"
    JsonInstant.decodeFromString<List<PendingMessage>>(raw)
} catch (_: Exception) { emptyList() }

suspend fun SettingsStore.savePendingMessages(msgs: List<PendingMessage>) {
    dataStore.edit { it[SettingsStore.PENDING_MESSAGES] = JsonInstant.encodeToString(msgs) }
}

suspend fun SettingsStore.addPendingMessage(msg: PendingMessage) {
    savePendingMessages(getPendingMessages() + msg)
}

suspend fun SettingsStore.getPollingTasks(): List<PollingTask> = try {
    val raw = dataStore.data.first()[SettingsStore.POLLING_TASKS] ?: "[]"
    JsonInstant.decodeFromString<List<PollingTask>>(raw)
} catch (_: Exception) { emptyList() }

suspend fun SettingsStore.addPollingTask(task: PollingTask) {
    val tasks = getPollingTasks().toMutableList()
    tasks.removeAll { it.source == task.source }
    tasks.add(task)
    dataStore.edit { it[SettingsStore.POLLING_TASKS] = JsonInstant.encodeToString(tasks) }
}
