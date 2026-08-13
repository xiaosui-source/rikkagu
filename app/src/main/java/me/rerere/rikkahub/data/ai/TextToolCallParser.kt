/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai

import kotlin.text.RegexOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart

/**
 * 将模型以**纯文本**形式输出的工具调用（例如：
 *   <invoke name="workspace_shell"><parameter name="command">ls</parameter></invoke>
 * 或
 *   <invoke tool_name="python_exec"><parameter name="code">print(1)</parameter></invoke>
 * ）从 [UIMessagePart.Text] 中提取出来，转换为结构化的 [UIMessagePart.Tool]，
 * 以便走统一的"正在执行工具"渲染与执行流程，而不是把原始 XML 代码展示给用户。
 *
 * 设计为通用解析器，不依赖任何特定供应商/模型：只要文本中出现成对的
 * 工具调用 XML 标签，就会被识别。可以覆盖所有接入了结构化 tool_call 的模型，
 * 在它们"退化"输出为文本 XML 时的场景。
 */
object TextToolCallParser {

    /**
     * 工具调用外层开始标签的正则。
     * 支持 <invoke ...> / <tool_call ...> / <use_tool ...> / <function_call ...> 等常见命名。
     * 属性需包含 name / tool_name / function。
     */
    private val TOOL_START =
        Regex("""<(invoke|tool_call|use_tool|function_call|tool)\b([^>]*)>""", RegexOption.IGNORE_CASE)

