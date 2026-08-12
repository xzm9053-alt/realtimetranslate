package com.xzm.realtimetranslate.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistent history of finished subtitle sessions, stored as a JSON array in
 * [historyFile] under filesDir. Not encrypted (transcripts aren't secrets).
 *
 * The repository owns a process-scoped IO coroutine scope so writes survive
 * past the moment the SubtitleSessionService is destroyed (its own ioScope is
 * cancelled in onDestroy) — [append] is fire-and-forget.
 */
class HistoryRepository(private val context: Context) {

    private val historyFile: File = File(context.filesDir, "history.json")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    init {
        // Load in the background so the main thread never touches the file.
        scope.launch { _entries.value = load() }
    }

    /** Fire-and-forget: newest first, capped at [MAX_ENTRIES]. */
    fun append(entry: HistoryEntry) {
        scope.launch {
            mutex.withLock {
                _entries.value = (_entries.value + entry)
                    .sortedByDescending { it.stoppedAtEpochMs }
                    .take(MAX_ENTRIES)
                persist(_entries.value)
            }
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                _entries.value = emptyList()
                persist(emptyList())
            }
        }
    }

    private fun load(): List<HistoryEntry> = runCatching {
        if (!historyFile.isFile) {
            emptyList()
        } else {
            json.decodeFromString<List<HistoryEntry>>(historyFile.readText(Charsets.UTF_8))
                .sortedByDescending { it.stoppedAtEpochMs }
        }
    }.getOrDefault(emptyList()) // 文件损坏兜底：返回空历史，不崩溃

    /** Atomic write via a temp file + rename so a crash mid-write can't corrupt history.json. */
    private fun persist(entries: List<HistoryEntry>) {
        historyFile.parentFile?.mkdirs()
        val tmp = File(historyFile.parentFile, "history.json.tmp")
        tmp.writeText(json.encodeToString(entries), Charsets.UTF_8)
        if (!tmp.renameTo(historyFile)) {
            tmp.copyTo(historyFile, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        const val MAX_ENTRIES = 20
    }
}
