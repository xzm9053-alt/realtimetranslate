package com.xzm.realtimetranslate.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubSettingsTest {

    @Test
    fun `hub enabled defaults to false`() {
        val settings = UserSettings()
        assertFalse(settings.hubEnabled)
    }

    @Test
    fun `hub enabled can be toggled`() {
        val settings = UserSettings(hubEnabled = true)
        assertTrue(settings.hubEnabled)
    }
}
