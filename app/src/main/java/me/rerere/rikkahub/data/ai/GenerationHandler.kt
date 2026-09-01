/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai
 
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.core.merge
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.EmbeddingGenerationParams
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.MessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.ai.tools.buildMemoryTools
import me.rerere.rikkahub.data.ai.tools.buildWriteFilesTool
import me.rerere.rikkahub.data.ai.tools.createSearchConversationsTool
import me.rerere.rikkahub.core.tools.ToolExecutionManager
import me.rerere.rikkahub.core.tools.ToolInvocation
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.service.MemoryBankService
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.rikkahub.utils.removeThinkingContent
import me.rerere.rikkahub.utils.extractThinkingContent
import me.rerere.rikkahub.utils.isPureThinking
import java.util.Locale
import kotlin.time.Clock
 
private const val TAG = "GenerationHandler"
 
// 流式生成时往 UI 推送消息更新的最小间隔。
// AI 的 SSE 增量可能每秒到达几十次，如果每次都原样同步到 UI 的 StateFlow，
// 会导致 Compose 高频重组（Markdown 全量重解析、代码高亮重新分词、
// animateContentSize 的尺寸补间动画被不断打断重启），表现为打字机效果的"抖动/掉帧"。
// 这里把推送频率限制在这个间隔以内，肉眼完全感知不到延迟，但能大幅降低重组频率。
private const val STREAM_UI_THROTTLE_MS = 250L
 
@Serializable
sealed interface GenerationChunk {
    data class Messages(
        val messages: List<UIMessage>
    ) : GenerationChunk

    /** 非对话内容的提醒（如上下文超限警告），不写入对话历史 */
    @Serializable
    data class Reminder(
        val text: String
    ) : GenerationChunk
}
 
