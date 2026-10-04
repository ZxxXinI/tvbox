package com.tvbox.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tvbox.app.domain.PlaybackInterruptionPolicy

internal class PlaybackSession {
    private val policy = PlaybackInterruptionPolicy()
    private var revision by mutableIntStateOf(0)
    val canPlay: Boolean get() { revision; return policy.playbackAllowed }
    val interrupted: Boolean get() { revision; return policy.interrupted }
    fun foreground() { policy.enterForeground(); revision++ }
    fun background() { policy.leaveForeground(); revision++ }
    fun interrupt() { policy.interrupt(); revision++ }
    fun pause(player: Player) { policy.pause(); revision++; player.pause() }
    fun play(player: Player) {
        if (policy.requestPlay()) { revision++; player.play() }
    }
    fun preparePlayback(player: Player) { player.playWhenReady = canPlay }
}

@Composable
internal fun rememberPlaybackSession(): PlaybackSession = remember { PlaybackSession() }

@Composable
internal fun BindPlaybackSession(
    session: PlaybackSession,
    player: ExoPlayer?,
    onInterrupted: () -> Unit = {},
) {
    val owner = LocalLifecycleOwner.current
    val latestInterrupted by rememberUpdatedState(onInterrupted)
    val latestPlayer by rememberUpdatedState(player)
    DisposableEffect(session, owner) {
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session.background()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> session.foreground()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    session.background()
                    latestPlayer?.pause()
                    latestInterrupted()
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player, session) {
        player?.setAudioAttributes(AudioAttributes.DEFAULT, true)
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) interrupt()
            }
            override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                if (reason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) interrupt()
            }
            private fun interrupt() {
                if (!session.interrupted) {
                    session.interrupt()
                    player?.pause()
                    latestInterrupted()
                }
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player, session.canPlay) {
        player?.let { session.preparePlayback(it) }
    }
}
