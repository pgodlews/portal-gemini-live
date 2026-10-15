package dev.portalgemini.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class AudioRecorder(
    private val onAudioChunk: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // 100ms of 16-bit PCM audio at 16kHz = 1600 samples = 3200 bytes
        const val CHUNK_SIZE_BYTES = 3200
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Boolean> = _isMuted // placeholder type check; see below
    val amplitude: StateFlow<Float> = _micAmplitude

    @SuppressLint("MissingPermission")
    fun start() {
        if (_isRecording.value) return

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(CHUNK_SIZE_BYTES * 2)

        try {
            // VOICE_COMMUNICATION enables hardware AEC/DSP on Qualcomm / Portal platform
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed with VOICE_COMMUNICATION, trying MIC")
                record.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            } else {
                audioRecord = record
            }

            val session = audioRecord?.audioSessionId ?: 0
            if (session != 0) {
                if (AcousticEchoCanceler.isAvailable()) {
                    try {
                        echoCanceler = AcousticEchoCanceler.create(session)?.apply {
                            enabled = true
                        }
                        Log.d(TAG, "AcousticEchoCanceler enabled")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to enable AcousticEchoCanceler: ${e.message}")
                    }
                }

                if (NoiseSuppressor.isAvailable()) {
                    try {
                        noiseSuppressor = NoiseSuppressor.create(session)?.apply {
                            enabled = true
                        }
                        Log.d(TAG, "NoiseSuppressor enabled")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to enable NoiseSuppressor: ${e.message}")
                    }
                }
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            recordJob = scope.launch {
                val buffer = ByteArray(CHUNK_SIZE_BYTES)
                var chunkCount = 0
                while (isActive && _isRecording.value) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        // Calculate RMS amplitude for visualization
                        val amp = calculateRms(buffer, readBytes)
                        _micAmplitude.value = amp

                        chunkCount++
                        if (chunkCount % 20 == 0) {
                            Log.d(TAG, "Audio recorded: bytes=$readBytes, amp=$amp, muted=${_isMuted.value}")
                        }

                        if (!_isMuted.value) {
                            val chunk = buffer.copyOf(readBytes)
                            onAudioChunk(chunk)
                        }
                    } else if (readBytes < 0) {
                        Log.w(TAG, "AudioRecord error readBytes: $readBytes")
                        delay(50)
                    }
                }
            }
            Log.i(TAG, "Audio recording started at ${SAMPLE_RATE}Hz")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            stop()
        }
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    fun toggleMute(): Boolean {
        val newState = !_isMuted.value
        _isMuted.value = newState
        return newState
    }

    fun stop() {
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null

        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null

        _micAmplitude.value = 0f
        Log.i(TAG, "Audio recording stopped")
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        val numSamples = length / 2
        if (numSamples == 0) return 0f

        val shortBuf = ByteBuffer.wrap(buffer, 0, length).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until numSamples) {
            val sample = shortBuf.get(i).toDouble()
            sum += sample * sample
        }
        val rms = sqrt(sum / numSamples)
        // Normalize 0..32767 to 0f..1f with soft log scaling
        val norm = (rms / 8000.0).toFloat().coerceIn(0f, 1f)
        return norm
    }
}
