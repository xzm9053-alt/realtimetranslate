# 独立闭源软件·版本更新检测与视频引流体系设计规范

## 1. 概述与设计意图

本项目为闭源的 Android 实时翻译应用。为在不依赖任何公开代码托管平台（如 GitHub）、不暴露源码隐私的前提下，实现应用版本更新感知并为作者新视频有效引流，本系统引入基于对象存储（腾讯云 COS / 阿里云 OSS）的轻量化无状态版本检测机制。

### 核心目标
1. **完全解耦与私密性**：客户端仅通过纯净的 CDN URL 请求轻量版本描述文件，绝不暴露开发仓库与作者个人隐私。
2. **平滑引流**：检测到新版本时，向用户呈现更新亮点并提供一键跳转至作者 B 站最新视频或动态的入口，实现顺畅引流与新版交付。
3. **零误打扰与完全可控**：日常开发、测试打包保持本地版本号不变，线上无任何误弹窗；仅在作者正式发布新视频并在云端提升版本号时触发推送。
4. **国内高可用与容错**：基于国内主流对象存储 CDN，毫秒级响应；若遇断网或弱网，客户端静默兜底，绝不影响核心功能运行。

---

## 2. 云端协议与数据格式规范

客户端请求的资源为一个轻量级的静态 JSON 文件 (`version.json`)。

### 2.1 协议格式
```json
{
  "versionCode": 2,
  "versionName": "v0.2.0",
  "title": "🎉 发现全新版本！",
  "message": "本次更新优化了核心性能，新增一键快捷操作。快来看看 UP 主的最新演示视频吧！",
  "videoUrl": "https://www.bilibili.com/video/BV1xxxxxx",
  "minSupportedCode": 1,
  "forceUpdate": false
}
```

### 2.2 字段定义
- `versionCode` (Int): 线上最新正式发布版本代码，单调递增。客户端依据此字段与 `BuildConfig.VERSION_CODE` 对比。
- `versionName` (String): 用户可见的版本标识字符串。
- `title` (String): 弹窗主标题。
- `message` (String): 更新详情日志或简短引流文案。
- `videoUrl` (String): 点击后跳转的目标链接（B 站视频/动态链接或网页直达链接）。
- `minSupportedCode` (Int, 可选): 最低兼容版本代码；低于此代码可建议强化升级。
- `forceUpdate` (Boolean, 可选): 是否强提示（默认 false，允许用户稍后提醒）。

---

## 3. 客户端架构与模块设计

### 3.1 模块职责划分

1. **`com.xzm.realtimetranslate.update.UpdateModel`**:
   - 定义 `RemoteUpdateInfo` 数据类，使用 `kotlinx.serialization` 注解支持安全反序列化。
   - 定义检查结果密封接口 `UpdateCheckResult`：
     - `HasUpdate(info: RemoteUpdateInfo)`: 发现新版
     - `AlreadyLatest`: 当前已是最新
     - `Failed(reason: String)`: 请求失败或解析异常

2. **`com.xzm.realtimetranslate.update.UpdateChecker`**:
   - 封装 OkHttp 异步请求，设置 3 秒连接/读取超时。
   - 解析响应内容并与本地 `BuildConfig.VERSION_CODE` 比对。
   - 捕获所有 `IOException` 与格式异常，记录到 `AppLog`，防止崩溃。

3. **`com.xzm.realtimetranslate.update.UpdatePreferences`**:
   - 记录用户交互状态（例如上次静默检查时间戳、被忽略的版本号），避免同一版本在单次运行或同一天内反复弹窗扰民。

4. **UI 层集成**:
   - **`MainActivity`（启动静默检查）**:
     - 在 Activity 创建后或主协程中异步触发一次静默检查。
     - 若存在新版本且未被当前会话跳过，弹出更新对话框。
   - **`SettingsScreen`（手动检查入口）**:
     - 在「关于/版本」卡片内添加「检查新版本」可点击项。
     - 触发时提供即时加载反馈，结果通过 Toast/Snackbar 明确反馈（若无新版提示“当前已是最新版本”，若有新版显示更新详情弹窗）。
   - **MIUIX 风格更新对话框**:
     - 遵循应用统一的 MIUIX 设计语言，展示版本号、更新亮点，提供「去看看新视频」和「暂不更新」两个操作。
     - 点击「去看看新视频」通过系统 `Intent.ACTION_VIEW` 优先唤起 Bilibili 客户端或系统默认浏览器。

---

## 4. 自动化推送脚本 (`scripts/push_update.py`)

为实现“用户一条指令，AI 自动完成全网推送”，在项目根目录下提供独立的发布脚本：
- 读取本地环境变量或配置中的腾讯云 COS / 阿里云 OSS 密钥。
- 接收参数：`--code`（新版本号）、`--name`（新版本名）、`--url`（新视频链接）、`--msg`（更新说明）。
- 一键构建新的 `version.json` 并覆盖至存储桶，实现全网秒级发布。

---

## 5. 异常处理与降级策略

1. **网络超时与阻断**：更新检查属于非关键边缘功能，任何网络故障（无网、DNS 解析失败、CDN 抖动）均降级为“静默忽略”，不向主界面抛出任何未捕获异常。
2. **无效/空 URL**：若 `videoUrl` 格式非法或为空，跳转按钮置灰或降级为仅展示更新说明。
3. **恶意篡改防御**：仅公有读私有写，严格校验反序列化字段类型与范围。
