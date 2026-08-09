package me.rerere.rikkahub.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    /**
     * 全量备份：把 App 私有目录下的「全部数据」打包成单个 zip。
     * dataDir 下通常包含：
     *   - databases/   Room 数据库 (rikka_hub.db / -wal / -shm)
     *   - files/       所有文件（工作区 workspace、上传、媒体、DataStore 设置、技能、插件、字体、图片等）
     *   - shared_prefs/ SharedPreferences
     *   - app_webview/ 等其它运行时数据
     * 仅排除 cache / code_cache（可随时重建的缓存）。
     */
    suspend fun export(context: Context, dest: Uri): String = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "backup_all.zip")
        ZipOutputStream(FileOutputStream(tmp)).use { zip ->
            val dataDir = context.dataDir
            if (dataDir.exists() && dataDir.isDirectory) {
                // 遍历 dataDir 下每个子目录，排除 cache / code_cache
                dataDir.listFiles()?.sortedBy { it.name }?.forEach { dir ->
                    val name = dir.name
                    if (dir.isDirectory && name != "cache" && name != "code_cache") {
                        addSourcedir(dir, "$name/", zip) { true }
                    }
                }
            } else {
                // 兜底：至少备份 database / files / shared_prefs（极端情况下 dataDir 不可用）
                val dbDir = context.getDatabasePath("rikka_hub").parentFile
                addSourcedir(dbDir, "databases/", zip) { true }
                addSourcedir(context.filesDir, "files/", zip) { true }
                val prefsDir = File(context.filesDir, "../shared_prefs").canonicalFile
                addSourcedir(prefsDir, "shared_prefs/", zip) { true }
            }
        }
        context.contentResolver.openOutputStream(dest)?.use { out ->
            tmp.inputStream().use { it.copyTo(out) }
        }
        val sizeKb = tmp.length() / 1024; tmp.delete()
        "导出完成: ${sizeKb}KB (全部数据已备份)"
    }

    suspend fun restore(context: Context, src: Uri): String = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "restore_all.zip")
        context.contentResolver.openInputStream(src)?.use { input ->
            FileOutputStream(tmp).use { out -> input.copyTo(out) }
        } ?: return@withContext "无法读取备份文件"
        val base = context.dataDir  // /data/data/pkg
        ZipInputStream(tmp.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val target = File(base, e.name)
                    if (!target.canonicalPath.startsWith(base.canonicalPath + File.separator)) {
                        throw SecurityException("Zip entry escapes target directory: ${e.name}")
                    }
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { o -> zip.copyTo(o) }
                }
                e = zip.nextEntry
            }
        }
        tmp.delete()
        "恢复完成，请重启App"
    }

    /** 把源目录下满足 [filter] 的文件写入 zip（保持相对路径）。 */
    private fun addSourcedir(dir: File?, prefix: String, zip: ZipOutputStream, filter: (String) -> Boolean) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        dir.walkTopDown()
            .filter { it.isFile && filter(prefix + it.relativeTo(dir).path) }
            .forEach { f ->
                zip.putNextEntry(ZipEntry(prefix + f.relativeTo(dir).path))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
    }
}