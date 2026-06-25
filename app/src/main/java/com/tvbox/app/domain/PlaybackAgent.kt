package com.tvbox.app.domain

const val PLAYBACK_HEALTH_COOLDOWN_MS = 30 * 60 * 1000L
const val PLAYBACK_HEALTH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000L
const val PLAYBACK_HEALTH_MAX_ENTRIES = 300

enum class PlaybackIssueType {
    Error,
    SlowBuffer,
}

data class PlaybackHealthEntry(
    val key: String,
    val lastFailureAtMs: Long = 0L,
    val lastSlowBufferAtMs: Long = 0L,
    val lastSuccessAtMs: Long = 0L,
    val failureCount: Int = 0,
    val slowBufferCount: Int = 0,
    val successCount: Int = 0,
) {
    fun isRecentlyUnhealthy(nowMs: Long, cooldownMs: Long = PLAYBACK_HEALTH_COOLDOWN_MS): Boolean {
        return recentIssueType(nowMs, cooldownMs) != null
    }

    fun recentIssueType(nowMs: Long, cooldownMs: Long = PLAYBACK_HEALTH_COOLDOWN_MS): PlaybackIssueType? {
        val lastIssueAtMs = maxOf(lastFailureAtMs, lastSlowBufferAtMs)
        if (lastIssueAtMs <= 0L) return null
        if (lastSuccessAtMs > lastIssueAtMs) return null
        if (nowMs - lastIssueAtMs >= cooldownMs) return null
        return if (lastSlowBufferAtMs > lastFailureAtMs) {
            PlaybackIssueType.SlowBuffer
        } else {
            PlaybackIssueType.Error
        }
    }

    val latestActivityAtMs: Long
        get() = maxOf(lastFailureAtMs, lastSlowBufferAtMs, lastSuccessAtMs)

    val issueCount: Int
        get() = failureCount + slowBufferCount
}

data class PlaybackHealthSnapshot(
    val entries: Map<String, PlaybackHealthEntry> = emptyMap(),
) {
    fun entryFor(key: String): PlaybackHealthEntry? = entries[key]

    val entryCount: Int
        get() = entries.size

    val successCount: Int
        get() = entries.values.sumOf { it.successCount }

    val failureCount: Int
        get() = entries.values.sumOf { it.failureCount }

    val slowBufferCount: Int
        get() = entries.values.sumOf { it.slowBufferCount }
}

fun prunePlaybackHealthEntries(
    entries: Collection<PlaybackHealthEntry>,
    nowMs: Long,
    retentionMs: Long = PLAYBACK_HEALTH_RETENTION_MS,
    maxEntries: Int = PLAYBACK_HEALTH_MAX_ENTRIES,
): List<PlaybackHealthEntry> {
    val cutoffMs = nowMs - retentionMs
    return entries
        .filter { entry ->
            entry.key.isNotBlank() &&
                entry.latestActivityAtMs > 0L &&
                entry.latestActivityAtMs >= cutoffMs
        }
        .sortedByDescending { it.latestActivityAtMs }
        .take(maxEntries.coerceAtLeast(0))
}

data class PlaybackAgentDecision(
    val nextSourceIndex: Int?,
    val nextSourceName: String? = null,
    val skippedRecentlyUnhealthyCount: Int = 0,
) {
    val switched: Boolean
        get() = nextSourceIndex != null
}

data class PlaybackAgentSourceSelection(
    val sourceIndex: Int,
    val sourceName: String? = null,
    val requestedSourceIndex: Int,
    val skippedRecentlyUnhealthyCount: Int = 0,
) {
    val switchedFromRequested: Boolean
        get() = sourceIndex != requestedSourceIndex
}

