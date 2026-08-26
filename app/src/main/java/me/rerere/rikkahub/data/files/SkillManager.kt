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
        private const val ALL_SKILLS_SKILL = "万能技能合集"
        private const val TAROT_SKILL = "精准占卜"
        private const val XINGCE_SKILL = "行测方法论"
        private const val CHESS_SKILL = "棋类全能王"
        /** 旧版英文技能名 → 新中文名 迁移映射 */
        private val LEGACY_SKILL_RENAMES = mapOf(
            "all-skills" to ALL_SKILLS_SKILL,
            "tarot-extreme-accuracy" to TAROT_SKILL,
            "xingce-methods" to XINGCE_SKILL,
        )
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

        val allSkillsDir = skillsDir.resolve(ALL_SKILLS_SKILL)

        // 检查 all-skills 技能是否已存在
        if (!allSkillsDir.exists()) {
            Log.d(TAG, "Initializing default skills...")

            // 创建 all-skills 目录
            allSkillsDir.mkdirs()

            // 创建 SKILL.md 文件
            val skillContent = """---
name: 万能技能合集
description: 内置 35 个实用技能合集：代码审查、Bug 诊断、原型开发、方案研究、测试驱动开发、教学讲解、任务拆解等，AI 会根据你的需求自动匹配合适的技能。
---

# 万能技能合集（35 个内置技能）

本合集收录了 35 个实用技能，覆盖开发、排查问题、写方案、教学等常见场景，AI 会自动挑选合适的技能来帮你干活。

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
name: 精准占卜
description: 塔罗牌、八字、六爻、梅花易数等占卜解读，直断吉凶不绕弯子。当你想问感情、事业、财运、运势或抽牌占卜时使用。
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
        }

        // 内置行测技能（xingce-methods）：花生十三行测解题方法论（原内置 MCP 工具迁移而来）
        val xingceDir = skillsDir.resolve(XINGCE_SKILL)
        if (!xingceDir.exists()) {
            xingceDir.mkdirs()
            runCatching {
                context.assets.open("xingce/skill.md").bufferedReader().use { it.readText() }
            }.onSuccess { content ->
                xingceDir.resolve("SKILL.md").writeText(content)
                Log.d(TAG, "Default skills initialized: $XINGCE_SKILL")
            }
        }

        // 内置棋类技能（棋类全能王）：五子棋/象棋/围棋/华容道等全棋种最强策略
        val chessDir = skillsDir.resolve(CHESS_SKILL)
        if (!chessDir.exists()) {
            chessDir.mkdirs()
            chessDir.resolve("SKILL.md").writeText(CHESS_SKILL_CONTENT)
            Log.d(TAG, "Default skills initialized: $CHESS_SKILL")
        }
    }

    /** 棋类全能王技能内容 */
    private val CHESS_SKILL_CONTENT = """---
name: 棋类全能王
description: 任何棋都会下且下得极准：五子棋必胜开局、象棋开局/中局/残局杀法、围棋死活与收官、华容道最优解、国际象棋、井字棋必不败等。用户要下棋、残局求解、复盘或问棋类策略时使用。
---

# 棋类全能王 —— 全棋种最强下法

你是棋力顶尖的全能棋手。下任何棋都遵循：**先穷尽对手的威胁，再计算自己的胜路**。宁可和局也不送胜。

## 通用下棋铁律
1. 每步先问：对手上一步想干什么？他最强的一步是什么？必须先挡住致命威胁再考虑进攻。
2. 算两遍：候选步至少向前推演 3 层（我→他→我），关键对杀至少 5 层。
3. 有必胜策略的棋绝不走偏：严格按必胜路线执行，不和棋、不秀操作。
4. 输出格式：先给「我方这步」，再用一句话说明威胁/意图，让对手心服口服。
5. 复盘时直说双方最佳手与错着，不客套。

## 五子棋（先手黑棋有必胜开局！）
- **先手必胜开局**：花月（直指2路）、浦月（斜指3路）是已证明的黑棋必胜开局。执黑时：开局天元，第二手贴着下（距离1格内），第三手成"活二夹角"压向一侧，之后每步同时制造双威胁。
- **核心战术 VCF/VCT**：优先找「连续冲四」取胜线（VCF）；找不到冲四就做「活三→冲四→活三」循环（VCT）。双活三、四三双杀是终结手段。
- **防守铁律**：对手活三必挡（挡中间比挡两端好，能反四先反四）；对手冲四必堵；对手双三点必占交叉点。
- **禁手提醒**：有禁手规则（正式比赛）时，黑棋三三、四四、长连都是禁手，执黑要避开，执白要诱黑入禁。
- 执白策略：第一手贴死黑棋，破坏其开局形状，逼成散棋后再找反杀机会。

## 华容道（经典横刀立马最少 81 步）
- **目标**：曹操移到底部中间出口。
- **通用策略**：①先把四个小兵移到上半区腾出下半空间；②竖将（关羽等）贴到曹操两侧随其下移；③横将横在上方做垫子；④曹操始终往中间对齐出口走。
- **经典布局最优解**：横刀立马 81 步（关羽横刀在曹操上方那版）；指挥若定 70 步；兵分三路 72 步；近在咫尺（横刀立马变体）138 步。若用户给的是已知布局，直接给最少步解法序列。
- **走法记法**：上=↑下=↓左=←右=→，按"棋子名+方向"逐步输出，如"兵1↑ 关羽← 曹操↓"。

## 中国象棋
- **开局原则**：三步出车（车路要通）、马不躁进、炮不轻发、士象勿乱补、先活马后架炮。中炮过河车对屏风马、仙人指路（兵七进一）都是均衡开局。
- **中局战术**：优先找「抽将」（将军同时吃子）、「双车错」（两车交替将军）、「马后炮」（马控将门炮将军）、「卧槽马/挂角马」（跳到对方将门）、「铁门栓」（车炮封将门）。兑子原则：优势兑子简化局面，劣势避免兑子保持复杂。
- **残局定式**：
  - 单车对士象全：和棋（士象规范不可破）。
  - 单车对双士/单缺象：车胜。
  - 车低兵必胜单车（兵占肋道）；车高兵必胜士象全。
  - 炮士象全对单车：和；炮有士可胜双士。
  - 兵：过河兵价值大涨，老兵（底线兵）只可协防。
  - 对杀时数步：谁的杀棋快一步谁赢，精确数将杀步数。
- **子力价值**：车9 炮4.5 马4 车>双炮>双马；士象各2，过河兵2-3。

## 围棋
- **布局**：金角银边草肚皮。占角＞守角＞挂角＞拆边。先手大利不走小官。
- **死活**：眼位两眼做活；直三、曲三、丁四、刀把五、花五、葡萄六是死形，点杀要点（直三点中间、曲三点拐头、丁四/方块四点中心）。盘角曲四＝死棋。胀牯牛（打劫活）注意劫材。
- **对杀**：先数气再动手，气长者胜；有眼杀无眼；大眼气数（直三3气、丁四5气、刀把五8气、花五8气、葡萄六12气）。
- **收官**：先手官子＞逆收＞双先＞后手大官。常用官子：一线扳粘、二线爬、伸腿。
- **行棋格言**：逢劫先找劫材；断哪边吃哪边；敌之要点我之要点；两番收腹成尤小。

## 国际象棋
- **开局**：快出子、占中心（e4/d4）、早易位、别重复走子别乱兑后。意大利开局/西西里防御/后翼弃兵皆可。
- **战术组合**：叉击（fork）、牵制（pin）、双将、闪击、过载、消除防御。后+车斜线配合杀王，双象杀单王。
- **残局**：王兵残局记住"关键格"理论；远方通路兵是资产；单后杀王、单车杀王（逼边）、双象杀王均有固定方法。

## 井字棋（先手必不败）
- 先手下角最强；对手若不占中心必胜，占中心则成和。后手：第一手必须占中心或角，否则必败。完美对局必和棋——能赢就赢，赢不了确保和。

## 跳棋 / 军棋 / 其他
- 跳棋：搭桥要早，中心通道优先，残局算最后一跳。
- 军棋：司令慎出、工兵护旗、雷区判断（大子必不在第一排冲锋位）。
- 遇到没见过的棋：先问清规则→找"该棋的必胜/必和不败策略"→按通用铁律深算。

## 复盘教学
用户发残局/棋谱时：标注双方最佳着法（用 ▲ 指出），指出败着（用 ✗），给出从该局面起的必胜/最优路线。
"""

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

