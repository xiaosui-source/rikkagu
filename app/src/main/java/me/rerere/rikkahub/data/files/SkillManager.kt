/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.files

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore

class SkillManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    companion object {
        private const val TAROT_SKILL = "精准占卜"
        private const val KARPATHY_SKILL = "Karpathy方法论"
        private const val CHESS_SKILL = "棋类全能王"
        /** 旧版英文技能名 → 新中文名 迁移映射 */
        private val LEGACY_SKILL_RENAMES = mapOf(
            "tarot-extreme-accuracy" to TAROT_SKILL,
        )
        /** 已移除的废弃技能目录（首启清理，避免残留显示） */
        private val REMOVED_SKILL_DIRS = listOf(
            "行测方法论",
            "万能技能合集",
            "Superpowers技能合集",
            "brainstorming",
            "dispatching-parallel-agents",
            "executing-plans",
            "finishing-a-development-branch",
            "receiving-code-review",
            "requesting-code-review",
            "subagent-driven-development",
            "systematic-debugging",
            "test-driven-development",
            "using-git-worktrees",
            "using-superpowers",
            "verification-before-completion",
            "writing-plans",
            "writing-skills",
        )
        private const val TAG = "SkillManager"
    }

    fun getSkillsDir(): File {
        val dir = context.filesDir.resolve(FileFolders.SKILLS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun listSkills(): List<SkillMetadata> = withContext(Dispatchers.IO) {
        listSkillsSync()
    }

    /** 同步读取技能列表（非 suspend），用于系统提示注入等不能 suspend 的场景 */
    fun listSkillsSync(): List<SkillMetadata> {
        val skillsDir = getSkillsDir()
        return skillsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val skillFile = dir.resolve("SKILL.md")
                if (!skillFile.exists()) return@mapNotNull null
                parseSkillFile(skillFile, dir)
            }
            ?: emptyList()
    }

    suspend fun initDefaultSkills() = withContext(Dispatchers.IO) {
        val skillsDir = getSkillsDir()

        // 旧版英文技能名迁移：重命名目录 + 同步更新助手 enabledSkills
        var needsSettingsMigration = false
        LEGACY_SKILL_RENAMES.forEach { (oldName, newName) ->
            val oldDir = skillsDir.resolve(oldName)
            if (oldDir.exists() && oldDir.isDirectory) {
                val newDir = skillsDir.resolve(newName)
                if (!newDir.exists()) {
                    if (oldDir.renameTo(newDir)) needsSettingsMigration = true
                } else {
                    oldDir.deleteRecursively()
                    needsSettingsMigration = true
                }
            }
        }
        if (needsSettingsMigration) {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map { assistant ->
                        assistant.copy(
                            enabledSkills = assistant.enabledSkills
                                .map { LEGACY_SKILL_RENAMES[it] ?: it }
                                .toSet()
                        )
                    }
                )
            }
        }

        // 清理已移除的废弃技能：删除残留目录 + 从所有助手 enabledSkills 中剔除
        REMOVED_SKILL_DIRS.forEach { removedName ->
            val dir = skillsDir.resolve(removedName)
            if (dir.exists()) dir.deleteRecursively()
        }
        val hasRemovedInSettings = settingsStore.settingsFlow.first().let { s ->
            s.assistants.any { it.enabledSkills.any { n -> n in REMOVED_SKILL_DIRS } }
        }
        if (hasRemovedInSettings) {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map { assistant ->
                        assistant.copy(enabledSkills = assistant.enabledSkills - REMOVED_SKILL_DIRS.toSet())
                    }
                )
            }
        }

        // ===== 统一从 assets/skills/ 初始化全部内置技能 =====
        // 技能内容固定存放在 assets/skills/<技能名>/SKILL.md，首启复制到 files/skills/ 供读取
        val builtinSkills = listOf(
            TAROT_SKILL to "精准占卜",
            KARPATHY_SKILL to "Karpathy方法论",
            CHESS_SKILL to "棋类全能王",
        )
        builtinSkills.forEach { (skillKey, assetDir) ->
            val dir = skillsDir.resolve(skillKey)
            if (!dir.exists()) {
                dir.mkdirs()
                runCatching {
                    context.assets.open("skills/$assetDir/SKILL.md").bufferedReader().use { it.readText() }
                }.onSuccess { content ->
                    dir.resolve("SKILL.md").writeText(content)
                    Log.d(TAG, "Default skill initialized: $skillKey")
                }.onFailure { t ->
                    Log.w(TAG, "Builtin skill missing from assets: $assetDir", t)
                }
            }
        }
    }

    fun readSkillBody(skillName: String): String? {
        val skillFile = resolveSkillDir(skillName)?.resolve("SKILL.md") ?: return null
        if (!skillFile.exists()) return null
        return SkillFrontmatterParser.extractBody(skillFile.readText())
    }

    fun readSkillContent(skillName: String): String? {
        val skillFile = resolveSkillDir(skillName)?.resolve("SKILL.md") ?: return null
        if (!skillFile.exists()) return null
        return skillFile.readText()
    }

    fun saveSkill(name: String, content: String): SkillMetadata? {
        val skillDir = resolveSkillDir(name) ?: return null
        skillDir.mkdirs()
        val skillFile = skillDir.resolve("SKILL.md")
        skillFile.writeText(content)
        return parseSkillFile(skillFile, skillDir)
    }

    suspend fun deleteSkill(name: String): Boolean = withContext(Dispatchers.IO) {
        val skillDir = resolveSkillDir(name) ?: return@withContext false
        val deleted = skillDir.deleteRecursively()
        if (deleted) {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map { assistant ->
                        if (assistant.enabledSkills.contains(name)) {
                            assistant.copy(enabledSkills = assistant.enabledSkills - name)
                        } else {
                            assistant
                        }
                    }
                )
            }
        }
        deleted
    }

    fun getSkillDir(skillName: String): File? = resolveSkillDir(skillName)

    fun saveSkillFile(skillName: String, relativePath: String, content: String): Boolean {
        val skillDir = resolveSkillDir(skillName) ?: return false
        val target = SkillPaths.resolveSkillFile(skillDir, relativePath) ?: return false
        target.parentFile?.mkdirs()
        target.writeText(content)
        return true
    }

    fun saveSkillFilesAtomically(skillName: String, files: Map<String, String>): Boolean {
        val skillsDir = getSkillsDir()
        val targetDir = resolveSkillDir(skillName) ?: return false
        val stagingDir = createTempSkillDir(skillsDir, skillName, "staging") ?: return false
        var backupDir: File? = null

        try {
            for ((relativePath, content) in files) {
                val target = SkillPaths.resolveSkillFile(stagingDir, relativePath) ?: return false
                target.parentFile?.mkdirs()
                target.writeText(content)
            }

            if (!stagingDir.resolve("SKILL.md").exists()) return false

            if (targetDir.exists()) {
                backupDir = createTempSkillDir(skillsDir, skillName, "backup") ?: return false
                if (!targetDir.renameTo(backupDir)) return false
            }

            if (!stagingDir.renameTo(targetDir)) {
                if (backupDir != null && !targetDir.exists()) {
                    backupDir.renameTo(targetDir)
                }
                return false
            }

            backupDir?.deleteRecursively()
            return true
        } catch (e: Exception) {
            Log.w(TAG, "saveSkillFilesAtomically: Failed to save $skillName", e)
            if (backupDir != null && !targetDir.exists()) {
                backupDir.renameTo(targetDir)
            }
            return false
        } finally {
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
            if (backupDir?.exists() == true && targetDir.exists()) {
                backupDir.deleteRecursively()
            }
        }
    }

    fun deleteSkillFile(skillName: String, relativePath: String): Boolean {
        val skillDir = resolveSkillDir(skillName) ?: return false
        val target = SkillPaths.resolveSkillFile(skillDir, relativePath) ?: return false
        return target.delete()
    }

    fun resolveSkillFile(skillName: String, relativePath: String): File? {
        val skillDir = resolveSkillDir(skillName) ?: return null
        return SkillPaths.resolveSkillFile(skillDir, relativePath)
    }

    private fun resolveSkillDir(skillName: String): File? {
        return SkillPaths.resolveSkillDir(getSkillsDir(), skillName)
    }

    private fun createTempSkillDir(skillsRoot: File, skillName: String, suffix: String): File? {
        repeat(100) { attempt ->
            val candidate = skillsRoot.resolve(".$skillName.$suffix.$attempt.tmp")
            if (!candidate.exists() && candidate.mkdirs()) {
                return candidate
            }
        }
        return null
    }

    private fun parseSkillFile(skillFile: File, skillDir: File): SkillMetadata? {
        return runCatching {
            val content = skillFile.readText()
            val frontmatter = SkillFrontmatterParser.parse(content)
            val name = frontmatter["name"]?.takeIf { it.isNotBlank() } ?: return null
            val description = frontmatter["description"]?.takeIf { it.isNotBlank() } ?: return null
            SkillMetadata(
                name = name,
                description = description,
                compatibility = frontmatter["compatibility"],
                allowedTools = frontmatter["allowed-tools"]?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
                disableModelInvocation = frontmatter.getBoolean("disable-model-invocation"),
                skillDir = skillDir,
            )
        }.getOrElse {
            Log.w(TAG, "parseSkillFile: Failed to parse ${skillFile.absolutePath}", it)
            null
        }
    }
}

data class SkillMetadata(
    val name: String,
    val description: String,
    val compatibility: String? = null,
    val allowedTools: List<String> = emptyList(),
    /** #1763: true 时不注入模型上下文（不列在可用技能中），仅在用户明确调用时使用 */
    val disableModelInvocation: Boolean = false,
    val skillDir: File,
) {
    val skillFile: File get() = skillDir.resolve("SKILL.md")
}

