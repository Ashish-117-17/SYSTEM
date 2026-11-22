package com.example.data.media

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ActiveMediaSource {
    NONE,
    MUSIC,
    YOUTUBE,
}

data class PlaybackSnapshot(
    val source: ActiveMediaSource = ActiveMediaSource.NONE,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val error: String? = null,
)

/**
 * Shared Media3 ExoPlayer engine managing both Music audio streaming and YouTube video playback
 * cleanly on a single hardware-backed player instance without overlapping audio streams.
 */
class PrismMediaController(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .build()

    private val _playback = MutableStateFlow(PlaybackSnapshot())
    val playback: StateFlow<PlaybackSnapshot> = _playback.asStateFlow()

    var onTrackEnded: ((ActiveMediaSource) -> Unit)? = null

    private var positionTicker: Job? = null

    init {
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playback.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) startTicker() else stopTicker()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val buffering = playbackState == Player.STATE_BUFFERING
                    val dur = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: _playback.value.durationMs
                    _playback.update { it.copy(isBuffering = buffering, durationMs = dur) }
                    if (playbackState == Player.STATE_ENDED) {
                        onTrackEnded?.invoke(_playback.value.source)
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    _playback.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            error = error.localizedMessage ?: "Playback failed",
                        )
                    }
                }
            },
        )
    }

    fun playStream(url: String, source: ActiveMediaSource, startPositionMs: Long = 0L) {
        _playback.update {
            it.copy(
                source = source,
                isBuffering = true,
                positionMs = startPositionMs,
                durationMs = 0L,
                error = null,
            )
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        if (startPositionMs > 0L) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        exoPlayer.seekTo(clamped)
        _playback.update { it.copy(positionMs = clamped) }
    }

    fun seekBy(deltaMs: Long) {
        val dur = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, dur)
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        exoPlayer.playbackParameters = PlaybackParameters(clamped)
        _playback.update { it.copy(playbackSpeed = clamped) }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        exoPlayer.volume = clamped
        _playback.update { it.copy(volume = clamped) }
    }

    private fun startTicker() {
        positionTicker?.cancel()
        positionTicker = scope.launch {
            while (isActive) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: _playback.value.durationMs
                _playback.update { it.copy(positionMs = pos, durationMs = dur) }
                delay(200L)
            }
        }
    }

    private fun stopTicker() {
        positionTicker?.cancel()
        positionTicker = null
    }

    fun release() {
        stopTicker()
        exoPlayer.release()
    }
}
