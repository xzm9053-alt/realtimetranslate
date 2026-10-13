# 独立闭源软件·版本更新检测与视频引流体系 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为应用构建轻量级无状态版本更新检测与视频引流弹窗体系，支持启动静默检测、设置页手动检查、一键唤起 B 站新视频，并配有自动化云端更新脚本。

**Architecture:** 客户端通过 OkHttp 异步读取对象存储 CDN 上的纯净 `version.json`，比对 `BuildConfig.VERSION_CODE`。若有新版，呈现 MIUIX 风格更新弹窗并通过 `Intent.ACTION_VIEW` 跳转至新视频；提供独立脚本供作者一条命令完成云端版本升级。

**Tech Stack:** Kotlin, Jetpack Compose, MIUIX UI, OkHttp, kotlinx.serialization, Python (腾讯云 COS SDK / 脚本).

**Spec:** `docs/superpowers/specs/2026-10-13-in-app-update-design.md`

## Global Constraints

- **严禁暴露源码或 GitHub 痕迹**：仅请求用户指定的纯净 CDN JSON 链接，不引用 GitHub API。
- **容错优先**：更新检测属于边缘辅助功能，任何网络故障、DNS 失败、超时或 JSON 畸变必须被静默捕获，绝不影响应用主流程。
- **零误打扰**：本地版本号等于或大于云端版本号时完全静默；同一会话内点击“稍后提醒”后不再重复弹窗。
- **UI 风格统一**：弹窗必须符合应用整体 MIUIX 设计语言（圆角、配色适配深浅主题）。

## Review Focus

1. **畸变或缺少字段的 JSON 响应**：容错默认值生效，避免反序列化崩溃抛出未捕获异常。
2. **极端超时与无网络环境**：OkHttp 设置 3 秒短超时，请求失败平稳转为 `Failed` 状态并写入 `AppLog`，静默忽略。
3. **空或非法 videoUrl 跳转**：点击按钮时做 URL 合法性防御校验与 `ActivityNotFoundException` 捕获，不崩应用。
4. **版本号比对边界（相等、降级）**：云端 `versionCode <= localVersionCode` 均认定为无更新。
5. **多次重复点击“检查新版本”**：通过状态锁（`checking` 标志位）防重复抖动点击。

---

### Task 1: 数据模型与解析定义 (UpdateModel.kt)

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/update/UpdateModel.kt`
- Create: `app/src/test/java/com/xzm/realtimetranslate/update/UpdateModelTest.kt`

**Interfaces:**
- Produces:
  - `data class RemoteUpdateInfo(val versionCode: Int, val versionName: String, val title: String, val message: String, val videoUrl: String, val forceUpdate: Boolean = false)`
  - `sealed interface UpdateCheckResult` (`HasUpdate`, `AlreadyLatest`, `Failed`)

- [ ] **Step 1: 编写数据模型与解析的单元测试**

```kotlin
package com.xzm.realtimetranslate.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateModelTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun parseValidJson_returnsCorrectModel() {
        val raw = """
            {
                "versionCode": 2,
                "versionName": "v0.2.0",
                "title": "新版发布",
                "message": "更新说明",
                "videoUrl": "https://b23.tv/demo",
                "forceUpdate": false
            }
        """.trimIndent()
        val info = json.decodeFromString<RemoteUpdateInfo>(raw)
        assertEquals(2, info.versionCode)
        assertEquals("v0.2.0", info.versionName)
        assertEquals("新版发布", info.title)
        assertEquals("更新说明", info.message)
        assertEquals("https://b23.tv/demo", info.videoUrl)
        assertFalse(info.forceUpdate)
    }

    @Test
    fun parseJsonWithMissingOptionalFields_usesDefaults() {
        val raw = """
            {
                "versionCode": 3,
                "versionName": "v0.3.0",
                "videoUrl": "https://b23.tv/demo"
            }
        """.trimIndent()
        val info = json.decodeFromString<RemoteUpdateInfo>(raw)
        assertEquals(3, info.versionCode)
        assertEquals("https://b23.tv/demo", info.videoUrl)
        assertEquals("发现新版本", info.title)
        assertEquals("", info.message)
        assertFalse(info.forceUpdate)
    }
}
```

- [ ] **Step 2: 运行测试以确认其失败**

运行：`./gradlew testDebugUnitTest --tests "com.xzm.realtimetranslate.update.UpdateModelTest"`
预期结果：编译失败（`RemoteUpdateInfo` 未定义）。

- [ ] **Step 3: 编写 UpdateModel.kt 实现**

```kotlin
package com.xzm.realtimetranslate.update

import kotlinx.serialization.Serializable

