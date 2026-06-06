package com.tvbox.app.data

import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.Category
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.PagedMovies
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MovieRepository {
    val apiLines: List<ApiLine>
    suspend fun getCategories(apiLineId: String): List<Category>
    suspend fun getMovies(apiLineId: String, page: Int, typeId: Int? = null, keyword: String? = null): PagedMovies
    suspend fun getMoviesByTypeIds(apiLineId: String, page: Int, typeIds: List<Int>): PagedMovies
    suspend fun getDetail(apiLineId: String, id: Int): Movie?
}

class DefaultMovieRepository(
    override val apiLines: List<ApiLine> = ApiLines.defaults,
) : MovieRepository {
    override suspend fun getCategories(apiLineId: String): List<Category> = withContext(Dispatchers.IO) {
        val line = requireLine(apiLineId)
        withFallback(line) { api ->
            api.getVod(action = "list").categories.mapNotNull { it.toDomainOrNull() }
        }
    }

    override suspend fun getMovies(apiLineId: String, page: Int, typeId: Int?, keyword: String?): PagedMovies = withContext(Dispatchers.IO) {
        val line = requireLine(apiLineId)
        withFallback(line) { api ->
            api.getVod(
                action = "videolist",
                page = page.coerceAtLeast(1),
                typeId = typeId,
                keyword = keyword?.takeIf { it.isNotBlank() },
            ).toPagedMovies(line)
        }
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
                    runCatching {
                        withFallback(line) { api ->
                            api.getVod(
                                action = "videolist",
                                page = requestedPage,
                                typeId = typeId,
                            ).toPagedMovies(line)
                        }
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

    override suspend fun getDetail(apiLineId: String, id: Int): Movie? = withContext(Dispatchers.IO) {
        val line = requireLine(apiLineId)
        val primary = withFallback(line) { api ->
            api.getVod(action = "videolist", ids = id.toString())
                .list
                .firstOrNull()
                ?.toDomainOrNull(line)
        } ?: return@withContext null

        primary.copy(playSources = loadLinePlaySources(primary))
    }

    private fun requireLine(apiLineId: String): ApiLine {
        return apiLines.firstOrNull { it.id == apiLineId } ?: apiLines.first()
    }

    private suspend fun <T> withFallback(
        line: ApiLine,
        block: suspend (MacCmsApi) -> T,
    ): T {
        var lastError: Throwable? = null
        for (baseUrl in line.baseUrls) {
            try {
                return block(MacCmsNetwork.api(baseUrl))
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("线路不可用：${line.name}")
    }

    private suspend fun loadLinePlaySources(primary: Movie): List<com.tvbox.app.domain.PlaySource> {
        val sources = buildList {
            primary.toLinePlaySource()?.let(::add)
            apiLines
                .filterNot { it.id == primary.apiLineId }
                .forEach { line ->
                    runCatching { findSameMovie(line, primary.name) }
                        .getOrNull()
                        ?.toLinePlaySource()
                        ?.let(::add)
                }
        }
        return sources.ifEmpty { primary.playSources }
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

    private fun Movie.toLinePlaySource(): com.tvbox.app.domain.PlaySource? {
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
}
