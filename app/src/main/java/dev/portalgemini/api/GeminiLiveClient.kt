package dev.portalgemini.api

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    INITIALIZING, // WebSocket connected, setup sent
    CONNECTED,    // SetupComplete received, live session ready
    ERROR
}

data class LiveConfig(
    val apiKey: String,
    val model: String = "models/gemini-3.8-live",
    val voice: String = "Puck",
    val systemPrompt: String = "You are Gemini, a helpful and friendly AI assistant running on a Meta Portal smart display. You can see through the camera and hear through the microphones. Keep answers conversational, natural, and concise.",
    val voiceOnly: Boolean = false,
    val thinkingLevel: String = "high"
) {
    val isVoiceOnly: Boolean
        get() = voiceOnly

    val isExtendedThinking: Boolean
        get() = model.contains("extended-thinking", ignoreCase = true)
}

class GeminiLiveClient(
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onTranscript: (String) -> Unit,
    private val onUserTranscript: (String) -> Unit = {},
    private val onThought: (String) -> Unit = {},
    private val onTurnComplete: () -> Unit = {},
    private val telemetryManager: dev.portalgemini.telemetry.TelemetryManager? = null
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val BASE_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private var webSocket: WebSocket? = null
    private var sessionGeneration = 0
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for continuous stream
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking

    private var currentConfig: LiveConfig? = null

    private fun setStatus(newStatus: ConnectionStatus) {
        _status.value = newStatus
        telemetryManager?.onConnectionStatusChanged(newStatus)
    }

    fun connect(config: LiveConfig) {
        if (_status.value == ConnectionStatus.CONNECTING ||
            _status.value == ConnectionStatus.INITIALIZING ||
            _status.value == ConnectionStatus.CONNECTED) {
            return
        }

        try {
            webSocket?.close(1000, "Reconnecting")
        } catch (e: Exception) {
            // ignore
        }
        webSocket = null

        val currentGen = ++sessionGeneration
        currentConfig = config
        _errorMessage.value = null
        _isThinking.value = false
        setStatus(ConnectionStatus.CONNECTING)

        val url = "$BASE_URL?key=${config.apiKey.trim()}"
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (currentGen != sessionGeneration) return
                Log.i(TAG, "WebSocket opened, sending setup for ${config.model}...")
                setStatus(ConnectionStatus.INITIALIZING)
                sendSetup(ws, config)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                if (currentGen != sessionGeneration) return
                val text = bytes.utf8()
                handleServerMessage(text)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (currentGen != sessionGeneration) return
                handleServerMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                if (currentGen != sessionGeneration) return
                Log.i(TAG, "WebSocket closing: $code")
                ws.close(1000, null)
                setStatus(ConnectionStatus.DISCONNECTED)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (currentGen != sessionGeneration) return
                Log.i(TAG, "WebSocket closed: $code")
                setStatus(ConnectionStatus.DISCONNECTED)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (currentGen != sessionGeneration) return
                // Responses and exception messages can contain request URLs (including the key).
                val errorMsg = response?.let { "Connection failed (HTTP ${it.code})" }
                    ?: "Connection failed. Check your network and API settings."
                Log.e(TAG, "WebSocket failure (${t.javaClass.simpleName})")
                _errorMessage.value = errorMsg
                setStatus(ConnectionStatus.ERROR)
            }
        })
    }

    private fun sendSetup(ws: WebSocket, config: LiveConfig) {
        try {
            val modelName = if (config.model.startsWith("models/")) config.model else "models/${config.model}"
            val setupJson = JSONObject().apply {
                val setup = JSONObject().apply {
                    put("model", modelName)
                    put("inputAudioTranscription", JSONObject())
                    put("outputAudioTranscription", JSONObject())

                    val generationConfig = JSONObject().apply {
                        val responseModalities = JSONArray().apply {
                            put("AUDIO")
                        }
                        put("responseModalities", responseModalities)

                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuiltVoiceConfig = JSONObject().apply {
                                    put("voiceName", config.voice)
                                }
                                put("prebuiltVoiceConfig", prebuiltVoiceConfig)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)

                        // Note: thinking_level is NOT supported for gemini-3.8-live per API docs (it features native interleaved reasoning)
                        if (config.isExtendedThinking && !modelName.contains("gemini-3.8", ignoreCase = true)) {
                            val thinkingConfig = JSONObject().apply {
                                put("thinkingLevel", config.thinkingLevel.lowercase())
                            }
                            put("thinkingConfig", thinkingConfig)
                        }
                    }
                    put("generationConfig", generationConfig)

                    if (config.systemPrompt.isNotBlank()) {
                        val systemInstruction = JSONObject().apply {
                            val parts = JSONArray().apply {
                                val part = JSONObject().apply {
                                    put("text", config.systemPrompt)
                                }
                                put(part)
                            }
                            put("parts", parts)
                        }
                        put("systemInstruction", systemInstruction)
                    }
                }
                put("setup", setup)
            }

            val setupMsg = setupJson.toString()
            telemetryManager?.onSetupSent()
            ws.send(setupMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating setup message (${e.javaClass.simpleName})")
            _errorMessage.value = "Failed to create setup message. Check your settings."
            setStatus(ConnectionStatus.ERROR)
        }
    }

    private var audioChunkCount = 0
    fun sendAudioChunk(pcmChunk: ByteArray) {
        if (_status.value != ConnectionStatus.CONNECTED) return
        val ws = webSocket ?: return

        try {
            val base64Data = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
            val json = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val audio = JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Data)
                    }
                    put("audio", audio)
                }
                put("realtime_input", realtimeInput)
            }
            ws.send(json.toString())
            telemetryManager?.onAudioChunkSent(pcmChunk.size)
            audioChunkCount++
            if (audioChunkCount % 50 == 0) {
                Log.d(TAG, "Sent audio chunk #$audioChunkCount (${pcmChunk.size} bytes)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk", e)
        }
    }

    fun sendImageFrame(jpegBytes: ByteArray) {
        if (_status.value != ConnectionStatus.CONNECTED) return
        val ws = webSocket ?: return

        try {
            val base64Data = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            val json = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val video = JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Data)
                    }
                    put("video", video)
                }
                put("realtime_input", realtimeInput)
            }
            ws.send(json.toString())
            telemetryManager?.onImageFrameSent(jpegBytes.size)
            Log.d(TAG, "Sent video frame (${jpegBytes.size} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending video frame", e)
        }
    }

    private fun handleServerMessage(text: String) {
        try {
            val json = JSONObject(text)

            // Setup confirmation
            if (json.has("setupComplete") || json.has("setup_complete")) {
                Log.i(TAG, "Gemini Live setup complete! Session is LIVE.")
                telemetryManager?.onSetupComplete()
                setStatus(ConnectionStatus.CONNECTED)
                return
            }

            // Voice activity detection event
            if (json.has("voiceActivity")) {
                val va = json.getJSONObject("voiceActivity")
                val vaType = va.optString("type", "")
                Log.d(TAG, "Voice activity: $vaType")
            }

            // Server Content (Audio / Transcript / Turn Status)
            val serverContent = json.optJSONObject("serverContent")
                ?: json.optJSONObject("server_content")
            if (serverContent != null) {

                // Check for user speech transcription (both camelCase and snake_case)
                val inputTrans = serverContent.optJSONObject("inputTranscription")
                    ?: serverContent.optJSONObject("input_transcription")
                if (inputTrans != null) {
                    val userText = inputTrans.optString("text", "")
                    if (userText.isNotBlank()) {
                        onUserTranscript(userText)
                    }
                } else if (serverContent.has("userTurn")) {
                    val userTurn = serverContent.getJSONObject("userTurn")
                    val parts = userTurn.optJSONArray("parts")
                    if (parts != null) {
                        val sb = StringBuilder()
                        for (i in 0 until parts.length()) {
                            val p = parts.getJSONObject(i)
                            if (p.has("text")) {
                                sb.append(p.getString("text"))
                            }
                        }
                        val userText = sb.toString()
                        if (userText.isNotBlank()) {
                            onUserTranscript(userText)
                        }
                    }
                }

                // Check for user interruption (barge-in)
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.i(TAG, "Model was interrupted by user!")
                    _isThinking.value = false
                    telemetryManager?.onInterrupted()
                    onInterrupted()
                }

                // Check for model speech transcription (outputAudioTranscription)
                var hasOutputTranscript = false
                val outputTrans = serverContent.optJSONObject("outputTranscription")
                    ?: serverContent.optJSONObject("output_transcription")
                if (outputTrans != null) {
                    val modelText = outputTrans.optString("text", "")
                    if (modelText.isNotBlank()) {
                        _isThinking.value = false
                        hasOutputTranscript = true
                        telemetryManager?.onTextReceived(modelText.length)
                        onTranscript(modelText)
                    }
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Audio chunk from Gemini
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val base64Data = inlineData.optString("data", "")
                                if (base64Data.isNotEmpty()) {
                                    val pcmData = Base64.decode(base64Data, Base64.NO_WRAP)
                                    _isThinking.value = false
                                    telemetryManager?.onAudioReceived(pcmData.size)
                                    onAudioReceived(pcmData)
                                }
                            }

                            // Text from Gemini: check if it's internal reasoning or spoken subtitle
                            if (part.has("text")) {
                                val text = part.getString("text")
                                val isThought = part.optBoolean("thought", false)
                                telemetryManager?.onTextReceived(text.length)
                                if (isThought) {
                                    _isThinking.value = true
                                    onThought(text)
                                } else if (!hasOutputTranscript) {
                                    _isThinking.value = false
                                    onTranscript(text)
                                }
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false) || serverContent.optBoolean("turn_complete", false)) {
                    _isThinking.value = false
                    onTurnComplete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling server message (${e.javaClass.simpleName})")
        }
    }

    fun disconnect() {
        sessionGeneration++
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing WebSocket (${e.javaClass.simpleName})")
        }
        webSocket = null
        _isThinking.value = false
        telemetryManager?.onDisconnected()
        setStatus(ConnectionStatus.DISCONNECTED)
    }
}
