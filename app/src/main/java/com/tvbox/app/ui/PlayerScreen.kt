package com.tvbox.app.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tvbox.app.ui.components.ErrorState

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    val movie = state.detailMovie
    val source = movie?.playSources?.getOrNull(state.playerSourceIndex)
    val episode = source?.episodes?.getOrNull(state.playerEpisodeIndex)

    BackHandler {
        actions.goBack()
    }

    if (movie == null || source == null || episode == null) {
        ErrorState(message = "播放地址不存在", onRetry = actions::goBack)
        return
    }

    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var reloadNonce by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "播放失败"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(episode.url, reloadNonce) {
        playbackError = null
        player.setMediaItem(MediaItem.fromUri(episode.url))
        player.prepare()
        player.play()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    -> {
                        if (player.isPlaying) player.pause() else player.play()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND,
                    -> {
                        player.seekBack()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    -> {
                        player.seekForward()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> {
                        actions.playNextEpisode()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        actions.playPreviousEpisode()
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        PlayerChrome(
            title = movie.name,
            sourceName = source.name,
            episodeTitle = episode.title,
            playbackError = playbackError,
            canPrevious = state.playerEpisodeIndex > 0,
            canNext = state.playerEpisodeIndex < source.episodes.lastIndex,
            onPrevious = actions::playPreviousEpisode,
            onNext = actions::playNextEpisode,
            onRetry = { reloadNonce++ },
            onBack = actions::goBack,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PlayerChrome(
    title: String,
    sourceName: String,
    episodeTitle: String,
    playbackError: String?,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xB0000000))
            .padding(horizontal = 32.dp, vertical = 18.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$sourceName / $episodeTitle",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (playbackError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = playbackError, color = MaterialTheme.colorScheme.tertiary)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPrevious, enabled = canPrevious) {
                Text("上一集")
            }
            Button(onClick = onNext, enabled = canNext) {
                Text("下一集")
            }
            if (playbackError != null) {
                Button(onClick = onRetry) {
                    Text("重试播放")
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onBack) {
                Text("返回详情")
            }
        }
    }
}

