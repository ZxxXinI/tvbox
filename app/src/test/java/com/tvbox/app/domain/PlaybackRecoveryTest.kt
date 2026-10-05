package com.tvbox.app.domain

import org.junit.Assert.*
import org.junit.Test

class PlaybackRecoveryTest {
    @Test fun automaticSwitchKeepsProgressAndFindsReorderedEpisode() {
        val movie = movie()
        val decision = PlaybackAgent().selectNextSource(movie, 0, 1, setOf(0), PlaybackHealthSnapshot())
        assertEquals(1, decision.nextSourceIndex)
        val target = resolvePlaybackSourceSwitch(movie, 0, 1, decision.nextSourceIndex!!, 1_234_567, true)!!
        assertEquals(0, target.episodeIndex)
        assertEquals(1_234_567L, target.positionMs)
        assertTrue(target.playWhenReady)
    }

    @Test fun automaticSwitchSkipsMissingAndBlankEpisodes() {
        val movie = movie().copy(playSources = listOf(
            source("A", "1", "2"), source("missing", "1", "3"),
            PlaySource("blank", listOf(PlayEpisode("2", ""))), source("D", "2"),
        ))
        val decision = PlaybackAgent().selectNextSource(movie, 0, 1, setOf(0), PlaybackHealthSnapshot())
        assertEquals(3, decision.nextSourceIndex)
        assertNull(resolvePlaybackSourceSwitch(movie, 0, 1, 1, 100, true))
        assertNull(resolvePlaybackSourceSwitch(movie, 0, 1, 2, 100, true))
    }

    @Test fun noCorrespondingEpisodeMeansNoAutomaticSwitch() {
        val movie = movie().copy(playSources = listOf(source("A", "1", "2"), source("B", "1", "3")))
        assertFalse(PlaybackAgent().selectNextSource(movie, 0, 1, setOf(0), PlaybackHealthSnapshot()).switched)
    }

    @Test fun manualSwitchRespectsPauseAndClampsNegativeProgress() {
        val target = resolvePlaybackSourceSwitch(movie(), 0, 1, 1, -100, false)!!
        assertFalse(target.playWhenReady)
        assertEquals(0L, target.positionMs)
    }

    @Test fun consecutiveUnpreparedSourcesRetainTheOriginalResumePoint() {
        val anchor = PlaybackResumeAnchor()
        anchor.update(1_800_000)
        val movie = movie().copy(playSources = movie().playSources + source("C", "1", "2"))
        val first = resolvePlaybackSourceSwitch(movie, 0, 1, 1, anchor.positionForFailure(0), true)!!
        anchor.update(first.positionMs)
        val second = resolvePlaybackSourceSwitch(movie, 1, first.episodeIndex, 2, anchor.positionForFailure(0), true)!!
        assertEquals(1_800_000L, second.positionMs)
        assertEquals(1, second.episodeIndex)
    }

    @Test fun failureUsesExactCurrentPositionInsteadOfPeriodicHistory() {
        val anchor = PlaybackResumeAnchor()
        anchor.update(10_000)
        assertEquals(14_321L, anchor.positionForFailure(14_321))
        assertEquals(14_321L, anchor.positionForFailure(0))
    }

    @Test fun rewindAndSeekToZeroAreNotOverriddenByAnOldResumePoint() {
        val anchor = PlaybackResumeAnchor()
        anchor.update(100_000)
        anchor.update(20_000)
        assertEquals(20_000L, anchor.positionForFailure(0))
        anchor.update(0)
        assertEquals(0L, anchor.positionForFailure(0))
    }

    @Test fun newEpisodeResetsTheResumePoint() {
        val anchor = PlaybackResumeAnchor()
        anchor.update(100_000)
        anchor.update(0)
        assertEquals(0L, anchor.positionForFailure(0))
    }

    @Test fun recoveredPlaybackClearsOnlyBufferingNotRealErrors() {
        assertNull(PlaybackIssueNotice(PlaybackIssueType.SlowBuffer, "slow").afterReady())
        val error = PlaybackIssueNotice(PlaybackIssueType.Error, "decoder error")
        assertEquals(error, error.afterReady())
    }

    @Test fun healthRankingUsesTheMatchingEpisodeIndexInEachSource() {
        val movie = movie().copy(playSources = movie().playSources + source("C", "1", "2"))
        val unhealthyKey = playbackHealthKey(movie.id, 0, movie.playSources[1])
        val health = PlaybackHealthSnapshot(mapOf(unhealthyKey to PlaybackHealthEntry(
            unhealthyKey, lastFailureAtMs = 9_000,
        )))
        assertEquals(2, PlaybackAgent().selectNextSource(movie, 0, 1, setOf(0), health, 10_000).nextSourceIndex)
    }

    private fun source(name: String, vararg episodes: String) =
        PlaySource(name, episodes.map { PlayEpisode(it, "https://test/$name/$it.m3u8") })

    private fun movie() = Movie(
        id = 1, apiLineId = "test", apiLineName = "test", name = "movie", typeId = 1, typeName = "test",
        posterUrl = "", remarks = "", year = "", area = "", language = "", actor = "", director = "",
        duration = "", description = "", playSources = listOf(source("A", "1", "2"), source("B", "2", "1")),
    )
}
