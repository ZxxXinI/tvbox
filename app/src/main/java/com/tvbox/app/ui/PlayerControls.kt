package com.tvbox.app.ui

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tvbox.app.domain.PlaySource
import com.tvbox.app.domain.VideoScaleMode
import com.tvbox.app.domain.correspondingEpisodeIndex

internal enum class PlayerPanel { None, Episodes, Sources, Speeds, Picture }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerControls(
    title: String,
    sources: List<PlaySource>,
    sourceIndex: Int,
    episodeIndex: Int,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    speed: Float,
    panel: PlayerPanel,
    videoScaleMode: VideoScaleMode,
    onPanel: (PlayerPanel) -> Unit,
    onToggle: () -> Unit,
    onEpisode: (Int) -> Unit,
    onSource: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onVideoScale: (VideoScaleMode) -> Unit,
    onSeek: (Long) -> Unit,
    onInteraction: () -> Unit,
    onScrubbing: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = sources[sourceIndex]
    val compact = LocalConfiguration.current.screenWidthDp < 800
    val buttonScroll = rememberScrollState()
    var progressFocused by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    var preview by remember { mutableStateOf<Float?>(null) }
    var lastSeekTime by remember { mutableStateOf(Long.MIN_VALUE) }
    LaunchedEffect(panel) { if (panel == PlayerPanel.None) playFocus.requestFocus() }
    Box(modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                .windowInsetsPadding(WindowInsets.safeDrawing).testTag("player_controls"),
            color = Color.Transparent, contentColor = Color.White,
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$title · ${source.episodes.getOrNull(episodeIndex)?.title.orEmpty()}",
                    style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatPlayerTime(preview?.toLong() ?: positionMs), modifier = Modifier.width(66.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = Color.White.copy(alpha = 0.8f))
                    Slider(
                        value = preview ?: positionMs.toFloat().coerceIn(0f, durationMs.coerceAtLeast(1).toFloat()),
                        onValueChange = { preview = it; onScrubbing(true); onInteraction() },
                        onValueChangeFinished = {
                            preview?.let { onSeek(it.toLong()) }; preview = null; onScrubbing(false)
                        },
                        valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(), enabled = durationMs > 0,
                        thumb = {
                            Box(Modifier.size(if (progressFocused) 18.dp else 12.dp)
                                .background(Color.White, CircleShape))
                        },
                        track = { slider ->
                            val fraction = ((slider.value - slider.valueRange.start) /
                                (slider.valueRange.endInclusive - slider.valueRange.start).coerceAtLeast(1f)).coerceIn(0f, 1f)
                            Canvas(Modifier.fillMaxWidth().height(if (progressFocused) 5.dp else 3.dp)) {
                                val center = size.height / 2
                                drawLine(Color.White.copy(alpha = 0.28f), Offset(0f, center), Offset(size.width, center), size.height, StrokeCap.Round)
                                drawLine(Color(0xFF7AE2BA), Offset(0f, center), Offset(size.width * fraction, center), size.height, StrokeCap.Round)
                            }
                        },
                        modifier = Modifier.weight(1f).height(36.dp).padding(horizontal = 8.dp)
                            .onFocusChanged { progressFocused = it.isFocused }
                            .testTag("player_progress").onPreviewKeyEvent { event ->
                            val delta = when (event.nativeKeyEvent.keyCode) {
                                NativeKeyEvent.KEYCODE_DPAD_LEFT -> -10_000L
                                NativeKeyEvent.KEYCODE_DPAD_RIGHT -> 10_000L
                                else -> return@onPreviewKeyEvent false
                            }
                            onInteraction()
                            if (event.type == KeyEventType.KeyDown) {
                                if (event.nativeKeyEvent.repeatCount == 0 || lastSeekTime == Long.MIN_VALUE ||
                                    event.nativeKeyEvent.eventTime - lastSeekTime >= 150) {
                                    preview = ((preview?.toLong() ?: positionMs) + delta).coerceIn(0, durationMs.coerceAtLeast(0)).toFloat()
                                    lastSeekTime = event.nativeKeyEvent.eventTime
                                }
                                onScrubbing(true)
                            } else if (event.type == KeyEventType.KeyUp) {
                                preview?.let { onSeek(it.toLong()) }; preview = null; onScrubbing(false)
                                lastSeekTime = Long.MIN_VALUE
                            }
                            true
                        },
                    )
                    Text(if (durationMs > 0) formatPlayerTime(durationMs) else "--:--", modifier = Modifier.width(66.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = Color.White.copy(alpha = 0.8f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().then(if (compact) Modifier.horizontalScroll(buttonScroll) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton(if (playing) "暂停" else "播放", onToggle, Modifier.focusRequester(playFocus), primary = true)
                    ControlButton("上一集", { onEpisode(episodeIndex - 1) }, enabled = episodeIndex > 0)
                    ControlButton("下一集", { onEpisode(episodeIndex + 1) }, enabled = episodeIndex < source.episodes.lastIndex)
                    if (!compact) Spacer(Modifier.weight(1f))
                    ControlButton("选集", { onPanel(PlayerPanel.Episodes) })
                    ControlButton("线路", { onPanel(PlayerPanel.Sources) })
                    ControlButton("${speed.toString().trimEnd('0').trimEnd('.')}x", { onPanel(PlayerPanel.Speeds) })
                    ControlButton("画面", { onPanel(PlayerPanel.Picture) })
                }
            }
        }
        if (panel != PlayerPanel.None) {
            val labels = when (panel) {
                PlayerPanel.Episodes -> source.episodes.map { it.title }
                PlayerPanel.Sources -> sources.map { it.name }
                PlayerPanel.Speeds -> playerSpeeds.map { "${it}x" }
                PlayerPanel.Picture -> VideoScaleMode.entries.map { it.displayName }
                PlayerPanel.None -> emptyList()
            }
            val selected = when (panel) {
                PlayerPanel.Episodes -> episodeIndex
                PlayerPanel.Sources -> sourceIndex
                PlayerPanel.Speeds -> playerSpeeds.indexOf(speed).coerceAtLeast(0)
                PlayerPanel.Picture -> VideoScaleMode.entries.indexOf(videoScaleMode)
                PlayerPanel.None -> 0
            }
            val pickerFocus = remember(panel, labels.size) { List(labels.size) { FocusRequester() } }
            val grid = rememberLazyGridState()
            LaunchedEffect(panel) {
                grid.scrollToItem(selected.coerceIn(0, labels.lastIndex.coerceAtLeast(0)))
                pickerFocus.getOrNull(selected)?.requestFocus()
            }
            Surface(Modifier.align(Alignment.Center).fillMaxWidth(0.8f).fillMaxHeight(0.6f),
                color = Color(0xFA202020), contentColor = Color.White, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(when (panel) {
                            PlayerPanel.Episodes -> "选择集数"
                            PlayerPanel.Sources -> "选择线路"
                            PlayerPanel.Picture -> "画面比例 · ${videoScaleMode.displayName}"
                            else -> "播放倍速"
                        }, Modifier.weight(1f))
                        ControlButton("关闭", { onPanel(PlayerPanel.None) })
                    }
                    if (panel == PlayerPanel.Picture) {
                        Text(videoScaleMode.description, style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 12.dp))
                    }
                    LazyVerticalGrid(GridCells.Adaptive(130.dp), state = grid,
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(labels) { index, label ->
                            val enabled = panel != PlayerPanel.Sources || correspondingEpisodeIndex(
                                sources[index], source.episodes[episodeIndex].title, episodeIndex) != null
                            ControlButton(if (index == selected) "$label ✓" else label, {
                                onInteraction()
                                when (panel) {
                                    PlayerPanel.Episodes -> onEpisode(index)
                                    PlayerPanel.Sources -> onSource(index)
                                    PlayerPanel.Speeds -> onSpeed(playerSpeeds[index])
                                    PlayerPanel.Picture -> onVideoScale(VideoScaleMode.entries[index])
                                    else -> Unit
                                }
                                onPanel(PlayerPanel.None)
                            }, Modifier.focusRequester(pickerFocus[index]), enabled)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (primary) 24.dp else 10.dp)
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).onFocusChanged { focused = it.isFocused }
            .border(2.dp, if (focused && enabled) Color(0xFF7AE2BA) else Color.Transparent, shape),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = when { primary -> Color.White; focused -> Color.White.copy(alpha = 0.18f); else -> Color.Transparent },
            contentColor = if (primary) Color(0xFF101614) else Color.White.copy(alpha = 0.9f),
            disabledContainerColor = Color.Transparent, disabledContentColor = Color.White.copy(alpha = 0.3f),
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(text, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

internal fun formatPlayerTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
private val playerSpeeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
