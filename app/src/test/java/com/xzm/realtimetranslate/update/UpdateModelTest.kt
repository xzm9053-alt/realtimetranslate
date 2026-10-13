package com.xzm.realtimetranslate.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateModelTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun parseValidJson_returnsCorrectModel() {
        val raw = """
            {
                "versionCode": 2,
                "versionName": "v0.2.0",
                "title": "新版发布",
                "message": "更新说明",
                "videoUrl": "https://b23.tv/demo",
                "forceUpdate": false
            }
        """.trimIndent()
        val info = json.decodeFromString<RemoteUpdateInfo>(raw)
        assertEquals(2, info.versionCode)
        assertEquals("v0.2.0", info.versionName)
        assertEquals("新版发布", info.title)
        assertEquals("更新说明", info.message)
        assertEquals("https://b23.tv/demo", info.videoUrl)
        assertFalse(info.forceUpdate)
    }

    @Test
    fun parseJsonWithMissingOptionalFields_usesDefaults() {
        val raw = """
            {
                "versionCode": 3,
                "versionName": "v0.3.0",
                "videoUrl": "https://b23.tv/demo"
            }
        """.trimIndent()
        val info = json.decodeFromString<RemoteUpdateInfo>(raw)
        assertEquals(3, info.versionCode)
        assertEquals("https://b23.tv/demo", info.videoUrl)
        assertEquals("发现新版本", info.title)
        assertEquals("", info.message)
        assertFalse(info.forceUpdate)
    }
}
