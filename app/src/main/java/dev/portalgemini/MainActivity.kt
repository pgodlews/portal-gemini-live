package dev.portalgemini

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import dev.portalgemini.api.ConnectionStatus
import dev.portalgemini.api.GeminiLiveClient
import dev.portalgemini.api.LiveConfig
import dev.portalgemini.audio.AudioPlayer
import dev.portalgemini.audio.AudioRecorder
import dev.portalgemini.camera.PortalCameraManager
import dev.portalgemini.model.ChatMessage
import dev.portalgemini.model.ChatSender
import dev.portalgemini.server.PortalHttpServer
import dev.portalgemini.server.ServerMode
import dev.portalgemini.telemetry.TelemetryManager
import dev.portalgemini.ui.MainScreen
import dev.portalgemini.ui.QrCodeDialog

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "GeminiMainActivity"
        private const val PREFS_NAME = "gemini_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_VOICE = "voice"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_FRAME_INTERVAL = "frame_interval_ms"
        private const val KEY_VOICE_ONLY = "voice_only"
        private const val KEY_THINKING_LEVEL = "thinking_level"
        private const val KEY_SHOW_TELEMETRY = "show_telemetry"
        private const val KEY_AUTO_SWITCH_CAMERA = "auto_switch_camera"
        private const val KEY_AUTO_SWITCH_VISION = "auto_switch_vision"
    }

    private lateinit var audioPlayer: AudioPlayer
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var cameraManager: PortalCameraManager
    private lateinit var liveClient: GeminiLiveClient
    private lateinit var telemetryManager: TelemetryManager

    private val conversationMessages = mutableStateListOf<ChatMessage>()
    private var isModelTurnActive = false
    private var lastUserTurnTimeMs = 0L
    private var currentSessionIndex by mutableStateOf(1)
    private var isCurrentSessionEnded by mutableStateOf(false)

    private var currentConfig by mutableStateOf(LiveConfig(apiKey = ""))
    private var connectionStatus by mutableStateOf(ConnectionStatus.DISCONNECTED)
    private var showTelemetry by mutableStateOf(true)
    private var autoSwitchCamera by mutableStateOf(true)
    private var localErrorMessage by mutableStateOf<String?>(null)
    private var speakerAmplitude by mutableStateOf(0f)
    private var micAmplitude by mutableStateOf(0f)
    private var isMuted by mutableStateOf(false)
    private var isCameraStreaming by mutableStateOf(true)
    private var latestTranscript by mutableStateOf("")
    private var activeHttpServer by mutableStateOf<PortalHttpServer?>(null)

    private fun startQrSetup() {
        stopHttpServer()
        val server = PortalHttpServer(
            context = this,
            mode = ServerMode.SETUP,
            initialConfig = currentConfig,
            onConfigReceived = { apiKey, model, voice ->
                val newConfig = currentConfig.copy(apiKey = apiKey, model = model, voice = voice)
                currentConfig = newConfig
                telemetryManager.updateConfig(model, voice, cameraManager.frameIntervalMs)
                savePreferences(newConfig, cameraManager.frameIntervalMs, showTelemetry, autoSwitchCamera)
            }
        )
        if (server.start()) {
            activeHttpServer = server
        }
    }

    private fun startConversationExport() {
        stopHttpServer()
        val server = PortalHttpServer(
            context = this,
            mode = ServerMode.EXPORT,
            conversationMessages = conversationMessages.toList()
        )
        if (server.start()) {
            activeHttpServer = server
        }
    }

    private fun stopHttpServer() {
        activeHttpServer?.stop()
        activeHttpServer = null
    }

    // Meta Portal persistent top bar inset quirk
    private val topInsetDp = if (Build.MANUFACTURER.equals("Facebook", true) || Build.MODEL.contains("Portal", true)) 64 else 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        Log.i(TAG, "Permissions result: audio=$audioGranted, camera=$cameraGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

        loadPreferences()
        checkAndRequestPermissions()

        // 1. Telemetry Manager (Network, Throughput, Latency)
        telemetryManager = TelemetryManager(this)

        // 2. Audio Output (Gemini -> Portal Speakers)
        audioPlayer = AudioPlayer()

        // 3. Audio Input (Portal Mics -> Gemini)
        audioRecorder = AudioRecorder { pcmChunk ->
            liveClient.sendAudioChunk(pcmChunk)
        }

        // 4. Camera Streamer (Portal Camera -> Gemini)
        cameraManager = PortalCameraManager(this) { jpegBytes ->
            liveClient.sendImageFrame(jpegBytes)
        }
        val savedFrameInterval = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_FRAME_INTERVAL, 1000L)
        cameraManager.frameIntervalMs = savedFrameInterval

        telemetryManager.updateConfig(currentConfig.model, currentConfig.voice, cameraManager.frameIntervalMs)

        // 5. Gemini Live WebSocket Client
        liveClient = GeminiLiveClient(
            onAudioReceived = { pcm24k ->
                audioPlayer.queueAudio(pcm24k)
            },
            onInterrupted = {
                runOnUiThread {
                    // User interrupted the model! Instantly flush speaker audio
                    audioPlayer.interrupt()
                    isModelTurnActive = false
                }
            },
            onTranscript = { text ->
                runOnUiThread {
                    val lastMsg = conversationMessages.lastOrNull()
                    val fullText = if (isModelTurnActive && lastMsg != null && lastMsg.sender == ChatSender.GEMINI && lastMsg.sessionIndex == currentSessionIndex) {
                        val combined = if (text.startsWith(lastMsg.text)) text else lastMsg.text + text
                        conversationMessages[conversationMessages.lastIndex] = lastMsg.copy(text = combined)
                        combined
                    } else {
                        val clean = text.trimStart()
                        conversationMessages.add(ChatMessage(sender = ChatSender.GEMINI, text = clean, sessionIndex = currentSessionIndex))
                        isModelTurnActive = true
                        clean
                    }
                    latestTranscript = fullText
                }
            },
            onUserTranscript = { text ->
                runOnUiThread {
                    val now = System.currentTimeMillis()
                    val lastMsg = conversationMessages.lastOrNull()
                    if (lastMsg != null && lastMsg.sender == ChatSender.USER && lastMsg.sessionIndex == currentSessionIndex && (now - lastUserTurnTimeMs) < 3500L) {
                        val combined = if (text.startsWith(lastMsg.text)) {
                            text
                        } else {
                            lastMsg.text + text
                        }
                        conversationMessages[conversationMessages.lastIndex] = lastMsg.copy(text = combined)
                    } else {
                        conversationMessages.add(ChatMessage(sender = ChatSender.USER, text = text.trimStart(), sessionIndex = currentSessionIndex))
                    }
                    lastUserTurnTimeMs = now
                    isModelTurnActive = false
                }
            },
            onThought = { thought ->
                runOnUiThread {
                    Log.d(TAG, "Gemini 3.8 Live Interleaved Reasoning trace: $thought")
                }
            },
            onTurnComplete = {
                runOnUiThread {
                    telemetryManager.onTurnComplete()
                    isModelTurnActive = false
                }
            },
            telemetryManager = telemetryManager
        )

        handleIntent(intent)

        setContent {
            val status by liveClient.status.collectAsState()
            val err by liveClient.errorMessage.collectAsState()
            val isThinking by liveClient.isThinking.collectAsState()
            val spkAmp by audioPlayer.amplitude.collectAsState()
            val micAmp by audioRecorder.amplitude.collectAsState()
            val muted by audioRecorder.isMuted.collectAsState()
            val camStreaming by cameraManager.isStreamingFrames.collectAsState()
            val cameraMode by cameraManager.cameraMode.collectAsState()
            val telemetryData by telemetryManager.telemetry.collectAsState()

            LaunchedEffect(status) {
                if (status == ConnectionStatus.DISCONNECTED || status == ConnectionStatus.ERROR) {
                    if (conversationMessages.any { it.sessionIndex == currentSessionIndex }) {
                        isCurrentSessionEnded = true
                    }
                }
            }

            LaunchedEffect(cameraMode) {
                telemetryManager.setCameraMode(cameraMode)
            }

            MainScreen(
                connectionStatus = status,
                errorMessage = localErrorMessage ?: err,
                speakerAmplitude = spkAmp,
                micAmplitude = micAmp,
                isMuted = muted,
                isCameraStreaming = camStreaming,
                latestTranscript = latestTranscript,
                config = currentConfig,
                isThinking = isThinking,
                telemetryData = telemetryData,
                showTelemetry = showTelemetry,
                conversationMessages = conversationMessages,
                isSessionEnded = isCurrentSessionEnded,
                onClearHistory = {
                    conversationMessages.clear()
                    currentSessionIndex = 1
                    isCurrentSessionEnded = false
                },
                onExportConversation = { startConversationExport() },
                onOpenQrSetup = { startQrSetup() },
                onConnect = {
                    localErrorMessage = null
                    startLiveSession()
                },
                onDisconnect = {
                    localErrorMessage = null
                    stopLiveSession()
                },
                onToggleMute = {
                    audioRecorder.toggleMute()
                },
                onToggleCamera = {
                    val isNowStreaming = cameraManager.toggleStreaming()
                    currentConfig = currentConfig.copy(voiceOnly = !isNowStreaming)
                },
                onUpdateConfig = { newConfig, newFrameInterval, newShowTelemetry ->
                    currentConfig = newConfig
                    cameraManager.frameIntervalMs = newFrameInterval
                    showTelemetry = newShowTelemetry
                    if (newConfig.voiceOnly) {
                        cameraManager.setCameraMode(dev.portalgemini.camera.CameraMode.STANDBY)
                    }
                    telemetryManager.updateConfig(newConfig.model, newConfig.voice, newFrameInterval)
                    savePreferences(newConfig, newFrameInterval, newShowTelemetry, autoSwitchCamera)
                },
                onBindCameraTexture = { textureView ->
                    cameraManager.startCamera(textureView)
                },
                topInsetDp = topInsetDp,
                currentFrameIntervalMs = cameraManager.frameIntervalMs
            )

            // Auto-launch QR setup on first run if no API key is set
            LaunchedEffect(Unit) {
                if (currentConfig.apiKey.isBlank()) {
                    startQrSetup()
                }
            }

            // QR Code Dialog (Setup or Export)
            activeHttpServer?.let { server ->
                QrCodeDialog(
                    server = server,
                    onDismiss = { stopHttpServer() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val apiKeyExtra = intent?.getStringExtra("api_key") ?: intent?.getStringExtra("key")
        val modelExtra = intent?.getStringExtra("model")
        val voiceExtra = intent?.getStringExtra("voice")
        val thinkingLevelExtra = intent?.getStringExtra("thinking_level")

        var configChanged = false
        var updated = currentConfig

        if (!apiKeyExtra.isNullOrBlank()) {
            updated = updated.copy(apiKey = apiKeyExtra.trim())
            configChanged = true
            Log.i(TAG, "API key updated from intent extra")
        }
        if (!modelExtra.isNullOrBlank()) {
            updated = updated.copy(model = modelExtra.trim())
            configChanged = true
            Log.i(TAG, "Model updated from intent extra: ${updated.model}")
        }
        if (!voiceExtra.isNullOrBlank()) {
            updated = updated.copy(voice = voiceExtra.trim())
            configChanged = true
            Log.i(TAG, "Voice updated from intent extra: ${updated.voice}")
        }
        if (thinkingLevelExtra != null && thinkingLevelExtra.isNotBlank()) {
            updated = updated.copy(thinkingLevel = thinkingLevelExtra.trim())
            configChanged = true
            Log.i(TAG, "Thinking level updated from intent extra: ${updated.thinkingLevel}")
        }

        if (intent != null && (intent.hasExtra("telemetry") || intent.hasExtra("show_telemetry"))) {
            val tel = intent.getBooleanExtra("telemetry", intent.getBooleanExtra("show_telemetry", true))
            showTelemetry = tel
            val interval = if (::cameraManager.isInitialized) cameraManager.frameIntervalMs else 1000L
            savePreferences(currentConfig, interval, showTelemetry, autoSwitchCamera)
            Log.i(TAG, "Telemetry visibility updated from intent extra: $showTelemetry")
        }

        if (intent != null && (intent.hasExtra("auto_switch_camera") || intent.hasExtra("auto_switch_vision"))) {
            val autoCam = intent.getBooleanExtra("auto_switch_camera", intent.getBooleanExtra("auto_switch_vision", true))
            autoSwitchCamera = autoCam
            if (::cameraManager.isInitialized) {
                cameraManager.autoSwitchEnabled = autoCam
            }
            val interval = if (::cameraManager.isInitialized) cameraManager.frameIntervalMs else 1000L
            savePreferences(currentConfig, interval, showTelemetry, autoSwitchCamera)
            Log.i(TAG, "Auto-switch camera updated from intent extra: $autoSwitchCamera")
        }

        if (configChanged) {
            currentConfig = updated
            localErrorMessage = null
            val interval = if (::cameraManager.isInitialized) cameraManager.frameIntervalMs else 1000L
            if (::telemetryManager.isInitialized) {
                telemetryManager.updateConfig(currentConfig.model, currentConfig.voice, interval)
            }
            savePreferences(currentConfig, interval, showTelemetry, autoSwitchCamera)
            if (liveClient.status.value == ConnectionStatus.CONNECTED || liveClient.status.value == ConnectionStatus.CONNECTING) {
                stopLiveSession()
                startLiveSession()
            }
        }

        val autoConnect = intent?.getBooleanExtra("auto_connect", false) ?: false
        if (autoConnect && currentConfig.apiKey.isNotBlank()) {
            if (liveClient.status.value != ConnectionStatus.CONNECTED && liveClient.status.value != ConnectionStatus.CONNECTING) {
                Log.i(TAG, "Auto-connecting live session from intent...")
                startLiveSession()
            }
        }
    }

    private fun startLiveSession() {
        if (currentConfig.apiKey.isBlank()) {
            localErrorMessage = "Please configure your Gemini API Key in Settings (gear icon)"
            return
        }

        if (isCurrentSessionEnded && conversationMessages.any { it.sessionIndex == currentSessionIndex }) {
            currentSessionIndex++
        }
        isCurrentSessionEnded = false

        if (currentConfig.voiceOnly) {
            cameraManager.setCameraMode(dev.portalgemini.camera.CameraMode.STANDBY)
        } else {
            cameraManager.setCameraMode(dev.portalgemini.camera.CameraMode.CONTINUOUS)
        }

        audioRecorder.start()
        liveClient.connect(currentConfig)
    }

    private fun stopLiveSession() {
        liveClient.disconnect()
        audioRecorder.stop()
        audioPlayer.interrupt()
        latestTranscript = ""
        cameraManager.setCameraMode(dev.portalgemini.camera.CameraMode.STANDBY)
        if (conversationMessages.any { it.sessionIndex == currentSessionIndex }) {
            isCurrentSessionEnded = true
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun applyImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        var model = prefs.getString(KEY_MODEL, "models/gemini-3.8-live") ?: "models/gemini-3.8-live"
        if (model.contains("gemini-2.0") || model.contains("gemini-3.1")) {
            model = "models/gemini-3.8-live"
        }
        val voice = prefs.getString(KEY_VOICE, "Puck") ?: "Puck"
        val prompt = prefs.getString(
            KEY_SYSTEM_PROMPT,
            "You are Gemini, a helpful and friendly conversational AI assistant running on a Meta Portal smart display. You can see through the camera and hear through the microphones. Keep answers conversational, natural, and concise."
        ) ?: ""
        val frameInterval = prefs.getLong(KEY_FRAME_INTERVAL, 1000L)
        val voiceOnly = prefs.getBoolean(KEY_VOICE_ONLY, false)
        val thinkingLevel = prefs.getString(KEY_THINKING_LEVEL, "high") ?: "high"
        showTelemetry = prefs.getBoolean(KEY_SHOW_TELEMETRY, true)
        autoSwitchCamera = prefs.getBoolean(KEY_AUTO_SWITCH_CAMERA, prefs.getBoolean(KEY_AUTO_SWITCH_VISION, true))
        if (::cameraManager.isInitialized) {
            cameraManager.frameIntervalMs = frameInterval
            cameraManager.autoSwitchEnabled = autoSwitchCamera
        }

        currentConfig = LiveConfig(apiKey, model, voice, prompt, voiceOnly, thinkingLevel)
    }

    private fun savePreferences(
        config: LiveConfig,
        frameIntervalMs: Long,
        showTel: Boolean = showTelemetry,
        autoSwitch: Boolean = autoSwitchCamera
    ) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_MODEL, config.model)
            .putString(KEY_VOICE, config.voice)
            .putString(KEY_SYSTEM_PROMPT, config.systemPrompt)
            .putLong(KEY_FRAME_INTERVAL, frameIntervalMs)
            .putBoolean(KEY_VOICE_ONLY, config.voiceOnly)
            .putString(KEY_THINKING_LEVEL, config.thinkingLevel)
            .putBoolean(KEY_SHOW_TELEMETRY, showTel)
            .putBoolean(KEY_AUTO_SWITCH_CAMERA, autoSwitch)
            .putBoolean(KEY_AUTO_SWITCH_VISION, autoSwitch)
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHttpServer()
        stopLiveSession()
        audioPlayer.release()
        cameraManager.release()
        telemetryManager.destroy()
    }
}
