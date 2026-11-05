# 视频翻译双引擎随全局与独立覆写及全应用智能源语言联动 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现视频翻译中的「翻译大模型」与「语音识别 ASR 模型」默认跟随全局设置，同时支持在视频翻译界面中独立切换与覆写，并打通全应用（首页同传、视频翻译、设置页）基于当前在用 ASR 模型能力的「智能源语言联动与自动纠错」。

**Architecture:** 
1. 在 `SupportedLanguages` 中建立统一的 ASR 模型能力过滤器 `filterSourceOptionsForModel` / `filterSourceOptions` 与语言代码安全纠错 `sanitizeSourceLanguage`；
2. 将 `SenseVoiceBatchRecognizer` 升级重构为通用的全架构批处理识别器 `LocalBatchRecognizer`，支持 SenseVoice、Paraformer、Whisper 各版本、Moonshine、Qwen3-ASR、FireRedASR 全部 6 大架构在视频提取中的批量流式识别；
3. `VideoTranslateViewModel` 响应式监听全局设置，维护 `overrideTranslationEngine` 与 `overrideLocalAsrModelId`，动态合成有效执行配置并驱动翻译与转写流水线；
4. `VideoTranslateScreen` 新增一体化翻译大模型与 ASR 模型卡片（支持所见即所配与一键恢复跟随全局），并在源语言点选时按当前识别模型能力智能动态过滤；
5. 首页 `SubtitleScreen` 与 `SettingsScreen` 同步接入源语言联动过滤，彻底杜绝选了纯中文模型却误设外语识别导致的静默失败。

**Tech Stack:** Kotlin 2.0, Android Jetpack Compose, Miuix Android, sherpa-onnx OfflineRecognizer, JUnit 4.

**Spec:**
- 需求 1（智能源语言联动）：
  - 纯中文模型（Paraformer、FireRedASR）：源语言锁定为中文（简体/繁体）；
  - 纯英文模型（Moonshine）：源语言锁定为英语；
  - 轻量多语种模型（SenseVoice）：提供自动、中文、英语、日语、韩语；
  - 全能多语种（Whisper 各版本、Qwen3-ASR、云端 Whisper）：开放全部 22 种全球主流语言；
  - 切换模型时，若原语言不被新模型支持，自动回退纠偏至首选可用语种；全应用首页、视频翻译、设置页一致生效。
- 需求 2（视频翻译双引擎联动与独立覆写）：
  - 翻译大模型与语音识别模型默认 100% 继承全局设置；
  - 视频翻译界面清晰展示当前在用的全局引擎，并提供就地覆写切换胶囊；
  - 覆写状态提供显眼的「↺ 恢复随全局」重置入口；
  - 本地识别全量支持 8 大已下载部署的离线模型。

## Global Constraints
- 严格保持现有 335 项单元测试 100% PASS，新增逻辑均需配齐 JVM 纯单元测试；
- 解码依然受 `supportsDraft = false` 规则保护，批处理识别只在整段提取时按段执行；
- 当所选本地模型未就绪时，保持平滑降级（Fallback）至内置 SenseVoice，严禁抛异常崩溃。

## Review Focus
1. 语言代码规范化映射（如 `zh-Hans` 与 `zh-Hant` 均应被 `zh` 支持模型接纳）：必须验证映射正确；
2. 模型切换后语言自动纠偏：原选 `ja`，切至 `paraformer-zh` 时，源语言必须自动变更为 `zh-Hans`，不残留非法值；
3. 视频翻译中「恢复随全局」后全局变动即时同步：清除覆写后，全局切换引擎，视频页应立即响应；
4. 仅识别模式与翻译引擎切换互斥：当选择「仅识别不翻译」引擎或开关打开时，不触发 API Key 校验；
5. `LocalBatchRecognizer` 资源安全释放：每个视频任务处理完毕或中途取消时，native `OfflineRecognizer` 必须安全 `release()`。

---

