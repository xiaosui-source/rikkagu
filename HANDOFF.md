# HANDOFF.md

## 最近任务 — 恢复本地供应商功能（🔄 进行中）

### 当前任务
从 rikkahub-agent 仓库恢复本地供应商功能，但只保留从该仓库深度集成的核心部分，不保留 LiteRt 特定实现文件。

### ✅ 已完成
- **local-llm 模块**：恢复核心文件（6个）
  - AcceleratorProbe.kt - 加速器探测
  - LocalRuntime.kt - 本地运行时
  - LocalRuntimePreferences.kt - 运行时配置
  - MemoryGuard.kt - 内存保护
  - ModelInstall.kt - 模型安装
  - build.gradle.kts - 模块构建配置
- **ProviderSetting.kt**：添加 `LocalModel` 类型
  - 兼容 OpenAI API 格式
  - 默认地址 `http://localhost:11434/v1`（Ollama）
  - 支持自定义 `modelFilePath` 字段
- **DefaultProviders.kt**：添加本地模型到默认供应商列表
  - 名称："本地模型"
  - 描述："运行在设备本地的 LLM 推理服务（Ollama / llama.cpp / vLLM）"
- **构建配置**：
  - settings.gradle.kts：添加 local-llm 模块
  - ai/build.gradle.kts：添加 local-llm 依赖
  - local-llm/build.gradle.kts：添加 LiteRT-LM 依赖（0.11.0）
  - gradle/libs.versions.toml：添加 litertlm 依赖

### ❌ 已删除（LiteRt 特定实现，非从 rikkahub-agent 迁移）
- LiteRtCatalog.kt
- LiteRtModelConfig.kt
- LiteRtModelMetadata.kt
- LiteRtProvider.kt
- LiteRtRuntime.kt
- LiteRtToolBridge.kt
- LiteRtToolBridgeRegistry.kt
- LiteRtToolPrefix.kt
- jniLibs/ 目录

### ⚠️ 卡住的问题
- **local-llm 模块构建失败**：
  - `mergeReleaseJniLibFolders` 任务失败（current 为 null）
  - 循环依赖：local-llm 依赖 ai，ai 依赖 local-llm
  - 已尝试移除循环依赖和 jniLibs 配置

### 📋 下一步
1. 修复 local-llm 模块的构建配置
2. 确保本地供应商功能完整可用
3. 测试本地模型加载和推理

### 🕳️ 踩过的坑
- **循环依赖问题**：local-llm 模块依赖 ai 模块，导致构建失败
- **jniLibs 配置问题**：空的 jniLibs 目录导致 merge 任务失败
- **继承冲突**：LocalModel 最初尝试继承 OpenAI，导致序列化和组件函数冲突

---

## 历史任务 — RikkaHub 备份改为全量备份（含工作区全部数据）

### 当前任务
让"备份与恢复"备份全部数据，工作区(workspace)里的数据也要备份。

### ✅ 已完成
- 本地 BackupManager：改为全量备份
  - databases/ (Room rikka_hub.db + wal + shm)
  - files/ (workspace/上传/datastore设置/媒体/技能/插件全部)
  - shared_prefs/
  - restore 完整还原，重启生效
- WebDavSync：备份/恢复补上 workspace/
- S3Sync：备份/恢复补上 workspace/
- BackupDialog 文案改为"打包全部数据"

### ⚠️ 注意
- WebDAV/S3 的 zip 用顶层条目(rikka_hub.db 等)，BackupManager 用 databases/ 前缀，两套独立备份格式

### 📋 踩过的坑
- WebDavSync/S3Sync 原本只备份 upload/skills/数据库，未包含工作区，需补 workspace + restoreWorkspaceEntry

---

## 历史任务 — RikkaHub 抖音 MCP 登录优化（2026-08-09）

### 当前任务
让 `douyin_login` 直接在 RikkaHub 对话中显示登录二维码，用户用手机抖音扫码即自动登录，无需手动复制 Cookie。

### ✅ 已完成
- `douyin_login` 改为模拟浏览器完整参数调用抖音 passport `/get_qr_code/` API
  - 参数：device_platform/aid/channel/version_code/app_name 等
  - 拿到 `qrcode`(data URI) → `UIMessagePart.Image` 直接展示二维码
  - 保存 `token` 到沙箱 `~/.config/douyinmcp/qr_token.txt`
- 新增 `douyin_open_login`：App 内置浏览器打开抖音登录页（API 被风控时的降级方案）
- `douyin_check_login`：从沙箱读 token 查询登录状态，确认后台自动保存 Cookie

### ⚠️ 卡住的问题
- **抖音 passport get_qr_code 在沙箱数据中心 IP 被风控**（error_code 16"该应用无权限"/22"非法应用"）
- 原因：纯 HTTP 无浏览器指纹（缺 verifyFp/msToken/ttwid），抖音拒发二维码
- **App 端是否放行未验证**：RikkaHub 用 OkHttp 从手机真实 IP 请求，网络指纹更接近正常用户，很可能能过风控，需真机验证
- 备选方案（若 App 端仍被拦截）：接入第三方扫码登录服务 / 用 Android WebView 渲染登录页截二维码

