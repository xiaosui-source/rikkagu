# 🍊 灵犀 Lingxi

> 源自 [rikkahub](https://github.com/scottwilliamavery26071994-bot/rikkahub) 的 AI 聊天助手，一款集 **多智能体协作、小说角色扮演、Minecraft 机器人、工具调用** 于一体的 Android AI 应用。
>
> 基于 **GNU AGPL v3** 开源（见 [LICENSE](LICENSE)）。

**版本 v2.4.5** · Compose Multiplatform · 多供应商模型 · 30+ 本地工具

---

## ✨ 功能全景

### 🧠 AI 对话核心

| 功能 | 说明 |
|---|---|
| **多供应商模型** | OpenAI / Claude / Gemini / DeepSeek / 智谱 / 通义 / Vertex 等；自定义 API 地址、请求头、请求体；二维码导入导出配置 |
| **多模态输入** | 图片理解、文档解析（PDF / DOCX / PPTX / EPUB）、OCR 文字提取 |
| **多助手体系** | 每个助手独立系统提示词、模型、温度 / topP / maxTokens、预设消息、正则替换、对话隔离 |
| **群聊模式** | 多 AI 同会话协作，消息分公开 / 私有 |
| **记忆系统** | 类 ChatGPT 跨对话记忆 + Memory Bank 长期向量记忆库 |
| **富文本渲染** | Markdown、代码高亮、LaTeX、Mermaid 流程图、表格 |
| **消息分支 / 翻译 / 标题** | 对话分叉探索、一键 AI 翻译、自动标题与回复建议 |
| **角色导入** | Silly Tavern 角色卡、Chatbox 聊天记录导入 |
| **工具调用三通道** | 原生 tool_calls + 纯文本 XML 解析（弱模型也能调工具）+ 规则路由兜底 |

### 🤖 多智能体联合工作（Agent）

- 配置多个不同专长的智能体（写作 / 翻译 / 代码 / …），每个绑定一个现有助手（复用其模型与密钥）
- 主助手在对话中识别到子任务属于某智能体专长时，自动调用 `agent_call_<id>` 工具**转交**给它
- 被调起的智能体用自己绑定的模型**独立子会话**完成任务，结果回传主助手继续作答
- 禁止嵌套转交，防止死循环；转交过程以工具卡片形式展示
- 入口：**设置 → 智能体管理**

### 📖 小说导入 · 角色扮演

- **本地导入**：TXT / EPUB 整本导入，自动切分章节、启发式提取角色（"XX说道/笑道" 词频）
- **搜索自动获取**：输入小说名 → 自动搜索在线资源 → 点击导入抓取内容并解析
- **角色扮演**：选择角色 → 自动创建"角色扮演"助手（注入角色设定 + 小说节选 + 扮演规则）→ 跳转对话，AI 以原著角色口吻互动
- 支持自定义剧情简介，增强扮演设定
- 入口：**设置 → 小说角色扮演**

### 🎮 Minecraft 机器人（AI 进服游玩）

- AI 作为游戏内机器人，Java / 基岩版双支持
- **离线模式** `auth=offline`：无需正版，直接以自定义名字进服
- **微软正版登录** `auth=microsoft`：**PCL2 同款设备码网页授权**——自动打开浏览器微软登录页，用户在浏览器登录并输入代码，无需提供账号密码
- 机器人操作：连接、说话、查询状态；基岩版支持 RCON 命令（传送 / 放置 / 召唤 / 时间天气等）
- **AI 自主通关模式**：设定目标（建家 / 挖矿 / 击败末影龙…），AI 自主循环"查状态 → 行动 → 汇报进度"直到通关

### 🛠️ 工具系统（30+ 本地工具）

- **系统控制**：时间、剪贴板、TTS 语音、电池、亮度、音量、手电筒、震动、闹钟、日历、TODO、短信、WiFi / 存储 / 电话信息、通知、Toast、分享、壁纸、媒体扫描
- **UI 自动化**（无障碍）：点击 / 滑动 / 长按 / 输入 / 滚动 / 截图 / 读取窗口树 / 找节点
- **硬件**：摄像头拍照、定位与附近搜索（高德）、Gadgetbridge 手环健康数据
- **网络**：网页抓取、HTTP 请求、多引擎搜索（Exa / Tavily / 智谱 / Brave / Perplexity / Custom JS）
- **文件**：文件系统读写、ZIP 打包 / 解析、工作区（Workspace）、SSH / SFTP 远程终端
- **特色**：抖音 MCP、12306 火车票、APK 逆向、行测 MCP、知识库、`eval_javascript`、`ask_user`、消息桥接
- **安全**：命令守卫（HardlineCommandGuard）、安全审计日志

### 🧩 扩展生态（Extensions）

API Explorer · 五子棋 AI 对战 · 工具箱（Base64 / 时间戳 / 密码 / JSON / 颜色 / 进制 / 正则）· AI 语音通话 · SSH 远程 · Memory Bank · **主动消息**（定时发送）· **微信 Bot**（微信变 AI 网关）· **工作流**（Tasker 式：触发 + 条件 → 动作，支持 cron 定时）· 本地插件系统（ZIP 导入 + 声明式 UI + WebView 插件）· MCP 协议（内置 / 外部 / 本地服务器）

### 🎨 个性化定制

Material You 动态取色 · 暗色模式 · 预测性返回 · 头像框 · 气泡透明度 · 思维链样式 · 输入框背景 · 字体包导入

### ☁️ 数据与同步

Supabase 云同步（多设备无缝切换）· WebDAV / S3 备份恢复 · 本地备份 · 消息全文搜索（FTS）· 导出 / 导入

### 🖥️ 多端访问

Android 原生客户端 + 内置 Web 服务（浏览器访问，React Web UI）

---

## 🏗️ 项目架构

```
├── app/          # 主应用（UI、服务、数据层、工具、扩展）
├── ai/           # 多供应商 AI SDK（Provider / Model / Tool / Stream）
├── common/       # 公共组件与资源
├── search/       # 搜索服务 SDK（Bing / 搜狗 / 360 / DuckDuckGo / Google / SearXNG / Custom JS…）
├── document/     # 文档解析（PDF / DOCX / PPTX / EPUB / CSV）
├── highlight/    # 代码高亮
├── speech/       # 语音（TTS / ASR）
├── workspace/    # 工作区（文件沙箱）
├── material3/    # Material3 组件（vendored material-color-utilities）
├── web-ui/       # Web 前端（React）
├── website/      # 项目网站
└── skills/       # 技能定义
```

### 关键模块

| 路径 | 职责 |
|---|---|
| `data/ai/` | 生成链路（GenerationHandler）、文本 XML 工具解析、规则路由、工具系统 |
| `data/ai/agents/` | **多智能体**（AgentProfile / AgentStore / AgentRunner / AgentTools） |
| `data/novel/` | **小说角色扮演**（NovelParser / NovelScene / NovelStore） |
| `data/ai/tools/` | 55 个工具文件（含 Minecraft 机器人 / 微软设备码授权） |
| `data/datastore/` | 全局设置（SettingsStore）、默认模型与助手 |
| `data/db/` | Room 数据库（会话 / 消息 / 记忆 / FTS） |
| `workflow/` | 工作流引擎（触发 + 条件 + 动作） |
| `plugin/` | 插件系统（导入 / 声明式 UI / WebView） |

---

## 🚀 构建与安装

### GitHub Actions（推荐）

仓库已配置 CI（`.github/workflows/build-apk.yml`）：push / 手动触发即构建 **Release APK**（Gradle 官方源 + 单 job 提速）。

1. 打开仓库 **Actions** 页 → 最新一次成功运行
2. 底部 **Artifacts → Release-APK** 下载安装

### 本地构建

```bash
# 环境要求：JDK 17+、Android SDK 37、Gradle 9.4.1（wrapper 已内置）
./gradlew assembleRelease -x lint -x lintVitalRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

### 技术栈

Kotlin · Jetpack Compose（Material3）· Koin（DI）· Room · OkHttp · kotlinx.serialization · Coroutines · Navigation3 · AGP 9.1.1 · Gradle 9.4.1

---

## 📄 许可证

本项目基于 **GNU AGPL v3** 开源。详见 [LICENSE](LICENSE)。
