/*
 * 灵犀 Lingxi
 * 参考自 Operit AI (https://github.com/AAswordman/Operit) 的应用管理工具
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.serialization.json.add
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
 * 应用管理工具（参考 Operit StandardSystemOperationTools）：
 * - list_installed_apps: 列出已安装应用
 * - start_app: 启动指定应用
 * - stop_app: 强制停止指定应用
 * - app_usage_time: 查询应用使用时长
 * 除 start_app 需 GET_TASKS 外，其余用 PackageManager 原生实现。uninstall 因权限限制默认不可用，
 * 提供系统卸载意图。
 */
fun createAppManagerTools(context: Context): List<Tool> = listOf(
    // ===== 列出已安装应用 =====
    Tool(
        name = "list_installed_apps",
        description = "List installed apps on the device (filterable by keyword). Use to find package names or check if an app is installed.",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("keyword", buildJsonObject {
                        put("type", "string")
                        put("description", "Optional keyword to filter apps by name, case-insensitive")
                    })
                },
                required = emptyList(),
            )
        },
        execute = { input ->
            val keyword = input.jsonObject["keyword"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { app ->
                    val label = pm.getApplicationLabel(app).toString()
                    label to app.packageName
                }
                .filter { keyword.isBlank() || it.first.lowercase().contains(keyword) || it.second.lowercase().contains(keyword) }
                .sortedBy { it.first }
                .take(100)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("count", apps.size)
                put("apps", buildJsonArray { apps.forEach { (label, pkg) ->
                    add(buildJsonObject {
                        put("label", label); put("package", pkg)
                    })
                } })
            }.toString()))
        }
    ),

    // ===== 启动应用 =====
    Tool(
        name = "start_app",
        description = "Launch an app by package name. Use when the user asks to open a specific application.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("package_name", buildJsonObject {
                        put("type", "string")
                        put("description", "Package name of the app to launch, e.g. com.android.settings")
                    })
                },
                required = listOf("package_name"),
            )
        },
        execute = { input ->
            val pkg = input.jsonObject["package_name"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"package_name required"}"""))
            val result = runCatching {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                    ?: return@runCatching "APP_NOT_FOUND"
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                "OK"
            }.getOrElse { "FAILED: ${it.message}" }
            listOf(UIMessagePart.Text(buildJsonObject { put("result", result) }.toString()))
        }
    ),

    // ===== 停止应用 =====
    Tool(
        name = "stop_app",
        description = "Force-stop an app by package name. Use when the user wants to close/kill a running application.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("package_name", buildJsonObject {
                        put("type", "string")
                        put("description", "Package name of the app to force-stop")
                    })
                },
                required = listOf("package_name"),
            )
        },
        execute = { input ->
            val pkg = input.jsonObject["package_name"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"package_name required"}"""))
            val result = runCatching {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                am.forceStopPackage(pkg)
                "OK"
            }.getOrElse { "FAILED: ${it.message} (可能缺少 FORCE_STOP_PACKAGES 权限)" }
            listOf(UIMessagePart.Text(buildJsonObject { put("result", result) }.toString()))
        }
    ),

    // ===== 系统卸载意图 =====
    Tool(
        name = "uninstall_app",
        description = "Open the system uninstall confirm for an app by package name. User must confirm the uninstall.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("package_name", buildJsonObject {
                        put("type", "string")
                        put("description", "Package name of the app to uninstall")
                    })
                },
                required = listOf("package_name"),
            )
        },
        execute = { input ->
            val pkg = input.jsonObject["package_name"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"package_name required"}"""))
            val result = runCatching {
                val intent = Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                "UNINSTALL_DIALOG_OPENED"
            }.getOrElse { "FAILED: ${it.message}" }
            listOf(UIMessagePart.Text(buildJsonObject { put("result", result) }.toString()))
        }
    ),
)