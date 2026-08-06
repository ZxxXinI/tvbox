package com.tvbox.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTypographyTest {
    @Test
    fun scaledTypographyKeepsUnspecifiedLetterSpacing() {
        val scaled = TextStyle(fontSize = 16.sp).scaled(1.18f)

        assertEquals(18.88f, scaled.fontSize.value, 0.001f)
        assertEquals(TextUnit.Unspecified, scaled.letterSpacing)
    }

    @Test
    fun scaledTypographyScalesSpecifiedLetterSpacing() {
        val scaled = TextStyle(fontSize = 16.sp, letterSpacing = 0.5.sp).scaled(1.36f)

        assertEquals(21.76f, scaled.fontSize.value, 0.001f)
        assertEquals(0.68f, scaled.letterSpacing.value, 0.001f)
    }
}
