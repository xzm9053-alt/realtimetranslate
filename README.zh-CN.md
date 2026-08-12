# 实时翻译（Live Translate）

[English](README.md)

一款 **Android 实时字幕**应用：捕获手机里正在播放的声音（媒体音）或麦克风，在**本地**用 SenseVoice 模型识别成文字，再翻译成你想要的文字，实时显示在**可拖动的悬浮窗**上。

- 识别**完全离线、本地完成**（SenseVoice 模型约 230MB，已内置进 APK），不上传音频
- 翻译可用 **DeepSeek**（流式、高质量）或 **微软免费端点**（无需 Key）
- 会话自动存**历史记录**，可导出为 `.txt`

---

## 功能

| 模块 | 说明 |
|------|------|
| **字幕页** | 源/目标语言（会记住）、声音来源、启动/停止、状态与最近预览 |
| **声音来源** | 媒体音 / 麦克风 / 媒体+麦克风（会记住；纯麦克风不弹录屏授权） |
| **本地识别** | 阿里 SenseVoice int8 模型，内置 APK，首启自动解压，离线可用 |
| **翻译引擎** | DeepSeek（流式 SSE，可配模型与 API 地址）/ 微软（免费、无需 Key） |
| **悬浮字幕** | 盖在其它 App 上；顶部细把手拖动位置；右下角把手缩放框体（不改字号） |
| **显示模式** | 仅译文，或双语（原文 + 译文，中间横线分隔） |
| **自动滚动** | 原文区 / 译文区各自滚动；**只有换行时才滚一行**，避免字跟着抖 |
| **音频采集** | 媒体：`MediaProjection` + `AudioPlaybackCapture`；麦克风：`AudioRecord` → 16 kHz PCM |
| **历史记录** | 每段会话按设置保存（自动清理 / 全部保存，上限 20 条）；历史页可展开、长按复制、每条存为 txt、一键清空 |
| **导出** | 停止后把本次原文+译文保存为 `.txt` 到系统下载目录 |
| **译音** | 译音开关与音量（默认关闭；音量可拉过 100% 压过原片声） |
| **设置** | 引擎与 API Key、识别模型状态/修复、字幕外观（字号、背景透明度、双语）、译音、历史策略、权限、关于 |
| **语言** | 跟随系统：手机中文 → App 中文；非中文 → App 英文 |

---

## 运行要求

### 使用 App

- Android **10 及以上**（API 29，系统内录音频需要）
- 用 DeepSeek 时：一个 [DeepSeek API Key](https://platform.deepseek.com/)；用微软免费引擎则无需任何 Key

### 从源码编译

- JDK **17+**、工程要求的 Android SDK（compileSdk **37** 等）
- Android Studio 或命令行 Gradle
- **必须先拉取内置模型**（见 [构建](#构建)）——模型约 240MB，超过 GitHub 100MB 单文件上限，**不放进仓库**

---

## 安装与使用

### 1. 安装

- 有正式包时从 [GitHub Releases](../../releases) 安装
- 或本地编译 debug/release 包（见下方 [构建](#构建)）

### 2. 配置翻译引擎

1. 打开 App → **设置**
2. 选引擎：
   - **DeepSeek**：填 API Key（可选改模型、API 地址）
   - **微软（免费）**：无需 Key，直接用
3. 点 **保存并测试连接** 验证

### 3. 启动字幕

1. 打开 **字幕** 页
2. 选 **源语言**（可自动检测；日/韩等改源语言后需重启一次会话生效）、**目标语言**
3. 选 **声音来源**（媒体音 / 麦克风 / 两者）
4. 点 **启动字幕**
5. 按提示授权：
   - **显示在其他应用上层**（悬浮窗）
   - 选了媒体音时：**录屏 / 投屏**（系统内录音频）
   - 选了麦克风时：**麦克风** 权限
6. 播放外文媒体或对着麦说话，悬浮窗里会出现译文

停止后若有内容，可在字幕页 **保存本次翻译为 txt**（存到下载目录，文件名类似 `8月13日-14:30-翻译结果.txt`）。会话也会按"历史记录"设置写入历史页。

### 4. 悬浮窗小技巧

- 拖顶部 **细横条** 移动位置
- 拖右下角 **把手** 改宽高（字号不变）
- 设置里可调：字号、背景透明度、双语开关、重置外观
- 历史页长按原文/译文块可复制，展开后每条可"保存为 txt"

---

## 构建

```bash
# Windows Git Bash 示例：指定 JDK
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.11_9"

# 若没有 local.properties，需配置 SDK 路径，例如：
# sdk.dir=C:/Users/你的用户名/AppData/Local/Android/Sdk

# 1. 先拉取内置模型（release 构建的硬性要求；debug 识别也需要它）
bash scripts/fetch-models.sh

# 2. 构建 debug
./gradlew :app:assembleDebug
```

产物路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

release 同理（`./gradlew :app:assembleRelease`），但**没跑 `fetch-models.sh` 时 release 会直接失败**（`preReleaseBuild` 守卫强制内置模型）。模型已内置的 APK 体积约 419MB。

也可在 Android Studio 中打开**仓库根目录** → Sync → Run。

> 若工程路径含中文等非 ASCII 字符，项目已设置 `android.overridePathCheck=true`。

---

## 项目结构

```text
app/src/main/java/com/xzm/realtimetranslate/
  ui/           # 字幕页、设置页、历史页、主题（MIUIX）
  service/      # 前台会话服务
  overlay/      # 悬浮字幕窗
  audio/        # 媒体内录 / 麦克风 / 混音 + 译音播放
  live/         # 实时翻译会话客户端（识别 + 翻译编排）
  translate/    # ASR 引擎 / DeepSeek / 微软免费翻译
  data/         # DataStore 设置、历史记录、API Key 存储
  util/         # txt 导出、模型下载、权限工具
```

App 通过 **Maven Central** 依赖 MIUIX 组件库。

---

## 隐私

- **识别在本地完成**（SenseVoice 模型内置），音频**不上传**
- 识别出的文字只发往你在设置里选择的翻译引擎（DeepSeek / 微软）
- API Key 仅保存在本机（可用时走 `EncryptedSharedPreferences`）
- 本项目**没有**自建后端收集密钥或音频
- 请勿提交 `local.properties`、密钥或签名文件

---

## 已知限制

- 部分 App / DRM 内容**禁止**被内录 → 媒体音模式下无声可译（可改用麦克风）
- 识别依赖 SenseVoice 模型，口音/噪音下可能有误差（模型固有局限）
- DeepSeek 上下文翻译基于**最近 4 句**，更早内容不参与
- 改源语言后需**重启一次字幕会话**生效
- "全部保存"上限 20 条，超出自动淘汰最旧

---

## 第三方

见 [NOTICE](NOTICE)。主要 UI 依赖：[MIUIX](https://github.com/compose-miuix-ui/miuix)（Apache-2.0）。

---

## 许可

[Apache License 2.0](LICENSE)

---

## 致谢

- [Linux.do](https://linux.do)
- 本项目基于 [luoxiaoxin123/live-translate](https://github.com/luoxiaoxin123/live-translate)（Apache 2.0）二次开发，含本地识别、DeepSeek/微软翻译、历史记录等改动
