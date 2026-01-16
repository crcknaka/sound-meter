package com.soundmeter.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.log10
import kotlin.math.sqrt

class SoundMeter {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    private val _decibelLevel = MutableStateFlow(0.0)
    val decibelLevel: StateFlow<Double> = _decibelLevel

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    // Calibration offset in dB
    var calibrationOffset: Double = 0.0

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val REFERENCE_AMPLITUDE = 1.0
        private const val MIN_DB = 0.0
        private const val MAX_DB = 120.0
    }

    private val bufferSize: Int by lazy {
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    }

    fun start(scope: CoroutineScope) {
        if (isRecording) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _decibelLevel.value = 0.0
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            _isRunning.value = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(bufferSize)

                while (isActive && isRecording) {
                    val readResult = audioRecord?.read(buffer, 0, bufferSize) ?: 0

                    if (readResult > 0) {
                        val amplitude = calculateRMS(buffer, readResult)
                        val db = amplitudeToDecibels(amplitude)
                        _decibelLevel.value = db.coerceIn(MIN_DB, MAX_DB)
                    }

                    delay(100)
                }
            }
        } catch (e: SecurityException) {
            _decibelLevel.value = 0.0
            _isRunning.value = false
        }
    }

    fun stop() {
        isRecording = false
        _isRunning.value = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        audioRecord = null
    }

    private fun calculateRMS(buffer: ShortArray, readSize: Int): Double {
        var sum = 0.0
        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / readSize)
    }

    private fun amplitudeToDecibels(amplitude: Double): Double {
        if (amplitude <= 0) return MIN_DB

        // Normalize to 0-1 range (16-bit audio max is 32767)
        val normalizedAmplitude = amplitude / 32767.0

        // Convert to decibels with base offset of 90 to shift range to ~30-120 dB
        // Then apply user calibration offset
        val db = 20 * log10(normalizedAmplitude) + 90 + calibrationOffset

        return db.coerceIn(MIN_DB, MAX_DB)
    }

    fun release() {
        stop()
    }
}
