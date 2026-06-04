package com.tvbox.app.domain

import com.tvbox.app.data.MacCmsResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackParserTest {
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
    fun decodesMacCmsResponse() {
        val response = json.decodeFromString<MacCmsResponse>(
            """
            {
              "code": 1,
              "msg": "数据列表",
              "page": 1,
              "pagecount": 2,
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

        val page = response.toPagedMovies()
        val movie = page.movies.singleOrNull()
        assertNotNull(movie)
        assertEquals(1, page.categories.size)
        val decodedMovie = movie!!
        assertEquals("测试影片", decodedMovie.name)
        assertEquals("简介", decodedMovie.description)
        assertEquals("https://video.test/index.m3u8", decodedMovie.playSources.single().episodes.single().url)
    }
}
