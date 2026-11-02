# UI、动效与交互体验全方位打磨实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 全面对齐并升级 LiveTranslate 的悬浮层（取词气泡、OCR二级菜单、同传操作栏、穿透解锁胶囊）与前台主界面（首页4宫格色彩微光底座、打赏弹窗交叉平滑淡入淡出）的视觉质感与微动效体验。

**Architecture:** 
1. 悬浮窗/悬浮球层：在 `OcrBallOverlay.kt` 中全面重构长按菜单与气泡渲染，引入带有 `RippleDrawable` 水波纹、微光彩色图标底座与进退场缩放动画；在 `SubtitleOverlayController.kt` 中将快捷操作栏升级为微章胶囊质感与平滑位移动效，穿透解锁胶囊升级为呼吸微光；
2. Compose 界面层：在 `SubtitleScreen.kt` 与 `SettingsScreen.kt` 中优化 4 宫格工具托盘的色彩底座、赞赏弹窗五档二维码平滑 `Crossfade` 切换与交互微触感。

**Tech Stack:** Kotlin, Android View / WindowManager Animation, ValueAnimator, RippleDrawable, GradientDrawable, Jetpack Compose, MIUIX Theme.

**Spec:** 本计划基于用户明确指示的“直接全面打磨优化”需求，对齐全 App 既有的高级深色磨砂晶体（MIUIX / Apple 风格）设计规范。

## Global Constraints
- Android SDK 版本兼容：保证 `minSdk = 26`、`targetSdk = 35`，使用高版本 API（如 `VibratorManager`）时必须具备向后优雅降级；
- 构建与单测守护：改动完成后必须通过 `./gradlew testDebugUnitTest`（原有 313 项单测 100% 保持全绿）；
- 性能与内存红线：悬浮窗口渲染不得在主线程分配过重位图或产生额外 Binder IPC 往返。

## Review Focus
1. `OcrBallOverlay` 菜单在靠近屏幕边缘时展开的坐标计算与尺寸自适应（防止菜单弹出被截断）；
2. 气泡关闭时动画若是异步执行，需确保关闭动画结束时才移除窗口，避免重复点击产生空指针或残存 View；
3. `SubtitleOverlayController` 快捷操作栏快速连续点击切换模式时，文本与图标必须即时响应；
4. `RewardDialog` 快速切换五档金额时，二维码图片不得出现内存泄漏或渲染闪退；
5. 4 宫格离线工具在深色/浅色模式下的色彩对比度与可读性。

---

### Task 1: OcrBallOverlay 长按二级菜单全面重构（对齐 MIUIX 磨砂晶体与进场动效）

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/overlay/OcrBallOverlay.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/overlay/OcrBallOverlayTest.kt` (或相关单测)

**Interfaces:**
- Produces: `OcrBallOverlay.showMenu()` 弹出带有微光图标底座、分组分割线、水波纹反馈与平滑缩放淡入（0.88f -> 1.0f, DecelerateInterpolator）的高级卡片。

- [ ] **Step 1: 检查并设计菜单项结构与资源映射**
在 `OcrBallOverlay.kt` 的 `showMenu()` 中：
重构原先的 `addItem(label)` 为结构化菜单：
- 暂停/恢复：琥珀橙微光底座（`0x26F59E0B`），高光图标 `▶` / `⏸`，辅助副标题“暂停或恢复取词识别”；
- 重新框选区域：科技蓝微光底座（`0x263482FF`），高光图标 `⛶`，辅助副标题“画框选取新翻译区域”；
- 显隐取词红框：灵动紫微光底座（`0x268B5CF6`），高光图标 `👁` / `🙈`，辅助副标题“切换选区红色外框”；
- 结束取词会话：柔和警示红底座（`0x26EF4444`），高光图标 `⏹`，辅助副标题“停止识别并保存记录”。

- [ ] **Step 2: 实现卡片容器的晶体质感与水波纹按压交互**
卡片容器采用 20dp 大圆角、深色半透磨砂背景（`0xE61C1D24`）、精细微发光边框（`0x26FFFFFF`）与软阴影；
每个条目使用 `RippleDrawable` 提供波纹与透明度按压反馈。

- [ ] **Step 3: 增加菜单弹出时的平滑弹性缩放动画**
在 `menuWindowView` 添加到 WindowManager 后，对 `card` 视图应用：
`card.alpha = 0f; card.scaleX = 0.88f; card.scaleY = 0.88f`
通过 `card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).setInterpolator(DecelerateInterpolator()).start()` 实现灵动丝滑入场。

- [ ] **Step 4: 编译并验证**
运行 `./gradlew :app:compileDebugKotlin` 确保语法与编译 0 错误。

---

### Task 2: 屏幕取词悬浮气泡（Popup Bubble）视觉与微动效质感打磨

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/overlay/OcrBallOverlay.kt`

