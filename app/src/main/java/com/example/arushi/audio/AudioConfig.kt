package com.example.arushi.audio

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AudioConfig {
    const val INPUT_SAMPLE_RATE = 16_000
    const val OUTPUT_SAMPLE_RATE = 24_000
    const val INPUT_MIME_TYPE = "audio/pcm;rate=16000"
    const val INPUT_CHUNK_BYTES = 3_200 // 100ms of 16kHz 16-bit mono PCM
}

enum class LogSeverity {
    INFO,
    AUDIO,
    TOOL,
    ERROR
}

data class DiagnosticLogEntry(
    val id: Long,
    val timestamp: String,
    val tag: String,
    val message: String,
    val severity: LogSeverity = LogSeverity.INFO
) {
    companion object {
        private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

        fun create(
            id: Long,
            tag: String,
            message: String,
            severity: LogSeverity = LogSeverity.INFO
        ): DiagnosticLogEntry = DiagnosticLogEntry(
            id = id,
            timestamp = formatter.format(Date()),
            tag = tag,
            message = message,
            severity = severity
        )
    }
}

object PcmUtils {
    /**
     * Computes normalized RMS energy [0f..1f] from little-endian 16-bit signed PCM bytes.
     */
    fun computeNormalizedRms(pcmBytes: ByteArray, length: Int = pcmBytes.size): Float {
        if (length < 2) return 0f
        val sampleCount = length / 2
        if (sampleCount == 0) return 0f
        var sumSquares = 0.0
        var i = 0
        while (i + 1 < length) {
            val low = pcmBytes[i].toInt() and 0xFF
            val high = pcmBytes[i + 1].toInt()
            val sample = (high shl 8) or low
            val normalized = sample / 32768.0
            sumSquares += normalized * normalized
            i += 2
        }
        val rms = kotlin.math.sqrt(sumSquares / sampleCount).toFloat()
        return (rms * 3.5f).coerceIn(0f, 1f)
    }

    /**
     * Parses sample rate from MIME type such as "audio/pcm;rate=24000", defaulting to fallbackRate.
     */
    fun parseSampleRateFromMimeType(mimeType: String?, fallbackRate: Int = AudioConfig.OUTPUT_SAMPLE_RATE): Int {
        if (mimeType.isNullOrBlank()) return fallbackRate
        val match = Regex("""rate=(\d+)""", RegexOption.IGNORE_CASE).find(mimeType)
        val parsed = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        return if (parsed != null && parsed in 8_000..48_000) parsed else fallbackRate
    }
}
