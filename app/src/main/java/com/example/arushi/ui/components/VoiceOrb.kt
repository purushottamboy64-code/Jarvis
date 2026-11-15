package com.example.arushi.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.arushi.ai.AssistantConnectionState
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CoralGlow
import com.example.ui.theme.CoralPulse
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.VioletAura
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoiceOrb(
    state: AssistantConnectionState,
    micLevel: Float,
    speakerLevel: Float,
    isTestingSpeaker: Boolean,
    modifier: Modifier = Modifier,
    orbSize: Dp = 240.dp
) {
    val activeLevel = when {
        isTestingSpeaker -> speakerLevel.coerceAtLeast(0.45f)
        state == AssistantConnectionState.SPEAKING -> speakerLevel
        state == AssistantConnectionState.LISTENING -> micLevel
        else -> 0f
    }

    val smoothedLevel by animateFloatAsState(
        targetValue = activeLevel,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 350f),
        label = "orbLevel"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "orbAmbient")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantConnectionState.CONNECTING -> 1600
                    AssistantConnectionState.SPEAKING -> 2200
                    AssistantConnectionState.LISTENING -> 3000
                    else -> 5200
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbPhase"
    )

    val breathing by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbBreathing"
    )

    val (primaryColor, secondaryColor, ringColor) = when {
        isTestingSpeaker -> Triple(AmberWarm, ElectricCyan, AmberWarm)
        state == AssistantConnectionState.SPEAKING -> Triple(CoralPulse, AmberWarm, CoralGlow)
        state == AssistantConnectionState.LISTENING -> Triple(ElectricCyan, VioletAura, ElectricCyan)
        state == AssistantConnectionState.CONNECTING -> Triple(VioletAura, ElectricCyan, VioletAura)
        state == AssistantConnectionState.ERROR -> Triple(ErrorCrimson, AmberWarm, ErrorCrimson)
        else -> Triple(CoralPulse.copy(alpha = 0.72f), VioletAura.copy(alpha = 0.72f), VioletAura)
    }

    Box(
        modifier = modifier.size(orbSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val minDim = size.minDimension
            val baseRadius = minDim * 0.27f
            val dynamicRadius = baseRadius * breathing * (1f + smoothedLevel * 0.28f)

            // Outer ambient glow halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.34f + smoothedLevel * 0.24f),
                        secondaryColor.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = minDim * 0.50f
                ),
                radius = minDim * 0.50f,
                center = center
            )

            // Reactive outer waveform ring
            val wavePoints = 72
            val waveBaseRadius = dynamicRadius * 1.24f
            val waveAmplitude = when {
                isTestingSpeaker || state == AssistantConnectionState.SPEAKING ->
                    minDim * (0.025f + smoothedLevel * 0.065f)
                state == AssistantConnectionState.LISTENING ->
                    minDim * (0.015f + smoothedLevel * 0.055f)
                state == AssistantConnectionState.CONNECTING ->
                    minDim * 0.02f
                else -> minDim * 0.008f
            }

            val path = Path()
            for (i in 0..wavePoints) {
                val theta = (i.toFloat() / wavePoints) * (2f * PI.toFloat())
                val harmonic = sin(theta * 5f + phase * 2f) * 0.6f +
                    cos(theta * 3f - phase * 1.5f) * 0.4f
                val r = waveBaseRadius + harmonic * waveAmplitude
                val x = center.x + cos(theta) * r
                val y = center.y + sin(theta) * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            drawPath(
                path = path,
                brush = Brush.sweepGradient(
                    colors = listOf(primaryColor, secondaryColor, ringColor, primaryColor),
                    center = center
                ),
                style = Stroke(width = (3.5.dp).toPx(), cap = StrokeCap.Round)
            )

            // Subtle secondary orbit ring
            drawCircle(
                color = ringColor.copy(alpha = 0.22f + smoothedLevel * 0.25f),
                radius = dynamicRadius * 1.48f,
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Core luminous sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        primaryColor,
                        secondaryColor,
                        ObsidianNight.copy(alpha = 0.9f)
                    ),
                    center = Offset(
                        center.x - dynamicRadius * 0.22f,
                        center.y - dynamicRadius * 0.22f
                    ),
                    radius = dynamicRadius * 1.25f
                ),
                radius = dynamicRadius,
                center = center
            )

            // Center voice bars inside the orb when listening or speaking
            if (state == AssistantConnectionState.LISTENING ||
                state == AssistantConnectionState.SPEAKING ||
                isTestingSpeaker
            ) {
                val barCount = 5
                val barSpacing = dynamicRadius * 0.26f
                val startX = center.x - ((barCount - 1) * barSpacing) / 2f
                for (i in 0 until barCount) {
                    val wave = kotlin.math.abs(sin(phase * 2.2f + i * 0.85f))
                    val heightScale = 0.25f + (smoothedLevel * 0.75f + 0.15f) * wave
                    val halfHeight = dynamicRadius * 0.48f * heightScale
                    val x = startX + i * barSpacing
                    drawLine(
                        color = Color.White.copy(alpha = 0.92f),
                        start = Offset(x, center.y - halfHeight),
                        end = Offset(x, center.y + halfHeight),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
