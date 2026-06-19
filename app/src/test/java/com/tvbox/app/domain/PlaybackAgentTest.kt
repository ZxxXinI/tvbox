package com.tvbox.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAgentTest {
    private val agent = PlaybackAgent(unhealthyCooldownMs = 30 * 60 * 1000L)

    @Test
    fun skipsSourcesAlreadyTriedInCurrentPlaybackRound() {
        val movie = movieWithSources("量子", "如意", "红牛")

        val decision = agent.selectNextSource(
            movie = movie,
            currentSourceIndex = 0,
            episodeIndex = 0,
            blockedSourceIndexes = setOf(0, 1),
            healthSnapshot = PlaybackHealthSnapshot(),
            nowMs = 10_000L,
        )

        assertTrue(decision.switched)
        assertEquals(2, decision.nextSourceIndex)
        assertEquals("红牛", decision.nextSourceName)
    }

    @Test
    fun prefersHealthySourceOverRecentlyFailedSource() {
        val movie = movieWithSources("量子", "如意", "红牛")
        val unhealthyKey = playbackHealthKey(movie.id, 0, movie.playSources[1])
        val health = PlaybackHealthSnapshot(
            entries = mapOf(
                unhealthyKey to PlaybackHealthEntry(
                    key = unhealthyKey,
                    lastFailureAtMs = 9_000L,
                ),
            ),
        )

        val decision = agent.selectNextSource(
            movie = movie,
            currentSourceIndex = 0,
            episodeIndex = 0,
            blockedSourceIndexes = setOf(0),
            healthSnapshot = health,
            nowMs = 10_000L,
        )

        assertEquals(2, decision.nextSourceIndex)
        assertEquals("红牛", decision.nextSourceName)
        assertEquals(1, decision.skippedRecentlyUnhealthyCount)
    }

    @Test
    fun recentSuccessClearsUnhealthyPenalty() {
        val movie = movieWithSources("量子", "如意", "红牛")
        val recoveredKey = playbackHealthKey(movie.id, 0, movie.playSources[1])
        val health = PlaybackHealthSnapshot(
            entries = mapOf(
                recoveredKey to PlaybackHealthEntry(
                    key = recoveredKey,
                    lastFailureAtMs = 8_000L,
                    lastSuccessAtMs = 9_000L,
                ),
            ),
        )

        val decision = agent.selectNextSource(
            movie = movie,
            currentSourceIndex = 0,
            episodeIndex = 0,
            blockedSourceIndexes = setOf(0),
            healthSnapshot = health,
            nowMs = 10_000L,
        )

        assertEquals(1, decision.nextSourceIndex)
        assertEquals("如意", decision.nextSourceName)
        assertEquals(0, decision.skippedRecentlyUnhealthyCount)
    }

    @Test
    fun reportsRecentIssueTypeForVisibleSourceStatus() {
        val failed = PlaybackHealthEntry(
            key = "failed",
            lastFailureAtMs = 9_000L,
        )
        val slow = PlaybackHealthEntry(
            key = "slow",
            lastSlowBufferAtMs = 9_500L,
        )

        assertEquals(PlaybackIssueType.Error, failed.recentIssueType(nowMs = 10_000L))
        assertEquals(PlaybackIssueType.SlowBuffer, slow.recentIssueType(nowMs = 10_000L))
    }

    @Test
    fun reportsNoSwitchWhenEveryAlternativeIsBlocked() {
        val movie = movieWithSources("量子", "如意")

        val decision = agent.selectNextSource(
            movie = movie,
            currentSourceIndex = 0,
            episodeIndex = 0,
            blockedSourceIndexes = setOf(0, 1),
            healthSnapshot = PlaybackHealthSnapshot(),
            nowMs = 10_000L,
        )

        assertFalse(decision.switched)
    }

    private fun movieWithSources(vararg names: String): Movie {
        return Movie(
            id = 100,
            apiLineId = "liangzi",
            apiLineName = "量子",
            name = "测试影片",
            typeId = 13,
            typeName = "国产剧",
            posterUrl = "",
            remarks = "",
            year = "",
            area = "",
            language = "",
            actor = "",
            director = "",
            duration = "",
            description = "",
            playSources = names.mapIndexed { index, name ->
                PlaySource(
                    name = name,
                    lineId = name.lowercase(),
                    lineName = name,
                    sourceName = "m3u8",
                    episodes = listOf(
                        PlayEpisode(
                            title = "第01集",
                            url = "https://video.test/$index/index.m3u8",
                        ),
                    ),
                )
            },
        )
    }
}
