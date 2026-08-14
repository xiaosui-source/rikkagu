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
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
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

/** 基岩版 RCON 命令（基岩版服务器支持 RCON over TCP） */
private fun bedrockRconCommand(host: String, port: Int, password: String, command: String): String {
    return try {
        val socket = Socket(host, port)
        socket.soTimeout = 10000
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        // 登录
        val loginBody = password.toByteArray(Charsets.UTF_8)
        val loginLen = 4 + 4 + loginBody.size + 2
        val lb = java.io.ByteArrayOutputStream()
        val ld = DataOutputStream(lb)
        ld.writeInt(loginLen); ld.writeInt(1); ld.writeInt(3); ld.write(loginBody); ld.writeByte(0); ld.writeByte(0)
        output.write(lb.toByteArray()); output.flush()
        readRconPacket(input); readRconPacket(input)
        // 命令
        val cmdBody = command.toByteArray(Charsets.UTF_8)
        val cmdLen = 4 + 4 + cmdBody.size + 2
        val cb = java.io.ByteArrayOutputStream()
        val cd = DataOutputStream(cb)
        cd.writeInt(cmdLen); cd.writeInt(2); cd.writeInt(2); cd.write(cmdBody); cd.writeByte(0); cd.writeByte(0)
        output.write(cb.toByteArray()); output.flush()
        val resp = readRconPacket(input)
        readRconPacket(input)
        socket.close()
        resp
    } catch (e: Exception) {
        "RCON 错误: ${e.message}"
    }
}

