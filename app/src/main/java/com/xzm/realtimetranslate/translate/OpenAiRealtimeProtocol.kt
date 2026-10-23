package com.xzm.realtimetranslate.translate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * DashScope 的 OpenAI-Realtime 兼容协议（Qwen3-ASR 实时模型使用）。
 *
 * 这里只负责 JSON 的构造与解析，不碰网络，方便单测覆盖字段名；事件名与
 * `session.update` 的结构按 OpenAI Realtime 规范书写。DashScope 若有个别字段
 * 差异，只有真机连接测试才能暴露，因此识别器会把原始错误文本一并打出来。
 */
object OpenAiRealtimeProtocol {

    const val DEFAULT_MODEL = "qwen3-asr-flash-realtime"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    sealed interface Event {
        /** 服务端已接受 session 配置，可以开始推音频。 */
        data object Ready : Event

        /** 增量转写文本，用于草稿显示。 */
        data class Partial(val text: String) : Event

        /** 一句话定稿。 */
        data class Segment(val text: String) : Event

        /** 服务端错误事件，[message] 为原始文本。 */
        data class Failed(val message: String) : Event

        data object Ignored : Event
    }

    fun sessionUpdate(
        model: String = DEFAULT_MODEL,
        language: String? = null,
        silenceDurationMs: Int = 400,
    ): String = buildJsonObject {
        put("type", "session.update")
        put(
            "session",
            buildJsonObject {
                put("modalities", JsonArray(listOf(JsonPrimitive("text"))))
                put("input_audio_format", "pcm16")
                put(
                    "input_audio_transcription",
                    buildJsonObject {
                        put("model", model.ifBlank { DEFAULT_MODEL })
                        normalizedLanguage(language)?.let { put("language", it) }
                    },
                )
                put(
                    "turn_detection",
                    buildJsonObject {
                        put("type", "server_vad")
                        put("silence_duration_ms", silenceDurationMs.coerceIn(200, 2000))
                    },
                )
            },
        )
    }.toString()

    fun audioAppend(base64Audio: String): String = buildJsonObject {
        put("type", "input_audio_buffer.append")
        put("audio", base64Audio)
    }.toString()

    fun audioCommit(): String = buildJsonObject {
        put("type", "input_audio_buffer.commit")
    }.toString()

    fun parse(raw: String): Event {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return Event.Ignored
        val type = root.stringOrNull("type") ?: return Event.Ignored

        fun field(name: String): String = root.stringOrNull(name)?.trim().orEmpty()

        return when (type) {
            "session.created", "session.updated" -> Event.Ready

            "conversation.item.input_audio_transcription.delta",
            "response.audio_transcript.delta",
            -> field("delta").takeIf { it.isNotEmpty() }?.let(Event::Partial) ?: Event.Ignored

            "conversation.item.input_audio_transcription.completed",
            "conversation.item.input_audio_transcription.done",
            "response.audio_transcript.done",
            "response.audio_transcript.completed",
            -> field("transcript").takeIf { it.isNotEmpty() }?.let(Event::Segment) ?: Event.Ignored

            "error" -> {
                val message = root["error"]?.let { element ->
                    runCatching { element.jsonObject.stringOrNull("message") }.getOrNull()
                } ?: root.stringOrNull("message")
                Event.Failed((message ?: raw).trim())
            }

            else -> Event.Ignored
        }
    }

    /** Realtime 协议只认两字母语言码；`auto` 表示交给服务端判断。 */
    fun normalizedLanguage(language: String?): String? {
        val lang = language?.trim().orEmpty()
        if (lang.isBlank() || lang.equals("auto", ignoreCase = true)) return null
        return lang.substringBefore('-').lowercase().takeIf { it.length == 2 }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
