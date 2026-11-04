# ASR 模型压缩包一键导入与开源模型分流指引 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 彻底解决安卓系统对 `/Android/data/` 目录的访问限制，提供系统文件选择器「一键选择模型压缩包（.zip / .tar.bz2 / .tar.gz）自动解压、校验并部署为端侧可用模型」，并重构界面标签与说明，彻底厘清「ASR 纯手机端侧离线」与「翻译开源大模型需电脑私有化局域网运行」的职责与配置步骤。

**Architecture:** 
1. 引入 `org.apache.commons:commons-compress` 建立纯 Kotlin/Java 解压引擎 `LocalAsrArchiveImporter`，流式读取系统 Uri，兼容 `.zip`、`.tar.bz2`、`.tar.gz` 与 `.tar` 格式；
2. 内部集成就地架构指纹识别（SenseVoice/Paraformer、Whisper、Moonshine、Qwen3-ASR、FireRedASR），支持跨层级包装子目录自动下钻，解压后安全原子部署至应用内部私有目录 `context.filesDir/models/asr/<modelId>/`；
3. `SettingsViewModel` 接入协程异步导入流水线并暴露状态流，导入完成即时触发探测探针刷新；
4. `SettingsScreen` UI 提供一键选择压缩包入口与动画进度反馈，并将开源翻译大模型与 ASR 模型的徽章、提示、指南清晰分流。

**Tech Stack:** Kotlin 2.0, Android Jetpack Compose, Miuix Android, Apache Commons Compress 1.26.1, Android Storage Access Framework (SAF / ActivityResultContracts.OpenDocument), Coroutines, JUnit 4.

**Spec:** 用户反馈与需求确诊：
- 需求 1：开源翻译大模型（Sakura/Seed-X/Qwen）标注清楚是电脑/服务器局域网运行，手机无法承载百亿参数，给出详细局域网配置指引；ASR 标注清楚为手机端侧离线跑；
- 需求 2：不强求用户通过电脑 USB 或受限的 Android/data 目录手动复制，支持在手机上直接选择已下载的压缩包，App 自动解压并就地配置。

## Global Constraints
- Android 11+ (API 30+) 强制 Scoped Storage，模型文件写入必须落入 `context.filesDir/models/asr/<modelId>`，避免外部存储权限拦截；
- 解压过程必须在 `Dispatchers.IO` 异步协程中执行，且必须实时汇报进度，禁止阻塞主线程；
- 临时文件必须在 `context.cacheDir` 中创建，解压部署完成或失败后必须无条件清理临时文件，绝不残留垃圾占用手机空间；
- 保持既有 328 项单测 100% PASS，新增导入器核心逻辑覆盖全量单元测试；
- 解压防 Zip Slip 安全漏洞：目标路径必须规范化并严格受限于解压目录。

## Review Focus
1. Zip Slip / 路径遍历攻击防御（压缩包内含 `../` 相对路径）：必须验证解压目标在允许的根目录内；
2. 架构不匹配或缺文件拦截：如用户导入只有单个 txt 或缺了 `model.int8.onnx` 的损坏包，返回结构化错误且不覆盖已有可用模型；
3. 压缩包内多层嵌套子目录（如 `archive/sherpa-onnx-whisper-tiny/model/...`）：必须能自动穿透定位到实际模型根目录；
4. 取消选择或畸形 Uri：用户打开文件选择器后点击返回取消，状态正常归位 `Idle`，不触发错误弹窗；
5. 导入成功后视图探针即刻同步：`checkModelStatus()` 必须正确探测新部署的文件并置 `isReady = true`。

---

### Task 1: 核心解压器与安全文件抽取实现 (`LocalAsrArchiveImporter`)

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/data/LocalAsrArchiveImporter.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/data/LocalAsrArchiveImporterTest.kt`
- Modify: `app/build.gradle.kts` (已加入 `org.apache.commons:commons-compress:1.26.1`)

**Interfaces:**
- Produces: 
  ```kotlin
  sealed interface ModelImportResult {
      data class Success(val modelId: String, val modelName: String, val fileCount: Int, val totalBytes: Long) : ModelImportResult
      data class Failure(val reason: String) : ModelImportResult
  }
  class LocalAsrArchiveImporter {
      suspend fun importArchive(
          context: Context,
          uri: Uri,
          targetModelId: String?,
          onProgress: (String) -> Unit = {}
      ): ModelImportResult
      fun unpackStream(
          inputStream: InputStream,
          archiveName: String,
          outputDir: File,
          onProgress: (String) -> Unit = {}
      ): Int
  }
  ```

- [ ] **Step 1: 编写解压安全性与多格式解包单元测试**

在 `LocalAsrArchiveImporterTest.kt` 中编写对 `.zip`、`.tar.gz`、`.tar.bz2` 的内存构建与解包测试，验证解包出目标文件、Zip Slip 路径逃逸拦截、单层子目录穿透。

- [ ] **Step 2: 运行测试验证失败**

运行: `gradlew.bat :app:testDebugUnitTest --tests com.xzm.realtimetranslate.data.LocalAsrArchiveImporterTest`
预期: FAIL (Class not found)

- [ ] **Step 3: 实现 `LocalAsrArchiveImporter`**

在 `LocalAsrArchiveImporter.kt` 中：
1. 实现对 Zip、Tar、TarGz、TarBz2 的自适应流式识别（按文件名后缀或文件头魔数）；
2. 严格的 canonicalPath 校验拦截 `..` 路径穿越；
3. 抽取文件到指定的暂存目录；
4. 包含进度回调通知。

- [ ] **Step 4: 运行测试验证通过**

运行: `gradlew.bat :app:testDebugUnitTest --tests com.xzm.realtimetranslate.data.LocalAsrArchiveImporterTest`
预期: PASS

- [ ] **Step 5: 提交代码**

```bash
git add app/build.gradle.kts app/src/main/java/com/xzm/realtimetranslate/data/LocalAsrArchiveImporter.kt app/src/test/java/com/xzm/realtimetranslate/data/LocalAsrArchiveImporterTest.kt
git commit -m "feat(asr): 实现多格式模型压缩包流式安全解压器 LocalAsrArchiveImporter"
```

---

### Task 2: 智能架构识别与目标内部存储自动部署

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/data/LocalAsrArchiveImporter.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/data/LocalAsrModelCatalog.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/data/LocalAsrArchiveImporterTest.kt`

