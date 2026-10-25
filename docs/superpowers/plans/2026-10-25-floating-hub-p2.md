# 通用常驻悬浮球与多模式入口整合（P2）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现一个常驻屏幕边缘的通用半球悬浮球（`FloatingHubService`），点球可唤出七种翻译模式菜单与运行时快捷控制，并将现有的声音同传与屏幕取词/全屏翻译无缝收敛接入，免除多个悬浮球冲突。

**Architecture:** 
1. 抽离纯 Kotlin 状态机 `HubBus`，统一管理悬浮球状态（`IDLE`、`VOICE`、`SCREEN_REGION`、`FULLSCREEN`、`COMIC`）与命令流，双向同步 `SessionBus` 和 `ScreenOcrBus`；
2. `FloatingHubService` 作为常驻前台服务宿主，通过 `HubBallOverlay`（基于现有 `BallView` 真实半球渲染与边缘贴吸算法）承载统一悬浮球与长按/点击弹出菜单；
3. 改造 `SubtitleOverlayController`（同传窗口）与 `OcrBallOverlay`（屏幕取词）：当 Hub 开启时，两者的私有悬浮球让位给 Hub，声音字幕拖边直接隐藏并由 Hub 唤回，屏幕取词气泡与红框由 Hub 统一控制暂停/恢复；
4. 首页（`SubtitleScreen`）与设置增加悬浮球总开关；点选静态模式（相机/图片/文档）拉起 `MainActivity` 对应模块。

**Tech Stack:** Kotlin、Android Foreground Service (`specialUse`)、WindowManager Overlay、StateFlow/SharedFlow、Jetpack Compose、DataStore、JUnit4。

**Spec:** `docs/superpowers/specs/2026-10-24-hub-modes-design.md`

## Global Constraints

- `minSdk = 29`，`targetSdk = 35`，包名 `com.xzm.realtimetranslate`。
- 遵循 MIUIX 设计语言与应用既有主题规范，不引入体积庞大的第三方库。
- 录屏授权令牌仅由屏幕会话服务持有，`FloatingHubService` 不持有 `MediaProjection`；若从 Hub 触发需要录屏的模式，引导至系统授权弹窗。
- 悬浮球贴边与半球裁剪严格沿用 `BallView` 的物理屏幕边缘裁剪设计（窗口宽度为 `size/2`，圆心对准边界），杜绝负坐标被 MIUI 强制弹回整圆的缺陷。
- 悬浮球菜单使用透明全屏 Scrim 窗口，点击外部区域立即轻巧关闭。
- 保持现有 241 个单测全部通过，新增组件需具备严格的单元测试覆盖。
- 提交信息用规范中文，不自动 push（遵照用户指示等待确认）。
- Windows 构建工作流标准命令：
  ```bash
  export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest assembleRelease --console=plain > E:\AAAtranslator\build_log_hub.txt 2>&1"; echo "GRADLE_EXIT=$?"
  ```

## Review Focus

1. **多服务生命周期与悬浮球抢夺**：当 Hub 开启时，同时启动声音同传或屏幕取词，不能出现屏幕上同时漂浮两个半球的情况；Hub 关闭时，旧模式的原生小球需平滑复活。
2. **声音同传拖边隐藏与唤回**：同传窗口在 Hub 开启时拖到边缘应平滑 `hide()` 而不是创建旧小球；Hub 菜单里必须能一键「显示字幕窗口」将窗口完整唤回。
3. **录屏权限中断与回退**：用户在 Hub 菜单点击「全屏翻译」或「屏幕取词」，如果用户取消录屏授权，Hub 应平稳回到 `IDLE` 状态，不得卡死在 `STARTING`。
4. **横竖屏旋转适配**：屏幕旋转时，`HubBallOverlay` 需像现有 `OcrBallOverlay` 一样通过 `ComponentCallbacks` 自动重新 reclamp 到屏幕边缘，禁止漂到屏幕外面。
5. **触摸穿透与 Scrim 泄漏**：Hub 菜单弹出时吃触摸，关闭后 Scrim 窗口必须彻底从 `WindowManager` 移除，绝不留下一层透明图层挡住下层 App。

