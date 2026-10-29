# 历史记录页与更多工具页 UI 视觉对齐重构计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将「历史记录页（HistoryScreen）」与「更多翻译 Hub（MoreScreen）」升级重构为与首页（方案 B 极简双核控制器）及设置页（方案 A 经典分组折叠流）完全一致的现代 Miuix 卡片流与彩色图标视觉体系，彻底消除页面间的质感断层。

**Architecture:** 
1. **组件层抽象 (`UiBits.kt`)**：沉淀统一的 `SettingRowIcon`（多彩圆角图标容器）与 `SettingDivider`（细腻卡片分割线），供设置页、历史页与更多翻译 Hub 全局共享；
2. **历史记录页重构 (`HistoryScreen.kt`)**：构建顶部概览卡片（会话总数、状态统计、清空确认）、重构历史条目卡片（彩色时间轴标签、双语字数徽章、旋转箭头展开、原文/译文独立框及一键复制、标准次级操作按钮），并升级空状态为沉浸式居中插画卡片；
3. **更多工具 Hub 重构 (`MoreScreen.kt`)**：将原先 5 个割裂离散的大卡片整合为两大逻辑分组卡片（【多媒体与文档翻译】、【图文与日常快译】），接入标准彩色图标与旋转箭头，顶部升级为带返回图标的标准导航栏。

**Tech Stack:** Kotlin、Jetpack Compose、Miuix KMP、Android Material Icons。

**Spec:** 对齐 `SubtitleScreen.kt`（首页方案 B）与 `SettingsScreen.kt`（设置页方案 A）的成熟视觉规范。

## Global Constraints

- `minSdk = 26`，`targetSdk = 35`，包名 `com.xzm.realtimetranslate`。
- 遵循应用既有主题规范与 Miuix 控件风格，不引入额外第三方 UI 依赖。
- 保持现有业务逻辑（单条删除、全部清空二次确认、导出 TXT、复制剪贴板、静态模式路由）100% 行为兼容。
- 保持现有 289 个单元测试全部通过，所有改动通过 `:app:testDebugUnitTest assembleRelease` 验证。
- Windows 构建工作流标准命令：
  ```bash
  export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest assembleRelease --console=plain > E:\AAAtranslator\build_log_ui.txt 2>&1"; echo "GRADLE_EXIT=$?"
  ```

## Review Focus

1. **历史记录展开/折叠状态保持与动画性能**：点击单条展开/折叠时，`animateContentSize()` 必须平滑自然，旋转箭头角度与展开状态精确对齐（0° ↔ 180°），不能出现列表闪烁。
2. **复制与导出操作反馈**：原文与译文单独复制 Toast 提示、单条导出 TXT 与删除操作回调必须与既有 `HistoryViewModel` 完美契合，不可丢失入参或错调。
3. **更多工具 Hub 路由与静态目标消费**：从悬浮球直达相机/图片/文档等子页时的 `initialStaticTarget` 路由逻辑与 `BackHandler` 必须丝滑可靠，物理返回键能准确逐级退出。
4. **深色模式与不同色彩对比度**：新增的彩色图标背景、标签药丸与分割线必须使用带透明度的色值，确保在明亮（Light）与深色（Dark）主题下文字均清晰可读。
5. **空状态占位与非空列表切换**：清空所有历史或初始无记录时，居中空状态卡片需完整居中展现，列表非空时不占用多余空间。

---

### Task 1: 沉淀共享基础组件 `SettingRowIcon` 与 `SettingDivider` 至 `UiBits.kt`

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/components/UiBits.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsScreen.kt`（复用公共组件，删除私有重复定义）

**Interfaces:**
- Produces in `com.xzm.realtimetranslate.ui.components`:
  - `fun SettingRowIcon(emoji: String, bg: Color, modifier: Modifier = Modifier, size: Dp = 36.dp, fontSize: TextUnit = 17.sp)`
  - `fun SettingDivider(modifier: Modifier = Modifier, horizontalPadding: Dp = 16.dp)`

- [ ] **Step 1: 在 `UiBits.kt` 中添加 `SettingRowIcon` 与 `SettingDivider`**

```kotlin
@Composable
fun SettingRowIcon(
    emoji: String,
    bg: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    fontSize: TextUnit = 17.sp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = fontSize)
    }
}

