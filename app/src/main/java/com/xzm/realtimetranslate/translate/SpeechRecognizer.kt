package com.xzm.realtimetranslate.translate

import java.io.Closeable

/**
 * Unified speech recognition interface for both on-device (SenseVoice) and
 * cloud-based backends (Aliyun FunASR WebSocket, Generic OpenAI /audio/transcriptions).
 */
interface SpeechRecognizer : Closeable {
    /**
     * Start/initialize the recognizer if background connections or threads are needed.
     */
    fun start() {}

    /**
     * Feed 16 kHz mono 16-bit PCM audio chunk into the recognizer.
     */
    fun acceptPcm(chunk: ByteArray)

    /**
     * Stop processing further audio and flush remaining buffers.
     */
    fun stop() {}

    /**
     * Release all native, executor, or network resources.
     */
    override fun close()
}
