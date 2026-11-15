package com.example.arushi.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.arushi.actions.ContactService
import com.example.arushi.actions.DeviceActionBridge
import com.example.arushi.actions.DeviceActionFeedback
import com.example.arushi.ai.ArushiLiveManager
import com.example.arushi.ai.ArushiVoicePreset
import com.example.arushi.ai.AssistantConnectionState
import com.example.arushi.audio.DiagnosticLogEntry
import com.example.arushi.audio.LogSeverity
import com.example.arushi.audio.MicAudioStreamer
import com.example.arushi.audio.SpeakerAudioPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "ArushiViewModel"
private const val MAX_DIAGNOSTIC_LOGS = 150

data class ArushiUiState(
    val connectionState: AssistantConnectionState = AssistantConnectionState.IDLE,
    val micLevel: Float = 0f,
    val speakerLevel: Float = 0f,
    val userTranscript: String = "",
    val arushiTranscript: String = "",
    val lastActionFeedback: DeviceActionFeedback? = null,
    val errorMessage: String? = null,
    val selectedVoice: ArushiVoicePreset = ArushiVoicePreset.ZEPHYR,
    val activeModelName: String = "",
    val isTestingSpeaker: Boolean = false
)

class ArushiViewModel(application: Application) : AndroidViewModel(application) {

    private val logIdCounter = AtomicLong(0L)
    private val _diagnosticLogs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = _diagnosticLogs.asStateFlow()

    private val _requestContactsPermissionEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestContactsPermissionEvent: SharedFlow<Unit> = _requestContactsPermissionEvent.asSharedFlow()

    private var pendingContactsDeferred: CompletableDeferred<Boolean>? = null

    private val _baseConnectionState = MutableStateFlow(AssistantConnectionState.IDLE)
    private val _lastActionFeedback = MutableStateFlow<DeviceActionFeedback?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _selectedVoice = MutableStateFlow(ArushiVoicePreset.ZEPHYR)

    private val micAudioStreamer = MicAudioStreamer(::appendLog)
    private val speakerAudioPlayer = SpeakerAudioPlayer(::appendLog)
    private val contactService = ContactService(application.applicationContext)

    private val deviceActionBridge = DeviceActionBridge(
        context = application.applicationContext,
        contactService = contactService,
        onRequestContactsPermission = { requestContactsPermissionFromUi() },
        onActionFeedback = { feedback -> _lastActionFeedback.value = feedback },
        onDiagnosticLog = ::appendLog
    )

    private val liveManager = ArushiLiveManager(
        micAudioStreamer = micAudioStreamer,
        speakerAudioPlayer = speakerAudioPlayer,
        deviceActionBridge = deviceActionBridge,
        onDiagnosticLog = ::appendLog
    )

    private val sessionAudioFlow = combine(
        _baseConnectionState,
        speakerAudioPlayer.isPlaying,
        micAudioStreamer.micLevel,
        speakerAudioPlayer.outputLevel
    ) { baseState, isSpeakerPlaying, micLvl, speakerLvl ->
        val effectiveState = when {
            baseState == AssistantConnectionState.LISTENING && isSpeakerPlaying ->
                AssistantConnectionState.SPEAKING
            else -> baseState
        }
        Triple(effectiveState, micLvl, speakerLvl)
    }

    private val transcriptAndMetaFlow = combine(
        liveManager.userTranscript,
        liveManager.arushiTranscript,
        _lastActionFeedback,
        _errorMessage,
        _selectedVoice
    ) { userTx, arushiTx, actionFb, err, voice ->
        Tuple5(userTx, arushiTx, actionFb, err, voice)
    }

