/*
 * 灵犀 Lingxi
 * 参考自 Operit AI (https://github.com/AAswordman/Operit) 的 condition 工具
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * 条件判断工具（参考 Operit ConditionEvaluator）：
 * 让 AI 把"是否满足条件"交给确定性评估，避免在逻辑分支上幻觉。
 * 支持比较、包含、空值判断、正则匹配。
 */
private fun evaluateCondition(
    left: String,
    op: String,
    right: String?,
): Boolean = when (op) {
    "==" -> left == (right ?: "")
    "!=" -> left != (right ?: "")
    "contains" -> left.contains(right ?: "")
    "!contains" -> !left.contains(right ?: "")
    "startswith" -> left.startsWith(right ?: "")
    "endswith" -> left.endsWith(right ?: "")
    "empty" -> left.isBlank()
    "!empty" -> left.isNotBlank()
    "regex" -> runCatching { Regex(right ?: "").containsMatchIn(left) }.getOrDefault(false)
    "lt" -> (left.toDoubleOrNull() ?: 0.0) < (right?.toDoubleOrNull() ?: 0.0)
    "lte" -> (left.toDoubleOrNull() ?: 0.0) <= (right?.toDoubleOrNull() ?: 0.0)
    "gt" -> (left.toDoubleOrNull() ?: 0.0) > (right?.toDoubleOrNull() ?: 0.0)
    "gte" -> (left.toDoubleOrNull() ?: 0.0) >= (right?.toDoubleOrNull() ?: 0.0)
    else -> false
}

fun createConditionTool(): Tool = Tool(
    name = "condition_check",
    description = "Deterministically evaluate a condition between a value and an expected state. " +
        "Use to ground logical branches in the tool rather than guessing. " +
        "Ops: ==, !=, contains, !contains, startswith, endswith, empty, !empty, regex, lt, lte, gt, gte.",
    needsApproval = false,
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("left", buildJsonObject {
                    put("type", "string")
                    put("description", "The actual value to test (e.g. a tool result, file content, user input)")
                })
                put("op", buildJsonObject {
                    put("type", "string")
                    put(
                        "enum",
                        buildJsonArray {
                            add("=="); add("!="); add("contains"); add("!contains")
                            add("startswith"); add("endswith"); add("empty"); add("!empty")
                            add("regex"); add("lt"); add("lte"); add("gt"); add("gte")
                        }
                    )
                    put("description", "The comparison operator")
                })
                put("right", buildJsonObject {
                    put("type", "string")
                    put("description", "The expected value (for boolean-only ops like 'empty', may be empty)")
                })
            },
            required = listOf("left", "op"),
        )
    },
    execute = { input ->
        val left = input.jsonObject["left"]?.jsonPrimitive?.contentOrNull ?: ""
        val op = input.jsonObject["op"]?.jsonPrimitive?.contentOrNull
            ?: return@Tool listOf(UIMessagePart.Text("""{"error":"op required"}"""))
        val right = input.jsonObject["right"]?.jsonPrimitive?.contentOrNull
        val result = evaluateCondition(left, op, right)
        listOf(UIMessagePart.Text(buildJsonObject {
            put("condition", "$left $op ${right ?: ""}")
            put("result", result)
        }.toString()))
    },
)