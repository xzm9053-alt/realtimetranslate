package com.xzm.realtimetranslate.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstInstallGuideSettingsTest {

    @Test
    fun `first install guide shown defaults to false`() {
        val settings = UserSettings()
        assertFalse(settings.firstInstallGuideShown)
    }

    @Test
    fun `first install guide shown can be marked true`() {
        val settings = UserSettings(firstInstallGuideShown = true)
        assertTrue(settings.firstInstallGuideShown)
    }
}
