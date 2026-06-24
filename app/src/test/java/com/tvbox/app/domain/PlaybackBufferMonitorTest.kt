package com.tvbox.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBufferMonitorTest {
    private fun monitor(): PlaybackBufferMonitor {
        return PlaybackBufferMonitor(
            continuousBufferThresholdMs = 5_000L,
            seekGraceMs = 3_000L,
            frequentBufferWindowMs = 60_000L,
            frequentBufferCount = 3,
            cumulativeBufferThresholdMs = 8_000L,
        )
    }

    @Test
    fun startupBufferUnderThresholdRecordsSuccessWithoutPenalty() {
        val monitor = monitor()

        assertNull(monitor.onBuffering(playWhenReady = true, nowMs = 0L))
        val result = monitor.onReady(playWhenReady = true, nowMs = 3_000L)

        assertNull(result.decision)
        assertTrue(result.shouldRecordPlaybackSuccess)
    }

    @Test
    fun startupBufferOverThresholdReportsSlowBuffer() {
        val monitor = monitor()

        assertNull(monitor.onBuffering(playWhenReady = true, nowMs = 0L))
        val decision = monitor.onBuffering(playWhenReady = true, nowMs = 5_000L)

        assertEquals(SlowBufferReason.StartupTooLong, decision?.reason)
    }

    @Test
    fun continuousBufferOverThresholdReportsSlowBufferAfterReady() {
        val monitor = monitor()
        monitor.onBuffering(playWhenReady = true, nowMs = 0L)
        monitor.onReady(playWhenReady = true, nowMs = 1_000L)

        monitor.onBuffering(playWhenReady = true, nowMs = 10_000L)
        val result = monitor.onReady(playWhenReady = true, nowMs = 15_100L)

        assertEquals(SlowBufferReason.ContinuousBufferTooLong, result.decision?.reason)
        assertFalse(result.shouldRecordPlaybackSuccess)
    }

    @Test
    fun frequentShortBuffersReportSlowBuffer() {
        val monitor = monitor()
        monitor.onBuffering(playWhenReady = true, nowMs = 0L)
        monitor.onReady(playWhenReady = true, nowMs = 500L)

        repeat(2) { index ->
            val startAt = 10_000L + index * 10_000L
            monitor.onBuffering(playWhenReady = true, nowMs = startAt)
            val result = monitor.onReady(playWhenReady = true, nowMs = startAt + 1_000L)
            assertNull(result.decision)
            assertTrue(result.shouldRecordPlaybackSuccess)
        }
        monitor.onBuffering(playWhenReady = true, nowMs = 30_000L)
        val result = monitor.onReady(playWhenReady = true, nowMs = 31_000L)

        assertEquals(SlowBufferReason.FrequentBuffering, result.decision?.reason)
        assertFalse(result.shouldRecordPlaybackSuccess)
    }

    @Test
    fun cumulativeShortBuffersReportSlowBuffer() {
        val monitor = PlaybackBufferMonitor(
            continuousBufferThresholdMs = 5_000L,
            seekGraceMs = 3_000L,
            frequentBufferWindowMs = 60_000L,
            frequentBufferCount = 10,
            cumulativeBufferThresholdMs = 8_000L,
        )
        monitor.onBuffering(playWhenReady = true, nowMs = 0L)
        monitor.onReady(playWhenReady = true, nowMs = 500L)

        monitor.onBuffering(playWhenReady = true, nowMs = 10_000L)
        monitor.onReady(playWhenReady = true, nowMs = 14_000L)
        monitor.onBuffering(playWhenReady = true, nowMs = 20_000L)
        val result = monitor.onReady(playWhenReady = true, nowMs = 24_100L)

        assertEquals(SlowBufferReason.CumulativeBufferTooLong, result.decision?.reason)
    }

    @Test
    fun shortBufferAfterSeekDoesNotCountAsFrequentBuffering() {
        val monitor = monitor()
        monitor.onBuffering(playWhenReady = true, nowMs = 0L)
        monitor.onReady(playWhenReady = true, nowMs = 500L)

        repeat(5) { index ->
            val seekAt = 10_000L + index * 10_000L
            monitor.onSeekStarted(nowMs = seekAt)
            monitor.onBuffering(playWhenReady = true, nowMs = seekAt + 100L)
            val result = monitor.onReady(playWhenReady = true, nowMs = seekAt + 1_000L)
            assertNull(result.decision)
            assertTrue(result.shouldRecordPlaybackSuccess)
        }
    }

    @Test
    fun longBufferAfterSeekIsProtectedUntilReady() {
        val monitor = monitor()
        monitor.onBuffering(playWhenReady = true, nowMs = 0L)
        monitor.onReady(playWhenReady = true, nowMs = 500L)

        monitor.onSeekStarted(nowMs = 10_000L)
        monitor.onBuffering(playWhenReady = true, nowMs = 10_100L)
        val decision = monitor.onBuffering(playWhenReady = true, nowMs = 15_100L)
        val result = monitor.onReady(playWhenReady = true, nowMs = 18_000L)

        assertNull(decision)
        assertNull(result.decision)
        assertTrue(result.shouldRecordPlaybackSuccess)
    }

    @Test
    fun bufferStartedAfterSeekGraceIsStillProtectedUntilReady() {
        val monitor = monitor()
        monitor.onBuffering(playWhenReady = true, nowMs = 0L)
        monitor.onReady(playWhenReady = true, nowMs = 500L)

        monitor.onSeekStarted(nowMs = 10_000L)
        monitor.onBuffering(playWhenReady = true, nowMs = 14_000L)
        val decision = monitor.onBuffering(playWhenReady = true, nowMs = 20_000L)
        val result = monitor.onReady(playWhenReady = true, nowMs = 21_000L)

        assertNull(decision)
        assertNull(result.decision)
        assertTrue(result.shouldRecordPlaybackSuccess)
    }

    @Test
    fun readyAfterSeekEndsCoolingForFutureBuffers() {
        val monitor = monitor()
        monitor.onBuffering(playWhenReady = true, nowMs = 0L)
        monitor.onReady(playWhenReady = true, nowMs = 500L)

        monitor.onSeekStarted(nowMs = 10_000L)
        monitor.onBuffering(playWhenReady = true, nowMs = 10_100L)
        monitor.onReady(playWhenReady = true, nowMs = 11_000L)

        monitor.onBuffering(playWhenReady = true, nowMs = 20_000L)
        val decision = monitor.onBuffering(playWhenReady = true, nowMs = 25_000L)

        assertEquals(SlowBufferReason.ContinuousBufferTooLong, decision?.reason)
    }

    @Test
    fun pausedBufferingIsIgnored() {
        val monitor = monitor()

        assertNull(monitor.onBuffering(playWhenReady = false, nowMs = 0L))
        val result = monitor.onReady(playWhenReady = false, nowMs = 10_000L)

        assertNull(result.decision)
        assertFalse(result.shouldRecordPlaybackSuccess)
    }
}
