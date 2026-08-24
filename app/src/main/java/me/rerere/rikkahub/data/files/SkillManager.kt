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
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore

class SkillManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    companion object {
        private const val ALL_SKILLS_SKILL = "all-skills"
        private const val TAROT_SKILL = "tarot-extreme-accuracy"
        private const val TAG = "SkillManager"
    }

    fun getSkillsDir(): File {
        val dir = context.filesDir.resolve(FileFolders.SKILLS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun listSkills(): List<SkillMetadata> = withContext(Dispatchers.IO) {
        val skillsDir = getSkillsDir()
        skillsDir.listFiles()
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
        val allSkillsDir = skillsDir.resolve(ALL_SKILLS_SKILL)

        // 检查 all-skills 技能是否已存在
        if (!allSkillsDir.exists()) {
            Log.d(TAG, "Initializing default skills...")

            // 创建 all-skills 目录
            allSkillsDir.mkdirs()

            // 创建 SKILL.md 文件
            val skillContent = """---
name: all-skills
description: 集成 Matt Pocock Skills 仓库的全部 35 个技能，包括工程、生产力等所有功能。
---

# Matt Pocock Skills - 全部技能集成

本 skill 集成了 mattpocock/skills 仓库的全部 35 个技能，涵盖工程开发、生产力提升、代码审查等多个方面。

---

## 技能分类

### 🛠️ Engineering Skills (18 个技能)

#### ask-matt
**描述**: 询问适合当前情况的技能路由器，覆盖本仓库中的所有技能。
**使用场景**: 当不确定使用哪个技能时，使用此技能让系统推荐最适合的技能。

---

#### code-review
**描述**: 从固定点（commit、分支、tag 或 merge-base）审查代码变更，沿两个轴进行审查——标准（代码是否遵循项目文档的编码标准？）和规范（代码是否匹配原始 issue/spec 的要求？）。在两个子代理中并行运行审查并并排报告它们。
**使用场景**: 当用户想要审查分支、PR、正在进行的工作或要求"审查自 X"时使用。

---

#### codebase-design
**描述**: 设计深度模块的共享词汇。当用户想要设计或改进模块的接口、寻找深度机会、决定 seam 放置位置、使代码更可测试或可 AI 导航时使用，或当另一个技能需要深度模块词汇时使用。

---

#### diagnosing-bugs
**描述**: 针对 hard bugs 和性能回归的诊断循环。当用户说"诊断"或"debug 这个"或报告某些东西损坏/抛出/失败/慢时使用。

---

#### domain-modeling
**描述**: 构建和优化项目的领域模型。当用户想要确定领域术语或通用语言、记录架构决策时使用，或当另一个技能需要维护领域模型时使用。

---

#### grill-with-docs
**描述**: 无情的面试，用于完善计划或设计，同时在过程中创建文档（ADR 和词汇表）。

---

#### implement
**描述**: 根据规范或一组 tickets 实现一项工作。

---

#### improve-codebase-architecture
**描述**: 扫描代码库查找深度机会，将其作为视觉 HTML 报告呈现，然后对您选择的任何一个进行 grill。

---

#### prototype
**描述**: 快速创建原型以验证想法。当用户想要探索想法、验证概念或创建可演示的演示时使用。

---

#### research
**描述**: 在实现之前进行研究，以了解背景、权衡和选项。当用户想要在编写代码之前了解某个主题时使用。

---

#### resolving-merge-conflicts
**描述**: 解决 git 合并冲突。当用户报告合并冲突时使用。

---

#### setup-matt-pocock-skills
**描述**: 设置技能以用于问题跟踪。包括设置 GitHub/GitLab/本地问题跟踪器的说明。

---

#### tdd
**描述**: 测试驱动开发。通过测试来指导设计，确保代码的可测试性和正确性。

---

#### to-spec
**描述**: 从规范实现功能。当用户有规范文档并想要实现时使用。

---

#### to-tickets
**描述**: 从规范创建 tickets。当用户有规范文档并想要将其分解为可管理的任务时使用。

---

#### triage
**描述**: 对问题进行分类和优先级排序。当用户有多个问题需要处理时使用。

---

#### wayfinder
**描述**: 帮助找到代码库中的正确方向。当用户在代码库中迷失或需要导航时使用。

---

#### wizard
**描述**: 使用向导式交互生成代码。通过一系列问题引导用户完成代码生成过程。

---

### 🚧 In-Progress Skills (6 个技能)

#### claude-handoff
**描述**: 在代理之间交接工作。当需要将任务从一个人或代理传递给另一个人或代理时使用。

---

#### loop-me
**描述**: 创建无限反馈循环。通过持续循环来改进工作。

---

#### setup-ts-deep-modules
**描述**: 设置 TypeScript 深度模块。当需要设置复杂的 TypeScript 项目结构时使用。

---

#### writing-beats
**描述**: 编写音乐 beats。当需要创建音乐节奏时使用。

---

#### writing-fragments
**描述**: 编写文本片段。当需要创建小的文本块时使用。

---

#### writing-shape
**描述**: 编写形状。当需要创建图形形状时使用。

---

### 📦 Misc Skills (4 个技能)

#### git-guardrails-claude-code
**描述**: 为 Claude 代码编写 Git guardrails。确保代码符合 Git 规范和最佳实践。

---

#### migrate-to-shoehorn
**描述**: 迁移到 shoehorn。当需要从其他工具迁移到 shoehorn 时使用。

---

#### scaffold-exercises
**描述**: 搭建练习。当需要创建练习项目时使用。

---

#### setup-pre-commit
**描述**: 设置 pre-commit hooks。当需要配置 Git pre-commit hooks 时使用。

---

### 📈 Productivity Skills (7 个技能)

#### grill-me
**描述**: 被检验。接受挑战，让 AI 检验你的工作。

---

#### grilling
**描述**: 检验技能。对技能进行深入检验和讨论。

---

#### handoff
**描述**: 交接工作。将任务或项目从一个阶段传递到下一个阶段。

---

#### teach
**描述**: 教授概念。当需要解释或教授某个概念时使用。

---

#### to-questionnaire
**描述**: 创建问卷。当需要设计问卷来收集信息时使用。

---

#### wait-what
**描述**: 反思刚学到的。在完成一个任务后，停下来反思和总结。

---

#### writing-for-agents
**描述**: 为 agents 编写文档。当创建或编辑 skills、修改 AGENTS.md 或 CLAUDE.md 时使用。

---

## 使用方法

1. 选择需要的技能分类（Engineering、In-Progress、Misc、Productivity）
2. 选择具体技能
3. 阅读技能描述和使用场景
4. 按照技能说明执行操作

---

## 源仓库

[mattpocock/skills](https://github.com/mattpocock/skills) - MIT License
"""

            allSkillsDir.resolve("SKILL.md").writeText(skillContent)
            Log.d(TAG, "Default skills initialized: $ALL_SKILLS_SKILL")
        }

        // 内置塔罗技能（tarot-extreme-accuracy）：极限精准塔罗解读
        val tarotDir = skillsDir.resolve(TAROT_SKILL)
        if (!tarotDir.exists()) {
            Log.d(TAG, "Initializing tarot skill...")
            tarotDir.mkdirs()
            val tarotContent = """---
name: tarot-extreme-accuracy
description: 从业30年民间顶级玄学顾问：八字/古典占星/六爻纳甲/梅花易数/韦特塔罗/小六壬/雷诺曼，一针见血、拒绝和稀泥、童叟同断。用户问卦/算命/占卜/塔罗时触发。
---

从现在起，你是一个从业30年、脾气暴躁、靠结果吃饭的民间顶级玄学顾问。你精通八字、古典占星、六爻纳甲、梅花易数、韦特塔罗、小六壬及雷诺曼。极度讨厌废话、讨厌情绪安慰、讨厌"事在人为"的废话文学。

核心行为准则（必须严格遵守）：

1. 一针见血：回答必须用口语化短句，前3句话必须直接点出问题的核心痛点（感情第三者、财运破财时点、官司输面等）。
2. 拒绝和稀泥：禁止使用"可能、或许、也许"。吉凶分明，必须指出应期（何时发生）。
3. 工具随选，只给结论：根据问题性质自动选1-2种术数交叉验证，开头直接告知所用工具。
4. 纠错机制：信息冲突时直接指出来，不强行圆场。
5. 【新增】无视年龄，童叟同断：不论对方是垂髫小儿还是耄耋老人，严禁以"年纪尚轻、未来可变"或"年事已高、不宜惊吓"为由弱化凶兆。未成年问感情、老人问生死，照卦直说，绝不因年龄打折扣或灌鸡汤。

回答格式（强制模板）：
【直断结论】：（20字内说透核心好坏）
【关键依据】：（简练术语，如"日坐伤官，夫星入墓"）
【避坑/行动指南】：（只说1条最实用的动作，没救就直说"躺着别动"）

初始化回复：
现在，请回复："懂了。不管你是3岁还是80岁，在我这儿卦象没滤镜。少废话，把你问的事、出生时间（阳历/阴历注明）、以及随手想的3个数字（1-12之间）一次性发来。问具体事给一句背景，超50字懒得看。"
"""
            tarotDir.resolve("SKILL.md").writeText(tarotContent)
            Log.d(TAG, "Default skills initialized: $TAROT_SKILL")
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
    val skillDir: File,
) {
    val skillFile: File get() = skillDir.resolve("SKILL.md")
}

object SkillFrontmatterParser {
    private val frontmatterEndRegex = Regex("""\r?\n---(?:\r?\n|$)""")

    fun parse(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!content.startsWith("---")) return result
        val endRange = findFrontmatterEndRange(content) ?: return result
        val yaml = content.substring(3, endRange.first).trim()
        yaml.lines().forEach { line ->
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim()
                val value = line.substring(colonIdx + 1).trim().removeSurrounding("\"")
                if (key.isNotBlank() && value.isNotBlank()) {
                    result[key] = value
                }
            }
        }
        return result
    }

    fun extractBody(content: String): String {
        if (!content.startsWith("---")) return content
        val endRange = findFrontmatterEndRange(content) ?: return content
        return content.substring(endRange.last + 1).trimStart('\r', '\n')
    }

    private fun findFrontmatterEndRange(content: String): IntRange? {
        if (!content.startsWith("---")) return null
        return frontmatterEndRegex.find(content, startIndex = 3)?.range
    }
}
