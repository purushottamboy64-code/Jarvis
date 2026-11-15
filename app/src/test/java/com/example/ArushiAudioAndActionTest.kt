package com.example

import com.example.arushi.audio.AudioConfig
import com.example.arushi.audio.PcmUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArushiAudioAndActionTest {

    @Test
    fun `parseSampleRateFromMimeType extracts explicit rate`() {
        assertEquals(24_000, PcmUtils.parseSampleRateFromMimeType("audio/pcm;rate=24000"))
        assertEquals(16_000, PcmUtils.parseSampleRateFromMimeType("audio/pcm;rate=16000"))
        assertEquals(
            AudioConfig.OUTPUT_SAMPLE_RATE,
            PcmUtils.parseSampleRateFromMimeType("audio/pcm")
        )
    }

    @Test
    fun `computeNormalizedRms returns zero for silence and positive for signal`() {
        val silence = ByteArray(160)
        assertEquals(0f, PcmUtils.computeNormalizedRms(silence), 0.0001f)

        val tone = byteArrayOf(0x00, 0x20, 0x00, 0x20, 0x00, 0x20, 0x00, 0x20)
        val rms = PcmUtils.computeNormalizedRms(tone)
        assertTrue("Expected positive RMS for PCM signal, got $rms", rms > 0.1f)
    }
}
