/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * Minecraft MCP：AI 就是机器人（bot）——直接以机器人身份登录 Minecraft
 * 服务器（Java版离线模式），在服务器里行动、说话、并汇报进度。
 *
 * 纯 Kotlin 协议实现（MinecraftBotClient），零依赖、不用工作区。
 */

private data class McBotConfig(
    val host: String = "127.0.0.1",
    val port: Int = 25565,
    val username: String = "AI_Bot",
)

private fun loadBotConfig(context: Context): McBotConfig {
    val prefs = context.getSharedPreferences("minecraft_mcp", Context.MODE_PRIVATE)
    return McBotConfig(
        host = prefs.getString("host", "127.0.0.1") ?: "127.0.0.1",
        port = prefs.getInt("port", 25565),
        username = prefs.getString("username", "AI_Bot") ?: "AI_Bot",
    )
}

private fun saveBotConfig(context: Context, host: String, port: Int, username: String) {
    context.getSharedPreferences("minecraft_mcp", Context.MODE_PRIVATE).edit()
        .putString("host", host)
        .putInt("port", port)
        .putString("username", username)
        .apply()
}

private fun loadProgress(context: Context): String {
    return context.getSharedPreferences("minecraft_mcp", Context.MODE_PRIVATE)
        .getString("progress", "游戏尚未开始") ?: "游戏尚未开始"
}

private fun saveProgress(context: Context, text: String) {
    context.getSharedPreferences("minecraft_mcp", Context.MODE_PRIVATE).edit()
        .putString("progress", text.take(2000))
        .apply()
}

/** 当前 bot 会话（单例，App 进程内） */
private var botSession: MinecraftBotClient? = null

fun buildMinecraftMcpTools(context: Context): List<Tool> = listOf(
    // ===== 机器人连接服务器 =====
    Tool(
        name = "mc_bot_connect",
        description = "AI 机器人登录 Minecraft 服务器（AI 就是游戏里的机器人）。Params: host(服务器地址), optional port(默认25565), username(机器人名字)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("host", buildJsonObject { put("type", "string"); put("description", "服务器地址，如 127.0.0.1 或 192.168.1.100") })
                put("port", buildJsonObject { put("type", "string"); put("description", "服务器端口，默认 25565") })
                put("username", buildJsonObject { put("type", "string"); put("description", "机器人名字（离线模式），默认 AI_Bot") })
            }, required = listOf("host"))
        },
        execute = { args ->
            val o = args.jsonObject
            val host = o["host"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"host required"}"""))
            val port = o["port"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 25565
            val username = o["username"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "AI_Bot"
            saveBotConfig(context, host, port, username)

            // 断开旧连接
            botSession?.disconnect()
            val bot = MinecraftBotClient(host, port, username)
            val result = bot.connect()
            if (result.contains("登录成功") || result.contains("连接")) {
                botSession = bot
            }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("connected", result.contains("登录成功") || result.contains("连接"))
                put("result", result)
                put("tip", "AI 已作为机器人进入服务器（离线模式服务器），可开始玩")
            }.toString()))
        },
    ),

    // ===== 机器人说话 =====
    Tool(
        name = "mc_bot_chat",
        description = "AI 机器人在服务器里说话（其他玩家可见）。Params: message(要说的话)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("message", buildJsonObject { put("type", "string"); put("description", "机器人要说的话") })
            }, required = listOf("message"))
        },
        execute = { args ->
            val o = args.jsonObject
            val msg = o["message"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"message required"}"""))
            val bot = botSession
            if (bot == null || !bot.isConnected()) {
                return@Tool listOf(UIMessagePart.Text("""{"error":"未连接服务器，先调用 mc_bot_connect"}"""))
            }
            listOf(UIMessagePart.Text(bot.chat(msg.take(256))))
        },
    ),

    // ===== 机器人状态 =====
    Tool(
        name = "mc_bot_status",
        description = "查询 AI 机器人状态（是否在线、连接信息）。",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
        execute = {
            val cfg = loadBotConfig(context)
            val bot = botSession
            listOf(UIMessagePart.Text(buildJsonObject {
                put("connected", bot?.isConnected() == true)
                put("server", "${cfg.host}:${cfg.port}")
                put("username", cfg.username)
                put("tip", if (bot?.isConnected() == true) "AI 机器人在线，可继续玩" else "机器人未连接，先 mc_bot_connect")
            }.toString()))
        },
    ),

    // ===== 进度汇报 =====
    Tool(
        name = "mc_progress",
        description = "记录/汇报 AI 机器人玩到哪一步。Params: optional progress(新进度，留空=查询)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("progress", buildJsonObject { put("type", "string"); put("description", "进度描述（可选）") })
            })
        },
        execute = { args ->
            val o = args.jsonObject
            val newProgress = o["progress"]?.jsonPrimitive?.contentOrNull
            if (newProgress != null && newProgress.isNotBlank()) {
                saveProgress(context, newProgress)
            }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("current_progress", loadProgress(context))
                put("note", "AI 机器人持续记录：进服务器/探索/建造/完成任务等每一步")
            }.toString()))
        },
    ),
)
