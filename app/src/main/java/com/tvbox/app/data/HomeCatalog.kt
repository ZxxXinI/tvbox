package com.tvbox.app.data

import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.Category
import com.tvbox.app.domain.PagedMovies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

enum class HomeSourceProbeStatus { Available, Timeout, Failed, Empty }

data class HomeSourceProbe(val apiLineId: String, val status: HomeSourceProbeStatus, val elapsedMs: Long? = null)

internal fun MovieRepository.probeHomeSources(
    lines: List<ApiLine>,
    sourceTimeoutMs: Long = HOME_SOURCE_TIMEOUT_MS,
    monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
): Flow<HomeSourceProbe> = channelFlow {
    val semaphore = Semaphore(6)
    for (line in lines.distinctBy { it.id }) {
        launch {
            val result = semaphore.withPermit {
                val started = monotonicMillis()
                try {
                    val page = withTimeoutOrNull(sourceTimeoutMs) { probeHomeCatalog(line.id) }
                    when {
                        page == null -> HomeSourceProbe(line.id, HomeSourceProbeStatus.Timeout)
                        page.movies.isEmpty() -> HomeSourceProbe(line.id, HomeSourceProbeStatus.Empty)
                        else -> HomeSourceProbe(
                            line.id, HomeSourceProbeStatus.Available,
                            (monotonicMillis() - started).coerceAtLeast(0),
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    HomeSourceProbe(line.id, HomeSourceProbeStatus.Failed)
                }
            }
            send(result)
        }
    }
}

private const val HOME_SOURCE_TIMEOUT_MS = 4_000L
