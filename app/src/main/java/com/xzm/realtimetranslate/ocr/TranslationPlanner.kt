package com.xzm.realtimetranslate.ocr

/** A translated-once unit: [key] is the de-duplication identity, [rect] the block's union box. */
data class TextTarget(
    val key: String,
    val text: String,
    val rect: PixelRect,
    /**
     * Height of one original text line in this block, in source pixels.
     *
     * The cover sizes the translation from this, not from [rect]: a ten-line
     * paragraph has a rect ten times taller than its text, so deriving the font
     * from the rect would render the translation at ten times the original size.
     * 0 means "unknown" and lets the painter fall back.
     */
    val sourceLineHeight: Float = 0f,
)

/**
 * Decides which blocks of a recognized page are worth sending to the translation
 * engine. Keeps the expensive path (network + user credits) away from text that is
 * already in the target language, noise glyphs, and repeat frames.
 */
object TranslationPlanner {

    fun plan(
        page: OcrPage,
        targetLang: String,
        alreadyTranslated: Set<String> = emptySet(),
    ): List<TextTarget> {
        val seen = HashSet<String>()
        val targets = ArrayList<TextTarget>(page.blocks.size)
        for (block in page.blocks) {
            val key = normalize(block.text)
            if (key.isEmpty()) continue
            if (key in alreadyTranslated) continue
            if (!shouldTranslate(key, targetLang)) continue
            if (!seen.add(key)) continue
            targets += TextTarget(
                key = key,
                text = block.text,
                rect = block.rect,
                sourceLineHeight = medianLineHeight(block.lines),
            )
        }
        return targets
    }

    /**
     * Paragraph planning for the full-screen cover.
     *
     * ML Kit splits a wrapped sentence into one block per visual line, so planning
     * per block sends the engine half-sentences ("Many students want to" /
     * "do well in English, but they"). Each fragment loses the context of the next,
     * and the translation comes back disconnected. This pass merges the blocks that
     * belong to one paragraph first, so the engine sees whole sentences and the cover
     * paints one block of prose instead of a stack of fragments.
     *
     * Two signals separate paragraphs:
     * - a vertical gap larger than the usual line pitch, and
     * - a left edge that does not line up with the paragraph's left edge (indented
     *   quotes, list markers, code blocks).
     * Lines are joined with [joinComicLines], which already handles CJK versus Latin
     * spacing correctly.
     */
    fun planParagraphs(
        page: OcrPage,
        targetLang: String,
        alreadyTranslated: Set<String> = emptySet(),
    ): List<TextTarget> {
        if (page.blocks.isEmpty()) return emptyList()

        val sorted = page.blocks.sortedWith(
            compareBy({ it.rect.top }, { it.rect.left }),
        )
        // Judge a paragraph break against this page's own line gap instead of a
        // fixed ratio. Body text sits at a tight, consistent pitch; a real paragraph
        // gap is clearly larger than that pitch. A hard-coded ratio either glues
        // paragraphs together or splits one paragraph apart depending on the
        // document's font size.
        val breakGap = paragraphBreakGap(sorted)

        val paragraphs = ArrayList<MutableList<OcrLine>>()
        var current = ArrayList<OcrLine>()
        var currentLeft = 0

        for (block in sorted) {
            val firstLine = block.lines.firstOrNull() ?: continue
            if (current.isEmpty()) {
                current = ArrayList(block.lines)
                currentLeft = block.rect.left
                continue
            }

            val previousBottom = current.last().rect.bottom
            val gap = firstLine.rect.top - previousBottom
            val sameIndent = kotlin.math.abs(block.rect.left - currentLeft) <= INDENT_TOLERANCE_PX
            val continuesParagraph = gap <= breakGap && sameIndent

            if (continuesParagraph) {
                current.addAll(block.lines)
            } else {
                paragraphs.add(current)
                current = ArrayList(block.lines)
                currentLeft = block.rect.left
            }
        }
        if (current.isNotEmpty()) paragraphs.add(current)

        val seen = HashSet<String>()
        val targets = ArrayList<TextTarget>(paragraphs.size)
        for (lines in paragraphs) {
            val ordered = lines.sortedWith(compareBy({ it.rect.top }, { it.rect.left }))
            val text = joinComicLines(ordered)
            val key = normalize(text)
            if (key.isEmpty()) continue
            if (key in alreadyTranslated) continue
            if (!shouldTranslate(key, targetLang)) continue
            if (!seen.add(key)) continue
            val rect = ordered.drop(1).fold(ordered.first().rect) { acc, line ->
                acc.union(line.rect)
            }
            targets += TextTarget(
                key = key,
                text = text,
                rect = rect,
                sourceLineHeight = medianLineHeight(ordered),
            )
        }
        return targets
    }