    private val PARAM =
        Regex("""<parameter\b([^>]*)>([\s\S]*?)</parameter>""", RegexOption.IGNORE_CASE)
    private val PROP_ATTR = Regex("""(name|tool_name|function|tool)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ID_ATTR = Regex("""(id|tool_call_id|call_id)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /**
     * 扫描 parts 中的 Text，把其中包含的工具调用 XML 提取出来。
     *
     * @param allowedToolNames 允许被识别为工具的工具名集合；为空时不限制。
     *   传 null/空的（默认）会接收任意 name 的工具调用。
     * @return 一个新的 parts 列表：
     *  - 纯文本部分的剩余内容仍作为 Text 保留；
     *  - 被识别出的工具调用转换为 [UIMessagePart.Tool]；
     *  - 原有的 Tool/其他 part 原样保留。
     */
    fun extract(
        messages: List<UIMessagePart>,
        allowedToolNames: Set<String> = emptySet(),
    ): List<UIMessagePart> {
        val result = mutableListOf<UIMessagePart>()
        for (part in messages) {
            if (part is UIMessagePart.Text) {
                val (newText, tools) = extractFromText(part.text, allowedToolNames)
                if (newText.isNotBlank()) {
                    result += UIMessagePart.Text(newText, metadata = part.metadata)
                }
                result += tools
            } else {
                result += part
            }
        }
        return result
    }

    /**
     * 从一段文本中解析出工具调用。
     * @return (剩余纯文本, 解析出的工具列表)。剩余文本中对应 XML 部分会被移除。
     */
    fun extractFromText(
        text: String,
        allowedToolNames: Set<String> = emptySet(),
    ): Pair<String, List<UIMessagePart.Tool>> {
        if (text.isBlank()) {
            return text to emptyList()
        }
        // 无标准 XML 标签时：尝试多种兼容格式
        if (!TOOL_START.containsMatchIn(text)) {
            // ① <工具名>{"参数":...}</工具名> 格式（星火等输出）
            val tagJsonTools = parseTagJsonToolCalls(text, allowedToolNames)
            if (tagJsonTools.isNotEmpty()) {
                val cleaned = TAG_JSON_TOOL.replace(text, "").trim()
                return cleaned to tagJsonTools
            }
            // ② 裸 JSON 工具调用 {"name":"xxx"}
            val jsonTools = parseJsonToolCalls(text, allowedToolNames)
            if (jsonTools.isNotEmpty()) {
                val cleaned = JSON_TOOL_CALL.replace(text, "").trim()
                return cleaned to jsonTools
            }
            return text to emptyList()
        }

        val tools = mutableListOf<UIMessagePart.Tool>()
        val cleaned = StringBuilder()
        var cursor = 0
        var match = TOOL_START.find(text)

        while (match != null) {
            // 追加 start 之前的纯文本
            cleaned.append(text, cursor, match.range.first)

            val tagStart = match.range.first
            val attrs = match.groupValues[2]

            // 读取工具名 / id
            val toolName = PROP_ATTR.find(attrs)?.groupValues?.get(2)?.trim() ?: ""
            val toolCallId = ID_ATTR.find(attrs)?.groupValues?.get(2)?.trim()
                ?: "text-tool-${tagStart}-${kotlin.random.Random.nextInt(1000000)}"

            // 找到对应的结束标签 </invoke> 等
            val endTag = "</${match.groupValues[1]}>"
            val endIdx = text.indexOf(endTag, match.range.last + 1, ignoreCase = true)

            // 解析参数（在开始标签后的 body 内）
            val bodyStart = match.range.last + 1
            val bodyEnd = if (endIdx >= 0) endIdx else text.length

            val params = mutableMapOf<String, String>()
            var pm = PARAM.find(text, bodyStart)
            while (pm != null && pm.range.first < bodyEnd) {
                val pName = jackAttrName(pm.groupValues[1])
                if (pName != null) {
                    params[pName] = pm.groupValues[2].trim()
                }
                pm = PARAM.find(text, pm.range.last + 1)
            }

            // 有空 name 或没有参数时, 若找不到 name 则跳过 (不误伤普通文本)
            if (toolName.isNotBlank() &&
                (allowedToolNames.isEmpty() || toolName in allowedToolNames)
            ) {
                val input = buildJsonObject {
                    params.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                }
                tools += UIMessagePart.Tool(
                    toolCallId = toolCallId,
                    toolName = toolName,
                    input = input.toString(),
                    output = emptyList(),
                    approvalState = me.rerere.ai.ui.ToolApprovalState.Auto,
                )
            }

            // 跳过整个工具调用块
            cursor = if (endIdx >= 0) endIdx + endTag.length else match.range.last + 1
            match = TOOL_START.find(text, cursor)
        }

        // 追加最后的剩余文本
        cleaned.append(text, cursor, text.length)
        return cleaned.toString().trim() to tools
    }

    /** <工具名>{"参数":...} 格式正则（星火等弱模型输出，可无闭合标签） */
    private val TAG_JSON_TOOL = Regex("""<([a-z_][a-z0-9_]*)\s*>\s*(\{[^}]*\})""", RegexOption.IGNORE_CASE)

    /** 解析 <工具名>{"参数":...}</工具名> 格式的工具调用 */
    private fun parseTagJsonToolCalls(text: String, allowedToolNames: Set<String>): List<UIMessagePart.Tool> {
        val tools = mutableListOf<UIMessagePart.Tool>()
        TAG_JSON_TOOL.findAll(text).forEach { m ->
            runCatching {
                val tagName = m.groupValues[1].trim()
                val matchedName = resolveToolName(tagName, allowedToolNames)
                if (matchedName != null) {
                    val bodyJson = m.groupValues[2]
                    val argsObj = runCatching {
                        Json.parseToJsonElement(bodyJson).jsonObject
                    }.getOrElse { JsonObject(emptyMap()) }
                    val input = buildJsonObject {
                        argsObj.forEach { (k, v) -> put(k, v) }
                    }.toString()
                    tools += UIMessagePart.Tool(
                        toolCallId = "text-tool-${m.range.first}-${kotlin.random.Random.nextInt(1000000)}",
                        toolName = matchedName,
                        input = input,
                        output = emptyList(),
                        approvalState = me.rerere.ai.ui.ToolApprovalState.Auto,
                    )
                }
            }
        }
        return tools
    }

    /** 裸 JSON 工具调用正则：{"name":"xxx","arguments":{...}} 或 {"name":"xxx"} */
    private val JSON_TOOL_CALL = Regex("""\{"name"\s*:\s*"[^"]*"\s*(?:,\s*"arguments"\s*:\s*\{[^}]*\})?\}""")

    /** 解析裸 JSON 形式的工具调用（兼容弱模型/提示词式输出的非标准格式） */
    private fun parseJsonToolCalls(text: String, allowedToolNames: Set<String>): List<UIMessagePart.Tool> {
        val tools = mutableListOf<UIMessagePart.Tool>()
        JSON_TOOL_CALL.findAll(text).forEach { m ->
            runCatching {
                val obj = Json.parseToJsonElement(m.value).jsonObject
                val rawName = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
                val matchedName = resolveToolName(rawName, allowedToolNames)
                if (matchedName != null) {
                    val argsObj: JsonObject = obj["arguments"]?.jsonObject ?: JsonObject(emptyMap())
                    val input = buildJsonObject {
                        argsObj.forEach { (k, v) -> put(k, v) }
                    }.toString()
                    tools += UIMessagePart.Tool(
                        toolCallId = "text-tool-${m.range.first}-${kotlin.random.Random.nextInt(1000000)}",
                        toolName = matchedName,
                        input = input,
                        output = emptyList(),
                        approvalState = me.rerere.ai.ui.ToolApprovalState.Auto,
                    )
                }
            }
        }
        return tools
    }

    /** 工具名模糊匹配：精确→忽略大小写→中文映射（抖音→douyin）→包含匹配 */
    private fun resolveToolName(raw: String, allowed: Set<String>): String? {
        if (allowed.isEmpty()) return raw
        if (raw in allowed) return raw
        allowed.firstOrNull { it.equals(raw, ignoreCase = true) }?.let { return it }
        // 中文工具名映射：抖音→douyin 等常见映射（覆盖常见工具名）
        val normalized = raw
            .replace("抖音", "douyin")
            .replace("文件", "file")
            .replace("记忆", "memory")
            .replace("视频详情", "video_detail")
            .replace("用户主页", "user_profile")
            .replace("热搜", "hot_search")
            .replace("推荐", "feed")
            .replace("视频", "video")
            .replace("搜索", "search")
            .replace("用户", "user")
            .replace("登录", "login")
            .replace("评论", "comment")
            .replace("点赞", "like")
            .replace("发布", "publish")
        allowed.firstOrNull { it.equals(normalized, ignoreCase = true) }?.let { return it }
        // 包含匹配（允许一方包含另一方，忽略下划线/大小写）
        allowed.firstOrNull { a ->
            val aN = a.lowercase().replace("_", "")
            val rN = raw.lowercase().replace("_", "")
            aN.contains(rN) || rN.contains(aN)
        }?.let { return it }
        return null
    }

    private fun jackAttrName(attrText: String): String? {
        return Regex("""(?:name|key|param|argument)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(attrText)?.groupValues?.get(1)?.trim()
    }
}