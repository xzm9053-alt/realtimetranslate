package com.xzm.realtimetranslate.ui.document

import com.xzm.realtimetranslate.document.DocumentInfo
import com.xzm.realtimetranslate.document.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTranslateViewModelTest {

    @Test
    fun `initial state has default bilingual alternating true`() {
        val state = DocumentTranslateUiState()
        assertNull(state.documentInfo)
        assertFalse(state.isWorking)
        assertTrue(state.bilingualAlternating)
        assertEquals("", state.progressLabel)
        assertFalse(state.hasDocument)
        assertFalse(state.hasResult)
    }

    @Test
    fun `state reflects selected document and progress`() {
        val info = DocumentInfo(
            uri = null,
            name = "research.pdf",
            sizeBytes = 1024 * 500,
            type = DocumentType.PDF,
        )
        val state = DocumentTranslateUiState(
            documentInfo = info,
            done = 3,
            total = 10,
            resultDisplayPath = "Download/实时翻译/research-双语.pdf",
        )
        assertTrue(state.hasDocument)
        assertTrue(state.hasResult)
        assertEquals("3/10", state.progressLabel)
        assertEquals("500.0 KB", info.formattedSize)
    }
}
