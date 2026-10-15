package dev.portalgemini.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.portalgemini.api.LiveConfig

@Composable
fun SettingsDialog(
    currentConfig: LiveConfig,
    showTelemetry: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (LiveConfig, Long, Boolean) -> Unit,
    currentFrameIntervalMs: Long = 1000L,
    onOpenQrSetup: () -> Unit = {}
) {
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var selectedModel by remember { mutableStateOf(currentConfig.model) }
    var selectedVoice by remember { mutableStateOf(currentConfig.voice) }
    var systemPrompt by remember { mutableStateOf(currentConfig.systemPrompt) }
    var frameInterval by remember { mutableStateOf(currentFrameIntervalMs) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var voiceOnly by remember { mutableStateOf(currentConfig.voiceOnly) }
    var thinkingLevel by remember { mutableStateOf(currentConfig.thinkingLevel) }
    var telemetryEnabled by remember { mutableStateOf(showTelemetry) }

    LaunchedEffect(currentConfig, currentFrameIntervalMs) {
        apiKey = currentConfig.apiKey
        selectedModel = currentConfig.model
        selectedVoice = currentConfig.voice
        voiceOnly = currentConfig.voiceOnly
        thinkingLevel = currentConfig.thinkingLevel
        frameInterval = currentFrameIntervalMs
    }

    data class VoiceOption(
        val name: String,
        val style: String,
        val description: String
    )

    val voices = remember {
        listOf(
            VoiceOption("Puck", "Upbeat", "Playful & energetic"),
            VoiceOption("Charon", "Informative", "Deep & steady"),
            VoiceOption("Aoede", "Breezy", "Warm & conversational"),
            VoiceOption("Kore", "Firm", "Clear & composed"),
            VoiceOption("Fenrir", "Excitable", "Dynamic & bold")
        )
    }

    data class ModelOption(
        val id: String,
        val displayName: String,
        val badge: String,
        val description: String
    )

    val availableModels = remember {
        listOf(
            ModelOption(
                id = "models/gemini-3.8-live",
                displayName = "Gemini 3.8 Live (Recommended)",
                badge = "Voice + Vision",
                description = "Ultra-low latency audio-to-audio dialogue with native interleaved reasoning"
            )
        )
    }

    var isCustomModel by remember {
        mutableStateOf(availableModels.none { it.id == selectedModel })
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1F24),
            contentColor = Color.White,
            modifier = Modifier
                .width(680.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Gemini Live Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // API Key Field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Google AI Gemini API Key",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFB0B3B8)
                        )
                        OutlinedButton(
                            onClick = onOpenQrSetup,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                            border = BorderStroke(1.dp, Color(0x6638BDF8)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Phone Setup (QR)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Paste your AI Studio API key here...") },
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility",
                                    tint = Color.LightGray
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4285F4),
                            unfocusedBorderColor = Color(0xFF3C4043),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                // Model Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Model Selection",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFB0B3B8)
                    )

                    availableModels.forEach { modelOpt ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (!isCustomModel && selectedModel == modelOpt.id) Color(0xFF2A2E3D) else Color(0xFF24262E),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (!isCustomModel && selectedModel == modelOpt.id) Color(0xFF4285F4) else Color(0xFF333742)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isCustomModel = false
                                    selectedModel = modelOpt.id
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                RadioButton(
                                    selected = !isCustomModel && selectedModel == modelOpt.id,
                                    onClick = {
                                        isCustomModel = false
                                        selectedModel = modelOpt.id
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4285F4))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = modelOpt.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (modelOpt.badge == "Deep Reasoning") Color(0xFF3B1E5F) else Color(0xFF1E3A5F)
                                        ) {
                                            Text(
                                                text = modelOpt.badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (modelOpt.badge == "Deep Reasoning") Color(0xFFCE93D8) else Color(0xFF82B1FF),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = modelOpt.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9AA0A6)
                                    )
                                }
                            }
                        }
                    }

                    // Custom model option
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCustomModel) Color(0xFF2A2E3D) else Color(0xFF24262E),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCustomModel) Color(0xFF4285F4) else Color(0xFF333742)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCustomModel = true }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isCustomModel,
                                    onClick = { isCustomModel = true },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4285F4))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Custom Model Name",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White
                                )
                            }
                            if (isCustomModel) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = selectedModel,
                                    onValueChange = { selectedModel = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("e.g. models/gemini-3.8-live") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF4285F4),
                                        unfocusedBorderColor = Color(0xFF3C4043),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }

                // Gemini 3.8 Live Info Note
                if (selectedModel.contains("gemini-3.8-live")) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E2838),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✨ Gemini 3.8 Live features native interleaved reasoning. Internal thoughts and speech plan in real-time with zero reasoning latency.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF90CAF9)
                            )
                        }
                    }
                }

                // Extended Thinking Level Selector (only shown when an older extended-thinking model is selected, not Gemini 3.8)
                val isExtendedThinking = selectedModel.contains("extended-thinking", ignoreCase = true) && !selectedModel.contains("3.8")
                if (isExtendedThinking) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Extended Thinking Level",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFB0B3B8)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF3B1E5F)
                            ) {
                                Text(
                                    text = "Reasoning Depth",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFCE93D8),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Low", "Medium", "High").forEach { level ->
                                FilterChip(
                                    selected = thinkingLevel.equals(level, ignoreCase = true),
                                    onClick = { thinkingLevel = level.lowercase() },
                                    label = { Text(level) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF7B1FA2),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF2D2F36),
                                        labelColor = Color(0xFFCCCCCC)
                                    )
                                )
                            }
                        }
                    }
                }

                // Voice Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Gemini Voice Persona",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFB0B3B8)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• 5 Studio Voices",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8AB4F8)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        voices.forEach { voice ->
                            val isSelected = selectedVoice.equals(voice.name, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedVoice = voice.name },
                                label = {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 3.dp, horizontal = 2.dp)
                                    ) {
                                        Text(
                                            text = voice.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = voice.style,
                                            fontSize = 10.sp,
                                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color(0xFF9E9E9E)
                                        )
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF4285F4),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF2D2F36),
                                    labelColor = Color(0xFFCCCCCC)
                                )
                            )
                        }
                    }
                }

                // Voice-Only Mode Switch
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF24262E),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333742)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Start in Voice-Only Mode",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White
                                )
                                if (voiceOnly) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF3B2F18)
                                    ) {
                                        Text(
                                            text = "Camera Off by Default",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFFFB74D),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Start session with camera off. Tap the camera button on screen to turn on the camera at any time without reconnecting.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9AA0A6)
                            )
                        }
                        Switch(
                            checked = voiceOnly,
                            onCheckedChange = { voiceOnly = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF4285F4),
                                uncheckedThumbColor = Color(0xFF9AA0A6),
                                uncheckedTrackColor = Color(0xFF333742)
                            )
                        )
                    }
                }

                // Camera Video Frame Rate (Greyed out in voice-only mode)
                Column(
                    modifier = Modifier.alpha(if (voiceOnly) 0.38f else 1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Camera Frame Rate to Gemini",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (voiceOnly) Color(0xFF6B7280) else Color(0xFFB0B3B8)
                        )
                        if (voiceOnly) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "(When camera is turned on)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF80868B)
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(
                            Pair("1 Frame/sec (Recommended)", 1000L),
                            Pair("2 Frames/sec", 500L),
                            Pair("0.5 FPS (Bandwidth Saver)", 2000L)
                        ).forEach { (label, interval) ->
                            FilterChip(
                                enabled = true,
                                selected = frameInterval == interval,
                                onClick = { frameInterval = interval },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF4285F4),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF2D2F36),
                                    labelColor = Color(0xFFCCCCCC),
                                    disabledContainerColor = Color(0xFF202228),
                                    disabledLabelColor = Color(0xFF555A64)
                                )
                            )
                        }
                    }
                }

                // Live Telemetry HUD Switch
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF24262E),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Live Telemetry HUD",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Display real-time network, video/audio throughput, and latency in the top-left corner",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9AA0A6)
                            )
                        }
                        Switch(
                            checked = telemetryEnabled,
                            onCheckedChange = { telemetryEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF00E5FF),
                                uncheckedThumbColor = Color(0xFF9AA0A6),
                                uncheckedTrackColor = Color(0xFF333742)
                            )
                        )
                    }
                }

                // System Prompt / Personality
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "System Prompt / Persona",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFB0B3B8)
                    )
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4285F4),
                            unfocusedBorderColor = Color(0xFF3C4043),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.LightGray)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val newConfig = currentConfig.copy(
                                apiKey = apiKey.trim(),
                                model = selectedModel,
                                voice = selectedVoice,
                                systemPrompt = systemPrompt.trim(),
                                voiceOnly = voiceOnly,
                                thinkingLevel = thinkingLevel.lowercase()
                            )
                            onSave(newConfig, frameInterval, telemetryEnabled)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
                    ) {
                        Text("Save & Apply", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
