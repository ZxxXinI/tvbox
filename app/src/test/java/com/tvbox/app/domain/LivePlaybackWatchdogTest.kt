package com.tvbox.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackWatchdogTest {
    private fun watchdog(): LivePlaybackWatchdog {
        return LivePlaybackWatchdog(noProgressThresholdMs = 4_000L)
    }

    @Test
    fun advancingPositionDoesNotReportAnIssue() {
        val watchdog = watchdog()

        assertFalse(watchdog.onSample(isPlaying = true, positionMs = 100L, nowMs = 0L))
        assertFalse(watchdog.onSample(isPlaying = true, positionMs = 200L, nowMs = 10_000L))
    }

    @Test
    fun unchangedPositionReportsAfterThreshold() {
        val watchdog = watchdog()

        assertFalse(watchdog.onSample(isPlaying = true, positionMs = 100L, nowMs = 0L))
        assertFalse(watchdog.onSample(isPlaying = true, positionMs = 100L, nowMs = 3_999L))
        assertTrue(watchdog.onSample(isPlaying = true, positionMs = 100L, nowMs = 4_000L))
        assertFalse(watchdog.onSample(isPlaying = true, positionMs = 100L, nowMs = 8_000L))
    }

    @Test
    fun pauseResetsStallTimer() {
        val watchdog = watchdog()

        assertFalse(watchdog.onSample(isPlaying = true, positionMs = 100L, nowMs = 0L))
        assertFalse(watchdog.onSample(isPlaying = false, positionMs = 100L, nowMs = 10_000L))
        assertFalse(watchdog.onSample(isPlaying = true, positionMs = 100L, nowMs = 10_001L))
        assertTrue(watchdog.onSample(isPlaying = true, positionMs = 100L, nowMs = 14_001L))
    }

    @Test
    fun mediaChangeResetsReportedIssue() {
        val watchdog = watchdog()

        assertFalse(watchdog.onSample(isPlaying = true, positionMs = 100L, nowMs = 0L))
        assertTrue(watchdog.onSample(isPlaying = true, positionMs = 100L, nowMs = 4_000L))
        watchdog.onMediaChanged()

        assertFalse(watchdog.onSample(isPlaying = true, positionMs = 500L, nowMs = 5_000L))
        assertTrue(watchdog.onSample(isPlaying = true, positionMs = 500L, nowMs = 9_000L))
    }
}
