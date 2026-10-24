# 全屏覆盖翻译（P0 + P1）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 App 具备「整屏识别 → 译文纯色块就地覆盖原文」的能力，并可从首页直接启动；这是后续漫画、相机、图片、文档四种覆盖模式的共同底座。

**Architecture:** OCR 从「一段字符串」升级为「带行级外框的结构化页面」；新增纯逻辑的翻译规划器与 LRU 缓存；新增触摸穿透的全屏覆盖窗口；`ScreenTextSessionService` 增加 `FULLSCREEN` 模式复用现有录屏、旋转与生命周期。通用悬浮球（P2）不在本计划内，本计划用首页按钮作为临时入口。

**Tech Stack:** Kotlin、Android View（覆盖层用原生 Canvas，不引入 Compose 到 WindowManager）、ML Kit bundled Text Recognition、现有 `TranslationEngine`、JUnit4。

**Spec:** `docs/superpowers/specs/2026-10-24-hub-modes-design.md`

## Global Constraints

- `minSdk = 29`，`targetSdk = 35`，包名 `com.xzm.realtimetranslate`。
- 不引入新的大体积依赖；覆盖层用原生 View/Canvas。
- 覆盖窗口必须 `FLAG_NOT_TOUCHABLE`，任何情况下不能吞掉下层 App 的触摸。
- 屏幕坐标恒等映射：`ScreenTextCapturer` 采集分辨率为真实屏幕 1:1；只有 OCR 预处理放大过才需要按比例回除。
- 同一时刻只有一路 `MediaProjection`；本计划内只有屏幕会话持有。
- 覆盖刷新间隔 `COVER_FRAME_INTERVAL_MS = 1200L`；翻译缓存容量 300；单页翻译并发 2。
- 现有 204 条单测必须保持全绿。
- 提交信息用中文；**不 push**，除非用户明确要求。
- 构建用 Bash 工具 + `cmd //c`（PowerShell 5.1 会吞 Kotlin 错误），`JAVA_HOME=/e/AAAtranslator/jdk`：
  ```bash
  export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest assembleRelease --console=plain > E:\AAAtranslator\build_log_coverN.txt 2>&1"; echo "GRADLE_EXIT=$?"
  ```
  验证签名：`"/e/AAAtranslator/android-sdk/build-tools/37.0.0/apksigner.bat" verify --print-certs <apk>`；装真机：`"/e/AAAtranslator/android-sdk/platform-tools/adb.exe" install -r -d <apk>`。

## Review Focus

这些是规格隐含、但单测覆盖不到、最容易让用户失望的输入，逐条列在这里，并在对应任务里钉住：

1. **Android 12+ 的不可信触摸限制**：全屏 `FLAG_NOT_TOUCHABLE` 覆盖层下方 App 能否正常点击。Task 9 真机第一条验的就是它；若被拦，方案要退化为按块小窗口。
2. **旋转中覆盖层错位**：转屏后旧覆盖块必须清空、按新尺寸重建，绝不能把上一方向的译文留在屏幕上（Task 7）。
3. **译文比原文长很多**：小框里放不下时缩字号、再放不下省略，绝不能溢出盖到相邻行（Task 6 的 `fit` 用例钉住）。
4. **整屏没有可翻译文字 / 全是目标语言**：必须零请求、零覆盖，而不是把原文再翻一遍（Task 4）。
5. **单块翻译失败**：该块保持原文（不画覆盖），其余块照常出译文；不能整页消失，也不能显示「翻译失败」盖住原文（Task 7）。

---

