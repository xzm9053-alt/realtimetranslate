package com.xzm.realtimetranslate.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The generic engine's "don't think" spelling is the one setting whose wrong value is
 * silently expensive: a backend that rejects the parameter fails the request with 400,
 * and one that ignores it leaves thinking on, so every sentence waits seconds.
 *
 * What is testable without a network is that the value survives DataStore, and that
 * anything unreadable degrades to [ThinkingOffStyle.NONE] — the only choice that cannot
 * break a request against a service this app has never seen.
 */
class ThinkingOffStyleTest {

    @Test
    fun `stored values round-trip`() {
        ThinkingOffStyle.entries.forEach { style ->
            assertEquals(style, ThinkingOffStyle.fromStorage(style.name))
        }
    }

    @Test
    fun `missing or unreadable values fall back to sending nothing`() {
        // Absent key on a fresh install.
        assertEquals(ThinkingOffStyle.NONE, ThinkingOffStyle.fromStorage(null))
        assertEquals(ThinkingOffStyle.NONE, ThinkingOffStyle.fromStorage(""))
        assertEquals(ThinkingOffStyle.NONE, ThinkingOffStyle.fromStorage("NOT_A_STYLE"))
        // A name from a spelling that was later renamed must not throw either.
        assertEquals(ThinkingOffStyle.NONE, ThinkingOffStyle.fromStorage("thinking_object"))
    }

    @Test
    fun `every style labels itself`() {
        // The settings screen iterates entries and calls stringResource(labelRes) with
        // no lookup, so an unlabelled value would render as a resource id or crash.
        val labels = ThinkingOffStyle.entries.map { it.labelRes }
        assertEquals(ThinkingOffStyle.entries.size, labels.distinct().size)
        assertEquals(ThinkingOffStyle.entries.size, labels.count { it != 0 })
    }
}
