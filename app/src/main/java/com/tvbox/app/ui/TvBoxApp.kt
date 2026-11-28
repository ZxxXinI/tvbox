package com.tvbox.app.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateHolder
import com.tvbox.app.domain.continueWatching
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.tvbox.app.domain.Category
import com.tvbox.app.domain.AiProviders
import com.tvbox.app.domain.TvTheme
import com.tvbox.app.ui.components.AppHeader
import com.tvbox.app.ui.components.AppNavigationRail
import com.tvbox.app.ui.components.CategoryPill
import com.tvbox.app.ui.components.DoubanHotPosterCard
import com.tvbox.app.ui.components.ErrorState
import com.tvbox.app.ui.components.HistoryItemCard
import com.tvbox.app.ui.components.LoadingState
import com.tvbox.app.ui.components.MoviePosterCard
import com.tvbox.app.ui.components.PageSurface
import com.tvbox.app.ui.components.PosterImage
import com.tvbox.app.ui.components.tvFocusScale
import com.tvbox.app.ui.theme.TvColors
import com.tvbox.app.ui.theme.TvDimens
import com.tvbox.app.ui.theme.TvLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun TvBoxApp(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
    onStartAiVoiceInput: () -> Unit = {},
    onStartUpdateDownload: () -> Unit = actions::startUpdateDownload,
    onInstallUpdate: (String) -> Unit = {},
    onOpenUpdateSettings: () -> Unit = {},
    onSaveUpdateApk: (String) -> Unit = {},
) {
    val screenStateHolder = rememberSaveableStateHolder()
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.showCinemaNavigation) {
            Row(modifier = Modifier.fillMaxSize()) {
                AppNavigationRail(
                    screen = state.screen,
                    onHome = actions::openHome,
                    onAiRecommend = actions::openAiRecommend,
                    onLive = actions::openLive,
                    onPlatformLive = actions::openPlatformLive,
                    onSettings = actions::openSettings,
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(TvColors.TextTertiary.copy(alpha = 0.55f)),
                )
                Box(modifier = Modifier.weight(1f)) {
                    TvBoxScreen(
                        holder = screenStateHolder,
                        state = state,
                        actions = actions,
                        onStartAiVoiceInput = onStartAiVoiceInput,
                    )
                }
            }
        } else {
            TvBoxScreen(
                holder = screenStateHolder,
                state = state,
                actions = actions,
                onStartAiVoiceInput = onStartAiVoiceInput,
            )
        }
        val picker = state.homeSourcePicker
        if (picker != null) {
            HomeSourcePicker(
                picker = picker,
                lines = state.apiLines,
                current = state.preferredHomeApiLine,
                onScan = actions::startHomeSourceProbe,
                onSelect = actions::selectHomeSource,
                onDismiss = actions::dismissHomeSourcePicker,
            )
        } else {
            AppUpdateDialog(
                state = state,
                actions = actions,
                onStartUpdateDownload = onStartUpdateDownload,
                onInstallUpdate = onInstallUpdate,
                onSaveUpdateApk = onSaveUpdateApk,
            )
        }
        UpdateDownloadCard(
            state = state,
            onRetry = onStartUpdateDownload,
            onInstall = onInstallUpdate,
            onOpenSettings = onOpenUpdateSettings,
            onSaveApk = onSaveUpdateApk,
            modifier = Modifier.align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        )
    }
}