    /** Median line height of [lines]; used as the line pitch of a paragraph. */
    private fun medianLineHeight(lines: List<OcrLine>): Float {
        if (lines.isEmpty()) return 0f
        val heights = lines.map { it.rect.height.toFloat() }.sorted()
        return heights[heights.size / 2]
    }

    /**
     * The vertical gap that still counts as the same paragraph.
     *
     * Derived from the page's median line height, not from its tightest gap: with
     * only two blocks on screen the tightest gap is the paragraph break itself, so
     * it would always merge them. Line height tracks the font size, and real
     * paragraph spacing is roughly one to one-and-a-half line heights.
     */
    private fun paragraphBreakGap(sorted: List<OcrBlock>): Float {
        val heights = sorted.map { medianLineHeight(it.lines) }.filter { it > 0f }.sorted()
        val medianHeight = if (heights.isEmpty()) 0f else heights[heights.size / 2]
        return maxOf(medianHeight * PARAGRAPH_GAP_FACTOR, MIN_PARAGRAPH_GAP_PX)
    }

    /**
     * Comic / Manga planning pass:
     * 1. Filters out large noise SFX (sound effects / onomatopoeia).
     * 2. Clusters spatially adjacent blocks into unified speech bubbles.
     * 3. Sorts vertical manga lines from Right to Left, top to bottom.
     * 4. Seamlessly unwraps dialogue lines without extra spaces between CJK glyphs.
     */
    fun planComic(
        page: OcrPage,
        targetLang: String,
        alreadyTranslated: Set<String> = emptySet(),
    ): List<TextTarget> {
        if (page.blocks.isEmpty()) return emptyList()

        // 1. Calculate median character size across page.
        // Prefer lines with dialogue markers/punctuation so giant SFX don't skew the median.
        val confirmedDialogueHeights = ArrayList<Float>()
        val allCharHeights = ArrayList<Float>()
        for (block in page.blocks) {
            for (line in block.lines) {
                val clean = line.text.filterNot { it.isWhitespace() }
                if (clean.length >= 2) {
                    val h = if (line.rect.height > line.rect.width * 1.1f) {
                        line.rect.height.toFloat() / clean.length
                    } else {
                        line.rect.height.toFloat()
                    }
                    if (h > 0f) {
                        allCharHeights.add(h)
                        if (clean.any { it in "。！？!?…「」『』（）()、,." }) {
                            confirmedDialogueHeights.add(h)
                        }
                    }
                }
            }
        }
        val medianCharHeight = when {
            confirmedDialogueHeights.isNotEmpty() -> {
                val sorted = confirmedDialogueHeights.sorted()
                sorted[sorted.size / 2]
            }
            allCharHeights.isNotEmpty() -> {
                val sorted = allCharHeights.sorted()
                sorted[sorted.size / 3] // Use lower percentile so SFX doesn't dominate
            }
            else -> 24f
        }

        // 2. Filter out SFX lines from blocks
        val filteredBlocks = ArrayList<OcrBlock>()
        for (block in page.blocks) {
            val validLines = block.lines.filterNot { isLikelySfx(it, medianCharHeight) }
            if (validLines.isNotEmpty()) {
                filteredBlocks.add(OcrBlock(validLines))
            }
        }
        if (filteredBlocks.isEmpty()) return emptyList()

        // 3. Cluster adjacent blocks into speech bubbles
        val clusters = clusterBlocks(filteredBlocks)

        // 4. Sort lines within bubble and join text cleanly
        val seen = HashSet<String>()
        val targets = ArrayList<TextTarget>(clusters.size)

        for (cluster in clusters) {
            val sortedLines = sortComicLines(cluster)
            val joinedText = joinComicLines(sortedLines)
            val key = normalize(joinedText)
            if (key.isEmpty()) continue
            if (key in alreadyTranslated) continue
            if (!shouldTranslate(key, targetLang)) continue
            if (!seen.add(key)) continue

            val unionRect = sortedLines.drop(1).fold(sortedLines.first().rect) { acc, line ->
                acc.union(line.rect)
            }
            targets += TextTarget(
                key = key,
                text = joinedText,
                rect = unionRect,
                sourceLineHeight = medianLineHeight(sortedLines),
            )
        }
        return targets
    }

