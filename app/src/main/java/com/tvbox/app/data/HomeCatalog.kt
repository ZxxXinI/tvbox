package com.tvbox.app.data

import com.tvbox.app.domain.Category
import com.tvbox.app.domain.PagedMovies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun MovieRepository.loadAvailableHomeCatalog(
    startApiLineId: String,
    sourceTimeoutMs: Long = HOME_SOURCE_TIMEOUT_MS,
): PagedMovies? {
    val lines = apiLines.distinctBy { it.id }
    val startIndex = lines.indexOfFirst { it.id == startApiLineId }.coerceAtLeast(0)
    for (line in lines.drop(startIndex) + lines.take(startIndex)) {
        val result = tryHomeSource(sourceTimeoutMs) {
            getHomeCatalogMovies(line.id, page = 1)
        }
        if (result != null && result.movies.isNotEmpty()) return result
    }
    return null
}

// Category 13 is specific to Liangzi. Other sources browse their own recent updates.
internal fun homeCatalogTypeId(apiLineId: String): Int? = if (apiLineId == "liangzi") 13 else null

internal suspend fun MovieRepository.getHomeCategoriesOrNull(apiLineId: String): List<Category>? {
    return tryHomeSource(HOME_SOURCE_TIMEOUT_MS) { getCategories(apiLineId) }
}

private suspend fun <T> tryHomeSource(timeoutMs: Long, request: suspend () -> T): T? {
    return try {
        withTimeoutOrNull(timeoutMs) { request() }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

private const val HOME_SOURCE_TIMEOUT_MS = 4_000L
