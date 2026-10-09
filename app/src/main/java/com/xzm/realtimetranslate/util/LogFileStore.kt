package com.xzm.realtimetranslate.util

import java.io.File
import java.io.RandomAccessFile

/**
 * A rotating append-only text log on disk: `name`, `name.1` … `name.maxBackups`.
 *
 * Pure `java.io` so it is unit-testable on the JVM. Every method here blocks; callers
 * are responsible for calling it off the main thread. Rotation is *not* thread-safe by
 * design — [AppLog] performs it on its single writer thread, which is the only place
 * that holds the write handle.
 */
class LogFileStore(
    val dir: File,
    private val name: String,
    val maxBytes: Long,
    private val maxBackups: Int,
) {

    val current: File get() = File(dir, name)

    fun backup(index: Int): File = File(dir, "$name.$index")

    /** All files this store owns, newest first. */
    fun files(): List<File> = buildList {
        add(current)
        for (i in 1..maxBackups) add(backup(i))
    }

    fun ensureDir(): Boolean = dir.isDirectory || dir.mkdirs()

    fun totalBytes(): Long = files().sumOf { if (it.isFile) it.length() else 0L }

    fun shouldRotate(): Boolean = current.isFile && current.length() >= maxBytes

    /**
     * Shifts `name.(n-1)` → `name.n`, dropping the oldest, then `name` → `name.1`.
     * Caller must close its write handle first.
     */
    fun rotate() {
        backup(maxBackups).delete()
        for (i in maxBackups downTo 2) {
            val src = backup(i - 1)
            if (src.isFile) src.renameTo(backup(i))
        }
        if (current.isFile) current.renameTo(backup(1))
    }

    fun clear() {
        files().forEach { it.delete() }
    }

    /**
     * The last [maxLines] lines, oldest first, reading at most [maxBytes] from disk and
     * spilling into the rotated backups when the current file is short (which is what a
     * user sees right after a rotation).
     *
     * Never loads the whole file: memory is bounded by [maxBytes] regardless of how big
     * the log has grown.
     */
    fun tail(maxLines: Int, maxBytes: Int = DEFAULT_TAIL_BYTES): List<String> {
        if (maxLines <= 0) return emptyList()
        val parts = ArrayList<List<String>>()
        var remainingLines = maxLines
        var budget = maxBytes.toLong()

        for (file in files()) {
            if (remainingLines <= 0 || budget <= 0L) break
            val chunk = readTail(file, remainingLines, budget)
            if (chunk.lines.isEmpty()) continue
            parts.add(chunk.lines)
            remainingLines -= chunk.lines.size
            budget -= chunk.bytesRead
        }

        val out = ArrayList<String>(maxLines)
        for (i in parts.indices.reversed()) out.addAll(parts[i])
        return if (out.size > maxLines) out.subList(out.size - maxLines, out.size).toList() else out
    }

    private class TailChunk(val lines: List<String>, val bytesRead: Long)

    private fun readTail(file: File, maxLines: Int, maxBytes: Long): TailChunk {
        val length = file.length()
        if (length <= 0L) return TailChunk(emptyList(), 0L)

        val limit = minOf(length, maxBytes, MAX_TAIL_BYTES.toLong()).toInt()
        val startOffset = length - limit
        val bytes = ByteArray(limit)
        var startsOnLineBoundary = startOffset == 0L
        RandomAccessFile(file, "r").use { raf ->
            if (startOffset > 0L) {
                // One byte of look-behind decides whether the window opens on a line
                // start. Without this we would throw away a perfectly good first line
                // whenever the byte budget happened to land on a newline.
                raf.seek(startOffset - 1)
                startsOnLineBoundary = raf.read() == '\n'.code
            }
            raf.seek(startOffset)
            raf.readFully(bytes)
        }

        // A backward read can start in the middle of a multi-byte character; skipping
        // the leading continuation bytes is what keeps the first Chinese character of
        // the tail from decoding as U+FFFD.
        val start = utf8StartOffset(bytes)
        if (start >= bytes.size) return TailChunk(emptyList(), limit.toLong())
        val text = String(bytes, start, bytes.size - start, Charsets.UTF_8)

        val split = text.split('\n').map { it.trimEnd('\r') }
        // Off a boundary the first element is the tail of a line whose head we never
        // read, so it is not a line the user ever saw.
        val complete = if (startsOnLineBoundary) split else split.drop(1)
        val trimmed = complete.dropLastWhile { it.isEmpty() }
        val lines = if (trimmed.size > maxLines) {
            trimmed.subList(trimmed.size - maxLines, trimmed.size)
        } else {
            trimmed
        }
        return TailChunk(lines, limit.toLong())
    }

    companion object {
        const val DEFAULT_TAIL_BYTES = 256 * 1024

        /** Hard ceiling for one backward read, so a caller cannot ask for a huge buffer. */
        const val MAX_TAIL_BYTES = 4 * 1024 * 1024

        /**
         * First index that is not a UTF-8 continuation byte (`10xxxxxx`). Exposed for
         * tests: this is the whole reason a mid-character cut does not produce mojibake.
         */
        fun utf8StartOffset(bytes: ByteArray): Int {
            var i = 0
            while (i < bytes.size && (bytes[i].toInt() and 0xC0) == 0x80) i++
            return i
        }
    }
}
