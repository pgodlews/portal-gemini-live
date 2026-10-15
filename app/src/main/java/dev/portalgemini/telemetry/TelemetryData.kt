package dev.portalgemini.telemetry

import dev.portalgemini.api.ConnectionStatus
import dev.portalgemini.camera.CameraMode

data class TelemetryData(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val networkType: String = "Offline",
    val linkSpeedMbps: Int? = null,
    val rssiDbm: Int? = null,
    val setupLatencyMs: Long? = null,
    val turnLatencyMs: Long? = null,
    val videoFps: Double = 0.0,
    val videoTxKbps: Double = 0.0,
    val lastFrameSizeKb: Double = 0.0,
    val audioTxKbps: Double = 0.0,
    val audioRxKbps: Double = 0.0,
    val totalTxBytes: Long = 0L,
    val totalRxBytes: Long = 0L,
    val sessionDurationSec: Long = 0L,
    val estimatedCostUsd: Double = 0.0,
    val estimatedCostGbp: Double = 0.0,
    val audioCostUsd: Double = 0.0,
    val cameraCostUsd: Double = 0.0,
    val cameraFramesSent: Int = 0,
    val cameraMode: CameraMode = CameraMode.STANDBY,
    val model: String = "models/gemini-3.8-live",
    val voice: String = "Puck",
    val frameIntervalMs: Long = 1000L
) {
    val targetFps: Double
        get() = if (frameIntervalMs > 0) 1000.0 / frameIntervalMs else 1.0

    val networkSummary: String
        get() = buildString {
            append(networkType)
            if (linkSpeedMbps != null && linkSpeedMbps > 0) {
                append(" · ")
                append(linkSpeedMbps)
                append(" Mbps")
            }
            if (rssiDbm != null && rssiDbm != 0) {
                append(" · ")
                append(rssiDbm)
                append(" dBm")
            }
        }
}
