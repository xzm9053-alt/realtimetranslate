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

        /**
         * 默认更新配置 CDN 地址。
         * 作者可在对象存储中直接维护 translate/version.json。
         */
        const val DEFAULT_UPDATE_URL = "https://aaatranslator-1505472964.cos.ap-beijing.myqcloud.com/translate/version.json"

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
