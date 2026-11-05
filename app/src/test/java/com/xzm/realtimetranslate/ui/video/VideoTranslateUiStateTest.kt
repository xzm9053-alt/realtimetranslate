package com.xzm.realtimetranslate.ui.video

import com.xzm.realtimetranslate.data.LocalAsrModelCatalog
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.video.VideoCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTranslateUiStateTest {

    @Test
    fun `default recognizeOnly is false`() {
        val state = VideoTranslateUiState()
        assertFalse(state.recognizeOnly)
    }

    @Test
    fun `recognizeOnly state preserves canStart canExport canBurn`() {
        val stateWithVideo = VideoTranslateUiState(
            uri = "content://media/1",
            recognizeOnly = true,
        )
        assertTrue(stateWithVideo.hasVideo)
        assertTrue(stateWithVideo.canStart)
        assertFalse(stateWithVideo.canExport)
        assertFalse(stateWithVideo.canBurn)

        val stateWithCues = stateWithVideo.copy(
            cues = listOf(VideoCue(0, 0L, 1000L, "Hello", "Hello")),
        )
        assertTrue(stateWithCues.canExport)
        assertTrue(stateWithCues.canBurn)
    }

    @Test
    fun `default state follows global engines with isOverridden false`() {
        val state = VideoTranslateUiState(
            globalTranslationEngine = TranslationEngineType.DEEPSEEK,
            globalLocalAsrModelId = "paraformer-zh",
        )
        assertEquals(TranslationEngineType.DEEPSEEK, state.effectiveTranslationEngine)
        assertEquals("paraformer-zh", state.effectiveLocalAsrModelId)
        assertEquals("paraformer-zh", state.effectiveAsrPreset.id)
        assertFalse(state.isOverridden)
        assertNull(state.overrideTranslationEngine)
        assertNull(state.overrideLocalAsrModelId)
    }

    @Test
    fun `overriding translation engine changes effective and sets isOverridden`() {
        val state = VideoTranslateUiState(
            globalTranslationEngine = TranslationEngineType.DEEPSEEK,
            overrideTranslationEngine = TranslationEngineType.MICROSOFT,
        )
        assertEquals(TranslationEngineType.MICROSOFT, state.effectiveTranslationEngine)
        assertTrue(state.isOverridden)
    }

    @Test
    fun `overriding asr model changes effective preset and sets isOverridden`() {
        val state = VideoTranslateUiState(
            globalLocalAsrModelId = "sensevoice-small",
            overrideLocalAsrModelId = "whisper-large-v3-turbo",
        )
        assertEquals("whisper-large-v3-turbo", state.effectiveLocalAsrModelId)
        assertEquals("whisper-large-v3-turbo", state.effectiveAsrPreset.id)
        assertTrue(state.isOverridden)
    }

    @Test
    fun `clearing overrides restores global defaults`() {
        val overridden = VideoTranslateUiState(
            globalTranslationEngine = TranslationEngineType.ZHIPU,
            overrideTranslationEngine = TranslationEngineType.GEMINI,
            globalLocalAsrModelId = "paraformer-zh",
            overrideLocalAsrModelId = "whisper-tiny",
        )
        assertTrue(overridden.isOverridden)
        assertEquals(TranslationEngineType.GEMINI, overridden.effectiveTranslationEngine)
        assertEquals("whisper-tiny", overridden.effectiveLocalAsrModelId)

        val restored = overridden.copy(
            overrideTranslationEngine = null,
            overrideLocalAsrModelId = null,
        )
        assertFalse(restored.isOverridden)
        assertEquals(TranslationEngineType.ZHIPU, restored.effectiveTranslationEngine)
        assertEquals("paraformer-zh", restored.effectiveLocalAsrModelId)
    }
}