@Serializable
data class RemoteUpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val title: String = "发现新版本",
    val message: String = "",
    val videoUrl: String = "",
    val forceUpdate: Boolean = false,
)

sealed interface UpdateCheckResult {
    data class HasUpdate(val info: RemoteUpdateInfo) : UpdateCheckResult
    data object AlreadyLatest : UpdateCheckResult
    data class Failed(val reason: String) : UpdateCheckResult
}
```

- [ ] **Step 4: 运行测试验证通过**

运行：`./gradlew testDebugUnitTest --tests "com.xzm.realtimetranslate.update.UpdateModelTest"`
预期结果：BUILD SUCCESSFUL，所有用例通过。

- [ ] **Step 5: 提交代码**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/update/UpdateModel.kt app/src/test/java/com/xzm/realtimetranslate/update/UpdateModelTest.kt
git commit -m "feat(update): 定义 RemoteUpdateInfo 与 UpdateCheckResult 数据模型"
```

---

### Task 2: 网络检查核心逻辑 (UpdateChecker.kt)

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/update/UpdateChecker.kt`
- Create: `app/src/test/java/com/xzm/realtimetranslate/update/UpdateCheckerTest.kt`

**Interfaces:**
- Consumes: `RemoteUpdateInfo`, `UpdateCheckResult`
- Produces: `class UpdateChecker(private val client: OkHttpClient = defaultClient)`
  - `fun evaluateUpdate(remoteInfo: RemoteUpdateInfo, currentVersionCode: Int): UpdateCheckResult`
  - `suspend fun checkForUpdate(url: String, currentVersionCode: Int): UpdateCheckResult`

- [ ] **Step 1: 编写版本对比与解析逻辑的单元测试**

```kotlin
package com.xzm.realtimetranslate.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val checker = UpdateChecker()

    @Test
    fun evaluateUpdate_whenRemoteIsHigher_returnsHasUpdate() {
        val remote = RemoteUpdateInfo(versionCode = 5, versionName = "v0.5.0")
        val result = checker.evaluateUpdate(remote, currentVersionCode = 4)
        assertTrue(result is UpdateCheckResult.HasUpdate)
        assertEquals(5, (result as UpdateCheckResult.HasUpdate).info.versionCode)
    }

    @Test
    fun evaluateUpdate_whenRemoteIsEqualOrLower_returnsAlreadyLatest() {
        val remoteEqual = RemoteUpdateInfo(versionCode = 4, versionName = "v0.4.0")
        assertEquals(UpdateCheckResult.AlreadyLatest, checker.evaluateUpdate(remoteEqual, currentVersionCode = 4))

        val remoteLower = RemoteUpdateInfo(versionCode = 3, versionName = "v0.3.0")
        assertEquals(UpdateCheckResult.AlreadyLatest, checker.evaluateUpdate(remoteLower, currentVersionCode = 4))
    }
}
```

- [ ] **Step 2: 运行测试以确认失败**

运行：`./gradlew testDebugUnitTest --tests "com.xzm.realtimetranslate.update.UpdateCheckerTest"`
预期结果：编译失败（`UpdateChecker` 未定义）。

- [ ] **Step 3: 编写 UpdateChecker.kt 实现**

```kotlin
package com.xzm.realtimetranslate.update

