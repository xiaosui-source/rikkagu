# HANDOFF — rikkahub（灵犀 Lingxi）

> 项目路径：`/workspace/rikkahub/rikkahub-master`
> 远程仓库：`new-origin` = `https://github.com/xiaosui-source/rikkagu.git`
> 分支：`master`（CI 构建成功，APK 由 Actions 自动产出）
> 工作流强制简体中文：`assembleUniversalGmsRelease`（带签名）+ SDK/Gradle 缓存
> 关键规则：**推送前全面自查、不本地编译**（编译交给 CI）

---

## 一、本次会话已完成的增量

### 1. 移除聊天记录「交接全部内容」功能（残留）
- 长按聊天记录菜单里的"交接"入口此前删过一次，但代码仍残留。
- 本次彻底清除：
  - `ChatDrawer.kt`：删 `onHandover` 传参 + `handoverConversationContent` 函数
  - `ConversationList.kt`：删 `onHandover` 参数(×2)、传参、菜单项
  - `strings.xml`：删 `conversation_handover` + `conversation_handover_desc` 两个 key（6 语言：values/zh/zh-rTW/ja/ko/ru）
- 提交：`04da8256`

### 2. 技能主动引导真正注入系统提示（修复 AI 用常识不用 skills）
- **根因**：`use_skill` 工具的 `systemPrompt`（含技能列表 + 主动使用指引）全项目**无调用点**，模型根本看不到有哪些技能、何时该用 → 只顾用通用常识回答。
- **修复**：
  - `SkillManager.kt`：新增 `listSkillsSync()`（非 suspend 同步版，供系统提示注入用）
  - `GenerationHandler.kt`：system 构建处常驻注入启用技能列表 + 主动引导（"命中技能必须 use_skill 加载遵循，不要用通用常识应付"）；通过 `KoinJavaComponent.getKoin().get()` 取 SkillManager
- 提交：`4bdd66a0`

### 3. Stdio MCP 传输支持（曾加后按用户要求整体回退）
- 曾实现 `StdioClientTransport.kt` + `McpConfig.StdioServer` + `McpSessionRegistry`/`SettingMcpPage` 支持，用于跑 Node MCP 服务器（如 minecraft-mcp/Mineflayer）。
- 用户最终决定不要，已 **revert 全部**（`a69748f7`），并删除 assets/mcp 打包源码。
- **结论**：项目 MCP 传输仅保留 SSE / Streamable HTTP，不支持 stdio 子进程。

### 4. 新增内置「图片 GPS 位置修改器」MCP（当前保留）
- `ImageGpsTools.kt`：`image_gps_get` / `image_gps_set` / `image_gps_clear`，读写图片 EXIF GPS。
- 用 `androidx.exifinterface`（版本 1.4.2），**注意 API 坑**：
  - 无 `ExifInterface(File)`，须用 `ExifInterface(file.absolutePath)`
  - `latLong` 不是属性，是 `getLatLong(FloatArray): Boolean`（参数是 FloatArray 不是 DoubleArray！）
  - `setLatLong(lat, long)` 参数是 Double
  - 写回用 `saveAttributes()`（String 构造可写）
- 接入：`McpManager.getBuiltinServerTools` 加 `imagegps` 分支；`getBuiltinServerInfos` 加 `builtin-imagegps` 卡片(3工具)；`Assistant.builtinMcpIds` 默认加 `imagegps`。
- 编译踩坑记录：`ExifInterface(File)`→`ExifInterface(path)`、`latLong`属性→`getLatLong(FloatArray)`。
- 提交：`0faafd6b`、`d317bd07`、`61bff2e2`

### 5. 离线本地规则模型「离线小助手」LocalRule（已删）
- 曾实现 `LocalRuleProvider`（纯本地规则引擎：智能对话、计算器、单位换算、笑话库、工具结果感知、话题化兜底）。
- 用户后来要求改为 GLM-4.7 Flash Heretic 本地模型，但该模型是 **safetensors MoE 大模型（几 GB）+ PyTorch transformers 格式，Android 无法直接跑，且与"不要模型文件"矛盾**，最终用户要求**全部删除离线模型**。
- **已彻底删除**（提交 `433911bb`）：
  - `LocalRuleProvider.kt` 删除
  - `ProviderSetting.kt` 删 LocalRule 类型 + Types 注册
  - `ProviderManager.kt` 删 import + 分支
  - `DefaultProviders.kt` 删条目
  - `ProviderConfigure.kt` 删空分支
- 全局无 `LocalRule`/`local-rule`/`离线小助手` 残留。

