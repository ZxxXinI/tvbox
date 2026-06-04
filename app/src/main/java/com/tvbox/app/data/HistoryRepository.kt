package com.tvbox.app.data

import android.content.Context
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.domain.isBlockedContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface HistoryRepository {
    suspend fun getHistory(): List<WatchHistoryItem>
    suspend fun saveProgress(item: WatchHistoryItem): List<WatchHistoryItem>
}

class SharedHistoryRepository(context: Context) : HistoryRepository {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("watch_history", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun getHistory(): List<WatchHistoryItem> = withContext(Dispatchers.IO) {
        readHistory()
    }

    override suspend fun saveProgress(item: WatchHistoryItem): List<WatchHistoryItem> = withContext(Dispatchers.IO) {
        val updatedItem = item.copy(updatedAtEpochMs = System.currentTimeMillis())
        val updated = buildList {
            add(updatedItem)
            readHistory()
                .filterNot { it.apiLineId == item.apiLineId && it.movieId == item.movieId }
                .forEach(::add)
        }.take(MAX_HISTORY_ITEMS)

        prefs.edit()
            .putString(KEY_ITEMS, json.encodeToString(updated))
            .apply()

        updated
    }

    private fun readHistory(): List<WatchHistoryItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<WatchHistoryItem>>(raw)
        }.getOrDefault(emptyList())
            .filterNot {
                isBlockedContent(
                    it.movieName,
                    it.typeName,
                    it.remarks,
                    it.episodeTitle,
                )
            }
            .sortedByDescending { it.updatedAtEpochMs }
    }

    private companion object {
        const val KEY_ITEMS = "items"
        const val MAX_HISTORY_ITEMS = 100
    }
}
