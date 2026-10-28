package com.xzm.realtimetranslate.document

import com.xzm.realtimetranslate.ocr.TranslationPlanner
import com.xzm.realtimetranslate.translate.TranslationEngine
import com.xzm.realtimetranslate.util.AppLog as Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class DocxTranslationResult(
    val totalParagraphs: Int,
    val translatedParagraphs: Int,
)

class DocxTranslator(private val engine: TranslationEngine) {

    /**
     * Translates an entire .docx input stream and writes the modified .docx to output stream.
     */
    suspend fun translateDocx(
        input: InputStream,
        output: OutputStream,
        sourceLang: String,
        targetLang: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): DocxTranslationResult = withContext(Dispatchers.IO) {
        val zis = ZipInputStream(input)
        val zos = ZipOutputStream(output)
        var totalParagraphs = 0
        var translatedParagraphs = 0

        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            val newEntry = ZipEntry(entry.name)
            zos.putNextEntry(newEntry)

            if (entry.name == "word/document.xml") {
                val docXmlBytes = zis.readBytes()
                val (processedBytes, total, translated) = processDocumentXml(
                    ByteArrayInputStream(docXmlBytes),
                    sourceLang,
                    targetLang,
                    onProgress,
                )
                zos.write(processedBytes)
                totalParagraphs += total
                translatedParagraphs += translated
            } else {
                zis.copyTo(zos)
            }
            zos.closeEntry()
            entry = zis.nextEntry
        }
        zos.finish()
        DocxTranslationResult(totalParagraphs, translatedParagraphs)
    }

    /**
     * Extracts all `<w:p>` paragraph texts from `word/document.xml`, translates them,
     * writes back the translated text to the first `<w:t>` and clears subsequent `<w:t>`.
     */
    suspend fun extractAndReplaceParagraphs(
        documentXmlStream: InputStream,
        sourceLang: String,
        targetLang: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ByteArray {
        val (bytes, _, _) = processDocumentXml(documentXmlStream, sourceLang, targetLang, onProgress)
        return bytes
    }

    private suspend fun processDocumentXml(
        documentXmlStream: InputStream,
        sourceLang: String,
        targetLang: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Triple<ByteArray, Int, Int> = coroutineScope {
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        val db = dbf.newDocumentBuilder()
        val doc = db.parse(documentXmlStream)

        // Find all <w:p> elements
        val pElements = mutableListOf<Element>()
        collectParagraphElements(doc.documentElement, pElements)

        data class ParagraphTarget(
            val pElement: Element,
            val tNodes: List<Node>,
            val text: String,
        )

        val targets = mutableListOf<ParagraphTarget>()
        for (p in pElements) {
            val tNodes = collectParagraphTextNodes(p)
            if (tNodes.isEmpty()) continue
            val fullText = tNodes.joinToString("") { it.textContent ?: "" }
            if (fullText.isBlank()) continue
            if (!TranslationPlanner.shouldTranslate(fullText, targetLang)) continue
            targets.add(ParagraphTarget(p, tNodes, fullText))
        }

        val total = targets.size
        val progress = AtomicInteger(0)
        val semaphore = Semaphore(TRANSLATE_CONCURRENCY)

        val translations = targets.map { target ->
            async {
                semaphore.withPermit {
                    val out = try {
                        val translated = engine.translate(
                            text = target.text,
                            sourceLang = sourceLang,
                            targetLang = targetLang,
                        ).last()
                        if (translated.isNotBlank()) translated else target.text
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to translate docx paragraph: '${target.text.take(20)}'", t)
                        target.text
                    }
                    val done = progress.incrementAndGet()
                    runCatching {
                        onProgress(done, total)
                    }
                    out
                }
            }
        }.awaitAll()

        // Write back
        var translatedCount = 0
        for (i in targets.indices) {
            val target = targets[i]
            val translated = translations[i]
            if (translated != target.text) {
                translatedCount++
            }
            val firstNode = target.tNodes.first()
            firstNode.textContent = translated
            if (firstNode is Element && !firstNode.hasAttribute("xml:space")) {
                firstNode.setAttribute("xml:space", "preserve")
            }
            for (j in 1 until target.tNodes.size) {
                target.tNodes[j].textContent = ""
            }
        }

        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(OutputKeys.INDENT, "no")
        val baos = ByteArrayOutputStream()
        transformer.transform(DOMSource(doc), StreamResult(baos))

        Triple(baos.toByteArray(), pElements.size, translatedCount)
    }

    private fun collectParagraphElements(node: Node, out: MutableList<Element>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val localName = node.localName ?: node.nodeName.substringAfterLast(':')
            if (localName == "p") {
                out.add(node as Element)
            }
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            collectParagraphElements(children.item(i), out)
        }
    }

    private fun collectParagraphTextNodes(pNode: Node): List<Node> {
        val tNodes = mutableListOf<Node>()
        fun traverse(node: Node) {
            val children = node.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val localName = child.localName ?: child.nodeName.substringAfterLast(':')
                    if (localName == "p") {
                        // Do not recurse into nested paragraph
                        continue
                    }
                    if (localName == "t") {
                        tNodes.add(child)
                    } else {
                        traverse(child)
                    }
                }
            }
        }
        traverse(pNode)
        return tNodes
    }

    companion object {
        private const val TAG = "DocxTranslator"
        private const val TRANSLATE_CONCURRENCY = 4
    }
}
