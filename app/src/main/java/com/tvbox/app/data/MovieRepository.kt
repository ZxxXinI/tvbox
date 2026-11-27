package com.tvbox.app.data

import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.domain.normalizeResumeTitle
import com.tvbox.app.domain.resumeEpisodeIndex
import com.tvbox.app.domain.Category
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.PagedMovies
import com.tvbox.app.domain.PlaySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

data class MultiSourceSearchUpdate(
    val movies: List<Movie>,
    val completedSources: Int,
    val totalSources: Int,
    val successfulSources: Int,
)

data class MovieDetailUpdate(
    val movie: Movie,
    val completedSources: Int,
    val totalSources: Int,
)

interface MovieRepository {
    val apiLines: List<ApiLine>
    fun updateCustomApiLines(customApiLines: List<ApiLine>)
    fun replaceDefaultApiLines(defaultApiLines: List<ApiLine>) = Unit
    suspend fun getCategories(apiLineId: String): List<Category>
    suspend fun getMovies(apiLineId: String, page: Int, typeId: Int? = null, keyword: String? = null): PagedMovies
    suspend fun getHomeCatalogMovies(apiLineId: String, page: Int): PagedMovies =
        getMovies(apiLineId = apiLineId, page = page, typeId = homeCatalogTypeId(apiLineId))

    suspend fun probeHomeCatalog(apiLineId: String): PagedMovies = getHomeCatalogMovies(apiLineId, page = 1)

    suspend fun getMoviesByTypeIds(apiLineId: String, page: Int, typeIds: List<Int>): PagedMovies
    suspend fun getDetail(apiLineId: String, id: Int): Movie?
    suspend fun getResumeDetail(apiLineId: String, id: Int): Movie? = getDetail(apiLineId, id)

    fun findResumeSources(item: WatchHistoryItem): Flow<Movie> = flow {
        val apiLineId = item.playedApiLineId ?: item.apiLineId
        val movieId = item.playedMovieId ?: item.movieId
        getResumeDetail(apiLineId, movieId)?.withResumeSources(item)?.let { emit(it) }
    }

    fun searchProgressively(
        apiLineId: String,
        page: Int,
        keyword: String,
        retryUnavailableSources: Boolean = false,
    ): Flow<MultiSourceSearchUpdate> = flow {
        val result = getMovies(apiLineId = apiLineId, page = page, keyword = keyword)
        emit(MultiSourceSearchUpdate(result.movies, completedSources = 1, totalSources = 1, successfulSources = 1))
    }

    fun getDetailProgressively(apiLineId: String, id: Int): Flow<MovieDetailUpdate> = flow {
        getDetail(apiLineId = apiLineId, id = id)?.let { movie ->
            emit(MovieDetailUpdate(movie, completedSources = 1, totalSources = 1))
        }
    }
}

