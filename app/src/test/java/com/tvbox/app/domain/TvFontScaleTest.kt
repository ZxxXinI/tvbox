package com.tvbox.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvFontScaleTest {
    @Test
    fun storageKeyResolvesToExpectedFontScale() {
        assertEquals(TvFontScale.Normal, TvFontScale.fromStorageKey("normal"))
        assertEquals(TvFontScale.Large, TvFontScale.fromStorageKey("large"))
        assertEquals(TvFontScale.ExtraLarge, TvFontScale.fromStorageKey("extra_large"))
    }

    @Test
    fun unknownStorageKeyFallsBackToNormal() {
        assertEquals(TvFontScale.Normal, TvFontScale.fromStorageKey("legacy"))
        assertEquals(TvFontScale.Normal, TvFontScale.fromStorageKey(null))
    }

    @Test
    fun fontScaleIncreasesForEachLargerDisplayMode() {
        assertTrue(TvFontScale.Large.typographyScale > TvFontScale.Normal.typographyScale)
        assertTrue(TvFontScale.ExtraLarge.typographyScale > TvFontScale.Large.typographyScale)
    }
}
