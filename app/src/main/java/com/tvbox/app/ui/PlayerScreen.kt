package com.tvbox.app.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
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
import com.tvbox.app.domain.PlaybackAttemptTracker
import com.tvbox.app.domain.PlaybackBufferDecision
import com.tvbox.app.domain.PlaybackBufferMonitor
import com.tvbox.app.domain.PlaybackIssueType
import com.tvbox.app.domain.SlowBufferReason
import com.tvbox.app.ui.components.ErrorState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToLong

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
    var speedPromptOverride by remember { mutableStateOf<Float?>(null) }
    var seekGesturePrompt by remember { mutableStateOf<String?>(null) }
    var seekGesturePromptNonce by remember { mutableIntStateOf(0) }
    var autoAdvancedEpisodeUrl by remember { mutableStateOf<String?>(null) }
    var playbackNotice by remember { mutableStateOf<String?>(null) }
    val bufferMonitor = remember { PlaybackBufferMonitor() }
    val attemptTracker = remember { PlaybackAttemptTracker() }
    var bufferingPlaybackKey by remember { mutableStateOf<String?>(null) }
    var bufferingCheckNonce by remember { mutableIntStateOf(0) }
    var failedSourceIndexes by remember(movie.id, state.playerEpisodeIndex) {
        mutableStateOf(emptySet<Int>())
    }
    val latestState by rememberUpdatedState(state)
    val latestControlsVisible by rememberUpdatedState(controlsVisible)
    val latestPlaybackError by rememberUpdatedState(playbackError)
    val latestFailedSourceIndexes by rememberUpdatedState(failedSourceIndexes)

    val handlePlaybackIssue = { issueType: PlaybackIssueType, switchPrefix: String, finalPrefix: String, message: String ->
        val currentState = latestState
        val failedSources = latestFailedSourceIndexes + currentState.playerSourceIndex
        failedSourceIndexes = failedSources
        val issueToRecord = if (attemptTracker.shouldRecordIssue(currentState.currentPlaybackKey(), issueType)) {
            issueType
        } else {
            null
        }
        val decision = actions.switchToNextPlayableSource(
            blockedSourceIndexes = failedSources,
            issueType = issueToRecord,
            autoTriggered = true,
        )
        controlsVisible = true
        controlsInteraction++
        if (decision.switched) {
            bufferingPlaybackKey = null
            playbackError = null
            playbackNotice = decision.toPlaybackNotice(switchPrefix)
        } else {
            playbackNotice = null
            playbackError = if (!currentState.appSettings.playbackAgentAutoSwitchEnabled) {
                "$message（播放管家自动换线已关闭）"
            } else if (currentState.detailMovie?.playSources.orEmpty().size > 1) {
                "$finalPrefix：$message"
            } else {
                message
            }
        }
    }

    val handleBufferDecision = { decision: PlaybackBufferDecision? ->
        if (decision != null) {
            val messages = decision.toPlaybackIssueMessages()
            handlePlaybackIssue(
                PlaybackIssueType.SlowBuffer,
                messages.switchPrefix,
                messages.finalPrefix,
                messages.message,
            )
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

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    bufferMonitor.onPaused()
                    bufferingPlaybackKey = null
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    bufferMonitor.onSeekStarted(System.currentTimeMillis())
                    bufferingPlaybackKey = null
                }
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
                        val decision = bufferMonitor.onBuffering(
                            playWhenReady = player.playWhenReady,
                            nowMs = System.currentTimeMillis(),
                        )
                        val playbackKey = currentState.currentPlaybackKey()
                        if (player.playWhenReady && playbackKey != null && bufferingPlaybackKey != playbackKey) {
                            bufferingPlaybackKey = playbackKey
                            bufferingCheckNonce++
                        }
                        handleBufferDecision(decision)
                    }
                    Player.STATE_READY -> {
                        bufferingPlaybackKey = null
                        val result = bufferMonitor.onReady(
                            playWhenReady = player.playWhenReady,
                            nowMs = System.currentTimeMillis(),
                        )
                        if (result.decision != null) {
                            handleBufferDecision(result.decision)
                        } else if (
                            result.shouldRecordPlaybackSuccess &&
                            attemptTracker.shouldRecordSuccess(currentState.currentPlaybackKey())
                        ) {
                            actions.recordPlaybackSuccess()
                        }
                    }
                    Player.STATE_ENDED -> {
                        bufferingPlaybackKey = null
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

    LaunchedEffect(state.playerSourceIndex, state.playerEpisodeIndex, episode.url, reloadNonce) {
        playbackError = null
        autoAdvancedEpisodeUrl = null
        bufferingPlaybackKey = null
        bufferMonitor.onMediaChanged()
        attemptTracker.onPlaybackChanged(state.currentPlaybackKey())
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

    LaunchedEffect(bufferingCheckNonce, bufferingPlaybackKey) {
        val watchedPlaybackKey = bufferingPlaybackKey ?: return@LaunchedEffect
        delay(PlaybackBufferMonitor.DEFAULT_CONTINUOUS_BUFFER_THRESHOLD_MS)
        if (
            bufferingPlaybackKey == watchedPlaybackKey &&
            latestState.currentPlaybackKey() == watchedPlaybackKey &&
            player.playbackState == Player.STATE_BUFFERING
        ) {
            val decision = bufferMonitor.onBuffering(
                playWhenReady = player.playWhenReady,
                nowMs = System.currentTimeMillis(),
            )
            handleBufferDecision(decision)
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
        speedPromptOverride = null
    }

    LaunchedEffect(seekGesturePromptNonce) {
        if (seekGesturePromptNonce == 0) return@LaunchedEffect
        delay(1_000L)
        seekGesturePrompt = null
    }

    val showSpeedPromptAndCycle = {
        speedPromptOverride = null
        speedPromptNonce++
        actions.cyclePlaybackSpeed()
    }

    val showLongPressSpeedPrompt = {
        speedPromptOverride = LONG_PRESS_PLAYBACK_SPEED
        speedPromptNonce++
    }
    val touchHandler = remember { Handler(Looper.getMainLooper()) }
    val touchGesture = remember { PlayerTouchGestureState() }
    val cancelPendingSingleTap = {
        touchGesture.singleTapRunnable?.let(touchHandler::removeCallbacks)
        touchGesture.singleTapRunnable = null
    }
    val cancelLongPressSpeed = {
        touchGesture.longPressRunnable?.let(touchHandler::removeCallbacks)
        touchGesture.longPressRunnable = null
        if (touchGesture.longPressActive) {
            touchGesture.longPressActive = false
            speedPromptVisible = false
            speedPromptOverride = null
            player.setPlaybackSpeed(latestState.playerSpeed)
        }
    }
    val scheduleLongPressSpeed = {
        touchGesture.longPressRunnable?.let(touchHandler::removeCallbacks)
        touchGesture.longPressActive = false
        val runnable = Runnable {
            touchGesture.longPressActive = true
            player.setPlaybackSpeed(LONG_PRESS_PLAYBACK_SPEED)
            showLongPressSpeedPrompt()
        }
        touchGesture.longPressRunnable = runnable
        touchHandler.postDelayed(
            runnable,
            ViewConfiguration.getLongPressTimeout().toLong(),
        )
    }
    val showControlsTemporarily = {
        controlsVisible = true
        controlsInteraction++
    }
    val toggleControlsByTap = {
        if (latestPlaybackError == null) {
            if (latestControlsVisible) {
                controlsVisible = false
            } else {
                showControlsTemporarily()
            }
        }
    }
    val togglePlaybackByGesture = {
        cancelLongPressSpeed()
        cancelPendingSingleTap()
        controlsVisible = true
        controlsInteraction++
        if (player.isPlaying) {
            bufferMonitor.onPaused()
            player.pause()
            seekGesturePrompt = "暂停"
        } else {
            player.play()
            seekGesturePrompt = "播放"
        }
        seekGesturePromptNonce++
    }
    val seekByGesture = { deltaMs: Long, label: String ->
        cancelLongPressSpeed()
        cancelPendingSingleTap()
        bufferMonitor.onSeekStarted(System.currentTimeMillis())
        val targetPosition = player.seekByOffset(deltaMs)
        seekGesturePrompt = "$label  ${formatPlaybackPosition(targetPosition)}"
        seekGesturePromptNonce++
        actions.savePlaybackProgress(
            positionMs = targetPosition,
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
        )
    }

    DisposableEffect(touchHandler, player) {
        onDispose {
            touchGesture.longPressRunnable?.let(touchHandler::removeCallbacks)
            touchGesture.singleTapRunnable?.let(touchHandler::removeCallbacks)
            touchGesture.longPressRunnable = null
            touchGesture.singleTapRunnable = null
        }
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
                        if (player.isPlaying) {
                            bufferMonitor.onPaused()
                            player.pause()
                        } else {
                            player.play()
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> {
                        player.play()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        bufferMonitor.onPaused()
                        player.pause()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND,
                    -> {
                        bufferMonitor.onSeekStarted(System.currentTimeMillis())
                        player.seekBack()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    -> {
                        bufferMonitor.onSeekStarted(System.currentTimeMillis())
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
                val touchSlop = ViewConfiguration.get(viewContext).scaledTouchSlop
                val doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong()
                PlayerView(viewContext).apply {
                    this.player = player
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    isFocusable = false
                    isFocusableInTouchMode = false
                    useController = true
                    controllerAutoShow = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                touchGesture.downX = event.x
                                touchGesture.downY = event.y
                                touchGesture.downPositionMs = player.currentPosition.coerceAtLeast(0L)
                                touchGesture.seekTargetMs = touchGesture.downPositionMs
                                touchGesture.seeking = false
                                scheduleLongPressSpeed()
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val totalDx = event.x - touchGesture.downX
                                val totalDy = event.y - touchGesture.downY
                                val durationMs = player.duration.takeIf { it > 0L }
                                if (
                                    durationMs != null &&
                                    !touchGesture.longPressActive &&
                                    (touchGesture.seeking || (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy)))
                                ) {
                                    cancelLongPressSpeed()
                                    cancelPendingSingleTap()
                                    if (!touchGesture.seeking) {
                                        touchGesture.seeking = true
                                        showControlsTemporarily()
                                    }
                                    val targetPosition = calculateDragSeekPosition(
                                        startPositionMs = touchGesture.downPositionMs,
                                        dragPx = totalDx,
                                        viewWidthPx = width,
                                        durationMs = durationMs,
                                    )
                                    touchGesture.seekTargetMs = targetPosition
                                    seekGesturePrompt = "进度 ${formatPlaybackPosition(targetPosition)} / ${formatPlaybackPosition(durationMs)}"
                                }
                            }
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL,
                            -> {
                                val wasLongPressActive = touchGesture.longPressActive
                                cancelLongPressSpeed()
                                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                                    touchGesture.seeking = false
                                    cancelPendingSingleTap()
                                    return@setOnTouchListener true
                                }
                                if (touchGesture.seeking) {
                                    touchGesture.seeking = false
                                    bufferMonitor.onSeekStarted(System.currentTimeMillis())
                                    player.seekTo(touchGesture.seekTargetMs)
                                    seekGesturePromptNonce++
                                    actions.savePlaybackProgress(
                                        positionMs = touchGesture.seekTargetMs,
                                        durationMs = player.duration.takeIf { it > 0L } ?: 0L,
                                    )
                                    return@setOnTouchListener true
                                }
                                if (wasLongPressActive) {
                                    cancelPendingSingleTap()
                                    return@setOnTouchListener true
                                }
                                val distanceFromLastTap = squaredDistance(
                                    event.x,
                                    event.y,
                                    touchGesture.lastTapX,
                                    touchGesture.lastTapY,
                                )
                                val isDoubleTap = event.eventTime - touchGesture.lastTapUpTimeMs <= doubleTapTimeoutMs &&
                                    distanceFromLastTap <= touchSlop * touchSlop
                                if (isDoubleTap) {
                                    touchGesture.lastTapUpTimeMs = 0L
                                    when {
                                        event.x < width / 3f -> seekByGesture(-DOUBLE_TAP_SEEK_MS, "快退 10 秒")
                                        event.x > width * 2f / 3f -> seekByGesture(DOUBLE_TAP_SEEK_MS, "快进 10 秒")
                                        else -> togglePlaybackByGesture()
                                    }
                                } else {
                                    touchGesture.lastTapUpTimeMs = event.eventTime
                                    touchGesture.lastTapX = event.x
                                    touchGesture.lastTapY = event.y
                                    cancelPendingSingleTap()
                                    val singleTapRunnable = Runnable {
                                        touchGesture.singleTapRunnable = null
                                        toggleControlsByTap()
                                    }
                                    touchGesture.singleTapRunnable = singleTapRunnable
                                    touchHandler.postDelayed(singleTapRunnable, doubleTapTimeoutMs)
                                }
                            }
                        }
                        true
                    }
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
                canSwitchLine = movie.playSources.size > 1,
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
                    bufferingPlaybackKey = null
                    bufferMonitor.onMediaChanged()
                    attemptTracker.reset()
                    reloadNonce++
                },
                onSpeed = {
                    controlsInteraction++
                    showSpeedPromptAndCycle()
                },
                onSwitchLine = {
                    controlsInteraction++
                    val currentState = latestState
                    val decision = actions.switchToNextPlayableSource(
                        blockedSourceIndexes = setOf(currentState.playerSourceIndex),
                        issueType = null,
                        autoTriggered = false,
                    )
                    controlsVisible = true
                    if (decision.switched) {
                        bufferingPlaybackKey = null
                        bufferMonitor.onMediaChanged()
                        attemptTracker.reset()
                        playbackError = null
                        playbackNotice = decision.toPlaybackNotice("播放管家：手动换线")
                    } else {
                        playbackNotice = "没有其它可用线路"
                    }
                },
                onBack = actions::goBack,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (speedPromptVisible) {
            PlaybackSpeedPrompt(
                playbackSpeed = speedPromptOverride ?: state.playerSpeed,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 148.dp),
            )
        }
        if (seekGesturePrompt != null) {
            GesturePrompt(
                text = seekGesturePrompt.orEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 212.dp),
            )
        }
    }
}

