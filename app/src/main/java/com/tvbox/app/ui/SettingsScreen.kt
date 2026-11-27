package com.tvbox.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tvbox.app.BuildConfig
import com.tvbox.app.domain.AiProviders
import com.tvbox.app.domain.DeviceMode
import com.tvbox.app.domain.TvFontScale
import com.tvbox.app.domain.TvTheme
import com.tvbox.app.ui.components.PageSurface

private enum class SettingsEditor {
    Device, Theme, Font, Intro, Statistics, ClearStatistics, Provider,
}

@Composable
internal fun SettingsScreen(state: TvBoxUiState, actions: TvBoxViewModel) {
    val settings = state.appSettings
    val provider = AiProviders.find(settings.aiProviderId)
    var editor by remember { mutableStateOf<SettingsEditor?>(null) }
    val dismiss = { editor = null }
    val homeSourceFocus = remember { FocusRequester() }
    var sourcePickerOpened by remember { mutableStateOf(false) }
    LaunchedEffect(state.homeSourcePicker != null) {
        if (state.homeSourcePicker != null) {
            sourcePickerOpened = true
        } else if (sourcePickerOpened) {
            sourcePickerOpened = false
            homeSourceFocus.requestPlayerControlFocus()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, actions) {
        fun closePhoneForms() {
            actions.closeAiConfigDialog()
            actions.closeVideoApiConfigDialog()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) closePhoneForms()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            closePhoneForms()
        }
    }

    when (editor) {
        SettingsEditor.Device -> {
            val context = LocalContext.current
            val detectedMode = remember(context) { context.detectDeviceMode() }
            SettingsChoices(
                "设备模式", DeviceMode.entries, settings.deviceMode, DeviceMode::displayName, dismiss,
                onSelect = actions::updateDeviceMode,
                description = "系统识别为${detectedMode.displayName}，播放以你的选择为准。",
                optionDetail = DeviceMode::description,
            )
        }
        SettingsEditor.Theme -> SettingsChoices(
            "界面主题", TvTheme.entries, settings.theme, TvTheme::displayName, dismiss,
            onSelect = actions::updateTheme,
            optionDetail = { theme ->
                when (theme) {
                    TvTheme.Default -> "顶部快捷入口"
                    TvTheme.Cinema -> "左侧导航和影院布局"
                }
            },
        )
        SettingsEditor.Font -> SettingsChoices(
            "字体大小", TvFontScale.entries, settings.fontScale, TvFontScale::displayName, dismiss,
            onSelect = actions::updateFontScale,
            description = "字体和内容卡片会一起调整。",
        )
        SettingsEditor.Intro -> IntroSkipDialog(
            seconds = settings.defaultIntroSkipSeconds,
            onDismiss = dismiss,
            onSave = actions::updateDefaultIntroSkipSeconds,
        )
        SettingsEditor.Provider -> SettingsChoices(
            "服务商", AiProviders.all, provider, { it.name }, dismiss,
            onSelect = { actions.updateAiProvider(it.id) },
            optionDetail = { if (it.isCustom) "OpenAI 兼容接口" else it.defaultModel },
        )
        SettingsEditor.Statistics -> {
            val health = state.playbackHealth
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("线路质量统计") },
                text = {
                    if (health.entryCount == 0) {
                        Text("暂无线路质量记录")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("最近 30 天 · ${health.entryCount} 条线路记录")
                            Text("成功 ${health.successCount}   失败 ${health.failureCount}   卡顿 ${health.slowBufferCount}")
                        }
                    }
                },
                confirmButton = { SettingsActionButton("返回", dismiss) },
                dismissButton = {
                    if (health.entryCount > 0) {
                        SettingsActionButton("清空统计", { editor = SettingsEditor.ClearStatistics })
                    }
                },
            )
        }
        SettingsEditor.ClearStatistics -> {
            val backToStatistics = { editor = SettingsEditor.Statistics }
            AlertDialog(
                onDismissRequest = backToStatistics,
                title = { Text("清空线路统计") },
                text = { Text("清空后，播放管家会重新学习线路表现。") },
                confirmButton = {
                    SettingsActionButton("清空", {
                        actions.clearPlaybackStats()
                        backToStatistics()
                    })
                },
                dismissButton = { SettingsActionButton("取消", backToStatistics) },
            )
        }
        null -> Unit
    }

    if (state.aiConfigDialogVisible) AiConfigDialog(state, actions)
    if (state.videoApiConfigDialogVisible) VideoApiConfigDialog(state, actions)

    PageSurface { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("设置", style = MaterialTheme.typography.headlineLarge)
                SettingsActionButton("返回", { actions.goBack() })
            }
            Spacer(Modifier.height(18.dp))
            SettingsColumns(
                modifier = Modifier.weight(1f),
                left = {
                    SettingsGroup("设备与外观") {
                        SettingsRow("设备模式", settings.deviceMode.displayName) { editor = SettingsEditor.Device }
                        SettingsDivider()
                        SettingsRow("界面主题", settings.theme.displayName) { editor = SettingsEditor.Theme }
                        SettingsDivider()
                        SettingsRow("字体大小", settings.fontScale.displayName) { editor = SettingsEditor.Font }
                    }
                    SettingsGroup("播放") {
                        SettingsRow("默认跳过片头", introSkipLabel(settings.defaultIntroSkipSeconds)) { editor = SettingsEditor.Intro }
                        SettingsDivider()
                        SettingsSwitchRow("自动换线", settings.playbackAgentAutoSwitchEnabled, actions::updatePlaybackAgentAutoSwitch)
                        SettingsDivider()
                        SettingsRow("线路质量统计", "查看") { editor = SettingsEditor.Statistics }
                    }
                    SettingsGroup("关于") {
                        Text(
                            "QQ群：734624519",
                            Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                right = {
                    SettingsGroup("视频接口") {
                        SettingsRow(
                            "首页数据源", state.preferredHomeApiLine?.name ?: "未选择",
                            enabled = state.apiLines.isNotEmpty(),
                            modifier = Modifier.focusRequester(homeSourceFocus)
                                .focusProperties { canFocus = state.apiLines.isNotEmpty() },
                            onClick = actions::openHomeSourcePicker,
                        )
                        SettingsDivider()
                        SettingsRow("扫码添加接口", onClick = actions::openVideoApiConfigDialog)
                    }
                    SettingsGroup("大模型") {
                        SettingsRow("服务商", provider.name) { editor = SettingsEditor.Provider }
                        SettingsDivider()
                        if (provider.isCustom) {
                            val config = settings.customAiConfig
                            SettingsRowContent("API 地址", config?.apiBaseUrl ?: "未配置", enabled = true, input = Modifier) {}
                            SettingsDivider()
                            SettingsRowContent("模型", config?.modelId ?: "未选择", enabled = true, input = Modifier) {}
                            SettingsDivider()
                            SettingsRow(
                                "手机扫码配置",
                                if (config?.apiKey.isNullOrBlank()) "API Key 未配置" else "API Key 已配置",
                                onClick = actions::openAiConfigDialog,
                            )
                        } else {
                            val config = settings.aiModelConfigs[provider.id]
                            SettingsRow("模型", config?.modelId.orEmpty().ifBlank { provider.defaultModel },
                                onClick = actions::openAiConfigDialog)
                            SettingsDivider()
                            SettingsRow("API Key", if (config?.apiKey.isNullOrBlank()) "未配置" else "已配置",
                                onClick = actions::openAiConfigDialog)
                        }
                    }
                    SettingsGroup("更新") {
                        SettingsSwitchRow("启动时检查更新", settings.checkUpdatesOnStartup, actions::updateStartupUpdateCheck)
                        SettingsDivider()
                        SettingsRow(
                            "立即检查更新", if (state.updateChecking) "检查中" else "v${BuildConfig.VERSION_NAME}",
                            enabled = !state.updateChecking,
                        ) { actions.checkForAppUpdate(showError = true) }
                        state.updateError?.let {
                            Text(
                                it,
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsColumns(
    modifier: Modifier = Modifier,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val spacing = Arrangement.spacedBy(16.dp)
        val scroll = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
        if (maxWidth >= 640.dp) {
            Row(scroll, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = spacing, content = left)
                Column(Modifier.weight(1f), verticalArrangement = spacing, content = right)
            }
        } else {
            Column(scroll, verticalArrangement = spacing) {
                left()
                right()
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, Modifier.padding(start = 2.dp), style = MaterialTheme.typography.titleSmall)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        ) {
            Column(Modifier.padding(1.dp), content = content)
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
}

@Composable
private fun SettingsRow(
    title: String,
    value: String = "",
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SettingsRowContent(
        title, value, enabled,
        input = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        modifier = modifier,
    ) {
        Text("›", Modifier.clearAndSetSemantics { }, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingsRowContent(
        title, if (checked) "开启" else "关闭", enabled = true,
        input = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        announceValue = false,
    ) {
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SettingsRowContent(
    title: String,
    value: String,
    enabled: Boolean,
    input: Modifier,
    modifier: Modifier = Modifier,
    announceValue: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(if (focused) colors.primary.copy(alpha = 0.13f) else Color.Transparent)
            .border(2.dp, if (focused) colors.primary else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .then(input)
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, Modifier.weight(1f), color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (value.isNotEmpty()) {
            Box(Modifier.weight(0.9f), contentAlignment = Alignment.CenterEnd) {
                Text(
                    value,
                    if (announceValue) Modifier else Modifier.clearAndSetSemantics { },
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun <T> SettingsChoices(
    title: String,
    options: List<T>,
    selected: T?,
    optionTitle: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    description: String? = null,
    optionDetail: ((T) -> String)? = null,
) {
    val initialIndex = options.indexOfFirst { it == selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val initialFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                LazyColumn(Modifier.heightIn(max = 280.dp), state = listState) {
                    itemsIndexed(options) { index, option ->
                        val checked = option == selected
                        val choice = Modifier
                            .then(if (index == initialIndex) Modifier.focusRequester(initialFocus) else Modifier)
                        SettingsChoiceRow(optionTitle(option), optionDetail?.invoke(option), checked, choice) {
                            onDismiss()
                            onSelect(option)
                        }
                    }
                }
            }
            if (options.isNotEmpty()) {
                LaunchedEffect(Unit) { initialFocus.requestPlayerControlFocus() }
            }
        },
        confirmButton = { SettingsActionButton("返回", onDismiss) },
    )
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    description: String?,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(if (selected || focused) colors.primary.copy(alpha = 0.13f) else Color.Transparent)
            .border(2.dp, if (focused) colors.primary else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        }
        if (selected) Text("✓", color = colors.primary)
    }
}
