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
        assertFalse(state.globalVideoRecognizeOnly)
    }

    @Test
    fun `globalVideoRecognizeOnly flag defaults correctly`() {
        val state = VideoTranslateUiState(globalVideoRecognizeOnly = true)
        assertTrue(state.globalVideoRecognizeOnly)
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

    @Test
    fun `audio file sets isAudio true and disables canBurn while allowing canExport`() {
        val audioState = VideoTranslateUiState(
            uri = "content://media/audio1",
            displayName = "recording.mp3",
            isAudio = true,
            cues = listOf(VideoCue(0, 0L, 1000L, "Hello", "你好")),
        )
        assertTrue(audioState.isAudio)
        assertTrue(audioState.canExport)
        assertFalse(audioState.canBurn) // 音频文件无法压制画面字幕
    }

    @Test
    fun `video file sets isAudio false and allows canBurn`() {
        val videoState = VideoTranslateUiState(
            uri = "content://media/video1",
            displayName = "game.mp4",
            isAudio = false,
            cues = listOf(VideoCue(0, 0L, 1000L, "Hello", "你好")),
        )
        assertFalse(videoState.isAudio)
        assertTrue(videoState.canExport)
        assertTrue(videoState.canBurn)
    }

    @Test
    fun `isAudioFile recognizes audio extensions and mime types`() {
        assertTrue(VideoTranslateViewModel.isAudioFile("song.mp3", "audio/mpeg"))
        assertTrue(VideoTranslateViewModel.isAudioFile("interview.WAV", null))
        assertTrue(VideoTranslateViewModel.isAudioFile("voice.m4a", null))
        assertTrue(VideoTranslateViewModel.isAudioFile("music.flac", null))
        assertTrue(VideoTranslateViewModel.isAudioFile("track.aac", null))
        assertTrue(VideoTranslateViewModel.isAudioFile("sound.ogg", null))
        assertTrue(VideoTranslateViewModel.isAudioFile("speech.opus", null))
        assertTrue(VideoTranslateViewModel.isAudioFile("record.amr", null))

        assertFalse(VideoTranslateViewModel.isAudioFile("movie.mp4", "video/mp4"))
        assertFalse(VideoTranslateViewModel.isAudioFile("clip.mkv", null))
        assertFalse(VideoTranslateViewModel.isAudioFile("short.mov", null))
        assertFalse(VideoTranslateViewModel.isAudioFile("stream.ts", null))
    }
}
