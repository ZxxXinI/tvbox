package com.tvbox.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@Composable
internal fun PhoneSearchDialog(state: TvBoxUiState, actions: TvBoxViewModel) {
    AlertDialog(
        onDismissRequest = actions::closePhoneSearchDialog,
        title = { Text("手机扫码输入") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("手机扫码后，在输入窗口填写影片名称并提交，电视会直接开始搜索。")
                val url = state.phoneSearchUrl
                if (url != null) {
                    val bitmap = remember(url) { createQrBitmap(url, 480) }
                    Image(bitmap.asImageBitmap(), "手机搜索二维码", Modifier.sizeIn(maxWidth = 240.dp, maxHeight = 240.dp))
                    Text("二维码 5 分钟有效，仅可成功提交一次。", style = MaterialTheme.typography.bodySmall)
                } else if (state.phoneSearchError == null) {
                    CircularProgressIndicator()
                    Text("正在生成二维码…")
                }
                state.phoneSearchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("手机和盒子需处于可互通的同一局域网。盒子接网线也可以；访客网络隔离可能导致连接失败。",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (state.phoneSearchError != null) Button(onClick = actions::openPhoneSearchDialog) { Text("重新生成") }
            else TextButton(onClick = actions::closePhoneSearchDialog) { Text("取消") }
        },
        dismissButton = {
            if (state.phoneSearchError != null) TextButton(onClick = actions::closePhoneSearchDialog) { Text("取消") }
        },
    )
}
