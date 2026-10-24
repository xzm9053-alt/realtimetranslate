package com.xzm.realtimetranslate.translate

/**
 * Session-scoped LRU of finished translations. Cover modes re-recognize the whole
 * screen every ~1.2s, so without this the same paragraphs would be re-billed on
 * every frame.
 */
class TranslationCache(private val capacity: Int = 300) {

    private val entries = object : LinkedHashMap<String, String>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > capacity
    }

    val size: Int
        @Synchronized get() = entries.size

    @Synchronized
    fun get(key: String): String? = entries[key]

    @Synchronized
    fun put(key: String, value: String) {
        entries[key] = value
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
