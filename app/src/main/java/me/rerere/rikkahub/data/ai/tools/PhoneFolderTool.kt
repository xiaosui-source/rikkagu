package me.rerere.rikkahub.data.ai.tools

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.koin.java.KoinJavaComponent

private const val TAG = "PhoneFolderTool"

private fun getSettingsStore(): SettingsStore = KoinJavaComponent.getKoin().get()

/** 列出授权文件夹下的一级条目（文件/子文件夹） */
private fun listFolderEntries(
    contentResolver: ContentResolver,
    treeUri: Uri,
): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val docId = DocumentsContract.getTreeDocumentId(treeUri)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
    contentResolver.query(
        childrenUri,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        ),
        null, null, null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getString(0)
            val name = cursor.getString(1)
            val mime = cursor.getString(2)
            val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
            result.add("$name${if (isDir) "/" else ""}" to docUri.toString())
        }
    }
    return result
}

/** 读取文件内容（文本，限制大小） */
private fun readFileText(
    contentResolver: ContentResolver,
    uri: Uri,
    maxBytes: Int = 64 * 1024,
): String {
    contentResolver.openInputStream(uri)?.use { input ->
        val bytes = input.readNBytes(maxBytes + 1)
        val limited = bytes.take(maxBytes).toByteArray()
        return limited.toString(Charsets.UTF_8)
    } ?: return "(无法打开文件)"
}

fun createReadPhoneFolderTool(context: Context): Tool = Tool(
    name = "read_phone_folder",
    description = "读取用户在设置中授权访问的手机文件夹。可列出文件夹内容或读取某个文本文件。如果未授权会提示用户先到设置中授权。",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("path") {
                    put("type", "string")
                    put("description", "可选：要读取的子路径（如 文档/notes.txt）。留空则列出授权文件夹根目录的一级条目")
                }
                putJsonObject("max_files") {
                    put("type", "integer")
                    put("description", "可选：列出文件的最大数量，默认 50")
                }
            }
        )
    },
    execute = { params ->
        val obj = params.jsonObject
        val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: ""
        val maxFiles = obj["max_files"]?.jsonPrimitive?.intOrNull ?: 1000
        val settingsStore = getSettingsStore()
        val uriStr = settingsStore.settingsFlow.value.phoneFolderUri
        if (uriStr.isNullOrBlank()) {
            return@Tool listOf(UIMessagePart.Text(
                "尚未授权手机文件夹。请到 设置 → 系统工具 → 选择手机文件夹 完成授权后再试。"
            ))
        }
        val treeUri = runCatching { Uri.parse(uriStr) }.getOrNull()
        if (treeUri == null) {
            return@Tool listOf(UIMessagePart.Text("授权的文件夹 URI 无效，请重新授权。"))
        }
        val resolver = context.contentResolver

        if (path.isBlank()) {
            // 列出根目录
            val entries = listFolderEntries(resolver, treeUri).take(maxFiles)
            if (entries.isEmpty()) {
                return@Tool listOf(UIMessagePart.Text("授权文件夹为空。"))
            }
            val sb = StringBuilder("授权文件夹内容（${entries.size} 项）：\n")
            entries.forEach { (name, _) -> sb.appendLine("- $name") }
            return@Tool listOf(UIMessagePart.Text(sb.toString()))
        }

        // 读取指定子路径：沿 DocumentsContract 逐级下钻
        val segments = path.trim('/').split('/')
        var currentUri = treeUri
        var currentDocId = DocumentsContract.getTreeDocumentId(treeUri)
        var found = false
        for (seg in segments) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(currentUri, currentDocId)
            var matchedUri: Uri? = null
            var matchedDocId: String? = null
            var matchedMime: String? = null
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1)
                    if (name == seg) {
                        matchedDocId = id
                        matchedMime = cursor.getString(2)
                        matchedUri = DocumentsContract.buildDocumentUriUsingTree(currentUri, id)
                        break
                    }
                }
            }
            if (matchedUri == null || matchedDocId == null) {
                return@Tool listOf(UIMessagePart.Text("未找到路径: $path"))
            }
            currentUri = matchedUri
            currentDocId = matchedDocId
            if (matchedMime != DocumentsContract.Document.MIME_TYPE_DIR) {
                found = true
                // 是文件：读取内容
                val text = readFileText(resolver, currentUri)
                return@Tool listOf(UIMessagePart.Text(text))
            }
        }
        if (!found) {
            // 最终是目录：列出其内容
            val entries = listFolderEntries(resolver, currentUri).take(maxFiles)
            val sb = StringBuilder("$path/ 内容（${entries.size} 项）：\n")
            entries.forEach { (name, _) -> sb.appendLine("- $name") }
            return@Tool listOf(UIMessagePart.Text(sb.toString()))
        }
        listOf(UIMessagePart.Text("(无内容)"))
    },
    needsApproval = false,
)
