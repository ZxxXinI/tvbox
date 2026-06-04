package com.tvbox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        PosterImage(
                            movie = movie,
                            modifier = Modifier
                                .width(210.dp)
                                .aspectRatio(2f / 3f),
                        )
                        Spacer(modifier = Modifier.width(28.dp))
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
                                Button(onClick = actions::goBack) {
                                    Text("返回")
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            InfoLine(label = "主演", value = movie.actor)
                            InfoLine(label = "导演", value = movie.director)
                            InfoLine(label = "地区", value = movie.area)
                            InfoLine(label = "语言", value = movie.language)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = movie.description.ifBlank { "暂无简介" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    PlaySourceTabs(
                        sources = movie.playSources,
                        selectedIndex = state.selectedSourceIndex,
                        onSelect = actions::selectPlaySource,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    EpisodeGrid(
                        source = movie.playSources.getOrNull(state.selectedSourceIndex),
                        onEpisode = { episodeIndex -> actions.openPlayer(state.selectedSourceIndex, episodeIndex) },
                    )
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
) {
    val episodes = source?.episodes.orEmpty()
    if (episodes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("当前播放源暂无剧集", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 132.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
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

