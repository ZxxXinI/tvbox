package com.tvbox.app.data

import android.content.Context
import com.tvbox.app.domain.AppSettings
import com.tvbox.app.domain.CustomVideoApiLine
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.withContext

interface AppSettingsRepository {
    suspend fun getSettings(): AppSettings
    suspend fun saveSettings(settings: AppSettings): AppSettings
}

class SharedAppSettingsRepository(context: Context) : AppSettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    override suspend fun getSettings(): AppSettings = withContext(Dispatchers.IO) {
        AppSettings(
            homeApiLineId = prefs.getString(KEY_HOME_API_LINE_ID, AppSettings().homeApiLineId)
                ?: AppSettings().homeApiLineId,
            customVideoApiLines = readCustomVideoApiLines(),
            aiProviderId = prefs.getString(KEY_AI_PROVIDER_ID, AppSettings().aiProviderId)
                ?: AppSettings().aiProviderId,
            aiModelName = prefs.getString(KEY_AI_MODEL_NAME, AppSettings().aiModelName)
                ?: AppSettings().aiModelName,
            aiApiKey = prefs.getString(KEY_AI_API_KEY, AppSettings().aiApiKey)
                ?: AppSettings().aiApiKey,
            checkUpdatesOnStartup = prefs.getBoolean(KEY_CHECK_UPDATES_ON_STARTUP, true),
            playbackAgentAutoSwitchEnabled = prefs.getBoolean(KEY_PLAYBACK_AGENT_AUTO_SWITCH, true),
        )
    }

    override suspend fun saveSettings(settings: AppSettings): AppSettings = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_HOME_API_LINE_ID, settings.homeApiLineId)
            .putString(KEY_CUSTOM_VIDEO_API_LINES, json.encodeToString(settings.customVideoApiLines))
            .putString(KEY_AI_PROVIDER_ID, settings.aiProviderId)
            .putString(KEY_AI_MODEL_NAME, settings.aiModelName)
            .putString(KEY_AI_API_KEY, settings.aiApiKey)
            .putBoolean(KEY_CHECK_UPDATES_ON_STARTUP, settings.checkUpdatesOnStartup)
            .putBoolean(KEY_PLAYBACK_AGENT_AUTO_SWITCH, settings.playbackAgentAutoSwitchEnabled)
            .apply()
        settings
    }

    private fun readCustomVideoApiLines(): List<CustomVideoApiLine> {
        val raw = prefs.getString(KEY_CUSTOM_VIDEO_API_LINES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<CustomVideoApiLine>>(raw) }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val KEY_HOME_API_LINE_ID = "home_api_line_id"
        const val KEY_CUSTOM_VIDEO_API_LINES = "custom_video_api_lines"
        const val KEY_AI_PROVIDER_ID = "ai_provider_id"
        const val KEY_AI_MODEL_NAME = "ai_model_name"
        const val KEY_AI_API_KEY = "ai_api_key"
        const val KEY_CHECK_UPDATES_ON_STARTUP = "check_updates_on_startup"
        const val KEY_PLAYBACK_AGENT_AUTO_SWITCH = "playback_agent_auto_switch"
    }
}
