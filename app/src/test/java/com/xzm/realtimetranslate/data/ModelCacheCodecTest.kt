package com.xzm.realtimetranslate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The model-check cache survives a restart by round-tripping through flat text. A
 * decoder that threw on a stray line would take the settings screen down with it, so
 * these pin the tolerant behaviour, not just the happy path.
 */
class ModelCacheCodecTest {

    @Test
    fun `a list of model ids round-trips`() {
        val ids = listOf("gemini-3.8-flash", "gemini-3.7-flash")
        assertEquals(ids, ModelCacheCodec.decodeList(ModelCacheCodec.encodeList(ids)))
    }

    @Test
    fun `blank entries are dropped rather than stored as empty models`() {
        assertEquals("a\nb", ModelCacheCodec.encodeList(listOf("a", "", "b")))
        assertEquals(emptyList<String>(), ModelCacheCodec.decodeList(null))
        assertEquals(emptyList<String>(), ModelCacheCodec.decodeList(""))
    }

    @Test
    fun `an availability map round-trips`() {
        val map = mapOf("gemini-3.8-flash" to "AVAILABLE", "gemini-3.7-flash" to "RETIRED")
        assertEquals(map, ModelCacheCodec.decodeMap(ModelCacheCodec.encodeMap(map)))
    }

    @Test
    fun `a malformed line is skipped instead of throwing`() {
        // No tab at all, an empty id, and an empty status — all must be ignored.
        assertEquals(
            mapOf("good" to "AVAILABLE"),
            ModelCacheCodec.decodeMap("no-tab-here\n\tAVAILABLE\ngood\tAVAILABLE\nlonely\t"),
        )
        assertEquals(emptyMap<String, String>(), ModelCacheCodec.decodeMap(null))
    }

    @Test
    fun `an id containing spaces still round-trips`() {
        // Split on the first tab only, so anything after it stays intact.
        val map = mapOf("weird model name" to "AVAILABLE")
        assertTrue(map == ModelCacheCodec.decodeMap(ModelCacheCodec.encodeMap(map)))
    }
}
