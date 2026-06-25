package com.tvbox.app.data

import android.content.Context
import com.tvbox.app.domain.PlaybackHealthEntry
import com.tvbox.app.domain.PlaybackHealthSnapshot
import com.tvbox.app.domain.PlaybackIssueType
import com.tvbox.app.domain.prunePlaybackHealthEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface PlaybackHealthRepository {
    suspend fun getSnapshot(): PlaybackHealthSnapshot
    suspend fun recordIssue(key: String, issueType: PlaybackIssueType, nowMs: Long): PlaybackHealthSnapshot
    suspend fun recordSuccess(key: String, nowMs: Long): PlaybackHealthSnapshot
    suspend fun clear(): PlaybackHealthSnapshot
}

class SharedPlaybackHealthRepository(context: Context) : PlaybackHealthRepository {
    private val prefs = context.applicationContext.getSharedPreferences("playback_health", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun getSnapshot(): PlaybackHealthSnapshot = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val entries = readPrunedEntries(nowMs)
        writeEntries(entries)
        entries.toSnapshot()
    }

    override suspend fun recordIssue(
        key: String,
        issueType: PlaybackIssueType,
        nowMs: Long,
    ): PlaybackHealthSnapshot = withContext(Dispatchers.IO) {
        val current = readPrunedEntries(nowMs)
            .associateBy { it.key }
            .toMutableMap()
        val entry = current[key] ?: PlaybackHealthEntry(key = key)
        current[key] = when (issueType) {
            PlaybackIssueType.Error -> entry.copy(
                lastFailureAtMs = nowMs,
                failureCount = entry.failureCount + 1,
            )
            PlaybackIssueType.SlowBuffer -> entry.copy(
                lastSlowBufferAtMs = nowMs,
                slowBufferCount = entry.slowBufferCount + 1,
            )
        }
        val entries = prunePlaybackHealthEntries(current.values, nowMs)
        writeEntries(entries)
        entries.toSnapshot()
    }

    override suspend fun recordSuccess(key: String, nowMs: Long): PlaybackHealthSnapshot = withContext(Dispatchers.IO) {
        val current = readPrunedEntries(nowMs)
            .associateBy { it.key }
            .toMutableMap()
        val entry = current[key] ?: PlaybackHealthEntry(key = key)
        current[key] = entry.copy(
            lastSuccessAtMs = nowMs,
            successCount = entry.successCount + 1,
        )
        val entries = prunePlaybackHealthEntries(current.values, nowMs)
        writeEntries(entries)
        entries.toSnapshot()
    }

    override suspend fun clear(): PlaybackHealthSnapshot = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(KEY_ENTRIES)
            .apply()
        PlaybackHealthSnapshot()
    }

    private fun readEntries(): Map<String, StoredPlaybackHealthEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyMap()
        return runCatching {
            json.decodeFromString<List<StoredPlaybackHealthEntry>>(raw)
                .filter { it.key.isNotBlank() }
                .associateBy { it.key }
        }.getOrDefault(emptyMap())
    }

    private fun readPrunedEntries(nowMs: Long): List<PlaybackHealthEntry> {
        return prunePlaybackHealthEntries(
            entries = readEntries().values.map { it.toDomain() },
            nowMs = nowMs,
        )
    }

    private fun writeEntries(entries: Collection<PlaybackHealthEntry>) {
        val stored = entries.map { it.toStored() }
        prefs.edit()
            .putString(KEY_ENTRIES, json.encodeToString(stored))
            .apply()
    }

    private fun Collection<PlaybackHealthEntry>.toSnapshot(): PlaybackHealthSnapshot {
        return PlaybackHealthSnapshot(associateBy { it.key })
    }

    private fun PlaybackHealthEntry.toStored(): StoredPlaybackHealthEntry {
        return StoredPlaybackHealthEntry(
            key = key,
            lastFailureAtMs = lastFailureAtMs,
            lastSlowBufferAtMs = lastSlowBufferAtMs,
            lastSuccessAtMs = lastSuccessAtMs,
            failureCount = failureCount,
            slowBufferCount = slowBufferCount,
            successCount = successCount,
        )
    }

    private fun StoredPlaybackHealthEntry.toDomain(): PlaybackHealthEntry {
        return PlaybackHealthEntry(
            key = key,
            lastFailureAtMs = lastFailureAtMs,
            lastSlowBufferAtMs = lastSlowBufferAtMs,
            lastSuccessAtMs = lastSuccessAtMs,
            failureCount = failureCount,
            slowBufferCount = slowBufferCount,
            successCount = successCount,
        )
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
    }
}

@Serializable
private data class StoredPlaybackHealthEntry(
    val key: String,
    val lastFailureAtMs: Long = 0L,
    val lastSlowBufferAtMs: Long = 0L,
    val lastSuccessAtMs: Long = 0L,
    val failureCount: Int = 0,
    val slowBufferCount: Int = 0,
    val successCount: Int = 0,
) {
    val latestActivityAtMs: Long
        get() = maxOf(lastFailureAtMs, lastSlowBufferAtMs, lastSuccessAtMs)
}
