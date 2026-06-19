package com.tvbox.app.domain

private const val DEFAULT_UNHEALTHY_COOLDOWN_MS = 30 * 60 * 1000L

enum class PlaybackIssueType {
    Error,
    SlowBuffer,
}

data class PlaybackHealthEntry(
    val key: String,
    val lastFailureAtMs: Long = 0L,
    val lastSlowBufferAtMs: Long = 0L,
    val lastSuccessAtMs: Long = 0L,
) {
    fun isRecentlyUnhealthy(nowMs: Long, cooldownMs: Long = DEFAULT_UNHEALTHY_COOLDOWN_MS): Boolean {
        val lastIssueAtMs = maxOf(lastFailureAtMs, lastSlowBufferAtMs)
        if (lastIssueAtMs <= 0L) return false
        if (lastSuccessAtMs > lastIssueAtMs) return false
        return nowMs - lastIssueAtMs < cooldownMs
    }

    val latestActivityAtMs: Long
        get() = maxOf(lastFailureAtMs, lastSlowBufferAtMs, lastSuccessAtMs)
}

data class PlaybackHealthSnapshot(
    val entries: Map<String, PlaybackHealthEntry> = emptyMap(),
) {
    fun entryFor(key: String): PlaybackHealthEntry? = entries[key]
}

data class PlaybackAgentDecision(
    val nextSourceIndex: Int?,
    val nextSourceName: String? = null,
    val skippedRecentlyUnhealthyCount: Int = 0,
) {
    val switched: Boolean
        get() = nextSourceIndex != null
}

class PlaybackAgent(
    private val unhealthyCooldownMs: Long = DEFAULT_UNHEALTHY_COOLDOWN_MS,
) {
    fun selectNextSource(
        movie: Movie,
        currentSourceIndex: Int,
        episodeIndex: Int,
        blockedSourceIndexes: Set<Int>,
        healthSnapshot: PlaybackHealthSnapshot,
        nowMs: Long = System.currentTimeMillis(),
    ): PlaybackAgentDecision {
        val sources = movie.playSources
        if (sources.size <= 1) return PlaybackAgentDecision(nextSourceIndex = null)

        val candidates = (1..sources.size)
            .map { offset -> (currentSourceIndex + offset) % sources.size }
            .filter { sourceIndex ->
                sourceIndex !in blockedSourceIndexes &&
                    sources[sourceIndex].episodes.getOrNull(episodeIndex)?.url?.isNotBlank() == true
            }

        if (candidates.isEmpty()) return PlaybackAgentDecision(nextSourceIndex = null)

        val healthyCandidates = candidates.filterNot { sourceIndex ->
            val key = playbackHealthKey(movie.id, episodeIndex, sources[sourceIndex])
            healthSnapshot.entryFor(key)?.isRecentlyUnhealthy(nowMs, unhealthyCooldownMs) == true
        }
        val selectedIndex = healthyCandidates.firstOrNull() ?: candidates.first()

        return PlaybackAgentDecision(
            nextSourceIndex = selectedIndex,
            nextSourceName = sources[selectedIndex].name,
            skippedRecentlyUnhealthyCount = candidates.size - healthyCandidates.size,
        )
    }
}

fun playbackHealthKey(movieId: Int, episodeIndex: Int, source: PlaySource): String {
    val sourceId = source.lineId
        .ifBlank { source.lineName }
        .ifBlank { source.name }
    return listOf(movieId.toString(), episodeIndex.toString(), sourceId).joinToString("|")
}
