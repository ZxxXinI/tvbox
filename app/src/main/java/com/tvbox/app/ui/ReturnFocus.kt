package com.tvbox.app.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

internal class ReturnFocus(
    private val savedKey: MutableState<String?>,
    private val savedIndex: MutableState<Int>,
) {
    val lastKey: String? get() = savedKey.value
    val lastIndex: Int get() = savedIndex.value
    private val requesters = mutableMapOf<String, FocusRequester>()
    fun reset() { savedKey.value = null; savedIndex.value = 0 }
    fun mark(key: String, index: Int) { savedKey.value = key; savedIndex.value = index }
    fun modifier(key: String, index: Int): Modifier {
        val requester = requesters.getOrPut(key) { FocusRequester() }
        return Modifier.focusRequester(requester).onFocusChanged {
            if (it.isFocused) mark(key, index)
        }
    }
    fun request(key: String): Boolean = runCatching { requesters[key]?.requestFocus() ?: false }.getOrDefault(false)
}

@Composable
internal fun rememberReturnFocus(): ReturnFocus {
    val key = rememberSaveable { mutableStateOf<String?>(null) }
    val index = rememberSaveable { mutableIntStateOf(0) }
    return remember { ReturnFocus(key, index) }
}

@Composable
internal fun RestoreGridFocus(focus: ReturnFocus, grid: LazyGridState, items: List<Pair<String, Int>>) {
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(items.isNotEmpty()) {
        if (restored || items.isEmpty() || focus.lastKey == null) return@LaunchedEffect
        val target = items.firstOrNull { it.first == focus.lastKey }
            ?: items.minByOrNull { kotlin.math.abs(it.second - focus.lastIndex) } ?: return@LaunchedEffect
        withFrameNanos { }
        if (grid.layoutInfo.visibleItemsInfo.none { it.index == target.second }) grid.scrollToItem(target.second)
        for (attempt in 0..12) {
            withFrameNanos { }
            if (focus.request(target.first)) break
        }
        restored = true
    }
}
