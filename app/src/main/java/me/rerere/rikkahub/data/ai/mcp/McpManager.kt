/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.mcp

import android.content.Context
import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.JsonInstant
import androidx.core.net.toUri
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

/**
 * MCP 管理器。
 *
 * 会话生命周期由 [McpSessionRegistry] 管理，OAuth 协议细节由 [McpOAuthCoordinator] 管理。
 * 额外保留本项目的独有能力：内置 MCP 服务器工具（抖音/12306/APK 等）、
 * 内置服务器信息、手动重连。
 */
class McpManager(
    private val settingsStore: SettingsStore,
    private val appScope: AppScope,
    private val filesManager: FilesManager,
    appEventBus: AppEventBus,
    private val workspaceRepository: WorkspaceRepository,
    private val context: Context,
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followSslRedirects(true)
        .followRedirects(true)
        .build()

    private val httpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        install(SSE)
    }

    private val statusStore = McpStatusStore()
    private val oauthCoordinator = McpOAuthCoordinator(
        settingsStore = settingsStore,
        appScope = appScope,
        appEventBus = appEventBus,
        oauthClient = McpOAuthClient(okHttpClient),
        updateStatus = statusStore::update,
    )
    private val sessionRegistry = McpSessionRegistry(
        settingsStore = settingsStore,
        appScope = appScope,
        httpClient = httpClient,
        oauthCoordinator = oauthCoordinator,
        statusStore = statusStore,
    )

    init {
        appScope.launch {
            settingsStore.settingsFlow
                .map { settings -> settings.mcpServers }
                .distinctUntilChanged()
                .collect(sessionRegistry::reconcile)
        }
    }

    val syncingStatus: StateFlow<Map<Uuid, McpStatus>>
        get() = statusStore.status

    fun getClient(config: McpServerConfig): Client? = sessionRegistry.getClient(config.id)

    fun getStatus(config: McpServerConfig): Flow<McpStatus> = sessionRegistry.getStatus(config.id)

    /** 可用 MCP 工具（保留本项目调用处的 Pair<Uuid, McpTool> 形式） */
    fun getAllAvailableTools(): List<Pair<Uuid, McpTool>> {
        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getCurrentAssistant()
        return settings.mcpServers
            .filter { it.commonOptions.enable && it.id in assistant.mcpServers }
            .flatMap { server ->
                server.commonOptions.tools
                    .filter { tool -> tool.enable }
                    .map { tool -> server.id to tool }
            }
    }

    /**
     * 内置 MCP 服务器工具（本项目独有：无需配置外部服务器，开箱即用）
     */
    fun getBuiltinServerTools(assistant: Assistant? = null): List<me.rerere.ai.core.Tool> {
        val enabled = assistant?.builtinMcpIds ?: setOf("filesystem", "memory")
        val list = mutableListOf<me.rerere.ai.core.Tool>()

        if ("memory" in enabled) {
            list += me.rerere.rikkahub.data.ai.tools.buildMemoryMcpTools()
        }
        if ("12306" in enabled) {
            list += me.rerere.rikkahub.data.ai.tools.buildTicket12306McpTools()
        }
        if ("apk" in enabled) {
            list += me.rerere.rikkahub.data.ai.tools.buildApkReverseMcpTools(context)
        }
        if ("context7" in enabled) {
            list += me.rerere.rikkahub.data.ai.tools.buildContext7McpTools()
        }
        if ("filesystem" in enabled) {
            list += me.rerere.rikkahub.data.ai.tools.buildFileSystemMcpTools(context)
        }
        if ("imagegps" in enabled) {
            list += me.rerere.rikkahub.data.ai.tools.createImageGpsTools()
        }
        return list
    }

    /** 内置 MCP 服务器信息（用于 MCP 管理界面显示，本项目独有） */
    fun getBuiltinServerInfos(): List<Pair<me.rerere.rikkahub.data.ai.tools.BuiltinMcpServerInfo, Int>> =
        listOf(
            me.rerere.rikkahub.data.ai.tools.BuiltinMcpServerInfo(
                id = "builtin-12306",
                name = "12306 火车票 🚄",
                description = "内置12306查票/中转/经停站/跨站/车站代码查询（源自 Joooook/12306-mcp）",
                toolCount = 6,
            ) to 1,
            me.rerere.rikkahub.data.ai.tools.BuiltinMcpServerInfo(
                id = "builtin-apk",
                name = "APK 解析 🔧",
                description = "内置APK解析：包信息/版本/权限/四大组件（Android系统解析，无需工作区/外部工具）",
                toolCount = 2,
            ) to 1,
            me.rerere.rikkahub.data.ai.tools.BuiltinMcpServerInfo(
                id = "builtin-context7",
                name = "Context7 文档 MCP 📚",
                description = "内置最新库文档查询：解析库名/获取文档（React/Vue/Kotlin/Next.js等，数据来自Context7）",
                toolCount = 2,
            ) to 1,
            me.rerere.rikkahub.data.ai.tools.BuiltinMcpServerInfo(
                id = "builtin-filesystem",
                name = "Filesystem MCP 📁",
                description = "内置完整文件系统操作：读/写/复制/移动/删除/搜索/目录树/文件信息",
                toolCount = 12,
            ) to 1,
            me.rerere.rikkahub.data.ai.tools.BuiltinMcpServerInfo(
                id = "builtin-memory",
                name = "Memory MCP",
                description = "内置内存键值存储 MCP（会话内临时数据，移植自 Kelivo）",
                toolCount = 4,
            ) to 1,
            me.rerere.rikkahub.data.ai.tools.BuiltinMcpServerInfo(
                id = "builtin-imagegps",
                name = "图片 GPS 位置修改器 📍",
                description = "内置图片 GPS 位置修改：读取/写入/清除图片 EXIF 拍摄地经纬度信息",
                toolCount = 3,
            ) to 1,
        )

    suspend fun callTool(serverId: Uuid, toolName: String, args: JsonObject): List<UIMessagePart> {
        val result = try {
            sessionRegistry.callTool(serverId, toolName, args)
        } catch (e: CancellationException) {
            throw e
        } catch (e: McpClientUnavailableException) {
            return listOf(UIMessagePart.Text("Failed to execute MCP tool: ${e.message ?: e.javaClass.name}"))
        }
        return result.content.map { content ->
            when (content) {
                is TextContent -> UIMessagePart.Text(content.text)
                is ImageContent -> convertImageContentToFilePart(content)
                else -> UIMessagePart.Text(JsonInstant.encodeToString(content))
            }
        }
    }

    suspend fun addClient(config: McpServerConfig) = sessionRegistry.addClient(config)

    suspend fun removeClient(config: McpServerConfig) = sessionRegistry.removeClient(config)

    suspend fun syncAll() = sessionRegistry.syncAll()

    /** 手动重连指定服务器（本项目独有） */
    suspend fun reconnectServer(serverId: Uuid) {
        val config = settingsStore.settingsFlow.value.mcpServers
            .firstOrNull { it.id == serverId } ?: return
        runCatching { sessionRegistry.removeClient(config) }
        runCatching { sessionRegistry.addClient(config) }
    }

    fun startAuthorization(config: McpServerConfig, context: Context) {
        oauthCoordinator.startAuthorization(config, context)
    }

    fun cancelAuthorization(config: McpServerConfig) {
        oauthCoordinator.cancelAuthorization(config.id)
    }

    suspend fun clearAuthorization(config: McpServerConfig) {
        val freshConfig = oauthCoordinator.clearAuthorization(config)
        sessionRegistry.addClient(freshConfig)
    }

    private suspend fun convertImageContentToFilePart(image: ImageContent): UIMessagePart.Image {
        val bytes = Base64.decode(image.data, Base64.NO_WRAP)
        val extension = android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(image.mimeType) ?: "bin"
        val entity = filesManager.saveManagedFromBytes(
            folder = "uploads",
            bytes = bytes,
            displayName = "mcp_image.$extension",
            mimeType = image.mimeType,
        )
        return UIMessagePart.Image(url = filesManager.getFile(entity).toUri().toString())
    }
}