**Interfaces:**
- Produces: `OcrBallOverlay.ensurePopup()` / `showPopup()` / `closePopup()` 实现精致微光晶体卡片外框、圆形图标按钮触控反馈与平滑进出场动效。

- [ ] **Step 1: 升级气泡卡片视觉质感**
在 `ensurePopup()` 中：
- `card` 背景升级为 16dp 圆角深色晶体（`0xF014141C`），增加 `0.8dp` 的微透白色高光边框（`0x26FFFFFF`）；
- 拖拽手柄小圆点（`dragHandle`）增加微光渐变，尺寸与外边距更加协调。

- [ ] **Step 2: 升级复制按钮与关闭按钮的交互触感**
- 复制按钮 📋：使用尺寸为 `26dp` 的圆形半透底座（`0x1FFFFFFF`），内嵌 `RippleDrawable`，点击触发轻微触感振动并 Toast 复制；
- 关闭按钮 ✕：使用对称的 `26dp` 圆形微红底座（`0x1AEF4444`）与高亮浅红字形，带有水波纹触控反馈。

- [ ] **Step 3: 升级气泡出现与关闭的平滑动画**
- `showPopup()`：取消原本单一的透明度渐变，改为 `view.alpha = 0f; view.scaleX = 0.92f; view.scaleY = 0.92f`，动画至 `alpha = 1f; scaleX = 1f; scaleY = 1f`（时长 180ms，`DecelerateInterpolator`）；
- `closePopup()`：先执行缩小淡出动画 `view.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(140L).withEndAction { windowManager.removeView(view) }`，彻底根除点击 ✕ 时硬闪退的割裂感。

- [ ] **Step 4: 编译并验证**
运行 `./gradlew :app:compileDebugKotlin` 验证通过。

---

### Task 3: 声音同传悬浮窗操作栏与穿透解锁胶囊质感升级

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/overlay/SubtitleOverlayController.kt`

**Interfaces:**
- Produces: `SubtitleOverlayController.buildActionBar()` 拥有水波纹反馈、微章胶囊图标与平滑展开动画；`showUnlockPill()` 拥有高质感微光水滴悬浮质感。

- [ ] **Step 1: 升级快捷操作栏按钮质感**
在 `buildActionBar()` 中：
- 将操作按钮升级为圆角矩形（10dp 圆角），使用深色半透底（`0x33FFFFFF`）与柔和白色高光边框（`0x1AFFFFFF`）；
- 按钮背景包装为 `RippleDrawable`，提供细腻点击波纹；
- 各按钮前缀增加精致微章色相（复制 📋 科技蓝微底、模式 🔄 灵动紫微底、穿透 🔒 琥珀金微底、关闭 ✕ 珊瑚红微底）。

- [ ] **Step 2: 优化操作栏呼出动效**
在 `showActionBar()` / `hideActionBar()` 中：
- 展开时加入垂直位移动画：从 `translationY = -8dp` 平滑过渡至 `0dp` 同时透明度由 `0f -> 1f`；
- 收起时反向退出，视觉表现更加轻盈。

- [ ] **Step 3: 升级穿透锁定微型胶囊（UnlockPill）**
在 `showUnlockPill()` 中：
- 胶囊背景增加两层渐变与外发光环（`0x333482FF` 外光晕 + `0xE62E7CF6` 主体）；
- 弹出时增加弹性放大缩放（`0.7f -> 1.0f`，`OvershootInterpolator`）；
- 按住时提供即时缩放反馈（`scaleX/Y = 0.92f`），长按成功解锁触发清脆震动反馈。

- [ ] **Step 4: 编译并验证**
运行 `./gradlew :app:compileDebugKotlin` 验证通过。

---

### Task 4: 首页 4 宫格工具托盘色彩微光底座与交互微触感打磨

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/subtitle/SubtitleScreen.kt`

