/*
 * 灵犀 Lingxi
 * 参考自 Operit AI (https://github.com/AAswordman/Operit) 的 tasker / intent 集成
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * 外部集成工具（参考 Operit integrations）：
 * - tasker_task: 通过 Tasker 广播触发 Tasker 任务（需用户授权 Tasker permissive）
 * - open_intent: 解析并执行一个 Android Intent（如打开 URL、拨号、启动应用）
 * 全部 rikkahub 原生实现。
 */
fun createIntegrationTools(context: Context): List<Tool> = listOf(
    // ===== Tasker 任务触发 =====
    Tool(
        name = "tasker_run",
        description = "Trigger a Tasker task via Tasker's intent receiver. " +
            "Requires the user to enable Tasker permission for this app. " +
            "Use when the user asks to run an automation task defined in Tasker.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("task_name", buildJsonObject {
                        put("type", "string")
                        put("description", "The Tasker task name to run")
                    })
                },
                required = listOf("task_name"),
            )
        },
        execute = { input ->
            val taskName = input.jsonObject["task_name"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"task_name required"}"""))
            val success = runCatching {
                val intent = Intent("net.dinglisch.android.tasker.TASKER").apply {
                    putExtra("task_name", taskName)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                context.sendBroadcast(intent)
                true
            }.getOrElse { false }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("task", taskName)
                put("success", success)
                put("tip", if (success) "已发送 Tasker 广播（需 Tasker 开启外部访问权限）" else "发送失败：确认 Tasker 已安装且对本应用开放权限")
            }.toString()))
        }
    ),

    // ===== Intent 解析执行 =====
    Tool(
        name = "open_intent",
        description = "Execute an Android Intent by action + optional uri. " +
            "Examples: VIEW a URL, DIAL a phone number, OPEN_APP launch a package, SEND a share. " +
            "Use to open links, apps, dial, or share on the user's device.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("description", "Intent action: VIEW / DIAL / SEND / OPEN_APP")
                    })
                    put("value", buildJsonObject {
                        put("type", "string")
                        put("description", "URI/package/text for the action (e.g. https://..., tel:..., package name, share text)")
                    })
                },
                required = listOf("action", "value"),
            )
        },
        execute = { input ->
            val action = input.jsonObject["action"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "VIEW"
            val value = input.jsonObject["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val result = runCatching {
                val intent = when (action) {
                    "VIEW" -> Intent(Intent.ACTION_VIEW, Uri.parse(value))
                    "DIAL" -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$value"))
                    "SEND" -> Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, value)
                    }
                    "OPEN_APP" -> context.packageManager.getLaunchIntentForPackage(value)?.let {
                        Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("package:$value") }
                    } ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$value"))
                    else -> Intent(Intent.ACTION_VIEW, Uri.parse(value))
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "OK"
            }.getOrElse { e ->
                LogWrapper.w("IntegrationTools", "open_intent failed: ${e.message}")
                "FAILED: ${e.message}"
            }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("action", action)
                put("result", result)
            }.toString()))
        }
    ),
)

/** 日志占位（避免直接依赖 android.util.Log 的重复） */
private object LogWrapper {
    fun w(tag: String, msg: String) {
        android.util.Log.w(tag, msg)
    }
}