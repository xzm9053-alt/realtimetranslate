# PDF 与 Word 文档翻译（P5）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 PDF 与 Word (.docx) 文档的离线识别与自动翻译，支持 PDF 双语交替导出（每页原文+每页译文）与 Word 原位段落文字替换，生成翻译文档保存至 `Downloads/实时翻译`。

**Architecture:** 
1. **Word (docx)**：基于标准 Zip 与 XML DOM 解析器，读取 `word/document.xml`，以 `<w:p>` 段落为粒度合并 `<w:t>` 文本送译，写回首个 `<w:t>` 并清空同段后续 `<w:t>`，保留其余 Zip 资源原样打包导出；
2. **PDF**：通过系统 `PdfRenderer` 按 ~2000px 长边高质量逐页位图渲染，复用 OCR + 规划 + 翻译 + 覆盖绘制管线，通过 `android.graphics.pdf.PdfDocument` 导出新 PDF（默认支持「双语交替」：第 1 页原文、第 2 页译文...）；
3. **输出与导出**：`DocumentExporter` 统一将结果输出至 MediaStore `Downloads/实时翻译`；
4. **UI**：`DocumentTranslateScreen` + `DocumentTranslateViewModel` 提供文档选择、PDF 双语交替开关、页数/段落实时进度与一键打开/分享，无缝接入悬浮球菜单与 `MoreScreen`。

**Tech Stack:** Kotlin、Android `PdfRenderer`、Android `PdfDocument`、Java Zip & XML DOM、Jetpack Compose、Miuix KMP、Kotlin Coroutines。

**Spec:** `docs/superpowers/specs/2026-10-24-hub-modes-design.md` §5.8 & §9。

## Global Constraints

- `minSdk = 29`，`targetSdk = 35`，包名 `com.xzm.realtimetranslate`。
- 遵循应用既有主题规范与 Miuix 控件风格，不引入重量级第三方 Office/PDF SDK（如 Apache POI、iText）。
- PDF 单次上限 50 页；超出时明确提示分次翻译。
- PDF 导出形态：默认支持并启用**双语交替**（用户已敲定：每页原文后紧跟一页译文），同时提供切换为「仅译文页」选项。
- Word 仅替换正文段落与表格单元格的文字，页眉页脚、文本框、艺术字不作承诺，结果页给出明确告知。
- 翻译文档保存路径统一为 MediaStore `Download/实时翻译/`。
- 保持现有 276 个单元测试全部通过，新增模块具备完整单元测试。
- Windows 构建工作流标准命令：
  ```bash
  export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest assembleRelease --console=plain > E:\AAAtranslator\build_log_p5.txt 2>&1"; echo "GRADLE_EXIT=$?"
  ```

## Review Focus

1. **Word 复杂运行与空段落处理**：`<w:p>` 可能包含空 run、超链接、公式或仅样式标记。提取与回写必须保证只替换有效文字，绝不能产生空指针或损坏 XML 命名空间。
2. **XML 转义与特殊字符**：段落文本中的 `&`、`<`、`>`、换行符或 Emoji 必须在 XML 序列化时合法转义，杜绝导出损坏的 Word 文档。
3. **PDF 页数上限与内存控制**：超过 50 页立即拦截提示；逐页渲染与覆盖位图必须及时 `recycle()`，避免大页数多图片引起 OOM。
4. **PDF 双语交替页序一致性**：双语导出时页码必须精确匹配（页 1 原文 -> 页 2 译文 -> 页 3 原文 -> 页 4 译文），不能出现错页或漏页。
5. **网络波动与部分翻译降级**：某段或某页翻译失败时，保留原文而不是打上 "null" 或破坏排版。

---

### Task 1: 文档模型 `DocumentModels` 与导出器 `DocumentExporter`

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/document/DocumentModels.kt`
- Create: `app/src/main/java/com/xzm/realtimetranslate/util/DocumentExporter.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/document/DocumentModelsTest.kt`

**Interfaces:**
- Produces:
  - `enum class DocumentType { PDF, DOCX, UNKNOWN }`
  - `data class DocumentInfo(val uri: Uri, val name: String, val sizeBytes: Long, val type: DocumentType)`
  - `data class DocumentTranslationConfig(val bilingualAlternating: Boolean = true, val maxPages: Int = 50)`
  - `data class DocumentTranslationProgress(val done: Int, val total: Int, val description: String)`
  - `object DocumentExporter`：
    - `fun saveToDownloads(context: Context, fileName: String, mimeType: String, writeBlock: (OutputStream) -> Unit): Result<String>`
    - `fun generateOutputFileName(originalName: String, bilingual: Boolean): String`

- [ ] **Step 1: 编写失败测试 `DocumentModelsTest.kt`**

```kotlin
package com.xzm.realtimetranslate.document

