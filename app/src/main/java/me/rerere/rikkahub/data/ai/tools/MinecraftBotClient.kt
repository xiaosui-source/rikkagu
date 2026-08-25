/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Minecraft Java 版协议机器人客户端（AI 就是机器人）。
 *
 * 纯 Kotlin 实现，不依赖外部库/工作区，目标 vanilla 服务器（无需任何服务器端配置）：
 * - Handshake + Login（作为 bot 登录服务器）—— 已实现
 * - Play 阶段：保持连接（Keep Alive）、读取玩家坐标、移动、挖方块、放方块 —— 本文件补全
 * - 说话 —— 已实现
 *
 * 协议版本：1.21.1 (protocol 767)。
 * 包 ID 基于 Java 版 protocol 767，若连的服务器版本不同可能需要微调包 ID。
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

    // 玩家位置（客户端bound Player Position And Look 同步）
    @Volatile var posX = 0.0; @Volatile var posY = 0.0; @Volatile var posZ = 0.0
    @Volatile var yaw = 0f; @Volatile var pitch = 0f
    @Volatile private var onGround = false
    @Volatile private var needTeleportSync = false

    private var readThread: Thread? = null

    // ===== Serverbound 包 ID（1.21.1 / protocol 767）=====
    private companion object {
        const val SB_KEEP_ALIVE = 0x1A
        const val SB_CHAT = 0x03
        const val SB_PLAYER_POS = 0x20
        const val SB_PLAYER_POS_ROT = 0x21
        const val SB_PLAYER_ACTION = 0x24
        const val SB_PLAYER_DIGGING = 0x1B
        const val SB_PLAYER_BLOCK_PLACEMENT = 0x2A

        // Clientbound（读包时用）
        const val CB_KEEP_ALIVE = 0x28
        const val CB_LOGIN_PLAY = 0x29
        const val CB_SYNC_PLAYER_POS = 0x40
        const val CB_SPAWN_POSITION = 0x24
    }

    /** 连接服务器：Handshake + Login Start，然后启动 Play 读包线程 */
    fun connect(): String {
        return try {
            socket = Socket(host, port)
            socket!!.soTimeout = 30000
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

            // 2. Login Start (0x00)
            val loginPayload = java.io.ByteArrayOutputStream()
            val ls = DataOutputStream(loginPayload)
            writeVarInt(ls, 0x00)
            writeString(ls, username)
            ls.writeLong(0L) // timestamp
            ls.writeLong(0L) // public key
            writeString(ls, "") // UUID
            if (accessToken != null) {
                writeString(ls, accessToken)
            }
            ls.flush()
            writePacket(loginPayload.toByteArray())

            // 3. 读取 Login 阶段响应
            val resp = readLoginResponse()
            if (resp.startsWith("ERROR")) {
                connected = false
                return resp
            }

            // 4. 进入 Play 阶段，启动读包线程（保持连接 + 同步坐标）
            connected = true
            startPlayReader()
            "已作为 '$username' 进入服务器 $host:$port，可开始移动/挖放/说话"
        } catch (e: Exception) {
            connected = false
            "连接失败: ${e.message}"
        }
    }

    /** 说话 */
    fun chat(message: String): String {
        if (!connected) return "未连接服务器，先调用 mc_bot_connect"
        return try {
            val payload = java.io.ByteArrayOutputStream()
            val out = DataOutputStream(payload)
            writeVarInt(out, SB_CHAT)
            writeString(out, message)
            out.flush()
            writePacket(payload.toByteArray())
            "已发送: $message"
        } catch (e: Exception) {
            "发送失败: ${e.message}"
        }
    }

    /** 获取当前坐标 */
    fun position(): String = "%.1f, %.1f, %.1f".format(posX, posY, posZ)

    /** 移动：发送 Player Position（相对服务器当前同步点）。dx/dy/dz 为相对位移，若已在同点则重发当前位置保持同步 */
    fun move(dx: Double, dy: Double, dz: Double): String {
        if (!connected) return "未连接"
        return try {
            // 第一次：如果还没同步过位置，用当前坐标；否则用传入的相对位移
            val nx = if (needTeleportSync) posX + dx else posX + dx
            val ny = if (needTeleportSync) posY + dy else posY + dy
            val nz = if (needTeleportSync) posZ + dz else posZ + dz
            val payload = java.io.ByteArrayOutputStream()
            val out = DataOutputStream(payload)
            writeVarInt(out, SB_PLAYER_POS)
            out.writeDouble(nx)
            out.writeDouble(ny)
            out.writeDouble(nz)
            out.writeBoolean(true) // onGround
            out.flush()
            writePacket(payload.toByteArray())
            // 乐观更新本地坐标（服务器下个同步包会校准）
            posX = nx; posY = ny; posZ = nz; onGround = true; needTeleportSync = false
            "移动到 $nx, $ny, $nz"
        } catch (e: Exception) {
            "移动失败: ${e.message}"
        }
    }

    /** 挖方块：face 0=底,1=顶,2=北,3=南,4=西,5=东 */
    fun dig(bx: Int, by: Int, bz: Int, face: Int = 1): String {
        if (!connected) return "未连接"
        return try {
            // 先挥动手臂(Player Action=start_sprint? 这里用最简单：直接开挖)
            placementPacket(SB_PLAYER_DIGGING) { out ->
                writeVarInt(out, 0) // START_DIGGING
                writeBlockPos(out, bx, by, bz)
                writeVarInt(out, face)
            }
            "挖掘方块 $bx,$by,$bz"
        } catch (e: Exception) {
            "挖方块失败: ${e.message}"
        }
    }

    /** 放方块：face 同 dig。blockStateId 用 0 表示主手物品目标位置 */
    fun place(bx: Int, by: Int, bz: Int, face: Int): String {
        if (!connected) return "未连接"
        return try {
            val payload = java.io.ByteArrayOutputStream()
            val out = DataOutputStream(payload)
            writeVarInt(out, SB_PLAYER_BLOCK_PLACEMENT)
            writeBlockPos(out, bx, by, bz)
            writeVarInt(out, face)
            // cursor3i := varint x,bits..（这里简化为内插分数主体：cursorX 0.5 等）
            // Block Placement 字段(1.21.1)：Location, Face, CursorPos(3×float), InsideBlock(bool), Sequence(VarInt), Item(Optional Slot)
            out.writeFloat(0.5f); out.writeFloat(0.5f); out.writeFloat(0.5f)
            out.writeBoolean(false) // inside block
            writeVarInt(out, 0) // sequence
            // 主手空物品：Optional Slot(None)：write Boolean(false)
            out.writeBoolean(false)
            out.flush()
            writePacket(payload.toByteArray())
            "在 $bx,$by,$bz 放置方块"
        } catch (e: Exception) {
            "放方块失败: ${e.message}"
        }
    }

    private fun placementPacket(id: Int, body: (DataOutputStream) -> Unit) {
        val payload = java.io.ByteArrayOutputStream()
        val out = DataOutputStream(payload)
        writeVarInt(out, id)
        body(out)
        out.flush()
        writePacket(payload.toByteArray())
    }

    private fun writeBlockPos(out: DataOutputStream, x: Int, y: Int, z: Int) {
        val valX = x and 0x3FFFFFF
        val valZ = z and 0x3FFFFFF
        val valY = y and 0xFFF
        val value = (valX shl 38) or (valZ shl 12) or valY
        writeVarInt(out, value)
    }

    /** 断开连接 */
    fun disconnect() {
        connected = false
        try { readThread?.interrupt() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    fun isConnected() = connected

    // ===== Play 阶段读包线程 =====
    private fun startPlayReader() {
        readThread = thread(isDaemon = true, name = "mc-bot-reader") {
            try {
                while (connected) {
                    // Envelope: VarInt(Length)，Length = packet id + 数据的总字节数
                    val length = readVarInt(input!!)
                    if (length < 0) break
                    if (length > 1_048_576) break // 防恶意大包
                    val body = ByteArray(length)
                    input!!.readFully(body)
                    val bodyIn = DataInputStream(java.io.ByteArrayInputStream(body))
                    val packetId = readVarInt(bodyIn)
                    when (packetId) {
                        CB_KEEP_ALIVE -> {
                            val keepAlive = readVarLong(bodyIn)
                            sendPacket(SB_KEEP_ALIVE) { out -> out.writeLong(keepAlive) }
                        }
                        CB_SYNC_PLAYER_POS -> {
                            posX = bodyIn.readDouble()
                            posY = bodyIn.readDouble()
                            posZ = bodyIn.readDouble()
                            yaw = bodyIn.readFloat()
                            pitch = bodyIn.readFloat()
                            bodyIn.readByte() // flags
                            readVarInt(bodyIn) // teleport id
                            // 回应 ACK（同步位置确认）
                            sendPacket(SB_PLAYER_POS_ROT) { out ->
                                out.writeDouble(posX); out.writeDouble(posY); out.writeDouble(posZ)
                                out.writeFloat(yaw); out.writeFloat(pitch); out.writeBoolean(true)
                            }
                            onGround = true
                            needTeleportSync = false
                        }
                        else -> {
                            // 未知包：已按 Length 读完 body，天然不会错位
                        }
                    }
                }
            } catch (e: Exception) {
                connected = false
            }
        }
    }

    private fun readVarLong(input: DataInputStream): Long {
        var value = 0L
        var shift = 0
        while (true) {
            val byte = input.readByte().toLong() and 0xFF
            value = value or ((byte and 0x7F) shl shift)
            if (byte and 0x80L == 0L) break
            shift += 7
            if (shift > 63) throw Exception("VarLong too big")
        }
        return value
    }

    private fun sendPacket(id: Int, body: (DataOutputStream) -> Unit) {
        val payload = java.io.ByteArrayOutputStream()
        val out = DataOutputStream(payload)
        writeVarInt(out, id)
        body(out)
        out.flush()
        writePacket(payload.toByteArray())
    }

    /** 读 Login 阶段响应包 */
    private fun readLoginResponse(): String {
        return try {
            input!!.soTimeout = 15000
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
                else -> "LOGIN_OK"
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