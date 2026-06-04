package com.tvbox.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.tvbox.app.ui.components.ErrorState
import com.tvbox.app.ui.components.LoadingState
import com.tvbox.app.ui.components.MoviePosterCard
import com.tvbox.app.ui.components.PageSurface

@Composable
fun SearchScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
) {
    PageSurface { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = "搜索影片",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = actions::updateSearchQuery,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("影片名称") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { actions.submitSearch() }),
                )
                Button(
                    onClick = actions::submitSearch,
                    enabled = !state.searchLoading,
                ) {
                    Text(if (state.searchLoading) "搜索中" else "搜索")
                }
                Button(onClick = actions::goBack) {
                    Text("返回")
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            when {
                state.searchLoading -> LoadingState(text = "正在搜索")
                state.searchError != null && state.searchResults.isEmpty() -> ErrorState(
                    message = state.searchError,
                    onRetry = actions::submitSearch,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.searchResults, key = { it.id }) { movie ->
                        MoviePosterCard(movie = movie, onClick = { actions.openDetail(movie.id) })
                    }
                }
            }
        }
    }
}