class PlaybackAgent(
    private val unhealthyCooldownMs: Long = PLAYBACK_HEALTH_COOLDOWN_MS,
) {
    fun selectBestSource(
        movie: Movie,
        requestedSourceIndex: Int,
        episodeIndex: Int,
        healthSnapshot: PlaybackHealthSnapshot,
        nowMs: Long = System.currentTimeMillis(),
    ): PlaybackAgentSourceSelection? {
        val sources = movie.playSources
        if (sources.isEmpty()) return null

        val safeRequestedSourceIndex = requestedSourceIndex.coerceIn(0, sources.lastIndex)
        val candidates = sources.indices.filter { sourceIndex ->
            sources[sourceIndex].episodes.getOrNull(episodeIndex)?.url?.isNotBlank() == true
        }
        if (candidates.isEmpty()) return null

        val skippedRecentlyUnhealthyCount = candidates.count { sourceIndex ->
            val key = playbackHealthKey(movie.id, episodeIndex, sources[sourceIndex])
            healthSnapshot.entryFor(key)?.isRecentlyUnhealthy(nowMs, unhealthyCooldownMs) == true
        }
        val selectedIndex = candidates
            .sortedWith(
                compareByDescending<Int> { sourceIndex ->
                    sourceHealthScore(
                        movie = movie,
                        sourceIndex = sourceIndex,
                        episodeIndex = episodeIndex,
                        requestedSourceIndex = safeRequestedSourceIndex,
                        healthSnapshot = healthSnapshot,
                        nowMs = nowMs,
                        cooldownMs = unhealthyCooldownMs,
                    )
                }.thenBy { sourceIndex ->
                    sourceDistance(sourceIndex, safeRequestedSourceIndex, sources.size)
                }.thenBy { sourceIndex ->
                    sourceIndex
                },
            )
            .first()

        return PlaybackAgentSourceSelection(
            sourceIndex = selectedIndex,
            sourceName = sources[selectedIndex].name,
            requestedSourceIndex = safeRequestedSourceIndex,
            skippedRecentlyUnhealthyCount = skippedRecentlyUnhealthyCount,
        )
    }

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
        val selectedIndex = candidates
            .sortedWith(
                compareByDescending<Int> { sourceIndex ->
                    sourceHealthScore(
                        movie = movie,
                        sourceIndex = sourceIndex,
                        episodeIndex = episodeIndex,
                        requestedSourceIndex = currentSourceIndex,
                        healthSnapshot = healthSnapshot,
                        nowMs = nowMs,
                        cooldownMs = unhealthyCooldownMs,
                    )
                }.thenBy { sourceIndex ->
                    sourceDistance(sourceIndex, currentSourceIndex, sources.size)
                }.thenBy { sourceIndex ->
                    sourceIndex
                },
            )
            .first()

        return PlaybackAgentDecision(
            nextSourceIndex = selectedIndex,
            nextSourceName = sources[selectedIndex].name,
            skippedRecentlyUnhealthyCount = candidates.size - healthyCandidates.size,
        )
    }
}

private fun sourceHealthScore(
    movie: Movie,
    sourceIndex: Int,
    episodeIndex: Int,
    requestedSourceIndex: Int,
    healthSnapshot: PlaybackHealthSnapshot,
    nowMs: Long,
    cooldownMs: Long,
): Int {
    val source = movie.playSources[sourceIndex]
    val key = playbackHealthKey(movie.id, episodeIndex, source)
    val entry = healthSnapshot.entryFor(key)
    val recentIssue = entry?.recentIssueType(nowMs, cooldownMs)
    var score = if (recentIssue == null) 10_000 else -10_000
    val lastSuccessAtMs = entry?.lastSuccessAtMs ?: 0L
    if (recentIssue == null && lastSuccessAtMs > 0L) {
        val successAgeMs = nowMs - lastSuccessAtMs
        score += if (successAgeMs in 0 until cooldownMs) 2_000 else 500
    }
    score += ((entry?.successCount ?: 0) * 60).coerceAtMost(1_000)
    score -= ((entry?.failureCount ?: 0) * 80).coerceAtMost(1_000)
    score -= ((entry?.slowBufferCount ?: 0) * 120).coerceAtMost(1_500)
    if (sourceIndex == requestedSourceIndex) {
        score += 100
    }
    return score
}

private fun sourceDistance(sourceIndex: Int, requestedSourceIndex: Int, sourceCount: Int): Int {
    if (sourceCount <= 1) return 0
    val forward = if (sourceIndex >= requestedSourceIndex) {
        sourceIndex - requestedSourceIndex
    } else {
        sourceCount - requestedSourceIndex + sourceIndex
    }
    val backward = if (requestedSourceIndex >= sourceIndex) {
        requestedSourceIndex - sourceIndex
    } else {
        sourceCount - sourceIndex + requestedSourceIndex
    }
    return minOf(forward, backward)
}

fun playbackHealthKey(movieId: Int, episodeIndex: Int, source: PlaySource): String {
    val sourceId = source.lineId
        .ifBlank { source.lineName }
        .ifBlank { source.name }
    return listOf(movieId.toString(), episodeIndex.toString(), sourceId).joinToString("|")
}
