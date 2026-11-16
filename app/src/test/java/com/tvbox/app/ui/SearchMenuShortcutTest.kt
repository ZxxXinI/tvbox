package com.tvbox.app.ui

import org.junit.Assert.*
import org.junit.Test

class SearchMenuShortcutTest {
    @Test fun opensOnReleaseOnlyOnce() {
        val shortcut = SearchMenuShortcut()
        assertTrue(shortcut.keyDown(true, 0))
        assertEquals(SearchShortcutResult(true, true), shortcut.keyUp(true, false, false))
        assertEquals(SearchShortcutResult(true, false), shortcut.keyUp(true, false, false))
    }

    @Test fun holdingMenuDoesNotRepeatOrRegenerateDialog() {
        val shortcut = SearchMenuShortcut()
        assertTrue(shortcut.keyDown(true, 0))
        repeat(12) { assertTrue(shortcut.keyDown(true, it + 1)) }
        assertTrue(shortcut.keyUp(true, false, false).openScanner)
        shortcut.keyDown(true, 0)
        assertFalse(shortcut.keyUp(true, true, false).openScanner)
    }

    @Test fun playbackAndHomeMenuPassThrough() {
        val shortcut = SearchMenuShortcut()
        assertFalse(shortcut.keyDown(false, 0))
        assertEquals(SearchShortcutResult(false, false), shortcut.keyUp(false, false, false))
    }

    @Test fun releasingAfterPageChangeDoesNotTriggerPlaybackSpeed() {
        val shortcut = SearchMenuShortcut()
        shortcut.keyDown(true, 0)
        assertTrue(shortcut.keyDown(false, 1))
        assertEquals(SearchShortcutResult(true, false), shortcut.keyUp(false, false, false))
        assertFalse(shortcut.keyDown(false, 0))
    }

    @Test fun cancelledOrBackgroundKeySequenceCannotOpenScanner() {
        val shortcut = SearchMenuShortcut()
        shortcut.keyDown(true, 0)
        assertFalse(shortcut.keyUp(true, false, true).openScanner)
        shortcut.keyDown(true, 0)
        shortcut.reset()
        assertFalse(shortcut.keyUp(true, false, false).openScanner)
    }

    @Test fun repeatedKeyFromAnotherPageDoesNotArmNewShortcut() {
        val shortcut = SearchMenuShortcut()
        shortcut.keyDown(false, 0)
        shortcut.keyDown(true, 3)
        assertFalse(shortcut.keyUp(true, false, false).openScanner)
    }

    @Test fun freshPlaybackPressRecoversFromLostSearchKeyUp() {
        val shortcut = SearchMenuShortcut()
        shortcut.keyDown(true, 0)
        assertFalse(shortcut.keyDown(false, 0))
        assertEquals(SearchShortcutResult(false, false), shortcut.keyUp(false, false, false))
    }
}
