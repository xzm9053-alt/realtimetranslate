# 通用悬浮球与七种翻译模式 —— 设计文档

日期：2026-10-24
状态：待审阅

## 1. 要做什么（意图）

把现在分散在两个入口里的能力，收进**一颗常驻悬浮球**，并补齐三种「译文就地覆盖原文」的屏幕翻译，以及三种「拍/选/导入后处理」的静态翻译。

首页有一个总开关：打开后，悬浮球常驻在所有 App 之上（不需要任何录制授权）；关闭后球与所有会话一起回收。

点球弹出模式菜单：

| # | 模式 | 形态 | 是否实时 | 输出 |
|---|------|------|----------|------|
| 1 | 语音翻译 | 系统/麦克风音频 → 字幕大窗 | 实时 | 悬浮字幕窗 |
| 2 | 屏幕取词 | 框选区域 → 译文气泡 | 实时 | 球旁气泡（沿用现状） |
| 3 | 漫画翻译 | 整屏识别（漫画调优）→ 纯色块覆盖原文 | 实时 | 屏幕覆盖层 |
| 4 | 全屏翻译 | 整屏识别（通用）→ 纯色块覆盖原文 | 实时 | 屏幕覆盖层 |
| 5 | 相机翻译 | 拍完照片 → 覆盖译文 | 静态 | 可保存到相册 |
| 6 | 图片翻译 | 相册选图 → 覆盖译文 | 静态 | 可保存到相册 |
| 7 | 文档翻译 | PDF / Word → 覆盖译文 | 静态 | 保存到 Downloads |

选中实时模式后，球进入「运行中」状态；再点球 = 退出当前模式 + 该模式的快捷设置。四种实时模式互斥。

语音模式的字幕大窗保留。原来的「拖到屏幕边缘缩成小球」改为**直接隐藏**（球本来就在），从球菜单「显示字幕窗」叫回来。

## 2. 为什么这么做

- 用户明确要求：球是唯一入口，七种能力从球里选；「原文去掉」= 译文直接盖住原文。
- 现有 OCR 只返回整段文字，没有坐标；覆盖原文必须先有**行级外框**，这是三种覆盖模式共同的底座。
- 静态三种（相机/图片/文档）与实时三种共享同一套「识别 → 规划 → 翻译 → 覆盖渲染」管线，只是输入源和输出目标不同。

## 3. 目标 / 非目标

**目标**
- 七种模式都能从球进入；实时模式可随时停下、切模式。
- 覆盖模式看起来像「原文被替换」：纯色块取原文背景色，译文写在原位。
- 相同文字不重复请求翻译（缓存 + 变化才翻）。
- 全流程离线识别（ML Kit 内置模型），翻译仍走现有 6 个引擎。

**非目标**
- 不做实时相机取景框（AR 式）覆盖：相机只处理**拍完的照片**。
- 不保证 Word 的文本框/艺术字/SmartArt 被替换；只处理正文段落与表格单元格文字。
- 不重绘漫画气泡边框，不翻译画面里的非文字内容。
- 不做本地离线翻译模型。
- 不改变现有屏幕取词的交互（框选 + 气泡）。

**反目标（做得再好也算失败）**
- 覆盖层挡住用户操作（必须触摸穿透）。
- 未获录屏授权时截屏。
- 同一段文字每隔 1 秒重复送翻译，把用户额度烧掉。
- 覆盖块位置偏移，盖到原文旁边。

## 4. 约束

1. **盖住原文只能靠悬浮窗**：Android 不允许修改别人的画面，所有覆盖都是 `TYPE_APPLICATION_OVERLAY` 窗口，必须触摸穿透，否则下层 App 无法操作。
2. **录屏授权一次一路**，每次新建 `MediaProjection` 系统都会弹授权框；同一次录屏内可切换区域/漫画/全屏，不重新授权。
3. **坐标恒等映射**：现有 `ScreenTextCapturer` 以真实分辨率 1:1 建虚拟屏，选区 Rect 与屏幕像素一致；覆盖层也按屏幕像素布局。
4. **OCR 预处理会缩放**：`ScreenTextOcr.preprocess()` 会把图放大（宽 ≥1200 或 2×），ML Kit 返回的坐标必须按该比例**除回去**，否则覆盖块整体偏移。
5. **MIUI 限制**：`adb shell input` 被禁、新包安装被拦；真机验收只能由用户手动点。
6. **APK 已 475MB**，不再引入大体积依赖；PDF 用系统 `PdfRenderer`/`PdfDocument`，Word 不引入渲染库。
7. **FileProvider 目前只暴露 `cache/diagnostics/`**（`res/xml/file_paths.xml`）。相机 `TakePicture` 的临时文件需要新增一条 `cache/shared/` 路径；不要把 `filesDir` 整目录暴露出去。
8. **OCR 缩放的实际情况**：`upscale()` 只在图小于 1200×120 时放大（≤2×），大图原样返回；`enhance()` 不改变尺寸。所以坐标回算只需处理「是否放大」这一种情况，比例取 `prepared.width / bitmap.width` 即可。

