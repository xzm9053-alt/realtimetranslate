package com.xzm.realtimetranslate.translate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiRealtimeProtocolTest {

    private fun parseJson(raw: String): JsonObject =
        Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `session update declares text modalities and 16k pcm`() {
        val session = parseJson(OpenAiRealtimeProtocol.sessionUpdate("qwen3-asr-flash-realtime"))
            .getValue("session").jsonObject

        assertEquals("pcm16", session.getValue("input_audio_format").jsonPrimitive.content)
        assertEquals(
            "qwen3-asr-flash-realtime",
            session.getValue("input_audio_transcription").jsonObject
                .getValue("model").jsonPrimitive.content,
        )
        assertEquals(
            "server_vad",
            session.getValue("turn_detection").jsonObject
                .getValue("type").jsonPrimitive.content,
        )
    }

    @Test
    fun `session update clamps the silence window`() {
        val session = parseJson(OpenAiRealtimeProtocol.sessionUpdate(silenceDurationMs = 20))
            .getValue("session").jsonObject
        assertEquals(
            200,
            session.getValue("turn_detection").jsonObject
                .getValue("silence_duration_ms").jsonPrimitive.content.toInt(),
        )
    }

    @Test
    fun `a blank model falls back to the default`() {
        val session = parseJson(OpenAiRealtimeProtocol.sessionUpdate(model = ""))
            .getValue("session").jsonObject
        assertEquals(
            OpenAiRealtimeProtocol.DEFAULT_MODEL,
            session.getValue("input_audio_transcription").jsonObject
                .getValue("model").jsonPrimitive.content,
        )
    }

    @Test
    fun `languages are reduced to two letter codes and auto is omitted`() {
        assertEquals("zh", OpenAiRealtimeProtocol.normalizedLanguage("zh-Hans"))
        assertEquals("pt", OpenAiRealtimeProtocol.normalizedLanguage("pt-BR"))
        assertEquals("en", OpenAiRealtimeProtocol.normalizedLanguage("EN"))
        assertEquals(null, OpenAiRealtimeProtocol.normalizedLanguage("auto"))
        assertEquals(null, OpenAiRealtimeProtocol.normalizedLanguage(null))
        assertEquals(null, OpenAiRealtimeProtocol.normalizedLanguage("  "))

        val withAuto = parseJson(OpenAiRealtimeProtocol.sessionUpdate(language = "auto"))
            .getValue("session").jsonObject
            .getValue("input_audio_transcription").jsonObject
        assertFalse("auto must not be sent to the server", withAuto.containsKey("language"))
    }

    @Test
    fun `audio append and commit use the documented event names`() {
        val append = parseJson(OpenAiRealtimeProtocol.audioAppend("AAAA"))
        assertEquals("input_audio_buffer.append", append.getValue("type").jsonPrimitive.content)
        assertEquals("AAAA", append.getValue("audio").jsonPrimitive.content)

        val commit = parseJson(OpenAiRealtimeProtocol.audioCommit())
        assertEquals("input_audio_buffer.commit", commit.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun `session lifecycle events mean ready`() {
        assertEquals(
            OpenAiRealtimeProtocol.Event.Ready,
            OpenAiRealtimeProtocol.parse("""{"type":"session.created","session":{}}"""),
        )
        assertEquals(
            OpenAiRealtimeProtocol.Event.Ready,
            OpenAiRealtimeProtocol.parse("""{"type":"session.updated","session":{}}"""),
        )
    }

    @Test
    fun `transcription deltas and completions are parsed`() {
        val delta = OpenAiRealtimeProtocol.parse(
            """{"type":"conversation.item.input_audio_transcription.delta","delta":"你好"}""",
        )
        assertEquals(OpenAiRealtimeProtocol.Event.Partial("你好"), delta)

        val done = OpenAiRealtimeProtocol.parse(
            """{"type":"conversation.item.input_audio_transcription.completed","transcript":"你好世界"}""",
        )
        assertEquals(OpenAiRealtimeProtocol.Event.Segment("你好世界"), done)
    }

    @Test
    fun `audio transcript events are accepted as a fallback`() {
        assertEquals(
            OpenAiRealtimeProtocol.Event.Partial("hi"),
            OpenAiRealtimeProtocol.parse("""{"type":"response.audio_transcript.delta","delta":"hi"}"""),
        )
        assertEquals(
            OpenAiRealtimeProtocol.Event.Segment("hi there"),
            OpenAiRealtimeProtocol.parse(
                """{"type":"response.audio_transcript.done","transcript":"hi there"}""",
            ),
        )
    }

    @Test
    fun `error events surface the server message`() {
        assertEquals(
            OpenAiRealtimeProtocol.Event.Failed("model not found"),
            OpenAiRealtimeProtocol.parse(
                """{"type":"error","error":{"type":"invalid_request_error","message":"model not found"}}""",
            ),
        )
        assertEquals(
            OpenAiRealtimeProtocol.Event.Failed("bad key"),
            OpenAiRealtimeProtocol.parse("""{"type":"error","message":"bad key"}"""),
        )
    }

    @Test
    fun `unknown and malformed payloads are ignored`() {
        assertEquals(OpenAiRealtimeProtocol.Event.Ignored, OpenAiRealtimeProtocol.parse("not json"))
        assertEquals(
            OpenAiRealtimeProtocol.Event.Ignored,
            OpenAiRealtimeProtocol.parse("""{"type":"rate_limits.updated"}"""),
        )
        assertEquals(
            OpenAiRealtimeProtocol.Event.Ignored,
            OpenAiRealtimeProtocol.parse("""{"foo":"bar"}"""),
        )
        assertEquals(
            OpenAiRealtimeProtocol.Event.Ignored,
            OpenAiRealtimeProtocol.parse(
                """{"type":"conversation.item.input_audio_transcription.completed","transcript":"   "}""",
            ),
        )
    }

    @Test
    fun `blank transcripts are not emitted as segments`() {
        val event = OpenAiRealtimeProtocol.parse(
            """{"type":"conversation.item.input_audio_transcription.delta","delta":""}""",
        )
        assertTrue(event is OpenAiRealtimeProtocol.Event.Ignored)
    }
}
