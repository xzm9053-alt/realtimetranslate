package com.xzm.realtimetranslate.overlay

import com.xzm.realtimetranslate.ocr.PixelRect

/**
 * Tracks and merges [CoverTarget]s across frames in full-screen cover mode.
 *
 * When FLAG_SECURE is disabled, the overlay is recorded into the screen capture,
 * so previously painted blocks are erased on the capture bitmap before OCR.
 * As a consequence, OCR only detects NEW or MOVED blocks on subsequent frames.
 * [CoverBlockManager] merges newly translated blocks into the active set,
 * updates positions of moved or overlapping blocks, prunes off-screen blocks,
 * and maintains the persistent state between captures.
 */
class CoverBlockManager(private val maxBlocks: Int = 100) {

    private val blocks = LinkedHashMap<String, CoverTarget>()

    @Synchronized
    fun getActiveTargets(): List<CoverTarget> = blocks.values.toList()

    @Synchronized
    fun getPaintedRects(): List<PixelRect> = blocks.values.map { it.rect }

    /**
     * Updates or adds a translated target.
     * - If [key] matches an existing block, its rect and background are updated.
     * - If an existing block overlaps with [target.rect] by >= 60%, it is replaced.
     * - Otherwise [target] is added as a new active block.
     */
    @Synchronized
    fun updateTarget(target: CoverTarget, key: String? = null) {
        val blockKey = key ?: target.original ?: target.text

        // Check if an existing block has high spatial overlap (e.g. content moved or re-framed)
        val overlappingKey = blocks.entries.firstOrNull { (_, existing) ->
            existing.rect.overlapRatio(target.rect) >= 0.6f
        }?.key

        if (overlappingKey != null && overlappingKey != blockKey) {
            blocks.remove(overlappingKey)
        }

        blocks[blockKey] = target

        while (blocks.size > maxBlocks) {
            val eldest = blocks.keys.first()
            blocks.remove(eldest)
        }
    }

    /**
     * Removes blocks that are completely off screen.
     */
    @Synchronized
    fun pruneOffscreen(displayWidth: Int, displayHeight: Int) {
        val it = blocks.entries.iterator()
        while (it.hasNext()) {
            val rect = it.next().value.rect
            if (rect.bottom <= 0 || rect.top >= displayHeight ||
                rect.right <= 0 || rect.left >= displayWidth
            ) {
                it.remove()
            }
        }
    }

    /**
     * Removes blocks whose age exceeds [maxAgeMs].
     * Useful for dynamic/video content where subtitles should not stick indefinitely.
     */
    @Synchronized
    fun pruneStale(maxAgeMs: Long, now: Long = System.currentTimeMillis()): Int {
        var removed = 0
        val it = blocks.entries.iterator()
        while (it.hasNext()) {
            val target = it.next().value
            if (target.createdAtMs > 0 && now - target.createdAtMs > maxAgeMs) {
                it.remove()
                removed++
            }
        }
        return removed
    }

    @Synchronized
    fun clear() {
        blocks.clear()
    }

    val size: Int
        @Synchronized get() = blocks.size
}