import com.xzm.realtimetranslate.util.DocumentExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentModelsTest {

    @Test
    fun `detect document type from extension`() {
        assertEquals(DocumentType.PDF, DocumentType.fromFileName("paper.pdf"))
        assertEquals(DocumentType.PDF, DocumentType.fromFileName("REPORT.PDF"))
        assertEquals(DocumentType.DOCX, DocumentType.fromFileName("notes.docx"))
        assertEquals(DocumentType.DOCX, DocumentType.fromFileName("doc.DOCX"))
        assertEquals(DocumentType.UNKNOWN, DocumentType.fromFileName("image.png"))
    }

    @Test
    fun `generate output file name with bilingual and translation tags`() {
        val pdfOut = DocumentExporter.generateOutputFileName("sample.pdf", bilingual = true)
        assertTrue(pdfOut.startsWith("sample-双语-"))
        assertTrue(pdfOut.endsWith(".pdf"))

        val docxOut = DocumentExporter.generateOutputFileName("contract.docx", bilingual = false)
        assertTrue(docxOut.startsWith("contract-翻译-"))
        assertTrue(docxOut.endsWith(".docx"))
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --tests com.xzm.realtimetranslate.document.DocumentModelsTest --console=plain"`
Expected: FAIL with missing classes.

- [ ] **Step 3: 实现 `DocumentModels.kt` 与 `DocumentExporter.kt`**

在 `app/src/main/java/com/xzm/realtimetranslate/document/DocumentModels.kt` 中定义枚举与数据结构；
在 `app/src/main/java/com/xzm/realtimetranslate/util/DocumentExporter.kt` 中实现通用字节流写入 MediaStore `Download/实时翻译` 逻辑（适配 Android Q+ IS_PENDING 机制与旧版本 fallback）。

- [ ] **Step 4: 运行测试验证通过**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --tests com.xzm.realtimetranslate.document.DocumentModelsTest --console=plain"`
Expected: PASS.

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/document/DocumentModels.kt app/src/main/java/com/xzm/realtimetranslate/util/DocumentExporter.kt app/src/test/java/com/xzm/realtimetranslate/document/DocumentModelsTest.kt
git commit -m "feat(document): 新增文档类型模型与 MediaStore 文档导出器"
```

---

### Task 2: Word (.docx) 翻译引擎 `DocxTranslator`

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/document/DocxTranslator.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/document/DocxTranslatorTest.kt`

**Interfaces:**
- Consumes:
  - `TranslationEngine`
  - `UserSettings`
- Produces:
  - `class DocxTranslator(private val engine: TranslationEngine)`：
    - `suspend fun translate(input: InputStream, output: OutputStream, sourceLang: String, targetLang: String, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): DocxTranslationResult`
    - `fun extractAndReplaceParagraphs(documentXmlStream: InputStream, translateBlock: (List<String>) -> List<String>): ByteArray`

- [ ] **Step 1: 编写失败测试 `DocxTranslatorTest.kt`**

覆盖：
1. `<w:p>` 包含多个 `<w:r><w:t>` run 的文本合并与单段回写；
2. 表格单元格 `<w:tc>` 中的 `<w:p>` 翻译支持；
3. 空白段落与仅有样式的 `<w:p>` 保持原样不崩溃；
4. XML 特殊字符（`<`, `>`, `&`）的正确序列化；
5. 完整 Zip 流处理（非 `document.xml` 的 entry 原样保留）。

```kotlin
package com.xzm.realtimetranslate.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DocxTranslatorTest {

    private val sampleXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:body>
                <w:p>
                    <w:pPr><w:jc w:val="center"/></w:pPr>
                    <w:r><w:t>Hello </w:t></w:r>
                    <w:r><w:t>World!</w:t></w:r>
                </w:p>
                <w:p>
                    <w:r><w:t>Tom &amp; Jerry &lt;1&gt;</w:t></w:r>
                </w:p>
                <w:p/>
            </w:body>
        </w:document>
    """.trimIndent()

    @Test
    fun `extract and replace paragraph text preserving xml structure`() {
        val translator = DocxTranslator(FakeTranslationEngine())
        val modifiedBytes = translator.extractAndReplaceParagraphs(sampleXml.byteInputStream()) { texts ->
            texts.map { "译: $it" }
        }
        val resultXml = String(modifiedBytes, Charsets.UTF_8)
        assertTrue(resultXml.contains("译: Hello World!"))
        assertTrue(resultXml.contains("译: Tom &amp; Jerry &lt;1&gt;"))
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --tests com.xzm.realtimetranslate.document.DocxTranslatorTest --console=plain"`
Expected: FAIL with `DocxTranslator` not found.

- [ ] **Step 3: 实现 `DocxTranslator.kt`**

使用 DOM (`DocumentBuilderFactory` / `TransformerFactory`) 解析 `word/document.xml`：
1. 获取所有 `<w:p>` 元素；
2. 提取子元素中的所有 `<w:t>` 文本拼成段落文字；
3. 过滤空白与已是目标语言的内容；
4. 并发限流（`Semaphore(4)`）分批请求翻译；
5. 回写至第一个 `<w:t>`，将该段其余 `<w:t>` 置空；
6. 其他 ZipEntry 通过缓冲管道原封不动复制到输出流。

- [ ] **Step 4: 运行测试验证通过**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --tests com.xzm.realtimetranslate.document.DocxTranslatorTest --console=plain"`
Expected: PASS.

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/document/DocxTranslator.kt app/src/test/java/com/xzm/realtimetranslate/document/DocxTranslatorTest.kt
git commit -m "feat(document): 实现 Word (docx) 段落提取与原位翻译写回引擎"
```

---

### Task 3: PDF 逐页渲染与覆盖翻译管线 `PdfTranslator`

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/document/PdfTranslator.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/document/PdfTranslatorTest.kt`

**Interfaces:**
- Consumes:
  - `ImageTranslationPipeline` (or `ScreenTextOcr` + `CoverPainter`)
  - `UserSettings`
  - `TranslationEngine`
- Produces:
  - `class PdfTranslator(private val ocr: ScreenTextOcr)`：
    - `suspend fun translatePdf(context: Context, pdfFileDescriptor: ParcelFileDescriptor, outputStream: OutputStream, settings: UserSettings, engine: TranslationEngine, config: DocumentTranslationConfig, onProgress: (page: Int, totalPages: Int) -> Unit): PdfTranslationResult`
  - `data class PdfTranslationResult(val pageCount: Int, val exportedPages: Int, val totalBlocksTranslated: Int)`

- [ ] **Step 1: 编写失败测试 `PdfTranslatorTest.kt`**

测试边界与策略：
1. 超过 `maxPages` (50页) 触发 `IllegalArgumentException` 异常并包含明确提示；
2. 双语交替算法验证：源 N 页在 `bilingualAlternating = true` 时生成 2N 页，在 `bilingualAlternating = false` 时生成 N 页；
3. 位图尺寸缩放计算：保证长边在 ~2000px 范围。

- [ ] **Step 2: 运行测试验证失败**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --tests com.xzm.realtimetranslate.document.PdfTranslatorTest --console=plain"`
Expected: FAIL.

- [ ] **Step 3: 实现 `PdfTranslator.kt`**

1. 检查 `PdfRenderer.pageCount`，超限立即抛出可读异常；
2. 初始化 `android.graphics.pdf.PdfDocument`；
3. 循环 `0 until pageCount`：
   - 渲染至 ~2000px ARGB_8888 位图，预填充白色底色；
   - 运行 OCR 识别与段落覆盖管线生成 `translatedBitmap`；
   - 根据 `bilingualAlternating` 决定写入顺序：
     - 若为 true：先写原文位图页，再写译文位图页；
     - 若为 false：只写译文位图页；
   - 立即释放并回收当前页临时位图（`bitmap.recycle()`）；
   - 回调 `onProgress(index + 1, pageCount)`；
4. 将 `PdfDocument` 写出到 `outputStream`，关闭资源。

- [ ] **Step 4: 运行测试验证通过**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --tests com.xzm.realtimetranslate.document.PdfTranslatorTest --console=plain"`
Expected: PASS.

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/document/PdfTranslator.kt app/src/test/java/com/xzm/realtimetranslate/document/PdfTranslatorTest.kt
git commit -m "feat(document): 实现 PDF 逐页高质量渲染与双语交替覆盖导出管线"
```

---

### Task 4: 文档翻译 `DocumentTranslateViewModel`

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/ui/document/DocumentTranslateViewModel.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/ui/document/DocumentTranslateViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `PdfTranslator`
  - `DocxTranslator`
  - `DocumentExporter`
  - `UserSettingsRepository`
  - `ApiKeyStore`
  - `HistoryRepository`
- Produces:
  - `class DocumentTranslateViewModel`
  - `data class DocumentTranslateUiState`

- [ ] **Step 1: 编写失败测试 `DocumentTranslateViewModelTest.kt`**

测试：
1. 选择文档后正确定位 `DocumentType`（PDF / DOCX）；
2. 缺少翻译 Key 时的提示状态；
3. 双语交替配置项切换；
4. 翻译完成后的历史记录写入。

- [ ] **Step 2: 运行测试验证失败**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --tests com.xzm.realtimetranslate.ui.document.DocumentTranslateViewModelTest --console=plain"`
Expected: FAIL.

- [ ] **Step 3: 实现 `DocumentTranslateViewModel.kt`**

管理文档选择、翻译执行、取消、进度更新以及调用系统 MediaStore 保存与历史落盘。

- [ ] **Step 4: 运行测试验证通过**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --tests com.xzm.realtimetranslate.ui.document.DocumentTranslateViewModelTest --console=plain"`
Expected: PASS.

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/document/DocumentTranslateViewModel.kt app/src/test/java/com/xzm/realtimetranslate/ui/document/DocumentTranslateViewModelTest.kt
git commit -m "feat(document): 实现文档翻译 ViewModel 与状态驱动"
```

---

### Task 5: 文档翻译界面 `DocumentTranslateScreen` 与资源串

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/ui/document/DocumentTranslateScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Produces:
  - `@Composable fun DocumentTranslateScreen(viewModel: DocumentTranslateViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: 增补中英文字符串资源**

在 `strings.xml` 和 `values-zh/strings.xml` 中添加：
- `document_title`, `document_subtitle`
- `document_select_file`, `document_change_file`
- `document_bilingual_toggle`, `document_bilingual_summary`
- `document_scope_notice`
- `document_start`, `document_translating`, `document_done`
- `document_open_file`, `document_share_file`
- `document_over_limit_error`

- [ ] **Step 2: 编写 `DocumentTranslateScreen.kt`**

基于 Miuix 风格（`Card`、`Button`、`Switch`、`ProgressBar`、`Text`）：
1. 顶部返回与标题栏；
2. 未选文档时的轻量占位卡片与「选择文档（PDF / Word）」按钮（使用 `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())`）；
3. 已选文档信息（名称、大小、类型标签）；
4. PDF 专属设置：双语交替开关（默认开）；
5. 限制告知：“说明：PDF 最多 50 页；Word 替换正文与表格，页眉页脚不替换”；
6. 正在翻译卡片（带环形/水平进度条与“正在翻译第 X / Y 页/段”）；
7. 结果卡片（保存路径高亮、一键打开文件、一键分享、重新选择）。

- [ ] **Step 3: 验证编译通过**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:compileDebugKotlin --console=plain"`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/document/DocumentTranslateScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat(document): 构建 Miuix 风格文档翻译界面与本地化文案"
```

---

### Task 6: 悬浮球 Hub 与 `MoreScreen` 入口整合

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/more/MoreScreen.kt`

**Interfaces:**
- Consumes:
  - `StaticModeTarget.DOCUMENT`
  - `DocumentTranslateScreen`
  - `DocumentTranslateViewModel`

- [ ] **Step 1: 改造 `MoreScreen.kt`**

1. 增加常量 `PAGE_DOCUMENT = 4`；
2. 构造 `DocumentTranslateViewModel` 并传入工厂；
3. 在 `LaunchedEffect(initialStaticTarget)` 中处理 `StaticModeTarget.DOCUMENT -> page = PAGE_DOCUMENT`；
4. 在 `MoreHub` 中增加「📄 文档翻译」卡片，点击切到 `PAGE_DOCUMENT`；
5. `when (page)` 分支渲染 `PAGE_DOCUMENT -> DocumentTranslateScreen(...)`。

- [ ] **Step 2: 验证编译与单测**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest --console=plain"`
Expected: ALL PASS.

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/more/MoreScreen.kt
git commit -m "feat(hub): 在悬浮球二级页面与通用 Hub 中激活文档翻译入口"
```

---

### Task 7: 全量验证、打包产出与版本提升

**Files:**
- Modify: `app/build.gradle.kts` (版本号升级至 v0.4.0, code+1)
- Verify: 所有单测通过
- Build: `assembleRelease`

- [ ] **Step 1: 更新版本号**

在 `app/build.gradle.kts` 中将版本号升至 `0.4.0`（设计文档 §9 规定 P0–P5 完成后升至 v0.4.0）。

- [ ] **Step 2: 运行全量单测与构建 Release APK**

Run: `export JAVA_HOME=/e/AAAtranslator/jdk && cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator :app:testDebugUnitTest assembleRelease --console=plain > E:\AAAtranslator\build_log_p5.txt 2>&1"; echo "GRADLE_EXIT=$?"`
Expected: 退出码 0，单测全部通过，产出 `app-release.apk`。

- [ ] **Step 3: 提交并记录**

```bash
git add app/build.gradle.kts
git commit -m "chore: 升级版本号至 v0.4.0，完成 P5 文档翻译"
```
