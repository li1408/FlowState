package com.markel.flowstate.components.feedback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionChimeTest {

    @Test
    fun `generated chime is a complete mono PCM wav`() {
        val wav = CompletionChime.createWavBytes()
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(CompletionChime.byteCount, wav.size)
        assertEquals("RIFF", wav.copyOfRange(0, 4).decodeToString())
        assertEquals(wav.size - 8, header.getInt(4))
        assertEquals("WAVE", wav.copyOfRange(8, 12).decodeToString())
        assertEquals(1, header.getShort(20).toInt())
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(CompletionChime.SAMPLE_RATE, header.getInt(24))
        assertEquals(16, header.getShort(34).toInt())
        assertEquals(wav.size - 44, header.getInt(40))
        assertTrue(wav.copyOfRange(44, wav.size).any { it.toInt() != 0 })
    }
}
