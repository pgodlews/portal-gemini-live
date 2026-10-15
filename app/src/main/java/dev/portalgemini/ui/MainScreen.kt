package dev.portalgemini.ui

import android.view.TextureView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.portalgemini.api.ConnectionStatus
import dev.portalgemini.api.LiveConfig
import dev.portalgemini.camera.CameraMode
import dev.portalgemini.model.ChatMessage
import dev.portalgemini.telemetry.TelemetryData

@Composable
fun MainScreen(
    connectionStatus: ConnectionStatus,
    errorMessage: String?,
    speakerAmplitude: Float,
    micAmplitude: Float,
    isMuted: Boolean,
    isCameraStreaming: Boolean,
    latestTranscript: String,
    config: LiveConfig,
    isThinking: Boolean = false,
    telemetryData: TelemetryData = TelemetryData(),
    showTelemetry: Boolean = true,
    conversationMessages: List<ChatMessage> = emptyList(),
    isSessionEnded: Boolean = false,
    onClearHistory: () -> Unit = {},
    onExportConversation: () -> Unit = {},
    onOpenQrSetup: () -> Unit = {},
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onUpdateConfig: (LiveConfig, Long, Boolean) -> Unit,
    onBindCameraTexture: (TextureView) -> Unit,
    topInsetDp: Int = 0,
    currentFrameIntervalMs: Long = 1000L
) {
    var showSettings by remember { mutableStateOf(false) }
    var isCameraExpanded by remember { mutableStateOf(false) }
    var showConversationHistory by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D11))
            .padding(top = topInsetDp.dp)
    ) {
        // 1. Background subtle ambient radial glow (only when not streaming)
        if (!isCameraStreaming) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0x184285F4),
                                Color(0x089B51E0),
                                Color.Transparent
                            ),
                            radius = 1200f
                        )
                    )
            )
        }

        // 2. Camera View (Fullscreen when streaming, PIP in top-right when paused)
        val cameraModifier = if (isCameraStreaming) {
            Modifier.fillMaxSize()
        } else {
            val cameraWidth = if (isCameraExpanded) 420.dp else 220.dp
            val cameraHeight = if (isCameraExpanded) 280.dp else 140.dp
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 32.dp)
                .size(width = cameraWidth, height = cameraHeight)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF14151B))
                .border(
                    2.dp,
                    Color(0xFF2C303E),
                    RoundedCornerShape(18.dp)
                )
                .shadow(12.dp, RoundedCornerShape(18.dp))
                .clickable { isCameraExpanded = !isCameraExpanded }
        }

        Box(modifier = cameraModifier) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        onBindCameraTexture(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Camera status overlay (only show in PIP mode when paused)
            if (!isCameraStreaming) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC000000),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF80868B))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Voice Only (Camera Paused)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 3. Gradient Scrims (when camera is streaming fullscreen for high contrast readability)
        if (isCameraStreaming) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xCC000000), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xDD000000))
                        )
                    )
            )
        }

        // 4. Center Content when Voice-Only: Large 360dp Gemini Orb & Subtitles
        if (!isCameraStreaming) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                GeminiOrb(
                    connectionStatus = connectionStatus,
                    speakerAmplitude = speakerAmplitude,
                    micAmplitude = micAmplitude,
                    size = 360.dp
                )

                Spacer(modifier = Modifier.height(24.dp))

                SubtitlePill(
                    latestTranscript = latestTranscript,
                    errorMessage = errorMessage,
                    isThinking = isThinking
                )
            }
        }

        // 5. Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // App Branding & Telemetry HUD
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.widthIn(max = 560.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Gemini ",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Live",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        ),
                        color = Color(0xFF4285F4)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    // Portal Device Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E212B)
                    ) {
                        Text(
                            text = "Portal+ Smart Display",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9AA0A6)
                        )
                    }
                }

                if (showTelemetry) {
                    TelemetryOverlay(
                        telemetry = telemetryData,
                        isVoiceOnly = config.isVoiceOnly
                    )
                }
            }

            // Connection Status Pill & Settings Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val (statusColor, statusText) = when (connectionStatus) {
                    ConnectionStatus.DISCONNECTED -> Pair(Color(0xFF80868B), "Ready to Connect")
                    ConnectionStatus.CONNECTING -> Pair(Color(0xFF4285F4), "Connecting...")
                    ConnectionStatus.INITIALIZING -> Pair(Color(0xFFFFBA00), "Starting Session...")
                    ConnectionStatus.CONNECTED -> when {
                        isThinking -> Pair(Color(0xFFAB47BC), "Thinking...")
                        !isCameraStreaming -> Pair(Color(0xFF00BFA5), "Live · Voice Only")
                        config.isExtendedThinking -> Pair(Color(0xFF34A853), "Live (Deep Reasoning)")
                        else -> Pair(Color(0xFF34A853), "Live · Voice + Camera")
                    }
                    ConnectionStatus.ERROR -> Pair(Color(0xFFEA4335), "Connection Error")
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1B1D24),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2E39))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }

                // Settings Button
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF1B1D24))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }
        }

        // 6. PIP Orb Card (when camera is streaming fullscreen, Gemini Orb sits in the top-right PIP window)
        if (isCameraStreaming) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xD914151B),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF4285F4)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 32.dp)
                    .size(width = 220.dp, height = 140.dp)
                    .shadow(16.dp, RoundedCornerShape(20.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    GeminiOrb(
                        connectionStatus = connectionStatus,
                        speakerAmplitude = speakerAmplitude,
                        micAmplitude = micAmplitude,
                        size = 100.dp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x99000000),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4285F4))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Gemini Live",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 7. Subtitle Pill when streaming fullscreen (floats cleanly above bottom controls)
        if (isCameraStreaming) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
            ) {
                SubtitlePill(
                    latestTranscript = latestTranscript,
                    errorMessage = errorMessage,
                    isThinking = isThinking
                )
            }
        }

        // 8. Bottom Controls Container
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(36.dp),
                color = Color(0xFF1E2028),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C303E)),
                modifier = Modifier.shadow(16.dp, RoundedCornerShape(36.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mic Mute Button
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0xFFEA4335) else Color(0xFF2B303C))
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Camera Button (Toggles between Constant Streaming and Paused)
                    val (cameraBg, cameraIcon, cameraDesc) = if (isCameraStreaming) {
                        Triple(
                            Color(0xFF4285F4),
                            Icons.Default.Videocam,
                            "Camera Streaming Constantly (Tap to pause)"
                        )
                    } else {
                        Triple(
                            Color(0xFF2B303C),
                            Icons.Default.VideocamOff,
                            "Camera Paused / Voice Only (Tap to stream constantly)"
                        )
                    }

                    IconButton(
                        onClick = onToggleCamera,
                        enabled = true,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(cameraBg)
                    ) {
                        Icon(
                            imageVector = cameraIcon,
                            contentDescription = cameraDesc,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Conversation History Button
                    IconButton(
                        onClick = { showConversationHistory = !showConversationHistory },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (showConversationHistory) Color(0xFF3949AB) else Color(0xFF2B303C))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = if (showConversationHistory) "Hide History" else "Show Conversation History",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Primary Start / Stop Live Session Action
                    if (connectionStatus == ConnectionStatus.CONNECTED || connectionStatus == ConnectionStatus.CONNECTING || connectionStatus == ConnectionStatus.INITIALIZING) {
                        Button(
                            onClick = onDisconnect,
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA4335)),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "End Conversation",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        Button(
                            onClick = onConnect,
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4)),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Start Gemini Live",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Start Gemini Live",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // 9. Conversation History Panel (Bottom Left, matching Telemetry width)
        AnimatedVisibility(
            visible = showConversationHistory,
            enter = slideInVertically(initialOffsetY = { 80 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { 80 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 104.dp)
        ) {
            ConversationPanel(
                messages = conversationMessages,
                onClose = { showConversationHistory = false },
                onClear = onClearHistory,
                onExport = onExportConversation,
                isSessionEnded = isSessionEnded
            )
        }

        // Settings Dialog Modal
        if (showSettings) {
            SettingsDialog(
                currentConfig = config,
                showTelemetry = showTelemetry,
                currentFrameIntervalMs = currentFrameIntervalMs,
                onDismiss = { showSettings = false },
                onSave = { newConfig, newFrameInterval, newShowTelemetry ->
                    onUpdateConfig(newConfig, newFrameInterval, newShowTelemetry)
                },
                onOpenQrSetup = onOpenQrSetup
            )
        }
    }
}

@Composable
private fun SubtitlePill(
    latestTranscript: String,
    errorMessage: String?,
    isThinking: Boolean
) {
    AnimatedVisibility(
        visible = isThinking || latestTranscript.isNotBlank() || errorMessage != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { 20 }),
        exit = fadeOut()
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = when {
                errorMessage != null -> Color(0x33EA4335)
                isThinking && latestTranscript.isBlank() -> Color(0xCC2A1C3D)
                else -> Color(0xCC1A1C23)
            },
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                when {
                    errorMessage != null -> Color(0xFFEA4335)
                    isThinking && latestTranscript.isBlank() -> Color(0xFF7B1FA2)
                    else -> Color(0xFF333846)
                }
            ),
            modifier = Modifier
                .widthIn(max = 680.dp)
                .padding(horizontal = 24.dp)
        ) {
            if (isThinking && errorMessage == null && latestTranscript.isBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFFCE93D8),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Thinking...",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                        color = Color(0xFFE1BEE7),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = errorMessage ?: latestTranscript,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                    color = if (errorMessage != null) Color(0xFFFF8A80) else Color(0xFFE8EAED),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}
