package com.tvbox.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

fun Modifier.tvFocusScale(
    shape: Shape,
    focusedBorder: Color = Color(0xFF23D1A8),
    idleBorder: Color = Color(0x334D5B6F),
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.045f else 1f, label = "tv-focus-scale")
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(
            width = if (focused) 3.dp else 1.dp,
            color = if (focused) focusedBorder else idleBorder,
            shape = shape,
        )
}

