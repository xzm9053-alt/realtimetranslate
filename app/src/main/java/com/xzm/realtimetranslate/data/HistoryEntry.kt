package com.xzm.realtimetranslate.data

import kotlinx.serialization.Serializable

/** One finished subtitle session, persisted in filesDir/history.json. */
@Serializable
data class HistoryEntry(
    val stoppedAtEpochMs: Long,
    val inputFull: String,
    val outputFull: String,
)
