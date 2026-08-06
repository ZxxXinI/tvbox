package com.tvbox.app.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.withFrameNanos
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.tvbox.app.domain.PlatformLiveCategory
import com.tvbox.app.domain.PlatformLiveParentCategory
import com.tvbox.app.domain.PlatformLiveRoom
import com.tvbox.app.domain.PlatformLiveSite
import com.tvbox.app.domain.ResolvedPlatformLiveStream
import com.tvbox.app.ui.components.ErrorState
import com.tvbox.app.ui.components.LoadingState
import com.tvbox.app.ui.components.PageSurface
import com.tvbox.app.ui.components.tvFocusScale
import com.tvbox.app.ui.theme.TvLayout
import kotlinx.coroutines.delay

@Composable
fun PlatformLiveScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    BackHandler { actions.goBack() }
    when (state.platformLiveDestination) {
        PlatformLiveDestination.Sites -> PlatformLiveSitesScreen(state = state, actions = actions)
        PlatformLiveDestination.ParentCategories -> PlatformLiveParentCategoriesScreen(state = state, actions = actions)
        PlatformLiveDestination.Categories -> PlatformLiveCategoriesScreen(state = state, actions = actions)
        PlatformLiveDestination.Rooms -> PlatformLiveRoomsScreen(state = state, actions = actions)
        PlatformLiveDestination.Player -> PlatformLivePlayerScreen(state = state, actions = actions)
    }
}

@Composable
private fun PlatformLiveSitesScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    val firstCardFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.platformLiveSites) {
        if (state.platformLiveSites.isNotEmpty()) firstCardFocusRequester.requestFocus()
    }
    PlatformLiveBrowseSurface(
        title = "直播",
        subtitle = "选择直播平台",
        loading = state.platformLiveLoading,
        error = state.platformLiveError,
        isEmpty = state.platformLiveSites.isEmpty(),
        emptyMessage = "没有可用平台直播服务",
        onRetry = actions::refreshPlatformLive,
    ) {
        itemsIndexed(state.platformLiveSites, key = { _, site -> site.id }) { index, site ->
            PlatformLiveSiteCard(
                site = site,
                onClick = { actions.selectPlatformLiveSite(site) },
                modifier = if (index == 0) Modifier.focusRequester(firstCardFocusRequester) else Modifier,
            )
        }
    }
}

@Composable
private fun PlatformLiveParentCategoriesScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    val firstCardFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.platformLiveParentCategories) {
        if (state.platformLiveParentCategories.isNotEmpty()) firstCardFocusRequester.requestFocus()
    }
    val site = state.platformLiveSelectedSite
    PlatformLiveBrowseSurface(
        title = site?.name ?: "直播",
        subtitle = "选择直播大类",
        loading = state.platformLiveLoading,
        error = state.platformLiveError,
        isEmpty = state.platformLiveParentCategories.isEmpty(),
        emptyMessage = "没有可用直播大分类",
        onRetry = actions::refreshPlatformLive,
    ) {
        itemsIndexed(
            state.platformLiveParentCategories,
            key = { _, category -> category.id },
        ) { index, category ->
            val childCount = state.platformLiveCategories.count { it.parentId == category.id }
            PlatformLiveParentCategoryCard(
                category = category,
                childCount = childCount,
                onClick = { actions.selectPlatformLiveParentCategory(category) },
                modifier = if (index == 0) Modifier.focusRequester(firstCardFocusRequester) else Modifier,
            )
        }
    }
}

@Composable
private fun PlatformLiveCategoriesScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    val firstCardFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.platformLiveCategories) {
        if (state.platformLiveCategories.isNotEmpty()) firstCardFocusRequester.requestFocus()
    }
    val site = state.platformLiveSelectedSite
    val parentCategory = state.platformLiveSelectedParentCategory
    val categories = state.platformLiveCategories.filter { it.parentId == parentCategory?.id }
    PlatformLiveBrowseSurface(
        title = parentCategory?.name ?: site?.name ?: "直播分类",
        subtitle = "选择二级分类",
        loading = state.platformLiveLoading,
        error = state.platformLiveError,
        isEmpty = categories.isEmpty(),
        emptyMessage = "没有可用二级分类",
        onRetry = actions::refreshPlatformLive,
    ) {
        itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
            PlatformLiveCategoryCard(
                category = category,
                onClick = { actions.selectPlatformLiveCategory(category) },
                modifier = if (index == 0) Modifier.focusRequester(firstCardFocusRequester) else Modifier,
            )
        }
    }
}

