package com.example.arushi.ai

import android.util.Log
import com.example.arushi.actions.DeviceActionBridge
import com.example.arushi.audio.AudioConfig
import com.example.arushi.audio.LogSeverity
import com.example.arushi.audio.MicAudioStreamer
import com.example.arushi.audio.SpeakerAudioPlayer
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveServerSetupComplete
import com.google.firebase.ai.type.LiveServerToolCall
import com.google.firebase.ai.type.LiveServerToolCallCancellation
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Collections

private const val TAG = "ArushiLiveManager"

enum class AssistantConnectionState {
    IDLE,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ERROR
}

@OptIn(PublicPreviewAPI::class)
class ArushiLiveManager(
    private val micAudioStreamer: MicAudioStreamer,
    private val speakerAudioPlayer: SpeakerAudioPlayer,
    private val deviceActionBridge: DeviceActionBridge,
    private val onDiagnosticLog: (String, String, LogSeverity) -> Unit
) {
    private var liveSession: LiveSession? = null
    private var receiveJob: Job? = null
    private val cancelledToolIds = Collections.synchronizedSet(mutableSetOf<String>())

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private val _userTranscript = MutableStateFlow("")
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()

    private val _arushiTranscript = MutableStateFlow("")
    val arushiTranscript: StateFlow<String> = _arushiTranscript.asStateFlow()

    private val _activeModelName = MutableStateFlow(ArushiConfig.PRIMARY_LIVE_MODEL)
    val activeModelName: StateFlow<String> = _activeModelName.asStateFlow()

    suspend fun startSession(
        scope: CoroutineScope,
        voicePreset: ArushiVoicePreset,
        onConnected: () -> Unit,
        onSessionEnded: () -> Unit,
        onSessionError: (String) -> Unit
    ) {
        stopSession()

        onDiagnosticLog(
            TAG,
            "Connecting Gemini Live session (model=${ArushiConfig.PRIMARY_LIVE_MODEL}, voice=${voicePreset.voiceName})",
            LogSeverity.INFO
        )

        val session = try {
            connectWithFallback(voicePreset)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.localizedMessage ?: "Failed to connect to Gemini Live session."
            Log.e(TAG, "Gemini Live connect failed", e)
            onDiagnosticLog(TAG, "Gemini Live connect error: $message", LogSeverity.ERROR)
            onSessionError(message)
            return
        }

        liveSession = session
        _sessionActive.value = true
        _userTranscript.value = ""
        _arushiTranscript.value = ""
        cancelledToolIds.clear()

        onDiagnosticLog(TAG, "Gemini Live session connected!", LogSeverity.INFO)

        speakerAudioPlayer.start(scope)
        onConnected()

        receiveJob = scope.launch {
            listenToServerMessages(
                session = session,
                scope = scope,
                onSessionEnded = onSessionEnded,
                onSessionError = onSessionError
            )
        }

        micAudioStreamer.start(
            scope = scope,
            onAudioChunk = { pcmChunk ->
                try {
                    val activeSession = liveSession
                    if (activeSession != null && !activeSession.isClosed()) {
                        activeSession.sendAudioRealtime(
                            InlineData(pcmChunk, AudioConfig.INPUT_MIME_TYPE)
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send mic audio chunk", e)
                    onDiagnosticLog(TAG, "Send audio chunk error: ${e.message}", LogSeverity.ERROR)
                }
            },
            onCaptureError = { micError ->
                onSessionError(micError)
            }
        )
    }

    private suspend fun connectWithFallback(voicePreset: ArushiVoicePreset): LiveSession {
        return try {
            _activeModelName.value = ArushiConfig.PRIMARY_LIVE_MODEL
            withTimeout(25_000L) {
                createLiveModel(ArushiConfig.PRIMARY_LIVE_MODEL, voicePreset).connect()
            }
        } catch (primaryError: CancellationException) {
            throw primaryError
        } catch (primaryError: Exception) {
            Log.w(
                TAG,
                "Primary Live model '${ArushiConfig.PRIMARY_LIVE_MODEL}' failed (${primaryError.message}); trying fallback '${ArushiConfig.FALLBACK_LIVE_MODEL}'"
            )
            onDiagnosticLog(
                TAG,
                "Primary model unavailable (${primaryError.message}); retrying ${ArushiConfig.FALLBACK_LIVE_MODEL}",
                LogSeverity.INFO
            )
            _activeModelName.value = ArushiConfig.FALLBACK_LIVE_MODEL
            withTimeout(25_000L) {
                createLiveModel(ArushiConfig.FALLBACK_LIVE_MODEL, voicePreset).connect()
            }
        }
    }

    private fun createLiveModel(modelName: String, voicePreset: ArushiVoicePreset) =
        Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = modelName,
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice(voicePreset.voiceName))
                inputAudioTranscription = AudioTranscriptionConfig()
                outputAudioTranscription = AudioTranscriptionConfig()
            },
            tools = ArushiConfig.buildTools(),
            systemInstruction = content { text(ArushiConfig.SYSTEM_INSTRUCTION) }
        )

    private suspend fun listenToServerMessages(
        session: LiveSession,
        scope: CoroutineScope,
        onSessionEnded: () -> Unit,
        onSessionError: (String) -> Unit
    ) {
        val inputBuffer = StringBuilder()
        val outputBuffer = StringBuilder()
        var receivedAudioChunks = 0L

        try {
            session.receive().collect { message ->
                when (message) {
                    is LiveServerSetupComplete -> {
                        onDiagnosticLog(TAG, "Server setup handshake complete", LogSeverity.INFO)
                    }
                    is LiveServerContent -> {
                        if (message.interrupted) {
                            onDiagnosticLog(
                                TAG,
                                "Server signaled user interruption — stopping playback & clearing queue",
                                LogSeverity.AUDIO
                            )
                            speakerAudioPlayer.interrupt()
                            outputBuffer.clear()
                        }

                        message.inputTranscription?.text?.let { fragment ->
                            if (fragment.isNotEmpty()) {
                                inputBuffer.append(fragment)
                                _userTranscript.value = inputBuffer.toString().trim()
                            }
                        }

                        message.outputTranscription?.text?.let { fragment ->
                            if (fragment.isNotEmpty()) {
                                outputBuffer.append(fragment)
                                _arushiTranscript.value = outputBuffer.toString().trim()
                            }
                        }

                        val parts = message.content?.parts.orEmpty()
                        for (part in parts) {
                            if (part is InlineDataPart && part.mimeType.startsWith("audio/", ignoreCase = true)) {
                                val audioBytes = part.inlineData
                                if (audioBytes.isNotEmpty()) {
                                    receivedAudioChunks++
                                    if (receivedAudioChunks == 1L || receivedAudioChunks % 25L == 0L) {
                                        onDiagnosticLog(
                                            TAG,
                                            "Gemini audio chunk #$receivedAudioChunks (${audioBytes.size} bytes, mime=${part.mimeType})",
                                            LogSeverity.AUDIO
                                        )
                                    }
                                    speakerAudioPlayer.enqueueAudio(audioBytes, part.mimeType)
                                }
                            }
                        }

                        if (message.turnComplete) {
                            onDiagnosticLog(
                                TAG,
                                "Gemini turn complete (audioChunks=$receivedAudioChunks)",
                                LogSeverity.AUDIO
                            )
                            speakerAudioPlayer.markTurnComplete()
                            inputBuffer.clear()
                            outputBuffer.clear()
                        }
                    }
                    is LiveServerToolCall -> {
                        val calls = message.functionCalls
                        onDiagnosticLog(
                            TAG,
                            "Tool call received: ${calls.joinToString { it.name }}",
                            LogSeverity.TOOL
                        )
                        scope.launch {
                            val responses = mutableListOf<FunctionResponsePart>()
                            for (call in calls) {
                                val callId = call.id
                                if (callId != null && cancelledToolIds.remove(callId)) {
                                    onDiagnosticLog(TAG, "Skipped cancelled tool call ${call.name} ($callId)", LogSeverity.TOOL)
                                    continue
                                }
                                val stringArgs = call.args.mapValues { (_, jsonVal) ->
                                    if (jsonVal is JsonPrimitive) {
                                        jsonVal.contentOrNull ?: jsonVal.toString()
                                    } else {
                                        jsonVal.jsonPrimitive.content
                                    }
                                }
                                val resultJson = deviceActionBridge.executeTool(call.name, stringArgs)
                                responses.add(FunctionResponsePart(call.name, resultJson, callId))
                            }
                            if (responses.isNotEmpty() && !session.isClosed()) {
                                try {
                                    session.sendFunctionResponse(responses)
                                    onDiagnosticLog(
                                        TAG,
                                        "Sent ${responses.size} tool response(s) back to Gemini Live",
                                        LogSeverity.TOOL
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed sending tool response", e)
                                    onDiagnosticLog(TAG, "Tool response send error: ${e.message}", LogSeverity.ERROR)
                                }
                            }
                        }
                    }
                    is LiveServerToolCallCancellation -> {
                        cancelledToolIds.addAll(message.functionIds)
                        onDiagnosticLog(
                            TAG,
                            "Tool calls cancelled by interruption: ${message.functionIds}",
                            LogSeverity.TOOL
                        )
                    }
                    is LiveServerGoAway -> {
                        onDiagnosticLog(
                            TAG,
                            "Server sent GoAway notice (timeLeft=${message.timeLeft})",
                            LogSeverity.INFO
                        )
                    }
                    else -> {
                        Log.d(TAG, "Unhandled LiveServerMessage: ${message::class.simpleName}")
                    }
                }
            }
            if (_sessionActive.value) {
                onDiagnosticLog(TAG, "Gemini Live stream ended normally", LogSeverity.INFO)
                onSessionEnded()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_sessionActive.value) {
                val err = e.localizedMessage ?: "Gemini Live connection lost."
                Log.e(TAG, "Live receive loop failed", e)
                onDiagnosticLog(TAG, "Live receive error: $err", LogSeverity.ERROR)
                onSessionError(err)
            }
        }
    }

    suspend fun stopSession() {
        val wasActive = _sessionActive.value || liveSession != null
        _sessionActive.value = false
        micAudioStreamer.stop()
        speakerAudioPlayer.interrupt()
        speakerAudioPlayer.stop()
        receiveJob?.cancel()
        receiveJob = null
        try {
            liveSession?.stopReceiving()
        } catch (_: Exception) {
        }
        try {
            liveSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LiveSession: ${e.message}")
        }
        liveSession = null
        if (wasActive) {
            onDiagnosticLog(TAG, "Gemini Live session closed and cleaned up", LogSeverity.INFO)
        }
    }
}
