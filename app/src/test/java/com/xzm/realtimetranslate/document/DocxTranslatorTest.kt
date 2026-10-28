package com.xzm.realtimetranslate.document

import com.xzm.realtimetranslate.translate.TranslationEngine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DocxTranslatorTest {

    private val sampleXml = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:body>
                <w:p>
                    <w:pPr><w:jc w:val="center"/></w:pPr>
                    <w:r><w:t>Hello </w:t></w:r>
                    <w:r><w:t>World!</w:t></w:r>
                </w:p>
                <w:p>
                    <w:r><w:t>Tom &amp; Jerry &lt;1&gt;</w:t></w:r>
                </w:p>
                <w:p/>
            </w:body>
        </w:document>
    """.trimIndent()

    private class FakeEngine : TranslationEngine {
        override fun translate(text: String, sourceLang: String, targetLang: String) =
            flowOf("译: $text")

        override suspend fun testConnection(targetLang: String): Result<String> =
            Result.success("ok")
    }

    @Test
    fun `extract and replace paragraph text preserving xml structure`() = runBlocking {
        val translator = DocxTranslator(FakeEngine())
        val modifiedBytes = translator.extractAndReplaceParagraphs(
            sampleXml.byteInputStream(),
            sourceLang = "en",
            targetLang = "zh",
        )
        val resultXml = String(modifiedBytes, Charsets.UTF_8)
        assertTrue("Must contain translated multi-run text", resultXml.contains("译: Hello World!"))
        assertTrue("Must contain translated special chars", resultXml.contains("译: Tom &amp; Jerry &lt;1&gt;"))
        assertTrue("Must preserve paragraph properties", resultXml.contains("<w:jc w:val=\"center\"/>"))
    }

    @Test
    fun `translate full docx zip stream and preserve other zip entries`() = runBlocking {
        // 1. Create a mock docx zip in memory
        val inputZipBaos = ByteArrayOutputStream()
        ZipOutputStream(inputZipBaos).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write("<Types/>".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(sampleXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        // 2. Translate
        val translator = DocxTranslator(FakeEngine())
        val outputZipBaos = ByteArrayOutputStream()
        val result = translator.translateDocx(
            input = ByteArrayInputStream(inputZipBaos.toByteArray()),
            output = outputZipBaos,
            sourceLang = "en",
            targetLang = "zh",
        )

        assertEquals(2, result.translatedParagraphs)
        assertTrue(result.totalParagraphs >= 2)

        // 3. Inspect output zip
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(outputZipBaos.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val content = zis.readBytes().toString(Charsets.UTF_8)
                entries[entry.name] = content
                entry = zis.nextEntry
            }
        }

        assertNotNull(entries["[Content_Types].xml"])
        assertEquals("<Types/>", entries["[Content_Types].xml"])
        val outputDocXml = entries["word/document.xml"]
        assertNotNull(outputDocXml)
        assertTrue(outputDocXml!!.contains("译: Hello World!"))
    }
}
