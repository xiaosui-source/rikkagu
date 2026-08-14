/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.io.File

/**
 * APK 反编译/解析工具 —— 纯 Android PackageManager 解析，不依赖工作区/外部二进制。
 *
 * 功能：读取 APK 的包信息、版本、权限、四大组件（Activity/Service/Receiver/Provider）、
 * 签名信息等。全部用系统 PackageManager 本地解析。
 */
fun buildApkReverseMcpTools(context: Context): List<Tool> = listOf(
    // ===== APK 基本信息 =====
    Tool(
        name = "apk_info",
        description = "解析 APK 基本信息：包名/版本/应用名/最小SDK/大小（用 Android 系统解析，无需工作区）。Params: apk_path(APK文件绝对路径)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("apk_path", buildJsonObject { put("type", "string"); put("description", "APK 文件绝对路径") })
            }, required = listOf("apk_path"))
        },
        execute = { args ->
            val o = args.jsonObject
            val path = o["apk_path"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"apk_path required"}"""))
            val file = File(path)
            if (!file.exists()) {
                return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                    put("error", "APK 文件不存在: $path")
                }.toString()))
            }
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(path, PackageManager.GET_PERMISSIONS) ?: run {
                return@Tool listOf(UIMessagePart.Text("""{"error":"无法解析APK"}"""))
            }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("package_name", info.packageName ?: "")
                put("version_name", info.versionName ?: "")
                put("version_code", info.versionCode)
                put("app_name", info.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: "")
                put("min_sdk", info.applicationInfo?.minSdkVersion ?: 0)
                put("target_sdk", info.applicationInfo?.targetSdkVersion ?: 0)
                put("file_size", file.length())
                put("file_size_kb", file.length() / 1024)
                put("permissions", JsonArray(
                    (info.requestedPermissions ?: emptyArray()).map { JsonPrimitive(it) }
                ))
                put("permission_count", info.requestedPermissions?.size ?: 0)
            }.toString()))
        },
    ),

    // ===== APK 组件解析 =====
    Tool(
        name = "apk_manifest",
        description = "解析 APK 的四大组件（Activity/Service/Receiver/Provider）+ 权限列表（Android 系统解析，无需工作区）。Params: apk_path(APK文件绝对路径)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("apk_path", buildJsonObject { put("type", "string"); put("description", "APK 文件绝对路径") })
            }, required = listOf("apk_path"))
        },
        execute = { args ->
            val o = args.jsonObject
            val path = o["apk_path"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"apk_path required"}"""))
            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS
            val info = pm.getPackageArchiveInfo(path, flags) ?: run {
                return@Tool listOf(UIMessagePart.Text("""{"error":"无法解析APK"}"""))
            }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("package_name", info.packageName ?: "")
                put("activities", JsonArray((info.activities ?: emptyArray()).map { JsonPrimitive(it.name ?: "") }))
                put("services", JsonArray((info.services ?: emptyArray()).map { JsonPrimitive(it.name ?: "") }))
                put("receivers", JsonArray((info.receivers ?: emptyArray()).map { JsonPrimitive(it.name ?: "") }))
                put("providers", JsonArray((info.providers ?: emptyArray()).map { JsonPrimitive(it.name ?: "") }))
                put("permissions", JsonArray((info.requestedPermissions ?: emptyArray()).map { JsonPrimitive(it) }))
                put("activity_count", info.activities?.size ?: 0)
                put("service_count", info.services?.size ?: 0)
                put("receiver_count", info.receivers?.size ?: 0)
            }.toString()))
        },
    ),
)
