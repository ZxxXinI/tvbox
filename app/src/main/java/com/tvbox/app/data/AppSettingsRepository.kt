package com.tvbox.app.data

import android.content.Context
import com.tvbox.app.domain.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AppSettingsRepository {
    suspend fun getSettings(): AppSettings
    suspend fun saveSettings(settings: AppSettings): AppSettings
}

class SharedAppSettingsRepository(context: Context) : AppSettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    override suspend fun getSettings(): AppSettings = withContext(Dispatchers.IO) {
        AppSettings(
            checkUpdatesOnStartup = prefs.getBoolean(KEY_CHECK_UPDATES_ON_STARTUP, true),
        )
    }

    override suspend fun saveSettings(settings: AppSettings): AppSettings = withContext(Dispatchers.IO) {
        prefs.edit()
            .putBoolean(KEY_CHECK_UPDATES_ON_STARTUP, settings.checkUpdatesOnStartup)
            .apply()
        settings
    }

    private companion object {
        const val KEY_CHECK_UPDATES_ON_STARTUP = "check_updates_on_startup"
    }
}
