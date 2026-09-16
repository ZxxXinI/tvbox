package com.tvbox.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFilterTest {
    @Test
    fun blocksEthicsAndMovieCommentaryWithoutBlockingLetterX() {
        assertTrue(isBlockedContent("伦理片"))
        assertTrue(isBlockedContent("热门电影解说"))
        assertTrue(isBlockedContent(null, "电影解说", "正常片名"))
        assertFalse(isBlockedContent("Example Drama X", "动作片"))
        assertFalse(isBlockedContent("正常电影", null))
    }
}