### 6. 新增内置「台风路径」MCP（当前保留）
- `TyphoonMcpTools.kt`：`typhoon_active` / `typhoon_detail`。
  - 用 OkHttp 拉取【中央气象台 NMC 台风网】公开源。
  - 精确适配 nmc 的「数组」结构：台风对象 `[id,enname,namecn,编号,编号,台风编号,含义,status]`（status="start"=活跃）；路径点 `[id,时间,epoch,级别,经度,纬度,气压,风速,移向,移速,...]`。
  - `typhoon_active` 拉 `list_default`；`typhoon_detail` 先在列表按名称/编号查 id，再拉 `view_{id}`。
- **数据源 2026-08-27 修复（重要）**：原默认源大部分 **404**（`list_current`/`list_6h`/`zj.slt.gateway` 均已失效），且原 `tfGet` 未校验 HTTP 状态码会把 404 页面误当数据返回。
  - 现已**实测**并只保留返回 **200** 的源：
    - `https://typhoon.nmc.cn/weatherservice/typhoon/jsons/list_default`（200）
    - `https://typhoon.nmc.cn/weatherservice/typhoon/jsons/view_{id}`（200）
  - `tfGet` 加状态码检查：非 2xx 直接判失败返回 null，404 不再误当数据。
  - **移除 `typhoon_search` 工具**（基于原文线条搜索意义不大），工具由 3 个减为 2 个；`McpManager` 卡片 `toolCount=3→2`、描述同步更新。
- 接入：
  - `McpManager.getBuiltinServerTools` 加 `taifeng` 分支 → `buildTyphoonMcpTools()`
  - `getBuiltinServerInfos` 加 `builtin-taifeng` 卡片（2 工具）
  - `Assistant.builtinMcpIds` 默认加 `taifeng`
  - `ToolRouter.route` 加"台风/台风路径/热带气旋"关键词兜底 → `typhoon_active`
- **注意**：NMC 接口为 JSONP（`func((...))`），解析器已剥离；若某接口再失效，`tfGet` 会因非 2xx 判失败并返回明确错误，不会静默成功。

### 7. 内网访问改「完全放开」（当前保留）
- **背景**：`http_request`（HttpRequestTool）与 `web_browse`（WorkspaceTools）原本有 SSRF 防护，禁止访问内网/回环地址。曾先实现「按需放行」（`intranetAccessGate`），后按用户要求改为**完全放开**。
- **现状**：已删除所有内网/回环地址拦截逻辑，AI 可自由访问内网、局域网、回环（127.0.0.1/localhost）地址。
- **清理**：
  - 删除 `intranetAccessGate()` 与 `isPrivateNetworkUrl()` 两个函数（HttpRequestTool.kt）。
  - 删除 `http_request` 与 `web_browse` 的 `allow_intranet` / `purpose` 参数。
  - 更新两工具 description 说明「内网可直接访问」。
- **注意**：若日后要恢复 SSRF 防护，参考上一版 HANDOFF 的被删逻辑（可按需放行或直接禁内网）。

### 8. 全局强制隐藏技能（让 AI 不傻，用户不可见/不可关）
- **动机**：用户希望所有 AI 全局默认具备「mattpocock/skills」工程/生产力方法论（灵犀已内置为「万能技能合集」35 技能），让 AI 遇复杂任务主动用 TDD/诊断反馈环/深度模块/代码审查等专业套路，而不是套平庸常识。
- **新增 `ForcedHiddenSkills.kt`**（`data/ai/tools/`）：
  - `globalSkillNames = setOf("万能技能合集")`：被强制全局启用的技能。
  - `SYSTEM_PROMPT_INJECT`：注入 system 的精简声明（约 150 字，省 token；只给名字+用途，全文靠 use_skill 加载）。
  - `filterHidden(meta)`：供 UI 过滤隐藏技能。
- **接入点**：
  - `GenerationHandler`：在「强制简体中文」后、常规技能注入前，**无条件**注入精简声明（不依赖 `assistant.enabledSkills`）→ 所有 AI 每次对话强制知道这套能力。
  - `ToolSurfaceBuilder` + `ChatService`(×2)：`use_skill` 工具**无条件创建**，`enabledSkills` 强制并入 `ForcedHiddenSkills.globalSkillNames` → AI 可 use_skill 加载「万能技能合集」。
  - UI 隐藏：`SkillsVM` / `AssistantDetailVM` / `ExtensionSelector` 拉取技能列表时 `filterHidden` → 用户在技能管理/选择界面**看不见**「万能技能合集」，也**无从关闭**（关闭入口即被过滤）。
