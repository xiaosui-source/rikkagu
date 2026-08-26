/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
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
 * 本地文件夹工作区：AI 可读写手机本地文件夹（全部文件 / 项目）。
 *
 * 两种访问模式：
 * 1. 【所有文件访问】当拥有 MANAGE_EXTERNAL_STORAGE 权限时，根目录为 /storage/emulated/0，
 *    AI 可直接读写手机全部文件夹，无需手动 SAF 选目录。
 * 2. 【SAF 授权】无所有文件权限时退回设置 → 系统工具 → 本地文件夹工作区 授权的目录。
 *
 * 与系统工作区（proot 沙箱）互补：本地文件夹直接操作真实文件，
 * 适合让 AI 读写手机里的项目源码、文档等。
 */
fun createLocalWorkspaceTools(context: Context): List<Tool> {
    val settingsStore: SettingsStore = KoinJavaComponent.getKoin().get()

    /** 是否拥有「所有文件访问」权限 */
    fun hasAllFilesAccess(): Boolean =
        android.os.Environment.isExternalStorageManager()

    /** 根节点统一抽象：优先真实文件(全部文件夹)，退回 SAF */
    sealed class Root {
        data class FileRoot(val dir: java.io.File) : Root()
        data class SafRoot(val doc: DocumentFile) : Root()
    }

    fun root(): Root? {
        if (hasAllFilesAccess()) {
            val base = android.os.Environment.getExternalStorageDirectory() // /storage/emulated/0
            return Root.FileRoot(base)
        }
        val uriStr = settingsStore.settingsFlow.value.localWorkspaceUri ?: return null
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return null
        return Root.SafRoot(DocumentFile.fromTreeUri(context, uri))
    }

    fun resolveFile(root: Root.FileRoot, path: String): java.io.File? {
        var f = root.dir
        for (seg in path.trim('/').split('/')) {
            if (seg.isBlank() || seg == ".") continue
            if (seg == "..") { f = f.parentFile ?: return null; continue }
            f = java.io.File(f, seg)
        }
        return f
    }

    fun resolveDoc(root: Root.SafRoot, path: String): DocumentFile? {
        var cur: DocumentFile = root.doc
        for (seg in path.trim('/').split('/')) {
            if (seg.isBlank() || seg == ".") continue
            if (seg == "..") { cur = cur.parentFile ?: return null; continue }
            cur = cur.findFile(seg) ?: return null
        }
        return cur
    }

    fun needAuth(): UIMessagePart.Text = UIMessagePart.Text(buildJsonObject {
        put("error", "本地文件夹工作区不可用")
        put("tip", "需要「所有文件访问」权限才能读写手机全部文件夹：请到 设置 → 系统工具 → 所有文件访问 里开启并重启授权；或到 设置 → 系统工具 → 本地文件夹工作区 选一个 SAF 文件夹作为备选")
    }.toString())

    return listOf(
        Tool(
            name = "local_ws_status",
            description = "查询本地文件夹工作区状态（当前根目录、模式）。普通文件操作优先用 workspace_*(proot 工作区)；需要读写手机真实文件夹时用这个。",
            needsApproval = false,
            parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
            execute = {
                val m = hasAllFilesAccess()
                val root = root()
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("mode", if (m) "all_files_access" else "saf")
                    put("root", when (root) {
                        is Root.FileRoot -> root.dir.absolutePath
                        is Root.SafRoot -> root.doc.uri.toString()
                        else -> ""
                    })
                    put("tip", if (m)
                        "当前为「所有文件访问」模式，AI 可读写 /storage/emulated/0 下全部文件夹，无需选目录"
                    else
                        "当前为 SAF 模式，仅能访问已授权目录。建议开启「所有文件访问」以访问全部文件夹")
                }.toString()))
            }
        ),
        Tool(
            name = "local_ws_list",
            description = "仅在需要读写手机真实文件夹时使用；若已绑定 proot 工作区，普通文件/代码操作请优先用 workspace_* 工具。功能：列出本地文件夹(全部文件模式根为 /storage/emulated/0)中目录。Params: optional path(相对路径，空=根目录)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "相对路径，如 Download 或 空") })
                })
            },
            execute = { args ->
                val root = root() ?: return@Tool listOf(needAuth())
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: ""
                val entries = listDir(root, path)
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("path", path.ifBlank { "/" })
                    put("count", entries.size)
                    put("entries", buildJsonArray {
                        entries.forEach { (n, d, s) ->
                            add(buildJsonObject {
                                put("name", n); put("type", if (d) "dir" else "file"); put("size", s)
                            })
                        }
                    })
                }.toString()))
            }
        ),
        Tool(
            name = "local_ws_read",
            description = "仅在需要读写手机真实文件夹时使用；若已绑定 proot 工作区，普通文件/代码操作请优先用 workspace_* 工具。功能：读取本地文件夹中的文件内容（文本，最大 50KB）。Params: path(相对路径)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "相对路径，如 src/main/App.kt") })
                }, required = listOf("path"))
            },
            execute = { args ->
                val root = root() ?: return@Tool listOf(needAuth())
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"path required"}"""))
                val bytes = when (root) {
                    is Root.FileRoot -> {
                        val f = resolveFile(root, path) ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "文件不存在"); put("path", path) }.toString()))
                        if (!f.isFile) return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "不是一个文件"); put("path", path) }.toString()))
                        runCatching { f.readBytes().let { if (it.size > 50 * 1024) it.copyOf(50 * 1024) else it } }.getOrNull()
                            ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "读取失败"); put("path", path) }.toString()))
                    }
                    is Root.SafRoot -> {
                        val f = resolveDoc(root, path) ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "文件不存在"); put("path", path) }.toString()))
                        if (!f.isFile) return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "不是一个文件"); put("path", path) }.toString()))
                        runCatching { context.contentResolver.openInputStream(f.uri)?.use { it.readBytes().let { b -> if (b.size > 50 * 1024) b.copyOf(50 * 1024) else b } } }.getOrNull()
                            ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "读取失败"); put("path", path) }.toString()))
                    }
                }
                val text = bytes.toString(Charsets.UTF_8)
                val fullSize = when (root) {
                    is Root.FileRoot -> resolveFile(root, path)?.length() ?: bytes.size
                    is Root.SafRoot -> resolveDoc(root, path)?.length() ?: bytes.size
                }
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("path", path)
                    put("size", fullSize)
                    put("truncated", fullSize > 50000)
                    put("content", text)
                }.toString()))
            }
        ),
        Tool(
            name = "local_ws_write",
            description = "仅在需要读写手机真实文件夹时使用；若已绑定 proot 工作区，普通文件/代码操作请优先用 workspace_* 工具。功能：写入/创建本地文件夹中的文件（文本，自动创建父目录）。Params: path(相对路径), content(文件内容)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "相对路径，如 src/main/App.kt 或 docs/note.md") })
                    put("content", buildJsonObject { put("type", "string"); put("description", "要写入的文本内容") })
                }, required = listOf("path", "content"))
            },
            execute = { args ->
                val root = root() ?: return@Tool listOf(needAuth())
                val o = args.jsonObject
                val path = o["path"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"path required"}"""))
                val content = o["content"]?.jsonPrimitive?.contentOrNull ?: ""
                val ok = when (root) {
                    is Root.FileRoot -> {
                        val target = java.io.File(resolveFile(root, path).let { if (it == null) java.io.File(root.dir, path) else it }.absolutePath)
                        runCatching {
                            target.parentFile?.mkdirs()
                            target.writeText(content)
                        }.isSuccess
                    }
                    is Root.SafRoot -> {
                        val segments = path.trim('/').split('/').filter { it.isNotBlank() }
                        if (segments.isEmpty()) return@Tool listOf(UIMessagePart.Text("""{"error":"invalid path"}"""))
                        var cur = root.doc
                        for (i in 0 until segments.size - 1) {
                            val dirName = segments[i]
                            cur = cur.findFile(dirName) ?: cur.createDirectory(dirName) ?: return@Tool listOf(
                                UIMessagePart.Text(buildJsonObject { put("error", "创建目录失败"); put("dir", dirName) }.toString())
                            )
                        }
                        val fileName = segments.last()
                        val target = cur.findFile(fileName) ?: cur.createFile("application/octet-stream", fileName)
                            ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "创建文件失败") }.toString()))
                        runCatching {
                            context.contentResolver.openOutputStream(target.uri, "wt")?.use { out ->
                                out.write(content.toByteArray(Charsets.UTF_8))
                            }
                        }.isSuccess
                    }
                }
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
            description = "仅在需要读写手机真实文件夹时使用；若已绑定 proot 工作区，普通文件/代码操作请优先用 workspace_* 工具。功能：删除本地文件夹中的文件或空目录。Params: path(相对路径)",
            needsApproval = true,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "相对路径") })
                }, required = listOf("path"))
            },
            execute = { args ->
                val root = root() ?: return@Tool listOf(needAuth())
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"path required"}"""))
                val ok = when (root) {
                    is Root.FileRoot -> {
                        val f = resolveFile(root, path) ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "路径不存在") }.toString()))
                        f.delete()
                    }
                    is Root.SafRoot -> {
                        val f = resolveDoc(root, path) ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "路径不存在") }.toString()))
                        f.delete()
                    }
                }
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", ok)
                    put("path", path)
                    put("message", if (ok) "已删除" else "删除失败")
                }.toString()))
            }
        ),
    )
}