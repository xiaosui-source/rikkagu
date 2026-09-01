/*
 * 灵犀 Lingxi
 * 参考自 Operit AI (https://github.com/AAswordman/Operit) 的 calculator 工具
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.calculator.JsCalculator

/**
 * 计算器工具（参考 Operit calculator）：AI 可精确计算数学表达式，避免数学幻觉。
 * 支持四则运算、指数、Math 函数、单位换算、日期计算、统计函数等（JS 风格语法树求值）。
 */
fun createCalculatorTool(): Tool = Tool(
    name = "calculate",
    description = "Accurately evaluate a mathematical expression and return the exact result. " +
        "Use for arithmetic, algebra, trig, unit conversion, date math, statistics. " +
        "Supports JS-style syntax: + - * / ** % ( ), Math.sin/Math.cos/Math.sqrt, " +
        "convert(value,'from','to') for unit conversion, now()/today()/date() for dates, " +
        "stats.mean/stats.sum/stats.stdev. Answer the user with the result.",
    needsApproval = false,
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("expression", buildJsonObject {
                    put("type", "string")
                    put("description", "The mathematical expression to evaluate, e.g. '2+3*4' or 'convert(100,'f','c')' or 'Math.sqrt(144)'")
                })
            },
            required = listOf("expression"),
        )
    },
    execute = { input ->
        val expr = input.jsonObject["expression"]?.jsonPrimitive?.contentOrNull
            ?: return@Tool listOf(UIMessagePart.Text("""{"error":"expression required"}"""))
        try {
            val result = JsCalculator.calc(expr)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("expression", expr)
                put("result", result)
            }.toString()))
        } catch (e: Exception) {
            listOf(UIMessagePart.Text(buildJsonObject {
                put("error", "计算失败: ${e.message}")
                put("expression", expr)
            }.toString()))
        }
    },
)