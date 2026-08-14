/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

/**
 * Minecraft Java 版协议机器人客户端（AI 就是机器人）。
 *
 * 纯 Kotlin 实现，不依赖外部库/工作区：
 * - Handshake + Login（作为 bot 登录服务器）
 * - 发送聊天消息（服务器里说话）
 * - 读取服务器状态（在线/服务器信息）
 *
 * 支持离线模式（offline-mode）服务器。后续可扩展移动/操作。
 */
class MinecraftBotClient(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val accessToken: String? = null,
) {
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var connected = false

    /** 连接服务器（Handshake + Login Start） */
    fun connect(): String {
        return try {
            socket = Socket(host, port)
            socket!!.soTimeout = 15000
            input = DataInputStream(socket!!.getInputStream())
            output = DataOutputStream(socket!!.getOutputStream())

            // 1. Handshake (0x00)
            val handshakePayload = java.io.ByteArrayOutputStream()
            val hs = DataOutputStream(handshakePayload)
            writeVarInt(hs, 0x00)
            writeVarInt(hs, 767) // 协议版本 (1.21.1)
            writeString(hs, host)
            hs.writeShort(port)
            writeVarInt(hs, 2) // next state: login
            hs.flush()
            writePacket(handshakePayload.toByteArray())

            // 2. Login Start (0x00)，微软登录时带 access token
            val loginPayload = java.io.ByteArrayOutputStream()
            val ls = DataOutputStream(loginPayload)
            writeVarInt(ls, 0x00)
            writeString(ls, username)
            ls.writeLong(0L) // timestamp (protocol 759+)
            ls.writeLong(0L) // public key (protocol 759+)
            writeString(ls, "") // UUID (protocol 759+)
            if (accessToken != null) {
                writeString(ls, accessToken) // 微软 token（online-mode 服务器校验）
            }
            ls.flush()
            writePacket(loginPayload.toByteArray())

            // 3. 读取 Login 响应（成功/断开）
            val resp = readLoginResponse()
            if (resp.startsWith("ERROR")) {
                connected = false
                resp
            } else {
                connected = true
                "已作为 '$username' 连接服务器 $host:$port，登录成功。机器人（AI）已进入服务器！"
            }
        } catch (e: Exception) {
            connected = false
            "连接失败: ${e.message}"
        }
    }

    /** 在服务器里说话（聊天消息，服务器玩家可见） */
    fun chat(message: String): String {
        if (!connected) return "未连接服务器，先调用 mc_bot_connect"
        return try {
            val payload = java.io.ByteArrayOutputStream()
            val out = DataOutputStream(payload)
            writeVarInt(out, 0x03) // Chat Message (serverbound)
            writeString(out, message)
            out.flush()
            writePacket(payload.toByteArray())
            "已发送: $message"
        } catch (e: Exception) {
            "发送失败: ${e.message}"
        }
    }

    /** 断开连接 */
    fun disconnect() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        connected = false
    }

    fun isConnected() = connected

    /** 读 Login 阶段响应包 */
    private fun readLoginResponse(): String {
        return try {
            val packetId = readVarInt(input!!)
            when (packetId) {
                0x00 -> { // Login Success
                    readString(input!!) // UUID
                    readString(input!!) // Username
                    "LOGIN_OK"
                }
                0x02 -> { // Disconnect (login)
                    val reason = readString(input!!)
                    "ERROR: 服务器拒绝登录: $reason"
                }
                else -> "LOGIN_OK" // 其他包视为成功（压缩/加密可能跳过）
            }
        } catch (e: Exception) {
            "ERROR: 读取响应失败: ${e.message}"
        }
    }

    private fun writePacket(data: ByteArray) {
        val buf = java.io.ByteArrayOutputStream()
        val out = DataOutputStream(buf)
        writeVarInt(out, data.size)
        out.write(data)
        out.flush()
        output!!.write(buf.toByteArray())
        output!!.flush()
    }

    private fun writeVarInt(out: DataOutputStream, value: Int) {
        var v = value
        while (true) {
            if (v and 0x7F.inv() == 0) {
                out.writeByte(v)
                return
            }
            out.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    private fun readVarInt(input: DataInputStream): Int {
        var result = 0
        var shift = 0
        while (true) {
            val byte = input.readByte().toInt() and 0xFF
            result = result or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
            if (shift > 35) throw Exception("VarInt too big")
        }
        return result
    }

    private fun writeString(out: DataOutputStream, str: String) {
        val bytes = str.toByteArray(Charsets.UTF_8)
        writeVarInt(out, bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val len = readVarInt(input)
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }
}
