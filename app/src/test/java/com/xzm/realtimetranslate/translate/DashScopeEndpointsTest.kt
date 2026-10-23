package com.xzm.realtimetranslate.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashScopeEndpointsTest {

    /**
     * A blank Workspace ID must keep the legacy host byte-for-byte: every existing
     * install has it blank, and the trailing slash is part of that endpoint.
     */
    @Test
    fun `blank workspace keeps the legacy endpoint`() {
        val expected = "wss://dashscope.aliyuncs.com/api-ws/v1/inference/"
        assertEquals(expected, DashScopeEndpoints.realtime(""))
        assertEquals(expected, DashScopeEndpoints.realtime("   "))
    }

    @Test
    fun `workspace id switches to the per-workspace host`() {
        assertEquals(
            "wss://llm-abc123.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference",
            DashScopeEndpoints.realtime(" llm-abc123 "),
        )
    }

    /** Only Qwen-Audio moved to workspace hosts; Fun-ASR/Paraformer still work on the old one. */
    @Test
    fun `only qwen-audio requires a workspace`() {
        assertTrue(DashScopeEndpoints.requiresWorkspace("qwen-audio-3.1-asr-flash-streaming"))
        assertFalse(DashScopeEndpoints.requiresWorkspace("fun-asr-realtime"))
        assertFalse(DashScopeEndpoints.requiresWorkspace("paraformer-realtime-v2"))
    }

    @Test
    fun `openai realtime endpoint carries the model in the query string`() {
        assertEquals(
            "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=qwen3-asr-flash-realtime",
            DashScopeEndpoints.openAiRealtime("qwen3-asr-flash-realtime"),
        )
    }

    @Test
    fun `openai realtime endpoint follows the workspace host`() {
        assertEquals(
            "wss://llm-abc.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime?model=qwen3-asr-flash-realtime",
            DashScopeEndpoints.openAiRealtime("qwen3-asr-flash-realtime", " llm-abc "),
        )
    }

    @Test
    fun `openai realtime endpoint falls back to the default model`() {
        assertEquals(
            "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=${OpenAiRealtimeProtocol.DEFAULT_MODEL}",
            DashScopeEndpoints.openAiRealtime(""),
        )
    }
}