private fun readRconPacket(input: DataInputStream): String {
    val length = input.readInt()
    if (length < 10) return ""
    input.readInt(); input.readInt()
    val bytes = ByteArray(length - 10)
    input.readFully(bytes)
    input.readByte(); input.readByte()
    return bytes.toString(Charsets.UTF_8).trim()
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
    // ===== 微软账号登录 =====
    Tool(
        name = "mc_bot_msauth",
        description = "微软账号登录（online-mode 服务器需要）。直接用账号密码登录，AI 自动完成认证。Params: email(微软账号邮箱), password(密码)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("email", buildJsonObject { put("type", "string"); put("description", "微软账号邮箱") })
                put("password", buildJsonObject { put("type", "string"); put("description", "微软账号密码") })
            }, required = listOf("email", "password"))
        },
        execute = { args ->
            val o = args.jsonObject
            val email = o["email"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"email required"}"""))
            val password = o["password"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"password required"}"""))
            try {
                val auth = MinecraftMicrosoftAuth()
                val result = auth.loginWithPassword(email, password)
                // 保存登录结果（token + 用户名）
                context.getSharedPreferences("minecraft_mcp", Context.MODE_PRIVATE).edit()
                    .putString("ms_token", result.accessToken)
                    .putString("ms_username", result.username)
                    .putString("ms_uuid", result.uuid)
                    .apply()
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", true)
                    put("username", result.username)
                    put("uuid", result.uuid)
                    put("message", "微软账号登录成功！AI 机器人将以此账号进服务器")
                    put("next", "调用 mc_bot_connect 连接服务器")
                }.toString()))
            } catch (e: Exception) {
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", false)
                    put("error", e.message ?: "登录失败")
                }.toString()))
            }
        },
    ),

    // ===== 机器人连接服务器 =====
    Tool(
        name = "mc_bot_connect",
        description = "AI 机器人登录 Minecraft 服务器（Java版或基岩版，AI 就是游戏里的机器人）。Params: host(服务器地址), optional port, optional version(java/bedrock，默认java), optional username(机器人名字), optional rcon_password(基岩版RCON密码)",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("host", buildJsonObject { put("type", "string"); put("description", "服务器地址，如 127.0.0.1 或 192.168.1.100") })
                put("port", buildJsonObject { put("type", "string"); put("description", "端口：Java默认25565，基岩版默认19132/RCON 25575") })
                put("version", buildJsonObject { put("type", "string"); put("description", "版本：java 或 bedrock，默认 java") })
                put("username", buildJsonObject { put("type", "string"); put("description", "机器人名字（离线模式），默认 AI_Bot") })
                put("rcon_password", buildJsonObject { put("type", "string"); put("description", "基岩版 RCON 密码（bedrock 需要）") })
            }, required = listOf("host"))
        },
        execute = { args ->
            val o = args.jsonObject
            val host = o["host"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"host required"}"""))
            val version = o["version"]?.jsonPrimitive?.contentOrNull ?: "java"
            val port = o["port"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: if (version == "bedrock") 19132 else 25565
            var username = o["username"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "AI_Bot"
            val rconPassword = o["rcon_password"]?.jsonPrimitive?.contentOrNull ?: ""
            saveBotConfig(context, host, port, username)

            // 基岩版：保存 RCON 配置（基岩版用 RCON 操作）
            if (version == "bedrock") {
                context.getSharedPreferences("minecraft_mcp", Context.MODE_PRIVATE).edit()
                    .putString("version", "bedrock")
                    .putString("rcon_password", rconPassword)
                    .apply()
            } else {
                context.getSharedPreferences("minecraft_mcp", Context.MODE_PRIVATE).edit()
                    .putString("version", "java")
                    .apply()
            }

            // 使用已保存的微软 token（mc_bot_msauth 登录）
            val prefs = context.getSharedPreferences("minecraft_mcp", Context.MODE_PRIVATE)
            var msToken: String? = prefs.getString("ms_token", null)
            val msUsername = prefs.getString("ms_username", null)
            if (msToken != null && msUsername != null) {
                username = msUsername
            }

            // 断开旧连接
            botSession?.disconnect()
            val bot = MinecraftBotClient(host, port, username, msToken)
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

    // ===== 基岩版操作（RCON）=====
    Tool(
        name = "mc_bedrock_do",
        description = "基岩版服务器操作（RCON 命令：移动/放置/召唤/时间/天气等）。Params: command(如 'tp @p 100 64 100'、'weather clear')",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("command", buildJsonObject { put("type", "string"); put("description", "基岩版命令") })
            }, required = listOf("command"))
        },
        execute = { args ->
            val o = args.jsonObject
            val cmd = o["command"]?.jsonPrimitive?.contentOrNull ?: return@Tool listOf(UIMessagePart.Text("""{"error":"command required"}"""))
            val cfg = loadBotConfig(context)
            val prefs = context.getSharedPreferences("minecraft_mcp", Context.MODE_PRIVATE)
            val rconPwd = prefs.getString("rcon_password", "") ?: ""
            val rconPort = 25575 // 基岩版 RCON 默认端口
            val result = bedrockRconCommand(cfg.host, rconPort, rconPwd, cmd)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("command", cmd)
                put("response", result)
                put("status", if (result.startsWith("RCON 错误")) "失败" else "成功")
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

    // ===== AI 自主游玩通关 =====
    Tool(
        name = "mc_auto_play",
        description = "【AI 自主游玩通关模式】调用此工具后，AI 自主决定玩什么、怎么玩，持续行动直到通关。AI 会自动循环：查询状态→决定下一步（探索/挖矿/建造/战斗）→执行操作→记录并汇报进度。目标：自主完成游戏内容（如建家/挖矿/打怪/击败Boss等）。",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(properties = buildJsonObject {
                put("goal", buildJsonObject { put("type", "string"); put("description", "通关目标（可选，如'建一座房子'、'挖到钻石'、'击败末影龙'）") })
            })
        },
        execute = { args ->
            val o = args.jsonObject
            val goal = o["goal"]?.jsonPrimitive?.contentOrNull ?: "自主探索并通关"
            // 进入自主游玩模式：AI 根据 goal 持续决策执行
            saveProgress(context, "【自主通关模式启动】目标: $goal")
            listOf(UIMessagePart.Text(buildJsonObject {
                put("auto_play", true)
                put("goal", goal)
                put("instructions", "AI 请开始自主游玩并持续汇报：每次行动后调用 mc_progress 记录进度，直到达成目标或通关")
                put("loop", "AI 循环执行：mc_status(查状态) → mc_bot_connect/mc_bedrock_do(行动) → mc_progress(汇报) → 继续")
            }.toString()))
        },
    ),
)
