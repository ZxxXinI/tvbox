package com.tvbox.app.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.tvbox.app.domain.PlaybackAgentDecision
import com.tvbox.app.domain.PlaybackIssueType
import com.tvbox.app.ui.components.ErrorState
import kotlinx.coroutines.delay

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
    val playerFocusRequester = remember { FocusRequester() }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var speedPromptVisible by remember { mutableStateOf(false) }
    var speedPromptNonce by remember { mutableIntStateOf(0) }
    var autoAdvancedEpisodeUrl by remember { mutableStateOf<String?>(null) }
    var playbackNotice by remember { mutableStateOf<String?>(null) }
    var bufferingEpisodeUrl by remember { mutableStateOf<String?>(null) }
    var bufferingCheckNonce by remember { mutableIntStateOf(0) }
    var failedSourceIndexes by remember(movie.id, state.playerEpisodeIndex) {
        mutableStateOf(emptySet<Int>())
    }
    val latestState by rememberUpdatedState(state)
    val latestFailedSourceIndexes by rememberUpdatedState(failedSourceIndexes)

    val handlePlaybackIssue = { issueType: PlaybackIssueType, switchPrefix: String, finalPrefix: String, message: String ->
        val currentState = latestState
        val failedSources = latestFailedSourceIndexes + currentState.playerSourceIndex
        failedSourceIndexes = failedSources
        val decision = actions.switchToNextPlayableSource(
            blockedSourceIndexes = failedSources,
            issueType = issueType,
        )
        controlsVisible = true
        controlsInteraction++
        if (decision.switched) {
            bufferingEpisodeUrl = null
            playbackError = null
            playbackNotice = decision.toPlaybackNotice(switchPrefix)
        } else {
            playbackNotice = null
            playbackError = if (currentState.detailMovie?.playSources.orEmpty().size > 1) {
                "$finalPrefix：$message"
            } else {
                message
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val message = error.localizedMessage ?: "播放失败"
                handlePlaybackIssue(
                    PlaybackIssueType.Error,
                    "播放管家：当前线路播放失败",
                    "播放失败，播放管家已尝试所有可用线路",
                    message,
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val currentState = latestState
                val currentSource = currentState.detailMovie
                    ?.playSources
                    ?.getOrNull(currentState.playerSourceIndex)
                val currentEpisode = currentSource
                    ?.episodes
                    ?.getOrNull(currentState.playerEpisodeIndex)
                    ?: return
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        if (bufferingEpisodeUrl != currentEpisode.url) {
                            bufferingEpisodeUrl = currentEpisode.url
                            bufferingCheckNonce++
                        }
                    }
                    Player.STATE_READY -> {
                        bufferingEpisodeUrl = null
                        actions.recordPlaybackSuccess()
                    }
                    Player.STATE_ENDED -> {
                        bufferingEpisodeUrl = null
                        if (autoAdvancedEpisodeUrl == currentEpisode.url) return

                        autoAdvancedEpisodeUrl = currentEpisode.url
                        actions.savePlaybackProgress(
                            positionMs = player.currentPosition,
                            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
                        )
                        if (currentState.playerEpisodeIndex < currentSource.episodes.lastIndex) {
                            actions.playNextEpisode()
                        }
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            actions.savePlaybackProgress(
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            )
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(episode.url, reloadNonce) {
        playbackError = null
        autoAdvancedEpisodeUrl = null
        bufferingEpisodeUrl = null
        player.setMediaItem(MediaItem.fromUri(episode.url), state.playerStartPositionMs)
        player.prepare()
        player.setPlaybackSpeed(state.playerSpeed)
        player.play()
        actions.savePlaybackProgress(
            positionMs = state.playerStartPositionMs,
            durationMs = 0L,
        )
    }

    LaunchedEffect(playbackNotice) {
        if (playbackNotice == null) return@LaunchedEffect
        delay(2_000L)
        playbackNotice = null
    }

    LaunchedEffect(bufferingCheckNonce, bufferingEpisodeUrl) {
        val watchedEpisodeUrl = bufferingEpisodeUrl ?: return@LaunchedEffect
        delay(15_000L)
        val currentEpisodeUrl = latestState.detailMovie
            ?.playSources
            ?.getOrNull(latestState.playerSourceIndex)
            ?.episodes
            ?.getOrNull(latestState.playerEpisodeIndex)
            ?.url
        if (
            bufferingEpisodeUrl == watchedEpisodeUrl &&
            currentEpisodeUrl == watchedEpisodeUrl &&
            player.playbackState == Player.STATE_BUFFERING
        ) {
            handlePlaybackIssue(
                PlaybackIssueType.SlowBuffer,
                "播放管家：当前线路缓冲过久",
                "当前线路缓冲过久，播放管家已尝试所有可用线路",
                "当前线路缓冲过久",
            )
        }
    }

    LaunchedEffect(player, episode.url) {
        while (true) {
            delay(5_000L)
            actions.savePlaybackProgress(
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            )
        }
    }

    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            playerFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(state.playerSpeed) {
        player.setPlaybackSpeed(state.playerSpeed)
    }

    LaunchedEffect(controlsInteraction, playbackError) {
        if (playbackError == null) {
            controlsVisible = true
            delay(4_000L)
            controlsVisible = false
        } else {
            controlsVisible = true
        }
    }

    LaunchedEffect(speedPromptNonce) {
        if (speedPromptNonce == 0) return@LaunchedEffect
        speedPromptVisible = true
        delay(1_600L)
        speedPromptVisible = false
    }

    val showSpeedPromptAndCycle = {
        speedPromptNonce++
        actions.cyclePlaybackSpeed()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                controlsVisible = true
                controlsInteraction++
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    -> {
                        if (player.isPlaying) player.pause() else player.play()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> {
                        player.play()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        player.pause()
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
                    AndroidKeyEvent.KEYCODE_1,
                    AndroidKeyEvent.KEYCODE_NUMPAD_1,
                    -> {
                        actions.playPreviousEpisode()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_3,
                    AndroidKeyEvent.KEYCODE_NUMPAD_3,
                    -> {
                        actions.playNextEpisode()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MENU -> {
                        showSpeedPromptAndCycle()
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    isFocusable = false
                    isFocusableInTouchMode = false
                    useController = true
                    controllerAutoShow = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        if (controlsVisible || playbackError != null) {
            PlayerChrome(
                title = movie.name,
                sourceName = source.name,
                episodeTitle = episode.title,
                playbackError = playbackError,
                playbackNotice = playbackNotice,
                playbackSpeed = state.playerSpeed,
                canPrevious = state.playerEpisodeIndex > 0,
                canNext = state.playerEpisodeIndex < source.episodes.lastIndex,
                onPrevious = {
                    controlsInteraction++
                    actions.playPreviousEpisode()
                },
                onNext = {
                    controlsInteraction++
                    actions.playNextEpisode()
                },
                onRetry = {
                    controlsInteraction++
                    failedSourceIndexes = emptySet()
                    playbackNotice = null
                    reloadNonce++
                },
                onSpeed = {
                    controlsInteraction++
                    showSpeedPromptAndCycle()
                },
                onBack = actions::goBack,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (speedPromptVisible) {
            PlaybackSpeedPrompt(
                playbackSpeed = state.playerSpeed,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 148.dp),
            )
        }
    }
}

@Composable
private fun PlaybackSpeedPrompt(
    playbackSpeed: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xB8000000))
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "当前倍速 ${formatPlaybackSpeed(playbackSpeed)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun PlayerChrome(
    title: String,
    sourceName: String,
    episodeTitle: String,
    playbackError: String?,
    playbackNotice: String?,
    playbackSpeed: Float,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onSpeed: () -> Unit,
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
        if (playbackNotice != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = playbackNotice, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPrevious, enabled = canPrevious) {
                Text("上一集(1)")
            }
            Button(onClick = onNext, enabled = canNext) {
                Text("下一集(3)")
            }
            Button(onClick = onSpeed) {
                Text("倍速 ${formatPlaybackSpeed(playbackSpeed)}")
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

private fun formatPlaybackSpeed(speed: Float): String {
    val raw = speed.toString().trimEnd('0').trimEnd('.')
    return "${raw}x"
}

private fun PlaybackAgentDecision.toPlaybackNotice(prefix: String): String {
    val sourceName = nextSourceName
    return if (sourceName.isNullOrBlank()) {
        "$prefix，正在自动切换下一条线路"
    } else {
        "$prefix，已切换到 $sourceName"
    }
}