import com.xzm.realtimetranslate.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdateChecker(
    private val client: OkHttpClient = defaultOkHttpClient,
    private val json: Json = defaultJson,
) {
    fun evaluateUpdate(remoteInfo: RemoteUpdateInfo, currentVersionCode: Int): UpdateCheckResult {
        return if (remoteInfo.versionCode > currentVersionCode) {
            UpdateCheckResult.HasUpdate(remoteInfo)
        } else {
            UpdateCheckResult.AlreadyLatest
        }
    }

    suspend fun checkForUpdate(
        url: String,
        currentVersionCode: Int,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext UpdateCheckResult.Failed("Update URL is not configured")
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.w(TAG, "Update check HTTP error: ${response.code}")
                    return@withContext UpdateCheckResult.Failed("HTTP ${response.code}")
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    return@withContext UpdateCheckResult.Failed("Empty response")
                }

                val remoteInfo = json.decodeFromString<RemoteUpdateInfo>(body)
                evaluateUpdate(remoteInfo, currentVersionCode)
            }
        } catch (e: IOException) {
            AppLog.w(TAG, "Network failure checking update: ${e.message}")
            UpdateCheckResult.Failed("Network failure: ${e.message}")
        } catch (e: Exception) {
            AppLog.e(TAG, "Unexpected error parsing update info: ${e.message}", e)
            UpdateCheckResult.Failed("Parse failure: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "UpdateChecker"

        /** 默认更新配置 CDN 地址（预置地址，亦可从设置动态读取） */
        const val DEFAULT_UPDATE_URL = "https://live-translate-update-1250000000.cos.ap-shanghai.myqcloud.com/translate/version.json"

        private val defaultOkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()

        private val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

运行：`./gradlew testDebugUnitTest --tests "com.xzm.realtimetranslate.update.UpdateCheckerTest"`
预期结果：BUILD SUCCESSFUL，所有用例通过。

- [ ] **Step 5: 提交代码**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/update/UpdateChecker.kt app/src/test/java/com/xzm/realtimetranslate/update/UpdateCheckerTest.kt
git commit -m "feat(update): 实现 OkHttp 异步版本检查器 UpdateChecker"
```

---

### Task 3: MIUIX 风格更新弹窗组件 (UpdateDialog.kt)

**Files:**
- Create: `app/src/main/java/com/xzm/realtimetranslate/ui/components/UpdateDialog.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `RemoteUpdateInfo`
- Produces: `@Composable fun UpdateDialog(info: RemoteUpdateInfo, onDismiss: () -> Unit, onGoToVideo: (String) -> Unit)`

- [ ] **Step 1: 在 strings.xml 中添加更新相关文案**

```xml
    <!-- App update strings -->
    <string name="update_check_title">检查新版本</string>
    <string name="update_checking">正在检查更新…</string>
    <string name="update_already_latest">当前已是最新版本</string>
    <string name="update_check_failed">检查更新失败，请稍后重试</string>
    <string name="update_action_watch_video">去看看新视频</string>
    <string name="update_action_later">稍后提醒</string>
    <string name="update_current_version">当前版本：%1$s (Build %2$d)</string>
    <string name="update_new_version">最新版本：%1$s</string>
```

- [ ] **Step 2: 实现 UpdateDialog.kt**

```kotlin
package com.xzm.realtimetranslate.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.update.RemoteUpdateInfo
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UpdateDialog(
    info: RemoteUpdateInfo,
    onDismiss: () -> Unit,
    onGoToVideo: (String) -> Unit,
) {
    SuperDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = info.title.ifBlank { stringResource(R.string.update_check_title) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            if (info.versionName.isNotBlank()) {
                Text(
                    text = stringResource(R.string.update_new_version, info.versionName),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (info.message.isNotBlank()) {
                Text(
                    text = info.message,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!info.forceUpdate) {
                TextButton(
                    text = stringResource(R.string.update_action_later),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            TextButton(
                text = stringResource(R.string.update_action_watch_video),
                onClick = { onGoToVideo(info.videoUrl) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

- [ ] **Step 3: 验证编译通过**

运行：`./gradlew compileDebugKotlin`
预期结果：BUILD SUCCESSFUL。

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/xzm/realtimetranslate/ui/components/UpdateDialog.kt
git commit -m "feat(ui): 增加符合 MIUIX 规范的 UpdateDialog 更新引流弹窗"
```

---

### Task 4: 应用主流程集成 (MainActivity 与 SettingsScreen)

**Files:**
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/main/MainActivity.kt`
- Modify: `app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsScreen.kt`

**Interfaces:**
- `MainActivity`: 启动时后台检测，若有更新触发状态弹窗，点击跳转使用 `Intent.ACTION_VIEW` 优先拉起 B 站或浏览器。
- `SettingsScreen`: 在关于卡片添加「检查新版本」可点击项，展示加载状态与结果 Toast。

- [ ] **Step 1: 在 MainActivity 中集成启动静默检测与弹窗控制**

在 `MainActivity.setContent` 内：
1. 维护 `var updateInfoToPrompt by remember { mutableStateOf<RemoteUpdateInfo?>(null) }`
2. `LaunchedEffect(Unit)` 异步调用 `UpdateChecker().checkForUpdate(UpdateChecker.DEFAULT_UPDATE_URL, BuildConfig.VERSION_CODE)`
3. 若结果为 `UpdateCheckResult.HasUpdate(info)`，设置 `updateInfoToPrompt = info`。
4. 渲染 `if (updateInfo != null) UpdateDialog(...)`。
5. 跳转方法：
```kotlin
private fun openVideoUrl(url: String) {
    if (url.isBlank()) return
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    } catch (e: Exception) {
        AppLog.w("MainActivity", "Failed to open video url: $url", e)
    }
}
```

- [ ] **Step 2: 在 SettingsScreen 中集成手动检查入口**

在 `SettingsScreen.kt` 的版本号卡片区域添加：
1. 维护检查状态：`var isCheckingUpdate by remember { mutableStateOf(false) }`
2. 提供「检查新版本」项：
   - 点击时若 `!isCheckingUpdate`，置为 `true`。
   - 协程调用 `UpdateChecker().checkForUpdate(...)`。
   - 结果处理：
     - `HasUpdate`: 调起传入的回调弹出 `UpdateDialog`。
     - `AlreadyLatest`: Toast 提示“当前已是最新版本”。
     - `Failed`: Toast 提示“检查更新失败，请稍后重试”。
   - 结束置 `isCheckingUpdate = false`。

- [ ] **Step 3: 运行完整编译与单元测试**

运行：`./gradlew testDebugUnitTest assembleDebug`
预期结果：BUILD SUCCESSFUL。

- [ ] **Step 4: 提交代码**

```bash
git add app/src/main/java/com/xzm/realtimetranslate/ui/main/MainActivity.kt app/src/main/java/com/xzm/realtimetranslate/ui/settings/SettingsScreen.kt
git commit -m "feat(update): 集成启动静默检查与设置页手动更新入口"
```

---

### Task 5: 自动化云端更新脚本 (scripts/push_update.py)

**Files:**
- Create: `scripts/push_update.py`
- Create: `scripts/README.md`

**Interfaces:**
- Produces: CLI 脚本，支持通过命令行一键更新指定云端 `version.json`，或在无密钥时导出标准 JSON 文件。

- [ ] **Step 1: 编写 scripts/push_update.py**

```python
#!/usr/bin/env python3
"""
一键更新与推送 version.json 到腾讯云 COS / 阿里云 OSS。
用法：
  python scripts/push_update.py --code 2 --name "v0.2.0" --url "https://b23.tv/xxx" --msg "全新版本已上线！"
"""

import argparse
import json
import os
import sys

def main():
    parser = argparse.ArgumentParser(description="发布新版本更新通知到云端")
    parser.add_argument("--code", type=int, required=True, help="最新版本代码 (versionCode, 整数)")
    parser.add_argument("--name", type=str, required=True, help="最新版本名称 (如 v0.2.0)")
    parser.add_argument("--url", type=str, required=True, help="引流跳转视频链接 (如 https://b23.tv/xxx)")
    parser.add_argument("--title", type=str, default="🎉 发现全新版本！", help="弹窗标题")
    parser.add_argument("--msg", type=str, default="优化了核心体验，快来看看 UP 主的最新演示视频吧！", help="更新说明")
    parser.add_argument("--output", type=str, default="version.json", help="本地导出文件路径")
    parser.add_argument("--upload", action="store_true", help="是否直接通过腾讯云 COS 上传")

    args = parser.parse_args()

    payload = {
        "versionCode": args.code,
        "versionName": args.name,
        "title": args.title,
        "message": args.msg,
        "videoUrl": args.url,
        "forceUpdate": False
    }

    content = json.dumps(payload, ensure_ascii=False, indent=2)
    with open(args.output, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"✅ 已成功生成本地版本配置文件: {args.output}")
    print(content)

    if args.upload:
        secret_id = os.environ.get("TENCENT_COS_SECRET_ID")
        secret_key = os.environ.get("TENCENT_COS_SECRET_KEY")
        bucket = os.environ.get("TENCENT_COS_BUCKET")
        region = os.environ.get("TENCENT_COS_REGION", "ap-shanghai")

        if not (secret_id and secret_key and bucket):
            print("⚠️ 未检测到 TENCENT_COS_SECRET_ID/SECRET_KEY 环境变量，已保留本地文件，请手动上传至存储桶。")
            return

        try:
            from qcloud_cos import CosConfig, CosS3Client
            config = CosConfig(Region=region, SecretId=secret_id, SecretKey=secret_key)
            client = CosS3Client(config)
            client.put_object(
                Bucket=bucket,
                Body=content.encode("utf-8"),
                Key="translate/version.json",
                ContentType="application/json"
            )
            print("🚀 已成功自动推送覆盖到腾讯云 COS！全网生效。")
        except ImportError:
            print("⚠️ 未安装 cos-python-sdk-v5 库。可通过 pip install -U cos-python-sdk-v5 安装后直接一键上传。")
        except Exception as e:
            print(f"❌ 上传失败: {e}")

if __name__ == "__main__":
    main()
```

- [ ] **Step 2: 编写 scripts/README.md 使用说明**

说明环境变量设置与使用方法。

- [ ] **Step 3: 运行脚本自测验证**

运行：`python scripts/push_update.py --code 2 --name "v0.2.0" --url "https://b23.tv/test" --output test_version.json`
验证生成内容正确后删除临时测试文件。

- [ ] **Step 4: 提交代码**

```bash
git add scripts/push_update.py scripts/README.md
git commit -m "feat(scripts): 增加一键版本发布推送自动化脚本"
```
