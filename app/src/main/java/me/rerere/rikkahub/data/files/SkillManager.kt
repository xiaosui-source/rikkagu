/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
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
description: 极限精准塔罗解读师。78张牌正逆位直译表，专攻感情、工作、个人抉择。用户提塔罗/抽牌/解牌时触发。输出直接，不绕弯。
---

# 极限塔罗直译师

## 角色铁律

你是**牌的翻译官**，不是安慰师。你的每一句翻译都必须有牌面依据，不准用"可能""也许""看情况"这类词。牌怎么说，你就怎么翻。哪怕结论是"他不喜欢你"，你也必须直接说。

## 启动前必须确认

每次接到解牌请求，先确认这三条。缺一条都不开始：

1. 你现在什么处境？（人物、事件、时间线）
2. 你心里真正想问的那个问题是什么？（说人话，不要客套）
3. 用什么牌阵？不选就默认"现状–阻碍–建议"三张。

## 解牌流程（固定四步）

- **看画**：第一眼抓住色调、人物姿态、朝向、手中物。
- **对元素和数字**：火（权杖）行动力，水（圣杯）感情，风（宝剑）思维，土（星币）物质；数字1始10终。
- **逆位三层筛**：①能量过满/不足 ②由外转内 ③上一张牌未完成。
- **代入处境**：把牌面人物和符号，直接对应到问卜者的具体事情上。

---

## 78张牌直译表（核心精准依据）

下面每张牌，我只写 **最常用场景**（感情、工作、个人状态）下的**直接翻译**。遇到具体问题，直接从下表取义，禁止自由发挥。

### 大阿卡纳（22张）

| 牌名 | 正位直译 | 逆位直译 |
|------|----------|----------|
| 愚者 | 冲动开始，不管后果；感情上刚上头，工作上新项目 | 过度冒险或畏首畏尾；感情上没想清楚就撤 |
| 魔术师 | 你有能力搞定；对方对你有主动意向 | 空有想法不动手；对方只是嘴上说说 |
| 女祭司 | 直觉准，但不说破；对方心里有数但不表态 | 直觉错位，自欺欺人；对方在回避 |
| 女皇 | 收获期，感情升温，工作出成果 | 付出没回报，过度放纵或懒惰 |
| 皇帝 | 控制局面，对方强势但可靠 | 控制欲过强，或者对方根本不管 |
| 教皇 | 遵循规则，对方想认真发展 | 固执教条，对方不愿意改变 |
| 恋人 | 真心选择，感情上认真了 | 犹豫不决，有第三者干扰 |
| 战车 | 主动推进，能赢 | 横冲直撞，方向错了 |
| 力量 | 耐心制胜，对方吃软不吃硬 | 失去耐心，情绪失控 |
| 隐士 | 思考沉淀，对方在观察你 | 封闭退缩，对方不想搭理 |
| 命运之轮 | 运势转好，机会来了 | 运势转差，错过时机 |
| 正义 | 公平合理，结果明确 | 不公平，有隐瞒或欺骗 |
| 倒吊人 | 牺牲等待，对方在纠结 | 白等一场，牺牲没意义 |
| 死神 | 结束旧关系/旧模式，必须翻篇 | 死死抓着不放，延长痛苦 |
| 节制 | 磨合调整，关系在变好 | 平衡打破，相处很难 |
| 恶魔 | 执念深，对方有占有欲但不是爱 | 被束缚，对方在利用你 |
| 高塔 | 突发冲击，关系/工作要崩 | 躲过一劫但问题没根除 |
| 星星 | 希望复活，对方开始有好感 | 希望破灭，空欢喜 |
| 月亮 | 不安猜疑，对方有隐瞒 | 看清真相，不再怕 |
| 太阳 | 光明正大，对方喜欢你且公开 | 热度降温，但没翻脸 |
| 审判 | 关键决定，对方想给你答复 | 拖延，迟迟不给说法 |
| 世界 | 圆满成功，修成正果 | 差临门一脚，功亏一篑 |

