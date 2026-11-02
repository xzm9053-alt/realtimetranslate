package com.xzm.realtimetranslate.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HistoryRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `append deduplicates identical rapid entries`() = runBlocking {
        val file = File(temp.root, "history.json")
        val repo = HistoryRepository(file)

        val now = System.currentTimeMillis()
        val entry1 = HistoryEntry(stoppedAtEpochMs = now, inputFull = "Hello", outputFull = "你好")
        val entry2 = HistoryEntry(stoppedAtEpochMs = now + 100, inputFull = "Hello", outputFull = "你好")

        repo.append(entry1)
        // Wait briefly for IO coroutine to complete write
        var count = 0
        while (repo.entries.value.isEmpty() && count < 20) {
            kotlinx.coroutines.delay(50)
            count++
        }
        assertEquals(1, repo.entries.value.size)

        repo.append(entry2)
        kotlinx.coroutines.delay(100)
        // Should be deduplicated, still size 1
        assertEquals(1, repo.entries.value.size)
        assertEquals("Hello", repo.entries.value.first().inputFull)
    }

    @Test
    fun `append accepts different content within short time`() = runBlocking {
        val file = File(temp.root, "history.json")
        val repo = HistoryRepository(file)

        val now = System.currentTimeMillis()
        val entry1 = HistoryEntry(stoppedAtEpochMs = now, inputFull = "First", outputFull = "第一")
        val entry2 = HistoryEntry(stoppedAtEpochMs = now + 100, inputFull = "Second", outputFull = "第二")

        repo.append(entry1)
        repo.append(entry2)
        var count = 0
        while (repo.entries.value.size < 2 && count < 20) {
            kotlinx.coroutines.delay(50)
            count++
        }
        assertEquals(2, repo.entries.value.size)
    }

    @Test
    fun `load deduplicates existing consecutive duplicate entries from disk`() = runBlocking {
        val file = File(temp.root, "history.json")
        // Write raw JSON with duplicate entries
        file.writeText(
            """
            [
                {"stoppedAtEpochMs": 1000, "inputFull": "Same", "outputFull": "相同"},
                {"stoppedAtEpochMs": 999, "inputFull": "Same", "outputFull": "相同"},
                {"stoppedAtEpochMs": 800, "inputFull": "Old", "outputFull": "旧"}
            ]
            """.trimIndent(),
        )

        val repo = HistoryRepository(file)
        var count = 0
        while (repo.entries.value.isEmpty() && count < 20) {
            kotlinx.coroutines.delay(50)
            count++
        }

        val entries = repo.entries.value
        assertEquals(2, entries.size)
        assertEquals("Same", entries[0].inputFull)
        assertEquals(1000L, entries[0].stoppedAtEpochMs)
        assertEquals("Old", entries[1].inputFull)
    }
}
