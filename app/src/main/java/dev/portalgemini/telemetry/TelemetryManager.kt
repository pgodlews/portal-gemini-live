package dev.portalgemini.telemetry

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.SystemClock
import dev.portalgemini.api.ConnectionStatus
import dev.portalgemini.camera.CameraMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class TelemetryManager(private val context: Context) {

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var tickerJob: Job? = null

    // Session timers & latencies
    private var sessionStartTime = 0L
    private var setupSentTime = 0L
    private var setupLatencyMs: Long? = null
    private var lastUserAudioTime = 0L
    private var turnLatencyMs: Long? = null
    private var isAwaitingResponse = false

    // Total lifetime session counters
    private val totalTxBytes = AtomicLong(0L)
    private val totalRxBytes = AtomicLong(0L)

    // Cost estimation counters
    private val totalAudioInBytes = AtomicLong(0L)
    private val totalAudioOutBytes = AtomicLong(0L)
    private val totalCameraFrames = AtomicInteger(0)
    private var currentCameraMode = CameraMode.STANDBY

    // Model, voice and frame rate config
    private var model: String = "models/gemini-3.8-live"
    private var voice: String = "Puck"
    private var frameIntervalMs: Long = 1000L

    // Windowed counters for 1-second rolling rates
    private val windowVideoFrames = AtomicInteger(0)
    private val windowVideoBytes = AtomicLong(0L)
    private val windowAudioTxBytes = AtomicLong(0L)
    private val windowAudioRxBytes = AtomicLong(0L)

    private var lastFrameSizeKb = 0.0
    private var currentStatus = ConnectionStatus.DISCONNECTED

    init {
        startPeriodicUpdates()
    }

    private fun startPeriodicUpdates() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                updateTelemetrySnapshot()
                delay(1000L)
            }
        }
    }

    private fun updateTelemetrySnapshot() {
        // Read window deltas and reset window counters
        val frames = windowVideoFrames.getAndSet(0)
        val videoBytes = windowVideoBytes.getAndSet(0L)
        val audioTx = windowAudioTxBytes.getAndSet(0L)
        val audioRx = windowAudioRxBytes.getAndSet(0L)

        val videoFps = frames.toDouble()
        val videoTxKbps = (videoBytes * 8.0) / 1000.0
        val audioTxKbps = (audioTx * 8.0) / 1000.0
        val audioRxKbps = (audioRx * 8.0) / 1000.0

        val sessionDuration = if (sessionStartTime > 0 && currentStatus == ConnectionStatus.CONNECTED) {
            (SystemClock.elapsedRealtime() - sessionStartTime) / 1000L
        } else {
            0L
        }

        // Cost estimation based on Gemini Live rates:
        // Audio input: $0.005/min (16kHz 16-bit mono = 32,000 B/s)
        // Audio output: $0.018/min (24kHz 16-bit mono = 48,000 B/s)
        // Video input: 258 tokens/frame at $0.30/1M tokens (~$0.0000774/frame)
        val audioInSec = totalAudioInBytes.get() / 32000.0
        val audioOutSec = totalAudioOutBytes.get() / 48000.0
        val audioInCost = (audioInSec / 60.0) * 0.005
        val audioOutCost = (audioOutSec / 60.0) * 0.018
        val audioCostUsd = audioInCost + audioOutCost

        val cameraFrames = totalCameraFrames.get()
        val cameraCostUsd = (cameraFrames * 258.0 / 1_000_000.0) * 0.30

        val totalCostUsd = audioCostUsd + cameraCostUsd
        val totalCostGbp = totalCostUsd * 0.77

        val (netType, speed, rssi) = getNetworkDetails()

        _telemetry.value = TelemetryData(
            connectionStatus = currentStatus,
            networkType = netType,
            linkSpeedMbps = speed,
            rssiDbm = rssi,
            setupLatencyMs = setupLatencyMs,
            turnLatencyMs = turnLatencyMs,
            videoFps = videoFps,
            videoTxKbps = videoTxKbps,
            lastFrameSizeKb = lastFrameSizeKb,
            audioTxKbps = audioTxKbps,
            audioRxKbps = audioRxKbps,
            totalTxBytes = totalTxBytes.get(),
            totalRxBytes = totalRxBytes.get(),
            sessionDurationSec = sessionDuration,
            estimatedCostUsd = totalCostUsd,
            estimatedCostGbp = totalCostGbp,
            audioCostUsd = audioCostUsd,
            cameraCostUsd = cameraCostUsd,
            cameraFramesSent = cameraFrames,
            cameraMode = currentCameraMode,
            model = model,
            voice = voice,
            frameIntervalMs = frameIntervalMs
        )
    }

    fun updateConfig(newModel: String, newVoice: String, newFrameIntervalMs: Long) {
        model = newModel
        voice = newVoice
        frameIntervalMs = newFrameIntervalMs
        updateTelemetrySnapshot()
    }

    private fun getNetworkDetails(): Triple<String, Int?, Int?> {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork ?: return Triple("Offline", null, null)
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return Triple("Offline", null, null)

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val info = wifiManager?.connectionInfo
                val rssi = info?.rssi
                val speed = info?.linkSpeed?.takeIf { it > 0 }
                val freq = info?.frequency ?: 0
                val band = when {
                    freq in 4900..5900 -> "5GHz"
                    freq in 2400..2500 -> "2.4GHz"
                    freq > 5900 -> "6GHz"
                    else -> ""
                }
                val label = if (band.isNotEmpty()) "Wi-Fi $band" else "Wi-Fi"
                return Triple(label, speed, rssi)
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return Triple("Ethernet", null, null)
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return Triple("Cellular", null, null)
            }
            return Triple("Connected", null, null)
        } catch (e: Exception) {
            return Triple("Network", null, null)
        }
    }

    // Callbacks from GeminiLiveClient and media pipeline
    fun onConnectionStatusChanged(status: ConnectionStatus) {
        currentStatus = status
        if (status == ConnectionStatus.DISCONNECTED || status == ConnectionStatus.ERROR) {
            sessionStartTime = 0L
            isAwaitingResponse = false
        }
    }

    fun onSetupSent() {
        setupSentTime = SystemClock.elapsedRealtime()
        setupLatencyMs = null
    }

    fun onSetupComplete() {
        if (setupSentTime > 0) {
            setupLatencyMs = SystemClock.elapsedRealtime() - setupSentTime
        }
        sessionStartTime = SystemClock.elapsedRealtime()
    }

    fun onAudioChunkSent(bytes: Int) {
        windowAudioTxBytes.addAndGet(bytes.toLong())
        totalAudioInBytes.addAndGet(bytes.toLong())
        totalTxBytes.addAndGet(bytes.toLong())
        lastUserAudioTime = SystemClock.elapsedRealtime()
        isAwaitingResponse = true
    }

    fun onImageFrameSent(bytes: Int) {
        windowVideoFrames.incrementAndGet()
        windowVideoBytes.addAndGet(bytes.toLong())
        totalCameraFrames.incrementAndGet()
        totalTxBytes.addAndGet(bytes.toLong())
        lastFrameSizeKb = bytes / 1024.0
    }

    fun onAudioReceived(bytes: Int) {
        windowAudioRxBytes.addAndGet(bytes.toLong())
        totalAudioOutBytes.addAndGet(bytes.toLong())
        totalRxBytes.addAndGet(bytes.toLong())
        if (isAwaitingResponse && lastUserAudioTime > 0) {
            turnLatencyMs = SystemClock.elapsedRealtime() - lastUserAudioTime
            isAwaitingResponse = false
        }
    }

    fun onTextReceived(chars: Int) {
        // Approximate bytes for JSON string payload
        val approxBytes = chars * 2L
        totalRxBytes.addAndGet(approxBytes)
        if (isAwaitingResponse && lastUserAudioTime > 0) {
            turnLatencyMs = SystemClock.elapsedRealtime() - lastUserAudioTime
            isAwaitingResponse = false
        }
    }

    fun onTurnComplete() {
        isAwaitingResponse = false
    }

    fun setCameraMode(mode: CameraMode) {
        currentCameraMode = mode
    }

    fun onInterrupted() {
        isAwaitingResponse = false
    }

    fun onDisconnected() {
        currentStatus = ConnectionStatus.DISCONNECTED
        sessionStartTime = 0L
        isAwaitingResponse = false
        windowVideoFrames.set(0)
        windowVideoBytes.set(0L)
        windowAudioTxBytes.set(0L)
        windowAudioRxBytes.set(0L)
        updateTelemetrySnapshot()
    }

    fun destroy() {
        tickerJob?.cancel()
    }
}
