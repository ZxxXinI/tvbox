package com.tvbox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.tvbox.app.data.*
import com.tvbox.app.domain.*
import com.tvbox.app.ui.*
import com.tvbox.app.ui.theme.TVBoxTheme

/** Debug-only deterministic acceptance harness; never packaged in Release. */
class AcceptanceActivity : ComponentActivity() {
    private val verifySourcePicker get() = intent.getBooleanExtra("source_picker", false)
    private val vm: TvBoxViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val service = "http://127.0.0.1:8766"
                val platform = DefaultPlatformLiveRepository()
                val localPlatform = object : PlatformLiveRepository by platform {
                    override suspend fun getSites(serviceUrl: String) = platform.getSites(service)
                    override suspend fun getCategoryTree(serviceUrl: String, site: String) = platform.getCategoryTree(service, site)
                    override suspend fun getRooms(serviceUrl: String, site: String, categoryId: String, page: Int) = platform.getRooms(service, site, categoryId, page)
                    override suspend fun resolve(serviceUrl: String, room: PlatformLiveRoom, forceRefresh: Boolean) = platform.resolve(service, room, forceRefresh)
                }
                val persistentSettings = SharedAppSettingsRepository(this@AcceptanceActivity)
                val douban = if (verifySourcePicker) {
                    object : DoubanHotRepository {
                        override suspend fun getRecentHot(page: Int, forceRefresh: Boolean, category: DoubanHotCategory) =
                            PagedDoubanHotMovies(page, pageCount = 1, total = 0, items = emptyList())
                    }
                } else DefaultDoubanHotRepository()
                return TvBoxViewModel(
                    repository = DefaultMovieRepository(listOf(
                        ApiLine("acceptance", "验收接口", listOf("$service/api/")),
                        ApiLine("acceptance-alt", "验收备用", listOf("$service/alt-api/")),
                    )),
                    doubanHotRepository = douban,
                    liveRepository = DefaultLiveRepository(listOf("$service/live.txt")),
                    platformLiveRepository = localPlatform,
                    appSettingsRepository = object : AppSettingsRepository {
                        private val theme = if (intent.getStringExtra("theme") == "cinema") TvTheme.Cinema else TvTheme.Default
                        private var settings = AppSettings(
                            homeApiLineId = "acceptance",
                            homeSourcePromptHandled = true,
                            checkUpdatesOnStartup = false,
                            theme = theme,
                        )
                        override suspend fun getSettings(): AppSettings {
                            if (!verifySourcePicker) return settings
                            val saved = persistentSettings.getSettings()
                            val savedLine = saved.homeApiLineId
                            return saved.copy(
                                homeApiLineId = if (savedLine == AppSettings().homeApiLineId) "acceptance" else savedLine,
                                checkUpdatesOnStartup = false,
                                theme = theme,
                            )
                        }
                        override suspend fun saveSettings(settings: AppSettings): AppSettings {
                            this.settings = settings
                            return if (verifySourcePicker) persistentSettings.saveSettings(settings) else settings
                        }
                    },
                    historyRepository = SharedHistoryRepository(this@AcceptanceActivity),
                    platformLiveFavoritesRepository = SharedPlatformLiveFavoritesRepository(this@AcceptanceActivity),
                ) as T
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = vm.state.collectAsStateWithLifecycle().value
            LaunchedEffect(Unit) { if (!verifySourcePicker) vm.selectParentCategory(2) }
            TVBoxTheme(state.appSettings.theme, state.appSettings.fontScale) {
                BackHandler(state.screen != TvScreen.Home) { vm.goBack() }
                TvBoxApp(state, vm)
            }
        }
    }
}
