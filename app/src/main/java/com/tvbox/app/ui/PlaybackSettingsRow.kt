package com.tvbox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 沿用电视频道行的色彩、间距、圆角和焦点描边，不使用操作栏按钮样式。 */
@Composable
internal fun PlaybackSettingsRow(
    title: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String = if (selected) "✓" else "",
    enabled: Boolean = true,
    inlineValue: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = Color(0xFF7AE2BA)
    val background = when {
        selected -> accent.copy(alpha = 0.1f)
        focused -> Color.White.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(background)
            .border(1.dp, if (focused) accent else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val titleColor = Color.White.copy(alpha = when {
            !enabled -> 0.42f
            selected || focused -> 1f
            else -> 0.78f
        })
        val valueColor = Color.White.copy(alpha = if (focused) 0.75f else 0.58f)
        if (inlineValue) {
            Text(title, Modifier.weight(1f), color = titleColor, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.weight(1.5f).padding(start = 8.dp), contentAlignment = Alignment.CenterEnd) {
                Text(value, color = valueColor, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = titleColor, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, color = valueColor, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailingText.isNotEmpty()) {
            Text(trailingText, Modifier.padding(start = 8.dp), color = if (selected || focused) accent else Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.titleMedium)
        }
    }
}
