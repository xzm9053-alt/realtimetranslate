package com.xzm.realtimetranslate.video

import com.xzm.realtimetranslate.data.LocalAsrArch
import com.xzm.realtimetranslate.data.LocalAsrModelCatalog
import com.xzm.realtimetranslate.data.LocalAsrProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class LocalBatchRecognizerTest {

    @Test
    fun testCleanResultTextForSenseVoice() {
        assertEquals("你好世界", LocalBatchRecognizer.cleanResultText("<|zh|><|NEUTRAL|>你好世界", LocalAsrArch.SENSE_VOICE))
        assertEquals("Hello world", LocalBatchRecognizer.cleanResultText("<|en|><|HAPPY|>Hello world", LocalAsrArch.SENSE_VOICE))
        assertNull(LocalBatchRecognizer.cleanResultText("<|zh|>Nose", LocalAsrArch.SENSE_VOICE))
        assertNull(LocalBatchRecognizer.cleanResultText("  ", LocalAsrArch.SENSE_VOICE))
        assertNull(LocalBatchRecognizer.cleanResultText(null, LocalAsrArch.SENSE_VOICE))
    }

    @Test
    fun testCleanResultTextForOtherArch() {
        assertEquals("会议记录测试", LocalBatchRecognizer.cleanResultText("  会议记录测试  ", LocalAsrArch.PARAFORMER))
        assertEquals("Whisper transcript text", LocalBatchRecognizer.cleanResultText("Whisper transcript text", LocalAsrArch.WHISPER))
        assertNull(LocalBatchRecognizer.cleanResultText("   ", LocalAsrArch.WHISPER))
        assertNull(LocalBatchRecognizer.cleanResultText("null", LocalAsrArch.QWEN3_ASR))
        assertNull(LocalBatchRecognizer.cleanResultText(null, LocalAsrArch.MOONSHINE))
    }

    @Test
    fun testFallbackDecision() {
        val paraformer = LocalAsrModelCatalog.getPresetById("paraformer-zh")
        // 未就绪时回退到 SenseVoice
        val fallbackPreset = LocalBatchRecognizer.resolveActivePreset(paraformer, null)
        assertEquals("sensevoice-small", fallbackPreset.id)

        val unreadyProbe = LocalAsrProbeResult(isReady = false, modelDir = null, files = emptyMap())
        val fallbackPreset2 = LocalBatchRecognizer.resolveActivePreset(paraformer, unreadyProbe)
        assertEquals("sensevoice-small", fallbackPreset2.id)

        // 就绪时保留目标模型
        val readyProbe = LocalAsrProbeResult(isReady = true, modelDir = File("/test"), files = mapOf("model_file" to File("/test/model.onnx")))
        val activePreset = LocalBatchRecognizer.resolveActivePreset(paraformer, readyProbe)
        assertEquals("paraformer-zh", activePreset.id)
    }

    @Test
    fun testLanguageNormalization() {
        assertEquals("", LocalBatchRecognizer.normalizeLanguageForArch("auto", LocalAsrArch.WHISPER))
        assertEquals("zh", LocalBatchRecognizer.normalizeLanguageForArch("zh-Hans", LocalAsrArch.WHISPER))
        assertEquals("en", LocalBatchRecognizer.normalizeLanguageForArch("en", LocalAsrArch.WHISPER))

        assertEquals("auto", LocalBatchRecognizer.normalizeLanguageForArch("auto", LocalAsrArch.SENSE_VOICE))
        assertEquals("zh", LocalBatchRecognizer.normalizeLanguageForArch("zh-Hans", LocalAsrArch.SENSE_VOICE))
    }
}
