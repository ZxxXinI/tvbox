package com.tvbox.app.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.tvbox.app.domain.PlatformLiveRoom
import com.tvbox.app.domain.ResolvedPlatformLiveStream
import com.tvbox.app.domain.TvTheme
import com.tvbox.app.ui.components.ErrorState
import com.tvbox.app.ui.components.LoadingState
import com.tvbox.app.ui.components.PageSurface
import com.tvbox.app.ui.components.tvFocusScale
import kotlinx.coroutines.delay

@Composable
fun PlatformLiveScreen(state: TvBoxUiState, actions: TvBoxViewModel) {
    BackHandler { actions.goBack() }
    if (state.platformLiveDestination == PlatformLiveDestination.Player) {
        PlatformLivePlayerScreen(state, actions)
    } else {
        PlatformLiveBrowserScreen(state, actions)
    }
}

@Composable
internal fun PlatformLiveRoomsPanel(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
    favoritesMode: Boolean,
    modifier: Modifier = Modifier,
    restoreRoomFocus: Boolean,
) {
    val gridState = rememberLazyGridState()
    val selectedRoomFocusRequester = remember { FocusRequester() }
    val query = state.platformLiveRoomQuery
    val searching = !favoritesMode && query.isNotBlank()
    val rooms = remember(state.platformLiveRooms, query) { state.visiblePlatformLiveRooms }
    val hasMore = !favoritesMode && state.platformLiveRoomPage < state.platformLiveRoomPageCount
    val selectedRoomId = state.platformLiveRooms.getOrNull(state.platformLiveRoomIndex)?.id
    val selectedRoomIndex = rooms.indexOfFirst { it.id == selectedRoomId }.coerceAtLeast(0)
    val loading = !favoritesMode && state.platformLiveLoading
    val error = if (favoritesMode) null else state.platformLiveError
    val roomGridReady = rooms.isNotEmpty() && !loading && error == null
    val cinema = state.appSettings.theme == TvTheme.Cinema
    var restoredFocus by remember { mutableStateOf(false) }
    var displayedQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query != displayedQuery) {
            restoredFocus = true
            displayedQuery = query
            if (error == null && !loading) gridState.scrollToItem(0)
        }
    }
    LaunchedEffect(roomGridReady) {
        if (!roomGridReady || restoredFocus || !restoreRoomFocus) return@LaunchedEffect
        restoredFocus = true
        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == selectedRoomIndex }) {
            gridState.scrollToItem(selectedRoomIndex)
        }
        selectedRoomFocusRequester.requestPlayerControlFocus()
    }

    val emptyMessage = if (favoritesMode) "还没有收藏的直播房间" else "这个分类暂时没有正在直播的房间"
    Column(modifier) {
        state.platformLiveFavoriteError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }
        when {
            loading -> LoadingState("正在加载直播间", Modifier.weight(1f))
            error != null -> ErrorState(error, actions::refreshPlatformLive, Modifier.weight(1f))
            state.platformLiveRooms.isEmpty() && !hasMore -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val columns = when {
                    !cinema && maxWidth >= 780.dp -> 4
                    maxWidth >= if (cinema) 620.dp else 540.dp -> 3
                    else -> 2
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(rooms, key = { _, room -> room.id }) { index, room ->
                        PlatformLiveRoomCard(
                            room = room,
                            onClick = { actions.openPlatformLiveRoom(room) },
                            isFavorite = state.platformLiveFavorites.any { it.id == room.id },
                            onToggleFavorite = { actions.togglePlatformLiveFavorite(room) },
                            favoritesMode = favoritesMode,
                            modifier = if (index == selectedRoomIndex) {
                                Modifier.focusRequester(selectedRoomFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                    }
                    if (searching && rooms.isEmpty() && !state.platformLiveLoadingMore) {
                        item(key = "room-search-empty", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = if (hasMore) "已查页面暂无匹配，可继续搜索。" else "当前分类没有匹配的直播间。",
                                modifier = Modifier.padding(vertical = 14.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (hasMore) {
                        item(key = "load-more") {
                            PlatformLiveLoadMoreCard(
                                loading = state.platformLiveLoadingMore,
                                text = when {
                                    state.platformLiveLoadMoreFailed -> "加载失败，重试本页"
                                    searching -> "继续搜索"
                                    else -> "加载更多（${state.platformLiveRoomPage}/${state.platformLiveRoomPageCount}）"
                                },
                                onClick = actions::loadMorePlatformLiveRooms,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlatformLiveRoomSearch(state: TvBoxUiState, actions: TvBoxViewModel, modifier: Modifier = Modifier) {
    var draft by rememberSaveable(state.platformLiveSelectedSite?.id, state.platformLiveSelectedCategory?.id) {
        mutableStateOf(state.platformLiveRoomQuery)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val inputFocus = remember { FocusRequester() }
    val submit = {
        keyboard?.hide()
        actions.searchPlatformLiveRooms(draft)
    }
    val canClear = draft.isNotEmpty() || state.platformLiveRoomQuery.isNotEmpty()
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).focusRequester(inputFocus)
                    .semantics { contentDescription = "用户名或直播标题" },
                placeholder = { Text("用户名或直播标题", maxLines = 1) },
                trailingIcon = if (canClear) {
                    {
                        IconButton(
                            onClick = {
                                draft = ""
                                keyboard?.hide()
                                actions.searchPlatformLiveRooms("")
                                inputFocus.requestFocus()
                            },
                            modifier = Modifier.semantics { contentDescription = "清除搜索" },
                        ) { Text("×", style = MaterialTheme.typography.titleLarge) }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
            )
            val shape = RoundedCornerShape(8.dp)
            Button(
                onClick = submit,
                enabled = !state.platformLiveLoading,
                shape = shape,
                modifier = Modifier.tvFocusScale(shape, focusedBorder = Color.White, idleBorder = Color.Transparent),
            ) { Text("搜索") }
        }
        if (state.platformLiveRoomQuery.isNotEmpty()) {
            Text(
                text = if (state.platformLiveLoadingMore) "已查前 ${state.platformLiveRoomPage} 页 · 正在搜索后续页面"
                    else "已查前 ${state.platformLiveRoomPage} 页",
                modifier = Modifier.padding(bottom = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PlatformLiveRoomCard(
    room: PlatformLiveRoom,
    onClick: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    favoritesMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val subtitle = listOf(room.anchor, if (favoritesMode) room.site.uppercase() else formatOnline(room.online))
        .filter { it.isNotBlank() }
        .joinToString(" / ")
    val shape = MaterialTheme.shapes.medium
    var focused by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = modifier.fillMaxWidth().tvFocusScale(shape).clip(shape)
                .onFocusChanged { focused = it.isFocused }
                .background(if (focused) Color(0xFF252525) else MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (room.cover.isBlank()) {
                    Text("直播", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    AsyncImage(
                        model = room.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = room.title,
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(start = 12.dp, end = 62.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = subtitle.ifBlank { "正在直播" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        var starFocused by remember { mutableStateOf(false) }
        val starShape = MaterialTheme.shapes.small
        Box(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp).size(44.dp)
                .tvFocusScale(starShape, focusedBorder = Color.White).clip(starShape)
                .onFocusChanged { starFocused = it.isFocused }
                .background(if (starFocused) MaterialTheme.colorScheme.primary else Color(0xCC101010))
                .semantics { contentDescription = if (isFavorite) "取消收藏 ${room.title}" else "收藏 ${room.title}" }
                .clickable(onClick = onToggleFavorite),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isFavorite) "★" else "☆",
                color = when {
                    starFocused -> MaterialTheme.colorScheme.onPrimary
                    isFavorite -> MaterialTheme.colorScheme.primary
                    else -> Color.White
                },
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

@Composable
private fun PlatformLiveLoadMoreCard(loading: Boolean, text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusScale(shape = shape)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .background(if (focused) Color(0xFF252525) else MaterialTheme.colorScheme.surface)
            .clickable { if (!loading) onClick() }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
        Text(text = if (loading) "正在加载" else text, style = MaterialTheme.typography.titleSmall)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlatformLivePlayerScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    val room = state.platformLiveRooms.getOrNull(state.platformLiveRoomIndex)
    if (room == null) {
        PageSurface { padding ->
            ErrorState(
                message = "未选择直播间",
                onRetry = { actions.goBack() },
                modifier = Modifier.padding(padding),
            )
        }
        return
    }
    KeepScreenOnWhileVisible()
    FullscreenWhilePlayingPageVisible(LocalActivity.current)
    val stream = state.platformLiveStream?.takeIf { it.channelId == room.id }
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(stream?.url, stream?.headers) {
        stream?.let { resolved ->
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setDefaultRequestProperties(resolved.headers)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
                .apply { playWhenReady = true }
        }
    }
    val playerFocusRequester = remember { FocusRequester() }
    val session = rememberPlaybackSession()
    BindPlaybackSession(session, player)
    var playbackError by remember(stream?.url) { mutableStateOf<String?>(null) }
    var playerState by remember(stream?.url) { mutableIntStateOf(Player.STATE_IDLE) }
    var badgeVisible by remember { mutableStateOf(true) }

    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (!session.canPlay) return
                    playbackError = error.localizedMessage ?: "平台直播播放失败"
                    badgeVisible = true
                    actions.recoverPlatformLiveChannel()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    playerState = playbackState
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
    }

    LaunchedEffect(player, stream?.url) {
        if (player == null || stream == null) return@LaunchedEffect
        playbackError = null
        badgeVisible = true
        player.setMediaItem(MediaItem.fromUri(stream.url))
        player.prepare()
        session.preparePlayback(player)
        delay(PLATFORM_LIVE_BADGE_HIDE_DELAY_MS)
        badgeVisible = false
    }

    LaunchedEffect(playerState, player, stream?.url, session.canPlay) {
        if (player == null || stream == null || !session.canPlay) return@LaunchedEffect
        when (playerState) {
            Player.STATE_ENDED -> actions.recoverPlatformLiveChannel()
            Player.STATE_BUFFERING -> {
                delay(8_000L)
                if (player.playbackState == Player.STATE_BUFFERING) {
                    playbackError = "直播连接中断，正在重新获取播放地址"
                    badgeVisible = true
                    actions.recoverPlatformLiveChannel()
                }
            }
        }
    }

    LaunchedEffect(Unit) { playerFocusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        actions.playPreviousPlatformLiveRoom()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        actions.playNextPlatformLiveRoom()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    -> {
                        if (session.interrupted && player != null) {
                            session.play(player)
                            actions.reconnectPlatformLiveChannel()
                        } else if (state.platformLiveError != null) actions.reconnectPlatformLiveChannel()
                        badgeVisible = true
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        if (player != null) {
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
        }
        if (session.interrupted && player != null) {
            ControlButton("继续播放", {
                session.play(player)
                actions.reconnectPlatformLiveChannel()
            }, Modifier.align(Alignment.Center))
        }
        if (badgeVisible || state.platformLiveResolving || state.platformLiveError != null) {
            PlatformLivePlayerBadge(
                room = room,
                stream = stream,
                error = state.platformLiveError ?: playbackError,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(28.dp),
            )
        }
        if (state.platformLiveResolving) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text("正在解析 ${room.title}", color = Color.White)
            }
        }
    }
}

@Composable
private fun PlatformLivePlayerBadge(
    room: PlatformLiveRoom,
    stream: ResolvedPlatformLiveStream?,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color(0x99000000))
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            text = stream?.title ?: room.title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stream?.let {
                "${it.quality} / ${it.protocol.uppercase()} / ${it.activeCandidate.cdn} ${it.activeCandidateIndex + 1}/${it.candidates.size}"
            } ?: room.anchor,
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatOnline(value: Int): String {
    return when {
        value <= 0 -> "正在直播"
        value < 10_000 -> "$value 人气"
        else -> "${value / 10_000}.${(value % 10_000) / 1_000} 万人气"
    }
}

private const val PLATFORM_LIVE_BADGE_HIDE_DELAY_MS = 2_000L