class DefaultMovieRepository(
    private val builtInApiLines: List<ApiLine> = ApiLines.defaults,
    private val apiFactory: (String) -> MacCmsApi = MacCmsNetwork::api,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : MovieRepository {
    @Volatile private var customApiLines: List<ApiLine> = emptyList()
    @Volatile private var defaultApiLines: List<ApiLine> = builtInApiLines
    private val categoriesCache = TimedLruCache<List<Category>>(maxEntries = 24)
    private val moviesCache = TimedLruCache<PagedMovies>(maxEntries = 80)
    private val detailsCache = TimedLruCache<Movie?>(maxEntries = 60)
    private val sourceHealth = SourceHealthTracker(nowMs)

    override val apiLines: List<ApiLine>
        get() = (defaultApiLines + customApiLines).distinctBy { it.id }

    override fun replaceDefaultApiLines(defaultApiLines: List<ApiLine>) {
        if (defaultApiLines.isNotEmpty()) this.defaultApiLines = defaultApiLines.toList()
    }

    override fun updateCustomApiLines(customApiLines: List<ApiLine>) {
        this.customApiLines = customApiLines
            .filter { it.id.isNotBlank() && it.name.isNotBlank() && it.baseUrls.isNotEmpty() }
            .distinctBy { it.id }
    }

    override suspend fun getCategories(apiLineId: String): List<Category> = withContext(Dispatchers.IO) {
        val line = requireLine(apiLineId)
        val key = "categories:${line.cacheIdentity}"
        categoriesCache.get(key, nowMs())?.let { return@withContext it }
        val categories = withFallback(line) { api ->
            api.getVod(action = "list").categories.mapNotNull { it.toDomainOrNull() }
        }
        categoriesCache.put(key, categories, nowMs() + MOVIES_CACHE_TTL_MS)
        categories
    }

    override suspend fun getMovies(apiLineId: String, page: Int, typeId: Int?, keyword: String?): PagedMovies = withContext(Dispatchers.IO) {
        val line = requireLine(apiLineId)
        loadMovies(line = line, page = page, typeId = typeId, keyword = keyword)
    }

    override suspend fun getHomeCatalogMovies(apiLineId: String, page: Int): PagedMovies = withContext(Dispatchers.IO) {
        loadMovies(
            line = requireLine(apiLineId),
            page = page,
            typeId = homeCatalogTypeId(apiLineId),
            allowCachedEmpty = page > 1,
        )
    }

    override suspend fun probeHomeCatalog(apiLineId: String): PagedMovies = withContext(Dispatchers.IO) {
        loadMovies(
            line = requireLine(apiLineId),
            page = 1,
            typeId = homeCatalogTypeId(apiLineId),
            bypassCache = true,
        )
    }

    override suspend fun getMoviesByTypeIds(apiLineId: String, page: Int, typeIds: List<Int>): PagedMovies = withContext(Dispatchers.IO) {
        val line = requireLine(apiLineId)
        val distinctTypeIds = typeIds
            .filter { it > 0 }
            .distinct()

        if (distinctTypeIds.isEmpty()) {
            return@withContext getMovies(apiLineId = apiLineId, page = page)
        }

        if (distinctTypeIds.size == 1) {
            return@withContext getMovies(apiLineId = apiLineId, page = page, typeId = distinctTypeIds.single())
        }

        val requestedPage = page.coerceAtLeast(1)
        val results = coroutineScope {
            distinctTypeIds.map { typeId ->
                async {
                    try {
                        Result.success(
                            loadMovies(line = line, page = requestedPage, typeId = typeId),
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                }
            }.awaitAll()
        }

        val pages = results.mapNotNull { it.getOrNull() }
        if (pages.isEmpty()) {
            throw results.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IllegalStateException("分类数据加载失败")
        }

        val movies = pages
            .flatMap { it.movies }
            .distinctBy { "${it.apiLineId}-${it.id}" }

        PagedMovies(
            page = requestedPage,
            pageCount = pages.maxOfOrNull { it.pageCount }?.coerceAtLeast(1) ?: 1,
            total = pages.sumOf { it.total },
            apiLine = line,
            categories = pages
                .flatMap { it.categories }
                .distinctBy { it.id },
            movies = movies,
        )
    }

    override suspend fun getDetail(apiLineId: String, id: Int): Movie? {
        return getDetailProgressively(apiLineId = apiLineId, id = id).lastOrNull()?.movie
    }

    override suspend fun getResumeDetail(apiLineId: String, id: Int): Movie? = withContext(Dispatchers.IO) {
        require(apiLines.any { it.id == apiLineId }) { "原影视接口已不可用，请从搜索重新打开影片" }
        loadPrimaryDetail(apiLineId, id, bypassCache = true)
    }

    override fun findResumeSources(item: WatchHistoryItem): Flow<Movie> = channelFlow {
        val available = apiLines.distinctBy { it.id }
        val playedLineId = item.playedApiLineId
        val legacyLine = if (playedLineId == null) {
            available.singleOrNull { it.name == item.sourceName }
        } else null
        val primaryLine = available.firstOrNull { it.id == (playedLineId ?: legacyLine?.id ?: item.apiLineId) }
        val primaryId = item.playedMovieId?.takeIf { playedLineId != null }
            ?: item.movieId.takeIf { primaryLine?.id == item.apiLineId }
        val primary = primaryLine?.let { line ->
            tryResumeSource {
                withContext(Dispatchers.IO) {
                    if (primaryId != null) loadPrimaryDetail(line.id, primaryId, bypassCache = true)
                    else findExactResumeMovie(line, item, item.movieYear)
                }
            }?.takeIf { it.isResumeMovie(item.movieName, item.movieYear) }
        }
        val match = primary?.withResumeSources(item)
        if (match != null) send(match)
        val year = item.movieYear?.takeIf { it.isNotBlank() } ?: primary?.year
        val alternatives = available
            .filterNot { match != null && it.id == primaryLine.id }
            .sortedWith(compareByDescending<ApiLine> { it.id == item.apiLineId }
                .thenByDescending { sourceHealth.score(it.id) })
        val semaphore = Semaphore(MAX_PARALLEL_SOURCE_REQUESTS)
        for (line in alternatives) {
            launch {
                val candidate = semaphore.withPermit {
                    tryResumeSource {
                        withContext(Dispatchers.IO) {
                            val original = if (line.id == item.apiLineId &&
                                (line.id != primaryLine?.id || primaryId != item.movieId)
                            ) {
                                try {
                                    loadPrimaryDetail(line.id, item.movieId, bypassCache = true)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    null
                                }?.takeIf { it.isResumeMovie(item.movieName, year) }?.withResumeSources(item)
                            } else null
                            original ?: findExactResumeMovie(line, item, year)
                        }
                    }
                }
                if (candidate != null) send(candidate)
            }
        }
    }

    override fun searchProgressively(
        apiLineId: String,
        page: Int,
        keyword: String,
        retryUnavailableSources: Boolean,
    ): Flow<MultiSourceSearchUpdate> = channelFlow {
        val requestedPage = page.coerceAtLeast(1)
        val lines = prioritizedLines(apiLineId, includeCoolingDown = retryUnavailableSources)
        if (lines.isEmpty()) {
            return@channelFlow
        }

        val semaphore = Semaphore(MAX_PARALLEL_SOURCE_REQUESTS)
        val updatesMutex = Mutex()
        val completedMovies = mutableMapOf<String, List<Movie>>()
        var completedSources = 0
        var successfulSources = 0

        coroutineScope {
            lines.map { line ->
                async {
                    val startedAtMs = nowMs()
                    var completedNormally = true
                    val result = try {
                        semaphore.withPermit {
                            withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                                loadMovies(line = line, page = requestedPage, keyword = keyword).movies
                            } ?: run {
                                completedNormally = false
                                sourceHealth.recordFailure(line.id)
                                emptyList()
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        completedNormally = false
                        sourceHealth.recordFailure(line.id)
                        emptyList()
                    }
                    if (completedNormally) {
                        sourceHealth.recordSuccess(line.id, nowMs() - startedAtMs)
                    }

                    updatesMutex.withLock {
                        completedSources += 1
                        if (completedNormally) successfulSources += 1
                        completedMovies[line.id] = result
                        trySend(
                            MultiSourceSearchUpdate(
                                movies = mergeMovies(lines, completedMovies),
                                completedSources = completedSources,
                                totalSources = lines.size,
                                successfulSources = successfulSources,
                            ),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    override fun getDetailProgressively(apiLineId: String, id: Int): Flow<MovieDetailUpdate> = channelFlow {
        val primary = withContext(Dispatchers.IO) {
            loadPrimaryDetail(apiLineId = apiLineId, id = id)
        } ?: return@channelFlow
        val lines = prioritizedLines(apiLineId)
        val primarySource = primary.toLinePlaySource()
        val sourceMutex = Mutex()
        val matchedSources = mutableMapOf<String, PlaySource>()
        var completedSources = 1

        trySend(
            MovieDetailUpdate(
                movie = primary.copy(playSources = listOfNotNull(primarySource)),
                completedSources = completedSources,
                totalSources = lines.size,
            ),
        )

        val semaphore = Semaphore(MAX_PARALLEL_SOURCE_REQUESTS)
        coroutineScope {
            lines
                .filterNot { it.id == primary.apiLineId }
                .map { line ->
                    async {
                        val startedAtMs = nowMs()
                        var completedNormally = true
                        val matched = try {
                            semaphore.withPermit {
                                val response = withTimeoutOrNull(DETAIL_SOURCE_TIMEOUT_MS) {
                                    Result.success(findSameMovie(line, primary.name)?.toLinePlaySource())
                                }
                                if (response == null) {
                                    completedNormally = false
                                    sourceHealth.recordFailure(line.id)
                                }
                                response?.getOrThrow()
                            }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                            completedNormally = false
                            sourceHealth.recordFailure(line.id)
                            null
                        }
                        if (completedNormally) {
                            sourceHealth.recordSuccess(line.id, nowMs() - startedAtMs)
                        }

                        sourceMutex.withLock {
                            completedSources += 1
                            matched?.let { matchedSources[line.id] = it }
                            val sources = buildList {
                                primarySource?.let(::add)
                                lines
                                    .filterNot { it.id == primary.apiLineId }
                                    .mapNotNull { matchedSources[it.id] }
                                    .forEach(::add)
                            }
                            trySend(
                                MovieDetailUpdate(
                                    movie = primary.copy(playSources = sources.ifEmpty { primary.playSources }),
                                    completedSources = completedSources,
                                    totalSources = lines.size,
                                ),
                            )
                        }
                    }
                }
                .awaitAll()
        }
    }

    private fun requireLine(apiLineId: String): ApiLine {
        return apiLines.firstOrNull { it.id == apiLineId }
            ?: throw IllegalArgumentException("影视来源已不可用")
    }

    private suspend fun <T> withFallback(
        line: ApiLine,
        block: suspend (MacCmsApi) -> T,
    ): T {
        var lastError: Throwable? = null
        for (baseUrl in line.baseUrls) {
            try {
                return block(apiFactory(baseUrl))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("线路不可用：${line.name}")
    }

    private suspend fun findExactResumeMovie(line: ApiLine, item: WatchHistoryItem, year: String?): Movie? {
        if (normalizeResumeTitle(item.movieName).isEmpty()) return null
        val matched = withFallback(line) { api ->
            api.getVod(action = "videolist", page = 1, keyword = item.movieName).list
                .mapNotNull { it.toDomainOrNull(line) }
                .filter { it.isResumeMovie(item.movieName, year) }
                .distinctBy { it.id }
        }.singleOrNull() ?: return null
        return loadPrimaryDetail(line.id, matched.id, bypassCache = true)
            ?.takeIf { it.isResumeMovie(item.movieName, year) }
            ?.withResumeSources(item)
    }

    private suspend fun tryResumeSource(request: suspend () -> Movie?): Movie? =
        try {
            withTimeoutOrNull(DETAIL_SOURCE_TIMEOUT_MS) { request() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private suspend fun findSameMovie(line: ApiLine, movieName: String): Movie? {
        val normalizedName = normalizeTitle(movieName)
        val candidates = withFallback(line) { api ->
            api.getVod(action = "videolist", page = 1, keyword = movieName)
                .list
                .mapNotNull { it.toDomainOrNull(line) }
        }
        val matched = candidates.firstOrNull { normalizeTitle(it.name) == normalizedName }
            ?: candidates.firstOrNull { candidate ->
                val normalizedCandidate = normalizeTitle(candidate.name)
                normalizedCandidate.isNotBlank() &&
                    (normalizedCandidate.contains(normalizedName) || normalizedName.contains(normalizedCandidate))
            }
            ?: return null

        return withFallback(line) { api ->
            api.getVod(action = "videolist", ids = matched.id.toString())
                .list
                .firstOrNull()
                ?.toDomainOrNull(line)
        }
    }

    private fun Movie.toLinePlaySource(): PlaySource? {
        val selected = playSources.getOrNull(preferredSourceIndex()) ?: return null
        if (selected.episodes.isEmpty()) return null
        return selected.copy(
            name = apiLineName,
            lineId = apiLineId,
            lineName = apiLineName,
            sourceName = selected.name,
        )
    }

    private fun normalizeTitle(title: String): String {
        return title
            .lowercase()
            .filter { it.isLetterOrDigit() }
    }

    private suspend fun loadMovies(
        line: ApiLine,
        page: Int,
        typeId: Int? = null,
        keyword: String? = null,
        allowCachedEmpty: Boolean = true,
        bypassCache: Boolean = false,
    ): PagedMovies {
        val normalizedKeyword = keyword?.trim().orEmpty()
        val key = "movies:${line.cacheIdentity}:${page.coerceAtLeast(1)}:${typeId ?: "all"}:$normalizedKeyword"
        if (!bypassCache) {
            moviesCache.get(key, nowMs())
                ?.takeIf { allowCachedEmpty || it.movies.isNotEmpty() }
                ?.let { return it }
        }
        val result = withFallback(line) { api ->
            api.getVod(
                action = "videolist",
                page = page.coerceAtLeast(1),
                typeId = typeId,
                keyword = normalizedKeyword.takeIf { it.isNotBlank() },
            ).toPagedMovies(line)
        }
        moviesCache.put(key, result, nowMs() + MOVIES_CACHE_TTL_MS)
        return result
    }

    private suspend fun loadPrimaryDetail(apiLineId: String, id: Int, bypassCache: Boolean = false): Movie? {
        val line = requireLine(apiLineId)
        val key = "detail:${line.cacheIdentity}:$id"
        if (!bypassCache) detailsCache.get(key, nowMs())?.let { return it }
        val detail = withFallback(line) { api ->
            api.getVod(action = "videolist", ids = id.toString())
                .list
                .firstOrNull()
                ?.toDomainOrNull(line)
        }
        detailsCache.put(key, detail, nowMs() + DETAIL_CACHE_TTL_MS)
        return detail
    }

    private fun prioritizedLines(primaryLineId: String, includeCoolingDown: Boolean = false): List<ApiLine> {
        val lines = apiLines
        val primary = lines.firstOrNull { it.id == primaryLineId } ?: lines.firstOrNull() ?: return emptyList()
        return buildList {
            add(primary)
            addAll(
                lines
                    .filter { it.id != primary.id && (includeCoolingDown || !sourceHealth.isCoolingDown(it.id)) }
                    .sortedByDescending { sourceHealth.score(it.id) },
            )
        }
    }

    private fun mergeMovies(lines: List<ApiLine>, moviesByLine: Map<String, List<Movie>>): List<Movie> {
        val priorityById = lines.mapIndexed { index, line -> line.id to index }.toMap()
        return moviesByLine
            .values
            .flatten()
            .sortedBy { priorityById[it.apiLineId] ?: Int.MAX_VALUE }
            .distinctBy { "${normalizeTitle(it.name)}:${it.year.trim()}" }
    }

    private companion object {
        const val MAX_PARALLEL_SOURCE_REQUESTS = 3
        const val SEARCH_TIMEOUT_MS = 3_000L
        const val DETAIL_SOURCE_TIMEOUT_MS = 4_000L
        const val MOVIES_CACHE_TTL_MS = 5 * 60_000L
        const val DETAIL_CACHE_TTL_MS = 30 * 60_000L
    }
}

private val ApiLine.cacheIdentity: String
    get() = "$id:${baseUrls.joinToString(",")}"

private fun Movie.isResumeMovie(movieName: String, expectedYear: String?): Boolean {
    val wanted = normalizeResumeTitle(movieName)
    val referenceYear = expectedYear.orEmpty().trim().take(4).toIntOrNull()
    val candidateYear = year.trim().take(4).toIntOrNull()
    return wanted.isNotEmpty() && normalizeResumeTitle(name) == wanted &&
        (referenceYear == null || candidateYear == null || referenceYear == candidateYear)
}

private fun Movie.withResumeSources(item: WatchHistoryItem): Movie? {
    val matches = playSources.filter { it.resumeEpisodeIndex(item) != null }
        .sortedWith(compareByDescending<PlaySource> { source ->
            source.episodes.any { it.url.isNotBlank() && it.url == item.episodeUrl }
        }.thenByDescending { source ->
            source.sourceName == item.sourceName || source.name == item.sourceName
        })
        .map { source ->
            source.copy(
                name = if (playSources.size == 1) apiLineName else "$apiLineName · ${source.sourceName}",
                lineId = apiLineId, lineName = apiLineName, movieId = id,
            )
        }
    return if (matches.isEmpty()) null else copy(playSources = matches)
}

private class TimedLruCache<T>(
    private val maxEntries: Int,
) {
    private val entries = object : LinkedHashMap<String, TimedCacheEntry<T>>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TimedCacheEntry<T>>): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun get(key: String, nowMs: Long): T? {
        val entry = entries[key] ?: return null
        if (entry.expiresAtMs > nowMs) return entry.value
        entries.remove(key)
        return null
    }

    @Synchronized
    fun put(key: String, value: T, expiresAtMs: Long) {
        entries[key] = TimedCacheEntry(value = value, expiresAtMs = expiresAtMs)
    }
}

private data class TimedCacheEntry<T>(
    val value: T,
    val expiresAtMs: Long,
)

private class SourceHealthTracker(
    private val nowMs: () -> Long,
) {
    private val stats = mutableMapOf<String, SourceHealthStats>()

    @Synchronized
    fun recordSuccess(lineId: String, latencyMs: Long) {
        val entry = stats.getOrPut(lineId, ::SourceHealthStats)
        entry.successes += 1
        entry.averageLatencyMs = if (entry.averageLatencyMs == 0L) {
            latencyMs
        } else {
            (entry.averageLatencyMs * 3 + latencyMs) / 4
        }
    }

    @Synchronized
    fun recordFailure(lineId: String) {
        val entry = stats.getOrPut(lineId, ::SourceHealthStats)
        entry.failures += 1
        entry.cooldownUntilMs = nowMs() + FAILURE_COOLDOWN_MS
    }

    @Synchronized
    fun isCoolingDown(lineId: String): Boolean {
        return stats[lineId]?.cooldownUntilMs?.let { it > nowMs() } == true
    }

    @Synchronized
    fun score(lineId: String): Long {
        val entry = stats[lineId] ?: return 0L
        return entry.successes * 1_000L - entry.failures * 500L - entry.averageLatencyMs / 10
    }

    private companion object {
        const val FAILURE_COOLDOWN_MS = 2 * 60_000L
    }
}

private data class SourceHealthStats(
    var successes: Long = 0,
    var failures: Long = 0,
    var averageLatencyMs: Long = 0,
    var cooldownUntilMs: Long = 0,
)
