package com.xzm.realtimetranslate.ui.video

import com.xzm.realtimetranslate.video.VideoCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