**Interfaces:**
- Consumes: `LocalAsrModelCatalog.PRESETS`, `LocalAsrModelCatalog.checkFilesForArch`
- Produces: `LocalAsrArchiveImporter.detectMatchingPreset(extractedDir: File, preferredId: String?): LocalAsrModelPreset?`

- [ ] **Step 1: 编写自动识别架构与部署的单测**

在 `LocalAsrArchiveImporterTest.kt` 中编写：
- 传入包含 `model.int8.onnx` + `tokens.txt` 的解压目录，测试正确匹配 `paraformer-zh`；
- 传入包含 `encoder.onnx` + `decoder.onnx` + `tokens.txt` 的目录，测试匹配 `whisper-*`；
- 验证将文件原子移动到 `filesDir/models/asr/<modelId>` 并验证其就绪状态。

- [ ] **Step 2: 运行测试验证失败**

运行: `gradlew.bat :app:testDebugUnitTest --tests com.xzm.realtimetranslate.data.LocalAsrArchiveImporterTest`
预期: FAIL

- [ ] **Step 3: 实现模型识别与部署逻辑**

1. 遍历解压目录与所有可能的一级/二级子目录；
2. 针对指定或所有 `LocalAsrModelCatalog.PRESETS` 执行架构特征文件比对；
3. 校验通过后，清理旧的同名目标目录，将有效权重完整转移至 `context.filesDir/models/asr/<matchedModelId>`；
4. 清理 `cacheDir` 临时解压目录。

- [ ] **Step 4: 运行测试验证通过**

运行: `gradlew.bat :app:testDebugUnitTest --tests com.xzm.realtimetranslate.data.LocalAsrArchiveImporterTest`
预期: PASS

- [ ] **Step 5: 提交代码**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/data/LocalAsrArchiveImporter.kt app/src/test/java/com/xzm/realtimetranslate/data/LocalAsrArchiveImporterTest.kt
git commit -m "feat(asr): 实现模型特征指纹自动识别与内部私有目录安全部署"
```

---

### Task 3: `SettingsViewModel` 集成异步导入流水线与响应式状态

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/ui/settings/SettingsViewModelTest.kt` (如有或新增导入测试)

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface ModelImportUiState {
      data object Idle : ModelImportUiState
      data class Importing(val progressMessage: String) : ModelImportUiState
      data class Success(val modelId: String, val message: String) : ModelImportUiState
      data class Error(val errorMessage: String) : ModelImportUiState
  }
  // in SettingsViewModel:
  val modelImportUiState: StateFlow<ModelImportUiState>
  fun importModelArchive(uri: Uri, targetPresetId: String?)
  fun clearModelImportState()
  ```

- [ ] **Step 1: 编写 ViewModel 导入状态流转单测**

测试 `importModelArchive` 触发后进入 `Importing`，处理完成后流转至 `Success` 或 `Error`，并在成功后触发 `checkModelStatus()`。

- [ ] **Step 2: 运行测试验证失败**

运行: `gradlew.bat :app:testDebugUnitTest --tests com.xzm.realtimetranslate.ui.settings.SettingsViewModelTest`
预期: FAIL

- [ ] **Step 3: 在 `SettingsViewModel` 中实现逻辑**

1. 声明 `_modelImportUiState = MutableStateFlow<ModelImportUiState>(ModelImportUiState.Idle)`；
2. 在 `viewModelScope.launch(Dispatchers.IO)` 中调用 `LocalAsrArchiveImporter.importArchive(...)`；
3. 捕获成功时更新 `ModelImportUiState.Success`，并同步刷新 `checkModelStatus()`；
4. 捕获失败或异常时更新 `ModelImportUiState.Error`；
5. 提供 `clearModelImportState()`。

- [ ] **Step 4: 运行测试验证通过**

运行: `gradlew.bat :app:testDebugUnitTest`
预期: 全量单测 PASS

- [ ] **Step 5: 提交代码**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsViewModel.kt
git commit -m "feat(settings): ViewModel 集成模型压缩包导入协程流水线与状态流"
```