### Task 1: 智能源语言联动与过滤纠偏核心引擎 (`SupportedLanguages`)

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/data/UserSettings.kt` (SupportedLanguages)
- Test: `app/src/test/java/com/xzm/realtimetranslate/data/SupportedLanguagesTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object SupportedLanguages {
      fun filterSourceOptionsForModel(preset: LocalAsrModelPreset?): List<LanguageOption>
      fun filterSourceOptions(asrEngineType: AsrEngineType, localPreset: LocalAsrModelPreset?): List<LanguageOption>
      fun sanitizeSourceLanguage(currentCode: String, availableOptions: List<LanguageOption>): String
  }
  ```

- [ ] **Step 1: 编写源语言智能过滤与纠偏单元测试**

在 `SupportedLanguagesTest.kt` 中编写：
1. `Paraformer` / `FireRedASR` 过滤：仅包含 `zh-Hans`, `zh-Hant`，不含 `auto` 与其他外语；
2. `Moonshine` 过滤：仅包含 `en`；
3. `SenseVoice` 过滤：仅包含 `auto`, `zh-Hans`, `zh-Hant`, `en`, `ja`, `ko`；
4. `Whisper` / `Qwen3-ASR` / `OpenAI Cloud` 过滤：包含完整全部 22 种语言及 `auto`；
5. `sanitizeSourceLanguage` 测试：原选 `ja` 在 Paraformer 下纠正为 `zh-Hans`；原选 `es` 在 SenseVoice 下纠正为 `auto`；合法语言保持不变。

- [ ] **Step 2: 运行测试验证失败**

运行: `PowerShell: $env:JAVA_HOME = "E:\AAAtranslator\jdk"; & "E:\AAAtranslator\gradlew.bat" -p "E:\AAAtranslator" testDebugUnitTest --tests com.xzm.realtimetranslate.data.SupportedLanguagesTest --console=plain`
预期: FAIL (方法未实现)

- [ ] **Step 3: 在 `SupportedLanguages` 中实现过滤与纠偏算法**

在 `UserSettings.kt` 的 `SupportedLanguages` 单例中实现 `filterSourceOptionsForModel`、`filterSourceOptions` 与 `sanitizeSourceLanguage`。

- [ ] **Step 4: 运行测试验证通过**

运行测试，验证全部 PASS。

- [ ] **Step 5: 提交代码**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/data/UserSettings.kt app/src/test/java/com/xzm/realtimetranslate/data/SupportedLanguagesTest.kt
git commit -m "feat(asr): 实现基于 ASR 模型能力的智能源语言过滤与自动纠错引擎"
```

---

### Task 2: 通用多架构离线批处理识别器 (`LocalBatchRecognizer`)

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/video/LocalBatchRecognizer.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/video/LocalBatchRecognizerTest.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/video/SenseVoiceBatchRecognizer.kt` (保留或别名委托以兼容老测试)

**Interfaces:**
- Produces:
  ```kotlin
  class LocalBatchRecognizer(
      preset: LocalAsrModelPreset,
      probeResult: LocalAsrProbeResult?,
      fallbackModelDir: File,
      language: String = "auto",
      numThreads: Int = 2,
  ) : BatchSpeechRecognizer
  ```

- [ ] **Step 1: 编写 `LocalBatchRecognizer` 架构配置与纯逻辑单元测试**

在 `LocalBatchRecognizerTest.kt` 中编写：
1. 语言代码到底层识别器 language tag 转换测试（Whisper、SenseVoice 规范化）；
2. 文本清洗测试（SenseVoice 标签剔除、通用空白/噪声过滤）；
3. Fallback 回退标记校验（当 probeResult 缺失时安全置为 SenseVoice 回退模式）。

- [ ] **Step 2: 运行测试验证失败**

运行测试，预期 FAIL (Class not found)。

- [ ] **Step 3: 实现 `LocalBatchRecognizer`**

在 `LocalBatchRecognizer.kt` 中：
1. 复用 `AsrEngine` 的架构分派逻辑，构建 `OfflineModelConfig`（覆盖 SENSE_VOICE、PARAFORMER、WHISPER、MOONSHINE、QWEN3_ASR、FIRE_RED_ASR 6 大架构）；
2. 捕获未就绪情况，自动平滑回退至内置 SenseVoice；
3. 实现 `recognize(samples: FloatArray)` 与 `close()` 释放 native 资源。

- [ ] **Step 4: 运行测试验证通过**

运行测试，验证全部 PASS。

- [ ] **Step 5: 提交代码**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/video/LocalBatchRecognizer.kt app/src/test/java/com/xzm/realtimetranslate/video/LocalBatchRecognizerTest.kt
git commit -m "feat(video): 实现支持 8 大端侧 ASR 模型的全架构离线批处理器 LocalBatchRecognizer"
```

