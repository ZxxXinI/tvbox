package com.tvbox.app.domain

data class PlaybackSourceSwitch(
    val sourceIndex: Int,
    val episodeIndex: Int,
    val positionMs: Long,
    val playWhenReady: Boolean,
)

fun resolvePlaybackSourceSwitch(
    movie: Movie,
    currentSourceIndex: Int,
    currentEpisodeIndex: Int,
    targetSourceIndex: Int,
    positionMs: Long,
    playWhenReady: Boolean,
): PlaybackSourceSwitch? {
    val episode = movie.playSources.getOrNull(currentSourceIndex)
        ?.episodes?.getOrNull(currentEpisodeIndex) ?: return null
    val target = movie.playSources.getOrNull(targetSourceIndex) ?: return null
    val index = correspondingEpisodeIndex(target, episode.title, currentEpisodeIndex) ?: return null
    if (target.episodes[index].url.isBlank()) return null
    return PlaybackSourceSwitch(targetSourceIndex, index, positionMs.coerceAtLeast(0), playWhenReady)
}

/** Retains the resume point when an unprepared replacement reports position zero. */
class PlaybackResumeAnchor {
    var positionMs: Long = 0
        private set

    fun update(positionMs: Long) {
        this.positionMs = positionMs.coerceAtLeast(0)
    }

    fun positionForFailure(playerPositionMs: Long): Long {
        if (playerPositionMs > 0) update(playerPositionMs)
        return positionMs
    }
}

data class PlaybackIssueNotice(val type: PlaybackIssueType, val message: String) {
    fun afterReady(): PlaybackIssueNotice? = takeUnless { type == PlaybackIssueType.SlowBuffer }
}
