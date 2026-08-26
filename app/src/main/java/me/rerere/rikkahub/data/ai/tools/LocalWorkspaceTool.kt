/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import java.io.File
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * 本地文件夹工作区：AI 直接读写手机全部文件夹。
 *
 * 仅支持「所有文件访问」模式：需拥有 MANAGE_EXTERNAL_STORAGE 权限，
 * 根目录为 /storage/emulated/0，AI 可访问手机全部真实文件夹，无需选目录。
 */
fun createLocalWorkspaceTools(context: Context): List<Tool> {
    /** 是否拥有「所有文件访问」权限 */
    fun hasAllFilesAccess(): Boolean =
        android.os.Environment.isExternalStorageManager()

    /** 根目录：/storage/emulated/0 */
    fun root(): File? = if (hasAllFilesAccess()) android.os.Environment.getExternalStorageDirectory() else null

    fun resolveFile(root: File, path: String): File? {
        var f = root
        for (seg in path.trim('/').split('/')) {
            if (seg.isBlank() || seg == ".") continue
            if (seg == "..") { f = f.parentFile ?: return null; continue }
            f = File(f, seg)
        }
        return f
    }

    fun needAuth(): UIMessagePart.Text = UIMessagePart.Text(buildJsonObject {
        put("error", "本地文件夹工作区不可用")
        put("tip", "需要「所有文件访问」权限才能读写手机全部文件夹：请到 设置 → 系统工具 → 本地文件夹工作区 → 开启所有文件访问")
    }.toString())

    return listOf(
        Tool(
            name = "local_ws_status",
            description = "查询本地文件夹工作区状态（根目录）。普通文件操作优先用 workspace_*(proot 工作区)；需要读写手机真实文件夹时用这个。",
            needsApproval = false,
            parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
            execute = {
                val root = root()
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("authorized", root != null)
                    put("mode", if (root != null) "all_files_access" else "unauthorized")
                    put("root", root?.absolutePath ?: "")
                    put("tip", if (root != null)
                        "所有文件访问已开启，AI 可读写 /storage/emulated/0 下全部文件夹"
                    else
                        "未开启所有文件访问，请到 设置 → 系统工具 开启")
                }.toString()))
            }
        ),
        Tool(
            name = "local_ws_list",
            description = "仅在需要读写手机真实文件夹时使用；若已绑定 proot 工作区，普通文件/代码操作请优先用 workspace_* 工具。功能：列出手机文件夹(根为 /storage/emulated/0)中目录。Params: optional path(相对路径，空=根目录)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "相对路径，如 Download、DCIM 或 空") })
                })
            },
            execute = { args ->
                val root = root() ?: return@Tool listOf(needAuth())
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull ?: ""
                val dir = resolveFile(root, path)
                if (dir == null || !dir.isDirectory) {
                    return@Tool listOf(UIMessagePart.Text(buildJsonObject {
                        put("error", "目录不存在"); put("path", path)
                    }.toString()))
                }
                val entries = dir.listFiles().orEmpty().filter { !it.name.isNullOrBlank() }
                    .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })
                    .map { f ->
                        buildJsonObject {
                            put("name", f.name)
                            put("type", if (f.isDirectory) "dir" else "file")
                            put("size", if (f.isFile) f.length() else 0L)
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
            description = "仅在需要读写手机真实文件夹时使用；若已绑定 proot 工作区，普通文件/代码操作请优先用 workspace_* 工具。功能：读取手机文件夹中的文件内容（文本，最大 50KB）。Params: path(相对路径)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "相对路径，如 Download/note.txt") })
                }, required = listOf("path"))
            },
            execute = { args ->
                val root = root() ?: return@Tool listOf(needAuth())
                val path = args.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"path required"}"""))
                val f = resolveFile(root, path)
                if (f == null || !f.isFile) {
                    return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "文件不存在"); put("path", path) }.toString()))
                }
                val fullSize = f.length()
                val readLen = if (fullSize > 50 * 1024) 50 * 1024 else fullSize.toInt()
                val bytes = runCatching { f.inputStream().use { it.readBytes().let { b -> if (b.size > 50 * 1024) b.copyOf(50 * 1024) else b } } }.getOrNull()
                    ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "读取失败"); put("path", path) }.toString()))
                val text = bytes.toString(Charsets.UTF_8)
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
            description = "仅在需要读写手机真实文件夹时使用；若已绑定 proot 工作区，普通文件/代码操作请优先用 workspace_* 工具。功能：写入/创建手机文件夹中的文件（文本，自动创建父目录）。Params: path(相对路径), content(文件内容)",
            needsApproval = false,
            parameters = {
                InputSchema.Obj(properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string"); put("description", "相对路径，如 Download/note.txt 或 docs/a.md") })
                    put("content", buildJsonObject { put("type", "string"); put("description", "要写入的文本内容") })
                }, required = listOf("path", "content"))
            },
            execute = { args ->
                val root = root() ?: return@Tool listOf(needAuth())
                val o = args.jsonObject
                val path = o["path"]?.jsonPrimitive?.contentOrNull
                    ?: return@Tool listOf(UIMessagePart.Text("""{"error":"path required"}"""))
                val content = o["content"]?.jsonPrimitive?.contentOrNull ?: ""
                val target = resolveFile(root, path) ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "非法路径"); put("path", path) }.toString()))
                val ok = runCatching {
                    target.parentFile?.mkdirs()
                    target.writeText(content)
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
            description = "仅在需要读写手机真实文件夹时使用；若已绑定 proot 工作区，普通文件/代码操作请优先用 workspace_* 工具。功能：删除手机文件夹中的文件或空目录。Params: path(相对路径)",
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
                val f = resolveFile(root, path) ?: return@Tool listOf(UIMessagePart.Text(buildJsonObject { put("error", "路径不存在") }.toString()))
                val ok = f.delete()
                listOf(UIMessagePart.Text(buildJsonObject {
                    put("success", ok)
                    put("path", path)
                    put("message", if (ok) "已删除" else "删除失败")
                }.toString()))
            }
        ),
    )
}