---

### Task 3: `VideoTranslateViewModel` 接入双引擎响应式随全局与独立覆写

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/video/VideoTranslateViewModel.kt`
- Test: `app/src/test/java/com/xzm/realtimetranslate/ui/video/VideoTranslateUiStateTest.kt`

**Interfaces:**
- Produces in `VideoTranslateUiState`:
  ```kotlin
  val globalTranslationEngine: TranslationEngineType
  val overrideTranslationEngine: TranslationEngineType?
  val effectiveTranslationEngine: TranslationEngineType get() = overrideTranslationEngine ?: globalTranslationEngine
  val globalLocalAsrModelId: String
  val overrideLocalAsrModelId: String?
  val effectiveLocalAsrModelId: String get() = overrideLocalAsrModelId ?: globalLocalAsrModelId
  val isOverridden: Boolean get() = overrideTranslationEngine != null || overrideLocalAsrModelId != null
  val availableSourceOptions: List<LanguageOption>
  ```
- Produces in `VideoTranslateViewModel`:
  ```kotlin
  fun setOverrideTranslationEngine(engine: TranslationEngineType?)
  fun setOverrideLocalAsrModel(modelId: String?)
  fun resetOverrides()
  ```

- [ ] **Step 1: 编写 ViewModel 状态联动与覆写单测**

在 `VideoTranslateUiStateTest.kt` 中编写：
1. 默认状态跟随全局引擎（`overrideTranslationEngine == null` 时 `effectiveTranslationEngine == globalTranslationEngine`）；
2. 独立覆写生效测试（设置 override 后 effective 改变，且 `isOverridden == true`）；
3. 恢复跟随全局测试（`resetOverrides` 后清空 override 状态）；
4. 源语言联动测试：切换至 Paraformer 时源语言自动纠偏。

- [ ] **Step 2: 运行测试验证失败**

运行测试，预期编译错误或断言失败。

- [ ] **Step 3: 在 `VideoTranslateViewModel` 中实现逻辑**

1. 连续 `collect` 全局 `settingsRepository.settings`，实时同步 `globalTranslationEngine` 与 `globalLocalAsrModelId`；
2. 实现 `setOverrideTranslationEngine`、`setOverrideLocalAsrModel` 与 `resetOverrides`；
3. 在模型变化时自动通过 `SupportedLanguages.filterSourceOptionsForModel` 重新计算 `availableSourceOptions` 并自动纠偏 `sourceCode`；
4. 在 `start()` 中采用 `effectiveTranslationEngine` 与 `effectiveLocalAsrModelId` 创建 `LocalBatchRecognizer` 与 `TranslationEngineFactory`。

- [ ] **Step 4: 运行测试验证通过**

运行测试，验证全部 PASS。

- [ ] **Step 5: 提交代码**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/video/VideoTranslateViewModel.kt app/src/test/java/com/xzm/realtimetranslate/ui/video/VideoTranslateUiStateTest.kt
git commit -m "feat(video): ViewModel 支持双引擎响应式随全局与独立覆写流水线"
```

---

