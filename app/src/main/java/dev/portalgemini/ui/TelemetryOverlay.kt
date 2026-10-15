package dev.portalgemini.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.portalgemini.telemetry.TelemetryData
import java.util.Locale

@Composable
fun TelemetryOverlay(
    telemetry: TelemetryData,
    isVoiceOnly: Boolean,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF262B38), RoundedCornerShape(12.dp)),
        color = Color(0xD9101218),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row with collapse/expand toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF00E5FF))
                    )
                    Text(
                        text = "LIVE TELEMETRY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFF00E5FF)
                    )
                    Text(
                        text = "· ${telemetry.networkSummary}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF9AA0A6)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse Telemetry" else "Expand Telemetry",
                    tint = Color(0xFF9AA0A6),
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    // Model Info Row (Model, Voice, Frame Rate)
                    val displayModel = when {
                        telemetry.model.contains("gemini-2.5-flash-native-audio") -> "gemini-2.5-flash-native-audio"
                        telemetry.model.contains("gemini-2.5-flash") -> "gemini-2.5-flash"
                        telemetry.model.contains("gemini-3.8-live-extended-thinking") -> "gemini-3.8-live (Thinking)"
                        telemetry.model.contains("gemini-3.8-live") -> "gemini-3.8-live"
                        else -> telemetry.model.removePrefix("models/").removeSuffix("-latest")
                    }
                    val targetFpsStr = if (telemetry.targetFps % 1.0 == 0.0) {
                        String.format(Locale.US, "%.0f FPS", telemetry.targetFps)
                    } else {
                        String.format(Locale.US, "%.1f FPS", telemetry.targetFps)
                    }

                    TelemetryRow(
                        label = "MODEL",
                        value = "$displayModel  ·  Voice: ${telemetry.voice}  ·  $targetFpsStr",
                        valueColor = Color(0xFFCE93D8)
                    )

                    // Latency Row
                    TelemetryRow(
                        label = "LATENCY",
                        value = buildString {
                            append("Turn: ")
                            if (telemetry.turnLatencyMs != null) {
                                append("${telemetry.turnLatencyMs} ms")
                            } else {
                                append("--")
                            }
                            append("  ·  Setup: ")
                            if (telemetry.setupLatencyMs != null) {
                                append("${telemetry.setupLatencyMs} ms")
                            } else {
                                append("--")
                            }
                        },
                        valueColor = Color(0xFF80D8FF)
                    )

                    // Camera Throughput Row
                    TelemetryRow(
                        label = "CAMERA",
                        value = if (isVoiceOnly && telemetry.cameraMode == dev.portalgemini.camera.CameraMode.STANDBY) {
                            "Voice Only (Standby · 0 FPS · $0)"
                        } else {
                            val modeStr = when (telemetry.cameraMode) {
                                dev.portalgemini.camera.CameraMode.STANDBY -> "Standby (0 FPS · $0)"
                                dev.portalgemini.camera.CameraMode.AUTO -> "Auto (Active · 1 FPS)"
                                dev.portalgemini.camera.CameraMode.CONTINUOUS -> "Continuous (1 FPS)"
                            }
                            if (telemetry.cameraMode == dev.portalgemini.camera.CameraMode.STANDBY) {
                                "0.0 FPS · $modeStr"
                            } else {
                                String.format(
                                    Locale.US,
                                    "%.1f FPS (%.0f kB/f) · %s",
                                    telemetry.videoFps,
                                    telemetry.lastFrameSizeKb,
                                    modeStr
                                )
                            }
                        },
                        valueColor = if (telemetry.cameraMode == dev.portalgemini.camera.CameraMode.STANDBY) Color(0xFF9AA0A6) else Color(0xFFB9F6CA)
                    )

                    // Audio Throughput Row
                    TelemetryRow(
                        label = "AUDIO",
                        value = String.format(
                            Locale.US,
                            "TX: %.0f kbps (16kHz)  ·  RX: %.0f kbps (24kHz)",
                            telemetry.audioTxKbps,
                            telemetry.audioRxKbps
                        ),
                        valueColor = Color(0xFFFFE57F)
                    )

                    // Estimated API Cost Row
                    TelemetryRow(
                        label = "EST. COST",
                        value = String.format(
                            Locale.US,
                            "$%.4f (£%.4f) · Aud: $%.4f · Cam: $%.4f",
                            telemetry.estimatedCostUsd,
                            telemetry.estimatedCostGbp,
                            telemetry.audioCostUsd,
                            telemetry.cameraCostUsd
                        ),
                        valueColor = Color(0xFFFFD54F)
                    )

                    // Data Traffic Row
                    TelemetryRow(
                        label = "TRAFFIC",
                        value = String.format(
                            Locale.US,
                            "▲ %s  ·  ▼ %s  ·  Time: %02d:%02d",
                            formatBytes(telemetry.totalTxBytes),
                            formatBytes(telemetry.totalRxBytes),
                            telemetry.sessionDurationSec / 60,
                            telemetry.sessionDurationSec % 60
                        ),
                        valueColor = Color(0xFFCFD8DC)
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = Color(0xFF757B88),
            modifier = Modifier.width(54.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = valueColor
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(Locale.US, "%.0f kB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
