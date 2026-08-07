package com.tvbox.app.ui.components

import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tvbox.app.R
import com.tvbox.app.data.DOUBAN_REFERER
import com.tvbox.app.data.DOUBAN_USER_AGENT
import com.tvbox.app.domain.DoubanHotItem
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.ui.TvScreen
import com.tvbox.app.ui.theme.TvColors
import com.tvbox.app.ui.theme.TvDimens

@Composable
fun AppHeader(
    title: String,
    subtitle: String,
    onHistory: () -> Unit,
    onSearch: () -> Unit,
    onAiRecommend: () -> Unit = {},
    onLive: () -> Unit = {},
    onPlatformLive: () -> Unit = {},
    onSettings: () -> Unit = {},
    showShortcutActions: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = if (showShortcutActions) {
                    MaterialTheme.typography.headlineLarge
                } else {
                    MaterialTheme.typography.titleLarge
                },
                fontWeight = if (showShortcutActions) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = TvColors.TextSecondary,
                style = if (showShortcutActions) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.labelMedium
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showShortcutActions) {
                HeaderActionButton(text = "历史(1)", onClick = onHistory, legacyStyle = true)
                HeaderActionButton(text = "搜索(2)", onClick = onSearch, legacyStyle = true)
                HeaderActionButton(text = "推荐(3)", onClick = onAiRecommend, legacyStyle = true)
                HeaderActionButton(text = "电视(4)", onClick = onLive, legacyStyle = true)
                HeaderActionButton(text = "直播(5)", onClick = onPlatformLive, legacyStyle = true)
                HeaderActionButton(text = "设置(6)", onClick = onSettings, legacyStyle = true)
            } else {
                HeaderActionButton(text = "搜索", onClick = onSearch)
                HeaderActionButton(text = "历史", onClick = onHistory)
            }
        }
    }
}

@Composable
fun AppNavigationRail(
    screen: TvScreen,
    onHome: () -> Unit,
    onAiRecommend: () -> Unit,
    onLive: () -> Unit,
    onPlatformLive: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(TvDimens.RailWidth),
        color = TvColors.Background,
        contentColor = TvColors.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(13.dp),
                color = TvColors.Accent,
                contentColor = TvColors.Background,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("TV", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TvNavigationItem("首页", NavigationIcon.Home, screen.isHomeDestination(), onHome)
            TvNavigationItem("电视", NavigationIcon.Live, screen == TvScreen.Live, onLive)
            TvNavigationItem("直播", NavigationIcon.PlatformLive, screen == TvScreen.PlatformLive, onPlatformLive)
            TvNavigationItem("推荐", NavigationIcon.Recommend, screen == TvScreen.AiRecommend, onAiRecommend)
            Spacer(modifier = Modifier.weight(1f))
            TvNavigationItem("设置", NavigationIcon.Settings, screen == TvScreen.Settings, onSettings)
        }
    }
}

@Composable
private fun TvNavigationItem(
    label: String,
    icon: NavigationIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusScale(
                shape = shape,
                focusedBorder = TvColors.FocusRing,
                idleBorder = if (selected) TvColors.Accent.copy(alpha = 0.5f) else TvColors.Border,
            )
            .clip(shape)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick)
            .focusable(),
        shape = shape,
        color = when {
            focused -> TvColors.SurfaceRaised
            selected -> TvColors.AccentSoft
            else -> Color.Transparent
        },
        contentColor = when {
            focused -> TvColors.TextPrimary
            selected -> TvColors.AccentStrong
            else -> TvColors.TextSecondary
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            NavigationGlyph(
                icon = icon,
                tint = when {
                    focused -> TvColors.TextPrimary
                    selected -> TvColors.AccentStrong
                    else -> TvColors.TextSecondary
                },
                label = label,
            )
        }
    }
}

private enum class NavigationIcon(val glyph: String) {
    Home("\uE608"),
    Live("\uEB34"),
    PlatformLive("\uE6E6"),
    Recommend("\uEB7D"),
    Settings("\uE6F0"),
}

private val TvBoxIconFont = FontFamily(Font(R.font.tvbox_iconfont))

@Composable
private fun NavigationGlyph(
    icon: NavigationIcon,
    tint: Color,
    label: String,
) {
    val glyphSize = if (icon == NavigationIcon.Live) 22.sp else 26.sp
    Text(
        text = icon.glyph,
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = label },
        color = tint,
        fontFamily = TvBoxIconFont,
        fontSize = glyphSize,
        lineHeight = glyphSize,
    )
}

private fun TvScreen.isHomeDestination(): Boolean {
    return this == TvScreen.Home || this == TvScreen.Detail
}

