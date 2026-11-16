package com.tvbox.app.ui

import kotlin.math.abs

private val playbackSpeeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

internal fun nextPlaybackSpeed(currentSpeed: Float): Float {
    val currentIndex = playbackSpeeds.indexOfFirst { abs(it - currentSpeed) < 0.01f }
    return playbackSpeeds[(currentIndex + 1).coerceAtLeast(0) % playbackSpeeds.size]
}

internal fun formatPlaybackSpeed(speed: Float): String =
    if (speed == 2f) "2.0x" else "${speed.toString().trimEnd('0').trimEnd('.')}x"