@Composable
private fun UpdateDownloadCard(
    state: TvBoxUiState,
    onRetry: () -> Unit,
    onInstall: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onSaveApk: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val update = state.availableUpdate ?: return
    if (state.updateDialogVisible || (!state.updateDownloading && state.updateDownloadedApkPath == null && state.updateError == null)) return
    Surface(
        modifier = modifier.width(240.dp), shape = RoundedCornerShape(14.dp),
        color = Color(0xEE151C19), contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("更新 ${update.versionName}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                if (state.updateDownloading) Text(state.updateDownloadProgress?.let { "$it%" } ?: "下载中",
                    style = MaterialTheme.typography.labelMedium, color = Color(0xFF7AE2BA))
            }
            if (state.updateDownloading) {
                val progress = state.updateDownloadProgress
                if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF7AE2BA), trackColor = Color.White.copy(alpha = 0.15f))
                Text("可继续使用，完成后打开安装界面", style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f))
            } else if (state.updateDownloadedApkPath != null) {
                Text(if (state.updateAwaitingInstallPermission) "下载完成，等待安装授权" else "下载完成",
                    style = MaterialTheme.typography.bodySmall)
                state.updateError?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
                state.updateExportMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
                TextButton(onClick = { onInstall(state.updateDownloadedApkPath) }, enabled = !state.updateExporting) {
                    Text(when {
                        state.updateAwaitingInstallPermission -> "授权并安装"
                        state.updateError != null -> "重试安装"
                        else -> "安装更新"
                    }, color = Color.White)
                }
                TextButton(onClick = { onSaveApk(state.updateDownloadedApkPath) }, enabled = !state.updateExporting) {
                    Text(if (state.updateExporting) "正在保存…" else "保存安装包", color = Color.White)
                }
                if (state.updateAwaitingInstallPermission) {
                    TextButton(onClick = onOpenSettings) { Text("系统设置", color = Color.White) }
                }
            } else {
                Text(state.updateError.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                TextButton(onClick = onRetry) { Text("重新下载", color = Color.White) }
            }
        }
    }
}

@Composable
private fun TvBoxScreen(
    holder: SaveableStateHolder,
    state: TvBoxUiState,
    actions: TvBoxViewModel,
    onStartAiVoiceInput: () -> Unit,
) {
    holder.SaveableStateProvider(state.screen.name) {
    when (state.screen) {
        TvScreen.Home -> HomeScreen(state = state, actions = actions)
        TvScreen.History -> HistoryScreen(state = state, actions = actions)
        TvScreen.Search -> SearchScreen(state = state, actions = actions)
        TvScreen.Detail -> DetailScreen(state = state, actions = actions)
        TvScreen.Player -> PlayerScreen(state = state, actions = actions)
        TvScreen.Live -> LiveScreen(state = state, actions = actions)
        TvScreen.PlatformLive -> PlatformLiveScreen(state = state, actions = actions)
        TvScreen.Settings -> SettingsScreen(state = state, actions = actions)
        TvScreen.AiRecommend -> AiRecommendScreen(
            state = state,
            actions = actions,
            onStartVoiceInput = onStartAiVoiceInput,
        )
    }
}
}

