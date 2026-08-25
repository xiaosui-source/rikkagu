/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.workspace.RootfsInstallProgress
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.WorkspaceStorageArea
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import kotlin.uuid.Uuid

class WorkspaceRepository(
    private val dao: WorkspaceDAO,
    private val manager: WorkspaceManager,
    private val rootfsInstaller: RootfsInstaller,
    private val settingsStore: SettingsStore,
) {
    fun listFlow(): Flow<List<WorkspaceEntity>> = dao.listFlow()

    suspend fun checkIntegrity() = withContext(Dispatchers.IO) {
        val workspaces = dao.getAll()
        for (workspace in workspaces) {
            val dir = manager.workspaceDir(workspace.root)
            if (!dir.exists()) {
                Log.w(TAG, "Workspace directory missing, removing record: id=${workspace.id}, root=${workspace.root}")
                dao.deleteById(workspace.id)
                cleanupAssistantReferences(workspace.id)
                continue
            }
            val statusName = workspace.shellStatus
            if ((statusName == WorkspaceShellStatus.READY.name || statusName == WorkspaceShellStatus.INSTALLING.name)
                && !manager.hasRootfs(workspace.root)
            ) {
                Log.w(TAG, "Rootfs missing, resetting shell status: id=${workspace.id}")
                updateShellState(workspace.id, WorkspaceShellStatus.DISABLED.name)
            }
        }
    }

    suspend fun getById(id: String): WorkspaceEntity? = dao.getById(id)

    suspend fun create(name: String): WorkspaceEntity {
        val id = Uuid.random().toString()
        val now = System.currentTimeMillis()
        val finalName = name.trim().ifBlank { "Workspace" }
        require(!isNameTaken(finalName, excludeId = null)) {
            "Workspace name already exists: $finalName"
        }
        val workspace = WorkspaceEntity(
            id = id,
            name = finalName,
            root = id,
            createdAt = now,
            updatedAt = now,
            lastAccessAt = null,
        )
        manager.ensureWorkspace(workspace.root)
        dao.upsert(workspace)
        return workspace
    }

    suspend fun rename(id: String, name: String): Boolean {
        val workspace = dao.getById(id) ?: return false
        val finalName = name.trim().ifBlank { workspace.name }
        require(!isNameTaken(finalName, excludeId = id)) {
            "Workspace name already exists: $finalName"
        }
        dao.upsert(
            workspace.copy(
                name = finalName,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    /** 名字是否已被其他 workspace 占用（trim 后精确匹配，排除 [excludeId] 自身） */
    suspend fun isNameTaken(name: String, excludeId: String?): Boolean {
        val target = name.trim()
        return dao.getAll().any { it.id != excludeId && it.name.trim() == target }
    }

    suspend fun setToolApproval(id: String, toolName: String, needsApproval: Boolean): Boolean {
        val workspace = dao.getById(id) ?: return false
        val overrides = workspace.toolApprovalOverrides() + (toolName to needsApproval)
        dao.upsert(
            workspace.copy(
                toolApprovals = JsonInstant.encodeToString(overrides),
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    suspend fun installRootfs(
        id: String,
        url: String,
        onProgress: (RootfsInstallProgress) -> Unit = {},
    ): Boolean {
        val workspace = dao.getById(id) ?: return false
        updateShellState(workspace, WorkspaceShellStatus.INSTALLING.name)
        try {
            // runInterruptible 让协程取消转成线程中断, 打断 install 内阻塞的下载/解压循环
            runInterruptible(Dispatchers.IO) {
                rootfsInstaller.install(workspace.root, url, onProgress)
            }
            updateShellState(workspace, WorkspaceShellStatus.READY.name)
            return true
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                restoreShellState(workspace)
            }
            throw e
        } catch (e: InterruptedException) {
            withContext(NonCancellable) {
                restoreShellState(workspace)
            }
            throw CancellationException("Rootfs install cancelled").also { it.initCause(e) }
        } catch (e: Throwable) {
            Log.e(TAG, "installRootfs failed: workspace=${workspace.id}, root=${workspace.root}, url=$url", e)
            updateShellState(workspace, WorkspaceShellStatus.BROKEN.name)
            throw e
        }
    }

    /**
     * 自动下载并安装 rootfs (Ubuntu 24.04 base arm64, 官方源)。
     * 需 shell 可访问网络。后台调用, 失败抛异常由调用方容错。
     */
    suspend fun installDefaultRootfs(
        id: String,
        onProgress: (RootfsInstallProgress) -> Unit = {},
    ): Boolean = installRootfs(id, DEFAULT_ROOTFS_URL, onProgress)

    /** 对已就绪的 rootfs 重新执行 patch (修复 passwd/group 等, 版本升级用) */
    suspend fun patchRootfs(id: String) = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: return@withContext
        runCatching {
            rootfsInstaller.patchExisting(workspace.root)
            // 修复旧版 jadx 安装路径错误（旧版链接指向 /opt/jadx-1.5.1/bin/jadx）
            runCatching {
                executeCommand(
                    id,
                    "ln -sf /opt/bin/jadx /usr/local/bin/jadx 2>/dev/null; " +
                        "ln -sf /opt/bin/jadx-gui /usr/local/bin/jadx-gui 2>/dev/null",
                    timeoutMillis = 10_000,
                )
            }
        }.onFailure { e ->
            Log.w(TAG, "patchRootfs failed: workspace=${workspace.id}", e)
        }
    }

    suspend fun listFiles(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
    ): List<WorkspaceFileEntry> = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: return@withContext emptyList()
        manager.ensureWorkspace(workspace.root)
        manager.listFiles(workspace.root, path, area)
    }

    suspend fun readText(
        id: String,
        path: String,
    ): String = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.readText(workspace.root, path)
    }

    /** 读取原始字节（用于图片/二进制预览） */
    suspend fun readBytes(
        id: String,
        path: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.readBytes(workspace.root, path)
    }

    suspend fun writeText(
        id: String,
        path: String,
        text: String,
        overwrite: Boolean,
    ): WorkspaceFileEntry = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.writeText(workspace.root, path, text, overwrite)
    }

    suspend fun importFile(
        id: String,
        area: WorkspaceStorageArea,
        destinationPath: String,
        fileName: String,
        inputStream: InputStream,
    ): WorkspaceFileEntry = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.importFile(workspace.root, destinationPath, area, fileName, inputStream)
    }

    suspend fun fileSize(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
    ): Long = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.fileSize(workspace.root, path, area)
    }

    suspend fun exportFile(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
        outputStream: OutputStream,
    ) = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.exportFile(workspace.root, path, area, outputStream)
    }

    suspend fun deleteFile(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
        recursive: Boolean,
    ): Boolean {
        val deleted = withContext(Dispatchers.IO) {
            val workspace = dao.getById(id) ?: return@withContext false
            manager.deleteFile(workspace.root, path, recursive, area)
        }
        return deleted
    }

    suspend fun moveFile(
        id: String,
        source: String,
        target: String,
        overwrite: Boolean,
    ): WorkspaceFileEntry = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.moveFile(workspace.root, source, target, overwrite)
    }

    suspend fun executeCommand(
        id: String,
        command: String,
        cwd: String = "",
        timeoutMillis: Long = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
        stdin: ByteArray? = null,
    ): WorkspaceCommandResult {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        // runInterruptible 让协程取消转化为线程中断，从而打断阻塞的 Process.waitFor 并杀掉进程
        return runInterruptible(Dispatchers.IO) {
            manager.ensureWorkspace(workspace.root)
            manager.executeCommand(workspace.root, command, cwd, timeoutMillis, stdin)
        }
    }

    suspend fun buildApk(
        id: String,
        projectDir: String = "",
        task: String = "assembleRelease",
        onProgress: (String) -> Unit = {},
    ): WorkspaceCommandResult {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")

        onProgress("开始构建 ($task)...")
        val projectPath = if (projectDir.isBlank()) "/workspace" else "/workspace/${projectDir.trim('/')}"
        val cmd = buildString {
            appendLine("export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64")
            appendLine("export ANDROID_HOME=/opt/android-sdk")
            appendLine("export ANDROID_SDK_ROOT=/opt/android-sdk")
            appendLine("export PATH=\${'$'}JAVA_HOME/bin:/opt/android-sdk/platform-tools:\${'$'}PATH")
            appendLine("export CI=true")
            // 自动按手机内存调节 JVM 堆/worker 数，防止编译 OOM 卡死
            appendLine("MEM_KB=\${'$'}(grep MemTotal /proc/meminfo | awk '{print \${'$'}2}')")
            appendLine("MEM_GB=\${'$'}(( MEM_KB / 1024 / 1024 ))")
            appendLine("if [ \${'$'}MEM_GB -ge 12 ]; then HEAP=4096; KOTLIN=2048; WORKERS=4;")
            appendLine("elif [ \${'$'}MEM_GB -ge 8 ]; then HEAP=3072; KOTLIN=1536; WORKERS=3;")
            appendLine("elif [ \${'$'}MEM_GB -ge 6 ]; then HEAP=2048; KOTLIN=1024; WORKERS=2;")
            appendLine("else HEAP=1536; KOTLIN=768; WORKERS=2; fi")
            appendLine("echo \"## 内存自适应: 手机\${'$'}MEM_GB GB → Gradle堆\${'$'}HEAP MB / Kotlin\${'$'}KOTLIN MB / workers=\${'$'}WORKERS\"")
            appendLine("export JAVA_TOOL_OPTIONS=\"-Xmx\${'$'}HEAP\${'$'}m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8\"")
            appendLine("export GRADLE_OPTS=\"-Dorg.gradle.workers.max=\${'$'}WORKERS\"")
            appendLine("cd $projectPath")
            // 覆盖项目 gradle.properties 的硬编码内存（备份原文件）
            appendLine("if [ -f gradle.properties ]; then cp gradle.properties gradle.properties.bak; sed -i '/org.gradle.jvmargs/d; /kotlin.daemon.jvmargs/d; /org.gradle.workers.max/d; /org.gradle.parallel/d; /org.gradle.caching/d' gradle.properties; fi")
            appendLine("printf 'org.gradle.jvmargs=-Xmx\${'$'}HEAP\${'$'}m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8\\norg.gradle.workers.max=\${'$'}WORKERS\\nkotlin.daemon.jvmargs=-Xmx\${'$'}KOTLIN\${'$'}m\\norg.gradle.parallel=true\\norg.gradle.caching=true\\n' >> gradle.properties 2>/dev/null || true")
            appendLine("ls settings.gradle* build.gradle* >/dev/null 2>&1 || { echo 'NO_GRADLE_PROJECT: 该目录不是 Android 项目（缺少 settings.gradle/build.gradle）'; exit 1; }")
            appendLine("chmod +x gradlew 2>/dev/null || true")
            appendLine("if [ -x ./gradlew ]; then ./gradlew $task --no-daemon --stacktrace 2>&1 | tail -150; else gradle $task 2>&1 | tail -150; fi")
            appendLine("echo '===APK-PATHS==='")
            appendLine("find . -name '*.apk' 2>/dev/null | head -8")
        }
        return executeCommand(id, cmd, timeoutMillis = 1_800_000)
    }

    /**
     * APK 二改完整链路：反编译 (apktool/jadx) → 修改 → 重打包 (apktool b) → 签名 (apksigner)。
     *
     * @param action decode=反编译 / build=重打包 / sign=签名 / full=反编译+重打包+签名
     * @param apkPath 工作区内 APK 路径（如 /workspace/input.apk）
     * @param outputName 输出 APK 文件名（默认 signed.apk）
     */
    suspend fun reworkApk(
        id: String,
        action: String = "full",
        apkPath: String,
        outputName: String = "signed.apk",
        onProgress: (String) -> Unit = {},
    ): WorkspaceCommandResult {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")

        onProgress("APK 二改：$action ...")
        val workDir = "/workspace/apk_rework"
        val baseName = apkPath.substringAfterLast('/').substringBeforeLast('.').ifBlank { "app" }
        val decodedDir = "$workDir/$baseName"
        val unsignedApk = "$workDir/${baseName}_unsigned.apk"
        val signedApk = "$workDir/$outputName"
        val keystore = "/opt/rework.keystore"

        val cmd = buildString {
            appendLine("export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64")
            appendLine("export ANDROID_HOME=/opt/android-sdk")
            appendLine("export ANDROID_SDK_ROOT=/opt/android-sdk")
            appendLine("export PATH=\${'$'}JAVA_HOME/bin:/opt/android-sdk/build-tools/36.0.0:/opt/android-sdk/platform-tools:\${'$'}PATH")
            // 自动按手机内存调节 JVM 堆，防止大 APK 反编译/重打包 OOM
            appendLine("MEM_KB=\${'$'}(grep MemTotal /proc/meminfo | awk '{print \${'$'}2}')")
            appendLine("MEM_GB=\${'$'}(( MEM_KB / 1024 / 1024 ))")
            appendLine("if [ \${'$'}MEM_GB -ge 12 ]; then HEAP=4096;")
            appendLine("elif [ \${'$'}MEM_GB -ge 8 ]; then HEAP=3072;")
            appendLine("elif [ \${'$'}MEM_GB -ge 6 ]; then HEAP=2048;")
            appendLine("else HEAP=1536; fi")
            appendLine("echo \"## 内存自适应: 手机\${'$'}MEM_GB GB → JVM堆\${'$'}HEAP MB\"")
            appendLine("export JAVA_TOOL_OPTIONS=\"-Xmx\${'$'}HEAP\${'$'}m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8\"")
            appendLine("mkdir -p $workDir")
            // 确保签名 key
            appendLine("if [ ! -f $keystore ]; then keytool -genkeypair -keystore $keystore -alias rework -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname \"CN=Android,O=Rework,C=CN\" 2>&1 | tail -2; fi")
            when (action) {
                "decode" -> {
                    appendLine("echo '=== DECODE ==='")
                    appendLine("rm -rf $decodedDir")
                    appendLine("apktool d $apkPath -o $decodedDir -f 2>&1 | tail -30")
                    appendLine("echo '=== DONE ==='")
                    appendLine("echo '解码完成，目录: $decodedDir （可用 workspace_shell/read_file 修改 smali/资源，改完执行 build）'")
                }
                "build" -> {
                    appendLine("echo '=== BUILD ==='")
                    appendLine("apktool b $decodedDir -o $unsignedApk 2>&1 | tail -40")
                    appendLine("echo '=== DONE ==='")
                    appendLine("echo '重打包完成: $unsignedApk （下一步 sign 签名）'")
                }
                "sign" -> {
                    appendLine("echo '=== SIGN ==='")
                    appendLine("apksigner sign --ks $keystore --ks-key-alias rework --ks-pass pass:android --key-pass pass:android --out $signedApk $unsignedApk 2>&1 | tail -10")
                    appendLine("echo '=== DONE ==='")
                    appendLine("echo '签名完成: $signedApk'")
                }
                else -> { // full
                    appendLine("echo '=== 1/3 DECODE ==='")
                    appendLine("rm -rf $decodedDir")
                    appendLine("apktool d $apkPath -o $decodedDir -f 2>&1 | tail -20")
                    appendLine("echo '=== 2/3 BUILD ==='")
                    appendLine("apktool b $decodedDir -o $unsignedApk 2>&1 | tail -30")
                    appendLine("echo '=== 3/3 SIGN ==='")
                    appendLine("apksigner sign --ks $keystore --ks-key-alias rework --ks-pass pass:android --key-pass pass:android --out $signedApk $unsignedApk 2>&1 | tail -10")
                    appendLine("echo '=== APK ==='")
                    appendLine("ls -lh $signedApk 2>/dev/null")
                    appendLine("echo '=== DONE ==='")
                    appendLine("echo '二改流程完成。提示：反编译后可先用 workspace_shell 查看 $decodedDir 结构，改完再跑一次 action=full 或分别 build+sign'")
                }
            }
        }
        return executeCommand(id, cmd, timeoutMillis = 1_800_000)
    }

    /**
     * APK 脱壳：壳检测 + 明文 dex 提取 + 生成 Frida 脱壳脚本。
     *
     * proot 内能做的：识别加固厂商、提取未加密的 dex/assets、生成 Frida dump 脚本供真机(root)使用；
     * 强壳(360v7/腾讯等)需真机 Frida/BlackDex，脚本会一并生成。
     */
    suspend fun unpackApk(
        id: String,
        apkPath: String,
        onProgress: (String) -> Unit = {},
    ): WorkspaceCommandResult {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")

        onProgress("开始脱壳分析：$apkPath ...")
        val base = apkPath.substringAfterLast('/').substringBeforeLast('.').ifBlank { "app" }
        val workDir = "/workspace/apk_unpack/$base"
        val cmd = buildString {
            appendLine("export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64")
            appendLine("export PATH=\${'$'}JAVA_HOME/bin:/opt/android-sdk/platform-tools:\${'$'}PATH")
            appendLine("mkdir -p $workDir/extracted")
            appendLine("cd $workDir")
            appendLine("cp $apkPath input.apk")
            appendLine("cd extracted && unzip -qo ../input.apk 2>/dev/null; cd ..")
            appendLine("echo '=== 1. 原生库 (so) ==='")
            appendLine("find extracted -name '*.so' 2>/dev/null | xargs -I{} basename {} | sort -u")
            appendLine("echo '=== 2. assets 目录 ==='")
            appendLine("ls extracted/assets/ 2>/dev/null | head -30")
            appendLine("echo '=== 3. 明文 dex ==='")
            appendLine("find extracted -name '*.dex' -o -name 'classes*.apk' 2>/dev/null | head -20")
            appendLine("echo '=== 4. 壳检测 ==='")
            appendLine("python3 - <<'PYEOF'")
            appendLine("import os, re")
            appendLine("root = 'extracted'")
            appendLine("sos = []")
            appendLine("for dp, dn, fn in os.walk(root):")
            appendLine("    for f in fn:")
            appendLine("        if f.endswith('.so'): sos.append(f)")
            appendLine("sos = set(sos)")
            appendLine("shell = None")
            appendLine("if any('jiagu' in s for s in sos): shell = '360加固 (libjiagu.so)'")
            appendLine("elif any('DexHelper' in s or 'shell' in s or 'tprt' in s for s in sos): shell = '腾讯乐固 (libDexHelper/libshell)'")
            appendLine("elif any('nqshield' in s or 'nesec' in s for s in sos): shell = '梆梆加固 (libnqshield)'")
            appendLine("elif any('ddog' in s or 'dexprotect' in s for s in sos): shell = '爱加密 (libddog)'")
            appendLine("elif any('chaosvmp' in s or 'vmp' in s.lower() for s in sos): shell = '娜迦/混淆VMP'")
            appendLine("elif any('secneo' in s for s in sos): shell = 'SecNeo'")
            appendLine("assets = os.listdir('extracted/assets') if os.path.isdir('extracted/assets') else []")
            appendLine("if shell is None:")
            appendLine("    if any('jiagu' in a or 'secData' in a for a in assets): shell = '360加固 (assets特征)'")
            appendLine("    elif any('libprotect' in a for a in assets): shell = '加固（libprotectClass）'")
            appendLine("print('加固类型:', shell if shell else '未检测到常见壳（可能无壳，可直接反编译）')")
            appendLine("print('so 列表:', sorted(sos))")
            appendLine("PYEOF")
            appendLine("echo '=== 5. 生成脱壳脚本（全程免root）==='")
            appendLine("cat > unpack_unicorn.py <<'UNIEOF'")
            appendLine("#!/usr/bin/env python3")
            appendLine("# 免root脱壳：用 Unicorn 模拟执行加固 so 的 JNI_OnLoad，dump 解密后的 dex/内存")
            appendLine("# 用法: python3 unpack_unicorn.py <libjiagu.so>")
            appendLine("# 原理: 模拟器里跑 so 的 JNI_OnLoad，hook FindClass/RegisterNatives，")
            appendLine("#       捕获解密流程写入内存的 dex 头(\\x64\\x65\\x78\\x0a magic 'dex')")
            appendLine("import sys, struct")
            appendLine("from unicorn import *")
            appendLine("from unicorn.arm64_const import *")
            appendLine("from capstone import *")
            appendLine("")
            appendLine("BASE = 0x70000000   # so 加载基址")
            appendLine("STACK = 0x7ff00000  # 模拟栈")
            appendLine("DUMPED = []")
            appendLine("")
            appendLine("def scan_memory(uc, start, size, label):")
            appendLine("    # 扫描内存中的 dex magic")
            appendLine("    data = uc.mem_read(start, size)")
            appendLine("    idx = 0")
            appendLine("    while True:")
            appendLine("        i = data.find(b'dex\\n', idx)")
            appendLine("        if i < 0: break")
            appendLine("        # dex 头: magic 8 字节 + 大小字段（小端，offset 0x20）")
            appendLine("        if i + 0x28 <= len(data):")
            appendLine("            dex_size = struct.unpack('<I', data[i+0x20:i+0x24])[0]")
            appendLine("            if 0x100 < dex_size < 0x10000000:")
            appendLine("                end = min(i + dex_size, len(data))")
            appendLine("                dex = data[i:end]")
            appendLine("                out = f'dumped_{label}_{len(DUMPED)}.dex'")
            appendLine("                open(out, 'wb').write(dex)")
            appendLine("                DUMPED.append(out)")
            appendLine("                print(f'[+] DEX dumped: {out} ({dex_size} bytes) @ {hex(start+i)}')")
            appendLine("                idx = i + dex_size")
            appendLine("                continue")
            appendLine("        idx = i + 4")
            appendLine("")
            appendLine("def load_so(path):")
            appendLine("    data = open(path, 'rb').read()")
            appendLine("    # ELF 解析：找 PT_LOAD 段")
            appendLine("    assert data[:4] == b'\\x7fELF', 'not ELF'")
            appendLine("    is64 = data[4] == 2")
            appendLine("    e_phoff = struct.unpack_from('<Q', data, 0x20)[0] if is64 else struct.unpack_from('<I', data, 0x1c)[0]")
            appendLine("    e_phentsize = struct.unpack_from('<H', data, 0x36)[0] if is64 else struct.unpack_from('<H', data, 0x2a)[0]")
            appendLine("    e_phnum = struct.unpack_from('<H', data, 0x38)[0] if is64 else struct.unpack_from('<H', data, 0x2c)[0]")
            appendLine("    segs = []")
            appendLine("    for i in range(e_phnum):")
            appendLine("        off = e_phoff + i * e_phentsize")
            appendLine("        p_type, p_flags = struct.unpack_from('<II', data, off)")
            appendLine("        if p_type != 1: continue  # PT_LOAD")
            appendLine("        if is64:")
            appendLine("            p_offset, p_vaddr, p_filesz, p_memsz = struct.unpack_from('<QQQQ', data, off+8)")
            appendLine("        else:")
            appendLine("            p_offset, p_vaddr, p_filesz, p_memsz = struct.unpack_from('<IIII', data, off+4)")
            appendLine("        segs.append((p_offset, p_vaddr, p_filesz, p_memsz, p_flags))")
            appendLine("    return data, segs")
            appendLine("")
            appendLine("def main(path):")
            appendLine("    data, segs = load_so(path)")
            appendLine("    uc = Uc(UC_ARCH_ARM64, UC_MODE_ARM)")
            appendLine("    # 映射段")
            appendLine("    for p_offset, p_vaddr, p_filesz, p_memsz, p_flags in segs:")
            appendLine("        vaddr = BASE + p_vaddr")
            appendLine("        uc.mem_map(vaddr & ~0xFFF, (p_memsz + 0xFFF) & ~0xFFF)")
            appendLine("        if p_filesz:")
            appendLine("            uc.mem_write(vaddr, data[p_offset:p_offset+p_filesz])")
            appendLine("    # 栈")
            appendLine("    uc.mem_map(STACK - 0x10000, 0x20000)")
            appendLine("    uc.reg_write(UC_ARM64_REG_SP, STACK)")
            appendLine("    # JNI 环境假对象（JNIEnv* 传参用，FindClass 等由 hook 处理）")
            appendLine("    jni_env = 0x71000000")
            appendLine("    uc.mem_map(jni_env, 0x1000)")
            appendLine("    uc.mem_write(jni_env, b'\\x00' * 0x1000)")
            appendLine("    # 尝试执行 JNI_OnLoad")
            appendLine("    # 导出表找 JNI_OnLoad（简化：从 .dynsym 解析略，直接按已知偏移尝试）")
            appendLine("    # 实际使用时 AI 需要按 so 的导出表/反汇编结果调整入口地址")
            appendLine("    entry = BASE + 0x1000  # 占位：AI 需先用 capstone 定位 JNI_OnLoad")
            appendLine("    print('[*] 开始模拟执行 JNI_OnLoad @', hex(entry))")
            appendLine("    try:")
            appendLine("        uc.emu_start(entry, 0, timeout=2*1000*1000, count=0x500000)")
            appendLine("    except UcError as e:")
            appendLine("        print('[!] 模拟结束(期望):', e)")
            appendLine("    # 扫描整个内存空间找 dex")
            appendLine("    for p_offset, p_vaddr, p_filesz, p_memsz, p_flags in segs:")
            appendLine("        scan_memory(uc, (BASE+p_vaddr) & ~0xFFF, (p_memsz + 0xFFF) & ~0xFFF, 'seg')")
            appendLine("    scan_memory(uc, STACK - 0x10000, 0x20000, 'stack')")
            appendLine("    if not DUMPED:")
            appendLine("        print('[-] 未直接扫描到 dex。建议：')")
            appendLine("        print('    1. 用 capstone 反汇编 JNI_OnLoad 找解密/内存写入点')")
            appendLine("        print('    2. hook memcpy/malloc 记录写入')")
            appendLine("        print('    3. 对 RC4/AES 加密的 dex，从 so 中提取密钥用 python 解密')")
            appendLine("")
            appendLine("if __name__ == '__main__':")
            appendLine("    main(sys.argv[1])")
            appendLine("UNIEOF")

            
            appendLine("echo '=== 6. 明文 dex 复制到当前目录（若存在）==='")
            appendLine("find extracted -name '*.dex' -exec cp {} . \\; 2>/dev/null")
            appendLine("ls -lh *.dex 2>/dev/null | head -10")
            appendLine("echo '=== DONE ==='")
            appendLine("echo '工作目录: $workDir'")
            appendLine("echo '· 无壳/明文dex：直接用 jadx/apktool 反编译 extracted/ 即可'")
            appendLine("echo '· 有壳(免root优先)：python3 unpack_unicorn.py <加固so路径>，模拟执行JNI_OnLoad自动dump dex；'")
            appendLine("echo '   强壳按脚本提示用 capstone 定位解密函数、hook memcpy、或提取RC4/AES密钥静态解密'")
            appendLine("echo '· 全程在 proot 用户态沙箱内完成，无需 root 真机'")
        }
        return executeCommand(id, cmd, timeoutMillis = 1_800_000)
    }

    suspend fun delete(id: String): Boolean {
        val workspace = dao.getById(id) ?: return false
        dao.deleteById(id)
        withContext(Dispatchers.IO) {
            manager.deleteWorkspace(workspace.root)
        }
        cleanupAssistantReferences(id)
        return true
    }

    private suspend fun cleanupAssistantReferences(workspaceId: String) {
        settingsStore.update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.workspaceId?.toString() == workspaceId) {
                        assistant.copy(workspaceId = null)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    private suspend fun restoreShellState(workspace: WorkspaceEntity) {
        updateShellState(workspace.id, workspace.shellStatus)
    }

    private suspend fun updateShellState(
        workspace: WorkspaceEntity,
        shellStatus: String,
    ) = updateShellState(workspace.id, shellStatus)

    private suspend fun updateShellState(
        workspaceId: String,
        shellStatus: String,
    ) {
        dao.updateShellStatus(
            id = workspaceId,
            shellStatus = shellStatus,
            updatedAt = System.currentTimeMillis(),
        )
    }

    companion object {
        private const val TAG = "WorkspaceRepository"

        /** rootfs 自动下载源 (Ubuntu 24.04 base arm64) */
        const val DEFAULT_ROOTFS_URL =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.3-base-arm64.tar.gz"
    }
}
