package com.tvbox.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tvbox.app.data.HomeSourceProbe
import com.tvbox.app.data.HomeSourceProbeStatus
import com.tvbox.app.domain.ApiLine

data class HomeSourcePickerState(
    val onboarding: Boolean,
    val testing: Boolean = false,
    val tested: Boolean = false,
    val probes: Map<String, HomeSourceProbe> = emptyMap(),
) {
    fun order(lines: List<ApiLine>): List<ApiLine> = if (!tested) lines else
        lines.sortedBy { probes[it.id]?.elapsedMs ?: Long.MAX_VALUE }

    fun recommendation(lines: List<ApiLine>): String? = if (!tested) null else
        order(lines).firstOrNull { probes[it.id]?.status == HomeSourceProbeStatus.Available }?.id
}

@Composable
internal fun HomeSourcePicker(
    picker: HomeSourcePickerState,
    lines: List<ApiLine>,
    current: ApiLine?,
    onScan: () -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sources = remember(lines, picker) { picker.order(lines.distinctBy { it.id }) }
    val recommendation = picker.recommendation(sources)
    val focusId = when {
        picker.testing -> null
        picker.tested -> recommendation
        else -> sources.firstOrNull { it.id == current?.id }?.id ?: sources.firstOrNull()?.id
    }
    val listState = rememberLazyListState()
    val sourceFocus = remember { FocusRequester() }
    val scanFocus = remember { FocusRequester() }
    val dismissFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        BoxWithConstraints(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp).widthIn(max = 640.dp).fillMaxWidth(),
        ) {
            val listHeight = minOf(310.dp, maxHeight * 0.51f)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth >= 370.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("选择首页数据源", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                                if (!picker.testing) {
                                    SettingsActionButton("重新测速", onScan, modifier = Modifier.focusRequester(scanFocus))
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("选择首页数据源", style = MaterialTheme.typography.titleLarge)
                                if (!picker.testing) {
                                    SettingsActionButton("重新测速", onScan, modifier = Modifier.focusRequester(scanFocus))
                                }
                            }
                        }
                    }
                    current?.let {
                        Text("当前使用：${it.name}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    if (picker.testing || picker.tested) {
                        val available = picker.probes.values.count { it.status == HomeSourceProbeStatus.Available }
                        Text(
                            "已测试 ${picker.probes.size} / ${sources.size} · $available 个可用",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (picker.testing) {
                            LinearProgressIndicator(
                                progress = { picker.probes.size.toFloat() / sources.size.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (recommendation == null) {
                            Text("暂时没有可用接口，可以重新测速。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = listHeight),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(sources, key = { it.id }) { source ->
                            HomeSourceRow(
                                source = source,
                                recommended = recommendation == source.id,
                                probe = picker.probes[source.id],
                                testing = picker.testing,
                                modifier = if (focusId == source.id) Modifier.focusRequester(sourceFocus) else Modifier,
                                onSelect = { onSelect(source.id) },
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SettingsActionButton(
                            if (picker.onboarding) "跳过，进入首页" else "返回",
                            onDismiss, modifier = Modifier.focusRequester(dismissFocus),
                        )
                    }
                }
            }
            LaunchedEffect(picker.testing, picker.tested, focusId) {
                when {
                    picker.testing -> dismissFocus.requestPlayerControlFocus()
                    focusId != null -> {
                        val index = sources.indexOfFirst { it.id == focusId }
                        if (index >= 0) {
                            listState.scrollToItem(index)
                            sourceFocus.requestPlayerControlFocus()
                        }
                    }
                    else -> scanFocus.requestPlayerControlFocus()
                }
            }
        }
    }
}

@Composable
private fun HomeSourceRow(
    source: ApiLine,
    recommended: Boolean,
    probe: HomeSourceProbe?,
    testing: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(9.dp)
    val status = when (probe?.status) {
        HomeSourceProbeStatus.Available -> "${probe.elapsedMs} ms"
        HomeSourceProbeStatus.Timeout -> "超时"
        HomeSourceProbeStatus.Failed -> "请求失败"
        HomeSourceProbeStatus.Empty -> "无可用内容"
        null -> if (testing) "等待测试" else ""
    }
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(if (focused) colors.primary.copy(alpha = 0.14f) else colors.surface)
            .border(2.dp, if (focused) colors.primary else Color.Transparent, shape)
            .focusProperties { canFocus = !testing }
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = !testing, role = Role.Button, onClick = onSelect)
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(source.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (recommended) Text("推荐", color = colors.primary, style = MaterialTheme.typography.labelMedium)
        if (status.isNotEmpty()) {
            Text(status, color = when (probe?.status) {
                HomeSourceProbeStatus.Available -> colors.primary
                HomeSourceProbeStatus.Timeout, HomeSourceProbeStatus.Failed -> colors.tertiary
                else -> colors.onSurfaceVariant
            }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
