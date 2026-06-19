package com.tvbox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvbox.app.domain.PlayEpisode
import com.tvbox.app.domain.PlaybackHealthSnapshot
import com.tvbox.app.domain.PlaybackIssueType
import com.tvbox.app.domain.PlaySource
import com.tvbox.app.domain.playbackHealthKey
import com.tvbox.app.ui.components.CategoryPill
import com.tvbox.app.ui.components.ErrorState
import com.tvbox.app.ui.components.InfoLine
import com.tvbox.app.ui.components.LoadingState
import com.tvbox.app.ui.components.PageSurface
import com.tvbox.app.ui.components.PosterImage
import com.tvbox.app.ui.components.SmallMeta
import com.tvbox.app.ui.components.tvFocusScale

@Composable
fun DetailScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    PageSurface { padding ->
        when {
            state.detailLoading -> LoadingState(text = "正在加载详情", modifier = Modifier.padding(padding))
            state.detailError != null -> ErrorState(
                message = state.detailError,
                onRetry = actions::retryCurrent,
                modifier = Modifier.padding(padding),
            )
            state.detailMovie == null -> ErrorState(
                message = "影片详情为空",
                onRetry = actions::goBack,
                modifier = Modifier.padding(padding),
            )
            else -> {
                val movie = state.detailMovie
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    val compact = maxHeight < 560.dp
                    val posterWidth = if (compact) 118.dp else 210.dp
                    val horizontalGap = if (compact) 16.dp else 28.dp
                    val headerMaxHeight = if (compact) 190.dp else 320.dp
                    val descriptionLines = if (compact) 2 else 5
                    val sectionGap = if (compact) 12.dp else 24.dp
                    val selectedSource = movie.playSources.getOrNull(state.selectedSourceIndex)
                    val canPlay = selectedSource?.episodes?.isNotEmpty() == true

                    Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = headerMaxHeight),
                    ) {
                        PosterImage(
                            movie = movie,
                            modifier = Modifier
                                .width(posterWidth)
                                .aspectRatio(2f / 3f),
                        )
                        Spacer(modifier = Modifier.width(horizontalGap))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = movie.name,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SmallMeta(movie.typeName)
                                        SmallMeta(movie.year)
                                        SmallMeta(movie.remarks)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Button(
                                        onClick = {
                                            actions.openPlayer(
                                                state.selectedSourceIndex,
                                                state.selectedEpisodeIndex,
                                            )
                                        },
                                        enabled = canPlay,
                                    ) {
                                        Text("立即播放")
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = actions::goBack) {
                                        Text("返回")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(if (compact) 10.dp else 18.dp))
                            InfoLine(label = "主演", value = movie.actor)
                            InfoLine(label = "导演", value = movie.director)
                            InfoLine(label = "地区", value = movie.area)
                            InfoLine(label = "语言", value = movie.language)
                            Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))
                            Text(
                                text = movie.description.ifBlank { "暂无简介" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = descriptionLines,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(sectionGap))
                    Text(
                        text = "播放线路",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PlaySourceTabs(
                        movieId = movie.id,
                        sources = movie.playSources,
                        selectedIndex = state.selectedSourceIndex,
                        selectedEpisodeIndex = state.selectedEpisodeIndex,
                        playbackHealth = state.playbackHealth,
                        onSelect = actions::selectPlaySource,
                    )
                    Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))
                    EpisodeGrid(
                        source = movie.playSources.getOrNull(state.selectedSourceIndex),
                        selectedEpisodeIndex = state.selectedEpisodeIndex,
                        onEpisode = { episodeIndex -> actions.openPlayer(state.selectedSourceIndex, episodeIndex) },
                        modifier = Modifier.weight(1f),
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaySourceTabs(
    movieId: Int,
    sources: List<PlaySource>,
    selectedIndex: Int,
    selectedEpisodeIndex: Int,
    playbackHealth: PlaybackHealthSnapshot,
    onSelect: (Int) -> Unit,
) {
    if (sources.isEmpty()) {
        Text("暂无可播放源", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val nowMs = System.currentTimeMillis()
    val recommendedIndex = recommendedPlaySourceIndex(
        movieId = movieId,
        sources = sources,
        episodeIndex = selectedEpisodeIndex,
        playbackHealth = playbackHealth,
        nowMs = nowMs,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(sources) { index, source ->
            val statusLabel = playSourceStatusLabel(
                movieId = movieId,
                source = source,
                sourceIndex = index,
                episodeIndex = selectedEpisodeIndex,
                playbackHealth = playbackHealth,
                recommendedIndex = recommendedIndex,
                nowMs = nowMs,
            )
            CategoryPill(
                label = listOfNotNull(source.name, statusLabel).joinToString(" · "),
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun EpisodeGrid(
    source: PlaySource?,
    selectedEpisodeIndex: Int,
    onEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodes = source?.episodes.orEmpty()
    if (episodes.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("当前播放源暂无剧集", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val safeSelectedEpisodeIndex = selectedEpisodeIndex.coerceIn(0, episodes.lastIndex)
    val gridState = rememberLazyGridState()
    val focusRequesters = remember(episodes) {
        List(episodes.size) { FocusRequester() }
    }

    LaunchedEffect(episodes, safeSelectedEpisodeIndex) {
        gridState.scrollToItem(safeSelectedEpisodeIndex)
        withFrameNanos { }
        runCatching {
            focusRequesters[safeSelectedEpisodeIndex].requestFocus()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 132.dp),
        state = gridState,
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(
            items = episodes,
            key = { index, episode -> "$index-${episode.url}" },
        ) { index, episode ->
            EpisodeButton(
                episode = episode,
                selected = index == safeSelectedEpisodeIndex,
                focusRequester = focusRequesters[index],
                onClick = { onEpisode(index) },
            )
        }
    }
}

@Composable
private fun EpisodeButton(
    episode: PlayEpisode,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier
            .tvFocusScale(shape = shape)
            .focusRequester(focusRequester)
            .clip(shape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .focusable(),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = episode.title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun recommendedPlaySourceIndex(
    movieId: Int,
    sources: List<PlaySource>,
    episodeIndex: Int,
    playbackHealth: PlaybackHealthSnapshot,
    nowMs: Long,
): Int? {
    val playableIndexes = sources.indices.filter { index ->
        sources[index].episodes.getOrNull(episodeIndex)?.url?.isNotBlank() == true
    }
    return playableIndexes.firstOrNull { index ->
        val key = playbackHealthKey(movieId, episodeIndex, sources[index])
        playbackHealth.entryFor(key)?.recentIssueType(nowMs) == null
    } ?: playableIndexes.firstOrNull()
}

private fun playSourceStatusLabel(
    movieId: Int,
    source: PlaySource,
    sourceIndex: Int,
    episodeIndex: Int,
    playbackHealth: PlaybackHealthSnapshot,
    recommendedIndex: Int?,
    nowMs: Long,
): String? {
    val key = playbackHealthKey(movieId, episodeIndex, source)
    return when (playbackHealth.entryFor(key)?.recentIssueType(nowMs)) {
        PlaybackIssueType.Error -> "近期失败"
        PlaybackIssueType.SlowBuffer -> "较慢"
        null -> if (sourceIndex == recommendedIndex) "推荐" else null
    }
}
