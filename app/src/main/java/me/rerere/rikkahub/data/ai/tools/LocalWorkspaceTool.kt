/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.koin.java.KoinJavaComponent

/**
 * 本地文件夹工作区：AI 可读写手机本地文件夹（项目），
 * 通过 SAF 授权（设置 → 系统工具 → 本地文件夹工作区）获取访问权限。
 *
 * 与系统工作区（proot 沙箱）互补：本地文件夹直接操作真实文件，
 * 适合让 AI 读写手机里的项目源码、文档等。
 */
fun createLocalWorkspaceTools(context: Context): List<Tool> {
    val settingsStore: SettingsStore = KoinJavaComponent.getKoin().get()

    fun rootDir(): DocumentFile? {
        val uriStr = settingsStore.settingsFlow.value.localWorkspaceUri
            ?: return null
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return null
        return DocumentFile.fromTreeUri(context, uri)
    }

    fun resolve(root: DocumentFile, path: String): DocumentFile? {
        if (path.isBlank()) return root
        var cur = root
        for (seg in path.trim('/').split('/')) {
            if (seg.isBlank() || seg == ".") continue
            if (seg == "..") {
                cur = cur.parentFile ?: return null
                continue
            }
            cur = cur.findFile(seg) ?: return null
        }
        return cur
    }

    fun needAuth(): UIMessagePart.Text = UIMessagePart.Text(buildJsonObject {
        put("error", "未授权本地文件夹工作区")
        put("tip", "请先在 设置 → 系统工具 → 本地文件夹工作区 中选择要授权的文件夹")
    }.toString())

    return listOf(
        Tool(
            name = "local_ws_status",
            description = "查询本地文件夹工作区状态（是否已授权、根目录 uri）。",
            needsApproval = false,
            parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
            execute = {
                val uri = settingsStore.settingsFlow.value.localWorkspaceUri
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("authorized", uri != null)
                    put("uri", uri ?: "")
                    put("tip", if (uri != null) "本地文件夹工作区已授权，可调用 local_ws_list/read/write 操作该文件夹" else "未授权，请先在设置中选择文件夹")
                }.toString()))
            }
        ),

        Tool(
            name = "local_ws_list",
            description = "列出本地文件夹工作区中的文件与目录（项目工作区）。Params: optional path(相对路径，空=根目录)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "相对路径，如 src/main 或空") })
                })
            },
            execute = { args ->
                val root = rootDir() ?: return@Tool listOf(needAuth())
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: ""
                val dir = resolve(root, path)
                if (dir == null) {
                    return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                        put("error", "路径不存在")
                        put("path", path)
                    }.toString()))
                }
                if (!dir.isDirectory) {
                    return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                        put("error", "不是目录")
                        put("path", path)
                    }.toString()))
                }
                val entries = dir.listFiles()
                    .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name.orEmpty() })
                    .map { f ->
                        buildJsonObject {
                            put("name", f.name ?: "")
                            put("type", if (f.isDirectory) "dir" else "file")
                            put("size", if (f.isFile) f.length() else 0)
                        }
                    }
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("path", path.ifBlank { "/" })
                    put("count", entries.size)
                    put("entries", buildJsonArray { entries.forEach { add(it) } })
                }.toString()))
            }
        ),

        Tool(
            name = "local_ws_read",
            description = "读取本地文件夹工作区中的文件内容（文本，最大 50KB）。Params: path(相对路径)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "相对路径，如 src/main/App.kt") })
                }, required = listOf("path"))
            },
            execute = { args ->
                val root = rootDir() ?: return@Tool listOf(needAuth())
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"path required"}"""))
                val file = resolve(root, path)
                if (file == null || !file.isFile) {
                    return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                        put("error", "文件不存在")
                        put("path", path)
                    }.toString()))
                }
                val content = runCatching {
                    context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
                }.getOrNull()
                if (content == null) {
                    return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                        put("error", "读取失败（可能是二进制或权限问题）")
                        put("path", path)
                    }.toString()))
                }
                val text = content.toString(Charsets.UTF_8)
                val truncated = text.length > 50000
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("path", path)
                    put("size", content.size)
                    put("truncated", truncated)
                    put("content", if (truncated) text.take(50000) else text)
                }.toString()))
            }
        ),

        Tool(
            name = "local_ws_write",
            description = "写入/创建本地文件夹工作区中的文件（文本，自动创建父目录）。Params: path(相对路径), content(文件内容)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "相对路径，如 src/main/App.kt 或 docs/note.md") })
                    put("content", buildJsonObject { put("type", "string"); put("description", "要写入的文本内容") })
                }, required = listOf("path", "content"))
            },
            execute = { args ->
                val root = rootDir() ?: return@Tool listOf(needAuth())
                val o = args.jsonObject
                val path = o["path"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"path required"}"""))
                val content = o["content"]?.jsonPrimitive?.contentOrNull ?: ""
                val segments = path.trim('/').split('/').filter { it.isNotBlank() }
                if (segments.isEmpty()) {
                    return@Tool listOf(UIMessagePart.Text("""{"error":"无效路径"}"""))
                }
                // 逐级创建父目录
                var cur = root
                for (i in 0 until segments.size - 1) {
                    val dirName = segments[i]
                    cur = cur.findFile(dirName) ?: cur.createDirectory(dirName) ?: return@Tool listOf(
                        UIMessagePart.Text(buildJsonObject { put("error", "创建目录失败"); put("dir", dirName) }.toString())
                    )
                }
                val fileName = segments.last()
                val target = cur.findFile(fileName) ?: cur.createFile("application/octet-stream", fileName)
                if (target == null) {
                    return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "创建文件失败") }.toString()))
                }
                val ok = runCatching {
                    context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                }.isSuccess
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", ok)
                    put("path", path)
                    put("bytes", content.toByteArray(Charsets.UTF_8).size)
                    put("message", if (ok) "写入成功" else "写入失败（权限或存储空间）")
                }.toString()))
            }
        ),

        Tool(
            name = "local_ws_delete",
            description = "删除本地文件夹工作区中的文件或空目录。Params: path(相对路径)",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "相对路径") })
                }, required = listOf("path"))
            },
            execute = { args ->
                val root = rootDir() ?: return@Tool listOf(needAuth())
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"path required"}"""))
                val file = resolve(root, path)
                if (file == null) {
                    return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "路径不存在") }.toString()))
                }
                val ok = file.delete()
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", ok)
                    put("path", path)
                    put("message", if (ok) "已删除" else "删除失败")
                }.toString()))
            }
        ),
    )
}
