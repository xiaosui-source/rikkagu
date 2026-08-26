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