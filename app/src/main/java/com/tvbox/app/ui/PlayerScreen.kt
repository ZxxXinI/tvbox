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
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.media3.ui.PlayerView
import com.tvbox.app.domain.PlaybackAgentDecision
import com.tvbox.app.domain.PlaybackAttemptTracker
import com.tvbox.app.domain.PlaybackBufferDecision
import com.tvbox.app.domain.PlaybackBufferMonitor
import com.tvbox.app.domain.PlaybackIssueType
import com.tvbox.app.domain.PlaybackIssueNotice
import com.tvbox.app.domain.PlaybackResumeAnchor
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
    KeepScreenOnWhileVisible()
    val movie = state.detailMovie
    val source = movie?.playSources?.getOrNull(state.playerSourceIndex)
    val episode = source?.episodes?.getOrNull(state.playerEpisodeIndex)


    if (movie == null || source == null || episode == null) {
        ErrorState(message = "播放地址不存在", onRetry = actions::goBack)
        return
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    FullscreenWhilePlayingPageVisible(activity)
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
    val session = rememberPlaybackSession()
    var loadedState by remember { mutableStateOf(state) }
    val resumeAnchor = remember { PlaybackResumeAnchor() }
    fun isCurrentPlayback(): Boolean = player.currentMediaItem?.mediaId?.let {
        it == actions.state.value.currentPlaybackKey()
    } == true
    fun saveCurrentProgress(positionMs: Long, durationMs: Long) {
        val position = if (player.playbackState == Player.STATE_READY) positionMs
            else resumeAnchor.positionForFailure(positionMs)
        actions.savePlaybackProgress(position, durationMs,
            loadedState.copy(playerEpisodeIndex = player.currentMediaItemIndex.coerceAtLeast(0)))
    }
    BindPlaybackSession(session, player) {
        saveCurrentProgress(player.currentPosition, player.duration.takeIf { it > 0 } ?: 0)
    }
    val playerFocusRequester = remember { FocusRequester() }
    var controlsVisible by remember { mutableStateOf(false) }
    var controlsPanel by remember { mutableStateOf(PlayerPanel.None) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var uiPosition by remember { mutableStateOf(0L) }
    var uiDuration by remember { mutableStateOf(0L) }
    var uiPlaying by remember { mutableStateOf(true) }
    BackHandler {
        when {
            controlsPanel != PlayerPanel.None -> controlsPanel = PlayerPanel.None
            controlsVisible -> { controlsVisible = false; playerFocusRequester.requestFocus() }
            else -> actions.goBack()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            if (isCurrentPlayback() && player.playbackState == Player.STATE_READY) {
                resumeAnchor.update(player.currentPosition)
            }
            uiPosition = player.currentPosition.coerceAtLeast(0)
            uiDuration = player.duration.takeIf { it > 0 } ?: 0
            uiPlaying = player.playWhenReady
            delay(250)
        }
    }
    LaunchedEffect(controlsVisible, controlsPanel, controlsInteraction, uiPlaying, scrubbing) {
        if (controlsVisible && controlsPanel == PlayerPanel.None && uiPlaying && !scrubbing) {
            delay(5_000)
            controlsVisible = false
            playerFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(session.interrupted) {
        if (session.interrupted) controlsVisible = true
    }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            androidx.compose.runtime.withFrameNanos { }
            playerFocusRequester.requestFocus()
        }
    }
    val remoteKeyState = remember { PlayerRemoteKeyState() }
    var videoDisplayMode by remember { mutableStateOf(VideoDisplayMode.Unknown) }
    var playbackIssue by remember { mutableStateOf<PlaybackIssueNotice?>(null) }
    var seekGesturePrompt by remember { mutableStateOf<String?>(null) }
    var seekGesturePromptNonce by remember { mutableIntStateOf(0) }
    var playbackNotice by remember { mutableStateOf<String?>(null) }
    val bufferMonitor = remember { PlaybackBufferMonitor() }
    val attemptTracker = remember { PlaybackAttemptTracker() }
    var bufferingPlaybackKey by remember { mutableStateOf<String?>(null) }
    var bufferingCheckNonce by remember { mutableIntStateOf(0) }
    var failedSourceIndexes by remember(movie.apiLineId, movie.id) {
        mutableStateOf(emptySet<Int>())
    }
    val latestState by rememberUpdatedState(state)

    val handlePlaybackIssue = issue@ { issueType: PlaybackIssueType, switchPrefix: String, finalPrefix: String, message: String ->
        val currentState = actions.state.value
        if (!session.canPlay || !isCurrentPlayback()) return@issue
        val resumePosition = resumeAnchor.positionForFailure(player.currentPosition)
        saveCurrentProgress(resumePosition, player.duration.takeIf { it > 0 } ?: 0)
        val failedSources = failedSourceIndexes + currentState.playerSourceIndex
        failedSourceIndexes = failedSources
        val issueToRecord = if (attemptTracker.shouldRecordIssue(currentState.currentPlaybackKey(), issueType)) {
            issueType
        } else {
            null
        }
        val decision = actions.switchToNextPlayableSource(
            blockedSourceIndexes = failedSources,
            positionMs = resumePosition,
            playWhenReady = player.playWhenReady,
            issueType = issueToRecord,
            autoTriggered = true,
        )
        if (decision.switched) {
            bufferingPlaybackKey = null
            playbackIssue = null
            playbackNotice = decision.toPlaybackNotice(switchPrefix)
        } else {
            playbackNotice = null
            val errorMessage = if (!currentState.appSettings.playbackAgentAutoSwitchEnabled) {
                "$message（播放管家自动换线已关闭）"
            } else if (currentState.detailMovie?.playSources.orEmpty().size > 1) {
                "$finalPrefix：$message"
            } else {
                message
            }
            playbackIssue = PlaybackIssueNotice(issueType, errorMessage)
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
                if (player.playerError != error) return
                val message = error.localizedMessage ?: "播放失败"
                handlePlaybackIssue(
                    PlaybackIssueType.Error,
                    "播放管家：当前线路播放失败",
                    "播放失败，播放管家已尝试所有可用线路",
                    message,
                )
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!isCurrentPlayback() || player.playWhenReady != playWhenReady) return
                actions.updatePlayerPlayIntent(playWhenReady)
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
                    if (loadedState.playerSourceIndex != actions.state.value.playerSourceIndex ||
                        loadedState.copy(playerEpisodeIndex = player.currentMediaItemIndex).currentPlaybackKey() !=
                        player.currentMediaItem?.mediaId) return
                    resumeAnchor.update(newPosition.positionMs)
                    bufferMonitor.onSeekStarted(System.currentTimeMillis())
                    bufferingPlaybackKey = null
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val currentState = actions.state.value
                if (loadedState.playerSourceIndex != currentState.playerSourceIndex ||
                    loadedState.detailMovie?.apiLineId != currentState.detailMovie?.apiLineId ||
                    loadedState.detailMovie?.id != currentState.detailMovie?.id) return
                val episodeIndex = player.currentMediaItemIndex
                if (loadedState.copy(playerEpisodeIndex = episodeIndex).currentPlaybackKey() != mediaItem?.mediaId) return
                val episodes = loadedState.detailMovie
                    ?.playSources
                    ?.getOrNull(loadedState.playerSourceIndex)
                    ?.episodes
                    .orEmpty()
                if (episodeIndex in episodes.indices && episodeIndex != currentState.playerEpisodeIndex) {
                    resumeAnchor.update(player.currentPosition)
                    failedSourceIndexes = emptySet()
                    playbackIssue = null
                    playbackNotice = null
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

                saveCurrentProgress(
                    positionMs = player.currentPosition,
                    durationMs = player.duration.takeIf { it > 0L } ?: 0L,
                )
                currentActivity.requestedOrientation = requestedOrientation
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!isCurrentPlayback() || player.playbackState != playbackState) return
                val currentState = actions.state.value
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
                        resumeAnchor.update(player.currentPosition)
                        playbackIssue = playbackIssue?.afterReady()
                        playbackNotice = null
                        failedSourceIndexes = emptySet()
                        val result = bufferMonitor.onReady(
                            playWhenReady = player.playWhenReady,
                            nowMs = System.currentTimeMillis(),
                        )
                        if (
                            result.shouldRecordPlaybackSuccess &&
                            attemptTracker.shouldRecordSuccess(currentState.currentPlaybackKey())
                        ) {
                            actions.recordPlaybackSuccess()
                        }
                    }
                    Player.STATE_ENDED -> {
                        bufferingPlaybackKey = null
                        saveCurrentProgress(
                            positionMs = player.currentPosition,
                            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
                        )
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            saveCurrentProgress(
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            )
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(state.playerSourceIndex, source.episodes) {
        loadedState = state
        resumeAnchor.update(state.playerStartPositionMs)
        playbackIssue = null
        bufferingPlaybackKey = null
        bufferMonitor.onMediaChanged()
        attemptTracker.onPlaybackChanged(state.currentPlaybackKey())
        player.setMediaItems(
            source.episodes.mapIndexed { index, item ->
                MediaItem.Builder().setUri(item.url)
                    .setMediaId(state.copy(playerEpisodeIndex = index).currentPlaybackKey().orEmpty()).build()
            },
            state.playerEpisodeIndex,
            state.playerStartPositionMs,
        )
        player.prepare()
        player.setPlaybackSpeed(state.playerSpeed)
        if (!state.playerPlayWhenReady) session.pause(player) else session.preparePlayback(player)
        saveCurrentProgress(
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
            actions.state.value.currentPlaybackKey() == watchedPlaybackKey && isCurrentPlayback() &&
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
            if (!isCurrentPlayback()) continue
            saveCurrentProgress(
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            )
        }
    }

    LaunchedEffect(state.playerSpeed) {
        player.setPlaybackSpeed(state.playerSpeed)
    }

    LaunchedEffect(Unit) {
        runCatching { playerFocusRequester.requestFocus() }
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
            player.setPlaybackSpeed(touchGesture.originalSpeed)
        }
    }
    val scheduleLongPressSpeed = {
        touchGesture.originalSpeed = player.playbackParameters.speed
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
    val showPlaybackStatusByTap = {
        controlsVisible = !controlsVisible
        controlsInteraction++
    }
    val togglePlaybackByGesture = {
        cancelLongPressSpeed()
        cancelPendingSingleTap()
        if (player.playWhenReady) {
            bufferMonitor.onPaused()
            session.pause(player)
            seekGesturePrompt = "暂停"
        } else {
            session.play(player)
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
        saveCurrentProgress(
            positionMs = targetPosition,
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
        )
    }
    val seekByRemote = { deltaMs: Long ->
        bufferMonitor.onSeekStarted(System.currentTimeMillis())
        val targetPosition = player.seekByOffset(deltaMs)
        val action = if (deltaMs < 0L) "快退 10 秒" else "快进 10 秒"
        val durationMs = player.duration.takeIf { it > 0L }
        seekGesturePrompt = if (durationMs == null) {
            "$action  ${formatPlaybackPosition(targetPosition)}"
        } else {
            "$action  ${formatPlaybackPosition(targetPosition)} / ${formatPlaybackPosition(durationMs)}"
        }
        seekGesturePromptNonce++
        remoteKeyState.seekChanged = true
    }
    val finishRemoteSeek = {
        if (remoteKeyState.seekChanged) {
            saveCurrentProgress(
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            )
        }
        remoteKeyState.resetSeek()
    }
    val changeEpisodeByRemote = { offset: Int ->
        val currentIndex = player.currentMediaItemIndex
        val targetIndex = currentIndex + offset
        if (targetIndex in source.episodes.indices) {
            player.seekTo(targetIndex, 0L)
            session.play(player)
            val action = if (offset < 0) "上一集" else "下一集"
            seekGesturePrompt = "$action  ${source.episodes[targetIndex].title}"
        } else {
            seekGesturePrompt = if (offset < 0) "已经是第一集" else "已经是最后一集"
        }
        seekGesturePromptNonce++
    }
    val cyclePlaybackSpeedByRemote = {
        val nextSpeed = nextPlaybackSpeed(latestState.playerSpeed)
        player.setPlaybackSpeed(nextSpeed)
        actions.updatePlaybackSpeed(nextSpeed)
        seekGesturePrompt = "当前倍速 ${formatPlaybackSpeed(nextSpeed)}"
        seekGesturePromptNonce++
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
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .onPreviewKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                if (controlsVisible) controlsInteraction++
                if (controlsVisible && nativeEvent.keyCode in listOf(
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN
                )) return@onPreviewKeyEvent false
                when (nativeEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_BACK -> {
                        if (controlsVisible || controlsPanel != PlayerPanel.None) {
                            if (event.type == KeyEventType.KeyUp) {
                                if (controlsPanel != PlayerPanel.None) controlsPanel = PlayerPanel.None
                                else controlsVisible = false
                            }
                            true
                        } else false
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND,
                    -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (remoteKeyState.shouldHandleSeek(nativeEvent.eventTime, nativeEvent.repeatCount)) {
                                    seekByRemote(-REMOTE_SEEK_STEP_MS)
                                }
                            }
                            KeyEventType.KeyUp -> finishRemoteSeek()
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> {
                                if (remoteKeyState.shouldHandleSeek(nativeEvent.eventTime, nativeEvent.repeatCount)) {
                                    seekByRemote(REMOTE_SEEK_STEP_MS)
                                }
                            }
                            KeyEventType.KeyUp -> finishRemoteSeek()
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    -> {
                        if (controlsVisible) false else {
                            if (event.type == KeyEventType.KeyUp) { controlsVisible = true; controlsInteraction++ }
                            true
                        }
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (event.type == KeyEventType.KeyUp) togglePlaybackByGesture()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> {
                        if (event.type == KeyEventType.KeyUp) {
                            session.play(player)
                            seekGesturePrompt = "播放"
                            seekGesturePromptNonce++
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (event.type == KeyEventType.KeyUp) {
                            bufferMonitor.onPaused()
                            session.pause(player)
                            seekGesturePrompt = "暂停"
                            seekGesturePromptNonce++
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    AndroidKeyEvent.KEYCODE_1,
                    AndroidKeyEvent.KEYCODE_NUMPAD_1,
                    -> {
                        if (event.type == KeyEventType.KeyUp) changeEpisodeByRemote(-1)
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                    AndroidKeyEvent.KEYCODE_3,
                    AndroidKeyEvent.KEYCODE_NUMPAD_3,
                    -> {
                        if (event.type == KeyEventType.KeyUp) changeEpisodeByRemote(1)
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MENU -> {
                        if (event.type == KeyEventType.KeyUp) cyclePlaybackSpeedByRemote()
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
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    isFocusable = false
                    isFocusableInTouchMode = false
                    useController = false
                    resizeMode = state.appSettings.videoScaleMode.toPlayerResizeMode()
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    val gestureTouchListener = View.OnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
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
                                    saveCurrentProgress(
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
                                        showPlaybackStatusByTap()
                                    }
                                    touchGesture.singleTapRunnable = singleTapRunnable
                                    touchHandler.postDelayed(singleTapRunnable, doubleTapTimeoutMs)
                                }
                            }
                        }
                        true
                    }
                    setOnTouchListener(gestureTouchListener)
                }
            },
            update = {
                it.player = player
                it.resizeMode = state.appSettings.videoScaleMode.toPlayerResizeMode()
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (controlsVisible) {
            PlayerControls(
                title = movie.name, sources = movie.playSources,
                sourceIndex = state.playerSourceIndex, episodeIndex = player.currentMediaItemIndex.coerceIn(0, source.episodes.lastIndex),
                positionMs = uiPosition, durationMs = uiDuration, playing = uiPlaying,
                speed = state.playerSpeed, panel = controlsPanel,
                videoScaleMode = state.appSettings.videoScaleMode,
                onPanel = { controlsPanel = it; controlsInteraction++ },
                onToggle = { togglePlaybackByGesture(); controlsInteraction++ },
                onEpisode = { index ->
                    if (index in source.episodes.indices) {
                        saveCurrentProgress(player.currentPosition, uiDuration)
                        player.seekTo(index, 0)
                        session.play(player)
                        controlsInteraction++
                    }
                },
                onSource = { index ->
                    saveCurrentProgress(player.currentPosition, uiDuration)
                    actions.switchPlayerSource(index, resumeAnchor.positionForFailure(player.currentPosition), player.playWhenReady)
                    controlsInteraction++
                },
                onSpeed = { speed -> player.setPlaybackSpeed(speed); actions.updatePlaybackSpeed(speed); controlsInteraction++ },
                onVideoScale = { mode -> actions.updateVideoScaleMode(mode); controlsInteraction++ },
                onSeek = { position ->
                    bufferMonitor.onSeekStarted(System.currentTimeMillis())
                    player.seekTo(position)
                    saveCurrentProgress(position, uiDuration)
                },
                onInteraction = { controlsInteraction++ },
                onScrubbing = { scrubbing = it },
            )
        }
        val playbackStatus = playbackIssue?.message ?: playbackNotice
        if (playbackStatus != null) {
            GesturePrompt(
                text = playbackStatus,
                textColor = if (playbackIssue != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
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
    return listOf(movie.apiLineId, movie.id, playerSourceIndex, playerEpisodeIndex, episode.url).joinToString("|")
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
    var originalSpeed: Float = 1f
    var downX: Float = 0f
    var downY: Float = 0f
    var downPositionMs: Long = 0L
    var seekTargetMs: Long = 0L
    var seeking: Boolean = false
    var swipeMode: PlayerSwipeMode = PlayerSwipeMode.None
    var startBrightness: Float = DEFAULT_SCREEN_BRIGHTNESS
    var startVolume: Int = 0
    var maxVolume: Int = 0
    var longPressActive: Boolean = false
    var longPressRunnable: Runnable? = null
    var singleTapRunnable: Runnable? = null
    var lastTapUpTimeMs: Long = 0L
    var lastTapX: Float = 0f
    var lastTapY: Float = 0f
}

private class PlayerRemoteKeyState {
    var lastSeekEventTimeMs: Long = Long.MIN_VALUE
    var seekChanged: Boolean = false

    fun shouldHandleSeek(eventTimeMs: Long, repeatCount: Int): Boolean {
        if (
            repeatCount == 0 ||
            lastSeekEventTimeMs == Long.MIN_VALUE ||
            eventTimeMs - lastSeekEventTimeMs >= REMOTE_SEEK_REPEAT_INTERVAL_MS
        ) {
            lastSeekEventTimeMs = eventTimeMs
            return true
        }
        return false
    }

    fun resetSeek() {
        lastSeekEventTimeMs = Long.MIN_VALUE
        seekChanged = false
    }
}

private const val DOUBLE_TAP_SEEK_MS = 10_000L
private const val REMOTE_SEEK_STEP_MS = 10_000L
private const val REMOTE_SEEK_REPEAT_INTERVAL_MS = 150L
private const val LONG_PRESS_PLAYBACK_SPEED = 2f
private const val MIN_SCREEN_BRIGHTNESS = 0.01f
private const val DEFAULT_SCREEN_BRIGHTNESS = 0.5f
private const val DEFAULT_SYSTEM_BRIGHTNESS = 128
private const val MAX_SYSTEM_BRIGHTNESS = 255f
private const val PORTRAIT_VIDEO_ASPECT_RATIO_MAX = 0.8f
private const val LANDSCAPE_VIDEO_ASPECT_RATIO_MIN = 1.1f
private val PLAYBACK_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

private fun nextPlaybackSpeed(currentSpeed: Float): Float {
    val currentIndex = PLAYBACK_SPEEDS.indexOfFirst { abs(it - currentSpeed) < 0.01f }
    return PLAYBACK_SPEEDS[(currentIndex + 1).coerceAtLeast(0) % PLAYBACK_SPEEDS.size]
}

private fun formatPlaybackSpeed(speed: Float): String {
    return "${speed.toString().trimEnd('0').trimEnd('.')}x"
}

private fun PlaybackAgentDecision.toPlaybackNotice(prefix: String): String {
    val sourceName = nextSourceName
    return if (sourceName.isNullOrBlank()) {
        "$prefix，正在自动切换下一条线路"
    } else {
        "$prefix，已切换到 $sourceName"
    }
}
