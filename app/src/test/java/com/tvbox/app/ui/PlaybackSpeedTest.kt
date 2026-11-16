package com.tvbox.app.ui

import org.junit.Assert.*
import org.junit.Test

class PlaybackSpeedTest {
    @Test fun cyclesInRequestedOrderAndReturnsToNormal() {
        val actual = mutableListOf(1f)
        repeat(5) { actual.add(nextPlaybackSpeed(actual.last())) }
        assertEquals(listOf(1f, 1.25f, 1.5f, 2f, 0.75f, 1f), actual)
    }

    @Test fun labelsMatchBottomButtonAndMenuPrompt() {
        assertEquals(listOf("1x", "1.25x", "1.5x", "2.0x", "0.75x"),
            listOf(1f, 1.25f, 1.5f, 2f, 0.75f).map(::formatPlaybackSpeed))
    }

    @Test fun approximateAndUnknownValuesKeepExistingMenuFallback() {
        assertEquals(1.5f, nextPlaybackSpeed(1.2501f), 0f)
        assertEquals(0.75f, nextPlaybackSpeed(1.8f), 0f)
        assertEquals(0.75f, nextPlaybackSpeed(Float.NaN), 0f)
    }

    @Test fun repeatedClicksCanCycleMoreThanOnceWithoutAPicker() {
        var speed = 1f
        repeat(10) { speed = nextPlaybackSpeed(speed) }
        assertEquals(1f, speed, 0f)
        assertEquals(1f, nextPlaybackSpeed(0.75f), 0f)
    }
}
