package com.tvbox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tvbox.app.ui.components.AppHeader
import com.tvbox.app.ui.components.CategoryPill
import com.tvbox.app.ui.components.ErrorState
import com.tvbox.app.ui.components.HistoryItemCard
import com.tvbox.app.ui.components.LoadingState
import com.tvbox.app.ui.components.MoviePosterCard
import com.tvbox.app.ui.components.PageSurface

@Composable
fun TvBoxApp(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    when (state.screen) {
        TvScreen.Home -> HomeScreen(state = state, actions = actions)
        TvScreen.History -> HistoryScreen(state = state, actions = actions)
        TvScreen.Search -> SearchScreen(state = state, actions = actions)
        TvScreen.Detail -> DetailScreen(state = state, actions = actions)
        TvScreen.Player -> PlayerScreen(state = state, actions = actions)
    }
}

@Composable
private fun HomeScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    PageSurface { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AppHeader(
                title = "TVBox",
                subtitle = "共 ${state.total} 部影片，支持 Android TV 遥控浏览",
                onHistory = actions::openHistory,
                onSearch = actions::openSearch,
                onRefresh = actions::refreshHome,
            )
            Spacer(modifier = Modifier.height(18.dp))
            CategoryRow(state = state, onCategory = actions::selectCategory)
            Spacer(modifier = Modifier.height(20.dp))
            when {
                state.homeLoading -> LoadingState(text = "正在加载影片")
                state.homeError != null -> ErrorState(message = state.homeError, onRetry = actions::refreshHome)
                else -> MovieGrid(
                    state = state,
                    onMovieClick = actions::openDetail,
                    onLoadMore = actions::loadNextPage,
                )
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
                Button(onClick = actions::goBack) {
                    Text("返回")
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
                    columns = GridCells.Adaptive(minSize = 230.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.historyItems, key = { "${it.movieId}-${it.updatedAtEpochMs}" }) { item ->
                        HistoryItemCard(item = item, onClick = { actions.resumeHistory(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    state: TvBoxUiState,
    onCategory: (Int?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            CategoryPill(
                label = "全部",
                selected = state.selectedCategoryId == null,
                onClick = { onCategory(null) },
            )
        }
        items(state.categories, key = { it.id }) { category ->
            CategoryPill(
                label = category.name,
                selected = state.selectedCategoryId == category.id,
                onClick = { onCategory(category.id) },
            )
        }
    }
}

@Composable
private fun MovieGrid(
    state: TvBoxUiState,
    onMovieClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.selectedCategoryId) {
        gridState.scrollToItem(0)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 148.dp),
        state = gridState,
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.movies, key = { it.id }) { movie ->
            MoviePosterCard(movie = movie, onClick = { onMovieClick(movie.id) })
        }
        if (state.canLoadMore || state.loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(onClick = onLoadMore, enabled = !state.loadingMore) {
                        Text(if (state.loadingMore) "加载中..." else "加载更多")
                    }
                }
            }
        } else if (state.movies.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "已经到底了",
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
