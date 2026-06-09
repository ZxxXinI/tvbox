package com.tvbox.app.domain

import com.tvbox.app.data.MacCmsResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackParserTest {
    private val testLine = ApiLine(
        id = "test",
        name = "测试线路",
        baseUrls = listOf("https://example.test/api.php/provide/vod/"),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun parsesMultipleSourcesAndEpisodes() {
        val sources = parsePlaySources(
            playFrom = "rym3u8\$\$\$ruyi",
            playUrl = "第01集\$https://a.test/1.m3u8#第02集\$https://a.test/2.m3u8\$\$\$云播\$https://b.test/play",
        )

        assertEquals(2, sources.size)
        assertEquals("rym3u8", sources[0].name)
        assertEquals("第02集", sources[0].episodes[1].title)
        assertEquals("https://a.test/2.m3u8", sources[0].episodes[1].url)
        assertEquals("ruyi", sources[1].name)
        assertEquals("https://b.test/play", sources[1].episodes.single().url)
    }

    @Test
    fun acceptsEpisodeUrlWithoutDollarDelimiter() {
        val sources = parsePlaySources(
            playFrom = "single",
            playUrl = "https://a.test/movie.m3u8",
        )

        assertEquals("播放", sources.single().episodes.single().title)
        assertEquals("https://a.test/movie.m3u8", sources.single().episodes.single().url)
    }

    @Test
    fun ignoresEmptyPlaybackData() {
        val sources = parsePlaySources(playFrom = "", playUrl = "")
        assertTrue(sources.isEmpty())
    }

    @Test
    fun cleansHtmlAndCommonEntities() {
        val cleaned = cleanHtml("<p>你好&nbsp;<b>TVBox</b>&amp;电影</p>")
        assertEquals("你好 TVBox &电影", cleaned)
    }

    @Test
    fun parsesLiveChannels() {
        val channels = parseLiveChannels(
            """
            1CCTV-1,http://video.test/1/index.m3u8
            CCTV-2,https://video.test/2/index.m3u8
            无效频道,
            没有逗号
            CCTV-3,http://video.test/3/index.m3u8,extra
            CCTV-1,http://video.test/1/index.m3u8
            """.trimIndent(),
        )

        assertEquals(3, channels.size)
        assertEquals(1, channels[0].number)
        assertEquals("CCTV-1", channels[0].name)
        assertEquals("http://video.test/1/index.m3u8", channels[0].url)
        assertEquals(3, channels[2].number)
        assertEquals("http://video.test/3/index.m3u8,extra", channels[2].url)
    }

    @Test
    fun decodesMacCmsResponse() {
        val response = json.decodeFromString<MacCmsResponse>(
            """
            {
              "code": 1,
              "msg": "数据列表",
              "page": "1",
              "pagecount": "2",
              "total": 1,
              "class": [{"type_id": 1, "type_name": "电影片"}],
              "list": [{
                "vod_id": 12345,
                "vod_name": "测试影片",
                "type_id": 1,
                "type_name": "电影片",
                "vod_pic": "https://img.test/1.jpg",
                "vod_year": "2026",
                "vod_content": "<p>简介</p>",
                "vod_play_from": "rym3u8",
                "vod_play_url": "HD${'$'}https://video.test/index.m3u8"
              }]
            }
            """.trimIndent(),
        )

        val page = response.toPagedMovies(testLine)
        val movie = page.movies.singleOrNull()
        assertNotNull(movie)
        assertEquals(1, page.categories.size)
        val decodedMovie = movie!!
        assertEquals("test", decodedMovie.apiLineId)
        assertEquals("测试线路", decodedMovie.apiLineName)
        assertEquals("测试影片", decodedMovie.name)
        assertEquals("简介", decodedMovie.description)
        assertEquals("https://video.test/index.m3u8", decodedMovie.playSources.single().episodes.single().url)
    }

    @Test
    fun filtersBlockedCategoriesAndMovies() {
        val response = json.decodeFromString<MacCmsResponse>(
            """
            {
              "code": 1,
              "class": [
                {"type_id": 1, "type_pid": 0, "type_name": "电影片"},
                {"type_id": 7, "type_pid": 1, "type_name": "喜剧片"},
                {"type_id": 2, "type_pid": 1, "type_name": "伦理片"},
                {"type_id": 3, "type_pid": 0, "type_name": "电影解说"},
                {"type_id": 41, "type_pid": 0, "type_name": "演员"},
                {"type_id": 42, "type_pid": 0, "type_name": "新闻资讯"},
                {"type_id": 43, "type_pid": 42, "type_name": "电影资讯"},
                {"type_id": 44, "type_pid": 42, "type_name": "娱乐新闻"}
              ],
              "list": [
                {
                  "vod_id": 1,
                  "vod_name": "正常电影",
                  "type_id": 1,
                  "type_name": "电影片",
                  "vod_play_from": "rym3u8",
                  "vod_play_url": "HD${'$'}https://video.test/ok.m3u8"
                },
                {
                  "vod_id": 2,
                  "vod_name": "伦理故事",
                  "type_id": 2,
                  "type_name": "伦理片",
                  "vod_play_from": "rym3u8",
                  "vod_play_url": "HD${'$'}https://video.test/blocked.m3u8"
                },
                {
                  "vod_id": 3,
                  "vod_name": "热门电影解说",
                  "type_id": 3,
                  "type_name": "电影解说",
                  "vod_play_from": "rym3u8",
                  "vod_play_url": "HD${'$'}https://video.test/commentary.m3u8"
                }
              ]
            }
            """.trimIndent(),
        )

        val page = response.toPagedMovies(testLine)

        assertEquals(listOf("电影片", "喜剧片"), page.categories.map { it.name })
        assertEquals(listOf(0, 1), page.categories.map { it.parentId })
        assertEquals(listOf("正常电影"), page.movies.map { it.name })
    }

    @Test
    fun calculatesHistoryProgressPercent() {
        val item = WatchHistoryItem(
            movieId = 1,
            movieName = "测试",
            posterUrl = "",
            typeName = "电影",
            remarks = "",
            sourceIndex = 0,
            sourceName = "rym3u8",
            episodeIndex = 0,
            episodeTitle = "第01集",
            episodeUrl = "https://video.test/index.m3u8",
            positionMs = 30_000,
            durationMs = 120_000,
            updatedAtEpochMs = 1,
        )

        assertEquals(25, item.progressPercent)
    }
}
