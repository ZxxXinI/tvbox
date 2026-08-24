package com.tvbox.app.data

import com.tvbox.app.domain.ApiLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieRepositoryTest {
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
