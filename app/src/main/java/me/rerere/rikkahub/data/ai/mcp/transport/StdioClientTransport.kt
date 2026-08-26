/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.mcp.transport

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "StdioClientTransport"

/**
 * Stdio MCP 传输：以子进程方式启动一个外部 MCP 服务器（如 node minecraft-mcp-server.js），
 * 通过 stdin/stdout 走 MCP JSON-RPC 协议通讯。用于内置/连接任何 stdio 型 MCP 服务器。
 */
class StdioClientTransport(
    private val commandLine: List<String>,
    private val workingDir: String? = null,
    private val env: Map<String, String> = emptyMap(),
) : AbstractTransport() {

    private val initialized = AtomicBoolean(false)
    private var process: Process? = null
    private lateinit var stdoutReader: BufferedReader
    private lateinit var stdinWriter: BufferedWriter
    private lateinit var scope: CoroutineScope
    private var readJob: kotlinx.coroutines.Job? = null
    private var stderrJob: kotlinx.coroutines.Job? = null

    override suspend fun start() {
        Log.d(TAG, "Starting stdio process: $commandLine")
        check(initialized.compareAndSet(expectedValue = false, newValue = true)) {
            "StdioClientTransport already started!"
        }
        try {
            val pb = ProcessBuilder(commandLine).also { b ->
                if (workingDir != null) b.directory(java.io.File(workingDir))
                if (env.isNotEmpty()) b.environment().putAll(env)
            }
            process = pb.start()
            stdoutReader = BufferedReader(InputStreamReader(process!!.inputStream, StandardCharsets.UTF_8))
            stdinWriter = BufferedWriter(OutputStreamWriter(process!!.outputStream, StandardCharsets.UTF_8))

            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            readJob = scope.launch(CoroutineName("StdioMcpClientTransport.read#${hashCode()}")) {
                readLoop()
            }
            stderrJob = scope.launch(CoroutineName("StdioMcpClientTransport.stderr#${hashCode()}")) {
                process!!.errorStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { line -> if (line.isNotBlank()) Log.w(TAG, "[mcp-server-stderr] $line") }
                }
            }
        } catch (e: Exception) {
            runCatching { process?.destroy() }
            initialized.set(false)
            throw e
        }
    }

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
        check(initialized.load()) { "StdioClientTransport is not initialized!" }
        try {
            val json = McpJson.encodeToString(message)
            val writer: BufferedWriter = stdinWriter
            writer.write(json)
            writer.newLine()
            writer.flush()
            Log.d(TAG, "Sent: $json")
        } catch (e: Throwable) {
            _onError(e)
            throw e
        }
    }

    override suspend fun close() {
        if (!initialized.compareAndSet(expectedValue = true, newValue = false)) return
        try {
            readJob?.cancelAndJoin()
            stderrJob?.cancelAndJoin()
            runCatching { stdinWriter.flush() }
            runCatching { stdinWriter.close() }
            runCatching { process?.destroy() }
            // 给进程一秒时间优雅退出，超时强制销毁
            runCatching { process?.waitFor(1, java.util.concurrent.TimeUnit.SECONDS) }
            runCatching { process?.destroyForcibly() }
        } catch (e: Throwable) {
            _onError(e)
        }
        invokeOnCloseCallback()
    }

    private suspend fun readLoop() {
        while (true) {
            val line = runCatching { stdoutReader.readLine() }.getOrNull() ?: break
            if (line.isBlank()) continue
            Log.d(TAG, "Recv: $line")
            try {
                val message = McpJson.decodeFromString<JSONRPCMessage>(line)
                _onMessage(message)
            } catch (e: SerializationException) {
                _onError(e)
            }
        }
        // stdout 结束 → 进程退出或主动关闭
        Log.d(TAG, "stdio server stdout closed")
        invokeOnCloseCallback()
    }
}