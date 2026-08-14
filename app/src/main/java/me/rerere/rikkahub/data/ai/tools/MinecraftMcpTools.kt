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
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

/**
 * Minecraft MCP：让 AI 指挥 Minecraft（Java版/基岩版）服务器里的机器人/玩家自动行动。
 *
 * 通过 RCON 协议（纯 Kotlin，零依赖、不用工作区）：
 * - 指挥游戏内操作（移动/放置/挖掘/召唤/时间/天气等）
 * - 查询服务器/玩家状态
 * - 记录并汇报"玩到哪一步"
 *
 * Java 版和基岩版服务器都支持 RCON（server.properties: enable-rcon=true）。
 */

private data class McServerConfig(
    val host: String = "127.0.0.1",
    val port: Int = 25575,
    val password: String = "",
)

private fun loadConfig(context: Context): McServerConfig {
    val prefs = context.getSharedPreferences("minecraft_mcp", Context.MODE_PRIVATE)
    return McServerConfig(
        host = prefs.getString("host", "127.0.0.1") ?: "127.0.0.1",
        port = prefs.getInt("port", 25575),
        password = prefs.getString("password", "") ?: "",
    )
}

private fun saveConfig(context: Context, host: String, port: Int, password: String) {
    context.getSharedPreferences("minecraft_mcp", Context.MODE_PRIVATE).edit()
        .putString("host", host)
        .putInt("port", port)
        .putString("password", password)
        .apply()
}

/** 进度记录（AI 汇报玩到哪一步） */
private fun loadProgress(context: Context): String {
    return context.getSharedPreferences("minecraft_mcp", Context.MODE_PRIVATE)
        .getString("progress", "游戏尚未开始") ?: "游戏尚未开始"
}

private fun saveProgress(context: Context, text: String) {
    context.getSharedPreferences("minecraft_mcp", Context.MODE_PRIVATE).edit()
        .putString("progress", text.take(2000))
        .apply()
}

/** RCON 协议：发送命令，返回服务器响应 */
private fun rconCommand(host: String, port: Int, password: String, command: String): String {
    return try {
        val socket = Socket(host, port)
        socket.soTimeout = 10000
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        // 登录
        output.write(buildRconPacket(1, 3, password))
        output.flush()
        readRconPacket(input)
        readRconPacket(input)

        // 命令
        output.write(buildRconPacket(2, 2, command))
        output.flush()
        val resp = readRconPacket(input)
        readRconPacket(input)

        socket.close()
        resp
    } catch (e: Exception) {
        "RCON 错误: ${e.message}"
    }
}

private fun buildRconPacket(requestId: Int, type: Int, body: String): ByteArray {
    val bodyBytes = body.toByteArray(Charsets.UTF_8)
    val length = 4 + 4 + bodyBytes.size + 2
    val buffer = java.io.ByteArrayOutputStream()
    val dataOut = DataOutputStream(buffer)
    dataOut.writeInt(length)
    dataOut.writeInt(requestId)
    dataOut.writeInt(type)
    dataOut.write(bodyBytes)
    dataOut.writeByte(0)
    dataOut.writeByte(0)
    dataOut.flush()
    return buffer.toByteArray()
}

private fun readRconPacket(input: DataInputStream): String {
    val length = input.readInt()
    if (length < 10) return ""
    input.readInt() // requestId
    input.readInt() // type
    val bodyBytes = ByteArray(length - 10)
    input.readFully(bodyBytes)
    input.readByte()
    input.readByte()
    return bodyBytes.toString(Charsets.UTF_8).trim()
}

fun buildMinecraftMcpTools(context: Context): List<Tool> = listOf(
    // ===== 连接服务器 =====
    Tool(
        name = "mc_connect",
        description = "配置并连接 Minecraft 服务器（Java版/基岩版都支持 RCON，机器人可玩任意服务器）。Params: host(服务器地址), optional port(默认25575), password(RCON密码)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("host", buildJsonObject { put("type", "string"); put("description", "服务器地址，如 127.0.0.1 或 192.168.1.100") })
                put("port", buildJsonObject { put("type", "string"); put("description", "RCON 端口，默认 25575") })
                put("password", buildJsonObject { put("type", "string"); put("description", "RCON 密码（server.properties 的 rcon.password）") })
            }, required = listOf("host", "password"))
        },
        execute = { args ->
            val o = args.jsonObject
            val host = o["host"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"host required"}"""))
            val port = o["port"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 25575
            val password = o["password"]?.jsonPrimitive?.contentOrNull ?: ""
            saveConfig(context, host, port, password)
            val test = rconCommand(host, port, password, "list")
            listOf(UIMessagePart.Text(buildJsonObject {
                put("connected", !test.startsWith("RCON 错误"))
                put("host", host)
                put("port", port)
                put("test_response", test)
                put("tip", "服务器需开启 RCON：enable-rcon=true, rcon.port=25575")
            }.toString()))
        },
    ),

    // ===== 指挥操作 =====
    Tool(
        name = "mc_do",
        description = "让机器人/玩家在 Minecraft 服务器执行操作（自动玩）。命令示例：'tp @p 100 64 100'(移动)、'execute at @p run setblock ~ ~1 ~ minecraft:stone'(放置)、'summon minecraft:zombie'(召唤)、'weather clear'(天气)、'time set day'(时间)、'say hello'(说话)、'give @p minecraft:diamond 64'(给物品)",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("command", buildJsonObject { put("type", "string"); put("description", "Minecraft 命令（控制移动/放置/挖掘/召唤/时间/天气等）") })
            }, required = listOf("command"))
        },
        execute = { args ->
            val o = args.jsonObject
            val cmd = o["command"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"command required"}"""))
            val cfg = loadConfig(context)
            val result = rconCommand(cfg.host, cfg.port, cfg.password, cmd)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("command", cmd)
                put("response", result)
                put("status", if (result.startsWith("RCON 错误")) "失败" else "成功")
            }.toString()))
        },
    ),

    // ===== 查询状态 =====
    Tool(
        name = "mc_status",
        description = "查询 Minecraft 服务器/玩家状态（在线玩家、服务器信息、当前位置等）。",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
        execute = {
            val cfg = loadConfig(context)
            val listResult = rconCommand(cfg.host, cfg.port, cfg.password, "list")
            val posResult = rconCommand(cfg.host, cfg.port, cfg.password, "execute at @p run tp @p ~ ~ ~")
            listOf(UIMessagePart.Text(buildJsonObject {
                put("server", "${cfg.host}:${cfg.port}")
                put("players", listResult)
                put("position_check", posResult)
            }.toString()))
        },
    ),

    // ===== 记录/汇报进度 =====
    Tool(
        name = "mc_progress",
        description = "记录当前玩到哪一步（AI 自动汇报游戏进度），或查询已记录进度。Params: optional progress(要记录的新进度描述，留空则查询)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("progress", buildJsonObject { put("type", "string"); put("description", "要记录的进度描述（可选，留空=查询当前进度）") })
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
                put("note", "AI 持续记录游戏进度：挖矿/建造/探索/任务完成情况")
            }.toString()))
        },
    ),
)
