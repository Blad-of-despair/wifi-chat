package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
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

class VoicePlayer(private val context: Context) {
    private val tag = "VoicePlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _currentPlayingUri = MutableStateFlow<String?>(null)
    val currentPlayingUri: StateFlow<String?> = _currentPlayingUri.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    fun play(uriOrPath: String) {
        if (_currentPlayingUri.value == uriOrPath && mediaPlayer != null) {
            if (_isPlaying.value) {
                pause()
            } else {
                resume()
            }
            return
        }

        stop()

        try {
            val mp = MediaPlayer().apply {
                if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
                    setDataSource(context, Uri.parse(uriOrPath))
                } else {
                    setDataSource(uriOrPath)
                }
                prepare()
                setSpeedInternal(this, _playbackSpeed.value)
                start()
            }
            mediaPlayer = mp
            _currentPlayingUri.value = uriOrPath
            _isPlaying.value = true
            _durationMs.value = mp.duration
            _currentPositionMs.value = 0

            mp.setOnCompletionListener {
                _isPlaying.value = false
                _currentPositionMs.value = 0
                progressJob?.cancel()
            }

            startProgressTracking()
        } catch (e: Exception) {
            Log.e(tag, "Failed to play audio", e)
            stop()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
        } catch (_: Exception) {}
    }

    fun resume() {
        try {
            mediaPlayer?.start()
            _isPlaying.value = true
            startProgressTracking()
        } catch (_: Exception) {}
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _currentPositionMs.value = positionMs
        } catch (_: Exception) {}
    }

    fun toggleSpeed() {
        val nextSpeed = when (_playbackSpeed.value) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _playbackSpeed.value = nextSpeed
        mediaPlayer?.let { setSpeedInternal(it, nextSpeed) }
    }

    private fun setSpeedInternal(player: MediaPlayer, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                player.playbackParams = PlaybackParams().apply { this.speed = speed }
            } catch (e: Exception) {
                Log.e(tag, "Error setting playback params", e)
            }
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let {
                    try {
                        _currentPositionMs.value = it.currentPosition
                    } catch (_: Exception) {}
                }
                delay(100L)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _isPlaying.value = false
        _currentPlayingUri.value = null
        _currentPositionMs.value = 0
        _durationMs.value = 0
    }
}
