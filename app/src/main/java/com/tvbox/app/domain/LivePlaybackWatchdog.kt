package com.tvbox.app.domain

class LivePlaybackWatchdog(
    private val noProgressThresholdMs: Long = DEFAULT_NO_PROGRESS_THRESHOLD_MS,
) {
    private var lastPositionMs: Long? = null
    private var lastProgressAtMs: Long? = null
    private var issueReported = false

    fun onMediaChanged() {
        reset()
    }

    fun onPaused() {
        reset()
    }

    fun onSample(
        isPlaying: Boolean,
        positionMs: Long,
        nowMs: Long,
    ): Boolean {
        if (!isPlaying) {
            reset()
            return false
        }

        val previousPositionMs = lastPositionMs
        if (previousPositionMs == null || previousPositionMs != positionMs) {
            lastPositionMs = positionMs
            lastProgressAtMs = nowMs
            issueReported = false
            return false
        }

        val lastProgressAtMs = lastProgressAtMs ?: nowMs.also {
            this.lastProgressAtMs = it
        }
        if (!issueReported && nowMs - lastProgressAtMs >= noProgressThresholdMs) {
            issueReported = true
            return true
        }
        return false
    }

    private fun reset() {
        lastPositionMs = null
        lastProgressAtMs = null
        issueReported = false
    }

    companion object {
        const val DEFAULT_NO_PROGRESS_THRESHOLD_MS = 4_000L
    }
}
