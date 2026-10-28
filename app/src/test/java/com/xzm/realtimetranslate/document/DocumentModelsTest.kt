package com.xzm.realtimetranslate.document

import com.xzm.realtimetranslate.util.DocumentExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentModelsTest {

    @Test
    fun `detect document type from extension`() {
        assertEquals(DocumentType.PDF, DocumentType.fromFileName("paper.pdf"))
        assertEquals(DocumentType.PDF, DocumentType.fromFileName("REPORT.PDF"))
        assertEquals(DocumentType.DOCX, DocumentType.fromFileName("notes.docx"))
        assertEquals(DocumentType.DOCX, DocumentType.fromFileName("doc.DOCX"))
        assertEquals(DocumentType.UNKNOWN, DocumentType.fromFileName("image.png"))
        assertEquals(DocumentType.UNKNOWN, DocumentType.fromFileName(""))
    }

    @Test
    fun `detect document type from mime type`() {
        assertEquals(DocumentType.PDF, DocumentType.fromMimeType("application/pdf"))
        assertEquals(
            DocumentType.DOCX,
            DocumentType.fromMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        )
        assertEquals(DocumentType.UNKNOWN, DocumentType.fromMimeType("image/jpeg"))
        assertEquals(DocumentType.UNKNOWN, DocumentType.fromMimeType(null))
    }

    @Test
    fun `generate output file name with bilingual and translation tags`() {
        val pdfOut = DocumentExporter.generateOutputFileName("sample.pdf", bilingual = true)
        assertTrue(pdfOut.startsWith("sample-双语-"))
        assertTrue(pdfOut.endsWith(".pdf"))

        val docxOut = DocumentExporter.generateOutputFileName("contract.docx", bilingual = false)
        assertTrue(docxOut.startsWith("contract-翻译-"))
        assertTrue(docxOut.endsWith(".docx"))

        val noExtOut = DocumentExporter.generateOutputFileName("plainfile", bilingual = false, fallbackExt = "txt")
        assertTrue(noExtOut.startsWith("plainfile-翻译-"))
        assertTrue(noExtOut.endsWith(".txt"))
    }

    @Test
    fun `config defaults`() {
        val config = DocumentTranslationConfig()
        assertTrue(config.bilingualAlternating)
        assertEquals(50, config.maxPages)
    }
}
