package com.tvbox.app.domain

/** Playback intent survives media replacement, but never an interruption. */
class PlaybackInterruptionPolicy {
    var foreground: Boolean = true
        private set
    var playRequested: Boolean = true
        private set
    var interrupted: Boolean = false
        private set

    val playbackAllowed: Boolean get() = foreground && playRequested

    fun enterForeground() { foreground = true }
    fun leaveForeground() {
        foreground = false
        interrupt()
    }
    fun interrupt() {
        playRequested = false
        interrupted = true
    }
    fun pause() { playRequested = false }
    fun requestPlay(): Boolean {
        if (!foreground) return false
        playRequested = true
        interrupted = false
        return true
    }
}
