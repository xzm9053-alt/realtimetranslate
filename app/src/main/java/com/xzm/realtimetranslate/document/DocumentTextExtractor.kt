package com.xzm.realtimetranslate.document

import java.io.InputStream

/**
 * 从一个文档里抽出要翻译的段落。
 *
 * 与 [DocxTranslator] / [PdfTranslator] 的区别：那两个是「读原格式 → 还写回原格式」，
 * 保留排版；这里只抽纯文本，输出一个双语对照的 .txt。
 * 用于 TXT / Markdown / Excel / PPT 这类「没有一个能原位回写文本的结构」的格式。
 */
interface DocumentTextExtractor {

    /** 每个元素是一段待翻译的文本（已 trim、已去空行）。 */
    fun extract(input: InputStream): List<String>
}
