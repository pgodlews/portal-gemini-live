package dev.portalgemini.camera

enum class CameraMode {
    STANDBY,    // Camera preview active locally, 0 frames sent to Gemini ($0 camera token cost)
    AUTO,       // Actively streaming frames for current question; auto-pauses when turnComplete is received
    CONTINUOUS  // Continuously streaming frames without auto-pausing
}
