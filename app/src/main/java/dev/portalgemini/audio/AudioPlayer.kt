package dev.portalgemini.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.sqrt

class AudioPlayer {
    companion object {
        private const val TAG = "AudioPlayer"
        const val SAMPLE_RATE = 24000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = (minBufferSize * 4).coerceAtLeast(8192)

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            startPlaybackLoop()
            Log.i(TAG, "AudioTrack initialized at ${SAMPLE_RATE}Hz")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
        }
    }

    private fun startPlaybackLoop() {
        playbackJob = scope.launch {
            while (isActive) {
                try {
                    val chunk = audioQueue.take()
                    if (chunk.isNotEmpty() && audioTrack != null) {
                        _isPlaying.value = true

                        // Calculate RMS amplitude for Gemini speaking visualizer
                        val amp = calculateRms(chunk)
                        _amplitude.value = amp

                        val written = audioTrack?.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING) ?: 0
                        if (written < 0) {
                            Log.w(TAG, "AudioTrack error write: $written")
                        }

                        if (audioQueue.isEmpty()) {
                            _isPlaying.value = false
                            _amplitude.value = 0f
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error in audio playback loop", e)
                }
            }
        }
    }

    fun queueAudio(pcmData: ByteArray) {
        if (pcmData.isNotEmpty()) {
            audioQueue.offer(pcmData)
        }
    }

    /**
     * Instantly interrupts playback when user speaks or Gemini Live sends `interrupted: true`.
     * Clears all pending buffers and flushes the AudioTrack buffer immediately.
     */
    fun interrupt() {
        audioQueue.clear()
        _amplitude.value = 0f
        _isPlaying.value = false

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
            Log.d(TAG, "Audio playback interrupted & flushed")
        } catch (e: Exception) {
            Log.w(TAG, "Error interrupting audioTrack: ${e.message}")
        }
    }

    fun release() {
        playbackJob?.cancel()
        playbackJob = null
        audioQueue.clear()

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audioTrack: ${e.message}")
        }
        audioTrack = null
        _isPlaying.value = false
        _amplitude.value = 0f
        Log.i(TAG, "AudioPlayer released")
    }

    private fun calculateRms(buffer: ByteArray): Float {
        var sum = 0.0
        val numSamples = buffer.size / 2
        if (numSamples == 0) return 0f

        val shortBuf = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until numSamples) {
            val sample = shortBuf.get(i).toDouble()
            sum += sample * sample
        }
        val rms = sqrt(sum / numSamples)
        return (rms / 7000.0).toFloat().coerceIn(0f, 1f)
    }
}
