package com.xzm.realtimetranslate.overlay

import com.xzm.realtimetranslate.ocr.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverBlockManagerTest {

    @Test
    fun `existing blocks are preserved when adding new blocks`() {
        val manager = CoverBlockManager()
        val target1 = CoverTarget(PixelRect(10, 10, 100, 50), "第一段译文", 0xFF000000.toInt(), "First paragraph")
        val target2 = CoverTarget(PixelRect(10, 60, 100, 100), "第二段译文", 0xFF000000.toInt(), "Second paragraph")

        manager.updateTarget(target1)
        assertEquals(1, manager.size)
        assertEquals(listOf(target1), manager.getActiveTargets())

        manager.updateTarget(target2)
        assertEquals(2, manager.size)
        assertEquals(listOf(target1, target2), manager.getActiveTargets())
        assertEquals(listOf(target1.rect, target2.rect), manager.getPaintedRects())
    }

    @Test
    fun `target with same key updates position and preserves identity`() {
        val manager = CoverBlockManager()
        val targetOld = CoverTarget(PixelRect(10, 10, 100, 50), "旧位置", 0xFF000000.toInt(), "Hello")
        val targetMoved = CoverTarget(PixelRect(10, 30, 100, 70), "新位置", 0xFF111111.toInt(), "Hello")

        manager.updateTarget(targetOld)
        manager.updateTarget(targetMoved)

        assertEquals(1, manager.size)
        assertEquals(targetMoved, manager.getActiveTargets().first())
    }

    @Test
    fun `spatially overlapping target replaces old block even if key differs`() {
        val manager = CoverBlockManager()
        val oldTarget = CoverTarget(PixelRect(10, 10, 100, 100), "原文A", 0, "sourceA")
        // New block has >60% overlap
        val newTarget = CoverTarget(PixelRect(12, 12, 98, 98), "原文B", 0, "sourceB")

        manager.updateTarget(oldTarget)
        manager.updateTarget(newTarget)

        assertEquals(1, manager.size)
        assertEquals(newTarget, manager.getActiveTargets().first())
    }

    @Test
    fun `pruneOffscreen removes targets that scrolled out of view`() {
        val manager = CoverBlockManager()
        val visible = CoverTarget(PixelRect(10, 100, 200, 300), "可见", 0)
        val scrolledAbove = CoverTarget(PixelRect(10, -100, 200, 0), "上方已出界", 0)
        val scrolledBelow = CoverTarget(PixelRect(10, 2400, 200, 2500), "下方已出界", 0)

        manager.updateTarget(visible)
        manager.updateTarget(scrolledAbove)
        manager.updateTarget(scrolledBelow)
        assertEquals(3, manager.size)

        manager.pruneOffscreen(displayWidth = 1080, displayHeight = 2400)
        assertEquals(1, manager.size)
        assertEquals(listOf(visible), manager.getActiveTargets())
    }

    @Test
    fun `clear removes all targets and painted rects`() {
        val manager = CoverBlockManager()
        manager.updateTarget(CoverTarget(PixelRect(0, 0, 50, 50), "text", 0))
        assertEquals(1, manager.size)

        manager.clear()
        assertEquals(0, manager.size)
        assertTrue(manager.getActiveTargets().isEmpty())
        assertTrue(manager.getPaintedRects().isEmpty())
    }

    @Test
    fun `capacity bounds maximum active blocks`() {
        val manager = CoverBlockManager(maxBlocks = 2)
        manager.updateTarget(CoverTarget(PixelRect(0, 0, 10, 10), "1", 0, "k1"))
        manager.updateTarget(CoverTarget(PixelRect(0, 20, 10, 30), "2", 0, "k2"))
        manager.updateTarget(CoverTarget(PixelRect(0, 40, 10, 50), "3", 0, "k3"))

        assertEquals(2, manager.size)
        assertEquals(listOf("2", "3"), manager.getActiveTargets().map { it.text })
    }

    @Test
    fun `pruneStale removes blocks older than maxAgeMs`() {
        val manager = CoverBlockManager()
        val oldTarget = CoverTarget(PixelRect(0, 0, 10, 10), "旧字幕", 0, createdAtMs = 1000L)
        val freshTarget = CoverTarget(PixelRect(0, 20, 10, 30), "新字幕", 0, createdAtMs = 4000L)

        manager.updateTarget(oldTarget)
        manager.updateTarget(freshTarget)
        assertEquals(2, manager.size)

        val removed = manager.pruneStale(maxAgeMs = 3500L, now = 5000L)
        assertEquals(1, removed)
        assertEquals(1, manager.size)
        assertEquals(listOf(freshTarget), manager.getActiveTargets())
    }
}
