package com.markel.flowstate.components.feedback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Generates FlowState's original, two-note completion chime as PCM/WAV.
 * Keeping the waveform deterministic makes the sound fully auditable and
 * avoids bundling another creator's audio asset or license.
 */
internal object CompletionChime {
    const val SAMPLE_RATE: Int = 44_100
    const val DURATION_MILLIS: Int = 420

    private const val ChannelCount = 1
    private const val BitsPerSample = 16
    private const val HeaderSize = 44

    val byteCount: Int
        get() = HeaderSize + sampleCount() * (BitsPerSample / 8)

    fun createWavBytes(): ByteArray {
        val samples = sampleCount()
        val dataSize = samples * (BitsPerSample / 8)
        val buffer = ByteBuffer.allocate(HeaderSize + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putAscii("RIFF")
        buffer.putInt(36 + dataSize)
        buffer.putAscii("WAVE")
        buffer.putAscii("fmt ")
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(ChannelCount.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * ChannelCount * BitsPerSample / 8)
        buffer.putShort((ChannelCount * BitsPerSample / 8).toShort())
        buffer.putShort(BitsPerSample.toShort())
        buffer.putAscii("data")
        buffer.putInt(dataSize)

        repeat(samples) { index ->
            val timeSeconds = index.toDouble() / SAMPLE_RATE
            val first = note(
                timeSeconds = timeSeconds,
                startSeconds = 0.0,
                durationSeconds = 0.27,
                frequencyHz = 523.25,
            )
            val second = note(
                timeSeconds = timeSeconds,
                startSeconds = 0.115,
                durationSeconds = 0.305,
                frequencyHz = 659.25,
            )
            val shimmer = note(
                timeSeconds = timeSeconds,
                startSeconds = 0.18,
                durationSeconds = 0.22,
                frequencyHz = 783.99,
            )
            val mixed = (first + second * 0.9 + shimmer * 0.25)
                .coerceIn(-1.0, 1.0)
            buffer.putShort((mixed * Short.MAX_VALUE * 0.34).toInt().toShort())
        }
        return buffer.array()
    }

    private fun sampleCount(): Int = SAMPLE_RATE * DURATION_MILLIS / 1_000

    private fun note(
        timeSeconds: Double,
        startSeconds: Double,
        durationSeconds: Double,
        frequencyHz: Double,
    ): Double {
        val localTime = timeSeconds - startSeconds
        if (localTime !in 0.0..durationSeconds) return 0.0
        val attack = (localTime / 0.012).coerceIn(0.0, 1.0)
        val release = (1.0 - localTime / durationSeconds).coerceIn(0.0, 1.0).pow(1.6)
        return sin(2.0 * PI * frequencyHz * localTime) * attack * release
    }

    private fun ByteBuffer.putAscii(value: String) {
        value.forEach { put(it.code.toByte()) }
    }
}
