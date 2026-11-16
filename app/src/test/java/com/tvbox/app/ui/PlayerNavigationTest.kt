package com.tvbox.app.ui

import org.junit.Assert.*
import org.junit.Test

class PlayerNavigationTest {
    @Test fun allSettingChildrenReturnToSettingsBeforeControlBar() {
        playerSettingsPanels.forEach { child ->
            assertEquals(PlayerPanel.Settings, child.parentPanel())
            assertEquals(PlayerPanel.None, child.parentPanel().parentPanel())
        }
    }

    @Test fun speedPickerIsRemovedAndNoneIsStable() {
        assertFalse(PlayerPanel.entries.any { it.name == "Speeds" })
        assertEquals(PlayerPanel.None, PlayerPanel.Settings.parentPanel())
        assertEquals(PlayerPanel.None, PlayerPanel.None.parentPanel())
    }

    @Test fun settingsRetainAllFourFeaturesWithoutAddingSpeedOrPlaybackActions() {
        assertEquals(listOf(PlayerPanel.Picture, PlayerPanel.Intro, PlayerPanel.Episodes, PlayerPanel.Sources), playerSettingsPanels)
        assertEquals(4, playerSettingsPanels.distinct().size)
        assertEquals(setOf(PlayerPanel.None, PlayerPanel.Settings, PlayerPanel.Picture, PlayerPanel.Intro,
            PlayerPanel.Episodes, PlayerPanel.Sources), PlayerPanel.entries.toSet())
    }

    @Test fun settingsDrawerMatchesTelevisionChannelPanelWidth() {
        listOf(1920, 960, 640, 360).forEach { width ->
            assertEquals(260f, playerSettingsWidthDp(width), 0.001f)
        }
    }

    @Test fun settingsDrawerNeverExceedsAvailableWidth() {
        assertEquals(160f, playerSettingsWidthDp(160), 0.001f)
        assertEquals(0f, playerSettingsWidthDp(0), 0.001f)
        assertEquals(0f, playerSettingsWidthDp(-1), 0.001f)
    }
}
