package com.xzm.realtimetranslate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalAsrModelCatalogTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testPresetsCountAndIds() {
        val ids = LocalAsrModelCatalog.PRESETS.map { it.id }
        assertEquals(8, ids.size)
        val expected = listOf(
            "sensevoice-small",
            "paraformer-zh",
            "whisper-tiny",
            "whisper-small",
            "whisper-large-v3-turbo",
            "moonshine-tiny",
            "qwen3-asr-0.6b",
            "fire-red-asr",
        )
        for (exp in expected) {
            assertTrue("Expected preset $exp to exist", ids.contains(exp))
        }
    }

    @Test
    fun testPresetMetadataAndUrls() {
        for (preset in LocalAsrModelCatalog.PRESETS) {
            assertTrue(preset.id.isNotBlank())
            assertTrue(preset.name.isNotBlank())
            assertTrue(preset.description.isNotBlank())
            assertTrue(preset.sizeMb > 0)
            assertTrue("domesticDownloadUrl must start with https://", preset.domesticDownloadUrl.startsWith("https://"))
            assertTrue("mirrorUrl must start with https://", preset.mirrorUrl.startsWith("https://"))
            assertTrue("supportedLangs must not be empty", preset.supportedLangs.isNotEmpty())
        }
    }

    @Test
    fun testDraftSuppressionRules() {
        // 非自回归或超低延迟模型：支持高频草稿
        assertTrue(LocalAsrModelCatalog.getPresetById("sensevoice-small").supportsDraft)
        assertTrue(LocalAsrModelCatalog.getPresetById("paraformer-zh").supportsDraft)
        assertTrue(LocalAsrModelCatalog.getPresetById("moonshine-tiny").supportsDraft)

        // 自回归或重型 Speech-LLM 模型：必须抑制高频草稿以防手机 CPU 积压发热
        assertFalse(LocalAsrModelCatalog.getPresetById("whisper-tiny").supportsDraft)
        assertFalse(LocalAsrModelCatalog.getPresetById("whisper-small").supportsDraft)
        assertFalse(LocalAsrModelCatalog.getPresetById("whisper-large-v3-turbo").supportsDraft)
        assertFalse(LocalAsrModelCatalog.getPresetById("qwen3-asr-0.6b").supportsDraft)
        assertFalse(LocalAsrModelCatalog.getPresetById("fire-red-asr").supportsDraft)
    }

    @Test
    fun testGetPresetByIdFallback() {
        val found = LocalAsrModelCatalog.getPresetById("whisper-large-v3-turbo")
        assertEquals(LocalAsrArch.WHISPER, found.architecture)
        assertEquals(650, found.sizeMb)

        val nonExistent = LocalAsrModelCatalog.getPresetById("unknown-model-xyz")
        assertEquals(LocalAsrModelCatalog.DEFAULT_MODEL_ID, nonExistent.id)
    }

    @Test
    fun testCheckFilesForArchSenseVoiceAndParaformer() {
        val dir = tempFolder.newFolder("sensevoice_test")
        assertNull(LocalAsrModelCatalog.checkFilesForArch(dir, LocalAsrArch.SENSE_VOICE))

        File(dir, "model.int8.onnx").writeBytes(byteArrayOf(1, 2, 3))
        File(dir, "tokens.txt").writeText("hello\nworld")

        val files = LocalAsrModelCatalog.checkFilesForArch(dir, LocalAsrArch.SENSE_VOICE)
        assertNotNull(files)
        assertEquals("model.int8.onnx", files!!["model_file"]?.name)
        assertEquals("tokens.txt", files["tokens_file"]?.name)

        // Paraformer checks the same structure
        val paraFiles = LocalAsrModelCatalog.checkFilesForArch(dir, LocalAsrArch.PARAFORMER)
        assertNotNull(paraFiles)
    }

    @Test
    fun testCheckFilesForArchWhisper() {
        val dir = tempFolder.newFolder("whisper_test")
        File(dir, "large-v3-turbo-encoder.int8.onnx").writeBytes(byteArrayOf(1))
        File(dir, "large-v3-turbo-decoder.int8.onnx").writeBytes(byteArrayOf(2))
        File(dir, "large-v3-turbo-tokens.txt").writeText("tokens")

        val files = LocalAsrModelCatalog.checkFilesForArch(dir, LocalAsrArch.WHISPER)
        assertNotNull(files)
        assertTrue(files!!["encoder_file"]!!.name.contains("encoder"))
        assertTrue(files["decoder_file"]!!.name.contains("decoder"))
        assertTrue(files["tokens_file"]!!.name.contains("tokens"))
    }

    @Test
    fun testCheckFilesForArchMoonshine() {
        val dir = tempFolder.newFolder("moonshine_test")
        File(dir, "preprocess.onnx").writeBytes(byteArrayOf(1))
        File(dir, "encode.onnx").writeBytes(byteArrayOf(2))
        File(dir, "uncached_decode.onnx").writeBytes(byteArrayOf(3))
        File(dir, "cached_decode.onnx").writeBytes(byteArrayOf(4))
        File(dir, "tokens.txt").writeText("tokens")

        val files = LocalAsrModelCatalog.checkFilesForArch(dir, LocalAsrArch.MOONSHINE)
        assertNotNull(files)
        assertEquals("preprocess.onnx", files!!["prep_file"]?.name)
        assertEquals("encode.onnx", files["enc_file"]?.name)
    }

    @Test
    fun testCheckFilesForArchQwen3Asr() {
        val dir = tempFolder.newFolder("qwen3_test")
        File(dir, "conv_frontend.onnx").writeBytes(byteArrayOf(1))
        File(dir, "qwen3-encoder.int8.onnx").writeBytes(byteArrayOf(2))
        File(dir, "qwen3-decoder.int8.onnx").writeBytes(byteArrayOf(3))
        File(dir, "tokenizer.json").writeText("{}")

        val files = LocalAsrModelCatalog.checkFilesForArch(dir, LocalAsrArch.QWEN3_ASR)
        assertNotNull(files)
        assertEquals("conv_frontend.onnx", files!!["conv_frontend"]?.name)
        assertTrue(files["encoder"]!!.name.contains("encoder"))
    }

    @Test
    fun testCheckFilesForArchFireRedAsr() {
        val dir = tempFolder.newFolder("firered_test")
        File(dir, "firered-encoder.int8.onnx").writeBytes(byteArrayOf(1))
        File(dir, "firered-decoder.int8.onnx").writeBytes(byteArrayOf(2))
        File(dir, "tokens.txt").writeText("tokens")

        val files = LocalAsrModelCatalog.checkFilesForArch(dir, LocalAsrArch.FIRE_RED_ASR)
        assertNotNull(files)
        assertTrue(files!!["encoder_file"]!!.name.contains("encoder"))
        assertTrue(files["decoder_file"]!!.name.contains("decoder"))
    }

    @Test
    fun testProbeSubdirectoryTolerance() {
        val root = tempFolder.newFolder("search_root")
        val modelDir = File(root, "paraformer-zh")
        // 模拟用户解压多出一层 wrapper 子目录
        val nestedSubDir = File(modelDir, "sherpa-onnx-paraformer-zh-2024-03-09")
        nestedSubDir.mkdirs()

        File(nestedSubDir, "model.int8.onnx").writeBytes(byteArrayOf(1))
        File(nestedSubDir, "tokens.txt").writeText("tokens")

        // 探针检索 candidates 中有 modelDir
        val result = LocalAsrModelCatalog.checkFilesForArch(nestedSubDir, LocalAsrArch.PARAFORMER)
        assertNotNull(result)

        // 验证上级目录直检失败，但子目录检索成功
        assertNull(LocalAsrModelCatalog.checkFilesForArch(modelDir, LocalAsrArch.PARAFORMER))
        val subDirs = modelDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        assertEquals(1, subDirs.size)
        val subResult = LocalAsrModelCatalog.checkFilesForArch(subDirs[0], LocalAsrArch.PARAFORMER)
        assertNotNull(subResult)
    }
}
