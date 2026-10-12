package com.xzm.realtimetranslate.audio

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 混音两路 16 kHz 单声道 PCM16 LE 音频流（媒体声音 + 麦克风），进行加权均值混音。
 * 内部基于无装箱的原语环形缓冲区（ByteArrayRingBuffer），彻底根除每秒数万个 Byte 对象带来的 GC 抖动。
 */
class PcmMixer(
    private val onMixed: (ByteArray) -> Unit,
) {
    private val mediaBuffer = ByteArrayRingBuffer(RING_BUFFER_CAPACITY)
    private val micBuffer = ByteArrayRingBuffer(RING_BUFFER_CAPACITY)
    private val closed = AtomicBoolean(false)
    private val drainLock = Any()

    fun offerMedia(chunk: ByteArray) {
        if (closed.get() || chunk.isEmpty()) return
        mediaBuffer.write(chunk)
        drain()
    }

    fun offerMic(chunk: ByteArray) {
        if (closed.get() || chunk.isEmpty()) return
        micBuffer.write(chunk)
        drain()
    }

    fun close() {
        closed.set(true)
        mediaBuffer.clear()
        micBuffer.clear()
    }

    private fun drain() {
        synchronized(drainLock) {
            // 防止单侧断流堆积（上限约 1.5s @ 16k mono 16bit = 48000 bytes）
            mediaBuffer.trimTo(MAX_QUEUE_BYTES)
            micBuffer.trimTo(MAX_QUEUE_BYTES)

            val availableMediaBytes = mediaBuffer.size
            val availableMicBytes = micBuffer.size
            val bytesToProcess = minOf(availableMediaBytes, availableMicBytes)
            val frames = bytesToProcess / 2
            if (frames <= 0) return

            val processBytes = frames * 2
            val mediaChunk = ByteArray(processBytes)
            val micChunk = ByteArray(processBytes)

            val readMedia = mediaBuffer.read(mediaChunk)
            val readMic = micBuffer.read(micChunk)
            val actualBytes = minOf(readMedia, readMic)
            val actualFrames = actualBytes / 2
            if (actualFrames <= 0) return

            val out = ByteArray(actualFrames * 2)
            var oi = 0
            for (i in 0 until actualFrames) {
                val idx = i * 2
                val m0 = mediaChunk[idx].toInt() and 0xFF
                val m1 = mediaChunk[idx + 1].toInt()
                val n0 = micChunk[idx].toInt() and 0xFF
                val n1 = micChunk[idx + 1].toInt()

                val mSigned = ((m1 shl 8) or m0).toShort().toInt()
                val nSigned = ((n1 shl 8) or n0).toShort().toInt()

                val mixed = ((mSigned + nSigned) / 2)
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

                out[oi++] = (mixed and 0xFF).toByte()
                out[oi++] = ((mixed shr 8) and 0xFF).toByte()
            }

            if (oi > 0) {
                onMixed(out)
            }
        }
    }

    companion object {
        private const val RING_BUFFER_CAPACITY = 64 * 1024 // 64 KB 环形缓冲区
        private const val MAX_QUEUE_BYTES = 48_000
    }
}

/**
 * 线程安全的无装箱定长字节环形缓冲区。
 */
class ByteArrayRingBuffer(private val capacity: Int) {
    private val buffer = ByteArray(capacity)
    private var head = 0
    private var tail = 0
    private var count = 0
    private val lock = Any()

    val size: Int get() = synchronized(lock) { count }

    fun write(src: ByteArray, offset: Int = 0, length: Int = src.size): Int {
        synchronized(lock) {
            val toWrite = minOf(length, capacity - count)
            if (toWrite <= 0) return 0
            val firstPart = minOf(toWrite, capacity - tail)
            System.arraycopy(src, offset, buffer, tail, firstPart)
            val secondPart = toWrite - firstPart
            if (secondPart > 0) {
                System.arraycopy(src, offset + firstPart, buffer, 0, secondPart)
            }
            tail = (tail + toWrite) % capacity
            count += toWrite
            return toWrite
        }
    }

    fun read(dest: ByteArray, offset: Int = 0, length: Int = dest.size): Int {
        synchronized(lock) {
            val toRead = minOf(length, count)
            if (toRead <= 0) return 0
            val firstPart = minOf(toRead, capacity - head)
            System.arraycopy(buffer, head, dest, offset, firstPart)
            val secondPart = toRead - firstPart
            if (secondPart > 0) {
                System.arraycopy(buffer, 0, dest, offset + firstPart, secondPart)
            }
            head = (head + toRead) % capacity
            count -= toRead
            return toRead
        }
    }

    fun trimTo(max: Int) {
        synchronized(lock) {
            if (count > max) {
                val drop = count - max
                // 确保丢弃的是偶数字节（对齐 16-bit 采样点）
                val dropAligned = if (drop % 2 != 0) drop + 1 else drop
                val actualDrop = minOf(dropAligned, count)
                head = (head + actualDrop) % capacity
                count -= actualDrop
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            head = 0
            tail = 0
            count = 0
        }
    }
}
