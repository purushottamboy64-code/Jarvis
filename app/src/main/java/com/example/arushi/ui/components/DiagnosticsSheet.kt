package com.example.arushi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.arushi.ai.ArushiVoicePreset
import com.example.arushi.audio.DiagnosticLogEntry
import com.example.arushi.audio.LogSeverity
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.MintSignal
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceGlass
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DiagnosticsPanelContent(
    logs: List<DiagnosticLogEntry>,
    selectedVoice: ArushiVoicePreset,
    activeModelName: String,
    isTestingSpeaker: Boolean,
    onSelectVoice: (ArushiVoicePreset) -> Unit,
    onTestSpeaker: () -> Unit,
    onClearLogs: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Voice & Audio Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (activeModelName.isNotBlank()) {
                    Text(
                        text = "Live model: $activeModelName",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier.testTag("clear_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = stringResource(R.string.cd_clear_logs),
                        tint = TextSecondary
                    )
                }
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("close_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_close_sheet),
                            tint = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Speaker Diagnostic Test button (plays 440Hz through the same AudioTrack output graph)
        FilledTonalButton(
            onClick = onTestSpeaker,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sheet_test_speaker_button"),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (isTestingSpeaker) AmberWarm.copy(alpha = 0.22f) else SurfaceElevated,
                contentColor = if (isTestingSpeaker) AmberWarm else TextPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isTestingSpeaker) "Playing 440Hz Tone…" else "Test Speaker (440Hz Tone)",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Arushi Voice Preset",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ArushiVoicePreset.entries) { preset ->
                val isSelected = preset == selectedVoice
                Surface(
                    onClick = { onSelectVoice(preset) },
                    modifier = Modifier.testTag("voice_preset_${preset.name.lowercase()}"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) CoralPulse.copy(alpha = 0.18f) else SurfaceGlass,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) CoralPulse else OutlineSubtle
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = if (isSelected) CoralPulse else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) TextPrimary else TextSecondary
                            )
                            Text(
                                text = preset.vibe,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = OutlineSubtle.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Live Pipeline Logs (${logs.size})",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 300.dp),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceGlass,
            border = BorderStroke(1.dp, OutlineSubtle)
        ) {
            if (logs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No pipeline logs yet. Tap the mic button or Test Speaker.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        val color = when (log.severity) {
                            LogSeverity.ERROR -> ErrorCrimson
                            LogSeverity.AUDIO -> ElectricCyan
                            LogSeverity.TOOL -> MintSignal
                            LogSeverity.INFO -> TextSecondary
                        }
                        Text(
                            text = "[${log.timestamp}] ${log.tag}: ${log.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = color
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
