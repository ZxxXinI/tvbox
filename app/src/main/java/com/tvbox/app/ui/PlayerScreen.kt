package com.tvbox.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3UiR
import com.tvbox.app.domain.PlaybackAgentDecision
import com.tvbox.app.domain.PlaybackAttemptTracker
import com.tvbox.app.domain.PlaybackBufferDecision
import com.tvbox.app.domain.PlaybackBufferMonitor
import com.tvbox.app.domain.PlaybackIssueType
import com.tvbox.app.domain.SlowBufferReason
import com.tvbox.app.ui.components.ErrorState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
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
    val activity = remember(context) { context.findActivity() }
    val isTelevision = remember(context) { context.isTelevision() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val initialScreenBrightness = remember(activity) {
        activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    var nativePlayerView by remember { mutableStateOf<PlayerView?>(null) }
    var videoDisplayMode by remember { mutableStateOf(VideoDisplayMode.Unknown) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var seekGesturePrompt by remember { mutableStateOf<String?>(null) }
    var seekGesturePromptNonce by remember { mutableIntStateOf(0) }
    var playbackNotice by remember { mutableStateOf<String?>(null) }
    val bufferMonitor = remember { PlaybackBufferMonitor() }
    val attemptTracker = remember { PlaybackAttemptTracker() }
    var bufferingPlaybackKey by remember { mutableStateOf<String?>(null) }
    var bufferingCheckNonce by remember { mutableIntStateOf(0) }
    var failedSourceIndexes by remember(movie.id, state.playerEpisodeIndex) {
        mutableStateOf(emptySet<Int>())
    }
    val latestState by rememberUpdatedState(state)
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
        nativePlayerView?.showController()
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

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val episodeIndex = player.currentMediaItemIndex
                val episodes = latestState.detailMovie
                    ?.playSources
                    ?.getOrNull(latestState.playerSourceIndex)
                    ?.episodes
                    .orEmpty()
                if (episodeIndex in episodes.indices && episodeIndex != latestState.playerEpisodeIndex) {
                    bufferingPlaybackKey = null
                    bufferMonitor.onMediaChanged()
                    actions.syncPlayerEpisode(episodeIndex)
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                actions.updatePlaybackSpeed(playbackParameters.speed)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val displayMode = videoSize.toVideoDisplayMode()
                if (displayMode == VideoDisplayMode.Unknown || displayMode == videoDisplayMode) return

                videoDisplayMode = displayMode
                if (isTelevision) return

                val requestedOrientation = displayMode.requestedOrientation ?: return
                val currentActivity = activity ?: return
                if (currentActivity.requestedOrientation == requestedOrientation) return

                actions.savePlaybackProgress(
                    positionMs = player.currentPosition,
                    durationMs = player.duration.takeIf { it > 0L } ?: 0L,
                )
                currentActivity.requestedOrientation = requestedOrientation
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
                        actions.savePlaybackProgress(
                            positionMs = player.currentPosition,
                            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
                        )
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

    LaunchedEffect(state.playerSourceIndex, source.episodes) {
        playbackError = null
        bufferingPlaybackKey = null
        bufferMonitor.onMediaChanged()
        attemptTracker.onPlaybackChanged(state.currentPlaybackKey())
        player.setMediaItems(
            source.episodes.map { item -> MediaItem.fromUri(item.url) },
            state.playerEpisodeIndex,
            state.playerStartPositionMs,
        )
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

    LaunchedEffect(player) {
        while (true) {
            delay(5_000L)
            actions.savePlaybackProgress(
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            )
        }
    }

    LaunchedEffect(state.playerSpeed) {
        player.setPlaybackSpeed(state.playerSpeed)
    }

    LaunchedEffect(seekGesturePromptNonce) {
        if (seekGesturePromptNonce == 0) return@LaunchedEffect
        delay(1_000L)
        seekGesturePrompt = null
    }

    val showLongPressSpeedPrompt = {
        seekGesturePrompt = "2x 倍速播放"
        seekGesturePromptNonce++
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
            seekGesturePrompt = null
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
        nativePlayerView?.showController()
    }
    val toggleControlsByTap = {
        if (latestPlaybackError == null) {
            nativePlayerView?.let { view ->
                if (view.isControllerFullyVisible) {
                    view.hideController()
                } else {
                    view.showController()
                }
            }
        }
    }
    val togglePlaybackByGesture = {
        cancelLongPressSpeed()
        cancelPendingSingleTap()
        nativePlayerView?.showController()
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

    DisposableEffect(touchHandler, player, activity, initialScreenBrightness, isTelevision) {
        onDispose {
            touchGesture.longPressRunnable?.let(touchHandler::removeCallbacks)
            touchGesture.singleTapRunnable?.let(touchHandler::removeCallbacks)
            touchGesture.longPressRunnable = null
            touchGesture.singleTapRunnable = null
            activity?.window?.restoreScreenBrightness(initialScreenBrightness)
            if (!isTelevision) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { viewContext ->
                val touchSlop = ViewConfiguration.get(viewContext).scaledTouchSlop
                val doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong()
                val controllerTouchAreaPx = (MEDIA3_CONTROLLER_TOUCH_AREA_DP * viewContext.resources.displayMetrics.density).toInt()
                PlayerView(viewContext).apply {
                    this.player = player
                    nativePlayerView = this
                    isFocusable = true
                    isFocusableInTouchMode = true
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = PLAYER_CONTROLLER_SHOW_TIMEOUT_MS
                    controllerHideOnTouch = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    val centerControls = findViewById<View>(Media3UiR.id.exo_center_controls)
                    setOnKeyListener { _, keyCode, keyEvent ->
                        if (keyEvent.action != AndroidKeyEvent.ACTION_UP) return@setOnKeyListener false
                        showControlsTemporarily()
                        when (keyCode) {
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
                                if (isControllerFullyVisible) return@setOnKeyListener false
                                bufferMonitor.onSeekStarted(System.currentTimeMillis())
                                player.seekBack()
                                true
                            }
                            AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                            AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                            -> {
                                if (isControllerFullyVisible) return@setOnKeyListener false
                                bufferMonitor.onSeekStarted(System.currentTimeMillis())
                                player.seekForward()
                                true
                            }
                            AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                            AndroidKeyEvent.KEYCODE_3,
                            AndroidKeyEvent.KEYCODE_NUMPAD_3,
                            -> {
                                player.seekToNextMediaItem()
                                true
                            }
                            AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                            AndroidKeyEvent.KEYCODE_1,
                            AndroidKeyEvent.KEYCODE_NUMPAD_1,
                            -> {
                                player.seekToPreviousMediaItem()
                                true
                            }
                            else -> false
                        }
                    }
                    val gestureTouchListener = View.OnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                touchGesture.isTouchingNativeController =
                                    isControllerFullyVisible && (
                                        event.isTouching(centerControls) ||
                                            event.y >= height - controllerTouchAreaPx
                                        )
                                if (touchGesture.isTouchingNativeController) {
                                    return@OnTouchListener false
                                }
                                touchGesture.downX = event.x
                                touchGesture.downY = event.y
                                touchGesture.downPositionMs = player.currentPosition.coerceAtLeast(0L)
                                touchGesture.seekTargetMs = touchGesture.downPositionMs
                                touchGesture.seeking = false
                                touchGesture.swipeMode = PlayerSwipeMode.None
                                touchGesture.startBrightness = activity?.window
                                    ?.currentScreenBrightness(context)
                                    ?: DEFAULT_SCREEN_BRIGHTNESS
                                touchGesture.startVolume = audioManager
                                    ?.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    ?: 0
                                touchGesture.maxVolume = audioManager
                                    ?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    ?: 0
                                scheduleLongPressSpeed()
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (touchGesture.isTouchingNativeController) {
                                    return@OnTouchListener false
                                }
                                val totalDx = event.x - touchGesture.downX
                                val totalDy = event.y - touchGesture.downY
                                val durationMs = player.duration.takeIf { it > 0L }
                                if (touchGesture.longPressActive) return@OnTouchListener true

                                if (touchGesture.swipeMode == PlayerSwipeMode.None) {
                                    when {
                                        abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy) && durationMs != null -> {
                                            touchGesture.swipeMode = PlayerSwipeMode.Seek
                                        }
                                        abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx) -> {
                                            touchGesture.swipeMode = if (touchGesture.downX < width / 2f) {
                                                PlayerSwipeMode.Brightness
                                            } else {
                                                PlayerSwipeMode.Volume
                                            }
                                        }
                                    }
                                }

                                if (touchGesture.swipeMode == PlayerSwipeMode.Seek && durationMs != null) {
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
                                    seekGesturePromptNonce++
                                } else if (touchGesture.swipeMode == PlayerSwipeMode.Brightness) {
                                    cancelLongPressSpeed()
                                    cancelPendingSingleTap()
                                    val brightness = (touchGesture.startBrightness - totalDy / height.toFloat())
                                        .coerceIn(MIN_SCREEN_BRIGHTNESS, 1f)
                                    activity?.window?.setScreenBrightness(brightness)
                                    seekGesturePrompt = "亮度 ${(brightness * 100).roundToInt()}%"
                                    seekGesturePromptNonce++
                                } else if (touchGesture.swipeMode == PlayerSwipeMode.Volume && touchGesture.maxVolume > 0) {
                                    cancelLongPressSpeed()
                                    cancelPendingSingleTap()
                                    val volume = (
                                        touchGesture.startVolume -
                                            (totalDy / height.toFloat() * touchGesture.maxVolume).roundToInt()
                                        )
                                        .coerceIn(0, touchGesture.maxVolume)
                                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                                    seekGesturePrompt = "音量 $volume/${touchGesture.maxVolume}"
                                    seekGesturePromptNonce++
                                }
                            }
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL,
                            -> {
                                if (touchGesture.isTouchingNativeController) {
                                    touchGesture.isTouchingNativeController = false
                                    return@OnTouchListener false
                                }
                                val swipeMode = touchGesture.swipeMode
                                touchGesture.swipeMode = PlayerSwipeMode.None
                                val wasLongPressActive = touchGesture.longPressActive
                                cancelLongPressSpeed()
                                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                                    touchGesture.seeking = false
                                    cancelPendingSingleTap()
                                    return@OnTouchListener true
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
                                    return@OnTouchListener true
                                }
                                if (swipeMode == PlayerSwipeMode.Brightness || swipeMode == PlayerSwipeMode.Volume) {
                                    cancelPendingSingleTap()
                                    return@OnTouchListener true
                                }
                                if (wasLongPressActive) {
                                    cancelPendingSingleTap()
                                    return@OnTouchListener true
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
                    setOnTouchListener(gestureTouchListener)
                    post { requestFocus() }
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        val playbackStatus = playbackError ?: playbackNotice
        if (playbackStatus != null) {
            GesturePrompt(
                text = playbackStatus,
                textColor = if (playbackError != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 112.dp),
            )
        }
        if (seekGesturePrompt != null) {
            GesturePrompt(
                text = seekGesturePrompt.orEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 164.dp),
            )
        }
    }
}

@Composable
private fun GesturePrompt(
    text: String,
    textColor: Color = MaterialTheme.colorScheme.primary,
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
            color = textColor,
        )
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

private fun MotionEvent.isTouching(view: View?): Boolean {
    val target = view ?: return false
    val location = IntArray(2)
    target.getLocationOnScreen(location)
    return rawX >= location[0] && rawX <= location[0] + target.width &&
        rawY >= location[1] && rawY <= location[1] + target.height
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.isTelevision(): Boolean {
    val deviceType = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return deviceType == Configuration.UI_MODE_TYPE_TELEVISION
}

private fun VideoSize.toVideoDisplayMode(): VideoDisplayMode {
    if (width <= 0 || height <= 0) return VideoDisplayMode.Unknown
    val aspectRatio = width.toFloat() * pixelWidthHeightRatio / height
    return when {
        aspectRatio <= PORTRAIT_VIDEO_ASPECT_RATIO_MAX -> VideoDisplayMode.Portrait
        aspectRatio >= LANDSCAPE_VIDEO_ASPECT_RATIO_MIN -> VideoDisplayMode.Landscape
        else -> VideoDisplayMode.Unknown
    }
}

private fun Window.currentScreenBrightness(context: Context): Float {
    val current = attributes.screenBrightness
    if (current >= 0f) return current.coerceIn(MIN_SCREEN_BRIGHTNESS, 1f)
    val systemBrightness = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(DEFAULT_SYSTEM_BRIGHTNESS)
    return (systemBrightness / MAX_SYSTEM_BRIGHTNESS).coerceIn(MIN_SCREEN_BRIGHTNESS, 1f)
}

private fun Window.setScreenBrightness(brightness: Float) {
    attributes = attributes.apply {
        screenBrightness = brightness.coerceIn(MIN_SCREEN_BRIGHTNESS, 1f)
    }
}

private fun Window.restoreScreenBrightness(brightness: Float) {
    attributes = attributes.apply {
        screenBrightness = brightness
    }
}

private enum class PlayerSwipeMode {
    None,
    Seek,
    Brightness,
    Volume,
}

private enum class VideoDisplayMode(
    val requestedOrientation: Int? = null,
) {
    Unknown,
    Portrait(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    Landscape(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
}

private class PlayerTouchGestureState {
    var downX: Float = 0f
    var downY: Float = 0f
    var downPositionMs: Long = 0L
    var seekTargetMs: Long = 0L
    var seeking: Boolean = false
    var swipeMode: PlayerSwipeMode = PlayerSwipeMode.None
    var startBrightness: Float = DEFAULT_SCREEN_BRIGHTNESS
    var startVolume: Int = 0
    var maxVolume: Int = 0
    var isTouchingNativeController: Boolean = false
    var longPressActive: Boolean = false
    var longPressRunnable: Runnable? = null
    var singleTapRunnable: Runnable? = null
    var lastTapUpTimeMs: Long = 0L
    var lastTapX: Float = 0f
    var lastTapY: Float = 0f
}

private const val DOUBLE_TAP_SEEK_MS = 10_000L
private const val LONG_PRESS_PLAYBACK_SPEED = 2f
private const val PLAYER_CONTROLLER_SHOW_TIMEOUT_MS = 4_000
private const val MEDIA3_CONTROLLER_TOUCH_AREA_DP = 112
private const val MIN_SCREEN_BRIGHTNESS = 0.01f
private const val DEFAULT_SCREEN_BRIGHTNESS = 0.5f
private const val DEFAULT_SYSTEM_BRIGHTNESS = 128
private const val MAX_SYSTEM_BRIGHTNESS = 255f
private const val PORTRAIT_VIDEO_ASPECT_RATIO_MAX = 0.8f
private const val LANDSCAPE_VIDEO_ASPECT_RATIO_MIN = 1.1f

private fun PlaybackAgentDecision.toPlaybackNotice(prefix: String): String {
    val sourceName = nextSourceName
    return if (sourceName.isNullOrBlank()) {
        "$prefix，正在自动切换下一条线路"
    } else {
        "$prefix，已切换到 $sourceName"
    }
}