    val uiState: StateFlow<ArushiUiState> = combine(
        sessionAudioFlow,
        transcriptAndMetaFlow,
        liveManager.activeModelName,
        speakerAudioPlayer.isPlaying
    ) { (effectiveState, micLvl, speakerLvl), meta, modelName, isSpeakerPlaying ->
        ArushiUiState(
            connectionState = effectiveState,
            micLevel = micLvl,
            speakerLevel = speakerLvl,
            userTranscript = meta.first,
            arushiTranscript = meta.second,
            lastActionFeedback = meta.third,
            errorMessage = meta.fourth,
            selectedVoice = meta.fifth,
            activeModelName = modelName,
            isTestingSpeaker = isSpeakerPlaying && effectiveState == AssistantConnectionState.IDLE
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ArushiUiState()
    )

    init {
        appendLog(TAG, "Arushi voice engine initialized (ready)", LogSeverity.INFO)
    }

    fun startVoiceSession() {
        val current = _baseConnectionState.value
        if (current == AssistantConnectionState.CONNECTING ||
            current == AssistantConnectionState.LISTENING ||
            current == AssistantConnectionState.SPEAKING
        ) {
            return
        }

        _errorMessage.value = null
        _lastActionFeedback.value = null
        _baseConnectionState.value = AssistantConnectionState.CONNECTING

        viewModelScope.launch {
            liveManager.startSession(
                scope = viewModelScope,
                voicePreset = _selectedVoice.value,
                onConnected = {
                    _baseConnectionState.value = AssistantConnectionState.LISTENING
                },
                onSessionEnded = {
                    viewModelScope.launch {
                        liveManager.stopSession()
                        _baseConnectionState.value = AssistantConnectionState.IDLE
                    }
                },
                onSessionError = { err ->
                    viewModelScope.launch {
                        liveManager.stopSession()
                        _errorMessage.value = err
                        _baseConnectionState.value = AssistantConnectionState.ERROR
                    }
                }
            )
        }
    }

    fun stopVoiceSession() {
        viewModelScope.launch {
            liveManager.stopSession()
            _baseConnectionState.value = AssistantConnectionState.IDLE
            _errorMessage.value = null
        }
    }

    fun onMicPermissionDenied() {
        val msg = "Microphone permission is needed so Arushi can hear you."
        appendLog(TAG, msg, LogSeverity.ERROR)
        _errorMessage.value = msg
        _baseConnectionState.value = AssistantConnectionState.ERROR
    }

    fun testSpeakerTone() {
        speakerAudioPlayer.playDiagnosticTone(viewModelScope)
    }

    fun selectVoicePreset(preset: ArushiVoicePreset) {
        _selectedVoice.value = preset
        appendLog(TAG, "Selected voice preset: ${preset.voiceName}", LogSeverity.INFO)
    }

    fun clearError() {
        _errorMessage.value = null
        if (_baseConnectionState.value == AssistantConnectionState.ERROR) {
            _baseConnectionState.value = AssistantConnectionState.IDLE
        }
    }

    fun clearLogs() {
        _diagnosticLogs.value = emptyList()
    }

    fun onContactsPermissionResult(granted: Boolean) {
        appendLog(
            TAG,
            if (granted) "READ_CONTACTS permission granted" else "READ_CONTACTS permission denied",
            if (granted) LogSeverity.INFO else LogSeverity.ERROR
        )
        pendingContactsDeferred?.complete(granted)
        pendingContactsDeferred = null
    }

    private suspend fun requestContactsPermissionFromUi(): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingContactsDeferred?.cancel()
        pendingContactsDeferred = deferred
        _requestContactsPermissionEvent.tryEmit(Unit)
        return deferred.await()
    }

    private fun appendLog(tag: String, message: String, severity: LogSeverity) {
        when (severity) {
            LogSeverity.ERROR -> Log.e(tag, message)
            else -> Log.d(tag, message)
        }
        val entry = DiagnosticLogEntry.create(
            id = logIdCounter.incrementAndGet(),
            tag = tag,
            message = message,
            severity = severity
        )
        _diagnosticLogs.value = (_diagnosticLogs.value + entry).takeLast(MAX_DIAGNOSTIC_LOGS)
    }

    override fun onCleared() {
        super.onCleared()
        pendingContactsDeferred?.cancel()
        micAudioStreamer.stop()
        speakerAudioPlayer.stop()
    }

    private data class Tuple5<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}
