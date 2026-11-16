package com.tvbox.app.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class IntroSkipTest {
    @Test fun defaultsAndOverridesRoundTripWithoutChangingHistory() {
        val key = introSkipKey("source", 1)
        val global = AppSettings(defaultIntroSkipSeconds = 90)
        assertEquals(0, AppSettings().introSkipSeconds("source", 1))
        assertEquals(90, global.introSkipSeconds("source", 1))
        val disabled = global.withIntroSkipOverride(key, 0)
        assertEquals(0, disabled.introSkipSeconds("source", 1))
        assertEquals(90, disabled.introSkipSeconds("other", 1))
        val custom = global.withIntroSkipOverride(key, 35)
        assertEquals(35, custom.introSkipSeconds("source", 1))
        assertEquals(90, custom.withIntroSkipOverride(key, null).introSkipSeconds("source", 1))
        assertEquals(custom.introSkipOverrides,
            Json.decodeFromString<Map<String, Int>>(Json.encodeToString(custom.introSkipOverrides)))
        assertEquals(600, global.withIntroSkipOverride(key, 900).introSkipSeconds("source", 1))
        assertEquals(0, global.withIntroSkipOverride(key, -1).introSkipSeconds("source", 1))
    }

    @Test fun appliesOnceAndManualRewindDoesNotRearm() {
        val p = IntroSkipPolicy()
        p.begin(true, 90)
        assertEquals(90_000L, p.target(0, 600_000, true))
        assertNull(p.target(0, 600_000, true))
        p.begin(true, 90)
        p.cancel()
        assertNull(p.target(0, 600_000, true))
    }

    @Test fun resumeAndSourceSwitchNeverSkipEvenAtZero() {
        val p = IntroSkipPolicy()
        p.begin(false, 90)
        assertNull(p.target(0, 600_000, true))
        assertNull(p.target(10_000, 600_000, true))
    }

    @Test fun waitsForTimelineAndIgnoresShortOrUnseekableMedia() {
        val p = IntroSkipPolicy()
        p.begin(true, 90)
        assertNull(p.target(0, -1, false))
        assertEquals(90_000L, p.target(100, 600_000, true))
        p.begin(true, 90)
        assertNull(p.target(0, 90_000, true))
        p.begin(true, 90)
        assertNull(p.target(0, 600_000, false))
        p.begin(true, 90)
        assertNull(p.target(95_000, 600_000, true))
        assertNull(p.target(0, 600_000, true))
    }

    @Test fun newEpisodeGetsNewSnapshot() {
        val p = IntroSkipPolicy()
        p.begin(true, 30)
        assertEquals(30_000L, p.target(0, 600_000, true))
        p.begin(true, 60)
        assertEquals(60_000L, p.target(0, 600_000, true))
        p.begin(true, 0)
        assertNull(p.target(0, 600_000, true))
    }
}
