package com.tvbox.app.domain

data class AppSettings(
    val homeApiLineId: String = "liangzi",
    val checkUpdatesOnStartup: Boolean = true,
    val playbackAgentAutoSwitchEnabled: Boolean = true,
)
