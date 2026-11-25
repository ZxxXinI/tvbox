package com.tvbox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvbox.app.domain.MAX_INTRO_SKIP_SECONDS
import com.tvbox.app.domain.PlaySource
import com.tvbox.app.domain.VideoScaleMode
import com.tvbox.app.domain.correspondingEpisodeIndex

private val SidebarAccent = Color(0xFF7AE2BA)
private val SidebarMuted = Color.White.copy(alpha = 0.6f)

@Composable
internal fun PlayerSettingsSidebar(
    panel: PlayerPanel,
    sources: List<PlaySource>,
    sourceIndex: Int,
    episodeIndex: Int,
    videoScaleMode: VideoScaleMode,
    defaultIntroSeconds: Int,
    introSecondsOverride: Int?,
    onPanel: (PlayerPanel) -> Unit,
    onVideoScale: (VideoScaleMode) -> Unit,
    onIntroSeconds: (Int?) -> Unit,
    onEpisode: (Int) -> Unit,
    onSource: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = sources[sourceIndex]
    val episodeTitle = source.episodes.getOrNull(episodeIndex)?.title.orEmpty()
    val introSeconds = (introSecondsOverride ?: defaultIntroSeconds).coerceIn(0, MAX_INTRO_SKIP_SECONDS)
    var lastEntry by remember { mutableStateOf(PlayerPanel.Picture) }
    val isRoot = panel == PlayerPanel.Settings
    val backToSettings = { onPanel(PlayerPanel.Settings) }
    Surface(
        modifier = modifier.testTag("player_settings"),
        color = Color(0xEF101514),
        contentColor = Color.White,
    ) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom))
                .padding(horizontal = 12.dp)
                .focusProperties {
                    onExit = {
                        when (requestedFocusDirection) {
                            FocusDirection.Up, FocusDirection.Down, FocusDirection.Left, FocusDirection.Right -> cancelFocusChange()
                            else -> Unit
                        }
                    }
                }
                .focusGroup(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!isRoot) {
                    SidebarButton(
                        text = "‹",
                        onClick = backToSettings,
                        modifier = Modifier.width(40.dp).semantics { contentDescription = "返回播放设置" },
                    )
                } else {
                    Spacer(Modifier.width(4.dp))
                }
                Text(panel.settingsTitle, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            // 切换子页时只重建内容。返回列表时恢复打开前的项目，片头未保存值随子页销毁。
            key(panel) {
                val initialFocus = remember { FocusRequester() }
                val contentModifier = Modifier.weight(1f).fillMaxWidth()
                if (panel != PlayerPanel.Episodes) {
                    LaunchedEffect(Unit) { initialFocus.requestPlayerControlFocus() }
                }
                when (panel) {
                    PlayerPanel.Settings -> SidebarList(contentModifier) {
                        playerSettingsPanels.forEach { entry ->
                            val value = when (entry) {
                                PlayerPanel.Picture -> videoScaleMode.displayName
                                PlayerPanel.Intro -> introSkipLabel(introSeconds)
                                PlayerPanel.Episodes -> episodeTitle
                                PlayerPanel.Sources -> source.name
                                else -> error("Expected a playback settings entry")
                            }
                            PlaybackSettingsRow(
                                title = entry.settingsTitle,
                                value = value,
                                selected = lastEntry == entry,
                                trailingText = "›",
                                inlineValue = true,
                                modifier = Modifier.initialFocusWhen(lastEntry == entry, initialFocus),
                                onClick = {
                                    lastEntry = entry
                                    onPanel(entry)
                                },
                            )
                        }
                    }
                    PlayerPanel.Picture -> SidebarList(contentModifier) {
                        VideoScaleMode.entries.forEach { mode ->
                            PlaybackSettingsRow(
                                title = mode.displayName,
                                value = when (mode) {
                                    VideoScaleMode.Fit -> "保留原始比例"
                                    VideoScaleMode.Zoom -> "原比例铺满，可能裁边"
                                    VideoScaleMode.Fill -> "完整铺满，比例可能改变"
                                },
                                selected = mode == videoScaleMode,
                                modifier = Modifier.initialFocusWhen(mode == videoScaleMode, initialFocus),
                                onClick = {
                                    onVideoScale(mode)
                                    backToSettings()
                                },
                            )
                        }
                    }
                    PlayerPanel.Intro -> SidebarList(contentModifier) {
                        IntroSettings(
                            inheritedSeconds = defaultIntroSeconds,
                            savedOverride = introSecondsOverride,
                            initialFocus = initialFocus,
                            onSave = {
                                onIntroSeconds(it)
                                backToSettings()
                            },
                        )
                    }
                    PlayerPanel.Episodes -> EpisodeSettings(
                        titles = source.episodes.map { it.title },
                        selectedIndex = episodeIndex,
                        initialFocus = initialFocus,
                        modifier = contentModifier,
                        onSelect = {
                            onEpisode(it)
                            backToSettings()
                        },
                    )
                    PlayerPanel.Sources -> SidebarList(contentModifier) {
                        val available = remember(sources, episodeTitle, episodeIndex) {
                            sources.map { correspondingEpisodeIndex(it, episodeTitle, episodeIndex) != null }
                        }
                        sources.forEachIndexed { index, option ->
                            PlaybackSettingsRow(
                                title = option.name,
                                value = when {
                                    index == sourceIndex -> "当前线路"
                                    !available[index] -> "缺少本集"
                                    else -> "可切换"
                                },
                                selected = index == sourceIndex,
                                enabled = available[index],
                                modifier = Modifier.initialFocusWhen(index == sourceIndex, initialFocus),
                                onClick = {
                                    onSource(index)
                                    backToSettings()
                                },
                            )
                        }
                    }
                    PlayerPanel.None -> Unit
                }
            }
        }
    }
}

