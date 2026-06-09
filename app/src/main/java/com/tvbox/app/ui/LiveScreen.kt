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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tvbox.app.domain.LiveChannel
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
    val channels = state.liveChannels
    val currentChannel = channels[state.liveChannelIndex.coerceIn(0, channels.lastIndex)]

    BackHandler {
        actions.goBack()
    }

    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    val playerFocusRequester = remember { FocusRequester() }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var channelListVisible by remember { mutableStateOf(false) }
    var channelListInteraction by remember { mutableIntStateOf(0) }
    var channelNumberInput by remember { mutableStateOf("") }
    var channelNumberNonce by remember { mutableIntStateOf(0) }
    var promptMessage by remember { mutableStateOf("") }
    var promptNonce by remember { mutableIntStateOf(0) }
    var promptVisible by remember { mutableStateOf(false) }
    var channelBadgeVisible by remember { mutableStateOf(true) }

    fun showPrompt(message: String) {
        promptMessage = message
        promptVisible = true
        promptNonce++
    }

    fun showChannelList() {
        channelListVisible = true
        channelListInteraction++
    }

    fun handleNumberKey(number: Int): Boolean {
        channelNumberInput = (channelNumberInput + number.toString()).takeLast(MAX_CHANNEL_NUMBER_DIGITS)
        showPrompt("频道 $channelNumberInput")
        channelNumberNonce++
        return true
    }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "直播播放失败"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(currentChannel.url) {
        playbackError = null
        channelBadgeVisible = true
        player.setMediaItem(MediaItem.fromUri(currentChannel.url))
        player.prepare()
        player.play()
        delay(CHANNEL_BADGE_HIDE_DELAY_MS)
        channelBadgeVisible = false
    }

    LaunchedEffect(playbackError) {
        if (playbackError == null) return@LaunchedEffect
        channelBadgeVisible = true
        delay(CHANNEL_BADGE_HIDE_DELAY_MS)
        channelBadgeVisible = false
    }

    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    LaunchedEffect(channelListVisible, channelListInteraction) {
        if (!channelListVisible) return@LaunchedEffect
        delay(CHANNEL_LIST_HIDE_DELAY_MS)
        channelListVisible = false
        playerFocusRequester.requestFocus()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
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
                            showChannelList()
                            true
                        } else {
                            false
                        }
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (channelListVisible) {
                            actions.playNextLiveChannel()
                            showChannelList()
                            true
                        } else {
                            false
                        }
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
                channelCount = channels.size,
                playbackError = playbackError,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(28.dp),
            )
        }
        if (channelListVisible) {
            LiveChannelList(
                channels = channels,
                selectedIndex = state.liveChannelIndex,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        if (promptVisible && promptMessage.isNotBlank()) {
            LivePrompt(
                message = promptMessage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
            )
        }
    }
}

@Composable
private fun LiveChannelBadge(
    channel: LiveChannel,
    channelCount: Int,
    playbackError: String?,
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
        if (playbackError != null) {
            Text(
                text = playbackError,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveChannelList(
    channels: List<LiveChannel>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val safeSelectedIndex = selectedIndex.coerceIn(0, channels.lastIndex)

    LaunchedEffect(safeSelectedIndex) {
        listState.animateScrollToItem(safeSelectedIndex)
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(360.dp),
        color = Color(0xCC000000),
        contentColor = Color.White,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.number }) { index, channel ->
                LiveChannelRow(
                    channel = channel,
                    selected = index == safeSelectedIndex,
                )
            }
        }
    }
}

@Composable
private fun LiveChannelRow(
    channel: LiveChannel,
    selected: Boolean,
) {
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
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
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private const val CHANNEL_LIST_HIDE_DELAY_MS = 2_000L
private const val CHANNEL_BADGE_HIDE_DELAY_MS = 2_000L
private const val CHANNEL_NUMBER_COMMIT_DELAY_MS = 1_000L
private const val PROMPT_HIDE_DELAY_MS = 1_500L
private const val MAX_CHANNEL_NUMBER_DIGITS = 4
