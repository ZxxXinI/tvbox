package com.tvbox.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class WatchHistoryItem(
    val movieId: Int,
    val movieName: String,
    val posterUrl: String,
    val typeName: String,
    val remarks: String,
    val sourceIndex: Int,
    val sourceName: String,
    val episodeIndex: Int,
    val episodeTitle: String,
    val episodeUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
) {
    val progressPercent: Int
        get() {
            if (durationMs <= 0L || positionMs <= 0L) return 0
            return ((positionMs * 100) / durationMs).coerceIn(0, 100).toInt()
        }
}