    fun joinComicLines(lines: List<OcrLine>): String {
        if (lines.isEmpty()) return ""
        val sb = StringBuilder()
        for (line in lines) {
            val text = OcrTextProcessor.cleanCjkInternalSpacing(line.text.trim())
            if (text.isEmpty()) continue
            if (sb.isEmpty()) {
                sb.append(text)
            } else {
                val prevLast = sb.last()
                val nextFirst = text.first()
                // A Latin sentence that ended mid-line still needs the space the
                // wrap removed; CJK lines must stay glued together.
                if (needsSpaceBetweenLines(prevLast, nextFirst)) {
                    sb.append(' ').append(text)
                } else {
                    sb.append(text)
                }
            }
        }
        return sb.toString()
    }

    /**
     * [CjkTextSpacers.shouldInsertSpace] treats any punctuation as "no space",
     * which is right for CJK but wrong for Latin: joining "line one." and
     * "First line" would produce "one.First". This keeps the CJK rules and adds
     * the Latin ones back.
     */
    private fun needsSpaceBetweenLines(prev: Char, next: Char): Boolean {
        if (prev.isWhitespace() || next.isWhitespace()) return false
        val prevCjk = com.xzm.realtimetranslate.util.CjkTextSpacers.isCjkChar(prev) ||
            com.xzm.realtimetranslate.util.CjkTextSpacers.isCjkPunctuation(prev)
        val nextCjk = com.xzm.realtimetranslate.util.CjkTextSpacers.isCjkChar(next) ||
            com.xzm.realtimetranslate.util.CjkTextSpacers.isCjkPunctuation(next)
        if (prevCjk || nextCjk) {
            return com.xzm.realtimetranslate.util.CjkTextSpacers.shouldInsertSpace(prev, next)
        }
        // Both sides are Latin: keep them apart. That covers ".First", ",and" and
        // plain word-to-word joins alike.
        return true
    }

    fun isLikelySfx(line: OcrLine, medianHeight: Float): Boolean {
        val clean = line.text.filterNot { it.isWhitespace() }
        if (clean.isEmpty()) return true
        // Dialogue with punctuation or quotes is NOT an SFX
        if (clean.any { it in "。！？!?…「」『』（）()—~～、,." }) return false

        val charHeight = if (line.rect.height > line.rect.width * 1.1f) {
            line.rect.height.toFloat() / maxOf(1, clean.length)
        } else {
            line.rect.height.toFloat()
        }

        // Giant letters without punctuation
        if (charHeight > medianHeight * 1.6f && clean.length <= 5) return true

        // Short Katakana noise words (e.g. ドォン, バキッ, ドドド, ゴゴゴ)
        val isAllKatakana = clean.all { it in 'ァ'..'ヶ' || it == 'ー' || it == 'ッ' }
        if (isAllKatakana && clean.length <= 4 && charHeight > medianHeight * 1.25f) return true

        return false
    }

    fun sortComicLines(lines: List<OcrLine>): List<OcrLine> {
        if (lines.size <= 1) return lines
        val verticalCount = lines.count { it.rect.height > it.rect.width * 1.1f }
        val isVertical = verticalCount >= lines.size / 2

        return if (isVertical) {
            // Vertical manga: Right to Left.
            // If two lines have overlapping X within tolerance, sort Top to Bottom.
            lines.sortedWith { a, b ->
                val colTolerance = minOf(a.rect.width, b.rect.width).coerceAtLeast(10) * 0.75f
                val diffX = a.rect.right - b.rect.right
                if (kotlin.math.abs(diffX) < colTolerance) {
                    a.rect.top.compareTo(b.rect.top)
                } else {
                    // Larger right coordinate comes first (Right to Left)
                    b.rect.right.compareTo(a.rect.right)
                }
            }
        } else {
            // Horizontal text: Top to Bottom, then Left to Right
            lines.sortedWith { a, b ->
                val rowTolerance = minOf(a.rect.height, b.rect.height).coerceAtLeast(10) * 0.75f
                val diffY = a.rect.top - b.rect.top
                if (kotlin.math.abs(diffY) < rowTolerance) {
                    a.rect.left.compareTo(b.rect.left)
                } else {
                    a.rect.top.compareTo(b.rect.top)
                }
            }
        }
    }