### Task 4: 视频翻译 UI 重构：双引擎卡片展示、覆写胶囊与智能源语言弹窗 (`VideoTranslateScreen`)

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/video/VideoTranslateScreen.kt`

- [ ] **Step 1: 在 `VideoTranslateScreen` 中加入「翻译大模型」与「语音识别」双卡片与覆写组件**

1. 翻译大模型卡片：展示当前在用引擎标题与徽章（带 `(全局默认)` 或 `(自定)` 标记）；提供常用引擎横向切换胶囊，支持切回全局；
2. 语音识别卡片：展示当前在用 ASR 模型名称（带 `(全局默认)` 或 `(自定)` 标记），支持在本地 8 大模型与云端 Whisper 之间点击切换；
3. 右上角提供显眼的「↺ 恢复随全局」操作按钮（仅在有覆写时显示）；
4. 源语言选择器根据 `state.availableSourceOptions` 动态呈现，支持过滤展示。

- [ ] **Step 2: 顶部特性徽章动态化**

将顶部静态写死的 `⚡ 端侧 SenseVoice` 改为动态展示当前在用的识别模型名称（如 `⚡ 端侧 Paraformer`、`🌐 端侧 Whisper Turbo`）。

- [ ] **Step 3: 运行全量单元测试验证**

运行: `PowerShell: $env:JAVA_HOME = "E:\AAAtranslator\jdk"; & "E:\AAAtranslator\gradlew.bat" -p "E:\AAAtranslator" testDebugUnitTest --console=plain`
预期: 全部 PASS。

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/video/VideoTranslateScreen.kt
git commit -m "feat(ui): 视频翻译界面集成双引擎随全局展示、独立覆写与源语言动态联动"
```

---

### Task 5: 首页声音同传与设置页打通智能源语言联动

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/subtitle/SubtitleScreen.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: 首页声音同传源语言弹窗过滤**

在 `SubtitleScreen.kt` 的 `LangPicker.Source` 分支中：
1. 依据当前 `settings.asrEngine` 与 `settings.localAsrModel` 调用 `SupportedLanguages.filterSourceOptions(...)`；
2. 弹窗只展示当前 ASR 模型支持的源语言列表；
3. 如果当前存储的 `settings.sourceLanguageCode` 不在支持列表中，进入页面或打开弹窗时自动执行纠偏。

- [ ] **Step 2: 设置页切换 ASR 模型时自动纠偏源语言**

在 `SettingsScreen.kt` 中，当用户点击选中新的本地 ASR 模型（如从 SenseVoice 切到 Paraformer）时，自动校验当前源语言是否受支持，若不支持自动纠偏为首选语言并 Toast 提示。

- [ ] **Step 3: 运行全量单元测试验证**

运行测试，预期 100% PASS。

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/subtitle/SubtitleScreen.kt app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsScreen.kt
git commit -m "feat(ui): 首页声音同传与设置页全面打通 ASR 模型智能源语言联动与自动纠错"
```

---

### Task 6: 全链路单测验证、assembleRelease 打包与真机安装

**Files:**
- Verify: 全部修改文件
- Output: `LiveTranslate-release.apk`

- [ ] **Step 1: 运行全量单元测试**

运行: `PowerShell: $env:JAVA_HOME = "E:\AAAtranslator\jdk"; & "E:\AAAtranslator\gradlew.bat" -p "E:\AAAtranslator" testDebugUnitTest --console=plain`
预期: 335+ 项单测全绿，0 失败。

- [ ] **Step 2: 编译 Release 安装包**

运行: `PowerShell: $env:JAVA_HOME = "E:\AAAtranslator\jdk"; & "E:\AAAtranslator\gradlew.bat" -p "E:\AAAtranslator" assembleRelease --console=plain`
预期: BUILD SUCCESSFUL，拷贝覆盖至 `E:\AAAtranslator\LiveTranslate-release.apk`。

- [ ] **Step 3: 验证 Release APK V2 签名**

运行: `& "E:\AAAtranslator\android-sdk\build-tools\37.0.0\apksigner.bat" verify --print-certs "E:\AAAtranslator\LiveTranslate-release.apk"`
预期: Verified using v2 scheme (true)。

- [ ] **Step 4: 推送至真机并拉起验证**

运行: `& "E:\AAAtranslator\android-sdk\platform-tools\adb.exe" -s U8VCZHFATWHUI7AU install -r -d "E:\AAAtranslator\LiveTranslate-release.apk"`
预期: Success，应用正常启动聚焦。

- [ ] **Step 5: 最终交付与提交**

```bash
git commit -m "chore: 完成视频翻译双引擎随全局与独立覆写及全应用源语言联动交付"
```
