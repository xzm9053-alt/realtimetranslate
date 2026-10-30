package com.xzm.realtimetranslate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrAppearanceSettingsTest {

    @Test
    fun `ocr appearance settings default to expected values`() {
        val settings = UserSettings()
        assertTrue(settings.ocrRegionOutlineEnabled)
        assertEquals(UserSettings.Defaults.OCR_OUTLINE_COLOR, settings.ocrRegionOutlineColor)
        assertEquals(UserSettings.Defaults.OCR_OUTLINE_ALPHA, settings.ocrRegionOutlineAlpha, 0.001f)
    }

    @Test
    fun `ocr appearance settings can be customized`() {
        val customized = UserSettings(
            ocrRegionOutlineEnabled = false,
            ocrRegionOutlineColor = 0xFFFF5252,
            ocrRegionOutlineAlpha = 0.8f,
        )
        assertEquals(false, customized.ocrRegionOutlineEnabled)
        assertEquals(0xFFFF5252, customized.ocrRegionOutlineColor)
        assertEquals(0.8f, customized.ocrRegionOutlineAlpha, 0.001f)
    }

    @Test
    fun `ocr appearance reset restores all defaults`() {
        val customized = UserSettings(
            ocrRegionOutlineEnabled = false,
            ocrRegionOutlineColor = 0xFFFF5252,
            ocrRegionOutlineAlpha = 0.8f,
            fontSizeSp = 24f,
            translationTextColor = 0xFF00FF00,
        )
        val reset = customized.copy(
            ocrRegionOutlineEnabled = UserSettings.Defaults.OCR_OUTLINE_ENABLED,
            ocrRegionOutlineColor = UserSettings.Defaults.OCR_OUTLINE_COLOR,
            ocrRegionOutlineAlpha = UserSettings.Defaults.OCR_OUTLINE_ALPHA,
            fontSizeSp = UserSettings.Defaults.FONT_SIZE_SP,
            translationTextColor = UserSettings.Defaults.TRANSLATION_TEXT_COLOR,
        )
        assertTrue(reset.ocrRegionOutlineEnabled)
        assertEquals(UserSettings.Defaults.OCR_OUTLINE_COLOR, reset.ocrRegionOutlineColor)
        assertEquals(UserSettings.Defaults.OCR_OUTLINE_ALPHA, reset.ocrRegionOutlineAlpha, 0.001f)
        assertEquals(UserSettings.Defaults.FONT_SIZE_SP, reset.fontSizeSp, 0.001f)
        assertEquals(UserSettings.Defaults.TRANSLATION_TEXT_COLOR, reset.translationTextColor)
    }
}
