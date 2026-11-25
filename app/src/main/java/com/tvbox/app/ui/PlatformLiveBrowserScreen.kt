package com.tvbox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tvbox.app.domain.PlatformLiveCategory
import com.tvbox.app.domain.PlatformLiveParentCategory
import com.tvbox.app.domain.PlatformLiveSite
import com.tvbox.app.domain.TvTheme
import com.tvbox.app.ui.components.ErrorState
import com.tvbox.app.ui.components.LoadingState
import com.tvbox.app.ui.components.PageSurface
import com.tvbox.app.ui.theme.TvColors

@Composable
internal fun PlatformLiveBrowserScreen(state: TvBoxUiState, actions: TvBoxViewModel) {
    val cinema = state.appSettings.theme == TvTheme.Cinema
    val favorites = state.platformLiveDestination == PlatformLiveDestination.Favorites
    val favoriteFocus = remember { FocusRequester() }
    var restoreRoomFocus by remember { mutableStateOf(true) }
    val favoritesEmpty = favorites && state.platformLiveRooms.isEmpty()
    LaunchedEffect(favoritesEmpty) {
        if (favoritesEmpty || state.platformLiveSites.isEmpty()) favoriteFocus.requestPlayerControlFocus()
    }

    val onFavorites = {
        restoreRoomFocus = false
        if (favorites) actions.returnToPlatformLiveBrowse() else actions.openPlatformLiveFavorites()
    }
    val onSite = { site: PlatformLiveSite ->
        restoreRoomFocus = false
        actions.selectPlatformLiveSite(site)
    }
    val onGroup = { group: PlatformLiveParentCategory ->
        restoreRoomFocus = false
        actions.selectPlatformLiveParentCategory(group)
    }
    val onCategory = { category: PlatformLiveCategory ->
        if (category.id != state.platformLiveSelectedCategory?.id || state.platformLiveError != null) {
            restoreRoomFocus = false
            actions.selectPlatformLiveCategory(category)
        }
    }

    PageSurface { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val usePlatformRail = cinema && maxWidth >= 620.dp
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (cinema) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("TVBox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(18.dp).background(TvColors.Border))
                            Text("直播", style = MaterialTheme.typography.titleMedium, color = TvColors.TextSecondary)
                        }
                    } else {
                        Text("直播(5)", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    if (!usePlatformRail) {
                        PlatformLiveFavoriteOption(favorites, favoriteFocus, onFavorites)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (usePlatformRail) {
                    HorizontalDivider(color = TvColors.Border)
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        PlatformLiveSiteRail(state, favorites, favoriteFocus, onSite, onFavorites)
                        Box(Modifier.fillMaxHeight().width(1.dp).background(TvColors.Border))
                        PlatformLiveBrowserContent(
                            state, actions, cinema, favorites, restoreRoomFocus, onGroup, onCategory,
                            Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, top = 12.dp),
                        )
                    }
                } else {
                    if (state.platformLiveSites.isNotEmpty()) {
                        PlatformLiveSiteTabs(state, favorites, onSite)
                    }
                    Spacer(Modifier.height(12.dp))
                    PlatformLiveBrowserContent(
                        state, actions, cinema, favorites, restoreRoomFocus, onGroup, onCategory,
                        Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformLiveBrowserContent(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
    cinema: Boolean,
    favorites: Boolean,
    restoreRoomFocus: Boolean,
    onGroup: (PlatformLiveParentCategory) -> Unit,
    onCategory: (PlatformLiveCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val site = state.platformLiveSelectedSite
    val group = state.platformLiveSelectedParentCategory
    val category = state.platformLiveSelectedCategory
    val hasGroups = !favorites && state.platformLiveParentCategories.isNotEmpty()
    val showRooms = favorites || state.platformLiveDestination == PlatformLiveDestination.Rooms

    Column(modifier) {
        if (hasGroups && !cinema) {
            Row(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlatformLiveGroupMenu(state.platformLiveParentCategories, group, onSelect = onGroup)
                PlatformLiveCategoryTabs(state, onCategory, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        if (cinema && hasGroups) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                site?.let { Text(it.name + " /", style = MaterialTheme.typography.bodySmall, color = TvColors.TextSecondary) }
                PlatformLiveGroupMenu(state.platformLiveParentCategories, group, compact = true, onSelect = onGroup)
            }
            Spacer(Modifier.height(4.dp))
        }
        if (showRooms) {
            val title = if (favorites) "收藏的直播间" else category?.name ?: "直播间"
            PlatformLiveRoomHeading(state, actions, title, favorites, cinema)
        }
        if (cinema && hasGroups) {
            Spacer(Modifier.height(5.dp))
            PlatformLiveCategoryTabs(state, onCategory, Modifier.fillMaxWidth())
        }
        when {
            showRooms -> key(favorites, site?.id, category?.id) {
                PlatformLiveRoomsPanel(
                    state, actions, favoritesMode = favorites, modifier = Modifier.weight(1f),
                    restoreRoomFocus = restoreRoomFocus,
                )
            }
            state.platformLiveLoading -> LoadingState(
                if (site == null) "正在加载直播平台" else "正在加载直播分类",
                Modifier.weight(1f),
            )
            state.platformLiveError != null -> ErrorState(
                state.platformLiveError, actions::refreshPlatformLive, Modifier.weight(1f),
            )
            else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("选择直播分类", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PlatformLiveSiteRail(
    state: TvBoxUiState,
    favorites: Boolean,
    favoriteFocus: FocusRequester,
    onSite: (PlatformLiveSite) -> Unit,
    onFavorites: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.width(126.dp).fillMaxHeight().background(TvColors.Surface).focusGroup(),
        contentPadding = PaddingValues(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(state.platformLiveSites, key = { it.id }) { site ->
            PlatformLiveOption(
                text = site.name,
                selected = !favorites && site.id == state.platformLiveSelectedSite?.id,
                modifier = Modifier.fillMaxWidth(),
                leading = true,
                onClick = { onSite(site) },
            )
        }
        item(key = "favorites") {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (state.platformLiveSites.isNotEmpty()) HorizontalDivider(color = TvColors.Border)
                PlatformLiveFavoriteOption(favorites, favoriteFocus, onFavorites, inRail = true)
            }
        }
    }
}

@Composable
private fun PlatformLiveSiteTabs(state: TvBoxUiState, favorites: Boolean, onSite: (PlatformLiveSite) -> Unit) {
    HorizontalDivider(color = TvColors.Border)
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(TvColors.Surface).focusGroup(),
        contentPadding = PaddingValues(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.platformLiveSites, key = { it.id }) { site ->
            PlatformLiveOption(
                text = site.name,
                selected = !favorites && site.id == state.platformLiveSelectedSite?.id,
                platform = true,
                onClick = { onSite(site) },
            )
        }
    }
    HorizontalDivider(color = TvColors.Border)
}

@Composable
private fun PlatformLiveFavoriteOption(
    selected: Boolean,
    focus: FocusRequester,
    onClick: () -> Unit,
    inRail: Boolean = false,
) {
    val modifier = Modifier.focusRequester(focus)
    PlatformLiveOption(
        text = if (selected) "★ 收藏" else "☆ 收藏",
        selected = selected,
        modifier = if (inRail) modifier.fillMaxWidth() else modifier,
        leading = inRail,
        onClick = onClick,
    )
}

@Composable
private fun PlatformLiveCategoryTabs(
    state: TvBoxUiState,
    onCategory: (PlatformLiveCategory) -> Unit,
    modifier: Modifier,
) {
    val groupId = state.platformLiveSelectedParentCategory?.id
    val selectedId = state.platformLiveSelectedCategory?.id
    val categories = remember(groupId, state.platformLiveCategories) {
        state.platformLiveCategories.filter { it.parentId == groupId }
    }
    key(state.platformLiveSelectedSite?.id, groupId) {
        val row = rememberLazyListState()
        LaunchedEffect(selectedId, categories) {
            val index = categories.indexOfFirst { it.id == selectedId }
            if (index >= 0 && row.layoutInfo.visibleItemsInfo.none { it.index == index }) row.scrollToItem(index)
        }
        LazyRow(
            modifier = modifier.focusGroup(),
            state = row,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                PlatformLiveOption(category.name, category.id == selectedId, onClick = { onCategory(category) })
            }
        }
    }
}

@Composable
private fun PlatformLiveRoomHeading(
    state: TvBoxUiState,
    actions: TvBoxViewModel,
    title: String,
    favorites: Boolean,
    cinema: Boolean,
) {
    val style = if (cinema) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val allowSingleRow = !favorites && maxWidth >= if (cinema) 565.dp else 650.dp
        if (allowSingleRow) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, modifier = Modifier.weight(1f), style = style, maxLines = 2, overflow = TextOverflow.Ellipsis)
                PlatformLiveRoomSearch(state, actions, Modifier.width(if (cinema) 325.dp else 360.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = style)
                if (!favorites) PlatformLiveRoomSearch(state, actions)
            }
        }
    }
}

@Composable
private fun PlatformLiveGroupMenu(
    groups: List<PlatformLiveParentCategory>,
    selectedGroup: PlatformLiveParentCategory?,
    compact: Boolean = false,
    onSelect: (PlatformLiveParentCategory) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var restoreAnchor by remember { mutableStateOf(false) }
    val anchorFocus = remember { FocusRequester() }
    val selectedItemFocus = remember { FocusRequester() }
    LaunchedEffect(expanded) {
        if (!expanded && restoreAnchor) {
            restoreAnchor = false
            anchorFocus.requestPlayerControlFocus()
        }
    }
    val dismiss = {
        expanded = false
        restoreAnchor = true
    }
    Box {
        PlatformLiveOption(
            text = "${selectedGroup?.name ?: "一级分类"} ▾",
            selected = expanded,
            modifier = Modifier.focusRequester(anchorFocus),
            quiet = compact,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = dismiss,
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            LaunchedEffect(Unit) { selectedItemFocus.requestPlayerControlFocus() }
            groups.forEachIndexed { index, group ->
                val selected = group.id == selectedGroup?.id
                val isInitial = selected || (selectedGroup == null && index == 0)
                var focused by remember(group.id) { mutableStateOf(false) }
                DropdownMenuItem(
                    text = {
                        Text(
                            group.name,
                            color = if (selected || focused) TvColors.AccentStrong else TvColors.TextPrimary,
                        )
                    },
                    onClick = {
                        dismiss()
                        onSelect(group)
                    },
                    modifier = (if (isInitial) Modifier.focusRequester(selectedItemFocus) else Modifier)
                        .onFocusChanged { focused = it.isFocused }
                        .background(if (focused) TvColors.AccentSoft else Color.Transparent)
                        .semantics { this.selected = selected },
                )
            }
        }
    }
}

@Composable
private fun PlatformLiveOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    platform: Boolean = false,
    leading: Boolean = false,
    quiet: Boolean = false,
) {
    val shape = RoundedCornerShape(7.dp)
    var focused by remember { mutableStateOf(false) }
    val color = if (selected || focused) TvColors.AccentStrong else TvColors.TextPrimary
    Column(
        modifier = modifier
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused -> TvColors.FocusRing
                    selected && !platform -> TvColors.Accent
                    platform || quiet -> Color.Transparent
                    else -> TvColors.Border
                },
                shape,
            )
            .background(
                when {
                    (!platform && selected) || focused -> TvColors.AccentSoft
                    platform || quiet -> Color.Transparent
                    else -> TvColors.Surface
                },
            )
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = if (quiet) 38.dp else 44.dp),
        horizontalAlignment = if (leading) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = when {
                platform -> 15.dp
                quiet -> 6.dp
                else -> 12.dp
            }, vertical = if (quiet) 5.dp else 8.dp),
            color = color,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        if (platform) {
            Box(Modifier.width(34.dp).height(2.dp).background(if (selected) TvColors.Accent else Color.Transparent))
        }
    }
}