### Task 1: OCR 结构化模型与坐标回算

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/ocr/OcrModels.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/ocr/OcrGeometryTest.kt`

**Interfaces:**
- Consumes: 无。
- Produces:
  - `data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int)`，属性 `width`、`height`，方法 `offset(dx: Int, dy: Int): PixelRect`、`scaleToSource(sx: Float, sy: Float): PixelRect`、`union(other: PixelRect): PixelRect`、`intersects(other: PixelRect): Boolean`
  - `data class OcrLine(val text: String, val rect: PixelRect)`
  - `data class OcrBlock(val lines: List<OcrLine>)`，属性 `rect: PixelRect`（各行的并集）、`text: String`（按行拼接）
  - `data class OcrPage(val width: Int, val height: Int, val blocks: List<OcrBlock>)`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.xzm.realtimetranslate.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrGeometryTest {

    @Test
    fun `scale back divides coordinates by the preprocessing factor`() {
        val upscaled = PixelRect(100, 50, 300, 150)
        assertEquals(PixelRect(50, 25, 150, 75), upscaled.scaleToSource(2f, 2f))
    }

    @Test
    fun `offset adds a region origin`() {
        assertEquals(PixelRect(110, 250, 310, 350), PixelRect(10, 50, 210, 150).offset(100, 200))
    }

    @Test
    fun `union covers all lines of a block`() {
        val block = OcrBlock(
            listOf(
                OcrLine("第一行", PixelRect(10, 10, 110, 40)),
                OcrLine("第二行", PixelRect(20, 50, 100, 80)),
            ),
        )
        assertEquals(PixelRect(10, 10, 110, 80), block.rect)
        assertEquals("第一行\n第二行", block.text)
    }

    @Test
    fun `intersection detects a status-bar overlap`() {
        val statusBand = PixelRect(0, 0, 1080, 80)
        assertTrue(statusBand.intersects(PixelRect(100, 40, 200, 90)))
        assertFalse(statusBand.intersects(PixelRect(100, 100, 200, 140)))
    }

    @Test
    fun `degenerate boxes still produce non-negative sizes`() {
        val box = PixelRect(50, 50, 50, 50)
        assertEquals(0, box.width)
        assertEquals(0, box.height)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --tests com.xzm.realtimetranslate.ocr.OcrGeometryTest --console=plain > E:\AAAtranslator\build_log_p1t1.txt 2>&1"; echo "GRADLE_EXIT=$?"
```
预期：编译失败，`Unresolved reference: PixelRect`。

- [ ] **Step 3: 实现 `OcrModels.kt`**

按上面的签名实现；`scaleToSource` 用 `(left / sx).toInt()` 等除法并保证 `right >= left`、`bottom >= top`；`union` 取四边极值；`intersects` 用严格不等：`left < other.right && other.left < right && top < other.bottom && other.top < bottom`。文件不导入任何 `android.*`。

- [ ] **Step 4: 跑测试确认通过**

Run：同 Step 2。预期：`BUILD SUCCESSFUL`，5 个用例通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ocr/OcrModels.kt app/src/test/java/com/xzm/realtimetranslate/ocr/OcrGeometryTest.kt
git commit -m "feat(ocr): 新增结构化 OCR 模型与坐标回算"
```

---

### Task 2: `ScreenTextOcr` 返回带外框的页面

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ocr/ScreenTextOcr.kt`
- Test: 无 JVM 测试（依赖 `Bitmap`/ML Kit）。由 Task 9 的真机验证兜底。

**Interfaces:**
- Consumes: `OcrPage`、`OcrBlock`、`OcrLine`、`PixelRect`（Task 1）。
- Produces: `suspend fun recognizePage(bitmap: Bitmap, script: OcrScript): OcrPage?`；`recognize()` 行为保持不变（返回清洗后的阅读顺序文本）。

- [ ] **Step 1: 改造预处理返回缩放系数**

把 `private fun preprocess(bitmap: Bitmap): Bitmap` 改成：

```kotlin
private class Prepared(val bitmap: Bitmap, val scaleX: Float, val scaleY: Float)

private fun prepare(bitmap: Bitmap): Prepared
```

`upscale` 返回 `Pair<Bitmap, Float>`（未放大时 1f），`enhance` 保持尺寸不变，因此 `scaleX = prepared.width.toFloat() / bitmap.width`，`scaleY` 同理。调用方 `recognize()` / `recognizePage()` 在 `finally` 中回收 `prepared.bitmap`（仅当 `!== bitmap`）以及中间 `enhance` 生成的位图。

- [ ] **Step 2: 实现 `recognizePage`**

```kotlin
suspend fun recognizePage(bitmap: Bitmap, script: OcrScript): OcrPage?
```

对 `script == AUTO` 并行跑 Latin + Japanese，沿用现有 `pickBest` 选择胜者，但改为**选择整份 `OcrPage`**：先比较两侧的拼接文本，胜者对应的 page 返回。单脚本直接识别。

每个 `Text.TextBlock` 转 `OcrBlock`：遍历 `block.lines`，`line.boundingBox?.let { PixelRect(it.left, it.top, it.right, it.bottom).scaleToSource(sx, sy) }`，丢弃空文本或空框；block 内没有有效行则丢弃。`OcrPage(width = bitmap.width, height = bitmap.height, blocks = ...)`。

- [ ] **Step 3: 让 `recognize()` 复用 `recognizePage()`**