class GenerationHandler(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val json: Json,
    private val memoryRepo: MemoryRepository,
    private val conversationRepo: ConversationRepository,
    private val aiLoggingManager: AILoggingManager,
    private val memoryBankService: MemoryBankService,
) {
    fun generateText(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer> = emptyList(),
        outputTransformers: List<OutputMessageTransformer> = emptyList(),
        assistant: Assistant,
        memories: List<AssistantMemory>? = null,
        tools: List<Tool> = emptyList(),
        maxSteps: Int = 256,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        workspaceCwd: String? = null,
        pluginPromptInjections: List<String> = emptyList(),
        conversationId: String? = null,
    ): Flow<GenerationChunk> = flow {
        val provider = model.findProvider(settings.providers)
            ?: error("Provider not found for model: ${model.id}")
        val providerImpl = providerManager.getProviderByType(provider)
 
        var messages: List<UIMessage> = messages

        // 静默催答提示（仅注入下一轮 system，用户不可见）：正文空白自动补全时设置
        var forcePrompt: String? = null

        // ===== 死循环/重复检测（对齐 Operit EnhancedAIService：静默纠偏，用户无感知）=====
        var consecutiveToolRepeatCount = 0
        var lastToolSignature = ""
        var consecutiveTextRepeatCount = 0
        var lastTextSignature = 0

        // ===== Agent 编排状态（#2 规划 + #5 反思）=====
        // 标记本线程是否已注入过规划/反思提示，避免每步都重复注入
        var agentPlannerInjected = false
        var agentReflectionInjected = false
        // 记录最近用户输入（用于判断是否复杂任务）
        val lastUserText = messages.lastOrNull { it.role == MessageRole.USER }?.toText() ?: ""

        // 强制原生工具调用：不注入工具列表、不做规则调度，
        // 由模型原生 tools 参数驱动（支持原生的模型自动调用，不支持的不调）

        for (stepIndex in 0 until maxSteps) {
            Log.i(TAG, "streamText: start step #$stepIndex (${model.id})")
 
            // ---------- Agent 编排注入（仅当 pendingTools 为空、要发起新一轮生成时）----------
            val hasAnyToolResults = messages.any { m ->
                m.parts.any { it is UIMessagePart.Tool }
            }
            // 复杂任务前缀规划（一次性注入，给模型一份行动计划）
            if (!agentPlannerInjected &&
                me.rerere.rikkahub.data.ai.agent.AgentOrchestrator.needsPlanning(
                    userText = lastUserText,
                    hasToolActivity = hasAnyToolResults,
                    enablePlanning = assistant?.enableAgentPlanning ?: true,
                )
            ) {
                agentPlannerInjected = true
                forcePrompt = me.rerere.rikkahub.data.ai.agent.AgentOrchestrator.buildPlannerPrompt()
                Log.i(TAG, "AgentOrchestrator: 注入规划提示")
            }
            // 工具执行后反思自评（一次性注入，让模型对上一步结果做修正决策）
            if (!agentReflectionInjected &&
                me.rerere.rikkahub.data.ai.agent.AgentOrchestrator.needsReflection(
                    enableReflection = assistant?.enableAgentReflection ?: true,
                    hasToolResults = hasAnyToolResults,
                    alreadyReflected = agentReflectionInjected,
                )
            ) {
                agentReflectionInjected = true
                val reflection = me.rerere.rikkahub.data.ai.agent.AgentOrchestrator.buildReflectionPrompt()
                forcePrompt = forcePrompt?.let { "$it\n\n$reflection" } ?: reflection
                Log.i(TAG, "AgentOrchestrator: 注入反思提示")
            }
 
            val toolsInternal = buildList {
                Log.i(TAG, "generateInternal: build tools($assistant)")
                if (assistant?.enableMemory == true) {
                    val memoryAssistantId = if (assistant.useGlobalMemory) {
                        MemoryRepository.GLOBAL_MEMORY_ID
                    } else {
                        assistant.id.toString()
                    }
                    buildMemoryTools(
                        json = json,
                        onCreation = { content ->
                            // OmbreBrain: 从内容提取简短标题并计算初始重要度
                            val title = content.trim().split('\n', '。', '.', '!', '？', '?')
                                .firstOrNull { it.isNotBlank() }?.trim()?.take(40) ?: content.take(40)
                            val initialImportance =
                                me.rerere.rikkahub.data.ai.memory.ombrebrain.ImportanceScorer()
                                    .initialScore(0.0, content.length, 0)
                                    .coerceAtLeast(0.2)
                            memoryRepo.addMemory(
                                memoryAssistantId, content,
                                title = title, importance = initialImportance
                            )
                        },
                        onUpdate = { id, content ->
                            memoryRepo.updateContent(id, content)
                        },
                        onDelete = { id ->
                            memoryRepo.deleteMemory(id)
                        },
                        onLink = { sourceId, targetId ->
                            memoryRepo.linkMemories(sourceId, targetId)
                        },
                        readOnly = assistant?.memoryReadOnly == true,
                    ).let(this::addAll)
                }
                // 文件写入工具 - AI可直接将文件内容写入设备或打包ZIP
                add(buildWriteFilesTool(conversationId))
                addAll(tools)
                // 参考历史聊天记录：提供搜索历史对话内容的能力，
                // 避免 AI 只有 recent_chat 标题而无法检索正文（幻觉调用不存在的工具）
                if (assistant?.enableRecentChatsReference == true) {
                    add(createSearchConversationsTool(conversationRepo, settings))
                }
            }.map { tool ->
                // DeepSeek 等 OpenAI 兼容接口严格校验 tools[].function.name 只允许
                // [a-zA-Z0-9_-]。MCP/插件等远端工具名可能含点号、空格、中文等非法字符，
                // 统一替换为下划线，避免请求被 400 拒绝。
                if (tool.name.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
                    tool
                } else {
                    tool.copy(
                        name = tool.name.map { c ->
                            if (c.isLetterOrDigit() || c == '_' || c == '-') c else '_'
                        }.joinToString("")
                    )
                }
            }
 
            // Check if we have tool calls ready to continue after user interaction.
            val pendingTools = messages.lastOrNull()?.getTools()?.filter {
                it.canResumeExecution
            } ?: emptyList()
 
            val toolsToProcess: List<UIMessagePart.Tool>
 
            // Skip generation if we have approved/denied tool calls to handle
            if (pendingTools.isEmpty()) {
                generateInternal(
                    assistant = assistant,
                    settings = settings,
                    messages = messages,
                    pluginPromptInjections = pluginPromptInjections,
                    onUpdateMessages = {
                        messages = it.transforms(
                            transformers = outputTransformers,
                            context = context,
                            model = model,
                            assistant = assistant,
                            settings = settings
                        )
                        emit(
                            GenerationChunk.Messages(
                                messages.visualTransforms(
                                    transformers = outputTransformers,
                                    context = context,
                                    model = model,
                                    assistant = assistant,
                                    settings = settings
                                )
                            )
                        )
                    },
                    transformers = inputTransformers,
                    model = model,
                    providerImpl = providerImpl,
                    provider = provider,
                    tools = toolsInternal,
                    memories = memories ?: emptyList(),
                    stream = assistant.streamOutput,
                    processingStatus = processingStatus,
                    conversationSystemPrompt = conversationSystemPrompt,
                    workspaceCwd = workspaceCwd,
                    internalForcePrompt = forcePrompt,
                )
                // 本轮已消费催答提示，重置（仅静默补全那一轮生效）
                forcePrompt = null
                messages = messages.visualTransforms(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                messages = messages.onGenerationFinish(
                    transformers = outputTransformers,
                    context = context,
                    model = model,
                    assistant = assistant,
                    settings = settings
                )
                messages = messages.slice(0 until messages.lastIndex) + messages.last().copy(
                    finishedAt = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                )
                emit(GenerationChunk.Messages(messages))

                // 兼容模型把工具调用以纯文本 XML（<invoke name=...><parameter name=...>...</parameter></invoke>）
                // 输出的情况：将其识别为结构化 Tool，统一走"正在执行工具"渲染与执行。
                // 覆盖所有供应商/模型：只要文本中出现成对的工具调用标签即被解析。
                val lastMsg = messages.last()
                val parsedParts = TextToolCallParser.extract(
                    lastMsg.parts,
                    allowedToolNames = toolsInternal.map { it.name }.toSet(),
                )
                if (parsedParts != lastMsg.parts) {
                    messages = messages.slice(0 until messages.lastIndex) +
                        lastMsg.copy(parts = parsedParts)
                    emit(GenerationChunk.Messages(messages))
                }

                val tools = messages.last().getTools().filter { !it.isExecuted }

                // ===== 死循环/重复检测：工具调用重复（对齐 Operit：静默纠偏或停止）=====
                if (tools.isNotEmpty()) {
                    val currentToolSignature = tools.joinToString(";") { "${it.toolName}:${it.input}" }
                    if (currentToolSignature == lastToolSignature) {
                        consecutiveToolRepeatCount++
                        Log.w(TAG, "streamText: 检测到连续重复工具调用 ($consecutiveToolRepeatCount 次): $currentToolSignature")
                        if (consecutiveToolRepeatCount >= 3) {
                            // 连续重复 3 次，判定为死循环，直接终止
                            Log.e(TAG, "streamText: 工具调用死循环，强制终止")
                            break
                        }
                        // 静默注入纠偏提示（用户无感知）
                        forcePrompt = (forcePrompt ?: "") + "\n\n【系统警告】你刚刚已经执行过完全相同的工具调用，但似乎没有解决问题或仍在重复。请立刻停止重复操作，重新审视当前状态和上下文，换一种方法，或者直接给用户回复当前的结果和遇到的问题。不要再执行相同的工具。"
                    } else {
                        consecutiveToolRepeatCount = 0
                        lastToolSignature = currentToolSignature
                    }
                }

                if (tools.isEmpty()) {
                    // finish_reason == "length" means the model hit the token limit
                    // but hasn't finished generating. Auto-continue by not breaking.
                    if (messages.last().finishReason == "length") {
                        Log.i(TAG, "streamText: finish_reason=length, auto-continuing step #$stepIndex")
                        // 输出已达长度上限，提醒用户（不阻塞自动补全逻辑）
                        emit(
                            GenerationChunk.Reminder(
                                "⚠️ 模型回复已达输出长度上限（finish_reason=length），当前回复可能不完整。" +
                                    "建议：精简问题、分多次提问，或压缩上下文后重试。"
                            )
                        )
                        continue
                    }

                    // 弱模型兜底：模型未返回 tool_calls，但用户消息明确需要工具时，
                    // 客户端自动调用工具（使用 ToolExecutionManager 统一管理）
                    val lastUserMsg = messages.lastOrNull { it.role == MessageRole.USER }
                    val userText = lastUserMsg?.toText()?.trim() ?: ""
                    val alreadyHasTool = messages.any { m ->
                        m.parts.any { it is UIMessagePart.Tool }
                    }
                    if (userText.isNotBlank() && !alreadyHasTool) {
                        val routed = me.rerere.rikkahub.data.ai.ToolRouter.route(userText)
                        if (routed != null) {
                            val (toolName, argsJson) = routed
                            val toolDef = toolsInternal.firstOrNull { it.name == toolName }
                            if (toolDef != null) {
                                try {
                                    val toolPart = UIMessagePart.Tool(
                                        toolCallId = "auto-${System.currentTimeMillis()}",
                                        toolName = toolName,
                                        input = argsJson,
                                        approvalState = me.rerere.ai.ui.ToolApprovalState.Auto,
                                    )
                                    val lastMsg = messages.last()
                                    messages = messages.dropLast(1) + lastMsg.copy(
                                        parts = lastMsg.parts + toolPart
                                    )
                                    emit(GenerationChunk.Messages(messages))

                                    // 使用 ToolExecutionManager 执行工具
                                    val invocation = me.rerere.rikkahub.core.tools.ToolInvocation(
                                        tool = toolDef,
                                        rawText = argsJson,
                                    )
                                    val result = me.rerere.rikkahub.core.tools.ToolExecutionManager.executeInvocation(invocation)
                                    
                                    val outputParts = if (result.success) {
                                        listOf(UIMessagePart.Text(result.result))
                                    } else {
                                        listOf(UIMessagePart.Text(
                                            json.encodeToString(buildJsonObject {
                                                put("error", JsonPrimitive(result.error ?: "Unknown error"))
                                            })
                                        ))
                                    }
                                    val executed = toolPart.copy(output = outputParts)

                                    val updatedLast = messages.last()
                                    messages = messages.dropLast(1) + updatedLast.copy(
                                        parts = updatedLast.parts.map { part ->
                                            if (part is UIMessagePart.Tool && part.toolCallId == executed.toolCallId) executed else part
                                        }
                                    )
                                    emit(GenerationChunk.Messages(messages))
                                    Log.i(TAG, "弱模型兜底自动调用工具: $toolName")
                                    continue
                                } catch (e: Exception) {
                                    Log.w(TAG, "兜底工具执行失败 $toolName: ${e.message}")
                                }
                            }
                        }
                    }

                    // 无工具调用。若最后一条 assistant 正文为空(模型只思考未输出)，静默自动续一轮强制给出完整回答
                    val finalText = messages.lastOrNull()?.toText()?.trim()
                    if (finalText.isNullOrBlank()) {
                        Log.i(TAG, "streamText: assistant produced no visible text (thinking only) step #$stepIndex, silently forcing completion")
                        // 静默催答：下一轮 generateInternal 通过 internalForcePrompt 注入提示，用户无感知
                        forcePrompt = "你刚才只进行了思考但还没有给出正式回答。请立即直接输出这个问题的完整答案，不要再次沉默或只思考。"
                        continue
                    }
                    
                    // Operit 纯思考检测：移除 thinking 标签后内容为空，说明 AI 只思考没输出
                    if (assistant?.enablePureThinkingDetection == true && !assistant?.disablePureThinkingWarning == true) {
                        val contentWithoutThinking = removeThinkingContent(finalText)
                        if (contentWithoutThinking.isEmpty()) {
                            Log.w(TAG, "streamText: 检测到纯思考输出（移除thinking后为空），回传告警让AI继续生成 step #$stepIndex")
                            // 向 UI 发出纯思考告警
                            val pureThinkingWarning = "⚠️ 你只进行了思考但没有输出正式答案。请直接给出完整回答。"
                            emit(UIMessagePart.Text(pureThinkingWarning))
                            // 将告警作为工具结果注入到历史，让AI知道需要继续输出
                            internalForcePrompt = "$internalForcePrompt\n【系统指令】你刚才的回复只有思考内容而没有正式回答。请立刻输出完整的正式答案，不要再沉默。\n"
                            continue
                        }
                    }

                    // ===== 死循环/重复检测：文字输出重复（对齐 Operit：静默纠偏或停止）=====
                    val currentTextSig = finalText.hashCode()
                    if (currentTextSig == lastTextSignature && finalText.length > 20) {
                        consecutiveTextRepeatCount++
                        Log.w(TAG, "streamText: 检测到连续重复文字输出 ($consecutiveTextRepeatCount 次)")
                        if (consecutiveTextRepeatCount >= 2) {
                            // 连续重复 2 次，判定为死循环，直接终止
                            Log.e(TAG, "streamText: 文字输出死循环，强制终止")
                            break
                        }
                        // 静默注入纠偏提示（用户无感知）
                        forcePrompt = (forcePrompt ?: "") + "\n\n【系统警告】你刚才的回复内容与上一次几乎完全相同，这是不允许的。请不要再重复刚才的回复。如果任务已经完成就直接结束；如果遇到困难，请尝试换一种思路或直接向用户说明情况。"
                        continue
                    } else {
                        consecutiveTextRepeatCount = 0
                        lastTextSignature = currentTextSig
                    }

                    // 无工具调用，正常结束
                    break
                }
 
                // Check for tools that need approval
                var hasPendingApproval = false
                val updatedTools = tools.map { tool ->
                    val toolDef = toolsInternal.find { it.name == tool.toolName }
                    when {
                        // Auto-approve everything (lazy mode) -> skip approval
                        settings.autoApproveAllTools -> tool

                        // Tool needs approval (or global force confirm) and state is Auto -> set to Pending
                        (settings.forceConfirmToolCalls || toolDef?.needsApproval == true) && tool.approvalState is ToolApprovalState.Auto -> {
                            hasPendingApproval = true
                            tool.copy(approvalState = ToolApprovalState.Pending)
                        }
                        // State is Pending -> keep waiting
                        tool.approvalState is ToolApprovalState.Pending -> {
                            hasPendingApproval = true
                            tool
                        }

                        else -> tool
                    }
                }
 
                // If any tools were updated to Pending, update the message and break
                if (updatedTools != tools) {
                    val lastMessage = messages.last()
                    val updatedParts = lastMessage.parts.map { part ->
                        if (part is UIMessagePart.Tool) {
                            updatedTools.find { it.toolCallId == part.toolCallId } ?: part
                        } else {
                            part
                        }
                    }
                    messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
                    emit(GenerationChunk.Messages(messages))
                }
 
                // If there are pending approvals, break and wait for user
                if (hasPendingApproval) {
                    Log.i(TAG, "generateText: waiting for tool approval")
                    break
                }
 
                toolsToProcess = updatedTools
            } else {
                // Resuming after user interaction - use the resumable tools directly.
                Log.i(TAG, "generateText: resuming with ${pendingTools.size} resumable tools")
                toolsToProcess = messages.last().getTools().filter { it.canResumeExecution }
            }
 
            // Handle tools (execute approved tools, handle denied tools)
            // 使用 Operit 风格的 ToolExecutionManager 统一处理工具执行
            val executedTools = arrayListOf<UIMessagePart.Tool>()
            
            // 根据 CLI 模式决定是否过滤工具
            val toolExposureMode = "FULL"
            
            toolsToProcess.forEach { tool ->
                // 协程取消检查：用户点取消后能及时中断工具执行循环
                coroutineContext.ensureActive()
                when (tool.approvalState) {
                    is ToolApprovalState.Denied -> {
                        // Tool was denied by user
                        val reason = (tool.approvalState as ToolApprovalState.Denied).reason
                        executedTools += tool.copy(
                            output = listOf(
                                UIMessagePart.Text(
                                    json.encodeToString(
                                        buildJsonObject {
                                            put(
                                                "error",
                                                JsonPrimitive("Tool execution denied by user. Reason: ${reason.ifBlank { "No reason provided" }}")
                                            )
                                        }
                                    )
                                )
                            )
                        )
                    }

                    is ToolApprovalState.Answered -> {
                        // Tool was answered by user (e.g., ask_user tool)
                        val answer = (tool.approvalState as ToolApprovalState.Answered).answer
                        executedTools += tool.copy(
                            output = listOf(
                                UIMessagePart.Text(answer)
                            )
                        )
                    }

                    is ToolApprovalState.Pending -> {
                        // Should not reach here, but just in case
                    }

                    else -> {
                        // Auto or Approved - execute the tool via ToolExecutionManager
                        runCatching {
                            val toolDef = toolsInternal.find { toolDef -> toolDef.name == tool.toolName }
                                ?: error("Tool ${tool.toolName} not found in ${toolsInternal.map { it.name }}")
                            Log.i(TAG, "generateText: executing tool ${toolDef.name}")
                            coroutineContext.ensureActive()
                            
                            // 使用 ToolExecutionManager 执行工具
                            val invocation = me.rerere.rikkahub.core.tools.ToolInvocation(
                                tool = toolDef,
                                rawText = tool.input.ifBlank { "{}" },
                            )
                            val result = me.rerere.rikkahub.core.tools.ToolExecutionManager.executeInvocation(invocation)
                            
                            coroutineContext.ensureActive()
                            
                            // 将 ToolResult 转换为 UIMessagePart
                            val outputParts = if (result.success) {
                                listOf(UIMessagePart.Text(result.result))
                            } else {
                                listOf(UIMessagePart.Text(
                                    json.encodeToString(buildJsonObject {
                                        put("error", JsonPrimitive(result.error ?: "Unknown error"))
                                    })
                                ))
                            }
                            executedTools += tool.copy(output = outputParts)
                        }.onFailure {
                            Log.e(TAG, "Tool execution failed", it)
                            executedTools += tool.copy(
                                output = listOf(
                                    UIMessagePart.Text(
                                        json.encodeToString(
                                            buildJsonObject {
                                                put(
                                                    "error",
                                                    JsonPrimitive(buildString {
                                                        append("[${it.javaClass.name}] ${it.message}")
                                                        append("\n${it.stackTraceToString()}")
                                                    })
                                                )
                                            }
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
            }
 
            if (executedTools.isEmpty()) {
                // No results to add (all tools were pending)
                break
            }
 
            // Update last message with executed tools (NOT create TOOL message)
            val lastMessage = messages.last()
            val updatedParts = lastMessage.parts.map { part ->
                if (part is UIMessagePart.Tool) {
                    executedTools.find { it.toolCallId == part.toolCallId } ?: part
                } else part
            }
            messages = messages.dropLast(1) + lastMessage.copy(parts = updatedParts)
            emit(
                GenerationChunk.Messages(
                    messages.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant,
                        settings = settings
                    )
                )
            )
        }
 
    }.throttleLatest(STREAM_UI_THROTTLE_MS)
        .flowOn(Dispatchers.IO)
 
    private suspend fun generateInternal(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        pluginPromptInjections: List<String> = emptyList(),
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
        transformers: List<MessageTransformer>,
        model: Model,
        providerImpl: Provider<ProviderSetting>,
        provider: ProviderSetting,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        stream: Boolean,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
        conversationSystemPrompt: String? = null,
        workspaceCwd: String? = null,
        /** 内部强制指令（仅进 system、不进 messages，源头不可见）——用于正文空白自动补全等静默催答 */
        internalForcePrompt: String? = null,
    ) {
        val internalMessages = buildList {
            val system = buildString {
                val effectiveSystemPrompt =
                    if (assistant.allowConversationSystemPrompt && !conversationSystemPrompt.isNullOrBlank()) {
                        conversationSystemPrompt
                    } else {
                        assistant.systemPrompt
                    }
                if (effectiveSystemPrompt.isNotBlank()) {
                    append(effectiveSystemPrompt)
                }

                // 全局强制简体中文：所有模型一律用简体中文回答，且思考链也使用简体中文
                appendLine()
                append("重要指令：请始终使用简体中文回答。你的所有输出（包括思考过程、推理、思考链、代码注释）都必须使用简体中文，除非用户明确要求输出其他语言。")

                // ===== 用户技术水平自适应：小白用户静默使用工具 + 大白话交互 =====
                runCatching {
                    if (me.rerere.rikkahub.data.ai.tools.UserHelpLevel.isBeginner(messages)) {
                        appendLine()
                        appendLine("这位用户是新手（小白），请按以下方式交流：")
                        appendLine("1. 全程用简单易懂的大白话，一次只问一个问题，不要堆术语。")
                        appendLine("2. 不要跟用户讨论“技能”“方法论”“use_skill”“TDD”等概念；这些由你在后台自动处理，不要主动介绍或让用户选择。")
                        appendLine("3. 你在后台自动使用专业方法做事（先确认需求→拆解→动手→验证），但只向用户呈现结果和步骤，用“我帮你做了什么、下一步建议做什么”这样的说法。")
                        appendLine("4. 用户求助时，直接给成品/直接动手，不要反问用户不懂的技术问题；只在必要处问“你想要哪种结果/什么时候要”这类简单问题。")
                    }
                }

                // 技能主动引导（常驻系统提示）：让模型知道有哪些可用技能并主动调用
                if (assistant.enabledSkills.isNotEmpty()) {
                    runCatching {
                        val skillManager: me.rerere.rikkahub.data.files.SkillManager = org.koin.java.KoinJavaComponent.getKoin().get()
                        val allSkills = skillManager.listSkillsSync()
                        val visible = allSkills.filter { it.name in assistant.enabledSkills && !it.disableModelInvocation }
                        if (visible.isNotEmpty()) {
                            appendLine()
                            appendLine("## 可用技能（主动使用）")
                            appendLine("你拥有以下专用技能。当用户的请求匹配某技能的能力时，你必须通过 `use_skill` 工具加载并遵循其指令，而不要只用你的通用常识应付。判断标准：请求主题与技能描述场景重合即应使用。")
                            appendLine("<available_skills>")
                            visible.forEach { skill ->
                                appendLine("  <skill>")
                                appendLine("    <name>${skill.name}</name>")
                                appendLine("    <description>${skill.description}</description>")
                                appendLine("  </skill>")
                            }
                            appendLine("</available_skills>")
                            appendLine("命中技能时调用 use_skill(name=...) 加载指令并按其方法论作答；若确实无技能匹配则正常回答，不要强行套用。")
                        }
                    }
                }

                // 内部强制催答（静默，用户不可见）：上一轮只思考没正文时，强制本轮直接输出完整答案
                if (!internalForcePrompt.isNullOrBlank()) {
                    appendLine()
                    append(internalForcePrompt)
                }

                // 表情包渲染：只要检测到表情目录里有图，就自动注入可用表情名提示（零配置）
                runCatching {
                    val hasStickers = me.rerere.rikkahub.data.ai.transformers.StickerRenderTransformer.hasStickers()
                    if (hasStickers) {
                        val prompt = me.rerere.rikkahub.data.ai.transformers.StickerRenderTransformer.buildPrompt()
                        if (prompt.isNotBlank()) {
                            appendLine()
                            append(prompt.trimEnd())
                        }
                    }
                }

                // 记忆
                if (assistant.enableMemory) {
                    appendLine()
                    val lastUserText = messages.lastOrNull { it.role == MessageRole.USER }?.toText()?.take(300)
                    append(buildMemoryPrompt(memories = memories, userMessage = lastUserText))
                }
 
                // 外置记忆库召回
                try {
                    val externalMemoryConfigs = settings.externalMemories.filter {
                        it.enabled && it.id in assistant.externalMemoryIds
                    }
                    externalMemoryConfigs.forEach { config ->
                        Log.i(TAG, "ExternalMemory config: name=${config.name}, url=${config.supabaseUrl}, table=${config.tableName}, summaryTable=${config.summariesTableName}, embeddingModelId=${config.embeddingModelId}, autoSaveDiarySummary=${config.autoSaveDiarySummary}")
                    }
                    if (externalMemoryConfigs.isNotEmpty()) {
                        val lastUserMessage = messages.lastOrNull { it.role == MessageRole.USER }
                        val queryText = lastUserMessage?.toText()?.take(200)?.trim() ?: ""
                        // 并发检索所有外置记忆库配置，每个配置最多 8 秒超时
                        val allRecalled = coroutineScope {
                            externalMemoryConfigs.map { config ->
                                async {
                                    withTimeoutOrNull(8.seconds) {
                                        runCatching {
                                            val service = me.rerere.rikkahub.data.service.ExternalMemoryService(config)
                                            val recalled = mutableListOf<String>()

                            // 如果配置了向量模型且开启了日记摘要，使用向量召回日记摘要
                            if (config.embeddingModelId != null && queryText.isNotBlank() && config.autoSaveDiarySummary) {
                                                val embeddingModel = settings.findModelById(config.embeddingModelId)
                                                if (embeddingModel != null) {
                                                    val embeddingProvider = embeddingModel.findProvider(settings.providers)
                                                    if (embeddingProvider != null) {
                                                        val embeddingProviderImpl = providerManager.getProviderByType(embeddingProvider)
                                                        val embedResult = embeddingProviderImpl.generateEmbedding(
                                                            providerSetting = embeddingProvider,
                                                            params = EmbeddingGenerationParams(
                                                                model = embeddingModel,
                                                                input = listOf(queryText),
                                                            )
                                                        )
                                                        val queryEmbedding = embedResult.embeddings.firstOrNull()
                                                        if (queryEmbedding != null) {
                                                            val recalledSummaries = service.vectorRecallSummaries(
                                                                queryEmbedding = queryEmbedding,
                                                                assistantId = assistant.id.toString(),
                                                                count = config.recallCount,
                                                            ).getOrDefault(emptyList())
                                                            recalledSummaries.forEach { summary ->
                                                                recalled.add(summary.content)
                                                            }
                                                            Log.d(TAG, "Vector recall ${recalledSummaries.size} summaries from ${config.name}")
                                                        }
                                                    }
                                                }
                                            } else {
                                                // 回退：文本召回聊天记录
                                                val recalledMessages = if (queryText.isNotBlank()) {
                                                    service.searchMessages(
                                                        assistantId = assistant.id.toString(),
                                                        keyword = queryText,
                                                        limit = config.recallCount,
                                                    ).getOrDefault(emptyList())
                                                } else {
                                                    service.queryLatestMessages(
                                                        assistantId = assistant.id.toString(),
                                                        limit = config.recallCount,
                                                    ).getOrDefault(emptyList())
                                                }
                                                recalledMessages.forEach { msg ->
                                                    val prefix = when (msg.role) {
                                                        "assistant" -> "AI"
                                                        "user" -> "用户"
                                                        else -> msg.role
                                                    }
                                                    recalled.add("[$prefix] ${msg.content}")
                                                }
                                            }
                                            recalled
                                        }.onFailure {
                                            Log.w(TAG, "External memory recall failed for ${config.name}", it)
                                        }.getOrNull()
                                    } ?: run {
                                        Log.w(TAG, "External memory recall timed out for ${config.name}")
                                        null
                                    }
                                }
                            }.awaitAll()
                                .filterNotNull()
                                .flatten()
                        }
                        if (allRecalled.isNotEmpty()) {
                            appendLine()
                            appendLine("## 外置记忆库")
                            allRecalled.reversed().forEachIndexed { index, memory ->
                                appendLine("${index + 1}. ${memory}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "External memory recall failed", e)
                }
 
                if (assistant.enableRecentChatsReference) {
                    appendLine()
                    append(buildRecentChatsPrompt(assistant, conversationRepo))
                }
 
                // 代码文件命名和ZIP打包功能说明（弱模型跳过，避免指令被原样回显）
                if (!isWeakModel(model, provider)) {
                    appendLine()
                    append(buildCodeBlockPrompt())
                }
 
 
                // 插件提示词注入
                if (pluginPromptInjections.isNotEmpty()) {
                    pluginPromptInjections.forEach { injection ->
                        appendLine()
                        appendLine()
                        append(injection)
                    }
                }
 
                // 允许跳过回复
                // 弱模型跳过复杂规则（理解不了）
                if (assistant.allowSkipReply && !isWeakModel(model, provider)) {
                    appendLine()
                    appendLine()
                    appendLine("## Skip Reply")
                    appendLine("If you determine that no reply is needed (e.g., the user's message doesn't require a response, or you have nothing meaningful to add), you may reply with exactly `[SKIP]` (without any other text). This message will be hidden from the user. Use this sparingly and only when truly appropriate.")
                }

                // 屏幕跳转能力（AI总是可以跳转，不需要开关）；弱模型跳过（理解不了）
                if (!isWeakModel(model, provider)) {
                    appendLine()
                    appendLine()
                    appendLine("## 屏幕跳转能力")
                    appendLine("你可以在回复末尾追加 [JUMP] 标记（单独一行）来把聊天界面拉到用户屏幕最前面。")
                    appendLine("适用场景：")
                    appendLine("- 用户说要去别的应用，你觉得需要把用户拉回来时")
                    appendLine("- 你觉得接下来的内容需要用户立即看到时")
                    appendLine("不适用场景：")
                    appendLine("- 一般闲聊不需要跳转")
                    appendLine("- 用户正在跟你正常对话时不需要跳转")
                    appendLine("[JUMP] 标记不会展示给用户，仅用于触发屏幕跳转。")
                }
 
                // 分气泡: 告知模型它自己能控制消息如何被拆成多个气泡；弱模型跳过
                if (assistant.splitBubbleByLine && !isWeakModel(model, provider)) {
                    appendLine()
                    appendLine()
                    appendLine("## Message Bubbles")
                    appendLine("Your reply will be automatically split into separate chat bubbles at every line break (\\n) you write, similar to how a person sends several short texts in a row instead of one long message. You are fully in control of this: write a line break whenever you want the previous thought/sentence to appear as its own bubble, and keep things on the same line when they belong together. Do not insert blank lines purely for spacing — every line break becomes a new bubble, so use them intentionally. Exception: line breaks inside fenced code blocks (```) and Markdown tables are preserved as-is and will NOT create new bubbles, since those must stay intact as a single block.")
                }
 
            }
            if (system.isNotBlank()) add(UIMessage.system(prompt = system))
            addAll(messages.limitContext(assistant.contextMessageSize))
        }.transforms(
            transformers = transformers,
            context = context,
            model = model,
            assistant = assistant,
            settings = settings,
            processingStatus = processingStatus,
            workspaceCwd = workspaceCwd,
        )
 
        var messages: List<UIMessage> = messages
        val params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            tools = tools,
            // 强制推理：任何模型都启用推理（OFF 强制转为 AUTO），
            // 原生不支持推理的模型自动走提示词式推理
            reasoningLevel = if (assistant.reasoningLevel == me.rerere.ai.core.ReasoningLevel.OFF) {
                me.rerere.ai.core.ReasoningLevel.AUTO
            } else {
                assistant.reasoningLevel
            },
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(model.customBodies)
            }
        )
        if (stream) {
            aiLoggingManager.addLog(
                AILogging.Generation(
                    params = params,
                    messages = messages,
                    providerSetting = provider,
                    stream = true
                )
            )
            // 生成超时保护：单次流式生成超过 5 分钟强制终止，避免无限卡死
            withTimeoutOrNull(5 * 60 * 1000L) {
                providerImpl.streamText(
                    providerSetting = provider,
                    messages = internalMessages,
                    params = params
                ).collect {
                    messages = messages.handleMessageChunk(chunk = it, model = model)
                    it.usage?.let { usage ->
                        messages = messages.mapIndexed { index, message ->
                            if (index == messages.lastIndex) {
                                message.copy(usage = message.usage.merge(usage))
                            } else {
                                message
                            }
                        }
                    }
                    onUpdateMessages(messages)
                }
            } ?: run {
                Log.w(TAG, "Generation timed out after 5 min, aborting stream")
                // 超时终止：注入一条被中断说明到消息尾部，避免无响应
            }
        } else {
            aiLoggingManager.addLog(
                AILogging.Generation(
                    params = params,
                    messages = messages,
                    providerSetting = provider,
                    stream = false
                )
            )
            val chunk = providerImpl.generateText(
                providerSetting = provider,
                messages = internalMessages,
                params = params,
            )
            messages = messages.handleMessageChunk(chunk = chunk, model = model)
            chunk.usage?.let { usage ->
                messages = messages.mapIndexed { index, message ->
                    if (index == messages.lastIndex) {
                        message.copy(
                            usage = message.usage.merge(usage)
                        )
                    } else {
                        message
                    }
                }
            }
            onUpdateMessages(messages)
        }
    }
 
    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: Locale,
        onStreamUpdate: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val model = settings.providers.findModelById(settings.translateModeId)
            ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")
 
        val providerHandler = providerManager.getProviderByType(provider)
 
        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            // Use regular translation with prompt
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguage.toString(),
            )
 
            var messages = listOf(UIMessage.user(prompt))
            var translatedText = ""
 
            providerHandler.streamText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
                ),
            ).collect { chunk ->
                messages = messages.handleMessageChunk(chunk)
                translatedText = messages.lastOrNull()?.toText() ?: ""
 
                if (translatedText.isNotBlank()) {
                    onStreamUpdate?.invoke(translatedText)
                    emit(translatedText)
                }
            }
        } else {
            // Use Qwen MT model with special translation options
            val messages = listOf(UIMessage.user(sourceText))
            val chunk = providerHandler.generateText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    topP = 0.95f,
                    customBody = listOf(
                        CustomBody(
                            key = "translation_options",
                            value = buildJsonObject {
                                put("source_lang", JsonPrimitive("auto"))
                                put(
                                    "target_lang",
                                    JsonPrimitive(targetLanguage.getDisplayLanguage(Locale.ENGLISH))
                                )
                            }
                        )
                    )
                ),
            )
            val translatedText = chunk.choices.firstOrNull()?.message?.toText() ?: ""
 
            if (translatedText.isNotBlank()) {
                onStreamUpdate?.invoke(translatedText)
                emit(translatedText)
            }
        }
    }.flowOn(Dispatchers.IO)
}
 
/**
 * 把原始 Flow 的高频发射节流成"每 periodMillis 毫秒最多发一次最新值"。
 *
 * 实现方式：对上游调用 conflate()（只保留未被消费的最新一个值，中间值会被丢弃），
 * 然后在 collect 里每处理完一个值就 delay(periodMillis)。这样在上游快速连续发射时，
 * delay 期间产生的多个值会被 conflate 自动合并成"最新一个"，delay 结束后立刻拿到它；
 * 但由于用的是"发一个、等一段时间、再要下一个"的顺序结构，上游结束前最后一次真正的发射
 * 一定会被完整地 collect 到并 emit 出去，不会像 sample() 那样有丢失最终值的风险。
 *
 * 用于把 AI 流式输出的高频消息更新（可能每秒几十次）降频到 UI 友好的节奏，从源头
 * 消除打字机效果的抖动/掉帧，同时保证生成结束时 UI 一定能拿到完整的最终内容。
 */
private fun <T> Flow<T>.throttleLatest(periodMillis: Long): Flow<T> {
    val upstream = this
    return flow {
        upstream.conflate().collect { value ->
            emit(value)
            delay(periodMillis)
        }
    }
}
 
/**
 * 构建代码块提示 - 告知AI代码文件命名和ZIP打包功能
 */
/**
 * 判断是否为弱模型：精简系统提示，避免指令回显。
 * 识别范围：模型名含 lite/mini/nano/tiny/small/light/compact + 弱模型服务 host。
 * 注意：不误伤 flash/turbo 等实际较强的模型。
 */
internal fun isWeakModel(model: Model, provider: ProviderSetting): Boolean {
    val id = model.modelId.lowercase()
    val host = (provider as? me.rerere.ai.provider.ProviderSetting.OpenAI)?.baseUrl?.lowercase() ?: ""
    // 弱模型名：各种 lite/mini/nano/tiny/small 等轻量模型（不误伤 flash/turbo 等强模型）
    val weakName = listOf(
        "lite", "mini", "nano", "tiny", "small", "light", "compact", "micro",
        "1.5-nano", "4-mini", "qwen-turbo", "moonshot-lite", "glm-lite",
    ).any { id.contains(it) }
    // 弱模型服务 host：免费/受限服务
    val weakHost = listOf(
        "pollinations", "free", "deepinfra", "atlas", "opencode", "groq",
    ).any { host.contains(it) }
    return weakName || weakHost
}

private fun buildCodeBlockPrompt(): String = buildString {
    appendLine("## Code Block Rules (MUST FOLLOW)")
    appendLine()
    appendLine("1. **ALWAYS name code blocks with filenames**: You MUST use the actual filename as the code block language tag instead of just the language name. This is critical for proper file saving and syntax highlighting. Examples:")
    appendLine("   - ✅ Correct: ```MainActivity.kt instead of ```kotlin")
    appendLine("   - ✅ Correct: ```index.html instead of ```html")
    appendLine("   - ✅ Correct: ```styles.css instead of ```css")
    appendLine("   - ✅ Correct: ```package.json instead of ```json")
    appendLine("   - ✅ Correct: ```manifest.xml instead of ```xml")
    appendLine("   - ✅ Correct: ```main.py instead of ```python")
    appendLine("   - ✅ Correct: ```App.vue instead of ```vue")
    appendLine("   - ❌ Wrong: ```kotlin, ```python, ```javascript (these don't provide filenames)")
    appendLine("   - For code without a specific filename, use a descriptive name like ```example.ts, ```helper.py")
    appendLine()
    appendLine("2. **ZIP Download via `write_files` tool**: Users can download code files as a ZIP ONLY when you call this tool.")
    appendLine("   - **Full write** (first time / new files): `{\"zip_name\":\"project.zip\",\"files\":[{\"name\":\"MainActivity.kt\",\"content\":\"...\"}]}`")
    appendLine("   - **Incremental edit** (saves tokens! For modifying existing files): `{\"zip_name\":\"project-v2.zip\",\"base_files\":\"previous\",\"edits\":[{\"name\":\"MainActivity.kt\",\"search\":\"old code\",\"replace\":\"new code\"}]}`")
    appendLine("   - The `edits` mode applies search/replace to the files from your previous `write_files` call. Files not mentioned in `edits` keep their content unchanged.")
    appendLine("   - Always use actual filenames (e.g. `MainActivity.kt`) as code block language tags, not just language names (e.g. `kotlin`).")
}
 