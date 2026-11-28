package com.tvbox.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tvbox.app.domain.DoubanHotItem
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.ui.components.ShortcutKeyLabel
import com.tvbox.app.ui.components.formatPlaybackPosition
import com.tvbox.app.ui.components.rememberDoubanPosterRequest
import com.tvbox.app.ui.theme.TvColors

private val HomePillShape = RoundedCornerShape(50)
private val HomeCoverShape = RoundedCornerShape(10.dp)
private val HomePillBackground = Color(0xFF1A1D20)
private val HomeMuted = Color(0xFF9199A4)
private val HomeBorder = Color.White.copy(alpha = 0.13f)

@Composable
internal fun DefaultHomeHeader(
    sourceName: String?,
    fontScale: Float,
    actions: TvBoxViewModel,
    firstActionModifier: Modifier,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        if (maxWidth >= (784 * fontScale).dp) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HomeBrand(sourceName)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    HomeShortcuts(actions, firstActionModifier)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeBrand(sourceName)
                HomeShortcuts(actions, firstActionModifier)
            }
        }
    }
}

@Composable
private fun HomeBrand(sourceName: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                Modifier.size(37.dp).background(TvColors.Accent, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                HomeGlyph(HomeIcon.Play, MaterialTheme.colorScheme.onPrimary)
            }
            Text("TVBox", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        if (!sourceName.isNullOrBlank()) {
            Text("首页数据源 · $sourceName", style = MaterialTheme.typography.labelMedium, color = HomeMuted)
        }
    }
}

@Composable
private fun HomeShortcuts(actions: TvBoxViewModel, firstActionModifier: Modifier) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(3.dp).focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeShortcut("历史", 1, HomeIcon.History, actions::openHistory, firstActionModifier)
        HomeShortcut("搜索", 2, HomeIcon.Search, actions::openSearch)
        HomeShortcut("AI 推荐", 3, HomeIcon.Recommend, actions::openAiRecommend)
        HomeShortcut("电视", 4, HomeIcon.Tv, actions::openLive)
        HomeShortcut("直播", 5, HomeIcon.Live, actions::openPlatformLive)
        HomeShortcut("设置", 6, HomeIcon.Settings, actions::openSettings)
    }
}

@Composable
private fun HomeShortcut(label: String, key: Int, icon: HomeIcon, onClick: () -> Unit, modifier: Modifier = Modifier) {
    HomePill(modifier = modifier, onClick = onClick) {
        HomeGlyph(icon, HomeMuted)
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        ShortcutKeyLabel(key)
    }
}

@Composable
internal fun DefaultHomeCategory(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomePill(modifier = modifier, selected = selected, onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else TvColors.TextPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun HomePill(
    modifier: Modifier,
    selected: Boolean? = null,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val input = if (selected == null) Modifier.clickable(role = Role.Button, onClick = onClick)
        else Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick)
    Row(
        modifier.onFocusChanged { focused = it.isFocused }
            .homeFocusOutline(HomePillShape, focused, filled = selected == true)
            .clip(HomePillShape)
            .background(if (selected == true) TvColors.Accent else HomePillBackground)
            .then(input)
            .heightIn(min = 38.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
internal fun DefaultHomeFeedCount(total: Int, page: Int) {
    Text("共 $total 部 · 第 $page 页", color = HomeMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
}

@Composable
internal fun DefaultHomeSectionTitle(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp, 19.dp).background(TvColors.Accent, HomePillShape))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun DefaultHomeMovieCard(movie: Movie, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val subtitle = listOf(movie.year, movie.area).filter(String::isNotBlank).joinToString(" · ")
    HomeCard(
        title = movie.name,
        image = movie.posterUrl,
        modifier = modifier,
        onClick = onClick,
        subtitle = subtitle.ifBlank { movie.typeName },
    ) {
        if (movie.remarks.isNotBlank()) HomeBadge(movie.remarks, accent = true)
    }
}

@Composable
internal fun DefaultHomeDoubanCard(
    item: DoubanHotItem,
    resolving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = if (item.posterUrl.isNotBlank()) rememberDoubanPosterRequest(item.posterUrl) else null
    HomeCard(
        title = item.title,
        image = image,
        modifier = modifier,
        onClick = onClick,
        subtitle = item.subtitle.split(" / ").take(2).joinToString(" · ").ifBlank { "豆瓣热播" },
    ) {
        if (item.score > 0) HomeBadge("豆瓣 ${"%.1f".format(item.score)}", accent = true)
        if (resolving) {
            Column(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("正在查找资源…", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun DefaultHomeResumeCard(item: WatchHistoryItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val episode = item.episodeTitle.ifBlank { "第 ${item.episodeIndex + 1} 集" }
    val duration = if (item.durationMs > 0) {
        val minutesLeft = (item.durationMs - item.positionMs).coerceAtLeast(0) / 60_000
        if (minutesLeft == 0L) "还剩不到 1 分钟" else "还剩 $minutesLeft 分钟"
    } else {
        "已看到 ${formatPlaybackPosition(item.positionMs)}"
    }
    HomeCard(
        title = item.movieName,
        image = item.posterUrl,
        modifier = modifier,
        onClick = onClick,
        aspectRatio = 438f / 220f,
    ) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.16f),
            0.65f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.68f),
        )))
        HomeBadge("$episode · $duration")
        if (item.durationMs > 0) {
            val progress = (item.positionMs.toDouble() / item.durationMs).coerceIn(0.0, 1.0).toFloat()
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 9.dp)
                    .height(4.dp).background(Color.White.copy(alpha = 0.32f), HomePillShape)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
            ) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).background(TvColors.Accent, HomePillShape))
            }
        }
    }
}

