package com.tvbox.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AiRecommendationParserTest {
    @Test
    fun `parses plain recommendation json`() {
        val result = parseAiRecommendationContent(
            """
            {
              "title": "为你推荐的悬疑电视剧",
              "items": [
                {
                  "name": "漫长的季节",
                  "year": "2023",
                  "area": "中国大陆",
                  "type": "悬疑 / 犯罪",
                  "reason": "口碑高，适合喜欢现实悬疑的观众。",
                  "score": "9.4",
                  "searchKeyword": "漫长的季节"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("为你推荐的悬疑电视剧", result.title)
        assertEquals(1, result.items.size)
        assertEquals("漫长的季节", result.items.first().name)
        assertEquals("悬疑 / 犯罪", result.items.first().genre)
        assertEquals("漫长的季节", result.items.first().searchKeyword)
    }

    @Test
    fun `parses fenced json and falls back to name as search keyword`() {
        val result = parseAiRecommendationContent(
            """
            ```json
            {
              "title": "AI 推荐",
              "items": [
                {
                  "title": "隐秘的角落",
                  "year": "2020",
                  "genre": "悬疑",
                  "reason": "节奏紧凑"
                }
              ]
            }
            ```
            """.trimIndent(),
        )

        assertEquals("AI 推荐", result.title)
        assertEquals("隐秘的角落", result.items.first().name)
        assertEquals("悬疑", result.items.first().genre)
        assertEquals("隐秘的角落", result.items.first().searchKeyword)
    }
}
