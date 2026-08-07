package com.tvbox.app.data

import com.tvbox.app.domain.findBestTitleMatchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoubanHotRepositoryTest {
    @Test
    fun hotRequestUsesTwentyItemsAndExpectedOffset() {
        val request = DoubanHotRequest(page = 3)

        assertEquals(20, request.limit)
        assertEquals(40, request.start)
        assertEquals("tv", request.category.category)
        assertEquals("tv", request.category.type)
    }

    @Test
    fun parsesHotItemsAndFiltersBlockedContent() {
        val page = parseDoubanHotPage(
            rawResponse = """
                {
                  "total": 42,
                  "items": [
                    {
                      "id": "1",
                      "title": "Example Drama",
                      "pic": {"large": "https://img1.doubanio.com/view/photo/m_ratio_poster/public/example.jpg"},
                      "rating": {"count": 9732, "value": 8.5},
                      "card_subtitle": "2026 / China / Drama"
                    },
                    {
                      "id": "2",
                      "title": "电影解说",
                      "card_subtitle": "blocked"
                    }
                  ]
                }
            """.trimIndent(),
            request = DoubanHotRequest(page = 2),
        )

        assertEquals(2, page.page)
        assertEquals(3, page.pageCount)
        assertEquals(42, page.total)
        assertEquals(1, page.items.size)
        assertEquals("Example Drama", page.items.single().title)
        assertEquals(8.5, page.items.single().score, 0.0)
        assertEquals(9732, page.items.single().ratingCount)
    }

    @Test
    fun titleMatcherPrefersExactMatchBeforePartialMatch() {
        val candidates = listOf("Example Drama Special", "Example Drama")

        assertEquals(1, findBestTitleMatchIndex("Example Drama", candidates))
        assertEquals(0, findBestTitleMatchIndex("Example Drama Special Edition", candidates))
        assertNull(findBestTitleMatchIndex("Different Show", candidates))
    }
}
