package com.tvbox.app.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tvbox.app.domain.MAX_INTRO_SKIP_SECONDS
import com.tvbox.app.ui.components.tvFocusScale

internal val introSkipPresets = listOf(0, 30, 60, 90, 120)

internal fun introSkipLabel(seconds: Int): String = if (seconds == 0) "不跳过" else "${seconds} 秒"

@Composable
internal fun IntroSkipDuration(
    seconds: Int,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("跳过时长", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(seconds.toString(), Modifier.alignByBaseline(), style = MaterialTheme.typography.displaySmall)
            Text("秒", Modifier.alignByBaseline(), color = secondaryColor, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "从头播放时自动跳过开头。\n续播保留原进度。",
            color = secondaryColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun IntroSkipDialog(
    seconds: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var number by remember { mutableIntStateOf(seconds.coerceIn(0, MAX_INTRO_SKIP_SECONDS)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("默认跳过片头") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IntroSkipDuration(number)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntroSkipOption("−5 秒", { number = (number - 5).coerceAtLeast(0) }, Modifier.weight(1f))
                    IntroSkipOption("+5 秒", { number = (number + 5).coerceAtMost(MAX_INTRO_SKIP_SECONDS) }, Modifier.weight(1f))
                }
                Text("常用时长", style = MaterialTheme.typography.bodySmall)
                introSkipPresets.chunked(3).forEach { presets ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        presets.forEach { value ->
                            IntroSkipOption(
                                text = introSkipLabel(value),
                                onClick = { number = value },
                                modifier = Modifier.weight(1f),
                                selected = number == value,
                            )
                        }
                        repeat(3 - presets.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(number); onDismiss() },
                modifier = Modifier.tvFocusScale(RoundedCornerShape(12.dp))) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun IntroSkipOption(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.tvFocusScale(shape),
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(text, maxLines = 1)
    }
}