## 5. 架构

### 5.1 总览

```
FloatingHubService (前台服务，常驻)
 ├── HubBallOverlay        球 + 模式菜单 + 快捷设置面板
 └── 模式状态机 HubState { Off, Idle, Voice, ScreenRegion, Comic, Fullscreen }
      ├── Voice        → SubtitleSessionService（现有）
      ├── ScreenRegion → ScreenTextSessionService(mode=REGION)（现有，球改由 hub 提供）
      ├── Comic        → ScreenTextSessionService(mode=COMIC)
      └── Fullscreen   → ScreenTextSessionService(mode=FULLSCREEN)
静态模式（相机/图片/文档）→ 打开 MainActivity 对应页面，球不退出
```

### 5.2 结构化 OCR（新底座）

新增 `ocr/OcrModels.kt`：

```kotlin
data class OcrLine(val text: String, val rect: Rect)
data class OcrBlock(val rect: Rect, val lines: List<OcrLine>) { val text: String }
data class OcrPage(val width: Int, val height: Int, val blocks: List<OcrBlock>)
```

`ScreenTextOcr` 新增 `recognizePage(bitmap, script): OcrPage?`：
- `preprocess()` 改为同时返回**缩放系数**（sx, sy）。
- 逐 `TextBlock → Line` 取 `boundingBox`，除以 sx/sy 换回源图坐标。
- `AUTO` 并行跑 Latin + Japanese，沿用现有 `pickBest` 的启发式，返回胜出的那一份结构化结果。
- 旧 `recognize()` 用 `recognizePage()` 重新实现（拼成阅读顺序文本），现有气泡逻辑零改动。

新增 `ocr/BackgroundColorSampler.kt`：给定位图与行框，采样框外一圈像素（去掉文字色），取量化后的众数颜色作为覆盖块底色；采样失败回退白色。

### 5.3 翻译规划（纯逻辑）

新增 `ocr/TranslationPlanner.kt`：

```kotlin
data class TextTarget(val key: String, val text: String, val rect: Rect)
fun plan(page: OcrPage, mode: OcrTranslateMode, targetLang: String, previous: Set<String>): List<TextTarget>
```

规则：
- **去重**：同一页重复文本只翻一次（`key = 规范化文本`）。
- **跳过已译**：目标语言是中文且文本基本是中文 → 不翻（避免 UI 中文被再翻一遍）。
- **跳过噪点**：<2 个字符且无标点 → 不翻。
- **漫画模式额外**：同 `TextBlock` 内多行合并成一句（CJK 不加空格、西文加空格）；竖排（行高 > 行宽、同 x）按列从右到左合并；疑似拟声词（短、无标点、字高 > 中位数 1.5 倍）跳过。
- **全屏模式**：按 block 合并，但保留块内换行数，避免整屏变成一段。

新增 `translate/TranslationCache.kt`：LRU（上限 300 条，字符串键），会话级；命中直接出结果，不计费。

### 5.4 覆盖渲染

新增 `overlay/CoverOverlayController.kt` + `overlay/CoverView.kt`：
- 全屏触摸穿透窗口（`FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`），`TYPE_APPLICATION_OVERLAY`。
- 每个 `TextTarget` 画：圆角纯色块（采样底色）→ 译文（自动缩字号至放得下、按框宽换行、超出框高省略）。
- 双语选项：原文小一号偏暗在上，译文在下（与视频字幕同一套视觉规则）。
- 屏幕映射：`screenRect = pageRect.offset(region.left, region.top)`；全屏模式 region = 整屏。
- 状态栏/导航栏区域的目标丢弃（按 `WindowInsets` 高度裁剪）。
- 旋转/尺寸变化：重新贴边并清空上一帧覆盖。
- 增量刷新：只重绘文本变化的目标，避免整屏闪烁。

### 5.5 屏幕会话服务改造

