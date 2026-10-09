package com.xzm.realtimetranslate.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The log file is written by a background thread while the user scrolls the log viewer
 * and exports a report, so the read side has to be robust rather than merely correct on
 * a well-formed file: a missing file, a file with no trailing newline, a backward read
 * that lands in the middle of a Chinese character. Rotation is the part that can
 * silently destroy data, so it is pinned exactly.
 */
class LogFileStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun store(maxBytes: Long = 1024, backups: Int = 3) =
        LogFileStore(temp.root, "app.log", maxBytes, backups)

    // ------------------------------------------------------------------ rotation

    @Test
    fun `rotate shifts every file down and drops the oldest`() {
        val s = store(backups = 3)
        s.current.writeText("cur")
        s.backup(1).writeText("one")
        s.backup(2).writeText("two")
        s.backup(3).writeText("three")

        s.rotate()

        assertFalse(s.current.exists())
        assertEquals("cur", s.backup(1).readText())
        assertEquals("one", s.backup(2).readText())
        assertEquals("two", s.backup(3).readText())
        assertEquals(3, s.files().count { it.isFile })
    }

    @Test
    fun `rotate on an empty store creates nothing`() {
        val s = store()
        s.rotate()
        assertEquals(0, s.files().count { it.isFile })
    }

    @Test
    fun `shouldRotate flips exactly at the byte limit`() {
        val s = store(maxBytes = 10)
        assertFalse(s.shouldRotate())
        s.current.writeText("123456789")      // 9 bytes
        assertFalse(s.shouldRotate())
        s.current.writeText("1234567890")     // 10 bytes
        assertTrue(s.shouldRotate())
    }

    // ---------------------------------------------------------------------- tail

    @Test
    fun `tail returns the last lines, oldest first`() {
        val s = store()
        s.current.writeText("a\nb\nc\nd\n")
        assertEquals(listOf("c", "d"), s.tail(2))
    }

    @Test
    fun `tail returns everything when the file is shorter than asked`() {
        val s = store()
        s.current.writeText("a\nb\n")
        assertEquals(listOf("a", "b"), s.tail(50))
    }

    @Test
    fun `tail of a missing or empty file is empty`() {
        val s = store()
        assertEquals(emptyList<String>(), s.tail(10))
        s.current.writeText("")
        assertEquals(emptyList<String>(), s.tail(10))
    }

    @Test
    fun `a file without a trailing newline still yields its last line`() {
        val s = store()
        s.current.writeText("a\nb")
        assertEquals(listOf("a", "b"), s.tail(10))
    }

    @Test
    fun `tail reads across rotated files when the current one is short`() {
        val s = store()
        s.backup(1).writeText("old1\nold2\n")
        s.current.writeText("new1\nnew2\n")
        assertEquals(listOf("old2", "new1", "new2"), s.tail(3))
    }

    @Test
    fun `tail respects the byte budget`() {
        val s = store()
        s.current.writeText("a".repeat(100) + "\n" + "b".repeat(100) + "\n")
        // Only the last 110 bytes are read, which starts inside the first long line.
        val lines = s.tail(10, maxBytes = 110)
        assertEquals(listOf("b".repeat(100)), lines)
    }

    // -------------------------------------------------------------- utf-8 safety

    @Test
    fun `a backward read that lands mid-character does not produce mojibake`() {
        val s = store()
        s.current.writeText("一二三四五\n一二三四五\n")

        // Byte 12 is a continuation byte in the middle of "四", so the window opens
        // mid-character and its first line is the truncated tail of a real line.
        val lines = s.tail(maxLines = 10, maxBytes = 20)

        assertEquals(listOf("一二三四五"), lines)
        assertFalse(lines.any { it.contains('�') })
    }

    @Test
    fun `a window that opens exactly on a newline keeps the whole first line`() {
        val s = store()
        s.current.writeText("一二三四五\n一二三四五\n")
        // 16 bytes is exactly one line: the look-behind sees the newline, so this line
        // must survive rather than be mistaken for the tail of a line we never read.
        assertEquals(listOf("一二三四五"), s.tail(maxLines = 1, maxBytes = 16))
    }

    @Test
    fun `utf8StartOffset skips only continuation bytes`() {
        val midCharacter = byteArrayOf(0x80.toByte(), 0xBF.toByte(), 0xE4.toByte(), 0xB8.toByte(), 0x80.toByte())
        assertEquals(2, LogFileStore.utf8StartOffset(midCharacter))
        assertEquals(0, LogFileStore.utf8StartOffset(byteArrayOf(0x41)))
        assertEquals(0, LogFileStore.utf8StartOffset(ByteArray(0)))
    }

    // --------------------------------------------------------------------- housekeeping

    @Test
    fun `clear removes every file the store owns`() {
        val s = store()
        s.current.writeText("x")
        s.backup(1).writeText("y")
        s.clear()
        assertEquals(0, s.files().count { it.isFile })
    }

    @Test
    fun `totalBytes sums the live files`() {
        val s = store()
        s.current.writeText("12345")
        s.backup(1).writeText("123")
        assertEquals(8L, s.totalBytes())
    }
}