---

### Task 1: 核心状态机 `HubBus` 设计与单元测试

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/service/HubBus.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/service/HubBusTest.kt`

**Interfaces:**
- Produces:
  - `enum class HubMode { IDLE, VOICE, SCREEN_REGION, FULLSCREEN, COMIC }`
  - `data class HubUiState(val isHubEnabled: Boolean = false, val mode: HubMode = HubMode.IDLE, val isPaused: Boolean = false, val isSubtitleWindowVisible: Boolean = true)`
  - `sealed class HubCommand`：`TogglePause`, `StopCurrentMode`, `SwitchMode(val mode: HubMode)`, `SetSubtitleWindowVisible(val visible: Boolean)`, `CloseHub`, `LaunchStaticMode(val target: StaticModeTarget)`
  - `object HubBus`：`val state: StateFlow<HubUiState>`, `val commands: SharedFlow<HubCommand>`, `fun setHubEnabled(enabled: Boolean)`, `fun setMode(mode: HubMode)`, `fun setPaused(paused: Boolean)`, `fun setSubtitleWindowVisible(visible: Boolean)`, `fun postCommand(cmd: HubCommand)`, `fun syncFromSession(sessionStatus: SessionBus.Status)`, `fun syncFromScreenOcr(ocrStatus: ScreenOcrBus.Status, ocrMode: ScreenOcrMode)`

- [ ] **Step 1: 编写失败测试 `HubBusTest.kt`**

```kotlin
package com.xzm.realtimetranslate.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HubBusTest {

    @Before
    fun setUp() {
        HubBus.reset()
    }

    @Test
    fun `initial state is idle and disabled`() {
        val s = HubBus.state.value
        assertFalse(s.isHubEnabled)
        assertEquals(HubMode.IDLE, s.mode)
        assertFalse(s.isPaused)
        assertTrue(s.isSubtitleWindowVisible)
    }

    @Test
    fun `sync from SessionBus updates mode to VOICE when running`() {
        HubBus.setHubEnabled(true)
        HubBus.syncFromSession(SessionBus.Status.Running)
        assertEquals(HubMode.VOICE, HubBus.state.value.mode)

        HubBus.syncFromSession(SessionBus.Status.Idle)
        assertEquals(HubMode.IDLE, HubBus.state.value.mode)
    }

    @Test
    fun `sync from ScreenOcrBus reflects region and fullscreen mode`() {
        HubBus.setHubEnabled(true)
        HubBus.syncFromScreenOcr(ScreenOcrBus.Status.Running, ScreenOcrMode.FULLSCREEN)
        assertEquals(HubMode.FULLSCREEN, HubBus.state.value.mode)

        HubBus.syncFromScreenOcr(ScreenOcrBus.Status.Paused, ScreenOcrMode.FULLSCREEN)
        assertTrue(HubBus.state.value.isPaused)

        HubBus.syncFromScreenOcr(ScreenOcrBus.Status.Stopped, ScreenOcrMode.FULLSCREEN)
        assertEquals(HubMode.IDLE, HubBus.state.value.mode)
    }

    @Test
    fun `command emission works with buffer`() = runTest {
        HubBus.postCommand(HubCommand.TogglePause)
        val cmd = HubBus.commands.first()
        assertEquals(HubCommand.TogglePause, cmd)
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --tests com.xzm.realtimetranslate.service.HubBusTest --console=plain > E:\AAAtranslator\build_log_hubt1.txt 2>&1"; echo "GRADLE_EXIT=$?"
```
预期：编译失败，找不到 `HubBus` 类。

- [ ] **Step 3: 实现 `HubBus.kt`**

在 `app/src/main/java/com/xzm/realtimetranslate/service/HubBus.kt` 中实现纯 Kotlin 状态机，包含状态流、命令流与状态同步逻辑。

- [ ] **Step 4: 运行测试验证通过**

Run：同 Step 2 命令。预期：`BUILD SUCCESSFUL`，4 个用例全部通过。

- [ ] **Step 5: 提交任务成果**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/service/HubBus.kt app/src/test/java/com/xzm/realtimetranslate/service/HubBusTest.kt
git commit -m "feat(hub): 新增统一悬浮球核心状态机与命令总线 HubBus"
```

---

### Task 2: DataStore 配置 `hub_enabled` 拓展与持久化

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/data/UserSettings.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/data/UserSettingsRepository.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/data/HubSettingsTest.kt`

**Interfaces:**
- Consumes: `UserSettings`, `UserSettingsRepository`
- Produces:
  - `UserSettings.hubEnabled: Boolean`（默认 `false`）
  - `UserSettingsRepository.Keys.hubEnabled`
  - `SettingsViewModel.setHubEnabled(Boolean)`

- [ ] **Step 1: 编写失败测试 `HubSettingsTest.kt`**

```kotlin
package com.xzm.realtimetranslate.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubSettingsTest {

    @Test
    fun `hub enabled defaults to false`() {
        val settings = UserSettings()
        assertFalse(settings.hubEnabled)
    }

    @Test
    fun `hub enabled can be toggled`() {
        val settings = UserSettings(hubEnabled = true)
        assertTrue(settings.hubEnabled)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --tests com.xzm.realtimetranslate.data.HubSettingsTest --console=plain > E:\AAAtranslator\build_log_hubt2.txt 2>&1"; echo "GRADLE_EXIT=$?"
```
预期：编译失败，找不到 `hubEnabled` 属性。

- [ ] **Step 3: 修改 `UserSettings.kt`、`UserSettingsRepository.kt` 与 `SettingsViewModel.kt`**

1. `UserSettings`: 增加 `val hubEnabled: Boolean = Defaults.HUB_ENABLED`，在 `Defaults` 中增加 `const val HUB_ENABLED = false`。
2. `UserSettingsRepository`: 在 `Keys` 增加 `val hubEnabled = booleanPreferencesKey("hub_enabled")`，在 `toSettings()` 和 `update()` 中处理键值存取。
3. `SettingsViewModel`: 增加 `fun setHubEnabled(enabled: Boolean)`，保存时同步调用 `HubBus.setHubEnabled(enabled)`。

- [ ] **Step 4: 运行测试验证通过**

Run：同 Step 2 命令。预期：`BUILD SUCCESSFUL`。

- [ ] **Step 5: 提交任务成果**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/data/UserSettings.kt app/src/main/java/com/xzm/realtimetranslate/data/UserSettingsRepository.kt app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsViewModel.kt app/src/test/java/com/xzm/realtimetranslate/data/HubSettingsTest.kt
git commit -m "feat(hub): UserSettings 新增悬浮球常驻开关配置项 hubEnabled"
```

---

### Task 3: `FloatingHubService` 前台服务创建与 Manifest 声明

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/service/FloatingHubService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml` & `values-zh/strings.xml`

**Interfaces:**
- Produces:
  - `FloatingHubService : Service`
  - 伴生方法 `start(context: Context)` 与 `stop(context: Context)`
  - 系统前台通知：通知渠道 `hub_channel`，标题 `悬浮球已开启`，快捷动作「关闭悬浮球」

- [ ] **Step 1: 编写字符串资源**

在 `values/strings.xml` 与 `values-zh/strings.xml` 增加：
- `hub_channel_name`: "悬浮球常驻服务" / "Floating Hub Service"
- `hub_notification_title`: "实时翻译悬浮球已开启" / "LiveTranslate Hub Active"
- `hub_notification_text`: "点击管理模式或关闭" / "Tap to manage modes or close"
- `hub_action_close`: "关闭悬浮球" / "Close Hub"

- [ ] **Step 2: 实现 `FloatingHubService.kt`**

1. 继承 `Service`，`onStartCommand` 中调用 `startForeground`（使用 `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`）；
2. 监听 `HubBus.commands`：收到 `HubCommand.CloseHub` 时调用 `stopSelf()`；
3. 注入 `CoroutineScope(SupervisorJob() + Dispatchers.Main)`，在 `onDestroy` 时清理所有窗口并更新 `HubBus.setHubEnabled(false)`。

- [ ] **Step 3: 在 `AndroidManifest.xml` 中声明服务**

```xml
<service
    android:name=".service.FloatingHubService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Persistent floating translation hub ball and mode launcher" />
</service>
```

- [ ] **Step 4: 编译检查**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:assembleDebug --console=plain > E:\AAAtranslator\build_log_hubt3.txt 2>&1"; echo "GRADLE_EXIT=$?"
```
预期：`GRADLE_EXIT=0`。

- [ ] **Step 5: 提交任务成果**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/service/FloatingHubService.kt app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat(hub): 新增 FloatingHubService 前台服务框架与系统通知"
```

---

### Task 4: `HubBallOverlay` 悬浮球贴边控制与多模式菜单渲染

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/overlay/HubBallOverlay.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/service/FloatingHubService.kt`

**Interfaces:**
- Consumes: `BallView`, `HubBus`, `HubMode`, `HubCommand`
- Produces:
  - `class HubBallOverlay(context: Context, onAction: (HubAction) -> Unit)`
  - 窗口管理：`show()`, `hide()`, `updateState(state: HubUiState)`
  - 菜单系统：
    - `IDLE` 菜单：展示 7 大模式（声音同传、屏幕取词、全屏翻译、漫画翻译、相机、图片、文档）+ 关闭球
    - `VOICE` 运行态菜单：显隐字幕大窗、暂停/继续、退出声音同传
    - `SCREEN` 运行态菜单：暂停/继续、重选区域（如果是取词）、显隐取词框、退出屏幕翻译

- [ ] **Step 1: 实现 `HubBallOverlay.kt` 的半球窗口与拖拽吸附**

1. 继承或复用 `BallView`，窗口初始尺寸 `size/2`，贴在屏幕右边缘 40% 高度处；
2. 继承 `OnTouchListener`：处理拖拽中的整球放大、跨边判定、松手后的磁吸贴边（`SIDE_LEFT` / `SIDE_RIGHT`）；
3. 触觉反馈：贴边吸附时调用 `triggerSnapHaptic()`。

- [ ] **Step 2: 实现全屏透明 Scrim 与卡片式菜单 `showMenu()`**

1. 点击悬浮球弹出菜单窗口，外层全屏透明 `FrameLayout` 拦截外部点击（点击空白处自动 dismiss）；
2. 菜单卡片采用 MIUIX 风格圆角半透明卡片（`#F2242426` / `#F2FFFFFF`，边框 `1dp #26FFFFFF`，圆角 16dp）；
3. 根据当前 `HubBus.state.value.mode` 动态渲染菜单项：
   - 空闲时展示大模式列表；
   - 运行态时展示运行时控制（暂停、停止、显隐窗口）。

- [ ] **Step 3: 将 `HubBallOverlay` 挂载到 `FloatingHubService`**

在 `FloatingHubService` 的 `onCreate` 中构建 `HubBallOverlay`，并在 `onStartCommand` 中 `show()`；在协程中 `collect` `HubBus.state` 并调用 `overlay.updateState(state)`。

- [ ] **Step 4: 编译检查**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:assembleDebug --console=plain > E:\AAAtranslator\build_log_hubt4.txt 2>&1"; echo "GRADLE_EXIT=$?"
```
预期：`GRADLE_EXIT=0`。

- [ ] **Step 5: 提交任务成果**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/overlay/HubBallOverlay.kt app/src/main/java/com/xzm/realtimetranslate/service/FloatingHubService.kt
git commit -m "feat(hub): 实现 HubBallOverlay 悬浮球贴边手势与状态感知交互菜单"
```

---

### Task 5: 现有服务解耦：同传贴边隐藏与 OCR 球让位

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/overlay/SubtitleOverlayController.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/overlay/OcrBallOverlay.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/service/SubtitleSessionService.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/service/ScreenTextSessionService.kt`

**Interfaces:**
- Consumes: `HubBus.state`
- Behavior:
  - `SubtitleOverlayController`: 当 `HubBus.state.value.isHubEnabled == true` 时，拖窗口到屏幕边缘松手不展开小球，而是直接 `hide()`（保留会话与字幕缓冲），并通知 `HubBus.setSubtitleWindowVisible(false)`；新增 `showWindow()` 供 Hub 菜单调用重新显示大窗。
  - `OcrBallOverlay`: 拆分 `ballWindowView` 的显隐逻辑；当 Hub 启用时，隐藏自身的 `ballWindowView`，仅保留气泡 `popupWindowView`、外框 `outlineWindowView` 与拖动圆点 `regionHandleWindow`。

- [ ] **Step 1: 改造 `SubtitleOverlayController.kt`**

1. 在 `maybeFold(side)` 判定中，检查 `HubBus.state.value.isHubEnabled`：
   - 若启用：直接触发渐隐动画并将窗口 `hide()`，向 `HubBus` 发送 `SetSubtitleWindowVisible(false)`；
   - 若未启用：保持原有折叠为半球动画。
2. 增加公共方法 `showWindow()`：将大窗恢复到保存的位置，并通知 `HubBus.setSubtitleWindowVisible(true)`。

- [ ] **Step 2: 改造 `OcrBallOverlay.kt`**

1. 增加 `var hubModeEnabled: Boolean = false`；
2. 当 `hubModeEnabled` 为 true 时，跳过 `ballWindowView` 的添加与展示；所有暂停、停止手势统一由 `HubBallOverlay` 触发并分发。

- [ ] **Step 3: 改造会话服务状态同步**

1. `SubtitleSessionService`: 启动成功时调用 `HubBus.syncFromSession(SessionBus.Status.Running)`，销毁时同步 `Status.Stopped`；监听 `HubBus.commands` 中的显隐命令和停止命令。
2. `ScreenTextSessionService`: 启动/暂停/停止时同步 `HubBus.syncFromScreenOcr`；把 OCR 内部的暂停/继续与 Hub 命令联动。

- [ ] **Step 4: 编译并跑已有单元测试**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --console=plain > E:\AAAtranslator\build_log_hubt5.txt 2>&1"; echo "GRADLE_EXIT=$?"
```
预期：241+ 项测试全部通过。

- [ ] **Step 5: 提交任务成果**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/overlay/SubtitleOverlayController.kt app/src/main/java/com/xzm/realtimetranslate/overlay/OcrBallOverlay.kt app/src/main/java/com/xzm/realtimetranslate/service/SubtitleSessionService.kt app/src/main/java/com/xzm/realtimetranslate/service/ScreenTextSessionService.kt
git commit -m "refactor(hub): 改造同传窗口贴边直接隐藏与屏幕取词球让位接入"
```

---

### Task 6: UI 接入与 MainActivity 调度中枢集成

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/subtitle/SubtitleScreen.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/main/MainActivity.kt`

**Interfaces:**
- Consumes: `HubBus`, `FloatingHubService`, `SettingsViewModel`
- Produces:
  - `SubtitleScreen` 增加「常驻悬浮球」快捷开关卡片或顶部胶囊切换。
  - `MainActivity` 监听 `HubBus.commands`：
    - 收到 `SwitchMode(VOICE)`：自动发起音频同传流程（包含麦克风/系统录音权限检测与 MediaProjection 弹窗）；
    - 收到 `SwitchMode(FULLSCREEN)`：自动发起全屏翻译录屏授权；
    - 收到 `SwitchMode(SCREEN_REGION)`：发起选区弹层；
    - 收到 `LaunchStaticMode`：跳转到更多工具页。

- [ ] **Step 1: 在 `SubtitleScreen.kt` 增加常驻悬浮球控制行**

在「声音同传 / 屏幕取词」胶囊上方或控制面板内增加：
- `SettingSwitchRow`：文案 `常驻悬浮球`，副标题 `在所有应用上方显示便捷操作球，整合七大翻译功能`，绑定 `settings.hubEnabled`，切换时触发权限检查（`canDrawOverlays`）。

- [ ] **Step 2: 在 `MainActivity.kt` 增加 Hub 命令总线监听**

1. 在 `onCreate` / `setContent` 中启动协程监听 `HubBus.commands`；
2. 当收到模式切换命令时，若当前正处于其他互斥模式，先平滑停止旧模式，再拉起新模式授权；
3. 动态启动/停止 `FloatingHubService`：当 `settings.hubEnabled` 变为 true 且已获得悬浮窗权限时，启动 `FloatingHubService`；变为 false 时停止。

- [ ] **Step 3: 运行完整单元测试**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --console=plain > E:\AAAtranslator\build_log_hubt6.txt 2>&1"; echo "GRADLE_EXIT=$?"
```
预期：所有单测全部 PASS。

- [ ] **Step 4: 提交任务成果**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/subtitle/SubtitleScreen.kt app/src/main/java/com/xzm/realtimetranslate/ui/main/MainActivity.kt
git commit -m "feat(hub): 主界面集成常驻悬浮球开关并打通 MainActivity 多模式中枢调度"
```

---

### Task 7: 完整 Release 构建、签名校验与真机安装验收

**Files:**
- Output: `E:\AAAtranslator\LiveTranslate-release.apk`
- Modify: `docs/superpowers/specs/2026-10-24-hub-modes-design.md`

- [ ] **Step 1: 全链路打包 Release APK**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest assembleRelease --console=plain > E:\AAAtranslator\build_log_hub_release.txt 2>&1"; echo "GRADLE_EXIT=$?"
```
预期：`GRADLE_EXIT=0`。

- [ ] **Step 2: 拷贝产物至根目录并验证签名**

```bash
cp app/build/outputs/apk/release/app-release.apk LiveTranslate-release.apk
"/e/AAAtranslator/android-sdk/build-tools/37.0.0/apksigner.bat" verify --print-certs LiveTranslate-release.apk
```

- [ ] **Step 3: 推送安装至真机并启动**

```bash
"/e/AAAtranslator/android-sdk/platform-tools/adb.exe" install -r -d LiveTranslate-release.apk
"/e/AAAtranslator/android-sdk/platform-tools/adb.exe" shell am start -n com.xzm.realtimetranslate/.ui.main.MainActivity
```

- [ ] **Step 4: 走查真机验收清单**

1. 首页打开「常驻悬浮球」开关，屏幕边缘出现半球；
2. 拖拽半球上下滑动跟手，跨左右屏幕边缘自如吸附；
3. 单击半球弹出模式菜单，背景透明 Scrim 阻断下层，点击空白处菜单收起；
4. 从菜单选择「全屏翻译」，授予录屏权限，整屏出现覆盖译文，悬浮球变为运行态；
5. 点运行态悬浮球，选择「暂停」，译文暂停刷新，球内出现 ▶ 符号；再次点击恢复；
6. 点运行态悬浮球选择「停止当前模式」，覆盖层清空，悬浮球恢复为 IDLE；
7. 选择「声音同传」，同传大窗出现；拖动大窗至屏幕边缘松手，大窗消失；点悬浮球选择「显示字幕窗」，大窗重新居中恢复。

- [ ] **Step 5: 提交并完成阶段总结**

```bash
git add docs/superpowers/specs/2026-10-24-hub-modes-design.md
git commit -m "docs(hub): 完成 P2 通用悬浮球与多模式入口整合实施与验证归档"
```
