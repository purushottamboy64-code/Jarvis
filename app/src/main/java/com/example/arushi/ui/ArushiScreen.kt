package com.example.arushi.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.arushi.ai.AssistantConnectionState
import com.example.arushi.audio.LogSeverity
import com.example.arushi.ui.components.DiagnosticsPanelContent
import com.example.arushi.ui.components.VoiceOrb
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.DeepIndigo
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.ErrorSurface
import com.example.ui.theme.MintSignal
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceGlass
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAura

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArushiScreen(
    viewModel: ArushiViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.diagnosticLogs.collectAsStateWithLifecycle()
    var showDiagnosticsSheet by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceSession()
        } else {
            viewModel.onMicPermissionDenied()
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onContactsPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        viewModel.requestContactsPermissionEvent.collect {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val onPowerButtonClick = {
        when (uiState.connectionState) {
            AssistantConnectionState.CONNECTING,
            AssistantConnectionState.LISTENING,
            AssistantConnectionState.SPEAKING -> viewModel.stopVoiceSession()
            AssistantConnectionState.IDLE,
            AssistantConnectionState.ERROR -> {
                val hasMic = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    viewModel.startVoiceSession()
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianNight,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianNight,
                            DeepIndigo,
                            ObsidianNight
                        )
                    )
                )
        ) {
            val isExpandedLayout = maxWidth >= 840.dp
            val errorCount = remember(logs) { logs.count { it.severity == LogSeverity.ERROR } }

            if (isExpandedLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    VoiceStageColumn(
                        uiState = uiState,
                        errorCount = errorCount,
                        showUtilityChips = false,
                        onPowerButtonClick = onPowerButtonClick,
                        onTestSpeaker = { viewModel.testSpeakerTone() },
                        onOpenDiagnostics = { showDiagnosticsSheet = true },
                        onDismissError = { viewModel.clearError() },
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    )

                    Surface(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(28.dp),
                        color = DeepIndigo.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, OutlineSubtle)
                    ) {
                        DiagnosticsPanelContent(
                            logs = logs,
                            selectedVoice = uiState.selectedVoice,
                            activeModelName = uiState.activeModelName,
                            isTestingSpeaker = uiState.isTestingSpeaker,
                            onSelectVoice = { viewModel.selectVoicePreset(it) },
                            onTestSpeaker = { viewModel.testSpeakerTone() },
                            onClearLogs = { viewModel.clearLogs() }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    VoiceStageColumn(
                        uiState = uiState,
                        errorCount = errorCount,
                        showUtilityChips = true,
                        onPowerButtonClick = onPowerButtonClick,
                        onTestSpeaker = { viewModel.testSpeakerTone() },
                        onOpenDiagnostics = { showDiagnosticsSheet = true },
                        onDismissError = { viewModel.clearError() },
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }

            if (showDiagnosticsSheet && !isExpandedLayout) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showDiagnosticsSheet = false },
                    sheetState = sheetState,
                    containerColor = DeepIndigo,
                    contentColor = TextPrimary
                ) {
                    DiagnosticsPanelContent(
                        logs = logs,
                        selectedVoice = uiState.selectedVoice,
                        activeModelName = uiState.activeModelName,
                        isTestingSpeaker = uiState.isTestingSpeaker,
                        onSelectVoice = { viewModel.selectVoicePreset(it) },
                        onTestSpeaker = { viewModel.testSpeakerTone() },
                        onClearLogs = { viewModel.clearLogs() },
                        onClose = { showDiagnosticsSheet = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceStageColumn(
    uiState: ArushiUiState,
    errorCount: Int,
    showUtilityChips: Boolean,
    onPowerButtonClick: () -> Unit,
    onTestSpeaker: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top header bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.arushi_subtitle),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = SurfaceGlass,
                border = BorderStroke(1.dp, OutlineSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.language_auto_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }
        }

        // Centerpiece reactive orb + live state/captions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            VoiceOrb(
                state = uiState.connectionState,
                micLevel = uiState.micLevel,
                speakerLevel = uiState.speakerLevel,
                isTestingSpeaker = uiState.isTestingSpeaker,
                orbSize = 236.dp
            )

            Spacer(modifier = Modifier.height(20.dp))

            StateStatusBadge(
                state = uiState.connectionState,
                isTestingSpeaker = uiState.isTestingSpeaker
            )

            Spacer(modifier = Modifier.height(10.dp))

            val headlineText = when {
                uiState.isTestingSpeaker -> "Playing 440Hz Speaker Test"
                uiState.connectionState == AssistantConnectionState.IDLE ->
                    stringResource(R.string.state_idle_title)
                uiState.connectionState == AssistantConnectionState.CONNECTING ->
                    stringResource(R.string.state_connecting_title)
                uiState.connectionState == AssistantConnectionState.LISTENING ->
                    stringResource(R.string.state_listening_title)
                uiState.connectionState == AssistantConnectionState.SPEAKING ->
                    stringResource(R.string.state_speaking_title)
                else -> stringResource(R.string.state_error_title)
            }

            val hintText = when (uiState.connectionState) {
                AssistantConnectionState.IDLE -> stringResource(R.string.state_idle_hint)
                AssistantConnectionState.CONNECTING -> stringResource(R.string.state_connecting_hint)
                AssistantConnectionState.LISTENING -> stringResource(R.string.state_listening_hint)
                AssistantConnectionState.SPEAKING -> stringResource(R.string.state_speaking_hint)
                AssistantConnectionState.ERROR -> uiState.errorMessage ?: "Tap the button to reconnect"
            }

            Text(
                text = headlineText,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subtle live voice caption strip (not a chat feed)
            val activeCaption = when {
                uiState.arushiTranscript.isNotBlank() &&
                    uiState.connectionState == AssistantConnectionState.SPEAKING ->
                    "Arushi: ${uiState.arushiTranscript}"
                uiState.userTranscript.isNotBlank() ->
                    "You: ${uiState.userTranscript}"
                uiState.arushiTranscript.isNotBlank() ->
                    "Arushi: ${uiState.arushiTranscript}"
                else -> ""
            }

            AnimatedVisibility(
                visible = activeCaption.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceGlass.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, OutlineSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = activeCaption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            // Device action execution status pill
            uiState.lastActionFeedback?.let { feedback ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (feedback.success) {
                        MintSignal.copy(alpha = 0.14f)
                    } else {
                        ErrorSurface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (feedback.success) MintSignal.copy(alpha = 0.6f) else ErrorCrimson
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = if (feedback.success) MintSignal else ErrorCrimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = feedback.summary,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Visible inline error banner
            AnimatedVisibility(
                visible = uiState.connectionState == AssistantConnectionState.ERROR &&
                    !uiState.errorMessage.isNullOrBlank()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ErrorSurface,
                    border = BorderStroke(1.dp, ErrorCrimson),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorCrimson,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissError) {
                            Text(
                                text = stringResource(R.string.dismiss),
                                color = CoralPulse
                            )
                        }
                    }
                }
            }
        }

        // Bottom Voice Dock (Mic/Power primary button + Speaker Diagnostic & Logs chips)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            if (showUtilityChips) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onTestSpeaker,
                        modifier = Modifier.testTag("test_speaker_button"),
                        shape = RoundedCornerShape(999.dp),
                        color = if (uiState.isTestingSpeaker) {
                            AmberWarm.copy(alpha = 0.2f)
                        } else {
                            SurfaceGlass
                        },
                        border = BorderStroke(
                            1.dp,
                            if (uiState.isTestingSpeaker) AmberWarm else OutlineSubtle
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = stringResource(R.string.cd_test_speaker),
                                tint = if (uiState.isTestingSpeaker) AmberWarm else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.action_test_speaker),
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    Surface(
                        onClick = onOpenDiagnostics,
                        modifier = Modifier.testTag("diagnostics_button"),
                        shape = RoundedCornerShape(999.dp),
                        color = SurfaceGlass,
                        border = BorderStroke(
                            1.dp,
                            if (errorCount > 0) ErrorCrimson else OutlineSubtle
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = stringResource(R.string.cd_open_diagnostics),
                                tint = if (errorCount > 0) ErrorCrimson else ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (errorCount > 0) {
                                    "${stringResource(R.string.action_diagnostics)} ($errorCount)"
                                } else {
                                    "${stringResource(R.string.action_diagnostics)} · ${uiState.selectedVoice.label}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            MicPowerButton(
                state = uiState.connectionState,
                onClick = onPowerButtonClick
            )
        }
    }
}

@Composable
private fun StateStatusBadge(
    state: AssistantConnectionState,
    isTestingSpeaker: Boolean
) {
    val (label, dotColor) = when {
        isTestingSpeaker -> "SPEAKER TEST" to AmberWarm
        state == AssistantConnectionState.IDLE -> "IDLE" to TextMuted
        state == AssistantConnectionState.CONNECTING -> "CONNECTING" to VioletAura
        state == AssistantConnectionState.LISTENING -> "LISTENING" to ElectricCyan
        state == AssistantConnectionState.SPEAKING -> "SPEAKING" to CoralPulse
        else -> "ERROR" to ErrorCrimson
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = dotColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = dotColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MicPowerButton(
    state: AssistantConnectionState,
    onClick: () -> Unit
) {
    val isActive = state == AssistantConnectionState.LISTENING ||
        state == AssistantConnectionState.SPEAKING ||
        state == AssistantConnectionState.CONNECTING

    val containerColor by animateColorAsState(
        targetValue = when (state) {
            AssistantConnectionState.LISTENING -> ElectricCyan
            AssistantConnectionState.SPEAKING -> CoralPulse
            AssistantConnectionState.CONNECTING -> SurfaceElevated
            AssistantConnectionState.ERROR -> ErrorCrimson
            AssistantConnectionState.IDLE -> CoralPulse
        },
        label = "micButtonColor"
    )

    val contentColor = when (state) {
        AssistantConnectionState.LISTENING -> ObsidianNight
        else -> Color.White
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(80.dp)
            .shadow(elevation = 16.dp, shape = CircleShape, ambientColor = containerColor)
            .testTag("mic_power_button"),
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(
            width = 2.dp,
            color = if (isActive) Color.White.copy(alpha = 0.45f) else CoralPulse.copy(alpha = 0.5f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (state) {
                AssistantConnectionState.CONNECTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        color = ElectricCyan,
                        strokeWidth = 3.dp
                    )
                }
                AssistantConnectionState.LISTENING,
                AssistantConnectionState.SPEAKING -> {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = stringResource(R.string.cd_mic_power_button),
                        tint = contentColor,
                        modifier = Modifier.size(34.dp)
                    )
                }
                AssistantConnectionState.IDLE,
                AssistantConnectionState.ERROR -> {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = stringResource(R.string.cd_mic_power_button),
                        tint = contentColor,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }
}
