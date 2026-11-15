package com.example.arushi.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.sin

private const val TAG = "SpeakerAudioPlayer"

class SpeakerAudioPlayer(
    private val onDiagnosticLog: (String, String, LogSeverity) -> Unit
) {
    private sealed interface PlaybackItem {
        data class PcmChunk(val pcmBytes: ByteArray, val sampleRate: Int) : PlaybackItem
        data class EndOfTurn(val generationId: Long) : PlaybackItem
    }

    private var audioTrack: AudioTrack? = null
    private var currentSampleRate: Int = AudioConfig.OUTPUT_SAMPLE_RATE
    private var playbackChannel = Channel<PlaybackItem>(Channel.UNLIMITED)
    private var playbackJob: Job? = null
    private val trackLock = Any()
    private val generationCounter = AtomicLong(0L)
    private var chunksPlayedInTurn = 0L

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _outputLevel = MutableStateFlow(0f)
    val outputLevel: StateFlow<Float> = _outputLevel.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (playbackJob?.isActive == true) return
        synchronized(trackLock) {
            ensureAudioTrackLocked(AudioConfig.OUTPUT_SAMPLE_RATE)
        }
        playbackChannel = Channel(Channel.UNLIMITED)
        playbackJob = scope.launch(Dispatchers.IO) {
            onDiagnosticLog(
                TAG,
                "Audio playback queue started (${AudioConfig.OUTPUT_SAMPLE_RATE}Hz, Mono, 16-bit PCM)",
                LogSeverity.AUDIO
            )
            try {
                for (item in playbackChannel) {
                    if (!currentCoroutineContext().isActive) break
                    when (item) {
                        is PlaybackItem.PcmChunk -> writeChunkToSpeaker(item)
                        is PlaybackItem.EndOfTurn -> {
                            if (item.generationId == generationCounter.get()) {
                                _isPlaying.value = false
                                _outputLevel.value = 0f
                                onDiagnosticLog(
                                    TAG,
                                    "Audio playback turn ended ($chunksPlayedInTurn chunks played)",
                                    LogSeverity.AUDIO
                                )
                                chunksPlayedInTurn = 0L
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (currentCoroutineContext().isActive) {
                    Log.e(TAG, "Playback queue loop failed", e)
                    onDiagnosticLog(TAG, "Playback queue error: ${e.message}", LogSeverity.ERROR)
                }
            } finally {
                _isPlaying.value = false
                _outputLevel.value = 0f
            }
        }
    }

    fun enqueueAudio(pcmBytes: ByteArray, mimeType: String?) {
        if (pcmBytes.isEmpty()) return
        val sampleRate = PcmUtils.parseSampleRateFromMimeType(mimeType, AudioConfig.OUTPUT_SAMPLE_RATE)
        _isPlaying.value = true
        val sent = playbackChannel.trySend(PlaybackItem.PcmChunk(pcmBytes, sampleRate))
        if (sent.isFailure) {
            Log.e(TAG, "Failed to enqueue audio chunk: ${sent.exceptionOrNull()?.message}")
            onDiagnosticLog(TAG, "Failed to enqueue audio chunk", LogSeverity.ERROR)
        }
    }

    fun markTurnComplete() {
        val gen = generationCounter.get()
        playbackChannel.trySend(PlaybackItem.EndOfTurn(gen))
    }

    /**
     * Immediately stops active speaker playback and clears queued stale audio chunks on interruption.
     */
    fun interrupt() {
        generationCounter.incrementAndGet()
        var drained = 0
        while (playbackChannel.tryReceive().isSuccess) {
            drained++
        }
        synchronized(trackLock) {
            try {
                audioTrack?.let { track ->
                    if (track.state == AudioTrack.STATE_INITIALIZED) {
                        track.pause()
                        track.flush()
                        track.play()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error flushing AudioTrack on interrupt: ${e.message}")
            }
        }
        chunksPlayedInTurn = 0L
        _isPlaying.value = false
        _outputLevel.value = 0f
        onDiagnosticLog(
            TAG,
            "Audio playback interrupted & cleared ($drained queued items dropped)",
            LogSeverity.AUDIO
        )
    }

    /**
     * Plays a 440Hz diagnostic sine tone through the exact same AudioTrack output graph.
     */
    fun playDiagnosticTone(scope: CoroutineScope) {
        onDiagnosticLog(TAG, "Diagnostic 440Hz speaker test triggered", LogSeverity.AUDIO)
        if (playbackJob?.isActive != true) {
            start(scope)
        }
        val sampleRate = AudioConfig.OUTPUT_SAMPLE_RATE
        val durationSeconds = 0.55
        val totalSamples = (sampleRate * durationSeconds).toInt()
        val fadeSamples = (sampleRate * 0.025).toInt()
        val pcmBytes = ByteArray(totalSamples * 2)

        for (i in 0 until totalSamples) {
            val env = when {
                i < fadeSamples -> i.toDouble() / fadeSamples
                i > totalSamples - fadeSamples -> (totalSamples - i).toDouble() / fadeSamples
                else -> 1.0
            }
            val angle = 2.0 * PI * 440.0 * i / sampleRate
            val sample = (sin(angle) * 0.35 * env * Short.MAX_VALUE).toInt().coerceIn(
                Short.MIN_VALUE.toInt(),
                Short.MAX_VALUE.toInt()
            ).toShort()
            pcmBytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        enqueueAudio(pcmBytes, "audio/pcm;rate=$sampleRate")
        markTurnComplete()
    }

    private fun writeChunkToSpeaker(chunk: PlaybackItem.PcmChunk) {
        val track = synchronized(trackLock) {
            ensureAudioTrackLocked(chunk.sampleRate)
        } ?: return

        chunksPlayedInTurn++
        if (chunksPlayedInTurn == 1L || chunksPlayedInTurn % 20L == 0L) {
            onDiagnosticLog(
                TAG,
                "Playing Gemini PCM chunk #$chunksPlayedInTurn (${chunk.pcmBytes.size} bytes @ ${chunk.sampleRate}Hz)",
                LogSeverity.AUDIO
            )
        }

        _isPlaying.value = true
        _outputLevel.value = PcmUtils.computeNormalizedRms(chunk.pcmBytes)

        var offset = 0
        while (offset < chunk.pcmBytes.size) {
            val written = track.write(
                chunk.pcmBytes,
                offset,
                chunk.pcmBytes.size - offset,
                AudioTrack.WRITE_BLOCKING
            )
            if (written < 0) {
                val err = "AudioTrack.write failed with code $written"
                Log.e(TAG, err)
                onDiagnosticLog(TAG, err, LogSeverity.ERROR)
                break
            }
            if (written == 0) break
            offset += written
        }
    }

    private fun ensureAudioTrackLocked(sampleRate: Int): AudioTrack? {
        val existing = audioTrack
        if (existing != null && existing.state == AudioTrack.STATE_INITIALIZED && currentSampleRate == sampleRate) {
            if (existing.playState != AudioTrack.PLAYSTATE_PLAYING) {
                existing.play()
            }
            return existing
        }

        try {
            existing?.stop()
            existing?.release()
        } catch (_: Exception) {
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            val err = "Invalid AudioTrack minBufferSize=$minBuf for ${sampleRate}Hz"
            Log.e(TAG, err)
            onDiagnosticLog(TAG, err, LogSeverity.ERROR)
            return null
        }

        val bufferSize = maxOf(minBuf * 2, sampleRate / 5 * 2)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        return try {
            val created = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            created.play()
            audioTrack = created
            currentSampleRate = sampleRate
            onDiagnosticLog(
                TAG,
                "AudioTrack initialized (${sampleRate}Hz, buffer=$bufferSize bytes, state=${created.state})",
                LogSeverity.AUDIO
            )
            created
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioTrack", e)
            onDiagnosticLog(TAG, "AudioTrack creation failed: ${e.message}", LogSeverity.ERROR)
            null
        }
    }

    fun stop() {
        generationCounter.incrementAndGet()
        playbackChannel.close()
        playbackJob?.cancel()
        playbackJob = null

        synchronized(trackLock) {
            try {
                audioTrack?.let { track ->
                    if (track.state == AudioTrack.STATE_INITIALIZED) {
                        track.pause()
                        track.flush()
                        track.stop()
                    }
                    track.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping AudioTrack: ${e.message}")
            }
            audioTrack = null
        }
        chunksPlayedInTurn = 0L
        _isPlaying.value = false
        _outputLevel.value = 0f
        onDiagnosticLog(TAG, "Speaker AudioTrack stopped and released", LogSeverity.AUDIO)
    }
}
