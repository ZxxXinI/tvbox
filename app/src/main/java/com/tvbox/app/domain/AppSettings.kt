package com.tvbox.app.domain

data class AppSettings(
    val checkUpdatesOnStartup: Boolean = true,
    val playbackAgentAutoSwitchEnabled: Boolean = true,
)