`ScreenTextSessionService` 增加 `mode: ScreenOcrMode { REGION, COMIC, FULLSCREEN }`：
- 启动 Intent 增加 `EXTRA_MODE`；缺省 `REGION`，旧调用路径行为不变。
- `REGION`：捕获 `currentRegion`，输出到气泡（现状）。
- `COMIC/FULLSCREEN`：捕获整屏（`Rect(0,0,w,h)`），输出到 `CoverOverlayController`。
- 采集循环仍是「抓帧 → 哈希去重 → OCR → 交给翻译 worker」；覆盖模式的 worker 按目标逐块翻译（并发 2、缓存命中先画），带世代号丢弃过期结果。
- 刷新间隔 `COVER_FRAME_INTERVAL_MS = 1200`。
- 切模式：同一投影内改 `mode`（无需重新授权）；跨语音/屏幕仍需停旧起新。
- REGION 模式下球由 hub 提供：`OcrBallOverlay` 拆出「气泡 + 选区外框 + 拖动圆点」为 `ScreenRegionPresenter`，移除自带球；保留暂停/继续/重选/停止回调，接到 hub 菜单。

### 5.6 通用悬浮球

新增 `service/FloatingHubService`（`FOREGROUND_SERVICE_SPECIAL_USE`）+ `overlay/HubBallOverlay`：
- 球沿用 `BallView`（真半球、贴边、可拖、跨边）。
- 点击球：
  - `Idle`：模式菜单（七项 + 关闭悬浮球）。
  - `Voice`：显示/隐藏字幕窗、暂停/继续、语音设置、退出语音翻译。
  - `ScreenRegion/Comic/Fullscreen`：暂停/继续、本模式设置（目标语言、仅译文/双语、字号、块颜色）、退出当前模式、切换模式。
- 菜单用独立窗口 + 全屏透明 scrim，点外部关闭（复用现有 `OcrBallOverlay.showMenu` 结构）。
- 静态三项：按 `Intent(ACTION_MAIN, component=MainActivity, extra open=…)` 拉起 App 对应页面。

新增 `service/HubBus.kt`（`StateFlow<HubState>` + 命令流），供首页、球、服务共享；`ScreenOcrBus` 保留兼容并逐步并入。

### 5.7 语音模式改动

- `SubtitleOverlayController.fold()/expand()/BallTouchListener` 停用：拖到边缘松手改为 `hide()`（保留会话与字幕缓冲）。
- 新增 `showWindow()` 供球菜单「显示字幕窗」调用。
- `BallView` 保留给 hub 使用。

### 5.8 静态管线

新增 `image/ImageTranslationPipeline.kt`（Bitmap → OcrPage → 翻译 → 覆盖渲染 → Bitmap），三种静态模式共用。

- **相机**：`ActivityResultContracts.TakePicture`（系统相机 + FileProvider 临时文件）。不引入 CameraX。
- **图片**：`PickVisualMedia`（系统相册选择器，无需存储权限）。
- **PDF**：`PdfRenderer` 逐页渲染（2× 缩放）→ 管线 → 覆盖后的位图；用 `PdfDocument` 生成新 PDF（每页一张图）。
- **Word(docx)**：解压 docx，`XmlPullParser` 读 `word/document.xml` 的 `<w:p>` 与 `<w:t>`；以段落为单位翻译，把译文写回该段第一个 run、其余 run 清空；其余 zip 条目原样复制，输出新 docx。表格单元格文字同样覆盖；页眉页脚/文本框不在本轮范围。
- 输出位置：相机/图片 → MediaStore `Pictures/实时翻译`；PDF/docx → MediaStore `Downloads/实时翻译`。
- 结果页：原文/译文切换预览、保存、分享、复制全文。

### 5.9 设置与状态

新增 DataStore 键：
- `hub_enabled`（Boolean，默认 false）
- `cover_bilingual`（Boolean，默认 false）
- `cover_font_scale`（Float，默认 1.0f，0.8–1.6）
- `cover_block_style`（String，`AUTO` / `DARK`，默认 `AUTO`）

沿用：`targetLanguageCode`、`sourceLanguageCode`、`ocrScript`、翻译引擎。

## 6. 生命周期与互斥

- 总开关打开 → 起 `FloatingHubService`（前台通知「悬浮球已开启」）；关闭 → 停所有会话 + 收球。
- 四种实时模式互斥：切模式先停旧会话；同一录屏内 REGION/COMIC/FULLSCREEN 直接改 mode。
- 语音 ↔ 屏幕：停旧起新（沿用现有互斥逻辑）。
- 进程被杀：hub 服务 `START_STICKY` 重启（若开关仍开）；录屏会话不自动恢复（系统不允许无授权恢复）。
- 屏幕旋转：沿用现有选区映射逻辑；覆盖层重建并清空。

## 7. 风险与对策

| 风险 | 对策 |
|---|---|
| 覆盖块整体偏移 | 预处理缩放系数必须回除；加单测（合成图 + 已知比例）；真机用日志核对 |
| 整屏翻译延迟大 | 只翻变化块 + 缓存 + 并发 2；先画命中缓存的，再补新块 |
| 覆盖层挡住操作 | 所有覆盖窗口 `FLAG_NOT_TOUCHABLE`；球单独窗口 |
| 翻译费用失控 | LRU 缓存、页面级去重、目标语言跳过 |
| MIUI 后台/悬浮窗限制 | 前台服务 + 已有悬浮窗权限；不新增特殊权限 |
| Word 版式丢失 | 只承诺段落/表格文字替换，其余原样保留；结果页明确提示 |
| 投影切换模式要重新授权 | 同一服务内改 mode，不重建 MediaProjection |