@Composable
private fun GesturePrompt(
    text: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xB8000000))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
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
    canSwitchLine: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    onSpeed: () -> Unit,
    onSwitchLine: () -> Unit,
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
            Button(onClick = onSwitchLine, enabled = canSwitchLine) {
                Text("手动换线")
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

private data class PlaybackIssueMessages(
    val switchPrefix: String,
    val finalPrefix: String,
    val message: String,
)

private fun TvBoxUiState.currentPlaybackKey(): String? {
    val movie = detailMovie ?: return null
    val source = movie.playSources.getOrNull(playerSourceIndex) ?: return null
    val episode = source.episodes.getOrNull(playerEpisodeIndex) ?: return null
    return listOf(movie.id, playerSourceIndex, playerEpisodeIndex, episode.url).joinToString("|")
}

private fun PlaybackBufferDecision.toPlaybackIssueMessages(): PlaybackIssueMessages {
    return when (reason) {
        SlowBufferReason.StartupTooLong,
        SlowBufferReason.ContinuousBufferTooLong,
        -> PlaybackIssueMessages(
            switchPrefix = "播放管家：当前线路缓冲超过 5 秒",
            finalPrefix = "当前线路缓冲超过 5 秒，播放管家已尝试所有可用线路",
            message = "当前线路缓冲超过 5 秒",
        )
        SlowBufferReason.FrequentBuffering -> PlaybackIssueMessages(
            switchPrefix = "播放管家：当前线路频繁卡顿",
            finalPrefix = "当前线路频繁卡顿，播放管家已尝试所有可用线路",
            message = "当前线路频繁卡顿",
        )
        SlowBufferReason.CumulativeBufferTooLong -> PlaybackIssueMessages(
            switchPrefix = "播放管家：当前线路累计缓冲过久",
            finalPrefix = "当前线路累计缓冲过久，播放管家已尝试所有可用线路",
            message = "当前线路累计缓冲过久",
        )
    }
}

private fun formatPlaybackSpeed(speed: Float): String {
    val raw = speed.toString().trimEnd('0').trimEnd('.')
    return "${raw}x"
}

private fun formatPlaybackPosition(positionMs: Long): String {
    val totalSeconds = (positionMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
}

private fun ExoPlayer.seekByOffset(deltaMs: Long): Long {
    val durationMs = duration.takeIf { it > 0L }
    val targetPosition = (currentPosition + deltaMs)
        .coerceAtLeast(0L)
        .let { position -> durationMs?.let { position.coerceAtMost(it) } ?: position }
    seekTo(targetPosition)
    return targetPosition
}

private fun calculateDragSeekPosition(
    startPositionMs: Long,
    dragPx: Float,
    viewWidthPx: Int,
    durationMs: Long,
): Long {
    if (viewWidthPx <= 0) return startPositionMs.coerceIn(0L, durationMs)
    val offsetMs = (dragPx / viewWidthPx.toFloat() * durationMs).roundToLong()
    return (startPositionMs + offsetMs).coerceIn(0L, durationMs)
}

private fun squaredDistance(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return dx * dx + dy * dy
}

private class PlayerTouchGestureState {
    var downX: Float = 0f
    var downY: Float = 0f
    var downPositionMs: Long = 0L
    var seekTargetMs: Long = 0L
    var seeking: Boolean = false
    var longPressActive: Boolean = false
    var longPressRunnable: Runnable? = null
    var singleTapRunnable: Runnable? = null
    var lastTapUpTimeMs: Long = 0L
    var lastTapX: Float = 0f
    var lastTapY: Float = 0f
}

private const val DOUBLE_TAP_SEEK_MS = 10_000L
private const val LONG_PRESS_PLAYBACK_SPEED = 2f

private fun PlaybackAgentDecision.toPlaybackNotice(prefix: String): String {
    val sourceName = nextSourceName
    return if (sourceName.isNullOrBlank()) {
        "$prefix，正在自动切换下一条线路"
    } else {
        "$prefix，已切换到 $sourceName"
    }
}
