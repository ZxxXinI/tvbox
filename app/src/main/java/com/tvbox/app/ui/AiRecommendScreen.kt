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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvbox.app.ui.components.ErrorState
import com.tvbox.app.ui.components.LoadingState
import com.tvbox.app.ui.components.PageSurface
import com.tvbox.app.ui.components.tvFocusScale

@Composable
fun AiRecommendScreen(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
    onStartVoiceInput: () -> Unit,
) {
    PageSurface { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = state.aiTitle,
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.aiQuery,
                    onValueChange = actions::updateAiQuery,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("例如：悬疑电视剧推荐") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { actions.submitAiRecommendation() }),
                )
                Button(
                    onClick = onStartVoiceInput,
                    enabled = !state.aiLoading,
                ) {
                    Text("语音")
                }
                Button(
                    onClick = actions::submitAiRecommendation,
                    enabled = !state.aiLoading,
                ) {
                    Text(if (state.aiLoading) "找片中" else "找片")
                }
                Button(onClick = actions::goBack) {
                    Text("返回")
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            when {
                state.aiLoading -> LoadingState(text = "AI 正在生成推荐")
                state.aiError != null && state.aiResults.isEmpty() -> ErrorState(
                    message = state.aiError,
                    onRetry = actions::submitAiRecommendation,
                )
                state.aiResults.isEmpty() -> EmptyAiRecommendState()
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    if (state.aiResolvingKeyword != null) {
                        Text(
                            text = "正在查找资源：${state.aiResolvingKeyword}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    } else if (state.aiError != null) {
                        Text(
                            text = state.aiError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 148.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.aiResults) { item ->
                            AiRecommendationCard(
                                item = item,
                                resolving = state.aiResolvingKeyword == item.recommendation.searchKeyword.ifBlank {
                                    item.recommendation.name
                                },
                                onClick = { actions.openAiRecommendation(item.recommendation) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAiRecommendState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("说出或输入你的找片需求", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "例如：悬疑电视剧推荐 / 最近高分电影 / 适合小孩看的动画电影",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AiRecommendationCard(
    item: AiRecommendationUiItem,
    resolving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recommendation = item.recommendation
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .tvFocusScale(shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .focusable(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                text = recommendation.name,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = if (resolving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (resolving) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text = if (resolving) "查找中" else "AI推荐",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = recommendation.name,
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = recommendation.metaText(),
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = recommendation.reason.ifBlank { "AI 推荐" },
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun com.tvbox.app.domain.AiRecommendationItem.metaText(): String {
    return listOf(year, area, genre, score)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .ifBlank { "AI 推荐" }
}
