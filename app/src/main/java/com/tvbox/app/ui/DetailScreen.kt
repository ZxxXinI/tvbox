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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvbox.app.domain.PlayEpisode
import com.tvbox.app.domain.PlaySource
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
                                        onClick = { actions.openPlayer(state.selectedSourceIndex, 0) },
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
                        text = "播放源",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PlaySourceTabs(
                        sources = movie.playSources,
                        selectedIndex = state.selectedSourceIndex,
                        onSelect = actions::selectPlaySource,
                    )
                    Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))
                    EpisodeGrid(
                        source = movie.playSources.getOrNull(state.selectedSourceIndex),
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
    sources: List<PlaySource>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    if (sources.isEmpty()) {
        Text("暂无可播放源", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(sources) { index, source ->
            CategoryPill(
                label = source.name,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun EpisodeGrid(
    source: PlaySource?,
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
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 132.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(episodes) { index, episode ->
            EpisodeButton(episode = episode, onClick = { onEpisode(index) })
        }
    }
}

@Composable
private fun EpisodeButton(
    episode: PlayEpisode,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        modifier = Modifier
            .tvFocusScale(shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .focusable(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = episode.title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
