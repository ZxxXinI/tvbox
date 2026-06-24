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
    fun prePlaybackSelectionKeepsRequestedSourceWhenNoHealthSignalExists() {
        val movie = movieWithSources("Line A", "Line B", "Line C")

        val selection = agent.selectBestSource(
            movie = movie,
            requestedSourceIndex = 1,
            episodeIndex = 0,
            healthSnapshot = PlaybackHealthSnapshot(),
            nowMs = 10_000L,
        )

        assertEquals(1, selection?.sourceIndex)
        assertEquals("Line B", selection?.sourceName)
        assertFalse(selection?.switchedFromRequested ?: true)
    }

    @Test
    fun prePlaybackSelectionPrefersRecentlySuccessfulSource() {
        val movie = movieWithSources("Line A", "Line B", "Line C")
        val successfulKey = playbackHealthKey(movie.id, 0, movie.playSources[2])
        val health = PlaybackHealthSnapshot(
            entries = mapOf(
                successfulKey to PlaybackHealthEntry(
                    key = successfulKey,
                    lastSuccessAtMs = 9_500L,
                ),
            ),
        )

        val selection = agent.selectBestSource(
            movie = movie,
            requestedSourceIndex = 0,
            episodeIndex = 0,
            healthSnapshot = health,
            nowMs = 10_000L,
        )

        assertEquals(2, selection?.sourceIndex)
        assertEquals("Line C", selection?.sourceName)
        assertTrue(selection?.switchedFromRequested ?: false)
    }

    @Test
    fun prePlaybackSelectionAvoidsRecentlyFailedRequestedSource() {
        val movie = movieWithSources("Line A", "Line B", "Line C")
        val failedKey = playbackHealthKey(movie.id, 0, movie.playSources[0])
        val health = PlaybackHealthSnapshot(
            entries = mapOf(
                failedKey to PlaybackHealthEntry(
                    key = failedKey,
                    lastFailureAtMs = 9_500L,
                ),
            ),
        )

        val selection = agent.selectBestSource(
            movie = movie,
            requestedSourceIndex = 0,
            episodeIndex = 0,
            healthSnapshot = health,
            nowMs = 10_000L,
        )

        assertEquals(1, selection?.sourceIndex)
        assertEquals("Line B", selection?.sourceName)
        assertTrue(selection?.switchedFromRequested ?: false)
        assertEquals(1, selection?.skippedRecentlyUnhealthyCount)
    }

    @Test
    fun prePlaybackSelectionLightlyPrefersSourceWithBetterLongTermStats() {
        val movie = movieWithSources("Line A", "Line B", "Line C")
        val stableKey = playbackHealthKey(movie.id, 0, movie.playSources[1])
        val health = PlaybackHealthSnapshot(
            entries = mapOf(
                stableKey to PlaybackHealthEntry(
                    key = stableKey,
                    successCount = 20,
                ),
            ),
        )

        val selection = agent.selectBestSource(
            movie = movie,
            requestedSourceIndex = 0,
            episodeIndex = 0,
            healthSnapshot = health,
            nowMs = 10_000L,
        )

        assertEquals(1, selection?.sourceIndex)
        assertEquals("Line B", selection?.sourceName)
    }

    @Test
    fun prePlaybackSelectionPenalizesSourceWithManySlowBuffers() {
        val movie = movieWithSources("Line A", "Line B", "Line C")
        val slowKey = playbackHealthKey(movie.id, 0, movie.playSources[0])
        val health = PlaybackHealthSnapshot(
            entries = mapOf(
                slowKey to PlaybackHealthEntry(
                    key = slowKey,
                    slowBufferCount = 20,
                ),
            ),
        )

        val selection = agent.selectBestSource(
            movie = movie,
            requestedSourceIndex = 0,
            episodeIndex = 0,
            healthSnapshot = health,
            nowMs = 10_000L,
        )

        assertEquals(1, selection?.sourceIndex)
        assertEquals("Line B", selection?.sourceName)
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