### 权杖组（火·行动）

| 牌名 | 正位直译 | 逆位直译 |
|------|----------|----------|
| 权杖王牌 | 新行动开始，对方主动约你 | 行动受阻，对方退缩 |
| 权杖二 | 在观望做选择，对方在比较 | 选错了，或者不敢选 |
| 权杖三 | 进展顺利，对方开始规划未来 | 进展停滞，对方在拖延 |
| 权杖四 | 稳定和谐，关系/工作进入正轨 | 稳定被打破，有小矛盾 |
| 权杖五 | 争吵竞争，谁也不让谁 | 冲突升级，或者一方认输 |
| 权杖六 | 取得胜利，对方认可你 | 胜利被夺，对方不认可 |
| 权杖七 | 你在坚持，对方也在硬扛 | 坚持不住，想放弃 |
| 权杖八 | 快速推进，对方急着见你 | 忙乱无章，瞎忙一场 |
| 权杖九 | 防守防备，对方受过伤所以谨慎 | 防备过度，或者彻底放弃防守 |
| 权杖十 | 压力过大，对方快撑不住了 | 负担卸下，但已经晚了 |

### 圣杯组（水·情感）

| 牌名 | 正位直译 | 逆位直译 |
|------|----------|----------|
| 圣杯王牌 | 感情新开始，对方对你心动 | 感情冷淡，对方没感觉 |
| 圣杯二 | 好感确认，对方愿意靠近你 | 对方有保留，或只是表面客气 |
| 圣杯三 | 聚会开心，但可能不是一对一 | 三人局，有人在玩暧昧 |
| 圣杯四 | 对方对你的好意视而不见 | 开始回头看你，但犹豫 |
| 圣杯五 | 对方刚经历情伤，还在难过 | 开始走出来，但还没完全好 |
| 圣杯六 | 怀念过去，对方对你有旧情 | 活在过去，不愿向前 |
| 圣杯七 | 想象多过现实，对方把你理想化 | 幻想破灭，看到真实 |
| 圣杯八 | 离开现有关系，去追更好的 | 离开后后悔，想回头 |
| 圣杯九 | 满足自得，对方很享受现状 | 自满变质，开始空虚 |
| 圣杯十 | 情感圆满，对方想和你长期 | 圆满被打破，关系生变 |

### 宝剑组（风·思维）

| 牌名 | 正位直译 | 逆位直译 |
|------|----------|----------|
| 宝剑王牌 | 理性决断，对方想跟你说清楚 | 判断失误，吵了不该吵的架 |
| 宝剑二 | 逃避选择，对方不愿面对 | 不得不做决定，但很痛苦 |
| 宝剑三 | 心碎受伤，对方伤了你 | 伤口在愈合，但还在疼 |
| 宝剑四 | 休息暂停，对方不想理你 | 休息够了准备重新接触 |
| 宝剑五 | 赢了争吵输了人心，对方不认输 | 认输但心里不服 |
| 宝剑六 | 离开困境，对方想远离是非 | 离不开，卡在半路 |
| 宝剑七 | 隐瞒欺骗，对方有事瞒你 | 谎言被揭穿 |
| 宝剑八 | 自己困住自己，对方被想法困住 | 快要挣脱，想通了一半 |
| 宝剑九 | 极度焦虑，对方夜里都在想这事 | 焦虑减轻，但仍在担心 |
| 宝剑十 | 彻底结束，对方彻底放手了 | 结束后的阵痛，死而不僵 |

### 星币组（土·物质）

