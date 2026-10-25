package com.xzm.realtimetranslate.service

import com.xzm.realtimetranslate.data.ScreenOcrMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HubBusTest {

    @Before
    fun setUp() {
        HubBus.reset()
    }

    @Test
    fun `initial state is idle and disabled`() {
        val s = HubBus.state.value
        assertFalse(s.isHubEnabled)
        assertEquals(HubMode.IDLE, s.mode)
        assertFalse(s.isPaused)
        assertTrue(s.isSubtitleWindowVisible)
    }

    @Test
    fun `sync from SessionBus updates mode to VOICE when running`() {
        HubBus.setHubEnabled(true)
        HubBus.syncFromSession(SessionBus.Status.Running)
        assertEquals(HubMode.VOICE, HubBus.state.value.mode)

        HubBus.syncFromSession(SessionBus.Status.Idle)
        assertEquals(HubMode.IDLE, HubBus.state.value.mode)
    }

    @Test
    fun `sync from ScreenOcrBus reflects region and fullscreen mode`() {
        HubBus.setHubEnabled(true)
        HubBus.syncFromScreenOcr(ScreenOcrBus.Status.Running, ScreenOcrMode.FULLSCREEN)
        assertEquals(HubMode.FULLSCREEN, HubBus.state.value.mode)

        HubBus.syncFromScreenOcr(ScreenOcrBus.Status.Paused, ScreenOcrMode.FULLSCREEN)
        assertTrue(HubBus.state.value.isPaused)

        HubBus.syncFromScreenOcr(ScreenOcrBus.Status.Stopped, ScreenOcrMode.FULLSCREEN)
        assertEquals(HubMode.IDLE, HubBus.state.value.mode)
    }

    @Test
    fun `command emission works with buffer`() = runBlocking {
        var received: HubCommand? = null
        val job = launch {
            received = HubBus.commands.first()
        }
        delay(50)
        HubBus.postCommand(HubCommand.TogglePause)
        withTimeout(2000) {
            job.join()
        }
        assertEquals(HubCommand.TogglePause, received)
    }

    @Test
    fun `subtitle window visibility toggle updates state`() {
        HubBus.setSubtitleWindowVisible(false)
        assertFalse(HubBus.state.value.isSubtitleWindowVisible)

        HubBus.setSubtitleWindowVisible(true)
        assertTrue(HubBus.state.value.isSubtitleWindowVisible)
    }
}
