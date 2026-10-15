package dev.portalgemini.ui

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.portalgemini.api.ConnectionStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GeminiOrb(
    connectionStatus: ConnectionStatus,
    speakerAmplitude: Float,
    micAmplitude: Float,
    size: Dp = 380.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    // Continuous rotation angle
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Breathing pulsation
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Secondary ripple phase
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Smooth speaker amplitude response
    val animatedSpeakerAmp by animateFloatAsState(
        targetValue = speakerAmplitude.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "speakerAmp"
    )

    // Smooth mic amplitude response
    val animatedMicAmp by animateFloatAsState(
        targetValue = micAmplitude.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "micAmp"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 3.2f

            when (connectionStatus) {
                ConnectionStatus.DISCONNECTED -> {
                    // Soft dim idle circle
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x334285F4),
                                Color(0x111E222B),
                                Color.Transparent
                            ),
                            center = center,
                            radius = baseRadius * 1.2f
                        ),
                        radius = baseRadius * 1.2f,
                        center = center
                    )
                    drawCircle(
                        color = Color(0x445F6368),
                        radius = baseRadius * 0.7f,
                        center = center,
                        style = Stroke(width = 3f)
                    )
                }

                ConnectionStatus.CONNECTING, ConnectionStatus.INITIALIZING -> {
                    // Spinning glowing aura while connecting
                    val dynamicRadius = baseRadius * pulse
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x774285F4),
                                Color(0x449B51E0),
                                Color.Transparent
                            ),
                            center = center,
                            radius = dynamicRadius * 1.4f
                        ),
                        radius = dynamicRadius * 1.4f,
                        center = center
                    )
                    // Rotating arc rings
                    val ringPath = Path()
                    val count = 36
                    for (i in 0..count) {
                        val angle = (i * 360f / count + rotation) * PI.toFloat() / 180f
                        val r = dynamicRadius + sin(angle * 3 + phase) * 12f
                        val x = center.x + r * cos(angle)
                        val y = center.y + r * sin(angle)
                        if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
                    }
                    ringPath.close()
                    drawPath(
                        path = ringPath,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF4285F4),
                                Color(0xFF9B51E0),
                                Color(0xFF00E5FF),
                                Color(0xFF4285F4)
                            ),
                            center = center
                        ),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }

                ConnectionStatus.CONNECTED -> {
                    // LIVE ACTIVE STATE (Gemini Speaking or User Speaking / Listening)
                    val isModelSpeaking = animatedSpeakerAmp > 0.05f
                    val isUserSpeaking = animatedMicAmp > 0.05f

                    val dynamicScale = if (isModelSpeaking) {
                        pulse * (1f + animatedSpeakerAmp * 0.55f)
                    } else if (isUserSpeaking) {
                        1f + animatedMicAmp * 0.25f
                    } else {
                        pulse
                    }

                    val currentRadius = baseRadius * dynamicScale

                    // Outer ambient glow (diffuse colorful halo)
                    val outerGlowColors = if (isModelSpeaking) {
                        listOf(
                            Color(0x994285F4),
                            Color(0x779B51E0),
                            Color(0x44FF4081),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            Color(0x554285F4),
                            Color(0x3300E5FF),
                            Color(0x111E222B),
                            Color.Transparent
                        )
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = outerGlowColors,
                            center = center,
                            radius = currentRadius * 1.6f
                        ),
                        radius = currentRadius * 1.6f,
                        center = center
                    )

                    // Multi-harmonic wavy fluid paths (Gemini live fluid aesthetic)
                    val waves = if (isModelSpeaking) 4 else 2
                    for (waveIdx in 0 until waves) {
                        val path = Path()
                        val points = 72
                        val wavePhase = phase + waveIdx * (PI / 2).toFloat()
                        val harmonicMultiplier = if (isModelSpeaking) 4 else 3
                        val distortionAmp = if (isModelSpeaking) {
                            (18f + animatedSpeakerAmp * 35f) / (waveIdx + 1)
                        } else {
                            8f + animatedMicAmp * 15f
                        }

                        for (i in 0..points) {
                            val angle = (i * 360f / points + rotation * (if (waveIdx % 2 == 0) 1 else -1)) * PI.toFloat() / 180f
                            val waveR = currentRadius + sin(angle * harmonicMultiplier + wavePhase) * distortionAmp
                            val x = center.x + waveR * cos(angle)
                            val y = center.y + waveR * sin(angle)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()

                        val strokeColors = if (waveIdx % 2 == 0) {
                            listOf(Color(0xFF4285F4), Color(0xFF00E5FF), Color(0xFF9B51E0), Color(0xFF4285F4))
                        } else {
                            listOf(Color(0xFFFF4081), Color(0xFF7C4DFF), Color(0xFF00E5FF), Color(0xFFFF4081))
                        }

                        drawPath(
                            path = path,
                            brush = Brush.sweepGradient(colors = strokeColors, center = center),
                            style = Stroke(width = (5f - waveIdx).dp.toPx())
                        )
                    }

                    // Dense glowing core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xEE4285F4),
                                Color(0xAA9B51E0),
                                Color.Transparent
                            ),
                            center = center,
                            radius = currentRadius * 0.75f
                        ),
                        radius = currentRadius * 0.75f,
                        center = center
                    )
                }

                ConnectionStatus.ERROR -> {
                    // Red error warning circle
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x66EA4335),
                                Color(0x22EA4335),
                                Color.Transparent
                            ),
                            center = center,
                            radius = baseRadius * 1.2f
                        ),
                        radius = baseRadius * 1.2f,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFFEA4335),
                        radius = baseRadius * 0.8f,
                        center = center,
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
    }
}
