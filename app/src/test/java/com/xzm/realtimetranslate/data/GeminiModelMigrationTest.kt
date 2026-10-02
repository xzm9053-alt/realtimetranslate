package com.xzm.realtimetranslate.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Changing a shipped default only affects installs that never stored one — every
 * existing install keeps whatever is on disk, including a model the provider has
 * since retired. This rewrite is what makes the new default actually reach them.
 */
class GeminiModelMigrationTest {

    @Test
    fun `the retired default is rewritten to the current one`() {
        assertEquals(
            UserSettings.Defaults.GEMINI_MODEL,
            migrateGeminiModel("gemini-2.5-flash"),
        )
    }

    @Test
    fun `a model the user chose is left alone`() {
        assertEquals("gemini-3.7-flash", migrateGeminiModel("gemini-3.7-flash"))
        assertEquals("my-custom-deployment", migrateGeminiModel("my-custom-deployment"))
    }

    @Test
    fun `an absent or blank value falls back to the default`() {
        assertEquals(UserSettings.Defaults.GEMINI_MODEL, migrateGeminiModel(null))
        assertEquals(UserSettings.Defaults.GEMINI_MODEL, migrateGeminiModel(""))
    }
}