@Composable
private fun HomeCard(
    title: String,
    image: Any?,
    modifier: Modifier,
    onClick: () -> Unit,
    subtitle: String = "",
    aspectRatio: Float = 284f / 256f,
    overlays: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var imageFailed by remember(image) { mutableStateOf(image == null || image == "") }
    Column(
        modifier.onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(aspectRatio)
                .homeFocusOutline(HomeCoverShape, focused)
                .clip(HomeCoverShape)
                .background(HomePillBackground),
            contentAlignment = Alignment.Center,
        ) {
            if (imageFailed) {
                Text(title, Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium,
                    color = TvColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { imageFailed = true },
                )
            }
            overlays()
        }
        Text(
            title, Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = HomeMuted, style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BoxScope.HomeBadge(text: String, accent: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            .background(if (accent) TvColors.Accent else Color(0xDF141718), HomePillShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = if (accent) MaterialTheme.colorScheme.onPrimary else Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Modifier.homeFocusOutline(shape: Shape, focused: Boolean, filled: Boolean = false): Modifier {
    val accent = TvColors.Accent
    val focus = if (focused) {
        Modifier.dropShadow(shape, Shadow(radius = 10.dp, color = accent.copy(alpha = 0.34f), spread = 1.dp))
    } else Modifier
    return this.then(focus).border(
        width = if (focused) 2.dp else 1.dp,
        color = when {
            focused && filled -> Color.White
            focused -> accent
            filled -> accent
            else -> HomeBorder
        },
        shape = shape,
    )
}

private enum class HomeIcon(val data: String) {
    Play("M8 4.5L20 12 8 19.5Z"),
    History("M21 12a9 9 0 1 1-18 0 9 9 0 1 1 18 0ZM12 7v5l3 2"),
    Search("M17 10.5a6.5 6.5 0 1 1-13 0 6.5 6.5 0 1 1 13 0ZM15.5 15.5l4.5 4.5"),
    Recommend("M11 2l2.4 6.6L20 11l-6.6 2.4L11 20l-2.4-6.6L2 11l6.6-2.4ZM19.5 16v6m-3-3h6"),
    Tv("M5 4h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2ZM12 17v4m-4 0h8"),
    Live("M5 5h9a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM16 10l5-3v10l-5-3M8 9.5l4 2.5-4 2.5z"),
    Settings("M3 6h10m4 0h4M3 12h4m4 0h10M3 18h12m4 0h2M17 6a2 2 0 1 1-4 0 2 2 0 1 1 4 0ZM11 12a2 2 0 1 1-4 0 2 2 0 1 1 4 0ZM19 18a2 2 0 1 1-4 0 2 2 0 1 1 4 0Z"),
}

@Composable
private fun HomeGlyph(icon: HomeIcon, tint: Color) {
    val path = remember(icon) { PathParser().parsePathString(icon.data).toPath() }
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            drawPath(path, tint, style = if (icon == HomeIcon.Play) Fill
                else Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
