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
