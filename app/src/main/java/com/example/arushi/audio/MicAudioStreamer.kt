package com.example.arushi.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MicAudioStreamer"

class MicAudioStreamer(
    private val onDiagnosticLog: (String, String, LogSeverity) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var captureJob: Job? = null

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onAudioChunk: suspend (ByteArray) -> Unit,
        onCaptureError: (String) -> Unit
    ) {
        stop()

        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConfig.INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            val msg = "Microphone hardware does not support 16kHz mono 16-bit PCM."
            Log.e(TAG, msg)
            onDiagnosticLog(TAG, msg, LogSeverity.ERROR)
            onCaptureError(msg)
            return
        }

        val bufferSize = maxOf(minBufferSize * 2, AudioConfig.INPUT_CHUNK_BYTES * 2)
        val record = createAudioRecord(bufferSize)
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            val msg = "Failed to initialize Android AudioRecord microphone stream."
            Log.e(TAG, msg)
            onDiagnosticLog(TAG, msg, LogSeverity.ERROR)
            onCaptureError(msg)
            return
        }

        attachAudioEffects(record.audioSessionId)
        audioRecord = record

        try {
            record.startRecording()
            _isCapturing.value = true
            onDiagnosticLog(
                TAG,
                "Microphone started (16kHz, Mono, 16-bit PCM, buffer=$bufferSize bytes)",
                LogSeverity.AUDIO
            )
            Log.i(TAG, "Microphone capture started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord.startRecording failed", e)
            onDiagnosticLog(TAG, "Microphone start failed: ${e.message}", LogSeverity.ERROR)
            stop()
            onCaptureError(e.localizedMessage ?: "Could not start microphone recording.")
            return
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val chunkBuffer = ByteArray(AudioConfig.INPUT_CHUNK_BYTES)
            var chunkCounter = 0L
            try {
                while (currentCoroutineContext().isActive && _isCapturing.value) {
                    val bytesRead = record.read(chunkBuffer, 0, chunkBuffer.size)
                    if (bytesRead > 0) {
                        val payload = chunkBuffer.copyOf(bytesRead)
                        val rms = PcmUtils.computeNormalizedRms(payload, bytesRead)
                        _micLevel.value = rms
                        chunkCounter++
                        if (chunkCounter == 1L || chunkCounter % 50L == 0L) {
                            onDiagnosticLog(
                                TAG,
                                "Mic PCM chunk #$chunkCounter ($bytesRead bytes, rms=${"%.2f".format(rms)})",
                                LogSeverity.AUDIO
                            )
                        }
                        onAudioChunk(payload)
                    } else if (bytesRead < 0) {
                        val err = "AudioRecord read error code: $bytesRead"
                        Log.e(TAG, err)
                        onDiagnosticLog(TAG, err, LogSeverity.ERROR)
                        onCaptureError("Microphone stream interrupted (code $bytesRead).")
                        break
                    }
                }
            } catch (e: Exception) {
                if (currentCoroutineContext().isActive) {
                    Log.e(TAG, "Microphone capture loop error", e)
                    onDiagnosticLog(TAG, "Mic capture error: ${e.message}", LogSeverity.ERROR)
                    onCaptureError(e.localizedMessage ?: "Microphone capture error.")
                }
            } finally {
                _micLevel.value = 0f
                _isCapturing.value = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(bufferSize: Int): AudioRecord? {
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )
        for (source in sources) {
            try {
                val candidate = AudioRecord(
                    source,
                    AudioConfig.INPUT_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    val label = if (source == MediaRecorder.AudioSource.VOICE_COMMUNICATION) {
                        "VOICE_COMMUNICATION"
                    } else {
                        "MIC"
                    }
                    Log.d(TAG, "AudioRecord initialized with source=$label")
                    onDiagnosticLog(TAG, "AudioRecord source selected: $label", LogSeverity.INFO)
                    return candidate
                }
                candidate.release()
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord source $source failed: ${e.message}")
            }
        }
        return null
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
                onDiagnosticLog(TAG, "Hardware AcousticEchoCanceler enabled", LogSeverity.INFO)
            }
        } catch (e: Exception) {
            Log.w(TAG, "AEC unavailable: ${e.message}")
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
                onDiagnosticLog(TAG, "Hardware NoiseSuppressor enabled", LogSeverity.INFO)
            }
        } catch (e: Exception) {
            Log.w(TAG, "NoiseSuppressor unavailable: ${e.message}")
        }
    }

    fun stop() {
        val wasActive = _isCapturing.value || audioRecord != null
        _isCapturing.value = false
        _micLevel.value = 0f
        captureJob?.cancel()
        captureJob = null

        try {
            echoCanceler?.release()
        } catch (_: Exception) {
        }
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {
        }
        noiseSuppressor = null

        try {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null

        if (wasActive) {
            onDiagnosticLog(TAG, "Microphone stopped and released", LogSeverity.AUDIO)
            Log.i(TAG, "Microphone stopped.")
        }
    }
}