### 📋 下一步
1. 真机测试 `douyin_login` 能否成功返回二维码图片
2. 若能 → 验证扫码后 `douyin_check_login` 自动确认登录流程
3. 若不能 → 在 App 内用 WebView 打开登录页截二维码，或接第三方扫码服务

### 🕳️ 踩过的坑
- **Chromium headless 在 proot 不可用**：`ProcessSingleton` 绑定失败(EINVAL)，与 Shannon 的 Unix socket 坑同源
- **playwright npm 包未全局安装**（只有 `@playwright/cli` + chromium 二进制），`require('playwright')` 失败
- **抖音 `get_qr_code` 完整参数仍被拒**：需 verifyFp 等 JS 生成指纹，纯 HTTP 伪造不了

---

## 历史任务 — Shannon AI Pentester 原生安装

## 当前任务（原）
在 **无法运行 Docker** 的沙箱环境（proot + Android 内核）中让 Shannon 完全原生运行。

## ✅ 已完成

| # | 事项 | 细节 |
|---|------|------|
| 1 | Node.js v22 | 从 v18 → v22.23.2（Shannon 需 `styleText`） |
| 2 | Shannon CLI v2.3.0 | `npm install -g @keygraph/shannon`，`su - shannon-user` 绕过 root 检测 |
| 3 | Temporal Server | `crane export --platform linux/arm64 temporalio/temporal:1.7.0` 提取原生二进制 |
| 4 | Worker 源码编译 | `git clone` → `pnpm install` → `tsc` |
| 5 | Docker 命令拦截器 | `/usr/local/bin/docker` wrapper，拦截 compose/run/ps/stop 等 |
| 6 | Chromium (Playwright) | `npx playwright install chromium` → `/usr/local/bin/chromium` |
| 7 | 原生启动器 | `/usr/local/bin/shannon-native`，一键 Temporal + Worker |
| 8 | 完整流水线验证 | ✅ Temporal 连接 → webpack 打包 → Worker RUNNING → preflight → 到 LLM 调用前 |

## ⚠️ 卡住的问题

| # | 问题 | 严重 | 说明 |
|---|------|------|------|
| 1 | **Temporal 不持久** | 中 | workspace_shell 之间进程被杀。改进 `shannon-native` 在同一命令里跑 |
| 2 | **缺 LLM API Key** | 高 | 真实扫描需 `ANTHROPIC_API_KEY`。流水线已到 "No credentials found" |
| 3 | **Docker wrapper 边缘情况** | 低 | `docker logs`、复杂 `-v` 映射可能漏掉 |

## 📋 下一步

1. 提供真实 `ANTHROPIC_API_KEY`，跑完整扫描
2. 用 OWASP Juice Shop 测试端到端
3. 加固 Docker wrapper（补 `docker logs`）

## 🕳️ 踩过的坑（关键 7 个）

| # | 坑 | 教训 |
|---|-----|------|
| 1 | **Unix socket 在 proot 不可用** | `bind()` AF_UNIX → EINVAL。Docker/Podman 全挂。**唯一出路：TCP + wrapper** |
| 2 | **`crane export` 默认拉 x86_64** | ARM 环境跑 x86 二进制 → Illegal instruction。**必须 `--platform linux/arm64`** |
| 3 | **Temporal `--ip 0.0.0.0` 静默退出** | 换 `--ip 127.0.0.1` 解决 |
| 4 | **Worker import 假死** | `parseCliArgs([])` → `process.exit(0)`。不是 hung，是秒退 |
| 5 | **pnpm workspace 依赖路径** | `@temporalio` 在 `apps/worker/node_modules/`，必须在 worker 目录下运行 |
| 6 | **Shannon 拒绝 root** | `su - shannon-user` 绕过 |
| 7 | **`chromium-browser` 是 snap 壳** | Ubuntu 的 deb 包只装 snap wrapper。**必须 `npx playwright install chromium`** 下载真实二进制 (~300MB) |

## 🚀 使用方法

```bash
# 设置 API Key
export ANTHROPIC_API_KEY=sk-ant-...

# 一键扫描
shannon-native https://target.com /path/to/repo --workspace my-scan

# 或通过 Shannon CLI（先启动 Temporal）
start-temporal.sh
su - shannon-user -c "shannon start --url https://target.com --repo /path/to/repo"
```

## 📁 关键路径

```
/usr/local/bin/shannon-native    # 原生启动器
/usr/local/bin/docker            # Docker 命令拦截器
/usr/local/bin/temporal          # Temporal Server 原生二进制
/usr/local/bin/chromium          # Playwright Chromium
/usr/local/bin/start-temporal.sh # Temporal 启动脚本
/workspace/shannon-repo/         # Shannon 源码 + 编译产物
```
