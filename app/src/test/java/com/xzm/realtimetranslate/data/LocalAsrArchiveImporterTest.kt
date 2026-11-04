package com.xzm.realtimetranslate.data

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalAsrArchiveImporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testUnpackZipExtractsFilesAndBlocksTraversal() {
        val outDir = tempFolder.newFolder("unpacked_zip")
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Normal file
            zos.putNextEntry(ZipEntry("model.int8.onnx"))
            zos.write("fake-onnx-content".toByteArray())
            zos.closeEntry()

            // Normal file inside folder
            zos.putNextEntry(ZipEntry("sub/tokens.txt"))
            zos.write("hello 0".toByteArray())
            zos.closeEntry()

            // Path traversal entry that should be blocked
            zos.putNextEntry(ZipEntry("../evil.txt"))
            zos.write("evil".toByteArray())
            zos.closeEntry()
        }

        val count = LocalAsrArchiveImporter.unpackStream(
            inputStream = ByteArrayInputStream(baos.toByteArray()),
            archiveName = "model.zip",
            outputDir = outDir,
        )

        assertTrue(count >= 2)
        assertTrue(File(outDir, "model.int8.onnx").exists())
        assertTrue(File(outDir, "sub/tokens.txt").exists())
        assertFalse(File(outDir.parentFile, "evil.txt").exists())
    }

    @Test
    fun testUnpackTarGzExtractsFiles() {
        val outDir = tempFolder.newFolder("unpacked_targz")
        val baos = ByteArrayOutputStream()
        GzipCompressorOutputStream(baos).use { gzos ->
            TarArchiveOutputStream(gzos).use { tos ->
                val entry1 = TarArchiveEntry("encoder.onnx")
                val content1 = "encoder-content".toByteArray()
                entry1.size = content1.size.toLong()
                tos.putArchiveEntry(entry1)
                tos.write(content1)
                tos.closeArchiveEntry()

                val entry2 = TarArchiveEntry("tokens.txt")
                val content2 = "tokens-content".toByteArray()
                entry2.size = content2.size.toLong()
                tos.putArchiveEntry(entry2)
                tos.write(content2)
                tos.closeArchiveEntry()
            }
        }

        val count = LocalAsrArchiveImporter.unpackStream(
            inputStream = ByteArrayInputStream(baos.toByteArray()),
            archiveName = "whisper.tar.gz",
            outputDir = outDir,
        )

        assertEquals(2, count)
        assertTrue(File(outDir, "encoder.onnx").exists())
        assertTrue(File(outDir, "tokens.txt").exists())
    }

    @Test
    fun testUnpackTarBz2ExtractsFiles() {
        val outDir = tempFolder.newFolder("unpacked_tarbz2")
        val baos = ByteArrayOutputStream()
        BZip2CompressorOutputStream(baos).use { bzos ->
            TarArchiveOutputStream(bzos).use { tos ->
                val entry1 = TarArchiveEntry("paraformer/model.int8.onnx")
                val content1 = "paraformer-bytes".toByteArray()
                entry1.size = content1.size.toLong()
                tos.putArchiveEntry(entry1)
                tos.write(content1)
                tos.closeArchiveEntry()

                val entry2 = TarArchiveEntry("paraformer/tokens.txt")
                val content2 = "token-bytes".toByteArray()
                entry2.size = content2.size.toLong()
                tos.putArchiveEntry(entry2)
                tos.write(content2)
                tos.closeArchiveEntry()
            }
        }

        val count = LocalAsrArchiveImporter.unpackStream(
            inputStream = ByteArrayInputStream(baos.toByteArray()),
            archiveName = "paraformer.tar.bz2",
            outputDir = outDir,
        )

        assertEquals(2, count)
        assertTrue(File(outDir, "paraformer/model.int8.onnx").exists())
        assertTrue(File(outDir, "paraformer/tokens.txt").exists())
    }

    @Test
    fun testDetectMatchingPresetWithWrapperDirectory() {
        val root = tempFolder.newFolder("wrapper_test")
        val wrapper = File(root, "sherpa-onnx-whisper-tiny").apply { mkdirs() }
        File(wrapper, "tiny-encoder.int8.onnx").writeText("enc")
        File(wrapper, "tiny-decoder.int8.onnx").writeText("dec")
        File(wrapper, "tokens.txt").writeText("tokens")

        val match = LocalAsrArchiveImporter.detectMatchingPreset(root, preferredModelId = "whisper-tiny")
        assertNotNull(match)
        assertEquals("whisper-tiny", match?.preset?.id)
        assertEquals(wrapper.canonicalPath, match?.modelDir?.canonicalPath)
    }

    @Test
    fun testDeployModelFilesMovesAndCleansUp() {
        val extractedRoot = tempFolder.newFolder("extract_src")
        val sourceDir = File(extractedRoot, "sub-paraformer").apply { mkdirs() }
        File(sourceDir, "model.int8.onnx").writeText("onnx-model")
        File(sourceDir, "tokens.txt").writeText("tokens")

        val destinationDir = File(tempFolder.newFolder("app_files"), "models/asr/paraformer-zh")

        val match = LocalAsrArchiveImporter.detectMatchingPreset(extractedRoot, preferredModelId = "paraformer-zh")
        assertNotNull(match)

        val success = LocalAsrArchiveImporter.deployModelFiles(
            sourceDir = match!!.modelDir,
            destinationDir = destinationDir,
            preset = match.preset,
        )

        assertTrue(success)
        assertTrue(File(destinationDir, "model.int8.onnx").exists())
        assertTrue(File(destinationDir, "tokens.txt").exists())
    }

    @Test
    fun testDetectMatchingPresetQwen3WithTokenizerSubdir() {
        val root = tempFolder.newFolder("qwen3_wrapper_test")
        val qwenDir = File(root, "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25").apply { mkdirs() }
        File(qwenDir, "conv_frontend.onnx").writeText("conv")
        File(qwenDir, "encoder.int8.onnx").writeText("enc")
        File(qwenDir, "decoder.int8.onnx").writeText("dec")
        val tokDir = File(qwenDir, "tokenizer").apply { mkdirs() }
        File(tokDir, "vocab.json").writeText("{}")
        File(tokDir, "tokenizer_config.json").writeText("{}")

        val match = LocalAsrArchiveImporter.detectMatchingPreset(root, preferredModelId = "qwen3-asr-0.6b")
        assertNotNull(match)
        assertEquals("qwen3-asr-0.6b", match?.preset?.id)
        assertEquals(qwenDir.canonicalPath, match?.modelDir?.canonicalPath)

        val destinationDir = File(tempFolder.newFolder("dest_qwen3"), "models/asr/qwen3-asr-0.6b")
        val success = LocalAsrArchiveImporter.deployModelFiles(match!!.modelDir, destinationDir, match.preset)
        assertTrue(success)
        assertTrue(File(destinationDir, "conv_frontend.onnx").exists())
        assertTrue(File(destinationDir, "encoder.int8.onnx").exists())
        assertTrue(File(destinationDir, "decoder.int8.onnx").exists())
        assertTrue(File(destinationDir, "tokenizer").isDirectory)
        assertTrue(File(destinationDir, "tokenizer/vocab.json").exists())
    }

    @Test
    fun testDeployRejectsInvalidEmptyDir() {
        val emptyDir = tempFolder.newFolder("empty_dir")
        val destinationDir = tempFolder.newFolder("dest_dir")
        val match = LocalAsrArchiveImporter.detectMatchingPreset(emptyDir, preferredModelId = null)
        assertNull(match)
    }
}