@Composable
fun SettingDivider(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.15f)),
    )
}
```

- [ ] **Step 2: 改造 `SettingsScreen.kt` 引用公共组件**

删除 `SettingsScreen.kt` 底部的私有 `private fun SettingRowIcon` 与 `private fun SettingDivider`，导入 `com.xzm.realtimetranslate.ui.components.SettingRowIcon` 和 `SettingDivider`。

- [ ] **Step 3: 运行单元测试验证无破坏**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --console=plain > E:\AAAtranslator\build_log_task1.txt 2>&1"; echo "GRADLE_EXIT=$?"`
Expected: GRADLE_EXIT=0, 289 个测试通过。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/components/UiBits.kt app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsScreen.kt
git commit -m "refactor(ui): 提取 SettingRowIcon 与 SettingDivider 基础组件至公共库"
```

---

### Task 2: 更多工具 Hub (`MoreScreen.kt`) 分组流重构

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/more/MoreScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Consumes:
  - `SettingRowIcon` & `SettingDivider` from `UiBits.kt`
  - `SectionCard` from `UiBits.kt`
  - `SmallTitle` from `top.yukonga.miuix.kmp.basic`

- [ ] **Step 1: 重构 `MoreHub` 布局结构**

将原先散落的 5 个 `SectionCard` 归并为两大逻辑分组：
1. **顶部导航栏**：
   - 带有标准的返回图标箭头（`Icons.AutoMirrored.Outlined.ArrowBack`）+ 标题 `更多翻译工具` 与副标题 `全场景离线与多模态翻译矩阵`；
2. **分组 1：【文档与音视频翻译】（大文件离线处理）**
   - 包含：
     - 📄 **文档翻译**：琥珀橙背景（`Color(0xFFF59E0B).copy(alpha = 0.12f)`），副标「PDF 逐页双语覆盖 · Word 原位段落替换」，带 `[PDF/Word]` 徽章；
     - 🎬 **视频字幕翻译**：深空紫背景（`Color(0xFF8B5CF6).copy(alpha = 0.12f)`），副标「本地音轨提取 · 逐句生成 SRT · 原画视频压制」，带 `[Media3]` 徽章；
3. **分组 2：【图文与日常快译】（实时与轻量工具）**
   - 包含：
     - 📷 **相机拍照翻译**：薄荷绿背景（`Color(0xFF10B981).copy(alpha = 0.12f)`），副标「对准文字拍照 · 自动识别覆写 · 原图保存相册」；
     - 🖼️ **图片相册翻译**：冰川蓝背景（`Color(0xFF06B6D4).copy(alpha = 0.12f)`），副标「选取长截图或照片 · 结构化 OCR 色块覆写」；
     - 📝 **文本快速翻译**：科技蓝背景（`Booth.Accent.copy(alpha = 0.12f)`），副标「双向即时输入 · 支持长文本流式翻译与一键复制」。
4. 每个条目使用统一的 `HubRowItem`，右侧替换为精致的旋转矢量箭头 `Icons.Outlined.ExpandMore`（`-90f`），条目之间插入 `SettingDivider`。

- [ ] **Step 2: 编译并运行单元测试**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --console=plain > E:\AAAtranslator\build_log_task2.txt 2>&1"; echo "GRADLE_EXIT=$?"`
Expected: GRADLE_EXIT=0.

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/more/MoreScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat(ui): 重构更多工具页为现代 Miuix 双卡片流，统一彩色图标与导航质感"
```

---

### Task 3: 历史记录页 (`HistoryScreen.kt`) 视觉全维度重构

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/history/HistoryScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Consumes:
  - `SettingRowIcon` & `SettingDivider` from `UiBits.kt`
  - `HistoryViewModel` 与 `HistoryEntry`
  - `MiuixTheme.colorScheme` 与 `Booth.Accent`

- [ ] **Step 1: 设计并实现顶部概览统计卡片 (`HistorySummaryCard`)**

在 `PageTitle` 下方增加精致的概览卡片：
- 左侧：`SettingRowIcon(emoji = "📜", bg = Booth.Accent.copy(alpha = 0.12f))`，文字显示 `已归档 X 场会话`（粗体 16sp），副标题显示 `精确时间戳 · 支持长文本导出 TXT`（12sp）；
- 右侧：当列表非空时，展示红橙警示色轻量药丸按钮 `🗑️ 清空全部`，点击弹出二次确认对话框。

- [ ] **Step 2: 设计并实现沉浸式空状态卡片 (`HistoryEmptyState`)**

当 `entries.isEmpty()` 时呈现：
- 顶部 72dp 圆角柔和彩色圆盘，内嵌 36sp 的 `📜` 或 `📂`；
- 标题 `暂无会话历史`（17sp 粗体）；
- 副标 `开启声音同传、屏幕取词或文档翻译后，双语文本将自动在此归档保存`（13sp，居中换行，柔和色调）；
- 整体置于全屏自适应卡片中，视觉饱满从容。

- [ ] **Step 3: 彻底重构历史记录项卡片 (`HistoryEntryCard`)**

1. **卡片 Header**：
   - 左侧展示柔和的彩色时间标签徽章（如 `10月28日 21:30`，采用 `DateTimeFormatter.ofPattern("M月d日 HH:mm")` 提升可读性）；
   - 中间展示双语字数徽章（`原文 X 字 · 译文 Y 字`，带有淡灰/微蓝的小药丸背景）；
   - 右侧展示带有动画旋转角度（`0f ↔ 180f`）的矢量箭头 `Icons.Outlined.ExpandMore`。
2. **折叠态预览**：
   - 处于折叠状态时，展示格式化的单行优雅省略预览，并加上 `译：...` 或 `原：...` 微缩提示，扫视一眼即可辨别会话内容。
3. **展开态双语对照排版**：
   - **原文面板**：带 `原文` 浅灰标签 + 右侧 `📋 复制` 小图标按钮，内嵌于圆角背景中；
   - **译文面板**：带 `译文 (中文)` 蓝色标签 + 右侧 `📋 复制` 小图标按钮，排版字号 14.5sp；
   - **底部动作工具条**：
     - `💾 保存为 TXT`：Miuix 次级胶囊按钮（带微蓝色调）；
     - `🗑️ 删除`：危险色轻量按钮；
     - 按钮紧凑排布在右侧，整洁有序。

- [ ] **Step 4: 运行单元测试与本地构建**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --console=plain > E:\AAAtranslator\build_log_task3.txt 2>&1"; echo "GRADLE_EXIT=$?"`
Expected: GRADLE_EXIT=0.

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/history/HistoryScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat(ui): 历史记录页全维度重构，新增统计概览、沉浸空状态与高质感双语卡片"
```

---

### Task 4: 全量构建、产出 Release APK 并自测验证

**Files:**
- Output: `E:\AAAtranslator\LiveTranslate-release.apk`
- Test: 全量 289 个单元测试与编译检查

- [ ] **Step 1: 运行全量单测与 Release 构建**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest assembleRelease --console=plain > E:\AAAtranslator\build_log_final_ui.txt 2>&1"; echo "GRADLE_EXIT=$?"`
Expected: GRADLE_EXIT=0, 289 个测试通过，APK 生成在 `app/build/outputs/apk/release/app-release.apk`。

- [ ] **Step 2: 覆盖更新根目录 Release APK 交付产物**

```bash
cp E:/AAAtranslator/app/build/outputs/apk/release/app-release.apk E:/AAAtranslator/LiveTranslate-release.apk
```

- [ ] **Step 3: 验证 APK 签名与 badging**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && /e/AAAtranslator/android-sdk/build-tools/37.0.0/aapt2.exe dump badging E:/AAAtranslator/LiveTranslate-release.apk | grep -E "package|versionCode|versionName"
```

- [ ] **Step 4: 记忆更新与工作区核验**

更新 `MEMORY.md` 与 `project-livetranslate-state.md`，记录本次历史页与更多工具页的 UI 重构。