**Interfaces:**
- Produces: `QuickToolsTray` 与 `QuickToolItem` 升级为具备独立品牌色相的彩色微光渐变底座与细腻微边框。

- [ ] **Step 1: 重构 QuickToolItem 颜色定义**
为 4 个离线静态工具赋予专属视觉标识：
- 文档翻译：琥珀金微光底座（`0xFFF59E0B`），高光浅金边框；
- 图片翻译：薄荷翡翠绿微光底座（`0xFF10B981`），清新微绿边框；
- 视频翻译：灵动深空紫微光底座（`0xFF8B5CF6`），幽雅微紫边框；
- 文本翻译：科技清泉蓝微光底座（`0xFF3B82F6`），沉稳微蓝边框。

- [ ] **Step 2: 升级图标容器与卡片按压反馈**
- 图标容器 Box 使用 `14dp` 圆角，背景使用对应主题色 `alpha = 0.14f` 的柔和色调，并添加 `0.8dp` 的匹配细微边框；
- 卡片增加微小点击弹性反馈（`Modifier.bounceClick()` 或平滑 scale 状态反馈）。

- [ ] **Step 3: 升级大发射按钮与翻译领域胶囊微光质感**
- 声音同传与取词的大发射按钮增加 Subtle Shadow / Elevation，强化视觉中心地位；
- 翻译领域胶囊在选中状态下增加微光描边（`Booth.Accent.copy(alpha = 0.4f)`），选中更醒目清晰。

- [ ] **Step 4: 编译并验证**
运行 `./gradlew :app:compileDebugKotlin` 验证通过。

---

### Task 5: 微信赞赏弹窗五档二维码平滑淡入淡出（Crossfade）优化

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Produces: `RewardDialog` 切换 0.5元 / 1元 / 6.6元 / 9.9元 / 自定义 五档二维码时，通过 `Crossfade` 平滑交叉渐变过渡，消除图片突兀刷新感。

- [ ] **Step 1: 引入 Crossfade 包装二维码图片展示容器**
在 `RewardDialog` 的图片展示区域，将硬编码的 `Image(painterResource(tiers[selected].res))` 替换为：
```kotlin
Crossfade(
    targetState = tiers[selected].res,
    animationSpec = tween(180),
    label = "RewardQrcodeCrossfade",
) { resId ->
    Image(
        painter = painterResource(resId),
        contentDescription = "赞赏二维码",
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
    )
}
```

- [ ] **Step 2: 档位选择胶囊动画增强**
档位切换时的选中背景色与字体粗细增加 `animateColorAsState`，平滑过渡。

- [ ] **Step 3: 编译并验证**
运行 `./gradlew :app:compileDebugKotlin` 验证通过。

---

### Task 6: 全链路自动化测试与最终 APK 打包验证

**Files:**
- Run: `./gradlew testDebugUnitTest`
- Run: `./gradlew assembleRelease`
- Output: `LiveTranslate-release.apk`

- [ ] **Step 1: 跑全套单元测试**
运行：`./gradlew testDebugUnitTest`
断言：313 个单元测试全部 PASS，0 失败、0 错误、0 跳过。

- [ ] **Step 2: 构建全新 Release 交付 APK**
运行：`./gradlew assembleRelease`
将生成的 `app/build/outputs/apk/release/app-release.apk` 拷贝覆盖至根目录 `LiveTranslate-release.apk`。

- [ ] **Step 3: Git 提交并推送（或就绪待确认）**
查看 `git status` 与 `git diff`，记录改动并做干净的代码提交。
