package com.tvbox.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
import com.tvbox.app.domain.LivePlaybackWatchdog
import com.tvbox.app.domain.LiveChannel
import com.tvbox.app.domain.PlaybackBufferDecision
import com.tvbox.app.domain.PlaybackBufferMonitor
import com.tvbox.app.ui.components.ErrorState
import com.tvbox.app.ui.components.LoadingState
import com.tvbox.app.ui.components.PageSurface
import kotlinx.coroutines.delay

@Composable
fun LiveScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    PageSurface { padding ->
        when {
            state.liveLoading -> LoadingState(
                text = "正在加载直播源",
                modifier = Modifier.padding(padding),
            )
            state.liveError != null -> ErrorState(
                message = state.liveError,
                onRetry = actions::refreshLive,
                modifier = Modifier.padding(padding),
            )
            state.liveChannels.isEmpty() -> ErrorState(
                message = "没有可用直播频道",
                onRetry = actions::refreshLive,
                modifier = Modifier.padding(padding),
            )
            else -> LivePlayerScreen(state = state, actions = actions)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LivePlayerScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    KeepScreenOnWhileVisible()
    val channels = state.liveChannels
    val currentChannel = channels[state.liveChannelIndex.coerceIn(0, channels.lastIndex)]
    val currentLine = currentChannel.lines[state.liveLineIndex.coerceIn(0, currentChannel.lines.lastIndex)]
    val latestLineUrl = rememberUpdatedState(currentLine.url)

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findActivityForLive() }
    val isTelevision = remember(context) { context.isTelevisionDeviceForLive() }
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val initialRequestedOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    val playerFocusRequester = remember { FocusRequester() }
    val bufferMonitor = remember {
        PlaybackBufferMonitor(
            continuousBufferThresholdMs = LIVE_CONTINUOUS_BUFFER_THRESHOLD_MS,
            frequentBufferWindowMs = LIVE_FREQUENT_BUFFER_WINDOW_MS,
            frequentBufferCount = LIVE_FREQUENT_BUFFER_COUNT,
            cumulativeBufferThresholdMs = LIVE_CUMULATIVE_BUFFER_THRESHOLD_MS,
        )
    }
    val playbackWatchdog = remember { LivePlaybackWatchdog() }
    var channelListVisible by remember { mutableStateOf(false) }
    var channelListInteraction by remember { mutableIntStateOf(0) }
    var mobileControlsVisible by remember { mutableStateOf(!isTelevision) }
    var mobileControlsInteraction by remember { mutableIntStateOf(0) }
    var channelNumberInput by remember { mutableStateOf("") }
    var channelNumberNonce by remember { mutableIntStateOf(0) }
    var promptMessage by remember { mutableStateOf("") }
    var promptNonce by remember { mutableIntStateOf(0) }
    var promptVisible by remember { mutableStateOf(false) }
    var channelBadgeVisible by remember { mutableStateOf(true) }
    var bufferingLineUrl by remember { mutableStateOf<String?>(null) }
    var bufferingCheckNonce by remember { mutableIntStateOf(0) }
    var automaticSwitchLineUrl by remember { mutableStateOf<String?>(null) }
    val latestAutomaticSwitchLineUrl = rememberUpdatedState(automaticSwitchLineUrl)

    fun showPrompt(message: String) {
        promptMessage = message
        promptVisible = true
        promptNonce++
    }

    fun showChannelList() {
        channelListVisible = true
        channelListInteraction++
    }

    fun keepChannelListVisibleWhileBrowsing() {
        if (channelListVisible) channelListInteraction++
    }

    fun showMobileControls() {
        if (!isTelevision) {
            mobileControlsVisible = true
            mobileControlsInteraction++
        }
    }

    fun handleNumberKey(number: Int): Boolean {
        channelNumberInput = (channelNumberInput + number.toString()).takeLast(MAX_CHANNEL_NUMBER_DIGITS)
        showPrompt("频道 $channelNumberInput")
        channelNumberNonce++
        return true
    }

    fun switchLiveLineAfterIssue() {
        val lineUrl = latestLineUrl.value
        if (latestAutomaticSwitchLineUrl.value == lineUrl) return
        automaticSwitchLineUrl = lineUrl
        actions.advanceLiveLineAfterFailure()
    }

    fun handleBufferDecision(decision: PlaybackBufferDecision?) {
        if (decision != null) {
            switchLiveLineAfterIssue()
        }
    }

    BackHandler {
        actions.goBack()
    }

    BackHandler(enabled = channelListVisible) {
        channelListVisible = false
        showMobileControls()
        if (isTelevision) playerFocusRequester.requestFocus()
    }

    DisposableEffect(activity, initialRequestedOrientation, isTelevision) {
        if (!isTelevision) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            if (!isTelevision) {
                activity?.requestedOrientation = initialRequestedOrientation
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                switchLiveLineAfterIssue()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    bufferMonitor.onPaused()
                    playbackWatchdog.onPaused()
                    bufferingLineUrl = null
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        handleBufferDecision(
                            bufferMonitor.onBuffering(
                                playWhenReady = player.playWhenReady,
                                nowMs = System.currentTimeMillis(),
                            ),
                        )
                        if (player.playWhenReady && bufferingLineUrl != latestLineUrl.value) {
                            bufferingLineUrl = latestLineUrl.value
                            bufferingCheckNonce++
                        }
                    }
                    Player.STATE_READY -> {
                        bufferingLineUrl = null
                        handleBufferDecision(
                            bufferMonitor.onReady(
                                playWhenReady = player.playWhenReady,
                                nowMs = System.currentTimeMillis(),
                            ).decision,
                        )
                    }
                    Player.STATE_ENDED -> {
                        bufferingLineUrl = null
                        switchLiveLineAfterIssue()
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(currentLine.url) {
        bufferMonitor.onMediaChanged()
        playbackWatchdog.onMediaChanged()
        bufferingLineUrl = null
        automaticSwitchLineUrl = null
        channelBadgeVisible = true
        player.setMediaItem(MediaItem.fromUri(currentLine.url))
        player.prepare()
        player.play()
        delay(CHANNEL_BADGE_HIDE_DELAY_MS)
        channelBadgeVisible = false
    }

    LaunchedEffect(bufferingCheckNonce, bufferingLineUrl) {
        val watchedLineUrl = bufferingLineUrl ?: return@LaunchedEffect
        delay(LIVE_CONTINUOUS_BUFFER_THRESHOLD_MS)
        if (
            bufferingLineUrl == watchedLineUrl &&
            latestLineUrl.value == watchedLineUrl &&
            player.playbackState == Player.STATE_BUFFERING
        ) {
            handleBufferDecision(
                bufferMonitor.onBuffering(
                    playWhenReady = player.playWhenReady,
                    nowMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    LaunchedEffect(player, currentLine.url) {
        while (true) {
            delay(LIVE_PROGRESS_SAMPLE_INTERVAL_MS)
            if (
                playbackWatchdog.onSample(
                    isPlaying = player.isPlaying,
                    positionMs = player.currentPosition,
                    nowMs = System.currentTimeMillis(),
                )
            ) {
                switchLiveLineAfterIssue()
            }
        }
    }

    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    LaunchedEffect(channelListVisible, channelListInteraction) {
        if (!isTelevision || !channelListVisible) return@LaunchedEffect
        delay(CHANNEL_LIST_HIDE_DELAY_MS)
        channelListVisible = false
        playerFocusRequester.requestFocus()
    }

    LaunchedEffect(mobileControlsVisible, mobileControlsInteraction, channelListVisible) {
        if (isTelevision || !mobileControlsVisible || channelListVisible) return@LaunchedEffect
        delay(MOBILE_CONTROLS_HIDE_DELAY_MS)
        mobileControlsVisible = false
    }

    LaunchedEffect(channelNumberNonce) {
        if (channelNumberNonce == 0) return@LaunchedEffect
        delay(CHANNEL_NUMBER_COMMIT_DELAY_MS)
        val requestedChannel = channelNumberInput.toIntOrNull()
        channelNumberInput = ""
        if (requestedChannel == null || !actions.selectLiveChannelNumber(requestedChannel)) {
            showPrompt("无频道内容")
        } else {
            promptVisible = false
        }
    }

    LaunchedEffect(promptNonce) {
        if (promptNonce == 0) return@LaunchedEffect
        delay(PROMPT_HIDE_DELAY_MS)
        promptVisible = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .pointerInput(channels.size) {
                detectTapGestures(
                    onTap = {
                        if (isTelevision) {
                            showChannelList()
                        } else {
                            mobileControlsVisible = !mobileControlsVisible
                            if (mobileControlsVisible) mobileControlsInteraction++
                        }
                    },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2f) {
                            actions.playPreviousLiveChannel()
                            showPrompt("上一个频道")
                        } else {
                            actions.playNextLiveChannel()
                            showPrompt("下一个频道")
                        }
                        showMobileControls()
                    },
                )
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        actions.playPreviousLiveChannel()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        actions.playNextLiveChannel()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    -> {
                        showChannelList()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        if (channelListVisible) {
                            actions.playPreviousLiveChannel()
                            keepChannelListVisibleWhileBrowsing()
                        } else {
                            actions.playPreviousLiveLine()
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (channelListVisible) {
                            actions.playNextLiveChannel()
                            keepChannelListVisibleWhileBrowsing()
                        } else {
                            actions.playNextLiveLine()
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_0,
                    AndroidKeyEvent.KEYCODE_NUMPAD_0,
                    -> handleNumberKey(0)
                    AndroidKeyEvent.KEYCODE_1,
                    AndroidKeyEvent.KEYCODE_NUMPAD_1,
                    -> handleNumberKey(1)
                    AndroidKeyEvent.KEYCODE_2,
                    AndroidKeyEvent.KEYCODE_NUMPAD_2,
                    -> handleNumberKey(2)
                    AndroidKeyEvent.KEYCODE_3,
                    AndroidKeyEvent.KEYCODE_NUMPAD_3,
                    -> handleNumberKey(3)
                    AndroidKeyEvent.KEYCODE_4,
                    AndroidKeyEvent.KEYCODE_NUMPAD_4,
                    -> handleNumberKey(4)
                    AndroidKeyEvent.KEYCODE_5,
                    AndroidKeyEvent.KEYCODE_NUMPAD_5,
                    -> handleNumberKey(5)
                    AndroidKeyEvent.KEYCODE_6,
                    AndroidKeyEvent.KEYCODE_NUMPAD_6,
                    -> handleNumberKey(6)
                    AndroidKeyEvent.KEYCODE_7,
                    AndroidKeyEvent.KEYCODE_NUMPAD_7,
                    -> handleNumberKey(7)
                    AndroidKeyEvent.KEYCODE_8,
                    AndroidKeyEvent.KEYCODE_NUMPAD_8,
                    -> handleNumberKey(8)
                    AndroidKeyEvent.KEYCODE_9,
                    AndroidKeyEvent.KEYCODE_NUMPAD_9,
                    -> handleNumberKey(9)
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
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        if (channelBadgeVisible) {
            LiveChannelBadge(
                channel = currentChannel,
                lineIndex = state.liveLineIndex,
                lineCount = currentChannel.lines.size,
                channelCount = channels.size,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(20.dp),
            )
        }
        if (channelListVisible) {
            val channelPanelModifier = when {
                !isTelevision && isPortrait -> Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(MOBILE_PORTRAIT_CHANNEL_PANEL_HEIGHT_FRACTION)
                isTelevision -> Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(180.dp)
                else -> Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width((maxWidth * MOBILE_LANDSCAPE_CHANNEL_PANEL_WIDTH_FRACTION).coerceIn(240.dp, 340.dp))
            }
            LiveChannelList(
                channels = channels,
                selectedIndex = state.liveChannelIndex,
                isPortraitPhone = !isTelevision && isPortrait,
                onChannelClick = { index ->
                    actions.selectLiveChannel(index)
                    channelListVisible = false
                    showPrompt(channels[index].name)
                    showMobileControls()
                    if (isTelevision) playerFocusRequester.requestFocus()
                },
                onInteraction = ::keepChannelListVisibleWhileBrowsing,
                modifier = channelPanelModifier,
            )
        }
        if (!isTelevision && mobileControlsVisible && !channelListVisible) {
            MobileLiveControls(
                isPortrait = isPortrait,
                orientationActionLabel = if (isPortrait) "横屏" else "自动",
                onPrevious = {
                    actions.playPreviousLiveChannel()
                    showPrompt("上一个频道")
                    showMobileControls()
                },
                onChannels = {
                    showChannelList()
                    mobileControlsVisible = false
                },
                onNext = {
                    actions.playNextLiveChannel()
                    showPrompt("下一个频道")
                    showMobileControls()
                },
                onNextLine = {
                    showPrompt(if (actions.playNextLiveLine()) "正在切换线路" else "当前频道只有一条线路")
                    showMobileControls()
                },
                onRefresh = actions::refreshLive,
                onOrientation = {
                    activity?.requestedOrientation = if (isPortrait) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    showMobileControls()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        if (promptVisible && promptMessage.isNotBlank()) {
            LivePrompt(
                message = promptMessage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = if (!isTelevision && mobileControlsVisible) 132.dp else 80.dp),
            )
        }
    }
}

@Composable
private fun LiveChannelBadge(
    channel: LiveChannel,
    lineIndex: Int,
    lineCount: Int,
    channelCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            text = "${channel.number}/$channelCount  ${channel.name}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${channel.groupName} · 线路${lineIndex + 1}/$lineCount",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveChannelList(
    channels: List<LiveChannel>,
    selectedIndex: Int,
    isPortraitPhone: Boolean,
    onChannelClick: (Int) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val safeSelectedIndex = selectedIndex.coerceIn(0, channels.lastIndex)

    LaunchedEffect(safeSelectedIndex) {
        listState.animateScrollToItem(safeSelectedIndex)
    }

    Surface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        onInteraction()
                    }
                }
            },
        shape = if (isPortraitPhone) {
            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        } else {
            RoundedCornerShape(0.dp)
        },
        color = Color(0xE6121212),
        contentColor = Color.White,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.number }) { index, channel ->
                Column {
                    if (index == 0 || channels[index - 1].groupName != channel.groupName) {
                        Text(
                            text = channel.groupName,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                        )
                    }
                    LiveChannelRow(
                        channel = channel,
                        selected = index == safeSelectedIndex,
                        onClick = { onChannelClick(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveChannelRow(
    channel: LiveChannel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = channel.number.toString(),
            modifier = Modifier.width(46.dp),
            color = contentColor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = channel.name,
            color = contentColor,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MobileLiveControls(
    isPortrait: Boolean,
    orientationActionLabel: String,
    onPrevious: () -> Unit,
    onChannels: () -> Unit,
    onNext: () -> Unit,
    onNextLine: () -> Unit,
    onRefresh: () -> Unit,
    onOrientation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xD9141414),
        contentColor = Color.White,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 4.dp,
    ) {
        if (isPortrait) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MobileLiveControlButton("上一台", onPrevious)
                    MobileLiveControlButton("频道", onChannels)
                    MobileLiveControlButton("下一台", onNext)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MobileLiveControlButton("换线", onNextLine)
                    MobileLiveControlButton("刷新", onRefresh)
                    MobileLiveControlButton(orientationActionLabel, onOrientation)
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(10.dp),
            ) {
                MobileLiveControlButton("上一台", onPrevious)
                MobileLiveControlButton("频道", onChannels)
                MobileLiveControlButton("下一台", onNext)
                MobileLiveControlButton("换线", onNextLine)
                MobileLiveControlButton("刷新", onRefresh)
                MobileLiveControlButton(orientationActionLabel, onOrientation)
            }
        }
    }
}

@Composable
private fun RowScope.MobileLiveControlButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun LivePrompt(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xB8000000))
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun Context.findActivityForLive(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityForLive()
    else -> null
}

private fun Context.isTelevisionDeviceForLive(): Boolean {
    val deviceType = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return deviceType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

private const val CHANNEL_LIST_HIDE_DELAY_MS = 2_000L
private const val MOBILE_CONTROLS_HIDE_DELAY_MS = 4_000L
private const val CHANNEL_BADGE_HIDE_DELAY_MS = 2_000L
private const val CHANNEL_NUMBER_COMMIT_DELAY_MS = 1_000L
private const val PROMPT_HIDE_DELAY_MS = 1_500L
private const val MAX_CHANNEL_NUMBER_DIGITS = 4
private const val LIVE_CONTINUOUS_BUFFER_THRESHOLD_MS = 6_000L
private const val LIVE_FREQUENT_BUFFER_WINDOW_MS = 60_000L
private const val LIVE_FREQUENT_BUFFER_COUNT = 3
private const val LIVE_CUMULATIVE_BUFFER_THRESHOLD_MS = 12_000L
private const val LIVE_PROGRESS_SAMPLE_INTERVAL_MS = 1_000L
private const val MOBILE_PORTRAIT_CHANNEL_PANEL_HEIGHT_FRACTION = 0.62f
private const val MOBILE_LANDSCAPE_CHANNEL_PANEL_WIDTH_FRACTION = 0.44f