## 8. 验证

- 单测（新增，纯逻辑）：坐标回算、规划/去重/跳过、漫画合并与竖排、缓存 LRU、覆盖文本排版、docx 文本替换。
- 现有 204 条单测必须保持全绿。
- 每个阶段 `assembleRelease` + 装真机；真机手动验收（MIUI 禁注入）。
- 关键真机判据：
  1. 总开关 → 球常驻；点球出七项菜单；关开关球消失。
  2. 全屏翻译：打开任意英文 App，译文盖住原文、位置对得上、能正常操作下层。
  3. 漫画：日文竖排页，译文按气泡合并、拟声词不翻、覆盖不越界。
  4. 语音：拖字幕窗到边缘 → 窗口消失但翻译继续；球菜单叫回。
  5. 相机/图片：拍/选一张外文图 → 结果覆盖正确 → 保存到相册。
  6. 文档：PDF 逐页覆盖、导出可打开；docx 文字被替换、版式基本保留。

## 9. 分阶段计划

| 阶段 | 内容 | 交付 |
|---|---|---|
| P0 | 结构化 OCR + 坐标回算 + 规划器 + 缓存 + 单测 | 不含 UI，测试全绿 |
| P1 | 覆盖窗口 + `ScreenTextSessionService` 全屏模式 | 全屏翻译可用 |
| P2 | `FloatingHubService` + 球菜单 + 语音窗贴边隐藏 + 现有两模式接入 | 入口统一 |
| P3 | 漫画调优（气泡合并、竖排、拟声词） | 漫画翻译可用 |
| P4 | 相机 + 图片（含保存） | 静态两种可用 |
| P5 | PDF + Word | 文档翻译可用 |

每个阶段独立可构建、可回滚；P0–P5 完成后版本号升到 v0.4.0。

## 10. 审阅后确定的事项

独立审阅提出了五个会改变计划的问题，处理如下：

1. **总开关关闭时旧入口还在**：首页保留现有的「开始同传 / 屏幕取词」入口，球是**额外**入口而不是唯一入口。总开关关着时一切照旧；打开后，旧模式的悬浮球/折叠球让位给 hub（`OcrBallOverlay` 不再显示自己的球，语音窗拖边隐藏）。旧入口启动会话时同步 `HubBus` 状态，球显示为对应模式的运行态。
2. **录屏授权由屏幕会话服务持有**：`FloatingHubService` 不持有投影。REGION / COMIC / FULLSCREEN 在同一次会话内改 mode，复用投影；一旦离开屏幕模式（回 Idle / 去语音 / 用户退出），释放投影，下次进入重新弹系统授权框。语音与屏幕互斥，切换本来就要重新授权。
3. **竖排日文不做竖排排版**：规划器把同一竖排块按列从右到左合并成一整段，覆盖时用该块所有列的**联合外框**横排渲染译文（中文横排更好读）。这样 `CoverView` 只需一套横排排版引擎，P3 规模可控。
4. **静态模式独占前台**：从球里点相机/图片/文档时，先停掉正在运行的实时会话，球折叠为静止状态，然后拉起 MainActivity 对应页面。避免覆盖层压在本 App 自己的界面上。
5. **旋转沿用现有做法**：同一投影内 `ScreenTextCapturer.resize()` + `remapRegion()`，不重新授权；覆盖层按新尺寸重建并清空。现有实现已在屏幕上验证过「不结束会话」。
6. **触摸穿透是待验证假设（风险最高）**：Android 12+ 对「穿过其他 App 悬浮窗的触摸」有不可信触摸限制。P1 的第一件事就是在真机验证：全屏 `FLAG_NOT_TOUCHABLE` 覆盖层下方 App 能否正常点击。若被拦截，退化为「按块开小窗口」：每个文字块一个独立的、不重叠的覆盖窗口，窗口仍不接收触摸；若连小窗口也拦，则该模式只能做「译文气泡」而不是覆盖（需回头找用户重新定方案）。

## 11. 留给实现者的决定

- 覆盖块圆角、内边距、行距的具体数值。
- 菜单面板的视觉细节（列表 vs 环形），沿用现有菜单窗口风格即可。
- 翻译并发数与缓存上限的具体取值。
- 静态结果页的排版细节。
- 竖排日文的合并阈值（先用启发式，真机再调）。