@Composable
private fun PlatformLiveRoomsScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    val gridState = rememberLazyGridState()
    val selectedRoomFocusRequester = remember { FocusRequester() }
    val selectedRoomIndex = state.platformLiveRoomIndex.coerceIn(
        0,
        state.platformLiveRooms.lastIndex.coerceAtLeast(0),
    )
    LaunchedEffect(
        state.platformLiveDestination,
        state.platformLiveRoomIndex,
        state.platformLiveRooms.isNotEmpty(),
    ) {
        if (state.platformLiveRooms.isEmpty()) return@LaunchedEffect
        gridState.scrollToItem(selectedRoomIndex)
        withFrameNanos { }
        runCatching { selectedRoomFocusRequester.requestFocus() }
    }
    val category = state.platformLiveSelectedCategory
    val site = state.platformLiveSelectedSite
    PlatformLiveBrowseSurface(
        title = category?.name ?: "直播间",
        subtitle = category?.parentName?.ifBlank { site?.name ?: "直播" } ?: site?.name ?: "直播",
        loading = state.platformLiveLoading,
        error = state.platformLiveError,
        isEmpty = state.platformLiveRooms.isEmpty(),
        emptyMessage = "这个分类暂时没有正在直播的房间",
        onRetry = actions::refreshPlatformLive,
        gridState = gridState,
    ) {
        itemsIndexed(state.platformLiveRooms, key = { _, room -> room.id }) { index, room ->
            PlatformLiveRoomCard(
                room = room,
                onClick = { actions.openPlatformLiveRoom(room) },
                modifier = if (index == selectedRoomIndex) {
                    Modifier.focusRequester(selectedRoomFocusRequester)
                } else {
                    Modifier
                },
            )
        }
        if (state.platformLiveRoomPage < state.platformLiveRoomPageCount) {
            item(key = "load-more") {
                PlatformLiveLoadMoreCard(
                    loading = state.platformLiveLoadingMore,
                    text = "加载更多（${state.platformLiveRoomPage}/${state.platformLiveRoomPageCount}）",
                    onClick = actions::loadMorePlatformLiveRooms,
                )
            }
        }
    }
}

@Composable
private fun PlatformLiveBrowseSurface(
    title: String,
    subtitle: String,
    loading: Boolean,
    error: String?,
    isEmpty: Boolean,
    emptyMessage: String,
    onRetry: () -> Unit,
    gridState: LazyGridState? = null,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit,
) {
    PageSurface { padding ->
        when {
            loading -> LoadingState(text = "正在加载$title", modifier = Modifier.padding(padding))
            error != null -> ErrorState(message = error, onRetry = onRetry, modifier = Modifier.padding(padding))
            isEmpty -> ErrorState(message = emptyMessage, onRetry = onRetry, modifier = Modifier.padding(padding))
            else -> {
                val resolvedGridState = gridState ?: rememberLazyGridState()
                val headerVisible by remember {
                    derivedStateOf {
                        resolvedGridState.firstVisibleItemIndex == 0 && resolvedGridState.firstVisibleItemScrollOffset < 24
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 28.dp, vertical = 18.dp),
                ) {
                    AnimatedVisibility(visible = headerVisible) {
                        Column {
                            Text(text = title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = subtitle,
                                modifier = Modifier.padding(top = 5.dp, bottom = 18.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    LazyVerticalGrid(
                        state = resolvedGridState,
                        columns = GridCells.Fixed(TvLayout.PlatformLiveColumns),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformLiveSiteCard(
    site: PlatformLiveSite,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformLiveTextCard(
        title = site.name,
        subtitle = site.description.ifBlank { "直播分类与房间浏览" },
        label = "直播平台",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun PlatformLiveParentCategoryCard(
    category: PlatformLiveParentCategory,
    childCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformLiveTextCard(
        title = category.name,
        subtitle = if (childCount > 0) "$childCount 个二级分类" else "选择二级分类",
        label = "",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun PlatformLiveCategoryCard(
    category: PlatformLiveCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformLiveImageCard(
        title = category.name,
        subtitle = category.parentName.ifBlank { "直播分类" },
        imageUrl = category.cover,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun PlatformLiveRoomCard(
    room: PlatformLiveRoom,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlatformLiveImageCard(
        title = room.title,
        subtitle = listOf(room.anchor, formatOnline(room.online)).filter { it.isNotBlank() }.joinToString(" / "),
        imageUrl = room.cover,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun PlatformLiveTextCard(
    title: String,
    subtitle: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusScale(shape = shape)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .background(if (focused) Color(0xFF252525) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .focusable()
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (label.isNotBlank()) {
            Text(text = label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlatformLiveImageCard(
    title: String,
    subtitle: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusScale(shape = shape)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .background(if (focused) Color(0xFF252525) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .focusable(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = "直播",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle.ifBlank { "正在直播" },
            modifier = Modifier.padding(start = 12.dp, top = 3.dp, end = 12.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            .clickable(enabled = !loading, onClick = onClick)
            .focusable()
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
    var playbackError by remember(stream?.url) { mutableStateOf<String?>(null) }
    var playerState by remember(stream?.url) { mutableIntStateOf(Player.STATE_IDLE) }
    var badgeVisible by remember { mutableStateOf(true) }

    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
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
        player.play()
        delay(PLATFORM_LIVE_BADGE_HIDE_DELAY_MS)
        badgeVisible = false
    }

    LaunchedEffect(playerState, player, stream?.url) {
        if (player == null || stream == null) return@LaunchedEffect
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
                        if (state.platformLiveError != null) actions.reconnectPlatformLiveChannel()
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

