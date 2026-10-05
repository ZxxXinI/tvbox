package com.tvbox.app.domain

import java.util.ArrayDeque

enum class SlowBufferReason {
    StartupTooLong,
    ContinuousBufferTooLong,
    FrequentBuffering,
    CumulativeBufferTooLong,
}

data class PlaybackBufferDecision(
    val reason: SlowBufferReason,
)

data class PlaybackBufferReadyResult(
    val decision: PlaybackBufferDecision? = null,
    val shouldRecordPlaybackSuccess: Boolean = false,
)

class PlaybackBufferMonitor(
    private val continuousBufferThresholdMs: Long = DEFAULT_CONTINUOUS_BUFFER_THRESHOLD_MS,
    private val seekGraceMs: Long = DEFAULT_SEEK_GRACE_MS,
    private val frequentBufferWindowMs: Long = DEFAULT_FREQUENT_BUFFER_WINDOW_MS,
    private val frequentBufferCount: Int = DEFAULT_FREQUENT_BUFFER_COUNT,
    private val cumulativeBufferThresholdMs: Long = DEFAULT_CUMULATIVE_BUFFER_THRESHOLD_MS,
) {
    private val completedBuffers = ArrayDeque<CompletedBuffer>()
    private var hasReachedReady = false
    private var currentBufferStartedAtMs: Long? = null
    private var currentBufferFromSeek = false
    private var currentBufferIsStartup = false
    private var currentBufferIssueReported = false
    private var lastSeekAtMs: Long? = null
    private var seekCooling = false

    fun onMediaChanged() {
        completedBuffers.clear()
        hasReachedReady = false
        currentBufferStartedAtMs = null
        currentBufferFromSeek = false
        currentBufferIsStartup = false
        currentBufferIssueReported = false
        lastSeekAtMs = null
        seekCooling = false
    }

    fun onSeekStarted(nowMs: Long) {
        lastSeekAtMs = nowMs
        seekCooling = true
        currentBufferStartedAtMs = null
        currentBufferFromSeek = false
        currentBufferIsStartup = false
        currentBufferIssueReported = false
    }

    fun onPaused() {
        currentBufferStartedAtMs = null
        currentBufferFromSeek = false
        currentBufferIsStartup = false
        currentBufferIssueReported = false
    }

    fun onBuffering(playWhenReady: Boolean, nowMs: Long): PlaybackBufferDecision? {
        if (!playWhenReady) {
            onPaused()
            return null
        }

        if (currentBufferStartedAtMs == null) {
            currentBufferStartedAtMs = nowMs
            currentBufferFromSeek = seekCooling || isWithinSeekGrace(nowMs)
            currentBufferIsStartup = !hasReachedReady
            currentBufferIssueReported = false
        }

        if (seekCooling) return null

        val startedAtMs = currentBufferStartedAtMs ?: return null
        val durationMs = nowMs - startedAtMs
        if (currentBufferIssueReported) return null
        pruneCompletedBuffers(nowMs)
        val reason = when {
            durationMs >= continuousBufferThresholdMs -> if (currentBufferIsStartup) {
                SlowBufferReason.StartupTooLong
            } else SlowBufferReason.ContinuousBufferTooLong
            !currentBufferFromSeek && completedBuffers.size >= frequentBufferCount -> SlowBufferReason.FrequentBuffering
            !currentBufferFromSeek && completedBuffers.sumOf { it.durationMs } >= cumulativeBufferThresholdMs ->
                SlowBufferReason.CumulativeBufferTooLong
            else -> null
        } ?: return null
        currentBufferIssueReported = true
        return PlaybackBufferDecision(reason)
    }

    fun onReady(playWhenReady: Boolean, nowMs: Long): PlaybackBufferReadyResult {
        val startedAtMs = currentBufferStartedAtMs
        val wasFromSeek = currentBufferFromSeek
        val wasStartup = currentBufferIsStartup
        val issueAlreadyReported = currentBufferIssueReported
        val wasSeekCooling = seekCooling
        currentBufferStartedAtMs = null
        currentBufferFromSeek = false
        currentBufferIsStartup = false
        currentBufferIssueReported = false
        hasReachedReady = true
        seekCooling = false

        if (!playWhenReady) return PlaybackBufferReadyResult()
        if (startedAtMs == null) {
            return PlaybackBufferReadyResult(shouldRecordPlaybackSuccess = true)
        }
        if (issueAlreadyReported) return PlaybackBufferReadyResult()

        if (wasSeekCooling || wasFromSeek) {
            return PlaybackBufferReadyResult(shouldRecordPlaybackSuccess = true)
        }

        val durationMs = nowMs - startedAtMs
        if (wasStartup) {
            return PlaybackBufferReadyResult(shouldRecordPlaybackSuccess = true)
        }

        recordCompletedBuffer(endedAtMs = nowMs, durationMs = durationMs.coerceAtLeast(0L))
        // Do not switch a source that has just recovered. Historical penalties are
        // evaluated only if another buffering interval actually begins.
        return PlaybackBufferReadyResult(shouldRecordPlaybackSuccess = true)
    }

    private fun recordCompletedBuffer(endedAtMs: Long, durationMs: Long) {
        completedBuffers.addLast(CompletedBuffer(endedAtMs = endedAtMs, durationMs = durationMs))
        pruneCompletedBuffers(endedAtMs)
    }

    private fun pruneCompletedBuffers(nowMs: Long) {
        val earliestKeptAtMs = nowMs - frequentBufferWindowMs
        while (completedBuffers.peekFirst()?.endedAtMs?.let { it < earliestKeptAtMs } == true) {
            completedBuffers.removeFirst()
        }
    }

    private fun isWithinSeekGrace(nowMs: Long): Boolean {
        val seekAtMs = lastSeekAtMs ?: return false
        return nowMs - seekAtMs in 0..seekGraceMs
    }

    private data class CompletedBuffer(
        val endedAtMs: Long,
        val durationMs: Long,
    )

    companion object {
        const val DEFAULT_CONTINUOUS_BUFFER_THRESHOLD_MS = 5_000L
        const val DEFAULT_SEEK_GRACE_MS = 3_000L
        const val DEFAULT_FREQUENT_BUFFER_WINDOW_MS = 60_000L
        const val DEFAULT_FREQUENT_BUFFER_COUNT = 3
        const val DEFAULT_CUMULATIVE_BUFFER_THRESHOLD_MS = 8_000L
    }
}