---

### Task 4: UI 全面升级：ASR 压缩包文件选择器与平滑导入反馈

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `SettingsViewModel.modelImportUiState`, `SettingsViewModel.importModelArchive`, `SettingsViewModel.clearModelImportState`

- [ ] **Step 1: 在 `SettingsScreen.kt` ASR 展开卡片中添加导入压缩包按钮与文件选择器**

1. 使用 `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())`，设置过滤类型：`arrayOf("*/*")` 或常见压缩包 mime 类型；
2. 在「📂 手机导入外部模型说明」卡片内显眼位置增加实体主按钮：
   `📦 一键选择已下载压缩包 (.zip / .tar.bz2 / .tar.gz)`；
3. 点击直接拉起 SAF 文件选择器，选中后即刻调用 `onImportArchive(uri, currentPreset.id)`。

- [ ] **Step 2: 增加导入进度条与状态展示**

1. 当 `modelImportUiState is ModelImportUiState.Importing` 时，原位展开动态进度条与提示：`正在解压并校验模型完整性，请稍候...`；
2. 当 `Success` 时展示绿色高光完成提示，自动置为在用并支持关闭；
3. 当 `Error` 时展示红色提示框，说明具体原因（如缺少 model.onnx 等）。

- [ ] **Step 3: 运行单测验证无编译错误与破坏性改动**

运行: `gradlew.bat :app:testDebugUnitTest`
预期: PASS

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsScreen.kt
git commit -m "feat(ui): 设置页 ASR 模型卡片集成压缩包原生选择导入与进度反馈"
```

---

### Task 5: 职责分流强化：ASR「端侧离线」vs 翻译大模型「电脑私有化」徽章与配置向导

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/data/TranslationEngineDetail.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/data/LocalAsrModelCatalog.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: 升级 ASR 模型与开源翻译模型的数据描述与徽章**

1. `LocalAsrModelPreset`: 增加 `badge = "📱 手机端侧离线 · 零流量"`；并在描述中注明“纯手机端侧离线计算，无需电脑、断网可用”；
2. `OpenSourceTranslationPreset`: 增加 `badge = "💻 电脑私有化 · 局域网直连"`；并在描述中醒目强调：“大模型参数庞大，手机无法独立运行；需在电脑使用 Ollama / LM Studio 等工具运行，手机通过 Wi-Fi 局域网直连使用”；
3. 微软翻译与谷歌免费保留 `🌐 免密直连 · 开箱即用`。

- [ ] **Step 2: 升级开源翻译大模型就地展开卡片的操作引导**

在开源翻译大模型的就地配置卡片中：
1. 顶部提供高对比度提示横幅：
   `💡 运行说明：本模型需在电脑/服务器上运行（如 Ollama）。请确保电脑和手机在同一 Wi-Fi 下，下方填入电脑 IP 即可免费调用电脑算力。`
2. 提供电脑 IP 快速填写示例与一键预填默认端点按钮；
3. 提供「常见局域网连接排错指南」（检查防火墙、检查 OLLAMA_HOST=0.0.0.0:11434）。

- [ ] **Step 3: 运行单测验证**

运行: `gradlew.bat :app:testDebugUnitTest`
预期: PASS

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/data/TranslationEngineDetail.kt app/src/main/java/com/xzm/realtimetranslate/data/LocalAsrModelCatalog.kt app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsScreen.kt
git commit -m "feat(ui): 强化 ASR 手机端侧离线与翻译大模型电脑私有化分流徽章与引导"
```

---

### Task 6: 全链路单测验证、assembleRelease 打包与真机安装

**Files:**
- Verify: 全部修改文件
- Output: `LiveTranslate-release.apk`

- [ ] **Step 1: 运行全量单元测试**

运行: `gradlew.bat :app:testDebugUnitTest --console=plain`
预期: 全量单测通过，0 失败。

- [ ] **Step 2: 编译 Release 安装包**

运行: `cmd //c "E:\AAAtranslator\gradlew.bat -p E:\AAAtranslator assembleRelease --console=plain > E:\AAAtranslator\build_log_import.txt 2>&1"`
预期: BUILD SUCCESSFUL，拷贝覆盖至 `E:\AAAtranslator\LiveTranslate-release.apk`。

- [ ] **Step 3: 验证 Release APK V2 签名**

运行: `apksigner.bat verify --print-certs E:\AAAtranslator\LiveTranslate-release.apk`
预期: Verified using v2 scheme (true)。

- [ ] **Step 4: 推送至真机并拉起验证**

运行: `adb -s U8VCZHFATWHUI7AU install -r -d E:\AAAtranslator\LiveTranslate-release.apk`
预期: Success，应用前台正常拉起，无崩溃。

- [ ] **Step 5: 最终提交**

```bash
git commit -m "chore: 完成 ASR 压缩包一键导入与开源模型分流指引交付"
```
