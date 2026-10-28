package com.xzm.realtimetranslate.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTranslatorTest {

    @Test
    fun `page count within limit passes validation`() {
        PdfTranslator.validatePageCount(pageCount = 1, maxPages = 50)
        PdfTranslator.validatePageCount(pageCount = 50, maxPages = 50)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty pdf throws exception`() {
        PdfTranslator.validatePageCount(pageCount = 0, maxPages = 50)
    }

    @Test
    fun `exceeding max pages throws exception with helpful message`() {
        try {
            PdfTranslator.validatePageCount(pageCount = 51, maxPages = 50)
            org.junit.Assert.fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("51") == true)
            assertTrue(e.message?.contains("50") == true)
        }
    }

    @Test
    fun `calculate scale factor scales a4 page to approximately 2000px`() {
        // Standard A4 at 72dpi is 595 x 842 points. Long side 842.
        val scale = PdfTranslator.calculateScaleFactor(pageWidth = 595, pageHeight = 842, targetMaxSide = 2000f)
        val longSide = (842 * scale).toInt()
        assertTrue("Long side should be close to 2000px (was $longSide)", longSide in 1900..2100)
    }

    @Test
    fun `calculate exported pages for bilingual vs single mode`() {
        assertEquals(20, PdfTranslator.calculateExportedPages(pageCount = 10, bilingualAlternating = true))
        assertEquals(10, PdfTranslator.calculateExportedPages(pageCount = 10, bilingualAlternating = false))
        assertEquals(2, PdfTranslator.calculateExportedPages(pageCount = 1, bilingualAlternating = true))
        assertEquals(1, PdfTranslator.calculateExportedPages(pageCount = 1, bilingualAlternating = false))
    }
}
