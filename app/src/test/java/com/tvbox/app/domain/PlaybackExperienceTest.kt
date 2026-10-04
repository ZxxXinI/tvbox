package com.tvbox.app.domain

import org.junit.Assert.*
import org.junit.Test

class PlaybackExperienceTest {
    @Test fun backgroundAndFocusRequireExplicitResume() {
        val policy = PlaybackInterruptionPolicy()
        assertTrue(policy.playbackAllowed)
        policy.leaveForeground()
        assertFalse(policy.requestPlay())
        policy.enterForeground()
        assertFalse(policy.playbackAllowed)
        assertTrue(policy.interrupted)
        assertTrue(policy.requestPlay())
        policy.interrupt()
        policy.enterForeground()
        assertFalse(policy.playbackAllowed)
        policy.requestPlay()
        assertTrue(policy.playbackAllowed)
    }
    @Test fun manualPauseSurvivesMediaChangeAndForegroundReturn() {
        val policy = PlaybackInterruptionPolicy()
        policy.pause()
        policy.enterForeground()
        assertFalse(policy.playbackAllowed)
        val monitor = PlaybackBufferMonitor()
        assertNull(monitor.onBuffering(policy.playbackAllowed, 0))
        assertNull(monitor.onBuffering(policy.playbackAllowed, 60_000))
    }
    @Test fun continueWatchingFiltersSortsAndCapsWithoutRemovingUnknownDuration() {
        val records = (1..10).map { history(it, position = 10, duration = 100) } +
            listOf(history(11, 95, 100), history(12, 0, 100), history(13, 50, 0))
        val selected = records.continueWatching()
        assertEquals(6, selected.size)
        assertEquals(listOf(13, 10, 9, 8, 7, 6), selected.map { it.movieId })
        assertFalse(selected.any { it.movieId == 11 || it.movieId == 12 })
        assertTrue(records.continueWatching(0).isEmpty())
    }
    @Test fun latestHistoryWinsBeforeCompletionFilter() {
        val old = history(1, 20, 100).copy(updatedAtEpochMs = 1)
        val latest = old.copy(positionMs = 100, updatedAtEpochMs = 2)
        assertTrue(listOf(old, latest).continueWatching().isEmpty())
    }
    @Test fun sourceSwitchMatchesTitleBeforeIndexAndRejectsMissingEpisode() {
        val target = PlaySource("alternate", listOf(PlayEpisode("two", "2"), PlayEpisode("one", "1")))
        assertEquals(1, correspondingEpisodeIndex(target, "one", 0))
        assertNull(correspondingEpisodeIndex(target, "three", 2))
        val missingMiddle = PlaySource("missing", listOf(PlayEpisode("第1集", "1"), PlayEpisode("第3集", "3")))
        assertNull(correspondingEpisodeIndex(missingMiddle, "第2集", 1))
        assertEquals(1, correspondingEpisodeIndex(missingMiddle, "03", 0))
    }
    private fun history(id: Int, position: Long, duration: Long) = WatchHistoryItem(
        movieId = id, apiLineId = "test", apiLineName = "test", movieName = "movie$id", posterUrl = "",
        typeName = "movie", remarks = "", sourceIndex = 0, sourceName = "source", episodeIndex = 0,
        episodeTitle = "one", episodeUrl = "http://test/$id", positionMs = position, durationMs = duration,
        updatedAtEpochMs = id.toLong(),
    )
}
