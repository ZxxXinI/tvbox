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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
    val voiceFocusRequester = remember { FocusRequester() }
    val inputEnabled = !state.aiLoading && !state.aiVoiceListening && state.aiResolvingKeyword == null

    LaunchedEffect(Unit) {
        runCatching { voiceFocusRequester.requestFocus() }
    }

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
                    enabled = inputEnabled,
                    singleLine = true,
                    label = { Text("例如：悬疑电视剧推荐") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { actions.submitAiRecommendation() }),
                )
                AiActionButton(
                    text = "语音找片",
                    onClick = onStartVoiceInput,
                    modifier = Modifier
                        .focusRequester(voiceFocusRequester),
                    enabled = inputEnabled,
                )
                AiActionButton(
                    text = if (state.aiLoading) "找片中" else "找片",
                    onClick = { actions.submitAiRecommendation() },
                    enabled = inputEnabled,
                )
                AiActionButton(
                    text = "换一批",
                    onClick = { actions.refreshAiRecommendationBatch() },
                    enabled = inputEnabled && state.aiResults.isNotEmpty(),
                )
                AiActionButton(
                    text = "返回",
                    onClick = actions::goBack,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            AiQuickPromptRow(
                enabled = inputEnabled,
                onPromptClick = { prompt -> actions.submitAiRecommendation(prompt) },
            )
            Spacer(modifier = Modifier.height(22.dp))
            when {
                state.aiVoiceListening -> LoadingState(text = "正在听，请说出找片需求")
                state.aiLoading -> LoadingState(text = "AI 正在生成推荐")
                state.aiError != null && state.aiResults.isEmpty() -> ErrorState(
                    message = state.aiError,
                    onRetry = { actions.submitAiRecommendation() },
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
private fun AiQuickPromptRow(
    enabled: Boolean,
    onPromptClick: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        rowItems(aiQuickPrompts) { prompt ->
            AiActionButton(
                text = prompt,
                onClick = { onPromptClick(prompt) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun AiActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .then(
                if (enabled) {
                    Modifier.tvFocusScale(
                        shape = shape,
                        focusedBorder = Color.White,
                        idleBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
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
            else -> Color.Transparent
        },
        contentColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            focused -> MaterialTheme.colorScheme.onPrimary
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
        )
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

private val aiQuickPrompts = listOf(
    "悬疑电视剧",
    "最近高分电影",
    "动画电影",
    "韩国犯罪剧",
    "搞笑综艺",
    "适合小孩看",
)
