package com.xzm.realtimetranslate.document

import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * 纯 Kotlin 的文档抽取器集合，不引入任何第三方库。
 *
 * xlsx / pptx 本质上都是 zip + XML，与 docx 同一套做法；为了读两张表就引一个
 * 几 MB 的库不划算——APK 已经 430MB 了。
 */
object DocumentTextExtractors {

    fun forType(type: DocumentType): DocumentTextExtractor? = when (type) {
        DocumentType.TXT, DocumentType.MARKDOWN -> PlainTextExtractor
        DocumentType.XLSX -> XlsxTextExtractor
        DocumentType.PPTX -> PptxTextExtractor
        else -> null // PDF / DOCX 走原格式回写，不走这条
    }
}

/** 纯文本：逐行一段。编码依次尝试 UTF-8 / GB18030 / UTF-16。 */
internal object PlainTextExtractor : DocumentTextExtractor {
    override fun extract(input: InputStream): List<String> {
        val raw = input.readBytes()
        val text = decodeSmart(raw)
        return text.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** BOM 优先，否则按 UTF-8 → GB18030 顺序试解码，都不行就用替换符不丢内容。 */
    private fun decodeSmart(raw: ByteArray): String {
        if (raw.size >= 3 && raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte()) {
            return String(raw, 3, raw.size - 3, Charsets.UTF_8)
        }
        if (raw.size >= 2 && raw[0] == 0xFF.toByte() && raw[1] == 0xFE.toByte()) {
            return String(raw, 2, raw.size - 2, Charsets.UTF_16LE)
        }
        if (raw.size >= 2 && raw[0] == 0xFE.toByte() && raw[1] == 0xFF.toByte()) {
            return String(raw, 2, raw.size - 2, Charsets.UTF_16BE)
        }
        return runCatching {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw)).toString()
        }.getOrElse {
            runCatching { String(raw, charset("GB18030")) }.getOrDefault(String(raw, Charsets.UTF_8))
        }
    }
}

/** xlsx：每一行拼成一段，单元格用 " | " 隔开（与电脑版一致）。 */
internal object XlsxTextExtractor : DocumentTextExtractor {
    override fun extract(input: InputStream): List<String> {
        val entries = readZipEntries(input)
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheetNames = entries.keys
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sorted()

        val out = ArrayList<String>()
        for (name in sheetNames) {
            val rows = parseSheetRows(entries[name]!!, sharedStrings)
            out += rows
        }
        return out
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(xml.inputStream())
        val nodes = doc.getElementsByTagName("si")
        return (0 until nodes.length).map { i ->
            val si = nodes.item(i) as Element
            // 一个 <si> 里可能有多个 <t>（富文本分片），必须拼起来
            val ts = si.getElementsByTagName("t")
            (0 until ts.length).joinToString("") { ts.item(it).textContent.orEmpty() }
        }
    }

    private fun parseSheetRows(xml: ByteArray, shared: List<String>): List<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(xml.inputStream())
        val rowNodes = doc.getElementsByTagName("row")
        val out = ArrayList<String>()
        for (i in 0 until rowNodes.length) {
            val row = rowNodes.item(i) as Element
            val cells = row.getElementsByTagName("c")
            val parts = ArrayList<String>()
            for (j in 0 until cells.length) {
                val cell = cells.item(j) as Element
                val type = cell.getAttribute("t")
                val v = firstChildText(cell, "v")
                val text = when {
                    // t="s" 表示这个值是 sharedStrings 里的下标
                    type == "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) }
                    type == "inlineStr" -> firstChildText(cell, "t")
                    else -> v
                }
                text?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it }
            }
            if (parts.isNotEmpty()) out += parts.joinToString(" | ")
        }
        return out
    }
}

/** pptx：逐页抽文本框，每个段落一行。 */
internal object PptxTextExtractor : DocumentTextExtractor {
    override fun extract(input: InputStream): List<String> {
        val entries = readZipEntries(input)
        val slideNames = entries.keys
            .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
            // slide2.xml 要排在 slide10.xml 前面，字符串排序会把 10 插到 2 前面
            .sortedBy { Regex("(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }

        val out = ArrayList<String>()
        for (name in slideNames) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(entries[name]!!.inputStream())
            // 每个 <a:p> 是一个段落；其下的 <a:t> 是这个段落里的文字切片，需拼接
            val paragraphs = doc.getElementsByTagName("a:p")
            for (i in 0 until paragraphs.length) {
                val p = paragraphs.item(i) as Element
                val ts = p.getElementsByTagName("a:t")
                val text = (0 until ts.length).joinToString("") { ts.item(it).textContent.orEmpty() }
                    .trim()
                if (text.isNotEmpty()) out += text
            }
        }
        return out
    }
}

/** 把 zip 里所有条目读进内存（只读文本类 xml）。 */
private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
    val out = HashMap<String, ByteArray>()
    ZipInputStream(input).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.endsWith(".xml")) {
                out[entry.name] = zis.readBytes()
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
    return out
}

private fun firstChildText(parent: Element, tag: String): String? {
    var child: Node? = parent.firstChild
    while (child != null) {
        if (child is Element && child.tagName == tag) return child.textContent
        child = child.nextSibling
    }
    return null
}
