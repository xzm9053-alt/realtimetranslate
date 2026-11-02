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
import java.util.UUID

/**
 * Persistent storage of user-configured glossary terms, stored as JSON in [glossaryFile].
 */
class GlossaryRepository(
    private val glossaryFile: File,
) {
    constructor(context: Context) : this(File(context.filesDir, "glossary.json"))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow<List<GlossaryTerm>>(emptyList())
    val entries: StateFlow<List<GlossaryTerm>> = _entries.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                _entries.value = load()
            }
        }
    }

    suspend fun add(source: String, target: String, note: String = "") {
        val s = source.trim()
        val t = target.trim()
        if (s.isEmpty() || t.isEmpty()) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = _entries.value
                val existing = current.find { it.source.equals(s, ignoreCase = true) }
                val updated = if (existing != null) {
                    current.map {
                        if (it.id == existing.id) it.copy(target = t, note = note.trim(), updatedAtEpochMs = System.currentTimeMillis())
                        else it
                    }
                } else {
                    current + GlossaryTerm(
                        id = UUID.randomUUID().toString(),
                        source = s,
                        target = t,
                        note = note.trim(),
                    )
                }
                _entries.value = updated
                persist(updated)
            }
        }
    }

    suspend fun update(term: GlossaryTerm) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val updated = _entries.value.map {
                    if (it.id == term.id) term.copy(updatedAtEpochMs = System.currentTimeMillis()) else it
                }
                _entries.value = updated
                persist(updated)
            }
        }
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val updated = _entries.value.filterNot { it.id == id }
                _entries.value = updated
                persist(updated)
            }
        }
    }

    suspend fun toggle(id: String, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val updated = _entries.value.map {
                    if (it.id == id) it.copy(enabled = enabled, updatedAtEpochMs = System.currentTimeMillis()) else it
                }
                _entries.value = updated
                persist(updated)
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

    /**
     * Imports tab-separated or comma-separated lines: "source\ttarget[\tnote]"
     * Returns count of imported terms.
     */
    suspend fun importText(content: String): Result<Int> = runCatching {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val lines = content.lines()
                var imported = 0
                val map = _entries.value.associateBy { it.source.lowercase() }.toMutableMap()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue
                    val parts = when {
                        trimmed.contains('\t') -> trimmed.split('\t')
                        trimmed.contains("->") -> trimmed.split("->")
                        trimmed.contains("=>") -> trimmed.split("=>")
                        trimmed.contains(',') -> trimmed.split(',')
                        else -> continue
                    }
                    if (parts.size >= 2) {
                        val src = parts[0].trim()
                        val dst = parts[1].trim()
                        val note = if (parts.size >= 3) parts[2].trim() else ""
                        if (src.isNotEmpty() && dst.isNotEmpty()) {
                            val key = src.lowercase()
                            val existing = map[key]
                            val term = if (existing != null) {
                                existing.copy(target = dst, note = note.ifBlank { existing.note }, updatedAtEpochMs = System.currentTimeMillis())
                            } else {
                                GlossaryTerm(
                                    id = UUID.randomUUID().toString(),
                                    source = src,
                                    target = dst,
                                    note = note,
                                )
                            }
                            map[key] = term
                            imported++
                        }
                    }
                }
                val resultList = map.values.toList()
                _entries.value = resultList
                persist(resultList)
                imported
            }
        }
    }

    fun exportTsv(): String {
        return _entries.value.joinToString("\n") {
            if (it.note.isNotBlank()) "${it.source}\t${it.target}\t${it.note}"
            else "${it.source}\t${it.target}"
        }
    }

    /**
     * Compiles enabled glossary terms into prompt instructions for LLM translation.
     */
    fun getEnabledTermsPrompt(): String {
        val active = _entries.value.filter { it.enabled && it.source.isNotBlank() && it.target.isNotBlank() }
        if (active.isEmpty()) return ""
        return buildString {
            append("【专业术语对照规范 / Glossary Guidelines】\n")
            append("In this translation, strictly translate the following terminology as specified:\n")
            for (term in active) {
                append("- \"${term.source}\" => \"${term.target}\"")
                if (term.note.isNotBlank()) {
                    append(" (Note: ${term.note})")
                }
                append("\n")
            }
            append("Do not alter or translate these terms into other words or synonyms.")
        }
    }

    private fun load(): List<GlossaryTerm> = runCatching {
        if (!glossaryFile.isFile) {
            emptyList()
        } else {
            json.decodeFromString<List<GlossaryTerm>>(glossaryFile.readText(Charsets.UTF_8))
        }
    }.getOrDefault(emptyList())

    private fun persist(entries: List<GlossaryTerm>) {
        glossaryFile.parentFile?.mkdirs()
        val tmp = File(glossaryFile.parentFile, "glossary.json.tmp")
        tmp.writeText(json.encodeToString(entries), Charsets.UTF_8)
        if (!tmp.renameTo(glossaryFile)) {
            tmp.copyTo(glossaryFile, overwrite = true)
            tmp.delete()
        }
    }
}