```kotlin
suspend fun recognize(bitmap: Bitmap, script: OcrScript): String? =
    recognizePage(bitmap, script)?.blocks?.joinToString("\n") { it.text }
        ?.let { OcrTextProcessor.cleanAndJoinLines(it) }
        ?.takeIf { it.isNotEmpty() }
```

保留 `joinReadingOrder` 的排序语义（按 block top、left 排序）在 `recognizePage()` 内实现，避免现有气泡文本顺序变化。

- [ ] **Step 4: 跑现有单测与编译**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest :app:compileDebugKotlin --console=plain > E:\AAAtranslator\build_log_p1t2.txt 2>&1"; echo "GRADLE_EXIT=$?"
```
预期：`GRADLE_EXIT=0`，204 条既有用例全绿。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ocr/ScreenTextOcr.kt
git commit -m "feat(ocr): OCR 返回行级外框，旧文本接口行为不变"
```

---

### Task 3: 背景色采样

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/ocr/BackgroundColorSampler.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/ocr/BackgroundColorQuantizerTest.kt`

**Interfaces:**
- Consumes: `PixelRect`（Task 1）。
- Produces:
  - `object ColorQuantizer { fun dominant(argb: IntArray): Int }`（纯 Kotlin，量化 5 bit/通道，返回不透明大众色；全透明或空数组返回 `Color.WHITE` 常量 `0xFFFFFFFF.toInt()`）
  - `class BackgroundColorSampler { fun sample(bitmap: Bitmap, rect: PixelRect): Int }`（Android 侧，取 rect 外扩 3px 的一圈像素交给 `dominant`）

- [ ] **Step 1: 写失败测试**

```kotlin
package com.xzm.realtimetranslate.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundColorQuantizerTest {

    @Test
    fun `most frequent color wins`() {
        val white = 0xFFFFFFFF.toInt()
        val red = 0xFFFF0000.toInt()
        val pixels = IntArray(10) { if (it < 7) white else red }
        assertEquals(white, ColorQuantizer.dominant(pixels))
    }

    @Test
    fun `near identical shades quantize together`() {
        val a = 0xFFFFFFFF.toInt()
        val b = 0xFFFEFEFE.toInt()
        val pixels = intArrayOf(a, a, b, b)
        assertEquals(a, ColorQuantizer.dominant(pixels))
    }

    @Test
    fun `transparent pixels are ignored`() {
        val black = 0xFF000000.toInt()
        val pixels = intArrayOf(0x00000000, black, black)
        assertEquals(black, ColorQuantizer.dominant(pixels))
    }

    @Test
    fun `empty input falls back to white`() {
        assertEquals(0xFFFFFFFF.toInt(), ColorQuantizer.dominant(IntArray(0)))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run：`--tests com.xzm.realtimetranslate.ocr.BackgroundColorQuantizerTest`（命令格式同 Task 1 Step 2）。预期：`Unresolved reference: ColorQuantizer`。

- [ ] **Step 3: 实现**

`dominant`：遍历像素，跳过 `alpha < 0x80`；键 = `(r shr 3 shl 10) or (g shr 3 shl 5) or (b shr 3)`；用 `HashMap<Int, Int>` 计数并记录该键第一个原始像素；返回计数最高的原始像素的不透明版本（`or 0xFF000000.toInt()`）。

`sample(bitmap, rect)`：把 rect 外扩 3px 后 clamp 到位图范围，只取外圈（原 rect 之外的像素），否则取 rect 内全部像素；调 `dominant`。

- [ ] **Step 4: 跑测试确认通过**

Run：同 Step 2。预期：4 个用例通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ocr/BackgroundColorSampler.kt app/src/test/java/com/xzm/realtimetranslate/ocr/BackgroundColorQuantizerTest.kt
git commit -m "feat(ocr): 覆盖块背景色采样"
```

---

### Task 4: 翻译规划器

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/ocr/TranslationPlanner.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/ocr/TranslationPlannerTest.kt`

**Interfaces:**
- Consumes: `OcrPage`、`OcrBlock`、`OcrLine`、`PixelRect`（Task 1）。
- Produces:

```kotlin
data class TextTarget(val key: String, val text: String, val rect: PixelRect)

object TranslationPlanner {
    fun plan(
        page: OcrPage,
        targetLang: String,
        alreadyTranslated: Set<String> = emptySet(),
    ): List<TextTarget>

    fun shouldTranslate(text: String, targetLang: String): Boolean
    fun normalize(text: String): String
}
```

- [ ] **Step 1: 写失败测试**

```kotlin
package com.xzm.realtimetranslate.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPlannerTest {

    private fun page(vararg blocks: OcrBlock) = OcrPage(1080, 2400, blocks.toList())

    private fun block(vararg lines: Pair<String, PixelRect>) =
        OcrBlock(lines.map { OcrLine(it.first, it.second) })

    @Test
    fun `english blocks are planned with their union rect`() {
        val p = page(
            block(
                "Hello world" to PixelRect(10, 10, 300, 40),
                "this is a test" to PixelRect(10, 50, 320, 80),
            ),
        )
        val targets = TranslationPlanner.plan(p, "zh")
        assertEquals(1, targets.size)
        assertEquals("Hello world\nthis is a test", targets[0].text)
        assertEquals(PixelRect(10, 10, 320, 80), targets[0].rect)
    }

    @Test
    fun `identical text is translated once`() {
        val p = page(
            block("Settings" to PixelRect(0, 0, 100, 30)),
            block("Settings" to PixelRect(0, 100, 100, 130)),
        )
        assertEquals(1, TranslationPlanner.plan(p, "zh").size)
    }

    @Test
    fun `text already in the target language is skipped`() {
        val p = page(block("设置" to PixelRect(0, 0, 100, 30)))
        assertTrue(TranslationPlanner.plan(p, "zh").isEmpty())
    }

    @Test
    fun `single characters and symbols are skipped`() {
        val p = page(
            block(">" to PixelRect(0, 0, 20, 20)),
            block("OK" to PixelRect(0, 40, 60, 70)),
        )
        assertEquals(listOf("OK"), TranslationPlanner.plan(p, "zh").map { it.text })
    }

    @Test
    fun `already translated keys are not planned again`() {
        val p = page(block("Open file" to PixelRect(0, 0, 200, 30)))
        val first = TranslationPlanner.plan(p, "zh")
        assertTrue(TranslationPlanner.plan(p, "zh", setOf(first[0].key)).isEmpty())
    }

    @Test
    fun `normalization ignores whitespace differences`() {
        assertEquals(
            TranslationPlanner.normalize("  Hello   world "),
            TranslationPlanner.normalize("Hello world"),
        )
    }

    @Test
    fun `empty page yields no targets`() {
        assertTrue(TranslationPlanner.plan(OcrPage(1080, 2400, emptyList()), "zh").isEmpty())
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run：`--tests com.xzm.realtimetranslate.ocr.TranslationPlannerTest`。预期：`Unresolved reference: TranslationPlanner`。

- [ ] **Step 3: 实现**

规则（全部纯 Kotlin）：

- `normalize(text)`：合并空白为单空格、`trim()`。
- `plan`：按 block 顺序；block 文本为空跳过；`key = normalize(block.text)`；`alreadyTranslated` 含该 key 跳过；同页 `seen` 去重。
- `shouldTranslate(text, targetLang)`：
  - 去掉空白后长度 < 2 → false。
  - `targetLang` 以 `zh` 开头：非 ASCII 字母数字比例 > 0.6 且不含假名/谚文 → false（已是中文）；否则 true。
  - `targetLang` 以 `en` 开头：字母中 ASCII 比例 > 0.9 且不含 CJK → false。
  - 其余语言一律 true。
- `rect` 取 `block.rect`；block 文本保持换行拼接（供双语/多行排版）。

- [ ] **Step 4: 跑测试确认通过**

Run：同 Step 2。预期：7 个用例通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ocr/TranslationPlanner.kt app/src/test/java/com/xzm/realtimetranslate/ocr/TranslationPlannerTest.kt
git commit -m "feat(ocr): 翻译规划器（合并、去重、跳过目标语言）"
```

---

### Task 5: 翻译缓存

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/translate/TranslationCache.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/translate/TranslationCacheTest.kt`

**Interfaces:**
- Consumes: 无。
- Produces:
  - `class TranslationCache(private val capacity: Int = 300)`，方法 `get(key: String): String?`、`put(key: String, value: String)`、`clear()`，属性 `size: Int`。

- [ ] **Step 1: 写失败测试**

```kotlin
package com.xzm.realtimetranslate.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationCacheTest {

    @Test
    fun `get returns what was put`() {
        val cache = TranslationCache()
        cache.put("hello", "你好")
        assertEquals("你好", cache.get("hello"))
    }

    @Test
    fun `least recently used entry is evicted first`() {
        val cache = TranslationCache(capacity = 2)
        cache.put("a", "甲")
        cache.put("b", "乙")
        cache.get("a") // "a" 变成最近使用
        cache.put("c", "丙")
        assertNull(cache.get("b"))
        assertEquals("甲", cache.get("a"))
        assertEquals("丙", cache.get("c"))
    }

    @Test
    fun `putting the same key updates without growing`() {
        val cache = TranslationCache(capacity = 2)
        cache.put("a", "甲")
        cache.put("a", "甲2")
        assertEquals(1, cache.size)
        assertEquals("甲2", cache.get("a"))
    }

    @Test
    fun `clear empties the cache`() {
        val cache = TranslationCache()
        cache.put("a", "甲")
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.get("a"))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run：`--tests com.xzm.realtimetranslate.translate.TranslationCacheTest`。预期：`Unresolved reference: TranslationCache`。

- [ ] **Step 3: 实现**

用 `LinkedHashMap<String, String>(capacity, 0.75f, true)`（access-order）+ 重写 `removeEldestEntry`，超过容量时淘汰。方法加 `@Synchronized`，因为采集 worker 与翻译 worker 会并发访问。

- [ ] **Step 4: 跑测试确认通过**

Run：同 Step 2。预期：4 个用例通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/translate/TranslationCache.kt app/src/test/java/com/xzm/realtimetranslate/translate/TranslationCacheTest.kt
git commit -m "feat(translate): 覆盖模式翻译缓存（LRU）"
```

---

### Task 6: 覆盖文本排版（纯逻辑）与覆盖窗口

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/overlay/CoverLayout.kt`
- Create: `app/src/main/java/com/xzm/realtimetranslate/overlay/CoverOverlayController.kt`
- Create: `app/src/main/java/com/xzm/realtimetranslate/overlay/CoverView.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/overlay/CoverLayoutTest.kt`

**Interfaces:**
- Consumes: `PixelRect`（Task 1）。
- Produces:
  - `data class CoverStyle(val bilingual: Boolean = false, val fontScale: Float = 1.0f, val blockStyle: String = "AUTO")`（覆盖层只看这三个值，不依赖 `UserSettings`）
  - `data class FittedText(val lines: List<String>, val fontSize: Float, val truncated: Boolean)`
  - `object CoverLayout`：
    - `fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String>`（纯逻辑，按字符贪心换行；CJK 之间可断，拉丁词优先按空格断）
    - `fun fit(text: String, maxWidth: Float, maxHeight: Float, maxFontSize: Float, minFontSize: Float, measure: (String, Float) -> Float, lineHeight: (Float) -> Float): FittedText`
  - `data class CoverTarget(val rect: PixelRect, val text: String, val background: Int)`
  - `class CoverOverlayController(context: Context)`：`show(style: CoverStyle)`、`update(targets: List<CoverTarget>)`、`reclamp()`、`hide()`
  - `class CoverView(context: Context) : View`：`fun setTargets(targets: List<CoverTarget>, style: CoverStyle)`、`fun clear()`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.xzm.realtimetranslate.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLayoutTest {

    private val measure: (String) -> Float = { it.length * 10f }

    private val measureAt: (String, Float) -> Float = { s, size -> s.length * size * 0.5f }

    private val lineHeight: (Float) -> Float = { it * 1.2f }

    @Test
    fun `short text stays on one line`() {
        assertEquals(listOf("hello"), CoverLayout.wrap("hello", 100f, measure))
    }

    @Test
    fun `latin text breaks at spaces`() {
        assertEquals(
            listOf("hello", "world foo"),
            CoverLayout.wrap("hello world foo", 100f, measure),
        )
    }

    @Test
    fun `cjk text breaks between characters`() {
        assertEquals(
            listOf("你好世界", "再见"),
            CoverLayout.wrap("你好世界再见", 40f, measure),
        )
    }

    @Test
    fun `a single too-wide token is hard-broken`() {
        val lines = CoverLayout.wrap("abcdefghij", 30f, measure)
        assertTrue(lines.all { it.length <= 3 })
    }

    @Test
    fun `empty text yields no lines`() {
        assertTrue(CoverLayout.wrap("", 100f, measure).isEmpty())
    }

    @Test
    fun `short text keeps the maximum font size`() {
        val fitted = CoverLayout.fit("hi", 100f, 40f, 28f, 8f, measureAt, lineHeight)
        assertEquals(28f, fitted.fontSize, 0.01f)
        assertFalse(fitted.truncated)
        assertEquals(listOf("hi"), fitted.lines)
    }

    @Test
    fun `long text shrinks until the box height is respected`() {
        val fitted = CoverLayout.fit("a".repeat(30), 50f, 36f, 28f, 8f, measureAt, lineHeight)
        assertTrue(fitted.fontSize < 28f)
        assertFalse(fitted.truncated)
        assertTrue(fitted.lines.size * lineHeight(fitted.fontSize) <= 36f + 0.01f)
    }

    @Test
    fun `text too long even at the minimum size is truncated`() {
        val fitted = CoverLayout.fit("a".repeat(400), 20f, 12f, 28f, 8f, measureAt, lineHeight)
        assertEquals(8f, fitted.fontSize, 0.01f)
        assertTrue(fitted.truncated)
        assertTrue(fitted.lines.size * lineHeight(fitted.fontSize) <= 12f + 0.01f)
        assertTrue(fitted.lines.last().endsWith("…"))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run：`--tests com.xzm.realtimetranslate.overlay.CoverLayoutTest`。预期：`Unresolved reference: CoverLayout`。

- [ ] **Step 3: 实现 `CoverLayout`，让上面的用例通过**

`wrap`：按空格切 token；CJK 字符各自可作为断点（逐字符累积）；单个 token 超宽时按字符硬断；空文本返回空列表。

`fit`：从 `maxFontSize` 起以 1f 步长降到 `minFontSize`，每次用 `wrap(text, maxWidth) { measure(it, size) }` 算 `lines.size * lineHeight(size)`，第一个 ≤ `maxHeight` 的字号即结果（`truncated = false`）。全部超限则取 `minFontSize`，只保留 `max(1, floor(maxHeight / lineHeight(minFontSize)))` 行，末行裁到宽度放得下并补 `…`，`truncated = true`。

Run：`--tests com.xzm.realtimetranslate.overlay.CoverLayoutTest`。预期：8 个用例通过。

- [ ] **Step 4: 实现 `CoverView`**

- `onDraw` 只画传入的 targets；`setTargets` 整体替换并 `invalidate()`，`clear()` 置空列表并 `invalidate()`。
- 每个 target：底色块 = `rect` 外扩 2dp、圆角 4dp、颜色 `target.background`（`style.blockStyle == "DARK"` 时用 `0xE6000000`）。
- 译文用 `CoverLayout.fit`：`maxWidth = 块内宽`，`maxHeight = 块内高`，`maxFontSize = rect.height * 0.55f` 按 `style.fontScale` 缩放后夹在 `[8dp, 28dp]`，`minFontSize = 8dp`；`measure`/`lineHeight` 用 `Paint` 实现。
- 译文纯白；`style.bilingual` 为真时原文缩到 `fontSize * 0.75f`、颜色 `0xC8FFFFFF` 画在译文上方，两者合起来仍须装进块高。
- 坐标直接用传入的屏幕像素（窗口是全屏的，View 原点即屏幕原点）。

- [ ] **Step 5: 实现 `CoverOverlayController`**

- `WindowManager.LayoutParams(MATCH_PARENT, MATCH_PARENT, TYPE_APPLICATION_OVERLAY, FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)`。
- `show(style)` 建全屏 `CoverView` 并 `addView`（记下 style）；重复调用先移除旧窗口。
- `update(targets)`：丢掉与状态栏/导航栏带相交的 target（按 `WindowInsets` 高度裁剪），主线程 `view.setTargets`。
- `reclamp()`：按当前屏幕尺寸重建窗口并清空覆盖（旋转后调用）。
- `hide()`：移除窗口、置空引用，可重复调用。

- [ ] **Step 6: 跑测试确认通过 + 编译**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest :app:compileDebugKotlin --console=plain > E:\AAAtranslator\build_log_p1t6.txt 2>&1"; echo "GRADLE_EXIT=$?"
```
预期：8 个排版用例通过，`GRADLE_EXIT=0`。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/overlay/CoverLayout.kt app/src/main/java/com/xzm/realtimetranslate/overlay/CoverOverlayController.kt app/src/main/java/com/xzm/realtimetranslate/overlay/CoverView.kt app/src/test/java/com/xzm/realtimetranslate/overlay/CoverLayoutTest.kt
git commit -m "feat(cover): 触摸穿透的全屏覆盖层与文本排版"
```

---

### Task 7: 屏幕会话支持全屏覆盖模式

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/service/ScreenTextSessionService.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/data/UserSettings.kt`（新增 `coverBilingual`、`coverFontScale`、`coverBlockStyle`）
- Modify: `app/src/main/java/com/xzm/realtimetranslate/data/UserSettingsRepository.kt`（新增三个 DataStore 键）
- Modify: `app/src/main/res/values/strings.xml`、`app/src/main/res/values-zh/strings.xml`（通知文案与错误文案）
- Test: 无 JVM 测试；由 Task 9 真机验证。

**Interfaces:**
- Consumes: `recognizePage`（Task 2）、`BackgroundColorSampler`（Task 3）、`TranslationPlanner`（Task 4）、`TranslationCache`（Task 5）、`CoverOverlayController`/`CoverTarget`（Task 6）。
- Produces:
  - `enum class ScreenOcrMode { REGION, FULLSCREEN }`（文件内或独立 `data/ScreenOcrMode.kt`）
  - `ScreenTextSessionService.start(context, region: Rect, resultCode: Int, data: Intent, mode: ScreenOcrMode = ScreenOcrMode.REGION)`
  - `const val EXTRA_MODE = "mode"`
  - 服务文件内的 `private fun coverStyleOf(s: UserSettings): CoverStyle`

- [ ] **Step 1: 新增设置字段**

`UserSettings`：`coverBilingual: Boolean = false`、`coverFontScale: Float = 1.0f`、`coverBlockStyle: String = "AUTO"`。Repository 增加键 `cover_bilingual`、`cover_font_scale`、`cover_block_style`，读写映射带默认值；`Defaults` 里加三个常量。`ScreenTextSessionService` 文件内加 `private fun coverStyleOf(s: UserSettings) = CoverStyle(s.coverBilingual, s.coverFontScale, s.coverBlockStyle)`。

- [ ] **Step 2: 服务读取 mode 并选择输出层**

`onStartCommand` 读取 `EXTRA_MODE`。`startSessionInternal` 里：
- `mode == REGION`：现有逻辑，`OcrBallOverlay`。
- `mode == FULLSCREEN`：`currentRegion = Rect(0, 0, displayW, displayH)`；`coverOverlay = CoverOverlayController(this).also { it.show(coverStyleOf(currentSettings)) }`；`showRegionOutline` 不调用；不创建 `OcrBallOverlay`。
- 通知标题/正文按模式取字符串：`cover_fullscreen_notification_title`（全屏翻译）、`cover_notification_text`（正在翻译屏幕文字）。

- [ ] **Step 3: 全屏模式的翻译 worker**

在 `runOcrLoop()` 内按模式分叉：
- 采集后用 `recognizePage(frame.bitmap, script)`。
- `targets = TranslationPlanner.plan(page, targetLang, alreadyTranslated)`
- 对每个 target：先查 `TranslationCache.get(target.key)`，命中直接收集；未命中进入并发 2 的翻译（`Semaphore(2)`），逐块 `engine.translate(text, sourceLang, targetLang).last()`（取最后累计值）。
- 每完成一块：`background = BackgroundColorSampler.sample(frame.bitmap, target.rect)`，把 `CoverTarget(rect = target.rect.offset(region.left, region.top), text = 译文, background)` 合并进当前帧的列表，`coverOverlay.update(list)`。
- 失败块：不加入覆盖列表（保持原文可见），记 `Log.w`。
- 世代号 `coverEpoch`：每次新 OCR 结果 `++`，worker 结果发现 epoch 变化就丢弃。
- 已翻译集合 `alreadyTranslated` 累加本页出现过的 key（命中缓存或翻译成功），避免下一帧重复请求。

- [ ] **Step 4: 暂停/停止/旋转清理**

- `setPaused(true)`：全屏模式只停采集与翻译，**保留当前覆盖层**（与现有气泡一致的语义）。
- `stopEverything` / `onDestroy`：`coverOverlay?.hide()`；`CoverOverlayController` 释放窗口。
- `onConfigurationChanged`：全屏模式不映射选区；`coverOverlay?.reclamp()` 并清空覆盖，`lastHash` 复位。

- [ ] **Step 5: 编译并跑既有单测**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest :app:compileDebugKotlin --console=plain > E:\AAAtranslator\build_log_p1t7.txt 2>&1"; echo "GRADLE_EXIT=$?"
```
预期：`GRADLE_EXIT=0`，既有用例全绿。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/service/ScreenTextSessionService.kt app/src/main/java/com/xzm/realtimetranslate/data/UserSettings.kt app/src/main/java/com/xzm/realtimetranslate/data/UserSettingsRepository.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat(cover): 屏幕会话新增全屏覆盖模式"
```

---

### Task 8: 首页临时入口

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/main/MainActivity.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/subtitle/SubtitleScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`、`app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Consumes: `ScreenOcrMode`、`ScreenTextSessionService.start(..., mode)`（Task 7）。
- Produces: 首页「全屏翻译」按钮；跑通全屏模式的权限链与授权链。

- [ ] **Step 1: 新增字符串**

- `cover_fullscreen`：`全屏翻译` / `Full-screen translate`
- `cover_starting`：`正在启动全屏翻译…` / `Starting full-screen translation…`
- 其余复用 `msg_requesting_projection`、`ocr_projection_failed`。

- [ ] **Step 2: MainActivity 增加启动函数**

参照现有 `requestOcrProjection`/`requestStartOcr`：

```kotlin
fun requestStartFullscreen() {
    // 互斥：先停语音与其他屏幕会话
    // 凭据门禁：missingEngineKeyMessage()
    // 通知权限 -> 悬浮窗权限 -> createScreenCaptureIntent
    // 授权成功：ScreenTextSessionService.start(this, Rect(0,0,1,1), code, data, ScreenOcrMode.FULLSCREEN)
}
```

区域参数传 `Rect(0, 0, 1, 1)` 仅作占位，服务在 FULLSCREEN 模式下会自行改为整屏；**不要**为全屏弹选区浮层。

- [ ] **Step 3: SubtitleScreen 加入口**

在「🔍 屏幕取词」按钮下方加一个同风格按钮「🖥 全屏翻译」，点击调用 `onStartFullscreen`（新增回调参数，MainActivity 传入 `requestStartFullscreen`）。运行中显示「停止全屏翻译」并调用 `stopOcr()`。

- [ ] **Step 4: 编译**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:compileDebugKotlin --console=plain > E:\AAAtranslator\build_log_p1t8.txt 2>&1"; echo "GRADLE_EXIT=$?"
```
预期：`GRADLE_EXIT=0`。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/main/MainActivity.kt app/src/main/java/com/xzm/realtimetranslate/ui/subtitle/SubtitleScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat(cover): 首页新增全屏翻译入口"
```

---

### Task 9: 构建、装机与真机验收

**Files:**
- 无（构建产物：`app/build/outputs/apk/release/app-release.apk` → 复制到 `E:\AAAtranslator\LiveTranslate-release.apk`）

**Interfaces:**
- Consumes: 前面全部任务。
- Produces: 可安装的 release APK 与验收记录。

- [ ] **Step 1: 全量构建 + 单测**

```bash
export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest assembleRelease --console=plain > E:\AAAtranslator\build_log_cover_release.txt 2>&1"; echo "GRADLE_EXIT=$?"
```
预期：`GRADLE_EXIT=0`；`app/build/test-results/testDebugUnitTest/TEST-*.xml` 汇总 `failures=0 errors=0 skipped=0`。

- [ ] **Step 2: 覆盖交付 APK 并验签**

```bash
cp app/build/outputs/apk/release/app-release.apk LiveTranslate-release.apk
export JAVA_HOME=/e/AAAtranslator/jdk && "/e/AAAtranslator/android-sdk/build-tools/37.0.0/apksigner.bat" verify --print-certs LiveTranslate-release.apk
```

- [ ] **Step 3: 装真机**

```bash
"/e/AAAtranslator/android-sdk/platform-tools/adb.exe" install -r -d LiveTranslate-release.apk
```

- [ ] **Step 4: 用户手动验收（MIUI 禁止 adb 注入，只能由用户操作）**

逐条确认并记录：
1. 首页点「全屏翻译」→ 录屏授权 → 打开任意英文网页/App → 原文被纯色块盖住、译文在框内、位置对得上。
2. **触摸穿透**：覆盖期间能正常点击、滑动下层 App。
3. 覆盖块颜色接近原文背景；译文过长时缩字号、再长则省略，不溢到相邻行。
4. 转屏后旧覆盖清空、按新方向重建，不残留。
5. 全是中文的界面启动后不发翻译请求（看日志无 `TRANSPROF translateLatest`）。
6. 点「停止全屏翻译」后覆盖层立即消失、录屏通知消失。
7. 回归：屏幕取词（框选+气泡）与语音同传行为不变。

- [ ] **Step 5: 提交验收记录（若无代码改动则跳过）**

若验收中发现需要修的问题，按问题单独提交；不要把所有修复塞进一个提交。

---

## 后续计划（不在本计划内）

- **Plan 2**：通用悬浮球（`FloatingHubService` + 菜单 + 语音窗贴边隐藏 + 现有两模式接入）。
- **Plan 3**：漫画模式调优（竖排合并、拟声词、气泡分组）。
- **Plan 4**：相机 + 图片翻译（含保存到相册）。
- **Plan 5**：PDF + Word 文档覆盖翻译。
