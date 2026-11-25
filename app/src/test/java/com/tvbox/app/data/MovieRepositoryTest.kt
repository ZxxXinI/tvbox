package com.tvbox.app.data

import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.Category
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieRepositoryTest {
    @Test
    fun searchCountsEmptyResponsesAsSuccessfulAndRetriesSourcesInCooldown() = runBlocking {
        val primary = line("primary")
        val backup = line("backup")
        var backupOffline = true
        val repo = repository(listOf(primary, backup), mapOf(
            primary.baseUrls.single() to fakeApi { _, _, _, _, _, _ -> response() },
            backup.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                if (backupOffline) throw IOException("Offline")
                response(vod(2, "恢复的结果"))
            },
        ))

        val firstSearch = repo.searchProgressively(primary.id, 1, "影片").toList().last()
        assertEquals(2, firstSearch.completedSources)
        assertEquals(1, firstSearch.successfulSources)
        assertTrue(firstSearch.movies.isEmpty())

        backupOffline = false
        val regularSearch = repo.searchProgressively(primary.id, 1, "影片").toList().last()
        assertEquals(1, regularSearch.totalSources)
        val retry = repo.searchProgressively(primary.id, 1, "影片", retryUnavailableSources = true).toList().last()
        assertEquals(2, retry.successfulSources)
        assertEquals("恢复的结果", retry.movies.single().name)
    }

    @Test
    fun homeCatalogKeepsWorkingPreferredSource() = runBlocking {
        val preferred = line("liangzi")
        val backup = line("backup")
        val repo = repository(listOf(preferred, backup), mapOf(
            preferred.baseUrls.single() to fakeApi { _, page, typeId, _, _, _ ->
                assertEquals(1, page)
                assertEquals(13, typeId)
                response(vod(1, "首选影片"))
            },
            backup.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                throw AssertionError("Working preferred source should be sufficient")
            },
        ))

        val result = repo.loadAvailableHomeCatalog(preferred.id)

        assertEquals(preferred.id, result?.apiLine?.id)
        assertEquals("首选影片", result?.movies?.single()?.name)
    }

    @Test
    fun homeCatalogUsesNextSourceAndPaginatesWithItsOwnQuery() = runBlocking {
        val earlier = line("earlier")
        val preferred = line("liangzi")
        val backup = line("backup")
        val requests = mutableListOf<String>()
        val repo = repository(listOf(earlier, preferred, backup), mapOf(
            earlier.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                throw AssertionError("Fallback should start at the source after the preferred one")
            },
            preferred.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                requests += preferred.id
                throw IOException("Offline")
            },
            backup.baseUrls.single() to fakeApi { _, page, typeId, _, _, _ ->
                assertNull(typeId)
                requests += "${backup.id}:$page"
                response(vod(page ?: 0, "备用影片 $page")).copy(
                    categories = listOf(CategoryDto(typeId = 21, typeName = "电影")),
                )
            },
        ))

        val firstPage = checkNotNull(repo.loadAvailableHomeCatalog(preferred.id))
        val secondPage = repo.getHomeCatalogMovies(firstPage.apiLine.id, page = 2)

        assertEquals(listOf("liangzi", "backup:1", "backup:2"), requests)
        assertEquals(listOf(Category(id = 21, parentId = 0, name = "电影")), firstPage.categories)
        assertEquals(backup.id, firstPage.movies.single().apiLineId)
        assertEquals(backup.id, secondPage.movies.single().apiLineId)
    }

    @Test
    fun homeCatalogSkipsTimeoutAndEmptyResultThenWrapsAround() = runBlocking {
        val working = line("working")
        val preferred = line("slow")
        val empty = line("empty")
        val requests = mutableListOf<String>()
        val repo = repository(listOf(working, preferred, empty), mapOf(
            working.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                requests += working.id
                response(vod(1, "可用影片"))
            },
            preferred.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                requests += preferred.id
                awaitCancellation()
            },
            empty.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                requests += empty.id
                response()
            },
        ))

        val result = repo.loadAvailableHomeCatalog(preferred.id, sourceTimeoutMs = 300)

        assertEquals(listOf("slow", "empty", "working"), requests)
        assertEquals(working.id, result?.apiLine?.id)
    }

    @Test
    fun homeCatalogReportsExhaustionAndCanRetryPreferredSource() = runBlocking {
        val preferred = line("preferred")
        val backup = line("backup")
        val requests = mutableListOf<String>()
        var preferredOffline = true
        val repo = repository(listOf(preferred), mapOf(
            preferred.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                requests += preferred.id
                if (preferredOffline) response() else response(vod(1, "恢复的影片"))
            },
            backup.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                requests += backup.id
                throw IOException("Offline")
            },
        ))
        repo.updateCustomApiLines(listOf(backup))

        assertNull(repo.loadAvailableHomeCatalog(preferred.id))
        assertEquals(listOf("preferred", "backup"), requests)

        preferredOffline = false
        assertEquals(preferred.id, repo.loadAvailableHomeCatalog(preferred.id)?.apiLine?.id)
        assertEquals(listOf("preferred", "backup", "preferred"), requests)
    }

    @Test
    fun cancellingHomeCatalogStopsBeforeTryingAnotherSource() = runBlocking {
        val preferred = line("preferred")
        val backup = line("backup")
        val started = CompletableDeferred<Unit>()
        val repo = repository(listOf(preferred, backup), mapOf(
            preferred.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                started.complete(Unit)
                awaitCancellation()
            },
            backup.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                throw AssertionError("Cancelled request must not continue to another source")
            },
        ))
        val request = launch { repo.loadAvailableHomeCatalog(preferred.id) }

        withTimeout(1_500) { started.await() }
        request.cancelAndJoin()
        assertTrue(request.isCancelled)
    }

    @Test
    fun resumeOnlyRequestsOriginalSourceWithoutWaitingForSlowAlternatives() = runBlocking {
        val primary = line("primary")
        val backup = line("backup")
        var backupRequests = 0
        val repo = repository(listOf(primary, backup), mapOf(
            primary.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                response(vod(1, "续播测试", sourceName = "m3u8", episodeUrl = "https://primary.test/play.m3u8"))
            },
            backup.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                backupRequests++
                delay(60_000)
                response()
            },
        ))
        val movie = withTimeout(1_500) { repo.getResumeDetail("primary", 1) }
        assertEquals("续播测试", movie?.name)
        assertEquals(0, backupRequests)
    }

    @Test
    fun slowAlternativeTimeoutDoesNotCancelSuccessfulPrimaryDetail() = runBlocking {
        val primary = line("primary")
        val backup = line("backup")
        val repo = repository(listOf(primary, backup), mapOf(
            primary.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                response(vod(1, "慢源测试", sourceName = "m3u8", episodeUrl = "https://primary.test/play.m3u8"))
            },
            backup.baseUrls.single() to fakeApi { _, _, _, _, _, _ -> delay(60_000); response() },
        ))
        val updates = withTimeout(7_000) { repo.getDetailProgressively("primary", 1).toList() }
        assertEquals(2, updates.last().completedSources)
        assertEquals("慢源测试", updates.last().movie.name)
        assertEquals(1, updates.last().movie.playSources.size)
    }

    @Test
    fun callerCancellationStillCancelsSourceWork() = runBlocking {
        val primary = line("primary")
        val repo = repository(listOf(primary), mapOf(
            primary.baseUrls.single() to fakeApi { _, _, _, _, _, _ -> delay(60_000); response() },
        ))
        var cancelled = false
        try { withTimeout(50) { repo.getResumeDetail("primary", 1) } }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test
    fun progressiveSearchDeduplicatesMoviesAndKeepsPrimarySourceFirst() = runBlocking {
        val primary = line("primary")
        val backup = line("backup")
        val slow = line("slow")
        val repository = repository(
            lines = listOf(primary, backup, slow),
            apiByBaseUrl = mapOf(
                primary.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                    response(
                        vod(id = 1, name = "同名影片", year = "2026"),
                        vod(id = 2, name = "主来源独有", year = "2026"),
                    )
                },
                backup.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                    response(
                        vod(id = 3, name = "同名影片", year = "2026"),
                        vod(id = 4, name = "备用来源独有", year = "2025"),
                    )
                },
                slow.baseUrls.single() to fakeApi { _, _, _, _, _, _ -> response() },
            ),
        )

        val updates = repository.searchProgressively("primary", page = 1, keyword = "影片").toList()
        val finalUpdate = updates.last()

        assertEquals(3, finalUpdate.completedSources)
        assertEquals(3, finalUpdate.totalSources)
        assertEquals(3, finalUpdate.movies.size)
        assertEquals("primary", finalUpdate.movies.first().apiLineId)
        assertEquals(
            setOf("同名影片", "主来源独有", "备用来源独有"),
            finalUpdate.movies.map { it.name }.toSet(),
        )
    }

    @Test
    fun repeatedMovieRequestUsesMemoryCache() = runBlocking {
        val primary = line("primary")
        var requestCount = 0
        val repository = repository(
            lines = listOf(primary),
            apiByBaseUrl = mapOf(
                primary.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                    requestCount += 1
                    response(vod(id = 1, name = "缓存影片"))
                },
            ),
        )

        repository.getMovies("primary", page = 1, keyword = "缓存影片")
        repository.getMovies("primary", page = 1, keyword = "缓存影片")

        assertEquals(1, requestCount)
    }

    @Test
    fun repeatedCategoryRequestUsesMemoryCache() = runBlocking {
        val primary = line("primary")
        var requestCount = 0
        val repository = repository(
            lines = listOf(primary),
            apiByBaseUrl = mapOf(
                primary.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                    requestCount += 1
                    response()
                },
            ),
        )

        repository.getCategories("primary")
        repository.getCategories("primary")

        assertEquals(1, requestCount)
    }

    @Test
    fun progressiveSearchLimitsConcurrentSourceRequestsToThree() = runBlocking {
        val activeRequests = AtomicInteger(0)
        val maximumConcurrentRequests = AtomicInteger(0)
        val lines = (1..5).map { line("source-$it") }
        val apiByBaseUrl = lines.associate { line ->
            line.baseUrls.single() to fakeApi { _, _, _, _, _, _ ->
                val active = activeRequests.incrementAndGet()
                maximumConcurrentRequests.updateAndGet { current -> maxOf(current, active) }
                try {
                    delay(40)
                    response()
                } finally {
                    activeRequests.decrementAndGet()
                }
            }
        }
        val repository = repository(lines, apiByBaseUrl)

        repository.searchProgressively("source-1", page = 1, keyword = "并发").toList()

        assertTrue(maximumConcurrentRequests.get() in 2..3)
    }

    @Test
    fun progressiveDetailEmitsPrimaryBeforeAlternativeSourcesFinish() = runBlocking {
        val primary = line("primary")
        val backup = line("backup")
        val extra = line("extra")
        val repository = repository(
            lines = listOf(primary, backup, extra),
            apiByBaseUrl = mapOf(
                primary.baseUrls.single() to fakeApi { _, _, _, _, _, ids ->
                    if (ids == "1") {
                        response(vod(id = 1, name = "测试电影", sourceName = "m3u8", episodeUrl = "https://primary.test/1.m3u8"))
                    } else {
                        response()
                    }
                },
                backup.baseUrls.single() to fakeApi { _, _, _, keyword, _, ids ->
                    when {
                        ids == "2" -> response(vod(id = 2, name = "测试电影", sourceName = "m3u8", episodeUrl = "https://backup.test/1.m3u8"))
                        keyword == "测试电影" -> response(vod(id = 2, name = "测试电影"))
                        else -> response()
                    }
                },
                extra.baseUrls.single() to fakeApi { _, _, _, keyword, _, ids ->
                    when {
                        ids == "3" -> response(vod(id = 3, name = "测试电影", sourceName = "m3u8", episodeUrl = "https://extra.test/1.m3u8"))
                        keyword == "测试电影" -> response(vod(id = 3, name = "测试电影"))
                        else -> response()
                    }
                },
            ),
        )

        val updates = repository.getDetailProgressively("primary", id = 1).toList()

        assertEquals(1, updates.first().movie.playSources.size)
        assertEquals(1, updates.first().completedSources)
        assertEquals(3, updates.last().completedSources)
        assertEquals(listOf("primary", "backup", "extra"), updates.last().movie.playSources.map { it.lineId })
    }

    private fun repository(
        lines: List<ApiLine>,
        apiByBaseUrl: Map<String, MacCmsApi>,
    ): DefaultMovieRepository {
        return DefaultMovieRepository(
            builtInApiLines = lines,
            apiFactory = { baseUrl -> checkNotNull(apiByBaseUrl[baseUrl]) },
            nowMs = { 1_000L },
        )
    }

    private fun line(id: String): ApiLine {
        return ApiLine(
            id = id,
            name = id,
            baseUrls = listOf("https://$id.test/api.php/provide/vod/"),
        )
    }

    private fun fakeApi(
        handler: suspend (
            action: String,
            page: Int?,
            typeId: Int?,
            keyword: String?,
            hours: Int?,
            ids: String?,
        ) -> MacCmsResponse,
    ): MacCmsApi {
        return object : MacCmsApi {
            override suspend fun getVod(
                action: String,
                page: Int?,
                typeId: Int?,
                keyword: String?,
                hours: Int?,
                ids: String?,
            ): MacCmsResponse = handler(action, page, typeId, keyword, hours, ids)
        }
    }

    private fun response(vararg items: VodDto): MacCmsResponse = MacCmsResponse(list = items.toList())

    private fun vod(
        id: Int,
        name: String,
        year: String = "2026",
        sourceName: String = "",
        episodeUrl: String = "",
    ): VodDto {
        return VodDto(
            vodId = id,
            vodName = name,
            vodYear = year,
            typeName = "剧情",
            vodPlayFrom = sourceName,
            vodPlayUrl = episodeUrl.takeIf { it.isNotBlank() }?.let { "第01集\$$it" }.orEmpty(),
        )
    }
}
