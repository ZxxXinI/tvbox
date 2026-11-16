package com.tvbox.app.ui

internal data class SearchShortcutResult(val consumed: Boolean, val openScanner: Boolean)

// 只处理菜单键的一次完整按下／松开，避免长按、取消及跨页面松开产生误操作。
internal class SearchMenuShortcut {
    private var pressedOnSearch = false

    fun keyDown(onSearch: Boolean, repeatCount: Int): Boolean {
        if (!onSearch) {
            if (repeatCount == 0) reset()
            return pressedOnSearch
        }
        if (repeatCount == 0) pressedOnSearch = true
        return true
    }

    fun keyUp(onSearch: Boolean, dialogOpen: Boolean, cancelled: Boolean): SearchShortcutResult {
        val result = SearchShortcutResult(
            consumed = pressedOnSearch || onSearch,
            openScanner = pressedOnSearch && onSearch && !dialogOpen && !cancelled,
        )
        reset()
        return result
    }

    fun reset() { pressedOnSearch = false }
}
