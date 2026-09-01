/*
 * 灵犀 Lingxi
 * 参考自 Operit AI (https://github.com/AAswordman/Operit) 的 debugger 工具
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.StatFs
import android.util.Log
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
import java.util.concurrent.TimeUnit

private const val TAG = "DebuggerTools"

/**
 * 调试器工具（参考 Operit DebuggerDeviceInfoToolExecutor / DebuggerSystemOperationTools）：
 * - debug_device_info: 设备信息（型号/系统/内存/存储/电池/CPU架构）
 * - debug_process_info: 进程/内存/线程状态（当前进程）
 * - debug_logcat: 抓取最近 logcat 日志（帮助排错）
 * 全部 rikkahub 原生实现，不依赖 Operit 框架。
 */
fun createDebuggerTools(context: Context): List<Tool> = listOf(
    // ===== 设备信息 =====
    Tool(
        name = "debug_device_info",
        description = "Get device diagnostics: model, Android version, CPU architecture, total/free RAM, storage, battery level. " +
            "Use when debugging crashes, performance, or environment issues.",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
        execute = {
            val memInfo = android.app.ActivityManager.MemoryInfo().also {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                am.getMemoryInfo(it)
            }
            val stats = StatFs(Environment.getDataDirectory().path)
            val totalStorage = stats.totalBytes
            val freeStorage = stats.availableBytes
            val battery = runCatching {
                val bm = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                bm?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            }.getOrDefault(-1)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("model", Build.MODEL)
                put("manufacturer", Build.MANUFACTURER)
                put("android_version", Build.VERSION.RELEASE)
                put("sdk", Build.VERSION.SDK_INT)
                put("cpu_abi", Build.SUPPORTED_ABIS.joinToString(","))
                put("ram_total_mb", memInfo.totalMem / 1024 / 1024)
                put("ram_available_mb", memInfo.availMem / 1024 / 1024)
                put("storage_total_gb", totalStorage / 1024 / 1024 / 1024)
                put("storage_free_gb", freeStorage / 1024 / 1024 / 1024)
                put("battery_percent", battery)
            }.toString()))
        }
    ),

    // ===== 进程/线程信息 =====
    Tool(
        name = "debug_process_info",
        description = "Get current app process diagnostics: heap memory (used/free), thread count, native memory, class loader info.",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
        execute = {
            val runtime = Runtime.getRuntime()
            listOf(UIMessagePart.Text(buildJsonObject {
                put("heap_used_kb", (runtime.totalMemory() - runtime.freeMemory()) / 1024)
                put("heap_total_kb", runtime.totalMemory() / 1024)
                put("heap_max_kb", runtime.maxMemory() / 1024)
                put("thread_count", Thread.getAllStackTraces().size)
                put("native_allocated_kb", runCatching { Debug.getNativeHeapAllocatedSize() / 1024 }.getOrDefault(-1L))
                put("uptime_minutes", TimeUnit.MILLISECONDS.toMinutes(android.os.SystemClock.elapsedRealtime()))
            }.toString()))
        }
    ),

    // ===== Logcat =====
    Tool(
        name = "debug_logcat",
        description = "Capture recent Android logcat output to help debug issues. Returns the last N lines (default 100).",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("lines", buildJsonObject {
                        put("type", "integer")
                        put("description", "Number of log lines to return (default 100, max 500)")
                    })
                },
                required = emptyList(),
            )
        },
        execute = { input ->
            val lines = input.jsonObject["lines"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.coerceIn(1, 500) ?: 100
            val output = runCatching {
                val process = ProcessBuilder("logcat", "-d", "-t", lines.toString()).redirectErrorStream(true).start()
                process.inputStream.bufferedReader().use { it.readText() }
            }.getOrDefault("logcat unavailable (needs READ_LOGS or shell access)")
            listOf(UIMessagePart.Text(buildJsonObject {
                put("lines", lines)
                put("log", JsonPrimitive(output.take(20000)))
            }.toString()))
        }
    ),
)