@Composable
private fun AppUpdateDialog(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
    onStartUpdateDownload: () -> Unit,
    onInstallUpdate: (String) -> Unit,
    onSaveUpdateApk: (String) -> Unit,
) {
    val update = state.availableUpdate ?: return
    if (!state.updateDialogVisible) return
    val downloadedApkPath = state.updateDownloadedApkPath
    AlertDialog(
        onDismissRequest = actions::dismissUpdateDialog,
        title = {
            Text("发现新版本 ${update.versionName}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "当前版本可更新到 ${update.versionName}，请选择是否现在更新。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (update.changelog.isNotEmpty()) {
                    Text(
                        text = update.changelog.joinToString(separator = "\n") { "- $it" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.updateDownloading) {
                    val progress = state.updateDownloadProgress
                    if (progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("下载进度 $progress%")
                    }
                }
                if (downloadedApkPath != null) {
                    Text(
                        text = "安装包已下载完成，可直接安装或保存后手动安装。",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { onSaveUpdateApk(downloadedApkPath) }, enabled = !state.updateExporting) {
                        Text(if (state.updateExporting) "正在保存…" else "保存安装包")
                    }
                    state.updateExportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                if (state.updateError != null) {
                    Text(
                        text = state.updateError,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !state.updateDownloading && !state.updateExporting,
                onClick = {
                    if (downloadedApkPath != null) {
                        onInstallUpdate(downloadedApkPath)
                    } else {
                        onStartUpdateDownload()
                    }
                },
            ) {
                Text(
                    when {
                        state.updateDownloading -> "下载中"
                        downloadedApkPath != null && state.updateAwaitingInstallPermission -> "授权并安装"
                        downloadedApkPath != null && state.updateError != null -> "重试安装"
                        downloadedApkPath != null -> "安装更新"
                        state.updateError != null -> "重新下载"
                        else -> "立即更新"
                    },
                )
            }
        },
        dismissButton = if (!update.force) {
            {
                TextButton(
                    enabled = !state.updateDownloading,
                    onClick = actions::dismissUpdateDialog,
                ) {
                    Text("稍后再说")
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun HomeScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    PageSurface { padding ->
        val homePadding = if (state.appSettings.theme == TvTheme.Default) {
            PaddingValues(start = TvDimens.PageHorizontalPadding, top = 20.dp, end = TvDimens.PageHorizontalPadding)
        } else padding
        BoxWithConstraints(Modifier.fillMaxSize().padding(homePadding)) {
            HomeContent(state, actions, maxWidth)
        }
    }
}

@Composable
private fun HomeContent(state: TvBoxUiState, actions: TvBoxViewModel, contentWidth: Dp) {
    val apiLineName = state.selectedApiLine?.name ?: "资源"
    val cinemaTheme = state.appSettings.theme == TvTheme.Cinema
    val homeSubtitle = when {
        state.isShowingDoubanHot -> "豆瓣热播 / 共 ${state.total} 部"
        state.doubanHotFallbackActive -> "$apiLineName 最近更新 / 共 ${state.total} 部"
        else -> "$apiLineName 数据 / 共 ${state.total} 部影片"
    }
    val allCategoryFocusRequester = remember { FocusRequester() }
    val navigationFocusRequester = remember { FocusRequester() }
    val navigationScope = rememberCoroutineScope()
    var forceNavigationVisible by remember { mutableStateOf(false) }
    var movingToNavigation by remember { mutableStateOf(false) }
    var navigationUpHeld by remember { mutableStateOf(false) }
    val movieGridState = rememberLazyGridState()
    val homeFocus = rememberReturnFocus()
    val homeTopContentVisible by remember {
        derivedStateOf { movieGridState.isAtStart() }
    }
    val showHomeTopContent = forceNavigationVisible || homeTopContentVisible || state.homeLoading || state.homeError != null
    LaunchedEffect(Unit) {
        if (homeFocus.lastKey == null) runCatching { allCategoryFocusRequester.requestFocus() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP && navigationUpHeld) {
                    if (event.type == KeyEventType.KeyUp) navigationUpHeld = false
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_1,
                    AndroidKeyEvent.KEYCODE_NUMPAD_1,
                    -> {
                        actions.openHistory()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_2,
                    AndroidKeyEvent.KEYCODE_NUMPAD_2,
                    -> {
                        actions.openSearch()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_3,
                    AndroidKeyEvent.KEYCODE_NUMPAD_3,
                    -> {
                        actions.openAiRecommend()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_4,
                    AndroidKeyEvent.KEYCODE_NUMPAD_4,
                    -> {
                        actions.openLive()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_5,
                    AndroidKeyEvent.KEYCODE_NUMPAD_5,
                    -> {
                        actions.openPlatformLive()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_6,
                    AndroidKeyEvent.KEYCODE_NUMPAD_6,
                    -> {
                        actions.openSettings()
                        true
                    }
                    else -> false
                }
            },
    ) {
        AnimatedVisibility(visible = showHomeTopContent) {
            Column {
                val firstActionModifier = Modifier.focusRequester(navigationFocusRequester)
                if (cinemaTheme) {
                    AppHeader(
                        title = "首页",
                        subtitle = homeSubtitle,
                        onHistory = actions::openHistory,
                        onSearch = actions::openSearch,
                        firstActionModifier = firstActionModifier,
                    )
                } else {
                    DefaultHomeHeader(
                        sourceName = state.selectedApiLine?.name,
                        fontScale = state.appSettings.fontScale.typographyScale,
                        actions = actions,
                        firstActionModifier = firstActionModifier,
                    )
                }
                if (cinemaTheme && !state.isShowingDoubanHot) {
                    state.movies.firstOrNull()?.let { featuredMovie ->
                        FeaturedMovieHero(
                            movie = featuredMovie,
                            onOpen = { actions.openDetail(featuredMovie.id) },
                        )
                        Spacer(modifier = Modifier.height(TvDimens.SectionGap))
                    }
                }
                HomeCategoryRows(
                    state = state,
                    contentWidth = contentWidth,
                    onHot = actions::selectDoubanHot,
                    onParent = actions::selectParentCategory,
                    onChild = actions::selectChildCategory,
                    hotCategoryModifier = Modifier.focusRequester(allCategoryFocusRequester),
                )
                Spacer(modifier = Modifier.height(if (cinemaTheme) 20.dp else 14.dp))
            }
        }
        MovieGrid(
            state = state,
            gridState = movieGridState,
            onMovieClick = actions::openDetail,
            onDoubanHotClick = actions::openDoubanHotDetail,
            onLoadMore = actions::loadNextPage,
            onRetryHome = { actions.refreshHome(forceRefresh = true) },
            focus = homeFocus,
            onContinue = { actions.resumeHistory(it, TvScreen.Home) },
            onMoveToNavigation = {
                navigationUpHeld = true
                forceNavigationVisible = true
                if (!movingToNavigation) {
                    movingToNavigation = true
                    navigationScope.launch {
                        try {
                            movieGridState.scrollToItem(0, 0)
                            // The navigation can be outside composition until AnimatedVisibility expands it.
                            for (attempt in 0..20) {
                                withFrameNanos { }
                                if (runCatching { navigationFocusRequester.requestFocus() }.getOrDefault(false)) break
                            }
                        } finally {
                            movingToNavigation = false
                        }
                    }
                }
            },
            onContentFocused = { if (!movingToNavigation) forceNavigationVisible = false },
            contentWidth = contentWidth,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FeaturedMovieHero(
    movie: com.tvbox.app.domain.Movie,
    onOpen: () -> Unit,
) {
    val heroShape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 165.dp, max = 190.dp)
            .clip(heroShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(TvColors.Surface, TvColors.SurfaceOverlay, Color.Transparent),
                ),
            ),
    ) {
        PosterImage(
            movie = movie,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.38f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            TvColors.Surface,
                            TvColors.SurfaceOverlay.copy(alpha = 0.82f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "今日热播",
                color = TvColors.AccentStrong,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = movie.name,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(movie.year, movie.typeName, movie.remarks)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString("  ·  "),
                color = TvColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (movie.description.isNotBlank()) {
                Text(
                    text = movie.description,
                    color = TvColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen) {
                    Text("播放")
                }
                TextButton(onClick = onOpen) {
                    Text("详情")
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    PageSurface { padding ->
        var confirmClearHistory by remember { mutableStateOf(false) }
        val historyGrid = rememberLazyGridState()
        val historyFocus = rememberReturnFocus()
        RestoreGridFocus(historyFocus, historyGrid, state.historyItems.mapIndexed { index, item -> "${item.apiLineId}:${item.movieId}" to index })
        if (confirmClearHistory) {
            AlertDialog(
                onDismissRequest = { confirmClearHistory = false },
                title = { Text("清空历史") },
                text = { Text("确定要清空全部观看历史吗？清空后无法从历史页继续播放。") },
                confirmButton = {
                    Button(
                        onClick = {
                            confirmClearHistory = false
                            actions.clearHistory()
                        },
                    ) {
                        Text("清空")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearHistory = false }) {
                        Text("取消")
                    }
                },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "历史",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = "观看记录：继续上次的线路、集数和播放进度",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = state.historyItems.isNotEmpty(),
                        onClick = { confirmClearHistory = true },
                    ) {
                        Text("清空历史")
                    }
                    Button(onClick = actions::goBack) {
                        Text("返回")
                    }
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            if (state.historyItems.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("暂无观看历史", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = actions::goBack) {
                        Text("去看影片")
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = TvLayout.HistoryGridMinWidth),
                    state = historyGrid,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.historyItems, key = { "${it.apiLineId}:${it.movieId}" }) { item ->
                        HistoryItemCard(item = item, onClick = {
                            historyFocus.mark("${item.apiLineId}:${item.movieId}", state.historyItems.indexOf(item))
                            actions.resumeHistory(item, TvScreen.History)
                        },
                            modifier = historyFocus.modifier("${item.apiLineId}:${item.movieId}", state.historyItems.indexOf(item)))
                    }
                }
            }
        }
    }
}

@Composable
internal fun AiConfigDialog(state: TvBoxUiState, actions: TvBoxViewModel) {
    val isCustom = state.appSettings.aiProviderId == AiProviders.custom.id
    PhoneConfigDialog(
        title = if (isCustom) "手机扫码配置大模型" else "手机扫码配置 AI",
        instructions = if (isCustom) "在手机上填写 API 地址和 Key，然后获取或手动填写模型。"
            else "在手机上填写模型名称和 API Key，提交后同步到电视。",
        url = state.aiConfigServerUrl,
        savedMessage = state.aiConfigSavedMessage,
        error = state.aiConfigServerError,
        onRegenerate = actions::openAiConfigDialog,
        onClose = actions::closeAiConfigDialog,
    )
}

@Composable
internal fun VideoApiConfigDialog(state: TvBoxUiState, actions: TvBoxViewModel) {
    PhoneConfigDialog(
        title = "手机扫码添加视频接口",
        instructions = "在手机上填写接口名称和 MacCms 地址，提交后添加到电视的视频接口列表。",
        url = state.videoApiConfigServerUrl,
        savedMessage = state.videoApiConfigSavedMessage,
        error = state.videoApiConfigServerError,
        onRegenerate = actions::openVideoApiConfigDialog,
        onClose = actions::closeVideoApiConfigDialog,
    )
}

@Composable
private fun PhoneConfigDialog(
    title: String,
    instructions: String,
    url: String?,
    savedMessage: String?,
    error: String?,
    onRegenerate: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            when {
                savedMessage != null -> Text(savedMessage, color = MaterialTheme.colorScheme.primary)
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                url == null -> Text("正在启动配置服务…")
                else -> Row(
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val qrBitmap = remember(url) { createQrBitmap(url, 320) }
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier.size(200.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(instructions, style = MaterialTheme.typography.bodyMedium)
                        Text("请使用同一局域网内的手机，二维码 5 分钟有效。")
                        Text(
                            text = url,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (error != null) SettingsActionButton(text = "重新生成", onClick = onRegenerate)
            else SettingsActionButton(text = if (savedMessage == null) "关闭" else "完成", onClick = onClose)
        },
        dismissButton = {
            if (error != null) TextButton(onClick = onClose) { Text("关闭") }
        },
    )
}

@Composable
internal fun SettingsActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .then(
                if (enabled) {
                    Modifier.tvFocusScale(
                        shape = shape,
                        idleBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .onFocusChanged { focused = enabled && (it.isFocused || it.hasFocus) }
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled = enabled),
        shape = shape,
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            focused -> MaterialTheme.colorScheme.primary
            selected -> TvColors.AccentSoft
            else -> TvColors.Surface
        },
        contentColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            focused -> MaterialTheme.colorScheme.onPrimary
            selected -> TvColors.AccentStrong
            else -> MaterialTheme.colorScheme.onSurface
        },
        tonalElevation = if (focused) 8.dp else 0.dp,
        shadowElevation = if (focused) 10.dp else 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeCategoryRows(
    state: TvBoxUiState,
    contentWidth: Dp,
    onHot: () -> Unit,
    onParent: (Int) -> Unit,
    onChild: (Int) -> Unit,
    hotCategoryModifier: Modifier = Modifier,
) {
    val defaultHome = state.appSettings.theme == TvTheme.Default
    val showCount = defaultHome && state.total > 0 && !state.homeLoading && state.homeError == null
    val inlineCount = showCount && contentWidth >= (640 * state.appSettings.fontScale.typographyScale).dp
    val parentCategories = state.categories.filter { it.parentId == 0 }
    val selectedParent = state.selectedParentCategoryId
        ?.let { parentId -> state.categories.firstOrNull { it.id == parentId } }
    val childCategories = selectedParent
        ?.let { parent -> state.categories.filter { it.parentId == parent.id } }
        .orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = if (defaultHome) 3.dp else 0.dp),
            ) {
                item {
                    HomeCategory(
                        label = "热播",
                        selected = state.homeFeedMode == HomeFeedMode.DoubanHot,
                        defaultHome = defaultHome,
                        onClick = onHot,
                        modifier = hotCategoryModifier,
                    )
                }
                items(parentCategories, key = { it.id }) { category ->
                    val label = if (defaultHome) when (category.name) {
                        "电影片", "综艺片", "动漫片" -> category.name.removeSuffix("片")
                        else -> category.name
                    } else category.name
                    HomeCategory(
                        label = label,
                        selected = state.selectedParentCategoryId == category.id,
                        defaultHome = defaultHome,
                        onClick = { onParent(category.id) },
                    )
                }
            }
            if (inlineCount) DefaultHomeFeedCount(state.total, state.page)
        }
        if (selectedParent != null && childCategories.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    HomeCategory(
                        label = selectedParent.allChildrenLabel(),
                        selected = state.selectedCategoryId == null,
                        defaultHome = defaultHome,
                        onClick = { onParent(selectedParent.id) },
                    )
                }
                items(childCategories, key = { it.id }) { category ->
                    HomeCategory(
                        label = category.name,
                        selected = state.selectedCategoryId == category.id,
                        defaultHome = defaultHome,
                        onClick = { onChild(category.id) },
                    )
                }
            }
        }
        if (showCount && !inlineCount) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                DefaultHomeFeedCount(state.total, state.page)
            }
        }
    }
}

@Composable
private fun HomeCategory(
    label: String,
    selected: Boolean,
    defaultHome: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (defaultHome) DefaultHomeCategory(label, selected, onClick, modifier)
    else CategoryPill(label, selected, onClick, modifier)
}

private fun LazyGridState.isAtStart(): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

private fun Modifier.homeNavigationUp(
    enabled: () -> Boolean,
    onMoveToNavigation: () -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    val keyEvent = event.nativeKeyEvent
    if (keyEvent.keyCode != AndroidKeyEvent.KEYCODE_DPAD_UP || !enabled()) {
        false
    } else {
        if (event.type == KeyEventType.KeyDown && keyEvent.repeatCount == 0) onMoveToNavigation()
        true
    }
}

@Composable
private fun MovieGrid(
    state: TvBoxUiState,
    gridState: LazyGridState,
    onMovieClick: (Int) -> Unit,
    onDoubanHotClick: (com.tvbox.app.domain.DoubanHotItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetryHome: () -> Unit,
    contentWidth: Dp,
    focus: ReturnFocus,
    onContinue: (com.tvbox.app.domain.WatchHistoryItem) -> Unit,
    onMoveToNavigation: () -> Unit,
    onContentFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var loadMoreFocused by remember { mutableStateOf(false) }
    val contextKey = "${state.selectedApiLineId}:${state.homeFeedMode}:${state.selectedParentCategoryId}:${state.selectedCategoryId}"
    var savedContext by rememberSaveable { mutableStateOf(contextKey) }
    LaunchedEffect(contextKey) {
        if (savedContext != contextKey) {
            if (focus.lastKey?.startsWith("continue:") != true) focus.reset()
            gridState.scrollToItem(0)
            savedContext = contextKey
        }
        loadMoreFocused = false
    }
    val recent = state.historyItems.continueWatching()
    val feedUnavailable = state.homeLoading || state.homeError != null
    val defaultHome = state.appSettings.theme == TvTheme.Default
    val showFeedTitle = defaultHome && !feedUnavailable && state.homeItemCount > 0
    val itemOffset = listOf(recent.isNotEmpty(), state.homeNotice != null, showFeedTitle).count { it }
    val gridGap = if (defaultHome) 16.dp else 18.dp
    val minPosterWidth = TvLayout.PosterGridMinWidth
    val posterSlots = ((contentWidth + gridGap) / (minPosterWidth * 0.9f + gridGap)).toInt().coerceIn(1, 6)
    val columns = if (defaultHome) GridCells.Fixed(posterSlots) else GridCells.Adaptive(minPosterWidth)
    val resumeGap = if (defaultHome) 16.dp else 12.dp
    val resumeSlots = ((contentWidth + resumeGap) / (144.dp + resumeGap)).toInt().coerceIn(1, 4)
    val resumeWidth = if (defaultHome) (contentWidth - resumeGap * (resumeSlots - 1)) / resumeSlots else 220.dp
    val focusItems = recent.map { "continue:${it.apiLineId}:${it.movieId}" to 0 } +
        if (state.isShowingDoubanHot) state.doubanHotMovies.mapIndexed { index, item -> "douban:${item.doubanId}" to index + itemOffset }
        else state.movies.mapIndexed { index, movie -> "${movie.apiLineId}:${movie.id}" to index + itemOffset }
    RestoreGridFocus(focus, gridState, focusItems)
    fun posterFocusModifier(key: String, index: Int): Modifier {
        val modifier = focus.modifier(key, index + itemOffset)
            .onFocusChanged { if (it.hasFocus) onContentFocused() }
        // A scrolled-away heading can also hide the navigation when the history strip is absent.
        return if (defaultHome && recent.isEmpty() && index < posterSlots) {
            modifier.homeNavigationUp(enabled = { !gridState.isAtStart() }, onMoveToNavigation)
        } else modifier
    }
    Column(modifier = modifier.fillMaxSize()) {
        if (!defaultHome && !feedUnavailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.isShowingDoubanHot) "豆瓣热播" else "正在热播",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${state.homeItemCount} / ${state.total} 部",
                    color = TvColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        LazyVerticalGrid(
            columns = columns,
            state = gridState,
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(gridGap),
            verticalArrangement = Arrangement.spacedBy(if (defaultHome) 14.dp else 22.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (recent.isNotEmpty()) {
                item(key = "continue-watching", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(bottom = if (defaultHome) 0.dp else 16.dp)) {
                        if (defaultHome) DefaultHomeSectionTitle("继续观看")
                        else Text("继续观看", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(if (defaultHome) 10.dp else 8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(resumeGap),
                            modifier = Modifier.homeNavigationUp(enabled = { true }, onMoveToNavigation).focusGroup(),
                        ) {
                            items(recent, key = { "${it.apiLineId}:${it.movieId}" }) { history ->
                                val key = "continue:${history.apiLineId}:${history.movieId}"
                                val open = {
                                    focus.mark(key, 0)
                                    onContinue(history)
                                }
                                val cardModifier = Modifier.width(resumeWidth).then(focus.modifier(key, 0))
                                    .onFocusChanged { if (it.hasFocus) onContentFocused() }
                                if (defaultHome) DefaultHomeResumeCard(history, open, cardModifier)
                                else HistoryItemCard(history, open, cardModifier)
                            }
                        }
                    }
                }
            }
            if (feedUnavailable) {
                item(key = "home-feed-status", span = { GridItemSpan(maxLineSpan) }) {
                    val statusModifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)
                    if (state.homeLoading) {
                        LoadingState("正在加载影片", statusModifier)
                    } else {
                        ErrorState(checkNotNull(state.homeError), onRetryHome, statusModifier)
                    }
                }
                return@LazyVerticalGrid
            }
            state.homeNotice?.let { notice ->
                item(key = "home-feed-notice", span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        color = TvColors.SurfaceRaised,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = notice,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = TvColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (showFeedTitle) {
                item(key = "home-feed-title", span = { GridItemSpan(maxLineSpan) }) {
                    DefaultHomeSectionTitle(if (state.isShowingDoubanHot) "豆瓣热播剧集" else "影视列表")
                }
            }
            if (state.isShowingDoubanHot) {
                itemsIndexed(state.doubanHotMovies, key = { _, item -> item.doubanId.ifBlank { item.title } }) { index, item ->
                    val key = "douban:${item.doubanId}"
                    val open = {
                        focus.mark(key, index + itemOffset)
                        onDoubanHotClick(item)
                    }
                    val cardModifier = posterFocusModifier(key, index)
                    val resolving = state.resolvingDoubanHotId == item.doubanId
                    if (defaultHome) DefaultHomeDoubanCard(item, resolving, open, cardModifier)
                    else DoubanHotPosterCard(item, resolving, open, cardModifier, posterAspectRatio = 0.78f)
                }
            } else {
                itemsIndexed(state.movies, key = { _, movie -> "${movie.apiLineId}-${movie.id}" }) { index, movie ->
                    val key = "${movie.apiLineId}:${movie.id}"
                    val open = {
                        focus.mark(key, index + itemOffset)
                        onMovieClick(movie.id)
                    }
                    val cardModifier = posterFocusModifier(key, index)
                    if (defaultHome) DefaultHomeMovieCard(movie, open, cardModifier)
                    else MoviePosterCard(movie, open, cardModifier, posterAspectRatio = 0.78f)
                }
            }
            if (state.canLoadMore || state.loadingMore) {
                item(key = "home-load-more", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Button(
                            onClick = {
                                if (!state.loadingMore) {
                                    onLoadMore()
                                }
                            },
                            modifier = Modifier.onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    if (!loadMoreFocused && state.canLoadMore && !state.homeLoadMoreFailed) {
                                        onLoadMore()
                                    }
                                    loadMoreFocused = true
                                } else {
                                    loadMoreFocused = false
                                }
                            },
                        ) {
                            Text(when {
                                state.loadingMore -> "加载中..."
                                state.homeLoadMoreFailed -> "加载失败，重试本页"
                                else -> "加载更多"
                            })
                        }
                    }
                }
            } else if (state.homeItemCount > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "已经到底了",
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = TvColors.TextSecondary,
                    )
                }
            }
        }
    }
}

private fun Category.allChildrenLabel(): String {
    val baseName = name.removeSuffix("片")
    return "全部$baseName"
}

internal fun createQrBitmap(content: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