    fun clusterBlocks(blocks: List<OcrBlock>): List<List<OcrLine>> {
        if (blocks.isEmpty()) return emptyList()
        val n = blocks.size
        val parent = IntArray(n) { it }
        fun find(i: Int): Int {
            var root = i
            while (root != parent[root]) root = parent[root]
            var curr = i
            while (curr != root) {
                val nxt = parent[curr]
                parent[curr] = root
                curr = nxt
            }
            return root
        }
        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) parent[rootI] = rootJ
        }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (shouldMergeIntoBubble(blocks[i].rect, blocks[j].rect)) {
                    union(i, j)
                }
            }
        }

        val groups = LinkedHashMap<Int, MutableList<OcrLine>>()
        for (i in 0 until n) {
            val root = find(i)
            groups.computeIfAbsent(root) { mutableListOf() }.addAll(blocks[i].lines)
        }
        return groups.values.toList()
    }

    private fun shouldMergeIntoBubble(a: PixelRect, b: PixelRect): Boolean {
        if (a.intersects(b)) return true

        // Vertical columns in a speech bubble:
        val gapX = maxOf(0, maxOf(a.left, b.left) - minOf(a.right, b.right))
        val overlapY = maxOf(0, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
        val minH = minOf(a.height, b.height)
        val xThreshold = maxOf(45, (maxOf(a.width, b.width) * 1.5f).toInt())
        if (gapX <= xThreshold && minH > 0 && (overlapY.toFloat() / minH) >= 0.4f) {
            return true
        }

        // Horizontal lines in a speech bubble:
        val gapY = maxOf(0, maxOf(a.top, b.top) - minOf(a.bottom, b.bottom))
        val overlapX = maxOf(0, minOf(a.right, b.right) - maxOf(a.left, b.left))
        val minW = minOf(a.width, b.width)
        val yThreshold = maxOf(30, (maxOf(a.height, b.height) * 1.2f).toInt())
        if (gapY <= yThreshold && minW > 0 && (overlapX.toFloat() / minW) >= 0.5f) {
            return true
        }

        return false
    }

    fun shouldTranslate(text: String, targetLang: String): Boolean {
        if (text.filterNot { it.isWhitespace() }.length < 2) return false
        return when {
            targetLang.startsWith("zh") -> !isAlreadyChinese(text)
            targetLang.startsWith("en") -> !isAlreadyLatin(text)
            else -> true
        }
    }

    fun normalize(text: String): String = WHITESPACE.replace(text, " ").trim()

    private val WHITESPACE = Regex("\\s+")

    /**
     * A gap this many times the page's normal line gap is a paragraph break.
     * Real paragraph spacing is usually 1.5-2x the line pitch.
     */
    private const val PARAGRAPH_GAP_FACTOR = 1.6f

    /** Floor for [PARAGRAPH_GAP_FACTOR] so tiny text cannot split every line. */
    private const val MIN_PARAGRAPH_GAP_PX = 8f

    /** How far a block's left edge may drift and still be the same paragraph. */
    private const val INDENT_TOLERANCE_PX = 40

    /** Mostly non-ASCII, no kana/hangul → it is already Chinese, translating again would garble it. */
    private fun isAlreadyChinese(text: String): Boolean {
        if (text.any { it in '぀'..'ヿ' || it in '가'..'힣' }) return false
        if (text.count { it.isLetterOrDigit() } == 0) return false
        return text.count { it.code > 0x7F }.toFloat() / text.length > 0.6f
    }

    private fun isAlreadyLatin(text: String): Boolean {
        if (text.any { it in '一'..'鿿' || it in '぀'..'ヿ' || it in '가'..'힣' }) return false
        val letters = text.count { it.isLetter() }
        if (letters == 0) return false
        val ascii = text.count { it in 'a'..'z' || it in 'A'..'Z' }
        return ascii.toFloat() / letters > 0.9f
    }
}
