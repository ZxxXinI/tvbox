package com.tvbox.app.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.tvbox.app.domain.AiProvider
import com.tvbox.app.domain.AiProviders
import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.Category
import com.tvbox.app.domain.PlaybackHealthSnapshot
import com.tvbox.app.domain.TvFontScale
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
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val showNavigationRail = state.appSettings.theme == TvTheme.Cinema &&
            state.screen != TvScreen.Player &&
            state.screen != TvScreen.Live
        if (showNavigationRail) {
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
                        state = state,
                        actions = actions,
                        onStartAiVoiceInput = onStartAiVoiceInput,
                    )
                }
            }
        } else {
            TvBoxScreen(
                state = state,
                actions = actions,
                onStartAiVoiceInput = onStartAiVoiceInput,
            )
        }
        AppUpdateDialog(
            state = state,
            actions = actions,
            onStartUpdateDownload = onStartUpdateDownload,
            onInstallUpdate = onInstallUpdate,
        )
    }
}

@Composable
private fun TvBoxScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
    onStartAiVoiceInput: () -> Unit,
) {
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

@Composable
private fun AppUpdateDialog(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
    onStartUpdateDownload: () -> Unit,
    onInstallUpdate: (String) -> Unit,
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
                        text = "安装包已下载完成，请选择安装更新。",
                        color = MaterialTheme.colorScheme.primary,
                    )
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
                enabled = !state.updateDownloading,
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
        val apiLineName = state.selectedApiLine?.name ?: "资源"
        val cinemaTheme = state.appSettings.theme == TvTheme.Cinema
        val homeSubtitle = when {
            state.isShowingDoubanHot -> "豆瓣热播 / 共 ${state.total} 部"
            state.doubanHotFallbackActive -> "$apiLineName 最近更新 / 共 ${state.total} 部"
            else -> "$apiLineName 数据 / 共 ${state.total} 部影片"
        }
        val allCategoryFocusRequester = remember { FocusRequester() }
        val movieGridState = rememberLazyGridState()
        val homeTopContentVisible by remember {
            derivedStateOf {
                movieGridState.firstVisibleItemIndex == 0 &&
                    movieGridState.firstVisibleItemScrollOffset == 0
            }
        }
        val showHomeTopContent = homeTopContentVisible || state.homeLoading || state.homeError != null
        LaunchedEffect(Unit) {
            runCatching { allCategoryFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
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
                }
                .padding(padding),
        ) {
            AnimatedVisibility(visible = showHomeTopContent) {
                Column {
                    AppHeader(
                        title = if (cinemaTheme) "首页" else "TVBox",
                        subtitle = homeSubtitle,
                        onHistory = actions::openHistory,
                        onSearch = actions::openSearch,
                        onAiRecommend = actions::openAiRecommend,
                        onLive = actions::openLive,
                        onPlatformLive = actions::openPlatformLive,
                        onSettings = actions::openSettings,
                        showShortcutActions = !cinemaTheme,
                    )
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
                        onHot = actions::selectDoubanHot,
                        onParent = actions::selectParentCategory,
                        onChild = actions::selectChildCategory,
                        hotCategoryModifier = Modifier.focusRequester(allCategoryFocusRequester),
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
            when {
                state.homeLoading -> LoadingState(
                    text = "正在加载影片",
                    modifier = Modifier.weight(1f),
                )
                state.homeError != null -> ErrorState(
                    message = state.homeError,
                    onRetry = { actions.refreshHome(forceRefresh = true) },
                    modifier = Modifier.weight(1f),
                )
                else -> MovieGrid(
                    state = state,
                    gridState = movieGridState,
                    onMovieClick = actions::openDetail,
                    onDoubanHotClick = actions::openDoubanHotDetail,
                    onLoadMore = actions::loadNextPage,
                    showSectionHeader = cinemaTheme,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.historyItems, key = { "${it.apiLineId}-${it.movieId}-${it.updatedAtEpochMs}" }) { item ->
                        HistoryItemCard(item = item, onClick = { actions.resumeHistory(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    PageSurface { padding ->
        var confirmClearPlaybackStats by remember { mutableStateOf(false) }
        if (confirmClearPlaybackStats) {
            AlertDialog(
                onDismissRequest = { confirmClearPlaybackStats = false },
                title = { Text("清空线路统计") },
                text = { Text("确定要清空线路质量统计吗？清空后播放管家会重新学习线路表现。") },
                confirmButton = {
                    Button(
                        onClick = {
                            confirmClearPlaybackStats = false
                            actions.clearPlaybackStats()
                        },
                    ) {
                        Text("清空")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearPlaybackStats = false }) {
                        Text("取消")
                    }
                },
            )
        }
        if (state.aiConfigDialogVisible) {
            AiConfigDialog(state = state, actions = actions)
        }
        if (state.videoApiConfigDialogVisible) {
            VideoApiConfigDialog(state = state, actions = actions)
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
                        text = "设置",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = "启动更新检查",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsActionButton(text = "返回", onClick = actions::goBack)
            }
            Spacer(modifier = Modifier.height(22.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = TvLayout.SettingsGridMinWidth),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SettingsSectionTitle(
                        title = "外观",
                        subtitle = "默认主题保留原有界面；影院主题使用左侧图标导航和影院色板。",
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ThemeSetting(
                        selectedTheme = state.appSettings.theme,
                        onThemeSelect = actions::updateTheme,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FontScaleSetting(
                        selectedFontScale = state.appSettings.fontScale,
                        onFontScaleSelect = actions::updateFontScale,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SettingsSectionTitle(
                        title = "视频接口",
                        subtitle = "内置接口保留不变，可扫码添加 MacCms 自定义视频接口。",
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeApiLineSetting(
                        apiLines = state.apiLines,
                        selectedApiLine = state.selectedApiLine,
                        customLineCount = state.appSettings.customVideoApiLines.size,
                        onSelect = actions::updateHomeApiLine,
                        onOpenConfig = actions::openVideoApiConfigDialog,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SettingsSectionTitle(
                        title = "大模型",
                        subtitle = "未填写 API Key 时使用 APK 内置 AI 配置。",
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AiProviderSetting(
                        selectedProvider = AiProviders.find(state.appSettings.aiProviderId),
                        modelName = state.appSettings.aiModelName,
                        apiKey = state.appSettings.aiApiKey,
                        onProviderSelect = actions::updateAiProvider,
                        onOpenConfig = actions::openAiConfigDialog,
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SettingsSectionTitle(
                        title = "播放管家",
                        subtitle = "控制播放失败或卡顿时是否自动尝试其它线路。",
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自动换线",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (state.appSettings.playbackAgentAutoSwitchEnabled) {
                                    "播放失败或缓冲过久时自动切换线路"
                                } else {
                                    "已关闭，播放器仍可手动换线"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.appSettings.playbackAgentAutoSwitchEnabled,
                            onCheckedChange = actions::updatePlaybackAgentAutoSwitch,
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "线路质量统计",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = state.playbackHealth.qualitySummaryText(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SettingsActionButton(
                            text = "清空统计",
                            enabled = state.playbackHealth.entryCount > 0,
                            onClick = { confirmClearPlaybackStats = true },
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SettingsSectionTitle(
                        title = "更新",
                        subtitle = "控制应用启动时是否自动检查 GitHub Release 更新。",
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "启动时检查更新",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (state.appSettings.checkUpdatesOnStartup) "已开启" else "已关闭",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.updateError != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.updateError,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsActionButton(
                                text = if (state.updateChecking) "检查中" else "立即检查",
                                enabled = !state.updateChecking,
                                onClick = { actions.checkForAppUpdate(showError = true) },
                            )
                            Switch(
                                checked = state.appSettings.checkUpdatesOnStartup,
                                onCheckedChange = actions::updateStartupUpdateCheck,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiConfigDialog(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    val configUrl = state.aiConfigServerUrl
    AlertDialog(
        onDismissRequest = actions::closeAiConfigDialog,
        title = { Text("手机扫码配置 AI") },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (configUrl == null) {
                    Box(
                        modifier = Modifier.size(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("正在启动配置服务...")
                    }
                } else {
                    val qrBitmap = remember(configUrl) { createQrBitmap(configUrl, 320) }
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "AI 配置二维码",
                        modifier = Modifier.size(220.dp),
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "使用手机扫描二维码",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "或在同一局域网的手机浏览器访问地址：",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = configUrl ?: "正在生成地址...",
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "手机页面里填写模型名称和 API Key，确认后会自动同步到电视。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.aiConfigSavedMessage != null) {
                        Text(
                            text = state.aiConfigSavedMessage,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (state.aiConfigServerError != null) {
                        Text(
                            text = state.aiConfigServerError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            SettingsActionButton(text = "完成", onClick = actions::closeAiConfigDialog)
        },
        dismissButton = {
            TextButton(onClick = actions::closeAiConfigDialog) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun VideoApiConfigDialog(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    val configUrl = state.videoApiConfigServerUrl
    AlertDialog(
        onDismissRequest = actions::closeVideoApiConfigDialog,
        title = { Text("手机扫码添加视频接口") },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (configUrl == null) {
                    Box(
                        modifier = Modifier.size(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("正在启动配置服务...")
                    }
                } else {
                    val qrBitmap = remember(configUrl) { createQrBitmap(configUrl, 320) }
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "视频接口配置二维码",
                        modifier = Modifier.size(220.dp),
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "使用手机扫描二维码",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "或在同一局域网的手机浏览器访问地址：",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = configUrl ?: "正在生成地址...",
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "手机页面里填写接口名称和 MacCms 地址，确认后会自动添加到电视的视频接口列表。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "示例：https://example.com/api.php/provide/vod",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state.videoApiConfigSavedMessage != null) {
                        Text(
                            text = state.videoApiConfigSavedMessage,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (state.videoApiConfigServerError != null) {
                        Text(
                            text = state.videoApiConfigServerError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            SettingsActionButton(text = "完成", onClick = actions::closeVideoApiConfigDialog)
        },
        dismissButton = {
            TextButton(onClick = actions::closeVideoApiConfigDialog) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun AiProviderSetting(
    selectedProvider: AiProvider,
    modelName: String,
    apiKey: String,
    onProviderSelect: (String) -> Unit,
    onOpenConfig: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayModelName = modelName.ifBlank { selectedProvider.defaultModel }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(0.9f)) {
            Text(
                text = "大模型选择",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                SettingsActionButton(
                    text = "${selectedProvider.name} ▾",
                    onClick = { expanded = true },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    AiProviders.all.forEach { provider ->
                        val selected = provider.id == selectedProvider.id
                        DropdownMenuItem(
                            text = { Text(if (selected) "已选 · ${provider.name}" else provider.name) },
                            onClick = {
                                expanded = false
                                onProviderSelect(provider.id)
                            },
                        )
                    }
                }
            }
        }
        SettingsActionButton(
            text = "模型：$displayModelName",
            onClick = onOpenConfig,
            modifier = Modifier.weight(1.25f),
        )
        SettingsActionButton(
            text = if (apiKey.isBlank()) "API Key：未配置" else "API Key：已配置",
            onClick = onOpenConfig,
            modifier = Modifier.weight(1.1f),
        )
    }
}

@Composable
private fun SettingsActionButton(
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
private fun HomeApiLineSetting(
    apiLines: List<ApiLine>,
    selectedApiLine: ApiLine?,
    customLineCount: Int,
    onSelect: (String) -> Unit,
    onOpenConfig: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "首页渲染数据",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = selectedApiLine?.let { "当前使用：${it.name}" } ?: "请选择首页资源站",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (customLineCount > 0) {
                    "已添加 $customLineCount 条自定义 MacCms 接口"
                } else {
                    "暂无自定义接口"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                SettingsActionButton(
                    text = "${selectedApiLine?.name ?: "选择资源"} ▾",
                    enabled = apiLines.isNotEmpty(),
                    onClick = { expanded = true },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    apiLines.forEach { line ->
                        val selected = line.id == selectedApiLine?.id
                        DropdownMenuItem(
                            text = {
                                Text(if (selected) "已选 · ${line.name}" else line.name)
                            },
                            onClick = {
                                expanded = false
                                onSelect(line.id)
                            },
                        )
                    }
                }
            }
            SettingsActionButton(
                text = "添加接口",
                onClick = onOpenConfig,
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(
    title: String,
    subtitle: String,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeCategoryRows(
    state: TvBoxUiState,
    onHot: () -> Unit,
    onParent: (Int) -> Unit,
    onChild: (Int) -> Unit,
    hotCategoryModifier: Modifier = Modifier,
) {
    val parentCategories = state.categories.filter { it.parentId == 0 }
    val selectedParent = state.selectedParentCategoryId
        ?.let { parentId -> state.categories.firstOrNull { it.id == parentId } }
    val childCategories = selectedParent
        ?.let { parent -> state.categories.filter { it.parentId == parent.id } }
        .orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                CategoryPill(
                    label = "热播",
                    selected = state.homeFeedMode == HomeFeedMode.DoubanHot,
                    onClick = onHot,
                    modifier = hotCategoryModifier,
                )
            }
            items(parentCategories, key = { it.id }) { category ->
                CategoryPill(
                    label = category.name,
                    selected = state.selectedParentCategoryId == category.id,
                    onClick = { onParent(category.id) },
                )
            }
        }
        if (selectedParent != null && childCategories.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    CategoryPill(
                        label = selectedParent.allChildrenLabel(),
                        selected = state.selectedCategoryId == null,
                        onClick = { onParent(selectedParent.id) },
                    )
                }
                items(childCategories, key = { it.id }) { category ->
                    CategoryPill(
                        label = category.name,
                        selected = state.selectedCategoryId == category.id,
                        onClick = { onChild(category.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieGrid(
    state: TvBoxUiState,
    gridState: LazyGridState,
    onMovieClick: (Int) -> Unit,
    onDoubanHotClick: (com.tvbox.app.domain.DoubanHotItem) -> Unit,
    onLoadMore: () -> Unit,
    showSectionHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    var loadMoreFocused by remember { mutableStateOf(false) }
    LaunchedEffect(state.homeFeedMode, state.selectedParentCategoryId, state.selectedCategoryId) {
        gridState.scrollToItem(0)
        loadMoreFocused = false
    }
    Column(modifier = modifier.fillMaxSize()) {
        if (showSectionHeader) {
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
            columns = GridCells.Adaptive(minSize = TvLayout.PosterGridMinWidth),
            state = gridState,
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.weight(1f),
        ) {
            state.homeNotice?.let { notice ->
                item(span = { GridItemSpan(maxLineSpan) }) {
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
            if (state.isShowingDoubanHot) {
                items(state.doubanHotMovies, key = { it.doubanId.ifBlank { it.title } }) { item ->
                    DoubanHotPosterCard(
                        item = item,
                        resolving = state.resolvingDoubanHotId == item.doubanId,
                        onClick = { onDoubanHotClick(item) },
                        posterAspectRatio = 0.78f,
                    )
                }
            } else {
                items(state.movies, key = { "${it.apiLineId}-${it.id}" }) { movie ->
                    MoviePosterCard(
                        movie = movie,
                        onClick = { onMovieClick(movie.id) },
                        posterAspectRatio = 0.78f,
                    )
                }
            }
            if (state.canLoadMore || state.loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
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
                                    if (!loadMoreFocused && !state.loadingMore) {
                                        onLoadMore()
                                    }
                                    loadMoreFocused = true
                                } else {
                                    loadMoreFocused = false
                                }
                            },
                        ) {
                            Text(if (state.loadingMore) "加载中..." else "加载更多")
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

@Composable
private fun ThemeSetting(
    selectedTheme: TvTheme,
    onThemeSelect: (TvTheme) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("界面主题", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "当前：${selectedTheme.displayName}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "选择后立即生效，设置会保存到本机。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvTheme.entries.forEach { theme ->
                SettingsActionButton(
                    text = if (selectedTheme == theme) "✓ ${theme.displayName}" else theme.displayName,
                    selected = selectedTheme == theme,
                    onClick = { onThemeSelect(theme) },
                )
            }
        }
    }
}

@Composable
private fun FontScaleSetting(
    selectedFontScale: TvFontScale,
    onFontScaleSelect: (TvFontScale) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("字体大小", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "当前：${selectedFontScale.displayName}。文字和内容卡片会立即按此大小调整。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(TvFontScale.entries, key = { it.storageKey }) { fontScale ->
                SettingsActionButton(
                    text = if (selectedFontScale == fontScale) {
                        "✓ ${fontScale.displayName}"
                    } else {
                        fontScale.displayName
                    },
                    selected = selectedFontScale == fontScale,
                    onClick = { onFontScaleSelect(fontScale) },
                )
            }
        }
    }
}

private fun Category.allChildrenLabel(): String {
    val baseName = name.removeSuffix("片")
    return "全部$baseName"
}

private fun PlaybackHealthSnapshot.qualitySummaryText(): String {
    if (entryCount <= 0) return "暂无线路质量记录"
    return "已记录 $entryCount 条线路表现｜成功 $successCount｜失败 $failureCount｜卡顿 $slowBufferCount｜保留最近 30 天"
}

private fun createQrBitmap(content: String, size: Int): Bitmap {
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