- **要点**：
  - 只注入精简声明省 token；AI 真要时再 use_skill 加载完整 35 技能 SKILL.md。
  - 「万能技能合集」SKILL.md 无 `disable-model-invocation`，modelVisible，AI 可在 available_skills 里看到并可调用。
  - 依赖 `initDefaultSkills()` 已把该技能写入磁盘（首次启动自动）。若想再加别的全局强制技能，往 `globalSkillNames` 加名字即可（需 assets/skills 有对应目录）。
- **注意**：强制注入对所有模型生效（含弱模型，但声明短，影响可控）。若需对弱模型跳过，可在注入处加 `isWeakModel(model, provider)` 判断。

---

## 二、技术要点 / 踩过的坑

1. **Provider 体系扩展坑**（新增 ProviderSetting 子类时）：
   - 必须注册：`ProviderSetting.kt` 的 sealed 子类 + `Types` 列表 + `ProviderManager.getProviderByType` 分支 + `DefaultProviders.kt`(若默认提供) + `ProviderConfigure.kt` 的 when 分支。
   - `ProviderSetting` 的 `description`/`shortDescription` 是 `@Composable () -> Unit`，默认值 `{}`。
   - **字符串里的中英文引号**：Kotlin 字符串内嵌**裸英文双引号**会导致字符串提前终止 & "Literals must be surrounded by whitespace" 编译错。中文文案中引号用中文引号`“ ”`或书名号`【】`，不要用英文 `"`。
   - 实现 `Provider` 接口必须补齐所有抽象方法：`generateText`/`streamText`/`generateImage`（`generateEmbedding`/`editImage` 有默认实现，可省略）。

2. **androidx.exifinterface 1.4.2 API**：
   - 构造仅 `String(路径)` / `InputStream` / `FileDescriptor` / `ByteArray`，**没有 `File`**。
   - `getLatLong(FloatArray): Boolean`；`setLatLong(Double, Double)`；写回 `saveAttributes()`。
   - 读取方向用 `TAG_ORIENTATION` + `getAttributeInt`；GPS 标签 `TAG_GPS_LATITUDE(LONGITUDE)(_REF)`、`TAG_GPS_ALTITUDE(_REF)`。

3. **技能注入位置**：`GenerationHandler.generateInternal` system 构建里 `// 强制简体中文` 之后。`KoinJavaComponent.getKoin().get<SkillManager>()`；必须用 **同步** `listSkillsSync()`（`listSkills()` 是 suspend，不能在 buildString 里调用）。

4. **MCP 客户端**：只支持 SSE / Streamable HTTP。内置服务器直接返回 `List<Tool>`（Kotlin 实现），不走进程/子进程。

5. **安卓弱模型兜底调工具**：`GenerationHandler` 已有——当 provider 返回无可执行 tool_calls 但用户消息命中 `ToolRouter.route()` 时，客户端自动执行工具并把结果放回消息流，再 `continue` 让 provider 再次生成最终回答。

---

## 三、当前卡点 / 未决问题

1. **用户曾要求接入 GLM-4.7 Flash Heretic 本地模型**（`https://huggingface.co/jtl11/GLM-4.7-Flash-heretic`）。
   - 现实：safetensors / transformers 格式、MoE 大模型（几 GB），**Android 端不可直接运行**；与"不要模型文件"诉求矛盾。
   - 现状：离线模型已删，此需求搁置。若用户仍执着，需明确：换GGUF小模型用 llama.cpp，或接受联网 API。
2. 反代（DeepSeek 反代）需求用户提及但未落地（未加代码），用户说"直接在软件里加反代"后转去处理其他，未交付。**潜在待办**：是否内置一个 DeepSeek/OpenAI 兼容"一键反代"提供商预设。

---

## 四、下一步计划（如需继续）

- [ ] 确认是否补做「DS 反代」内置提供商（需用户提供 baseUrl 或决定用系统自带 OpenAI 兼容自定义能力）。
- [ ] GLM 本地模型需求：与用户对齐「手机可跑的小 GGUF 模型」或接受联网，再决定是否加 llama.cpp/onnx 推理。
- [ ] 持续跟进 CI：各提交后 `assembleUniversalGmsRelease` 均 success；如有新失败再修。

---

## 五、提交历史（本次相关）
- `04da8256` remove 交接残留
- `4bdd66a0` fix 技能主动引导注入
- `004458d4`→`a69748f7` Stdio MCP（加后 revert）
- `0faafd6b` feat 图片 GPS
- `d317bd07`/`61bff2e2` fix 图片 GPS 编译
- `433911bb` remove 离线小助手 LocalRule