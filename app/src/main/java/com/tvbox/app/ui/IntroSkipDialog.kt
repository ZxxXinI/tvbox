package com.tvbox.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tvbox.app.domain.MAX_INTRO_SKIP_SECONDS
import com.tvbox.app.ui.components.tvFocusScale

@Composable
internal fun IntroSkipDialog(
    seconds: Int?,
    globalSeconds: Int,
    allowInherit: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit,
) {
    var inherit by remember { mutableStateOf(allowInherit && seconds == null) }
    var number by remember { mutableIntStateOf((seconds ?: globalSeconds).coerceIn(0, MAX_INTRO_SKIP_SECONDS)) }
    fun select(value: Int) {
        inherit = false
        number = value.coerceIn(0, MAX_INTRO_SKIP_SECONDS)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (allowInherit) "本片／整部剧的片头" else "默认跳过片头") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("只在从头播放或切集时跳过，不改变当前播放进度。")
                Text("当前跳过：${number} 秒", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 30, 60, 90, 120).forEach { value ->
                        OutlinedButton(onClick = { select(value) }, modifier = Modifier.tvFocusScale(RoundedCornerShape(12.dp))) {
                            Text((if (value == 0) "不跳过" else "${value}秒") +
                                if (number == value) " ✓" else "")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { select(number - 5) },
                        modifier = Modifier.tvFocusScale(RoundedCornerShape(12.dp))) { Text("−5 秒") }
                    OutlinedButton(onClick = { select(number + 5) },
                        modifier = Modifier.tvFocusScale(RoundedCornerShape(12.dp))) { Text("＋5 秒") }
                }
                Text("0 秒表示不跳过；可通过预设或 ±5 秒调整，仅保存在本机。",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(if (inherit) null else number); onDismiss() },
                modifier = Modifier.tvFocusScale(RoundedCornerShape(12.dp))) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