@Composable
private fun SidebarList(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun IntroSettings(
    inheritedSeconds: Int,
    savedOverride: Int?,
    initialFocus: FocusRequester,
    onSave: (Int?) -> Unit,
) {
    var draftOverride by remember { mutableStateOf(savedOverride) }
    val seconds = (draftOverride ?: inheritedSeconds).coerceIn(0, MAX_INTRO_SKIP_SECONDS)
    IntroSkipDuration(seconds, secondaryColor = SidebarMuted)
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SidebarButton(
            "−5 秒", { if (seconds > 0) draftOverride = (seconds - 5).coerceAtLeast(0) },
            Modifier.weight(1f).initialFocusWhen(seconds == MAX_INTRO_SKIP_SECONDS, initialFocus),
        )
        SidebarButton(
            "+5 秒", { if (seconds < MAX_INTRO_SKIP_SECONDS) draftOverride = (seconds + 5).coerceAtMost(MAX_INTRO_SKIP_SECONDS) },
            Modifier.weight(1f).initialFocusWhen(seconds < MAX_INTRO_SKIP_SECONDS, initialFocus),
        )
    }
    Text("常用时长", color = SidebarMuted, style = MaterialTheme.typography.bodySmall)
    introSkipPresets.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { preset ->
                SidebarButton(
                    introSkipLabel(preset), { draftOverride = preset }, Modifier.weight(1f),
                    selected = seconds == preset,
                )
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    SidebarButton("保存", { onSave(draftOverride) }, Modifier.fillMaxWidth().padding(top = 6.dp), primary = true)
}

@Composable
private fun EpisodeSettings(
    titles: List<String>,
    selectedIndex: Int,
    initialFocus: FocusRequester,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    if (titles.isEmpty()) return
    val grid = rememberLazyGridState()
    val initialIndex = remember { selectedIndex.coerceIn(titles.indices) }
    LaunchedEffect(Unit) {
        grid.scrollToItem(initialIndex)
        initialFocus.requestPlayerControlFocus()
    }
    Column(modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("共 ${titles.size} 集", color = SidebarMuted, style = MaterialTheme.typography.labelSmall)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = grid,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(titles) { index, title ->
                val selected = index == selectedIndex
                SidebarButton(
                    if (selected) "$title ✓" else title,
                    { onSelect(index) },
                    Modifier.initialFocusWhen(index == initialIndex, initialFocus),
                    selected = selected,
                )
            }
        }
    }
}

@Composable
private fun SidebarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    primary: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val background = when {
        primary -> SidebarAccent
        selected || focused -> SidebarAccent.copy(alpha = 0.13f)
        else -> Color.Transparent
    }
    val border = when {
        primary && focused -> Color.White
        focused -> SidebarAccent
        primary -> Color.Transparent
        else -> Color.White.copy(alpha = 0.16f)
    }
    Box(
        modifier.clip(shape)
            .background(background)
            .border(if (primary && focused) 2.dp else 1.dp, border, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) Color(0xFF101514) else Color.White,
            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val PlayerPanel.settingsTitle: String
    get() = when (this) {
        PlayerPanel.None, PlayerPanel.Settings -> "播放设置"
        PlayerPanel.Picture -> "画面"
        PlayerPanel.Intro -> "片头"
        PlayerPanel.Episodes -> "选集"
        PlayerPanel.Sources -> "线路"
    }

private fun Modifier.initialFocusWhen(useRequester: Boolean, requester: FocusRequester) =
    if (useRequester) focusRequester(requester) else this

internal suspend fun FocusRequester.requestPlayerControlFocus() {
    repeat(8) {
        withFrameNanos { }
        if (runCatching { requestFocus() }.getOrDefault(false)) return
    }
}
