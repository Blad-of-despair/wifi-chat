package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class VoiceRecorder(private val context: Context) {
    private val tag = "VoiceRecorder"
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordStartTime: Long = 0L
    private var amplitudeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes: StateFlow<List<Float>> = _amplitudes.asStateFlow()

    fun startRecording(): Boolean {
        if (_isRecording.value) return false
        return try {
            val audioDir = File(context.cacheDir, "voice_notes").apply { mkdirs() }
            val outputFile = File(audioDir, "voice_${System.currentTimeMillis()}.m4a")
            currentOutputFile = outputFile

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            recordStartTime = System.currentTimeMillis()
            _isRecording.value = true
            _amplitudes.value = emptyList()

            amplitudeJob = scope.launch {
                val ampList = mutableListOf<Float>()
                while (isActive && _isRecording.value) {
                    _recordingDurationMs.value = System.currentTimeMillis() - recordStartTime
                    val maxAmp = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
                    val normalized = (maxAmp / 32767f).coerceIn(0.05f, 1.0f)
                    ampList.add(normalized)
                    if (ampList.size > 40) ampList.removeAt(0)
                    _amplitudes.value = ampList.toList()
                    delay(100L)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start recording", e)
            stopRecording(cancel = true)
            false
        }
    }

    data class RecordResult(
        val file: File,
        val durationMs: Long
    )

    fun stopRecording(cancel: Boolean = false): RecordResult? {
        amplitudeJob?.cancel()
        amplitudeJob = null
        _isRecording.value = false

        val duration = System.currentTimeMillis() - recordStartTime
        val file = currentOutputFile

        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error stopping recorder", e)
        }
        recorder = null
        currentOutputFile = null

        if (cancel || duration < 500L) {
            file?.delete()
            return null
        }

        return if (file != null && file.exists()) {
            RecordResult(file, duration)
        } else {
            null
        }
    }
}
