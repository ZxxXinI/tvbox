package com.tvbox.app.data

import android.content.Context
import com.tvbox.app.domain.PlaybackHealthEntry
import com.tvbox.app.domain.PlaybackHealthSnapshot
import com.tvbox.app.domain.PlaybackIssueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface PlaybackHealthRepository {
    suspend fun getSnapshot(): PlaybackHealthSnapshot
    suspend fun recordIssue(key: String, issueType: PlaybackIssueType, nowMs: Long): PlaybackHealthSnapshot
    suspend fun recordSuccess(key: String, nowMs: Long): PlaybackHealthSnapshot
}

class SharedPlaybackHealthRepository(context: Context) : PlaybackHealthRepository {
    private val prefs = context.applicationContext.getSharedPreferences("playback_health", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun getSnapshot(): PlaybackHealthSnapshot = withContext(Dispatchers.IO) {
        readSnapshot()
    }

    override suspend fun recordIssue(
        key: String,
        issueType: PlaybackIssueType,
        nowMs: Long,
    ): PlaybackHealthSnapshot = withContext(Dispatchers.IO) {
        val current = readEntries().toMutableMap()
        val entry = current[key]?.toDomain() ?: PlaybackHealthEntry(key = key)
        current[key] = when (issueType) {
            PlaybackIssueType.Error -> entry.copy(lastFailureAtMs = nowMs).toStored()
            PlaybackIssueType.SlowBuffer -> entry.copy(lastSlowBufferAtMs = nowMs).toStored()
        }
        writeEntries(current.values)
        current.toSnapshot()
    }

    override suspend fun recordSuccess(key: String, nowMs: Long): PlaybackHealthSnapshot = withContext(Dispatchers.IO) {
        val current = readEntries().toMutableMap()
        val entry = current[key]?.toDomain() ?: PlaybackHealthEntry(key = key)
        current[key] = entry.copy(lastSuccessAtMs = nowMs).toStored()
        writeEntries(current.values)
        current.toSnapshot()
    }

    private fun readSnapshot(): PlaybackHealthSnapshot = readEntries().toSnapshot()

    private fun readEntries(): Map<String, StoredPlaybackHealthEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<List<StoredPlaybackHealthEntry>>(raw)
                .filter { it.key.isNotBlank() }
                .associateBy { it.key }
        }.getOrDefault(emptyMap())
    }

    private fun writeEntries(entries: Collection<StoredPlaybackHealthEntry>) {
        val trimmed = entries
            .sortedByDescending { it.latestActivityAtMs }
            .take(MAX_HEALTH_ENTRIES)
        prefs.edit()
            .putString(KEY_ENTRIES, json.encodeToString(trimmed))
            .apply()
    }

    private fun Map<String, StoredPlaybackHealthEntry>.toSnapshot(): PlaybackHealthSnapshot {
        return PlaybackHealthSnapshot(values.map { it.toDomain() }.associateBy { it.key })
    }

    private fun PlaybackHealthEntry.toStored(): StoredPlaybackHealthEntry {
        return StoredPlaybackHealthEntry(
            key = key,
            lastFailureAtMs = lastFailureAtMs,
            lastSlowBufferAtMs = lastSlowBufferAtMs,
            lastSuccessAtMs = lastSuccessAtMs,
        )
    }

    private fun StoredPlaybackHealthEntry.toDomain(): PlaybackHealthEntry {
        return PlaybackHealthEntry(
            key = key,
            lastFailureAtMs = lastFailureAtMs,
            lastSlowBufferAtMs = lastSlowBufferAtMs,
            lastSuccessAtMs = lastSuccessAtMs,
        )
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val MAX_HEALTH_ENTRIES = 300
    }
}

@Serializable
private data class StoredPlaybackHealthEntry(
    val key: String,
    val lastFailureAtMs: Long = 0L,
    val lastSlowBufferAtMs: Long = 0L,
    val lastSuccessAtMs: Long = 0L,
) {
    val latestActivityAtMs: Long
        get() = maxOf(lastFailureAtMs, lastSlowBufferAtMs, lastSuccessAtMs)
}
