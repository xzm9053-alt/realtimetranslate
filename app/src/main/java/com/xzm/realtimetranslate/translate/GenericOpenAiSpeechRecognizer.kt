package com.xzm.realtimetranslate.translate

import com.xzm.realtimetranslate.util.AppLog as Log
import com.xzm.realtimetranslate.util.WavEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Standard OpenAI-compatible speech transcription engine (POST {baseUrl}/audio/transcriptions).
 * Supports:
 * - SiliconFlow (FunAudioLLM/SenseVoiceSmall, whisper-large-v3)
 * - Groq (whisper-large-v3-turbo, whisper-large-v3)
 * - Local / Self-hosted Faster-Whisper, whisper.cpp, Ollama
 */
class GenericOpenAiSpeechRecognizer(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val language: String? = null,
    private val onSegment: (String) -> Unit,
    private val onPartial: ((String) -> Unit)? = null,
    private val client: OkHttpClient = defaultClient(),
) : SpeechRecognizer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val audioSliceChannel = Channel<ByteArray>(capacity = 16)

    // PCM buffer
    private val pcmBuffer = ByteArrayOutputStream()
    private val pcmLock = Any()

    private var speechDetected = false
    private var speechBytesAccumulated = 0
    private var silenceBytesAccumulated = 0

    init {
        scope.launch {
            for (slice in audioSliceChannel) {
                if (!isActive || closed.get()) break
                transcribeSlice(slice)
            }
        }
    }

    override fun acceptPcm(chunk: ByteArray) {
        if (closed.get() || chunk.isEmpty()) return

        var sliceToEmit: ByteArray? = null

        synchronized(pcmLock) {
            pcmBuffer.write(chunk)

            // Analyze energy to detect speech vs silence
            var energySum = 0L
            val sampleCount = chunk.size / 2
            for (i in 0 until sampleCount) {
                val lo = chunk[i * 2].toInt() and 0xff
                val hi = chunk[i * 2 + 1].toInt()
                val sample = ((hi shl 8) or lo).toShort()
                energySum += abs(sample.toInt())
            }
            val avgEnergy = if (sampleCount > 0) energySum / sampleCount else 0

            if (avgEnergy > SPEECH_ENERGY_THRESHOLD) {
                speechDetected = true
                speechBytesAccumulated += chunk.size
                silenceBytesAccumulated = 0
            } else {
                if (speechDetected) {
                    silenceBytesAccumulated += chunk.size
                }
            }

            // Cut a segment if:
            // 1. We had speech for at least 0.6s and now have silence for >0.4s
            // 2. OR speech segment reaches max speech duration (3.5s) to avoid huge latency
            val hasMinSpeech = speechBytesAccumulated >= MIN_SPEECH_BYTES
            val hasSilenceBreak = silenceBytesAccumulated >= SILENCE_BREAK_BYTES
            val isMaxDurationReached = speechBytesAccumulated >= MAX_SPEECH_BYTES

            if ((hasMinSpeech && hasSilenceBreak) || isMaxDurationReached) {
                sliceToEmit = pcmBuffer.toByteArray()
                pcmBuffer.reset()
                speechDetected = false
                speechBytesAccumulated = 0
                silenceBytesAccumulated = 0
            } else if (!speechDetected && pcmBuffer.size() > MAX_IDLE_BUFFER_BYTES) {
                // Keep buffer clean if it's purely idle noise
                pcmBuffer.reset()
            }
        }

        sliceToEmit?.let {
            if (it.size >= MIN_SPEECH_BYTES) {
                audioSliceChannel.trySend(it)
            }
        }
    }

    private fun transcribeSlice(pcmSlice: ByteArray) {
        if (closed.get()) return
        val wavBytes = WavEncoder.encodePcm16leToWav(pcmSlice)

        val endpoint = baseUrl.trimEnd('/')
            .removeSuffix("/chat/completions")
            .removeSuffix("/audio/transcriptions") + "/audio/transcriptions"

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "speech.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", model.ifBlank { "whisper-1" })
            .addFormDataPart("response_format", "json")

        if (!language.isNullOrBlank() && language != "auto") {
            multipartBuilder.addFormDataPart("language", normalizeLanguageCode(language))
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(multipartBuilder.build())

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.w(TAG, "ASR HTTP ${response.code}: ${body.take(200)}")
                    return
                }

                val json = JSONObject(body)
                val text = json.optString("text", "").trim()
                if (text.isNotBlank() && !isHallucinatedNoise(text)) {
                    onSegment(text)
                }
            }
        } catch (e: Exception) {
            if (!closed.get()) {
                Log.w(TAG, "transcription failed: ${e.message}")
            }
        }
    }

    private fun isHallucinatedNoise(text: String): Boolean {
        val lower = text.lowercase()
        return lower == "you" ||
                lower == "thank you." ||
                lower == "thank you" ||
                lower == "subtitles by" ||
                lower == "subtitles by the amara.org community"
    }

    private fun normalizeLanguageCode(code: String): String = when {
        code.startsWith("zh") -> "zh"
        code.startsWith("en") -> "en"
        code.startsWith("ja") -> "ja"
        code.startsWith("ko") -> "ko"
        else -> code
    }

    override fun stop() {
        val remaining: ByteArray? = synchronized(pcmLock) {
            if (speechDetected && pcmBuffer.size() >= MIN_SPEECH_BYTES) {
                val data = pcmBuffer.toByteArray()
                pcmBuffer.reset()
                data
            } else {
                pcmBuffer.reset()
                null
            }
        }
        remaining?.let { audioSliceChannel.trySend(it) }
    }

    override fun close() {
        if (closed.getAndSet(true)) return
        audioSliceChannel.close()
        scope.cancel()
        synchronized(pcmLock) {
            pcmBuffer.reset()
        }
    }

    companion object {
        private const val TAG = "GenericOpenAiASR"
        private const val SAMPLE_RATE = 16_000
        private const val BYTES_PER_SAMPLE = 2 // 16-bit mono

        // 16kHz mono = 32000 bytes/sec
        private const val MIN_SPEECH_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * 6 / 10 // 0.6s
        private const val SILENCE_BREAK_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * 4 / 10 // 0.4s
        private const val MAX_SPEECH_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * 35 / 10 // 3.5s
        private const val MAX_IDLE_BUFFER_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * 2 // 2.0s

        // RMS Energy threshold for 16-bit PCM amplitude
        private const val SPEECH_ENERGY_THRESHOLD = 500

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
    }
}
