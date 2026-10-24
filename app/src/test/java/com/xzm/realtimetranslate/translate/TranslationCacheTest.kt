package com.xzm.realtimetranslate.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationCacheTest {

    @Test
    fun `get returns what was put`() {
        val cache = TranslationCache()
        cache.put("hello", "你好")
        assertEquals("你好", cache.get("hello"))
    }

    @Test
    fun `least recently used entry is evicted first`() {
        val cache = TranslationCache(capacity = 2)
        cache.put("a", "甲")
        cache.put("b", "乙")
        cache.get("a")
        cache.put("c", "丙")
        assertNull(cache.get("b"))
        assertEquals("甲", cache.get("a"))
        assertEquals("丙", cache.get("c"))
    }

    @Test
    fun `putting the same key updates without growing`() {
        val cache = TranslationCache(capacity = 2)
        cache.put("a", "甲")
        cache.put("a", "甲2")
        assertEquals(1, cache.size)
        assertEquals("甲2", cache.get("a"))
    }

    @Test
    fun `clear empties the cache`() {
        val cache = TranslationCache()
        cache.put("a", "甲")
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.get("a"))
    }
}