@Composable
private fun HeaderActionButton(
    text: String,
    onClick: () -> Unit,
    legacyStyle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = if (legacyStyle) RoundedCornerShape(50) else RoundedCornerShape(10.dp)
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .tvFocusScale(
                shape = shape,
                focusedBorder = TvColors.FocusRing,
                idleBorder = TvColors.Border,
            )
            .clip(shape)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick)
            .focusable(),
        shape = shape,
        color = when {
            focused && legacyStyle -> TvColors.Accent
            focused -> TvColors.SurfaceRaised
            else -> TvColors.Surface
        },
        contentColor = if (focused && legacyStyle) TvColors.Background else TvColors.TextPrimary,
        tonalElevation = if (focused) 5.dp else 0.dp,
        shadowElevation = if (focused) 8.dp else 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = if (legacyStyle) 20.dp else 14.dp,
                vertical = if (legacyStyle) 10.dp else 8.dp,
            ),
            style = if (legacyStyle) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            fontWeight = if (focused || legacyStyle) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
fun LoadingState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

@Composable
fun CategoryPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .tvFocusScale(
                shape = shape,
                focusedBorder = TvColors.FocusRing,
                idleBorder = if (selected) TvColors.Accent.copy(alpha = 0.7f) else TvColors.Border,
            )
            .clip(shape)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick)
            .focusable(),
        shape = shape,
        color = when {
            focused -> TvColors.SurfaceRaised
            selected -> TvColors.AccentSoft
            else -> TvColors.Surface
        },
        contentColor = when {
            focused -> TvColors.TextPrimary
            selected -> TvColors.AccentStrong
            else -> TvColors.TextSecondary
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MoviePosterCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    posterAspectRatio: Float = 2f / 3f,
) {
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .tvFocusScale(
                shape = shape,
                focusedBorder = TvColors.FocusRing,
                idleBorder = TvColors.Border,
            )
            .clip(shape)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .background(if (focused) TvColors.SurfaceRaised else TvColors.Surface)
            .clickable(onClick = onClick)
            .focusable(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(posterAspectRatio)
                .background(TvColors.SurfaceRaised),
        ) {
            if (movie.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = "无海报",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (movie.remarks.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC080B0B)),
                            ),
                        )
                        .padding(8.dp),
                ) {
                    Text(
                        text = movie.remarks,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = movie.name,
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = movie.subtitle.ifBlank { movie.typeName.ifBlank { "影视" } },
            modifier = Modifier.padding(start = 10.dp, top = 3.dp, end = 10.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DoubanHotPosterCard(
    item: DoubanHotItem,
    resolving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    posterAspectRatio: Float = 0.78f,
) {
    val shape = RoundedCornerShape(8.dp)
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    var posterFailed by remember(item.doubanId, item.posterUrl) { mutableStateOf(item.posterUrl.isBlank()) }
    val imageRequest = remember(item.posterUrl) {
        ImageRequest.Builder(context)
            .data(item.posterUrl)
            .apply {
                val host = Uri.parse(item.posterUrl).host.orEmpty()
                if (host.endsWith(".doubanio.com", ignoreCase = true)) {
                    addHeader("Referer", DOUBAN_REFERER)
                    addHeader("User-Agent", DOUBAN_USER_AGENT)
                }
            }
            .build()
    }

    Column(
        modifier = modifier
            .tvFocusScale(
                shape = shape,
                focusedBorder = TvColors.FocusRing,
                idleBorder = TvColors.Border,
            )
            .clip(shape)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .background(if (focused) TvColors.SurfaceRaised else TvColors.Surface)
            .clickable(onClick = onClick)
            .focusable(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(posterAspectRatio)
                .background(TvColors.SurfaceRaised),
        ) {
            if (!posterFailed) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    onError = { posterFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = item.title,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.score > 0.0) {
                Text(
                    text = "豆瓣 ${"%.1f".format(item.score)}",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0xCC080B0B), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (resolving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xB3080B0B)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                        Text(
                            text = "正在查找资源...",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        Text(
            text = item.title,
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle.ifBlank { "豆瓣热播" },
            modifier = Modifier.padding(start = 10.dp, top = 3.dp, end = 10.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HistoryItemCard(
    item: WatchHistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .tvFocusScale(shape = shape)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .background(if (focused) Color(0xFF252525) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .focusable(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (item.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.movieName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = "无封面",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xB0000000))
                    .padding(8.dp),
            ) {
                Text(
                    text = item.episodeTitle,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        LinearProgressIndicator(
            progress = { item.progressPercent / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = item.movieName,
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${item.sourceName} / ${formatPlaybackPosition(item.positionMs)}",
            modifier = Modifier.padding(start = 10.dp, top = 3.dp, end = 10.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PosterImage(movie: Movie, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (movie.posterUrl.isNotBlank()) {
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("无海报", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PageSurface(content: @Composable (PaddingValues) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TvColors.Background,
        contentColor = TvColors.TextPrimary,
    ) {
        content(
            PaddingValues(
                horizontal = TvDimens.PageHorizontalPadding,
                vertical = TvDimens.PageVerticalPadding,
            ),
        )
    }
}

@Composable
fun InfoLine(label: String, value: String, modifier: Modifier = Modifier) {
    if (value.isBlank()) return
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.width(64.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SmallMeta(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun TinySpacer() {
    Spacer(modifier = Modifier.size(8.dp))
}

private fun formatPlaybackPosition(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