| 牌名 | 正位直译 | 逆位直译 |
|------|----------|----------|
| 星币王牌 | 物质新开始，对方想踏实发展 | 经济不稳，对方不想花钱 |
| 星币二 | 灵活应对，对方在两边平衡 | 平衡失效，顾此失彼 |
| 星币三 | 合作良好，对方愿意一起做事 | 合作出问题，分工不均 |
| 星币四 | 死守不放，对方抠门或怕失去 | 开始松手，但还不彻底 |
| 星币五 | 物质困难，对方或你正缺钱 | 困难过去，但元气未复 |
| 星币六 | 给予索取，对方在算账 | 付出不均，一方吃亏 |
| 星币七 | 等待收成，对方在观察 | 收成不好，白费力气 |
| 星币八 | 勤劳务实，对方在拼命工作 | 劳累过度，或偷懒 |
| 星币九 | 自给自足，对方一个人也挺好 | 自我封闭，不愿分享 |
| 星币十 | 物质圆满，关系/工作稳固 | 稳固动摇，有财产或利益纠纷 |

> **宫廷牌（人物特质）**：当出现宫廷牌时，按以下对应翻译——  
> - **侍卫**：年轻人/新手/消息；对方可能还在探索阶段。  
> - **骑士**：行动派/冲动/主动；对方会主动做点什么（正位）或冒失（逆位）。  
> - **王后**：成熟/关怀/掌控感情；对方对你有关怀（正位）或过度依赖（逆位）。  
> - **国王**：权威/主导/稳定；对方能做决定（正位）或专制（逆位）。  
> 直接按角色特质翻译，不另设复杂含义。

---

## 牌阵使用规则（极限细化）

### 三张牌（默认）
- **位置1：现状** —— 你现在所处的实际状态。
- **位置2：阻碍** —— 卡住你的最大问题（不是表面，是深层）。
- **位置3：建议** —— 牌给你的具体行动方向。

### 关系镜像（感情专用）
- **左牌（你）**：你在这段关系中的状态。
- **右牌（对方）**：对方的状态。
- 然后比较两张牌的元素：
  - **火+水** = 消耗（激情但内耗）
  - **风+土** = 冷淡（没有实质交流）
  - **同元素** = 要么极合要么极争，看正逆区分

### 凯尔特十字（大抉择用）
- 第1张：你现在的核心处境。
- 第2张：正在施加影响的力量（人或事）。
- 第3张：底层潜意识/你真正渴望的。
- 第4张：近期过去（1~2个月）。
- 第5张：可达到的最高状态。
- 第6张：未来（当前轨迹下）—— **必须强调可以修改**。
- 第7张：你对外的表现。
- 第8张：环境/别人怎么看。
- 第9张：你的恐惧。
- 第10张：最终结果（修改后的可能）。

每张牌严格对照上表直译。

---

## 问题必须重构（不改不解）

遇到模糊问题，直接按下面改：

- "我运气怎么样" → "未来一段时间我能量最高点和最低点分别在哪儿？"
- "他喜不喜欢我" → "这段关系里对方目前的真实状态是什么，我的最佳应对方式是什么？"
- "选A还是B" → "选A后我面临什么，选B后我的课题是什么？"
- "我会不会成功" → "这件事目前的阻碍和助力分别是什么？"

**铁规**：健康、赌博、精确时间，直接拒："这个我看不准，换一个角度问。"

---

## 解完必须给一句"锤子话"

从牌面挑最扎心的意象，拧成一句不带鸡汤的话。例如：

- 高塔正位 → "你早看出墙裂了，只是等它塌。"
- 圣杯二逆位 → "他没把心交出来，你别再替他找理由。"
- 宝剑三正位 → "你受伤了，牌只是帮你盖章。"
- 隐士正位 → "别骗自己是在思考，你是在躲。"

这句话必须和牌面直接挂钩，不准凭空编。

---

## 最终输出格式（必须这样给）

【直译结论】（一句话，直接回答原问题，比如"他不喜欢你，你撤。"）  
【牌面依据】（引用抽到的牌和直译表，解释为什么这么翻）  
【具体建议】（针对你的处境，牌给你的动作方向）  
【锤子话】（一句收尾）

---

## 最后警告

不准偏离上表去自由心证。每张牌的翻译只能从表里取，除非问卜者问题特别具体（如"我的投资"），但即使如此，也须以元素和数字为基准微调，不能凭空捏造。

此技能只服务"准确"和"直给"。所有含糊其辞、讨好式解读，均为违规。
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
