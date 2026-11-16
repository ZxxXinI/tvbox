package com.tvbox.app.ui

internal enum class PlayerPanel {
    None, Settings, Episodes, Sources, Picture, Intro;

    fun parentPanel(): PlayerPanel = when (this) {
        Episodes, Sources, Picture, Intro -> Settings
        Settings, None -> None
    }
}

internal val playerSettingsPanels = listOf(
    PlayerPanel.Picture, PlayerPanel.Intro, PlayerPanel.Episodes, PlayerPanel.Sources,
)

internal fun playerSettingsWidthDp(availableWidthDp: Int): Float {
    val available = availableWidthDp.coerceAtLeast(0).toFloat()
    return 260f.coerceAtMost(available)
}
