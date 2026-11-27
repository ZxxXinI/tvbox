package com.tvbox.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvbox.app.data.AppSettingsRepository
import com.tvbox.app.BuildConfig
import com.tvbox.app.data.AiRecommendationRepository
import com.tvbox.app.data.AppUpdateRepository
import com.tvbox.app.data.DefaultAiRecommendationRepository
import com.tvbox.app.data.DefaultMovieRepository
import com.tvbox.app.data.DefaultLiveRepository
import com.tvbox.app.data.DefaultDoubanHotRepository
import com.tvbox.app.data.DoubanHotRepository
import com.tvbox.app.data.HistoryRepository
import com.tvbox.app.data.LiveRepository
import com.tvbox.app.data.MovieRepository
import com.tvbox.app.data.VideoSourceCatalogRepository
import com.tvbox.app.data.MovieDetailUpdate
import com.tvbox.app.data.PlaybackHealthRepository
import com.tvbox.app.data.DefaultPlatformLiveRepository
import com.tvbox.app.data.PlatformLiveRepository
import com.tvbox.app.data.PlatformLiveFavoritesRepository
import com.tvbox.app.data.PhoneFormServer
import com.tvbox.app.data.PhoneFormSubmission
import com.tvbox.app.data.PhoneSearchServer
import com.tvbox.app.data.aiConfigServer
import com.tvbox.app.data.customAiConfigServer
import com.tvbox.app.data.userAiRequestConfigOrNull
import com.tvbox.app.data.videoApiConfigServer
import com.tvbox.app.data.getHomeCategoriesOrNull
import com.tvbox.app.data.loadAvailableHomeCatalog
import com.tvbox.app.data.probeHomeSources
import com.tvbox.app.domain.AiRecommendationItem
import com.tvbox.app.domain.AiModelConfig
import com.tvbox.app.domain.AppSettings
import com.tvbox.app.domain.introSkipKey
import com.tvbox.app.domain.withIntroSkipOverride
import com.tvbox.app.domain.DeviceMode
import com.tvbox.app.domain.AiProviders
import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.AppUpdate
import com.tvbox.app.domain.Category
import com.tvbox.app.domain.CustomVideoApiLine
import com.tvbox.app.domain.DoubanHotItem
import com.tvbox.app.domain.LiveChannel
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.PlaybackAgent
import com.tvbox.app.domain.PlaybackAgentDecision
import com.tvbox.app.domain.PlaybackHealthSnapshot
import com.tvbox.app.domain.PlaybackIssueType
import com.tvbox.app.domain.PlatformLiveCategory
import com.tvbox.app.domain.PlatformLiveParentCategory
import com.tvbox.app.domain.PlatformLiveRoom
import com.tvbox.app.domain.PlatformLiveSite
import com.tvbox.app.domain.ResolvedPlatformLiveStream
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.domain.TvTheme
import com.tvbox.app.domain.TvFontScale
import com.tvbox.app.domain.findBestTitleMatchIndex
import com.tvbox.app.domain.playbackHealthKey
import com.tvbox.app.domain.resumeEpisodeIndex
import com.tvbox.app.domain.toApiLines
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class TvScreen {
    Home,
    History,
    Search,
    Detail,
    Player,
    Live,
    PlatformLive,
    Settings,
    AiRecommend,
}

enum class HomeFeedMode {
    DoubanHot,
    Catalog,
}

enum class PlatformLiveDestination {
    Sites,
    Favorites,
    ParentCategories,
    Categories,
    Rooms,
    Player,
}

data class AiRecommendationUiItem(
    val recommendation: AiRecommendationItem,
)

data class HistoryResumeState(
    val item: WatchHistoryItem,
    val searching: Boolean = true,
    val recoveryEnabled: Boolean = true,
    val failedUrls: Set<String> = emptySet(),
    val failurePositionMs: Long? = null,
)

data class TvBoxUiState(
    val screen: TvScreen = TvScreen.Home,
    val apiLines: List<ApiLine> = emptyList(),
    val selectedApiLineId: String = "",
    val homeSourcePicker: HomeSourcePickerState? = null,
    val categories: List<Category> = emptyList(),
    val homeFeedMode: HomeFeedMode = HomeFeedMode.DoubanHot,
    val selectedParentCategoryId: Int? = null,
    val selectedCategoryId: Int? = null,
    val movies: List<Movie> = emptyList(),
    val doubanHotMovies: List<DoubanHotItem> = emptyList(),
    val doubanHotFallbackActive: Boolean = false,
    val homeNotice: String? = null,
    val resolvingDoubanHotId: String? = null,
    val page: Int = 1,
    val pageCount: Int = 1,
    val total: Int = 0,
    val homeLoading: Boolean = false,
    val loadingMore: Boolean = false,
    val homeError: String? = null,
    val homeLoadMoreFailed: Boolean = false,
    val historyItems: List<WatchHistoryItem> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Movie> = emptyList(),
    val searchLoading: Boolean = false,
    val searchError: String? = null,
    val searchCompletedSources: Int = 0,
    val searchTotalSources: Int = 0,
    val searchFailedSources: Int = 0,
    val detailMovie: Movie? = null,
    val historyResume: HistoryResumeState? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val detailCompletedSources: Int = 0,
    val detailTotalSources: Int = 0,
    val detailReturnScreen: TvScreen = TvScreen.Home,
    val selectedSourceIndex: Int = 0,
    val selectedEpisodeIndex: Int = 0,
    val playerSourceIndex: Int = 0,
    val playerEpisodeIndex: Int = 0,
    val playerStartPositionMs: Long = 0L,
    val playerCanSkipIntro: Boolean = false,
    val phoneSearchDialogVisible: Boolean = false,
    val phoneSearchUrl: String? = null,
    val phoneSearchError: String? = null,
    val phoneSearchCompletedNonce: Int = 0,
    val playerSpeed: Float = 1f,
    val playerPlayWhenReady: Boolean = true,
    val liveChannels: List<LiveChannel> = emptyList(),
    val liveChannelIndex: Int = 0,
    val liveLineIndex: Int = 0,
    val liveLoading: Boolean = false,
    val liveError: String? = null,
    val platformLiveDestination: PlatformLiveDestination = PlatformLiveDestination.Sites,
    val platformLiveSites: List<PlatformLiveSite> = emptyList(),
    val platformLiveFavorites: List<PlatformLiveRoom> = emptyList(),
    val platformLiveFavoriteError: String? = null,
    val platformLivePlayerReturnDestination: PlatformLiveDestination = PlatformLiveDestination.Rooms,
    val platformLiveSelectedSite: PlatformLiveSite? = null,
    val platformLiveParentCategories: List<PlatformLiveParentCategory> = emptyList(),
    val platformLiveSelectedParentCategory: PlatformLiveParentCategory? = null,
    val platformLiveCategories: List<PlatformLiveCategory> = emptyList(),
    val platformLiveSelectedCategory: PlatformLiveCategory? = null,
    val platformLiveRooms: List<PlatformLiveRoom> = emptyList(),
    val platformLiveRoomQuery: String = "",
    val platformLiveRoomIndex: Int = 0,
    val platformLiveRoomPage: Int = 1,
    val platformLiveRoomPageCount: Int = 1,
    val platformLiveLoading: Boolean = false,
    val platformLiveLoadingMore: Boolean = false,
    val platformLiveLoadMoreFailed: Boolean = false,
    val platformLiveResolving: Boolean = false,
    val platformLiveError: String? = null,
    val platformLiveStream: ResolvedPlatformLiveStream? = null,
    val availableUpdate: AppUpdate? = null,
    val updateDialogVisible: Boolean = false,
    val updateChecking: Boolean = false,
    val updateDownloading: Boolean = false,
    val updateDownloadProgress: Int? = null,
    val updateDownloadedApkPath: String? = null,
    val updateAutoInstallPath: String? = null,
    val updateAwaitingInstallPermission: Boolean = false,
    val updateExporting: Boolean = false,
    val updateExportMessage: String? = null,
    val updateError: String? = null,
    val appSettings: AppSettings = AppSettings(),
    val playbackHealth: PlaybackHealthSnapshot = PlaybackHealthSnapshot(),
    val aiQuery: String = "",
    val aiTitle: String = "推荐",
    val aiResults: List<AiRecommendationUiItem> = emptyList(),
    val aiLoading: Boolean = false,
    val aiVoiceListening: Boolean = false,
    val aiError: String? = null,
    val aiResolvingKeyword: String? = null,
    val aiConfigDialogVisible: Boolean = false,
    val aiConfigServerUrl: String? = null,
    val aiConfigServerError: String? = null,
    val aiConfigSavedMessage: String? = null,
    val videoApiConfigDialogVisible: Boolean = false,
    val videoApiConfigServerUrl: String? = null,
    val videoApiConfigServerError: String? = null,
    val videoApiConfigSavedMessage: String? = null,
) {
    val canLoadMore: Boolean
        get() = page < pageCount && homeError == null && !homeLoading && !loadingMore

    val isShowingDoubanHot: Boolean
        get() = homeFeedMode == HomeFeedMode.DoubanHot && !doubanHotFallbackActive

    val homeItemCount: Int
        get() = if (isShowingDoubanHot) doubanHotMovies.size else movies.size

    val selectedApiLine: ApiLine?
        get() = apiLines.firstOrNull { it.id == selectedApiLineId }

    val visiblePlatformLiveRooms: List<PlatformLiveRoom>
        get() = if (platformLiveRoomQuery.isBlank()) platformLiveRooms else platformLiveRooms.filter { room ->
            room.anchor.contains(platformLiveRoomQuery, ignoreCase = true) ||
                room.title.contains(platformLiveRoomQuery, ignoreCase = true)
        }

    val preferredHomeApiLine: ApiLine?
        get() = apiLines.firstOrNull { it.id == appSettings.homeApiLineId }

    internal val showCinemaNavigation: Boolean
        get() = appSettings.theme == TvTheme.Cinema && when (screen) {
            TvScreen.Live, TvScreen.Player, TvScreen.PlatformLive -> false
            else -> true
        }
}

class TvBoxViewModel(
    private val repository: MovieRepository = DefaultMovieRepository(),
    private val doubanHotRepository: DoubanHotRepository = DefaultDoubanHotRepository(),
    private val liveRepository: LiveRepository = DefaultLiveRepository(),
    private val platformLiveRepository: PlatformLiveRepository = DefaultPlatformLiveRepository(),
    private val aiRecommendationRepository: AiRecommendationRepository = DefaultAiRecommendationRepository(BuildConfig.AI_API_KEY),
    private val appUpdateRepository: AppUpdateRepository = DefaultAppUpdateRepositoryPlaceholder(),
    private val appSettingsRepository: AppSettingsRepository = DefaultAppSettingsRepositoryPlaceholder(),
    private val videoSourceCatalogRepository: VideoSourceCatalogRepository? = null,
    private val playbackHealthRepository: PlaybackHealthRepository = DefaultPlaybackHealthRepositoryPlaceholder(),
    private val historyRepository: HistoryRepository,
    private val platformLiveFavoritesRepository: PlatformLiveFavoritesRepository,
    private val platformLiveServiceUrl: String = BuildConfig.PLATFORM_LIVE_SERVICE_URL,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val _state = MutableStateFlow(TvBoxUiState())
    val state: StateFlow<TvBoxUiState> = _state.asStateFlow()

    private var homeJob: Job? = null
    private var homeSourceProbeJob: Job? = null
    private var videoSourceSyncJob: Job? = null
    private var videoSourceSettingsLoaded = false
    private var pendingDefaultVideoSources: List<ApiLine>? = null
    private var doubanHotResolveJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var historyResumeJob: Job? = null
    private var retryDetailRequest: (() -> Unit)? = null
    private var liveJob: Job? = null
    private var platformLiveJob: Job? = null
    private var platformLiveResolveJob: Job? = null
    private var platformLiveRecoveryCount = 0
    private var platformLiveRecoveryInProgress = false
    private val platformLiveFavoritesMutex = Mutex()
    private val historyWriteMutex = Mutex()
    private var aiJob: Job? = null
    private var updateJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var updateInstallStateJob: Job? = null
    private var settingsWriteJob: Job? = null
    private var activeAiConfigServer: PhoneFormServer? = null
    private var activeVideoConfigServer: PhoneFormServer? = null
    private var phoneSearchServer: PhoneSearchServer? = null
    private var phoneSearchStartJob: Job? = null
    private var phoneSearchGeneration = 0L
    private val playbackAgent = PlaybackAgent()

    init {
        restorePendingUpdateDownload()
        val defaultApiLineId = repository.apiLines.firstOrNull()?.id.orEmpty()
        _state.update {
            it.copy(
                apiLines = repository.apiLines,
                selectedApiLineId = defaultApiLineId,
            )
        }
        loadPlatformLiveFavorites()
        viewModelScope.launch {
            val settings = runCatching { appSettingsRepository.getSettings() }
                .getOrDefault(AppSettings())
            videoSourceCatalogRepository?.loadCachedSources()?.let(repository::replaceDefaultApiLines)
            repository.updateCustomApiLines(settings.customVideoApiLines.toApiLines())
            val availableApiLines = repository.apiLines
            val selectedApiLineId = settings.homeApiLineId
                .takeIf { apiLineId -> availableApiLines.any { it.id == apiLineId } }
                ?: availableApiLines.firstOrNull()?.id.orEmpty()
            val playbackHealth = runCatching { playbackHealthRepository.getSnapshot() }
                .getOrDefault(PlaybackHealthSnapshot())
            _state.update {
                it.copy(
                    apiLines = availableApiLines,
                    selectedApiLineId = selectedApiLineId,
                    appSettings = settings,
                    playbackHealth = playbackHealth,
                )
            }
            loadHistory()
            videoSourceSettingsLoaded = true
            syncVideoSources()
            if (settings.homeSourcePromptHandled) {
                refreshHome()
            } else {
                _state.update { it.copy(homeSourcePicker = HomeSourcePickerState(onboarding = true)) }
                startHomeSourceProbe()
            }
            if (settings.checkUpdatesOnStartup) {
                checkForAppUpdate()
            }
        }
    }

    fun syncVideoSources() {
        val catalog = videoSourceCatalogRepository ?: return
        if (!videoSourceSettingsLoaded || videoSourceSyncJob?.isActive == true) return
        videoSourceSyncJob = viewModelScope.launch {
            try {
                catalog.refreshSources()?.let { pendingDefaultVideoSources = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A network or verification failure leaves the active list intact.
            }
        }
    }

    // Apply only at navigation or refresh boundaries so a scan or playback keeps its source set.
    private fun applyPendingVideoSources(): Boolean {
        val pending = pendingDefaultVideoSources ?: return false
        val state = _state.value
        if (state.homeSourcePicker != null) return false
        pendingDefaultVideoSources = null
        repository.replaceDefaultApiLines(pending)
        val lines = repository.apiLines
        if (lines == state.apiLines) return false
        val active = lines.firstOrNull { it.id == state.selectedApiLineId }
            ?: lines.firstOrNull { it.id == state.appSettings.homeApiLineId }
            ?: lines.firstOrNull()
        val homeChanged = active != state.selectedApiLine
        if (homeChanged) homeJob?.cancel()
        _state.update {
            if (homeChanged) {
                it.copy(
                    apiLines = lines,
                    selectedApiLineId = active?.id.orEmpty(),
                    selectedParentCategoryId = null,
                    selectedCategoryId = null,
                    categories = emptyList(),
                    movies = emptyList(),
                    doubanHotMovies = emptyList(),
                    page = 1,
                    pageCount = 1,
                    total = 0,
                    homeError = null,
                    homeNotice = null,
                )
            } else it.copy(apiLines = lines)
        }
        return homeChanged
    }

    fun refreshHome(forceRefresh: Boolean = false) {
        if (_state.value.screen == TvScreen.Home) applyPendingVideoSources()
        _state.update { state ->
            if (state.homeFeedMode == HomeFeedMode.DoubanHot) {
                state.copy(doubanHotFallbackActive = false, homeNotice = null)
            } else {
                state
            }
        }
        loadHomePage(reset = true, forceRefresh = forceRefresh)
    }

    fun selectDoubanHot() {
        val current = _state.value
        if (current.isShowingDoubanHot && current.doubanHotMovies.isNotEmpty()) return
        _state.update {
            it.copy(
                homeFeedMode = HomeFeedMode.DoubanHot,
                selectedParentCategoryId = null,
                selectedCategoryId = null,
                movies = emptyList(),
                doubanHotMovies = emptyList(),
                doubanHotFallbackActive = false,
                homeNotice = null,
                page = 1,
                pageCount = 1,
                total = 0,
            )
        }
        loadHomePage(reset = true)
    }

    fun selectParentCategory(parentCategoryId: Int) {
        val current = _state.value
        if (current.selectedParentCategoryId == parentCategoryId && current.selectedCategoryId == null) return
        _state.update {
            it.copy(
                homeFeedMode = HomeFeedMode.Catalog,
                selectedParentCategoryId = parentCategoryId,
                selectedCategoryId = null,
                movies = emptyList(),
                doubanHotFallbackActive = false,
                homeNotice = null,
                page = 1,
                pageCount = 1,
                total = 0,
            )
        }
        loadHomePage(reset = true)
    }

    fun selectChildCategory(categoryId: Int) {
        val current = _state.value
        if (current.selectedCategoryId == categoryId) return
        val parentCategoryId = current.categories
            .firstOrNull { it.id == categoryId }
            ?.parentId
            ?.takeIf { it > 0 }
            ?: current.selectedParentCategoryId
        _state.update {
            it.copy(
                homeFeedMode = HomeFeedMode.Catalog,
                selectedParentCategoryId = parentCategoryId,
                selectedCategoryId = categoryId,
                movies = emptyList(),
                doubanHotFallbackActive = false,
                homeNotice = null,
                page = 1,
                pageCount = 1,
                total = 0,
            )
        }
        loadHomePage(reset = true)
    }

    fun selectApiLine(apiLineId: String) {
        updateHomeApiLine(apiLineId)
    }

    fun openHomeSourcePicker() {
        if (_state.value.homeSourcePicker != null) return
        if (applyPendingVideoSources()) refreshHome()
        _state.update { it.copy(homeSourcePicker = HomeSourcePickerState(onboarding = false)) }
    }

    fun startHomeSourceProbe() {
        val picker = _state.value.homeSourcePicker ?: return
        if (picker.testing) return
        val lines = _state.value.apiLines.distinctBy { it.id }
        _state.update {
            it.copy(homeSourcePicker = picker.copy(testing = true, tested = false, probes = emptyMap()))
        }
        homeSourceProbeJob = viewModelScope.launch {
            repository.probeHomeSources(lines).collect { result ->
                currentCoroutineContext().ensureActive()
                _state.update { state ->
                    val active = state.homeSourcePicker ?: return@update state
                    state.copy(homeSourcePicker = active.copy(probes = active.probes + (result.apiLineId to result)))
                }
            }
            currentCoroutineContext().ensureActive()
            _state.update { state ->
                state.copy(homeSourcePicker = state.homeSourcePicker?.copy(testing = false, tested = true))
            }
        }
    }

    fun dismissHomeSourcePicker() = finishHomeSourcePicker(null)

    fun selectHomeSource(apiLineId: String) {
        val current = _state.value
        if (current.homeSourcePicker?.testing != false || current.apiLines.none { it.id == apiLineId }) return
        finishHomeSourcePicker(apiLineId)
    }

    private fun finishHomeSourcePicker(apiLineId: String?) {
        val picker = _state.value.homeSourcePicker ?: return
        homeSourceProbeJob?.cancel()
        val current = _state.value
        val nextSettings = current.appSettings.copy(
            homeSourcePromptHandled = current.appSettings.homeSourcePromptHandled || picker.onboarding,
            homeApiLineId = apiLineId ?: current.appSettings.homeApiLineId,
        )
        if (nextSettings != current.appSettings) saveSettings(nextSettings)
        _state.update { it.copy(homeSourcePicker = null, appSettings = nextSettings) }
        if (apiLineId != null) {
            updateHomeApiLine(
                apiLineId,
                forceRefresh = picker.onboarding || apiLineId != current.appSettings.homeApiLineId,
            )
        } else if (picker.onboarding) {
            refreshHome()
        }
    }

    fun updateHomeApiLine(apiLineId: String, forceRefresh: Boolean = false) {
        val current = _state.value
        if (current.apiLines.none { it.id == apiLineId }) return
        if (!forceRefresh && current.appSettings.homeApiLineId == apiLineId && current.selectedApiLineId == apiLineId &&
            current.homeError == null
        ) return
        val settings = current.appSettings.copy(homeApiLineId = apiLineId)
        if (settings != current.appSettings) saveSettings(settings)
        _state.update {
            it.copy(
                appSettings = settings,
                selectedApiLineId = apiLineId,
                selectedParentCategoryId = null,
                selectedCategoryId = null,
                categories = emptyList(),
                movies = emptyList(),
                doubanHotMovies = emptyList(),
                doubanHotFallbackActive = false,
                homeNotice = null,
                page = 1,
                pageCount = 1,
                total = 0,
                homeError = null,
            )
        }
        refreshHome()
    }

    fun loadNextPage() {
        val current = _state.value
        if (!current.canLoadMore) return
        loadHomePage(reset = false, forceRefresh = current.homeLoadMoreFailed || current.homeNotice != null)
    }

    fun openHome() {
        _state.update {
            it.copy(
                screen = TvScreen.Home,
                detailReturnScreen = TvScreen.Home,
                platformLiveDestination = PlatformLiveDestination.Sites,
                platformLiveStream = null,
            )
        }
        if (applyPendingVideoSources()) refreshHome()
    }

    fun openSearch() {
        _state.update { it.copy(screen = TvScreen.Search, searchError = null) }
    }

    fun openAiRecommend() {
        _state.update { it.copy(screen = TvScreen.AiRecommend, aiVoiceListening = false, aiError = null) }
    }

    fun openLive() {
        _state.update { it.copy(screen = TvScreen.Live, liveError = null) }
        if (_state.value.liveChannels.isEmpty()) {
            loadLiveChannels()
        }
    }

    fun openPlatformLive() {
        _state.update {
            it.copy(
                screen = TvScreen.PlatformLive,
                platformLiveDestination = PlatformLiveDestination.Sites,
                platformLiveError = null,
                platformLiveStream = null,
            )
        }
        if (platformLiveServiceUrl.isBlank()) {
            _state.update {
                it.copy(platformLiveError = "测试服务未配置，请使用 TVBOX_PLATFORM_LIVE_SERVICE_URL 构建 APK")
            }
        } else if (_state.value.platformLiveSites.isEmpty()) {
            loadPlatformLiveSites()
        } else {
            returnToPlatformLiveBrowse()
        }
    }

    fun openSettings() {
        _state.update { it.copy(screen = TvScreen.Settings, updateError = null) }
        refreshPlaybackHealth()
    }

    fun refreshLive() {
        loadLiveChannels()
    }

    fun refreshPlatformLive() {
        val current = _state.value
        when (current.platformLiveDestination) {
            PlatformLiveDestination.Sites -> loadPlatformLiveSites()
            PlatformLiveDestination.Favorites -> loadPlatformLiveFavorites()
            PlatformLiveDestination.ParentCategories, PlatformLiveDestination.Categories -> {
                current.platformLiveSelectedSite?.let(::loadPlatformLiveCategories)
            }
            PlatformLiveDestination.Rooms -> _state.value.platformLiveSelectedCategory?.let {
                loadPlatformLiveRooms(it, reset = true)
            }
            PlatformLiveDestination.Player -> resolvePlatformLiveRoom(forceRefresh = true)
        }
    }

    fun checkForAppUpdate(showError: Boolean = false) {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            _state.update { it.copy(updateChecking = true, updateError = null) }
            runCatching { appUpdateRepository.checkForUpdate(BuildConfig.VERSION_CODE.toLong()) }
                .onSuccess { update ->
                    _state.update {
                        if (update == null) {
                            it.copy(updateChecking = false)
                        } else if (it.updateDownloading || it.updateDownloadedApkPath != null) {
                            it.copy(updateChecking = false)
                        } else {
                            it.copy(
                                availableUpdate = update,
                                updateDialogVisible = true,
                                updateChecking = false,
                                updateDownloading = false,
                                updateDownloadProgress = null,
                                updateDownloadedApkPath = null,
                                updateError = null,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            updateChecking = false,
                            updateDialogVisible = showError && it.availableUpdate != null,
                            updateError = if (showError) error.userMessage() else null,
                        )
                    }
                }
        }
    }

    fun dismissUpdateDialog() {
        if (_state.value.availableUpdate?.force == true) return
        _state.update { it.copy(updateDialogVisible = false, updateError = null) }
    }

    fun startUpdateDownload() {
        val update = _state.value.availableUpdate ?: return
        if (_state.value.updateDownloading) return
        updateDownloadJob?.cancel()
        _state.update {
            it.copy(updateDialogVisible = false, updateDownloading = true,
                updateDownloadProgress = 0, updateDownloadedApkPath = null,
                updateAutoInstallPath = null, updateAwaitingInstallPermission = false, updateError = null,
                updateExporting = false, updateExportMessage = null)
        }
        updateDownloadJob = viewModelScope.launch {
            try {
                updateInstallStateJob?.join()
                val apkFile = appUpdateRepository.downloadUpdate(update) { progress ->
                    _state.update { it.copy(updateDownloadProgress = progress.takeIf { value -> value >= 0 }) }
                }
                val autoInstall = appUpdateRepository.shouldAutoInstall(apkFile.absolutePath)
                val awaitingPermission = appUpdateRepository.isAwaitingInstallPermission(apkFile.absolutePath)
                _state.update {
                    it.copy(updateDownloading = false, updateDownloadProgress = 100,
                        updateDownloadedApkPath = apkFile.absolutePath,
                        updateAwaitingInstallPermission = awaitingPermission,
                        updateAutoInstallPath = apkFile.absolutePath.takeIf { autoInstall }, updateError = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _state.update {
                    it.copy(updateDownloading = false, updateDownloadProgress = null,
                        updateDownloadedApkPath = null, updateAutoInstallPath = null,
                        updateAwaitingInstallPermission = false, updateError = error.userMessage())
                }
            }
        }
    }

    private fun restorePendingUpdateDownload() {
        viewModelScope.launch {
            val pending = runCatching { appUpdateRepository.pendingDownload() }.getOrNull() ?: return@launch
            if (_state.value.updateDownloading) return@launch
            _state.update { it.copy(availableUpdate = pending, updateDialogVisible = false) }
            startUpdateDownload()
        }
    }

    fun markUpdateInstallPrompted(apkPath: String) {
        _state.update { it.copy(updateAutoInstallPath = null, updateDialogVisible = false,
            updateAwaitingInstallPermission = false, updateError = null) }
        persistUpdateInstallState { appUpdateRepository.markInstallPrompted(apkPath) }
    }

    fun beginUpdateInstallAttempt(apkPath: String): Boolean {
        if (_state.value.updateDownloadedApkPath != apkPath || _state.value.updateExporting) return false
        // Consume only the UI event, not the persistent successful-launch marker.
        _state.update { it.copy(updateAutoInstallPath = null, updateDialogVisible = false,
            updateAwaitingInstallPermission = false, updateError = null) }
        return true
    }

    fun awaitUpdateInstallPermission(apkPath: String, message: String? = null) {
        if (_state.value.updateDownloadedApkPath != apkPath) return
        _state.update { it.copy(updateAutoInstallPath = null, updateAwaitingInstallPermission = true,
            updateError = message) }
        persistUpdateInstallState { appUpdateRepository.setAwaitingInstallPermission(apkPath, true) }
    }

    fun resumeUpdateInstallAfterPermission(apkPath: String) {
        _state.update {
            if (it.updateDownloadedApkPath == apkPath && it.updateAwaitingInstallPermission)
                it.copy(updateAutoInstallPath = apkPath, updateError = null) else it
        }
    }

    fun reportUpdateInstallFailure(apkPath: String, message: String, fileMissing: Boolean = false) {
        _state.update { it.copy(updateAutoInstallPath = null, updateAwaitingInstallPermission = false,
            updateDownloadedApkPath = if (fileMissing) null else it.updateDownloadedApkPath, updateError = message) }
        persistUpdateInstallState {
            if (fileMissing) appUpdateRepository.forgetDownload(apkPath)
            else appUpdateRepository.setAwaitingInstallPermission(apkPath, false)
        }
    }

    fun beginUpdateApkExport(apkPath: String): Boolean {
        if (_state.value.updateDownloadedApkPath != apkPath || _state.value.updateExporting) return false
        _state.update { it.copy(updateAutoInstallPath = null, updateAwaitingInstallPermission = false,
            updateExporting = true, updateExportMessage = null, updateError = null) }
        persistUpdateInstallState { appUpdateRepository.chooseManualInstall(apkPath) }
        return true
    }

    fun cancelUpdateApkExport(message: String = "已取消保存，安装包仍保留") {
        _state.update { it.copy(updateExporting = false, updateExportMessage = message) }
    }

    fun saveUpdateApk(apkPath: String, destination: android.net.Uri?) {
        _state.update { it.copy(updateExporting = true, updateExportMessage = null) }
        viewModelScope.launch {
            try {
                updateInstallStateJob?.join()
                val location = appUpdateRepository.exportUpdateApk(apkPath, destination)
                _state.update { it.copy(updateExporting = false,
                    updateExportMessage = "已保存：$location。请通过文件管理器打开安装") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("TVBoxUpdate", "Unable to export update APK", error)
                _state.update { it.copy(updateExporting = false, updateExportMessage = "保存失败：${error.userMessage()}") }
            }
        }
    }

    private fun persistUpdateInstallState(write: suspend () -> Unit) {
        val previous = updateInstallStateJob
        updateInstallStateJob = viewModelScope.launch {
            previous?.join()
            try { write() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("TVBoxUpdate", "Unable to persist install state", error) }
        }
    }

    fun openHistory() {
        loadHistory()
        _state.update { it.copy(screen = TvScreen.History) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching { historyRepository.clearHistory() }
                .onSuccess { history ->
                    _state.update { it.copy(historyItems = history) }
                }
        }
    }

    fun updateStartupUpdateCheck(enabled: Boolean) {
        val settings = _state.value.appSettings.copy(checkUpdatesOnStartup = enabled)
        saveSettings(settings)
        _state.update { it.copy(appSettings = settings) }
    }

    fun updatePlaybackAgentAutoSwitch(enabled: Boolean) {
        val settings = _state.value.appSettings.copy(playbackAgentAutoSwitchEnabled = enabled)
        saveSettings(settings)
        _state.update { it.copy(appSettings = settings) }
    }

    fun updateVideoScaleMode(mode: com.tvbox.app.domain.VideoScaleMode) {
        val settings = _state.value.appSettings.copy(videoScaleMode = mode)
        saveSettings(settings)
        _state.update { it.copy(appSettings = settings) }
    }

    fun updateAiProvider(providerId: String) {
        val provider = AiProviders.find(providerId)
        val current = _state.value.appSettings
        if (current.aiProviderId == provider.id) return
        closeAiConfigDialog()
        val settings = current.copy(aiProviderId = provider.id)
        saveSettings(settings)
        _state.update { it.copy(appSettings = settings, aiError = null) }
    }

    fun openAiConfigDialog() {
        closeAiConfigDialog(stopOnly = true)
        val current = _state.value.appSettings
        val provider = AiProviders.find(current.aiProviderId)
        lateinit var server: PhoneFormServer
        suspend fun acceptConfig(update: (AppSettings) -> AppSettings): PhoneFormSubmission =
            withContext(Dispatchers.Main.immediate) {
                val state = _state.value
                if (activeAiConfigServer !== server || !server.isSessionActive ||
                    state.screen != TvScreen.Settings || AiProviders.find(state.appSettings.aiProviderId) != provider
                ) {
                    PhoneFormSubmission.Inactive("电视已离开此配置会话，请重新扫码。")
                } else {
                    val settings = update(state.appSettings)
                    saveSettings(settings)
                    _state.update {
                        it.copy(
                            appSettings = settings,
                            aiError = null,
                            aiConfigServerUrl = null,
                            aiConfigServerError = null,
                            aiConfigSavedMessage = "大模型配置已保存。",
                        )
                    }
                    PhoneFormSubmission.Accepted
                }
            }
        val onExpired: suspend () -> Unit = {
            withContext(Dispatchers.Main.immediate) {
                if (activeAiConfigServer === server && _state.value.aiConfigSavedMessage == null) {
                    _state.update {
                        it.copy(aiConfigServerUrl = null, aiConfigServerError = "二维码已过期，请重新生成。")
                    }
                }
            }
        }
        server = if (provider.isCustom) {
            customAiConfigServer(
                scope = viewModelScope,
                saved = current.customAiConfig,
                onSubmit = { config -> acceptConfig { it.copy(customAiConfig = config) } },
                onExpired = onExpired,
            )
        } else {
            aiConfigServer(
                scope = viewModelScope,
                provider = provider,
                modelName = current.aiModelConfigs[provider.id]?.modelId.orEmpty().ifBlank { provider.defaultModel },
                onSubmit = { model, apiKey ->
                    acceptConfig {
                        it.copy(aiModelConfigs = it.aiModelConfigs + (provider.id to AiModelConfig(model, apiKey)))
                    }
                },
                onExpired = onExpired,
            )
        }
        activeAiConfigServer = server
        _state.update {
            it.copy(
                aiConfigDialogVisible = true,
                aiConfigServerUrl = null,
                aiConfigServerError = null,
                aiConfigSavedMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { server.start() } }.onSuccess { url ->
                if (activeAiConfigServer === server && server.isSessionActive) {
                    _state.update {
                        if (it.aiConfigSavedMessage != null) it
                        else it.copy(aiConfigServerUrl = url, aiConfigServerError = null)
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (activeAiConfigServer === server) {
                    _state.update {
                        it.copy(aiConfigServerUrl = null, aiConfigServerError = "启动配置服务失败：${error.userMessage()}")
                    }
                }
            }
        }
    }

    fun closeAiConfigDialog() {
        closeAiConfigDialog(stopOnly = false)
    }

    fun openVideoApiConfigDialog() {
        closeVideoApiConfigDialog(stopOnly = true)
        lateinit var server: PhoneFormServer
        server = videoApiConfigServer(
            scope = viewModelScope,
            onSubmit = { submittedName, submittedUrl ->
                withContext(Dispatchers.Main.immediate) {
                    if (activeVideoConfigServer !== server || !server.isSessionActive ||
                        _state.value.screen != TvScreen.Settings
                    ) {
                        PhoneFormSubmission.Inactive("电视已离开此配置会话，请重新扫码。")
                    } else {
                        applyCustomVideoApiFromPhone(submittedName, submittedUrl)?.let {
                            PhoneFormSubmission.Invalid(it)
                        } ?: PhoneFormSubmission.Accepted
                    }
                }
            },
            onExpired = {
                withContext(Dispatchers.Main.immediate) {
                    if (activeVideoConfigServer === server && _state.value.videoApiConfigSavedMessage == null) {
                        _state.update {
                            it.copy(videoApiConfigServerUrl = null, videoApiConfigServerError = "二维码已过期，请重新生成。")
                        }
                    }
                }
            },
        )
        activeVideoConfigServer = server
        _state.update {
            it.copy(
                videoApiConfigDialogVisible = true,
                videoApiConfigServerUrl = null,
                videoApiConfigServerError = null,
                videoApiConfigSavedMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { server.start() } }
                .onSuccess { url ->
                    if (activeVideoConfigServer === server && server.isSessionActive) {
                        _state.update {
                            if (it.videoApiConfigSavedMessage != null) it
                            else it.copy(videoApiConfigServerUrl = url, videoApiConfigServerError = null)
                        }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (activeVideoConfigServer === server) {
                        _state.update {
                            it.copy(videoApiConfigServerUrl = null, videoApiConfigServerError = "启动配置服务失败：${error.userMessage()}")
                        }
                    }
                }
        }
    }

    fun closeVideoApiConfigDialog() {
        closeVideoApiConfigDialog(stopOnly = false)
    }

    private fun closeAiConfigDialog(stopOnly: Boolean) {
        activeAiConfigServer?.stop()
        activeAiConfigServer = null
        if (!stopOnly) {
            _state.update {
                it.copy(
                    aiConfigDialogVisible = false,
                    aiConfigServerUrl = null,
                    aiConfigServerError = null,
                    aiConfigSavedMessage = null,
                )
            }
        }
    }

    private fun closeVideoApiConfigDialog(stopOnly: Boolean) {
        activeVideoConfigServer?.stop()
        activeVideoConfigServer = null
        if (!stopOnly) {
            _state.update {
                it.copy(
                    videoApiConfigDialogVisible = false,
                    videoApiConfigServerUrl = null,
                    videoApiConfigServerError = null,
                    videoApiConfigSavedMessage = null,
                )
            }
        }
    }

    private fun applyCustomVideoApiFromPhone(lineName: String, apiUrl: String): String? {
        val normalizedName = lineName.trim()
        val normalizedUrl = normalizeMacCmsApiUrl(apiUrl)
            ?: return "请输入 http:// 或 https:// 开头的 MacCms 接口地址。"
        if (normalizedName.isBlank()) {
            return "接口名称需要填写。"
        }

        val customLine = CustomVideoApiLine(
            id = customVideoApiLineId(normalizedUrl),
            name = normalizedName,
            baseUrl = normalizedUrl,
        )
        val currentSettings = _state.value.appSettings
        val customLines = (currentSettings.customVideoApiLines
            .filterNot { it.id == customLine.id || it.baseUrl.equals(customLine.baseUrl, ignoreCase = true) } +
            customLine)
        val settings = currentSettings.copy(
            homeApiLineId = customLine.id,
            customVideoApiLines = customLines,
        )
        saveSettings(settings)
        repository.updateCustomApiLines(settings.customVideoApiLines.toApiLines())
        _state.update {
            it.copy(
                apiLines = repository.apiLines,
                selectedApiLineId = customLine.id,
                appSettings = settings,
                selectedParentCategoryId = null,
                selectedCategoryId = null,
                categories = emptyList(),
                movies = emptyList(),
                page = 1,
                pageCount = 1,
                total = 0,
                homeError = null,
                videoApiConfigServerUrl = null,
                videoApiConfigServerError = null,
                videoApiConfigSavedMessage = "已添加视频接口：${customLine.name}",
            )
        }
        refreshHome()
        return null
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun openPhoneSearchDialog() {
        closePhoneSearchDialog()
        if (_state.value.screen != TvScreen.Search) return
        val generation = phoneSearchGeneration
        _state.update { it.copy(phoneSearchDialogVisible = true) }
        val server = PhoneSearchServer(viewModelScope,
            onSubmit = { query ->
                withContext(Dispatchers.Main.immediate) {
                    if (generation != phoneSearchGeneration || !_state.value.phoneSearchDialogVisible ||
                        _state.value.screen != TvScreen.Search || phoneSearchServer?.isSessionActive != true)
                        "扫码窗口已关闭或过期，请重新扫码。"
                    else { updateSearchQuery(query); submitSearch(); null }
                }
            },
            onDelivered = {
                withContext(Dispatchers.Main.immediate) {
                    if (generation == phoneSearchGeneration) {
                        _state.update { it.copy(phoneSearchCompletedNonce = it.phoneSearchCompletedNonce + 1) }
                        closePhoneSearchDialog()
                    }
                }
            },
            onExpired = {
                withContext(Dispatchers.Main.immediate) {
                    if (generation == phoneSearchGeneration) _state.update {
                        it.copy(phoneSearchUrl = null, phoneSearchError = "二维码已过期，请重新生成。")
                    }
                }
            },
        )
        phoneSearchServer = server
        phoneSearchStartJob = viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) { server.start() }
                if (generation != phoneSearchGeneration) server.stop()
                else _state.update { it.copy(phoneSearchUrl = session.url) }
            } catch (cancelled: CancellationException) {
                server.stop()
                throw cancelled
            } catch (error: Exception) {
                server.stop()
                if (generation == phoneSearchGeneration) _state.update { it.copy(phoneSearchError = error.userMessage()) }
            }
        }
    }

    fun closePhoneSearchDialog() {
        phoneSearchGeneration++
        phoneSearchStartJob?.cancel()
        phoneSearchStartJob = null
        phoneSearchServer?.stop()
        phoneSearchServer = null
        _state.update { it.copy(phoneSearchDialogVisible = false, phoneSearchUrl = null, phoneSearchError = null) }
    }

    fun updateAiQuery(query: String) {
        _state.update { it.copy(aiQuery = query, aiVoiceListening = false, aiError = null) }
    }

    fun submitAiRecommendation() {
        submitAiRecommendation(query = _state.value.aiQuery)
    }

    fun submitAiRecommendation(query: String) {
        submitAiRecommendationInternal(query = query, refreshBatch = false)
    }

    fun refreshAiRecommendationBatch() {
        submitAiRecommendationInternal(query = _state.value.aiQuery, refreshBatch = true)
    }

    fun showAiMessage(message: String) {
        _state.update {
            it.copy(
                screen = TvScreen.AiRecommend,
                aiLoading = false,
                aiVoiceListening = false,
                aiResolvingKeyword = null,
                aiError = message,
            )
        }
    }

    fun startAiVoiceListening() {
        _state.update {
            it.copy(
                screen = TvScreen.AiRecommend,
                aiLoading = false,
                aiVoiceListening = true,
                aiResolvingKeyword = null,
                aiError = null,
            )
        }
    }

    private fun submitAiRecommendationInternal(query: String, refreshBatch: Boolean) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            _state.update { it.copy(aiVoiceListening = false, aiResults = emptyList(), aiError = "请输入找片需求") }
            return
        }
        val excludedNames = if (refreshBatch) {
            _state.value.aiResults.map { it.recommendation.name }
        } else {
            emptyList()
        }
        val requestQuery = buildAiRecommendationRequest(trimmedQuery, excludedNames)
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    aiQuery = trimmedQuery,
                    aiLoading = true,
                    aiVoiceListening = false,
                    aiError = null,
                    aiResults = emptyList(),
                    aiResolvingKeyword = null,
                    aiTitle = if (refreshBatch) "AI 正在换一批" else "AI 正在找片",
                )
            }
            runCatching {
                val recommendations = aiRecommendationRepository.getRecommendations(
                    query = requestQuery,
                    userConfig = _state.value.appSettings.userAiRequestConfigOrNull(),
                )
                recommendations.title to recommendations.items.map { AiRecommendationUiItem(recommendation = it) }
            }.onSuccess { (title, items) ->
                _state.update {
                    it.copy(
                        aiLoading = false,
                        aiVoiceListening = false,
                        aiTitle = title.ifBlank { "AI 推荐" },
                        aiResults = items,
                        aiError = if (items.isEmpty()) "AI 没有返回可展示的推荐" else null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        aiLoading = false,
                        aiVoiceListening = false,
                        aiResults = emptyList(),
                        aiResolvingKeyword = null,
                        aiError = error.userMessage(),
                    )
                }
            }
        }
    }

    fun openAiRecommendation(item: AiRecommendationItem) {
        val keyword = item.searchKeyword.ifBlank { item.name }.trim()
        if (keyword.isBlank()) {
            _state.update { it.copy(aiError = "暂无该视频资源") }
            return
        }
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    aiError = null,
                    aiResolvingKeyword = keyword,
                )
            }
            runCatching { findPlayableMovie(item) }
                .onSuccess { movie ->
                    if (movie == null) {
                        _state.update {
                            it.copy(
                                screen = TvScreen.AiRecommend,
                                aiResolvingKeyword = null,
                                aiError = "暂无该视频资源",
                            )
                        }
                    } else {
                        _state.update { it.copy(aiResolvingKeyword = null, aiError = null) }
                        openDetail(movie.id)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            screen = TvScreen.AiRecommend,
                            aiResolvingKeyword = null,
                            aiError = error.userMessage(),
                        )
                    }
                }
        }
    }

    fun openDoubanHotDetail(item: DoubanHotItem) {
        val current = _state.value
        if (current.resolvingDoubanHotId == item.doubanId) return

        val apiLineId = current.selectedApiLineId
        doubanHotResolveJob?.cancel()
        doubanHotResolveJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    homeNotice = null,
                    resolvingDoubanHotId = item.doubanId,
                )
            }
            runCatching { findPlayableDoubanMovie(item.title, apiLineId) }
                .onSuccess { movie ->
                    _state.update { it.copy(resolvingDoubanHotId = null) }
                    if (movie == null) {
                        _state.update { it.copy(homeNotice = "暂无该视频资源") }
                    } else {
                        openDetail(movie.id, apiLineId)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            resolvingDoubanHotId = null,
                            homeNotice = error.userMessage(),
                        )
                    }
                }
        }
    }

    fun submitSearch() = search(retryUnavailableSources = false)

    fun retrySearch() = search(retryUnavailableSources = true)

    private fun search(retryUnavailableSources: Boolean) {
        val query = _state.value.searchQuery.trim()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), searchError = "请输入搜索关键词") }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    searchLoading = true,
                    searchError = null,
                    searchResults = emptyList(),
                    searchCompletedSources = 0,
                    searchTotalSources = 0,
                    searchFailedSources = 0,
                )
            }
            try {
                repository.searchProgressively(
                    apiLineId = _state.value.let { it.preferredHomeApiLine?.id ?: it.selectedApiLineId },
                    page = 1,
                    keyword = query,
                    retryUnavailableSources = retryUnavailableSources,
                ).collect { update ->
                    val finished = update.completedSources == update.totalSources
                    val failedSources = update.completedSources - update.successfulSources
                    _state.update {
                        it.copy(
                            searchLoading = !finished,
                            searchResults = update.movies,
                            searchCompletedSources = update.completedSources,
                            searchTotalSources = update.totalSources,
                            searchFailedSources = failedSources,
                            searchError = when {
                                !finished || update.movies.isNotEmpty() -> null
                                update.successfulSources == 0 -> "暂时无法连接视频源，请重试"
                                failedSources > 0 -> "可用视频源未找到相关影片，部分视频源请求失败"
                                else -> "没有找到相关影片"
                            },
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _state.update {
                    it.copy(searchLoading = false, searchError = error.userMessage())
                }
            }
        }
    }

    fun openDetail(movieId: Int, apiLineId: String = _state.value.selectedApiLineId) {
        historyResumeJob?.cancel()
        detailJob?.cancel()
        retryDetailRequest = { openDetail(movieId, apiLineId) }
        val current = _state.value
        val returnScreen = when (current.screen) {
            TvScreen.AiRecommend -> TvScreen.AiRecommend
            TvScreen.Search -> TvScreen.Search
            TvScreen.History -> TvScreen.History
            TvScreen.Detail -> current.detailReturnScreen
            else -> TvScreen.Home
        }
        _state.update {
            it.copy(
                screen = TvScreen.Detail,
                detailReturnScreen = returnScreen,
                detailMovie = null,
                historyResume = null,
                detailLoading = true,
                detailError = null,
                detailCompletedSources = 0,
                detailTotalSources = 0,
                selectedSourceIndex = 0,
                selectedEpisodeIndex = 0,
                playerSourceIndex = 0,
                playerEpisodeIndex = 0,
                playerStartPositionMs = 0L,
            )
        }
        detailJob = viewModelScope.launch {
            var receivedPrimary = false
            try {
                repository.getDetailProgressively(apiLineId = apiLineId, id = movieId).collect { update ->
                    currentCoroutineContext().ensureActive()
                    if (receivedPrimary) {
                        _state.update { it.appendDetailSources(update) }
                    } else {
                        _state.update { state ->
                            val sourceIndex = resolveBestPlaybackSourceIndex(
                                state, update.movie, update.movie.preferredSourceIndex(), episodeIndex = 0,
                            )
                            state.copy(
                                detailMovie = update.movie,
                                detailLoading = false,
                                detailError = null,
                                detailCompletedSources = update.completedSources,
                                detailTotalSources = update.totalSources,
                                selectedSourceIndex = sourceIndex,
                                playerSourceIndex = sourceIndex,
                            )
                        }
                        receivedPrimary = true
                    }
                }
                if (!receivedPrimary) {
                    _state.update {
                        it.copy(detailLoading = false, detailError = "影片详情不存在")
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                _state.update {
                    if (receivedPrimary) it.copy(detailTotalSources = 0)
                    else it.copy(detailLoading = false, detailError = error.userMessage())
                }
            }
        }
    }

    fun selectPlaySource(index: Int) {
        _state.update { state ->
            val maxIndex = (state.detailMovie?.playSources?.lastIndex ?: 0).coerceAtLeast(0)
            state.copy(
                selectedSourceIndex = index.coerceIn(0, maxIndex),
                selectedEpisodeIndex = 0,
            )
        }
    }

    fun openPlayer(sourceIndex: Int, episodeIndex: Int, startPositionMs: Long = 0L) {
        stopHistoryRecovery()
        _state.update { state ->
            val movie = state.detailMovie
            val maxSourceIndex = (movie?.playSources?.lastIndex ?: 0).coerceAtLeast(0)
            val requestedSourceIndex = sourceIndex.coerceIn(0, maxSourceIndex)
            val requestedSource = movie?.playSources?.getOrNull(requestedSourceIndex)
            val requestedEpisodeIndex = episodeIndex.coerceIn(
                0,
                (requestedSource?.episodes?.lastIndex ?: 0).coerceAtLeast(0),
            )
            val selectedSourceIndex = movie?.let {
                resolveBestPlaybackSourceIndex(
                    state = state,
                    movie = it,
                    requestedSourceIndex = requestedSourceIndex,
                    episodeIndex = requestedEpisodeIndex,
                )
            } ?: requestedSourceIndex
            val selectedSource = movie?.playSources?.getOrNull(selectedSourceIndex)
            val selectedEpisodeIndex = requestedEpisodeIndex.coerceIn(
                0,
                (selectedSource?.episodes?.lastIndex ?: 0).coerceAtLeast(0),
            )
            state.copy(
                screen = TvScreen.Player,
                selectedSourceIndex = selectedSourceIndex,
                selectedEpisodeIndex = selectedEpisodeIndex,
                playerSourceIndex = selectedSourceIndex,
                playerEpisodeIndex = selectedEpisodeIndex,
                playerStartPositionMs = startPositionMs.coerceAtLeast(0L),
                playerCanSkipIntro = startPositionMs <= 0L,
                playerPlayWhenReady = true,
            )
        }
    }

    fun clearPlaybackStats() {
        viewModelScope.launch {
            runCatching { playbackHealthRepository.clear() }
                .onSuccess { snapshot ->
                    _state.update { it.copy(playbackHealth = snapshot) }
                }
        }
    }

    fun resumeHistory(item: WatchHistoryItem, returnScreen: TvScreen = TvScreen.Home) {
        historyResumeJob?.cancel()
        detailJob?.cancel()
        retryDetailRequest = { resumeHistory(item, returnScreen) }
        _state.update {
            it.copy(
                screen = TvScreen.Detail,
                detailReturnScreen = returnScreen,
                detailMovie = null,
                historyResume = HistoryResumeState(item),
                detailLoading = true,
                detailError = null,
                detailCompletedSources = 0,
                detailTotalSources = 0,
                selectedSourceIndex = item.sourceIndex,
                selectedEpisodeIndex = item.episodeIndex,
            )
        }
        historyResumeJob = viewModelScope.launch {
            try {
                repository.findResumeSources(item).collect { candidate ->
                    currentCoroutineContext().ensureActive()
                    _state.update { it.applyResumeCandidate(item, candidate) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Finish this lookup just as when all candidate sources have failed.
            }
            currentCoroutineContext().ensureActive()
            _state.update { state ->
                val resume = state.historyResume ?: return@update state
                state.copy(
                    historyResume = resume.copy(searching = false),
                    detailLoading = false,
                    detailError = if (state.detailMovie == null) "暂时无法找到这一集的播放地址，请重试" else null,
                )
            }
        }
    }

    private fun stopHistoryRecovery() {
        val resume = _state.value.historyResume ?: return
        historyResumeJob?.cancel()
        _state.update {
            it.copy(historyResume = resume.copy(searching = false, recoveryEnabled = false, failurePositionMs = null))
        }
    }

    internal fun reportHistoryPlaybackFailure(positionMs: Long, issueType: PlaybackIssueType?): Boolean {
        val current = _state.value
        val resume = current.historyResume ?: return false
        if (!resume.recoveryEnabled || !current.appSettings.playbackAgentAutoSwitchEnabled ||
            current.screen != TvScreen.Player
        ) return false
        val url = current.detailMovie?.playSources?.getOrNull(current.playerSourceIndex)
            ?.episodes?.getOrNull(current.playerEpisodeIndex)?.url ?: return false
        if (issueType != null) recordPlaybackIssue(current, issueType)
        _state.update {
            it.copy(historyResume = resume.copy(
                failedUrls = resume.failedUrls + url,
                failurePositionMs = positionMs.coerceAtLeast(0L),
            ))
        }
        return true
    }

    internal fun recoverHistoryPlayback(): Boolean {
        val current = _state.value
        val resume = current.historyResume ?: return false
        val position = resume.failurePositionMs ?: return false
        if (current.screen != TvScreen.Player || !current.playerPlayWhenReady ||
            !current.appSettings.playbackAgentAutoSwitchEnabled || !resume.recoveryEnabled
        ) return false
        val target = current.historyPlaybackTarget() ?: return false
        _state.update {
            it.copy(
                historyResume = resume.copy(failurePositionMs = null),
                selectedSourceIndex = target.first, playerSourceIndex = target.first,
                selectedEpisodeIndex = target.second, playerEpisodeIndex = target.second,
                playerStartPositionMs = position, playerCanSkipIntro = false,
            )
        }
        return true
    }

    internal fun clearHistoryPlaybackFailure() {
        _state.update { state ->
            val resume = state.historyResume ?: return@update state
            if (resume.failurePositionMs == null && resume.failedUrls.isEmpty()) return@update state
            state.copy(historyResume = resume.copy(failurePositionMs = null, failedUrls = emptySet()))
        }
    }

    internal fun retryHistoryPlayback() {
        val current = _state.value
        val resume = current.historyResume ?: return
        val position = resume.failurePositionMs ?: return
        if (current.screen == TvScreen.Player && !resume.searching) {
            val latest = current.historyItems.firstOrNull {
                it.apiLineId == resume.item.apiLineId && it.movieId == resume.item.movieId
            } ?: resume.item
            resumeHistory(latest.copy(positionMs = position), current.detailReturnScreen)
        }
    }

    fun playNextEpisode() {
        val current = _state.value
        val episodes = current.detailMovie
            ?.playSources
            ?.getOrNull(current.playerSourceIndex)
            ?.episodes
            .orEmpty()
        if (current.playerEpisodeIndex < episodes.lastIndex) {
            stopHistoryRecovery()
            _state.update {
                val nextEpisodeIndex = it.playerEpisodeIndex + 1
                it.copy(
                    selectedSourceIndex = it.playerSourceIndex,
                    selectedEpisodeIndex = nextEpisodeIndex,
                    playerEpisodeIndex = nextEpisodeIndex,
                    playerStartPositionMs = 0L,
                    playerCanSkipIntro = true,
                )
            }
        }
    }

    fun playPreviousEpisode() {
        if (_state.value.playerEpisodeIndex > 0) {
            stopHistoryRecovery()
            _state.update {
                val previousEpisodeIndex = it.playerEpisodeIndex - 1
                it.copy(
                    selectedSourceIndex = it.playerSourceIndex,
                    selectedEpisodeIndex = previousEpisodeIndex,
                    playerEpisodeIndex = previousEpisodeIndex,
                    playerStartPositionMs = 0L,
                    playerCanSkipIntro = true,
                )
            }
        }
    }

    fun switchToNextPlayableSource(
        blockedSourceIndexes: Set<Int>,
        positionMs: Long,
        playWhenReady: Boolean,
        issueType: PlaybackIssueType? = null,
        autoTriggered: Boolean = true,
    ): PlaybackAgentDecision {
        val current = _state.value
        if (issueType != null) {
            recordPlaybackIssue(current, issueType)
        }
        if (autoTriggered && !current.appSettings.playbackAgentAutoSwitchEnabled) {
            return PlaybackAgentDecision(nextSourceIndex = null)
        }
        val movie = current.detailMovie ?: return PlaybackAgentDecision(nextSourceIndex = null)
        val decision = playbackAgent.selectNextSource(
            movie = movie,
            currentSourceIndex = current.playerSourceIndex,
            episodeIndex = current.playerEpisodeIndex,
            blockedSourceIndexes = blockedSourceIndexes,
            healthSnapshot = current.playbackHealth,
        )
        val nextSourceIndex = decision.nextSourceIndex ?: return decision

        if (!switchPlayerSource(nextSourceIndex, positionMs, playWhenReady)) {
            return PlaybackAgentDecision(nextSourceIndex = null)
        }
        return decision
    }

    fun recordPlaybackSuccess() {
        val current = _state.value
        val key = current.playbackHealthKeyOrNull() ?: return
        viewModelScope.launch {
            runCatching { playbackHealthRepository.recordSuccess(key, System.currentTimeMillis()) }
                .onSuccess { snapshot ->
                    _state.update { it.copy(playbackHealth = snapshot) }
                }
        }
    }

    fun playNextLiveChannel() {
        _state.update { state ->
            val channels = state.liveChannels
            if (channels.isEmpty()) return@update state
            state.copy(
                liveChannelIndex = (state.liveChannelIndex + 1) % channels.size,
                liveLineIndex = 0,
            )
        }
    }

    fun updateDeviceMode(mode: DeviceMode) {
        if (_state.value.appSettings.deviceMode == mode) return
        val settings = _state.value.appSettings.copy(deviceMode = mode)
        _state.update { it.copy(appSettings = settings) }
        saveSettings(settings)
    }

    fun updateDefaultIntroSkipSeconds(seconds: Int) {
        val settings = _state.value.appSettings.copy(defaultIntroSkipSeconds = seconds.coerceIn(0, 600))
        _state.update { it.copy(appSettings = settings) }
        saveSettings(settings)
    }

    fun consumeIntroStartRequest(playbackKey: String?) {
        _state.update {
            if (playbackKey != null && it.currentPlaybackKey() == playbackKey) it.copy(playerCanSkipIntro = false) else it
        }
    }

    fun updateMovieIntroSkipSeconds(seconds: Int?) {
        val movie = _state.value.detailMovie ?: return
        val settings = _state.value.appSettings.withIntroSkipOverride(introSkipKey(movie.apiLineId, movie.id), seconds)
        _state.update { it.copy(appSettings = settings) }
        saveSettings(settings)
    }

    fun updateTheme(theme: TvTheme) {
        if (_state.value.appSettings.theme == theme) return
        val settings = _state.value.appSettings.copy(theme = theme)
        saveSettings(settings)
        _state.update { it.copy(appSettings = settings) }
    }

    fun updateFontScale(fontScale: TvFontScale) {
        if (_state.value.appSettings.fontScale == fontScale) return
        val settings = _state.value.appSettings.copy(fontScale = fontScale)
        saveSettings(settings)
        _state.update { it.copy(appSettings = settings) }
    }

    fun playPreviousLiveChannel() {
        _state.update { state ->
            val channels = state.liveChannels
            if (channels.isEmpty()) return@update state
            val previousIndex = if (state.liveChannelIndex <= 0) channels.lastIndex else state.liveChannelIndex - 1
            state.copy(
                liveChannelIndex = previousIndex,
                liveLineIndex = 0,
            )
        }
    }

    fun playNextLiveLine(): Boolean {
        val state = _state.value
        val lines = state.liveChannels.getOrNull(state.liveChannelIndex)?.lines.orEmpty()
        if (lines.size <= 1) return false
        _state.update {
            it.copy(liveLineIndex = (it.liveLineIndex + 1) % lines.size)
        }
        return true
    }

    fun playPreviousLiveLine(): Boolean {
        val state = _state.value
        val lines = state.liveChannels.getOrNull(state.liveChannelIndex)?.lines.orEmpty()
        if (lines.size <= 1) return false
        _state.update {
            val previousIndex = if (it.liveLineIndex <= 0) lines.lastIndex else it.liveLineIndex - 1
            it.copy(liveLineIndex = previousIndex)
        }
        return true
    }

    fun advanceLiveLineAfterFailure(): Boolean {
        val state = _state.value
        val lines = state.liveChannels.getOrNull(state.liveChannelIndex)?.lines.orEmpty()
        val nextIndex = state.liveLineIndex + 1
        if (nextIndex !in lines.indices) return false
        _state.update { it.copy(liveLineIndex = nextIndex) }
        return true
    }

    private fun switchPlatformLiveSite(site: PlatformLiveSite) {
        _state.update {
            it.copy(
                screen = TvScreen.PlatformLive,
                platformLiveDestination = PlatformLiveDestination.ParentCategories,
                platformLiveSelectedSite = site,
                platformLiveParentCategories = emptyList(),
                platformLiveSelectedParentCategory = null,
                platformLiveSelectedCategory = null,
                platformLiveCategories = emptyList(),
                platformLiveRooms = emptyList(),
                platformLiveRoomIndex = 0,
                platformLiveRoomQuery = "",
                platformLiveRoomPage = 1,
                platformLiveRoomPageCount = 1,
                platformLiveLoading = platformLiveServiceUrl.isNotBlank(),
                platformLiveLoadingMore = false,
                platformLiveLoadMoreFailed = false,
                platformLiveError = null,
                platformLiveStream = null,
            )
        }
        loadPlatformLiveCategories(site)
    }

    fun selectPlatformLiveSite(site: PlatformLiveSite) {
        val current = _state.value
        if (current.platformLiveSelectedSite?.id == site.id) {
            when {
                current.platformLiveDestination == PlatformLiveDestination.Favorites -> returnToPlatformLiveBrowse()
                current.platformLiveError == null -> Unit
                else -> refreshPlatformLive()
            }
        } else {
            switchPlatformLiveSite(site)
        }
    }

    fun returnToPlatformLiveBrowse() {
        val current = _state.value
        val previousSite = current.platformLiveSites.firstOrNull { it.id == current.platformLiveSelectedSite?.id }
        val previousCategory = current.platformLiveSelectedCategory?.takeIf {
            previousSite != null && it in current.platformLiveCategories
        }
        val site = previousSite ?: current.platformLiveSites.firstOrNull()
        when {
            previousCategory != null -> selectPlatformLiveCategory(previousCategory)
            site != null -> switchPlatformLiveSite(site)
            else -> openPlatformLive()
        }
    }

    fun openPlatformLiveFavorites() {
        platformLiveJob?.cancel()
        _state.update {
            it.copy(
                screen = TvScreen.PlatformLive,
                platformLiveDestination = PlatformLiveDestination.Favorites,
                platformLiveRooms = it.platformLiveFavorites,
                platformLiveRoomQuery = "",
                platformLiveRoomIndex = 0,
                platformLiveLoading = false,
                platformLiveLoadingMore = false,
                platformLiveError = null,
                platformLiveStream = null,
            )
        }
    }

    fun togglePlatformLiveFavorite(room: PlatformLiveRoom) {
        viewModelScope.launch {
            platformLiveFavoritesMutex.withLock {
                runCatching { platformLiveFavoritesRepository.toggleFavorite(room) }
                    .onSuccess { favorites ->
                        _state.update { current ->
                            if (current.platformLiveDestination == PlatformLiveDestination.Favorites) {
                                current.copy(
                                    platformLiveFavorites = favorites,
                                    platformLiveRooms = favorites,
                                    platformLiveRoomIndex = current.platformLiveRoomIndex.coerceAtMost(favorites.lastIndex.coerceAtLeast(0)),
                                    platformLiveFavoriteError = null,
                                )
                            } else {
                                current.copy(platformLiveFavorites = favorites, platformLiveFavoriteError = null)
                            }
                        }
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        _state.update { it.copy(platformLiveFavoriteError = error.userMessage()) }
                    }
            }
        }
    }

    fun selectPlatformLiveParentCategory(parentCategory: PlatformLiveParentCategory) {
        val current = _state.value
        if (current.platformLiveSelectedParentCategory?.id == parentCategory.id &&
            current.platformLiveDestination == PlatformLiveDestination.Rooms) return
        val firstCategory = current.platformLiveCategories.firstOrNull { it.parentId == parentCategory.id }
        platformLiveJob?.cancel()
        _state.update {
            it.copy(
                screen = TvScreen.PlatformLive,
                platformLiveDestination = PlatformLiveDestination.Categories,
                platformLiveSelectedParentCategory = parentCategory,
                platformLiveSelectedCategory = null,
                platformLiveRooms = emptyList(),
                platformLiveRoomIndex = 0,
                platformLiveRoomQuery = "",
                platformLiveLoading = false,
                platformLiveLoadingMore = false,
                platformLiveLoadMoreFailed = false,
                platformLiveError = if (firstCategory == null) "这个大分类暂时没有可用的二级分类" else null,
                platformLiveStream = null,
            )
        }
        firstCategory?.let(::selectPlatformLiveCategory)
    }

    fun selectPlatformLiveCategory(category: PlatformLiveCategory) {
        _state.update {
            it.copy(
                screen = TvScreen.PlatformLive,
                platformLiveDestination = PlatformLiveDestination.Rooms,
                platformLiveSelectedCategory = category,
                platformLiveRooms = emptyList(),
                platformLiveRoomQuery = "",
                platformLiveRoomIndex = 0,
                platformLiveRoomPage = 1,
                platformLiveRoomPageCount = 1,
                platformLiveLoading = platformLiveServiceUrl.isNotBlank(),
                platformLiveLoadingMore = false,
                platformLiveError = null,
                platformLiveLoadMoreFailed = false,
                platformLiveStream = null,
            )
        }
        loadPlatformLiveRooms(category, reset = true)
    }

    fun loadMorePlatformLiveRooms() {
        val state = _state.value
        val category = state.platformLiveSelectedCategory ?: return
        if (state.screen != TvScreen.PlatformLive || state.platformLiveDestination != PlatformLiveDestination.Rooms ||
            state.platformLiveLoading || state.platformLiveLoadingMore ||
            state.platformLiveRoomPage >= state.platformLiveRoomPageCount
        ) {
            return
        }
        val pages = if (state.platformLiveRoomQuery.isBlank()) 1 else PLATFORM_LIVE_SEARCH_PAGES_PER_BATCH
        loadPlatformLiveRooms(category, reset = false, maxPages = pages)
    }

    fun searchPlatformLiveRooms(query: String) {
        val state = _state.value
        if (state.screen != TvScreen.PlatformLive || state.platformLiveDestination != PlatformLiveDestination.Rooms ||
            state.platformLiveLoading
        ) return
        val normalized = query.trim()
        if (normalized != state.platformLiveRoomQuery) {
            if (state.platformLiveLoadingMore) platformLiveJob?.cancel()
            _state.update {
                it.copy(
                    platformLiveRoomQuery = normalized,
                    platformLiveLoadingMore = false,
                    platformLiveLoadMoreFailed = false,
                )
            }
        }
        if (normalized.isNotBlank()) loadMorePlatformLiveRooms()
    }

    fun openPlatformLiveRoom(room: PlatformLiveRoom) {
        val current = _state.value
        val index = current.platformLiveRooms.indexOfFirst { it.id == room.id }
        if (index < 0) return
        val stopSearch = current.platformLiveRoomQuery.isNotBlank() && current.platformLiveLoadingMore
        if (stopSearch) platformLiveJob?.cancel()
        _state.update {
            it.copy(
                screen = TvScreen.PlatformLive,
                platformLiveDestination = PlatformLiveDestination.Player,
                platformLivePlayerReturnDestination = it.platformLiveDestination,
                platformLiveRoomIndex = index,
                platformLiveLoadingMore = !stopSearch && it.platformLiveLoadingMore,
                platformLiveError = null,
                platformLiveStream = null,
            )
        }
        resolvePlatformLiveRoom()
    }

    fun playNextPlatformLiveRoom() {
        movePlatformLiveRoom(1)
    }

    fun playPreviousPlatformLiveRoom() {
        movePlatformLiveRoom(-1)
    }

    private fun movePlatformLiveRoom(offset: Int) {
        val state = _state.value
        val rooms = state.visiblePlatformLiveRooms
        if (rooms.isEmpty()) return
        val currentRoomId = state.platformLiveRooms.getOrNull(state.platformLiveRoomIndex)?.id
        val index = rooms.indexOfFirst { it.id == currentRoomId }.coerceAtLeast(0)
        val nextRoom = rooms[(index + offset + rooms.size) % rooms.size]
        if (nextRoom.id == currentRoomId && state.platformLiveRoomQuery.isNotBlank()) return
        _state.update {
            it.copy(
                platformLiveRoomIndex = state.platformLiveRooms.indexOfFirst { room -> room.id == nextRoom.id },
                platformLiveError = null,
                platformLiveStream = null,
            )
        }
        resolvePlatformLiveRoom()
    }

    fun selectLiveChannel(index: Int) {
        _state.update { state ->
            val channels = state.liveChannels
            if (channels.isEmpty()) return@update state
            state.copy(
                liveChannelIndex = index.coerceIn(0, channels.lastIndex),
                liveLineIndex = 0,
            )
        }
    }

    fun selectLiveChannelNumber(number: Int): Boolean {
        val index = number - 1
        val channels = _state.value.liveChannels
        if (index !in channels.indices) return false
        selectLiveChannel(index)
        return true
    }

    fun retryPlatformLiveChannel() {
        refreshPlatformLive()
    }

    fun reconnectPlatformLiveChannel() {
        resolvePlatformLiveRoom(forceRefresh = true)
    }

    fun recoverPlatformLiveChannel() {
        val state = _state.value
        val stream = state.platformLiveStream ?: run {
            reconnectPlatformLiveChannel()
            return
        }
        if (platformLiveRecoveryInProgress || state.platformLiveResolving) return

        if (platformLiveRecoveryCount < PLATFORM_LIVE_RETRY_PER_LINE) {
            val retryCount = platformLiveRecoveryCount
            platformLiveRecoveryCount++
            platformLiveRecoveryInProgress = true
            viewModelScope.launch {
                if (retryCount == PLATFORM_LIVE_RETRY_PER_LINE - 1) {
                    delay(PLATFORM_LIVE_SECOND_RETRY_DELAY_MS)
                }
                resolvePlatformLiveRoom(
                    forceRefresh = true,
                    candidateIndex = stream.activeCandidateIndex,
                    isRecovery = true,
                )
            }
            return
        }

        platformLiveRecoveryCount = 0
        if (stream.activeCandidateIndex < stream.candidates.lastIndex) {
            _state.update {
                it.copy(
                    platformLiveError = null,
                    platformLiveStream = stream.withActiveCandidate(stream.activeCandidateIndex + 1),
                )
            }
        } else {
            _state.update {
                it.copy(platformLiveError = "全部 CDN 线路均播放失败，请稍后重试")
            }
        }
    }

    fun switchPlayerSource(index: Int, positionMs: Long, playWhenReady: Boolean): Boolean {
        val current = _state.value
        val movie = current.detailMovie ?: return false
        val target = com.tvbox.app.domain.resolvePlaybackSourceSwitch(
            movie, current.playerSourceIndex, current.playerEpisodeIndex, index, positionMs, playWhenReady,
        ) ?: return false
        if (index != current.playerSourceIndex) stopHistoryRecovery()
        _state.update {
            it.copy(
                playerSourceIndex = target.sourceIndex,
                selectedSourceIndex = target.sourceIndex,
                playerEpisodeIndex = target.episodeIndex,
                selectedEpisodeIndex = target.episodeIndex,
                playerStartPositionMs = target.positionMs,
                playerCanSkipIntro = false,
                playerPlayWhenReady = target.playWhenReady,
            )
        }
        return true
    }

    fun updatePlaybackSpeed(speed: Float) {
        if (speed <= 0f || _state.value.playerSpeed == speed) return
        _state.update { it.copy(playerSpeed = speed) }
    }

    fun updatePlayerPlayIntent(playWhenReady: Boolean) {
        _state.update { it.copy(playerPlayWhenReady = playWhenReady) }
    }

    fun savePlaybackProgress(positionMs: Long, durationMs: Long, snapshot: TvBoxUiState = _state.value) {
        val current = snapshot
        val movie = current.detailMovie ?: return
        val source = movie.playSources.getOrNull(current.playerSourceIndex) ?: return
        val episode = source.episodes.getOrNull(current.playerEpisodeIndex) ?: return
        if (episode.url.isBlank()) return

        val history = current.historyResume?.item
        val item = WatchHistoryItem(
            movieId = history?.movieId ?: movie.id,
            apiLineId = history?.apiLineId ?: movie.apiLineId,
            apiLineName = history?.apiLineName ?: movie.apiLineName,
            movieName = movie.name,
            posterUrl = movie.posterUrl,
            typeName = movie.typeName,
            remarks = movie.remarks,
            sourceIndex = current.playerSourceIndex,
            sourceName = source.sourceName,
            episodeIndex = current.playerEpisodeIndex,
            episodeTitle = episode.title,
            episodeUrl = episode.url,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAtEpochMs = System.currentTimeMillis(),
            playedApiLineId = source.lineId.ifBlank { movie.apiLineId },
            playedMovieId = source.movieId ?: movie.id,
            movieYear = movie.year.ifBlank { history?.movieYear.orEmpty() },
        )
        viewModelScope.launch {
            historyWriteMutex.withLock {
                runCatching { historyRepository.saveProgress(item) }
                    .onSuccess { history ->
                        _state.update { it.copy(historyItems = history) }
                    }
            }
        }
    }

    fun retryCurrent() {
        when (_state.value.screen) {
            TvScreen.Home -> refreshHome(forceRefresh = true)
            TvScreen.History -> loadHistory()
            TvScreen.Search -> retrySearch()
            TvScreen.Detail -> if (_state.value.detailError != null) retryDetailRequest?.invoke()
            TvScreen.Player -> Unit
            TvScreen.Live -> refreshLive()
            TvScreen.PlatformLive -> retryPlatformLiveChannel()
            TvScreen.Settings -> checkForAppUpdate(showError = true)
            TvScreen.AiRecommend -> submitAiRecommendation()
        }
    }

    fun goBack(): Boolean {
        val current = _state.value
        val handled = when (current.screen) {
            TvScreen.Player -> {
                stopHistoryRecovery()
                _state.update {
                    it.copy(
                        screen = TvScreen.Detail,
                        selectedSourceIndex = it.playerSourceIndex,
                        selectedEpisodeIndex = it.playerEpisodeIndex,
                    )
                }
                true
            }
            TvScreen.Detail -> {
                historyResumeJob?.cancel()
                detailJob?.cancel()
                retryDetailRequest = null
                _state.update { it.copy(screen = it.detailReturnScreen) }
                true
            }
            TvScreen.Search, TvScreen.History, TvScreen.Settings, TvScreen.AiRecommend -> {
                _state.update { it.copy(screen = TvScreen.Home) }
                true
            }
            TvScreen.Live -> {
                _state.update { it.copy(screen = TvScreen.Home) }
                true
            }
            TvScreen.PlatformLive -> {
                when (current.platformLiveDestination) {
                    PlatformLiveDestination.Player -> {
                        platformLiveResolveJob?.cancel()
                        _state.update {
                            it.copy(
                                platformLiveDestination = it.platformLivePlayerReturnDestination,
                                platformLiveResolving = false,
                                platformLiveError = null,
                                platformLiveStream = null,
                            )
                        }
                    }
                    PlatformLiveDestination.Favorites -> returnToPlatformLiveBrowse()
                    else -> {
                        platformLiveJob?.cancel()
                        openHome()
                    }
                }
                true
            }
            TvScreen.Home -> false
        }
        if (handled && _state.value.screen == TvScreen.Home && applyPendingVideoSources()) refreshHome()
        return handled
    }

    private fun loadHomePage(reset: Boolean, forceRefresh: Boolean = false) {
        homeJob?.cancel()
        val current = _state.value
        val nextPage = if (reset) 1 else current.page + 1
        homeJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    homeLoading = reset,
                    loadingMore = !reset,
                    homeError = null,
                    homeLoadMoreFailed = false,
                    page = if (reset) 1 else it.page,
                    pageCount = if (reset) 1 else it.pageCount,
                    total = if (reset) 0 else it.total,
                    homeNotice = if (reset) null else it.homeNotice,
                    movies = if (reset) emptyList() else it.movies,
                    doubanHotMovies = if (reset && current.isShowingDoubanHot) emptyList() else it.doubanHotMovies,
                )
            }
            if (current.isShowingDoubanHot) {
                loadDoubanHotPage(current, nextPage, reset, forceRefresh)
            } else {
                loadCatalogHomePage(current, nextPage, reset)
            }
        }
    }

    fun syncPlayerEpisode(episodeIndex: Int) {
        val current = _state.value
        if (episodeIndex != current.playerEpisodeIndex &&
            current.detailMovie?.playSources?.getOrNull(current.playerSourceIndex)?.episodes?.getOrNull(episodeIndex) != null
        ) stopHistoryRecovery()
        _state.update { state ->
            val episodes = state.detailMovie
                ?.playSources
                ?.getOrNull(state.playerSourceIndex)
                ?.episodes
                .orEmpty()
            if (episodeIndex !in episodes.indices) {
                state
            } else {
                state.copy(
                    selectedSourceIndex = state.playerSourceIndex,
                    selectedEpisodeIndex = episodeIndex,
                    playerEpisodeIndex = episodeIndex,
                    playerStartPositionMs = 0L,
                )
            }
        }
    }

    private suspend fun loadDoubanHotPage(
        current: TvBoxUiState,
        nextPage: Int,
        reset: Boolean,
        forceRefresh: Boolean,
    ): Unit = coroutineScope {
        val categoryRequest = if (reset) {
            async { repository.getHomeCategoriesOrNull(current.selectedApiLineId) }
        } else {
            null
        }
        val result = try {
            doubanHotRepository.getRecentHot(page = nextPage, forceRefresh = forceRefresh)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        currentCoroutineContext().ensureActive()
        val doubanUnavailable = result?.items.isNullOrEmpty()
        val categories = if (doubanUnavailable) {
            categoryRequest?.cancel()
            null
        } else {
            categoryRequest?.await()
        }
        currentCoroutineContext().ensureActive()
        if (reset && (doubanUnavailable || categories.isNullOrEmpty())) {
            loadCatalogHomePage(
                current = current,
                nextPage = 1,
                reset = true,
                fromDouban = true,
                doubanUnavailable = doubanUnavailable,
            )
        } else if (result == null) {
            _state.update {
                it.copy(loadingMore = false, homeLoadMoreFailed = true)
            }
        } else {
            _state.update {
                it.copy(
                    categories = categories ?: it.categories,
                    doubanHotMovies = if (reset) {
                        result.items
                    } else {
                        (it.doubanHotMovies + result.items)
                            .distinctBy { item -> item.doubanId.ifBlank { item.title } }
                    },
                    page = result.page,
                    pageCount = result.pageCount,
                    total = result.total,
                    homeLoading = false,
                    loadingMore = false,
                    homeNotice = null,
                )
            }
        }
    }

    private suspend fun loadCatalogHomePage(
        current: TvBoxUiState,
        nextPage: Int,
        reset: Boolean,
        fromDouban: Boolean = false,
        doubanUnavailable: Boolean = false,
    ) {
        val result = try {
            val selectedTypeId = current.selectedCategoryId
            val parentTypeIds = current.childCategoryIdsForSelectedParent()
            when {
                selectedTypeId != null -> repository.getMovies(
                    apiLineId = current.selectedApiLineId,
                    page = nextPage,
                    typeId = selectedTypeId,
                )
                parentTypeIds.isNotEmpty() -> repository.getMoviesByTypeIds(
                    apiLineId = current.selectedApiLineId,
                    page = nextPage,
                    typeIds = parentTypeIds,
                )
                current.selectedParentCategoryId != null -> repository.getMovies(
                    apiLineId = current.selectedApiLineId,
                    page = nextPage,
                    typeId = current.selectedParentCategoryId,
                )
                reset -> repository.loadAvailableHomeCatalog(current.selectedApiLineId)
                else -> repository.getHomeCatalogMovies(current.selectedApiLineId, nextPage)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            _state.update {
                if (reset) it.copy(homeLoading = false, homeError = error.userMessage())
                else it.copy(loadingMore = false, homeLoadMoreFailed = true)
            }
            return
        }
        currentCoroutineContext().ensureActive()
        if (result == null) {
            _state.update {
                it.copy(
                    homeLoading = false,
                    loadingMore = false,
                    doubanHotFallbackActive = fromDouban || it.doubanHotFallbackActive,
                    homeError = "暂时无法从任何视频源加载影片，请重试",
                )
            }
            return
        }

        val lineChanged = result.apiLine.id != current.selectedApiLineId
        val categories = result.categories.ifEmpty {
            val existing = if (lineChanged) emptyList() else current.categories
            if (reset && existing.isEmpty()) {
                repository.getHomeCategoriesOrNull(result.apiLine.id).orEmpty()
            } else {
                existing
            }
        }
        currentCoroutineContext().ensureActive()
        val notice = when {
            doubanUnavailable -> "豆瓣热播暂时不可用，已显示${result.apiLine.name}最近更新"
            lineChanged -> "已切换到${result.apiLine.name}"
            else -> null
        }
        _state.update {
            it.copy(
                selectedApiLineId = result.apiLine.id,
                categories = categories,
                movies = if (reset) {
                    result.movies
                } else {
                    (it.movies + result.movies).distinctBy { movie -> "${movie.apiLineId}-${movie.id}" }
                },
                page = result.page,
                pageCount = result.pageCount,
                total = result.total,
                homeLoading = false,
                loadingMore = false,
                doubanHotFallbackActive = fromDouban || it.doubanHotFallbackActive,
                homeNotice = if (reset) notice else it.homeNotice,
                homeError = if (result.movies.isEmpty() && reset) "当前没有影片" else null,
            )
        }
    }

    private suspend fun findPlayableMovie(item: AiRecommendationItem): Movie? {
        val keyword = item.searchKeyword.ifBlank { item.name }.trim()
        if (keyword.isBlank()) return null
        val movies = repository.getMovies(
            apiLineId = _state.value.let { it.preferredHomeApiLine?.id ?: it.selectedApiLineId },
            page = 1,
            keyword = keyword,
        ).movies
        if (movies.isEmpty()) return null

        val normalizedKeyword = keyword.normalizeAiMatchText()
        val normalizedName = item.name.normalizeAiMatchText()
        return movies.firstOrNull { movie ->
            val candidate = movie.name.normalizeAiMatchText()
            candidate == normalizedKeyword || candidate == normalizedName
        } ?: movies.firstOrNull { movie ->
            val candidate = movie.name.normalizeAiMatchText()
            candidate.isNotBlank() &&
                (candidate.contains(normalizedKeyword) || normalizedKeyword.contains(candidate))
        } ?: movies.first()
    }

    private suspend fun findPlayableDoubanMovie(title: String, apiLineId: String): Movie? {
        val keyword = title.trim()
        if (keyword.isBlank()) return null
        val movies = repository.getMovies(
            apiLineId = apiLineId,
            page = 1,
            keyword = keyword,
        ).movies
        val matchedIndex = findBestTitleMatchIndex(keyword, movies.map { it.name }) ?: return null
        return movies.getOrNull(matchedIndex)
    }

    private fun buildAiRecommendationRequest(query: String, excludedNames: List<String>): String {
        val distinctNames = excludedNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
        if (distinctNames.isEmpty()) return query
        return buildString {
            append(query)
            append("\n请换一批推荐，不要重复这些作品：")
            append(distinctNames.joinToString("、"))
            append("。")
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            runCatching { historyRepository.getHistory() }
                .onSuccess { history ->
                    _state.update { it.copy(historyItems = history) }
                }
        }
    }

    private fun refreshPlaybackHealth() {
        viewModelScope.launch {
            runCatching { playbackHealthRepository.getSnapshot() }
                .onSuccess { snapshot ->
                    _state.update { it.copy(playbackHealth = snapshot) }
                }
        }
    }

    private fun saveSettings(settings: AppSettings) {
        val previous = settingsWriteJob
        settingsWriteJob = viewModelScope.launch {
            previous?.join()
            runCatching { appSettingsRepository.saveSettings(settings) }
        }
    }

    private fun recordPlaybackIssue(state: TvBoxUiState, issueType: PlaybackIssueType) {
        val key = state.playbackHealthKeyOrNull() ?: return
        viewModelScope.launch {
            runCatching {
                playbackHealthRepository.recordIssue(
                    key = key,
                    issueType = issueType,
                    nowMs = System.currentTimeMillis(),
                )
            }.onSuccess { snapshot ->
                _state.update { it.copy(playbackHealth = snapshot) }
            }
        }
    }

    private fun loadLiveChannels() {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    screen = TvScreen.Live,
                    liveLoading = true,
                    liveError = null,
                )
            }
            runCatching { liveRepository.getChannels() }
                .onSuccess { channels ->
                    _state.update {
                        it.copy(
                            liveChannels = channels,
                            liveChannelIndex = it.liveChannelIndex.coerceIn(0, (channels.lastIndex).coerceAtLeast(0)),
                            liveLineIndex = 0,
                            liveLoading = false,
                            liveError = if (channels.isEmpty()) "没有可用直播频道" else null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            liveLoading = false,
                            liveError = error.userMessage(),
                        )
                    }
                }
        }
    }

    private fun loadPlatformLiveSites() {
        val serviceUrl = platformLiveServiceUrl
        if (serviceUrl.isBlank()) {
            _state.update {
                it.copy(
                    screen = TvScreen.PlatformLive,
                    platformLiveLoading = false,
                    platformLiveError = "测试服务未配置，请使用 TVBOX_PLATFORM_LIVE_SERVICE_URL 构建 APK",
                )
            }
            return
        }
        platformLiveJob?.cancel()
        platformLiveResolveJob?.cancel()
        platformLiveJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    screen = TvScreen.PlatformLive,
                    platformLiveDestination = PlatformLiveDestination.Sites,
                    platformLiveLoading = true,
                    platformLiveLoadingMore = false,
                    platformLiveResolving = false,
                    platformLiveError = null,
                    platformLiveStream = null,
                )
            }
            runCatching { platformLiveRepository.getSites(serviceUrl) }
                .onSuccess { sites ->
                    currentCoroutineContext().ensureActive()
                    if (_state.value.screen != TvScreen.PlatformLive ||
                        _state.value.platformLiveDestination != PlatformLiveDestination.Sites) return@onSuccess
                    _state.update {
                        if (it.platformLiveDestination != PlatformLiveDestination.Sites) it else it.copy(
                            platformLiveSites = sites,
                            platformLiveLoading = false,
                            platformLiveError = if (sites.isEmpty()) "没有可用平台直播服务" else null,
                        )
                    }
                    if (sites.isNotEmpty()) returnToPlatformLiveBrowse()
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _state.update {
                        if (it.platformLiveDestination != PlatformLiveDestination.Sites) it else it.copy(
                            platformLiveLoading = false,
                            platformLiveError = error.userMessage(),
                        )
                    }
                }
        }
    }

    private fun loadPlatformLiveFavorites() {
        viewModelScope.launch {
            platformLiveFavoritesMutex.withLock {
                runCatching { platformLiveFavoritesRepository.getFavorites() }
                    .onSuccess { favorites ->
                        _state.update { current ->
                            if (current.platformLiveDestination == PlatformLiveDestination.Favorites) {
                                current.copy(platformLiveFavorites = favorites, platformLiveRooms = favorites, platformLiveFavoriteError = null)
                            } else {
                                current.copy(platformLiveFavorites = favorites, platformLiveFavoriteError = null)
                            }
                        }
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        _state.update { it.copy(platformLiveFavoriteError = error.userMessage()) }
                    }
            }
        }
    }

    private fun loadPlatformLiveCategories(site: PlatformLiveSite) {
        val serviceUrl = platformLiveServiceUrl
        if (serviceUrl.isBlank()) return
        platformLiveJob?.cancel()
        platformLiveJob = viewModelScope.launch {
            _state.update {
                if (it.platformLiveSelectedSite?.id != site.id) it else {
                    it.copy(platformLiveLoading = true, platformLiveError = null)
                }
            }
            runCatching { platformLiveRepository.getCategoryTree(serviceUrl, site.id) }
                .onSuccess { tree ->
                    currentCoroutineContext().ensureActive()
                    val current = _state.value
                    if (current.screen != TvScreen.PlatformLive || current.platformLiveSelectedSite?.id != site.id ||
                        current.platformLiveDestination == PlatformLiveDestination.Favorites) return@onSuccess
                    _state.update {
                        if (it.platformLiveSelectedSite?.id != site.id) it else {
                            it.copy(
                                platformLiveDestination = PlatformLiveDestination.ParentCategories,
                                platformLiveParentCategories = tree.parentCategories,
                                platformLiveSelectedParentCategory = null,
                                platformLiveCategories = tree.categories,
                                platformLiveLoading = false,
                                platformLiveError = if (tree.parentCategories.isEmpty()) "没有可用直播大分类" else null,
                            )
                        }
                    }
                    val group = tree.parentCategories.firstOrNull { parent ->
                        tree.categories.any { it.parentId == parent.id }
                    }
                    group?.let(::selectPlatformLiveParentCategory)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    currentCoroutineContext().ensureActive()
                    _state.update {
                        if (it.platformLiveSelectedSite?.id != site.id) it else {
                            it.copy(platformLiveLoading = false, platformLiveError = error.userMessage())
                        }
                    }
                }
        }
    }

    private fun loadPlatformLiveRooms(category: PlatformLiveCategory, reset: Boolean, maxPages: Int = 1) {
        val state = _state.value
        val serviceUrl = platformLiveServiceUrl
        val site = state.platformLiveSelectedSite ?: return
        if (serviceUrl.isBlank()) return
        val startPage = if (reset) 1 else state.platformLiveRoomPage + 1
        fun TvBoxUiState.isCurrentCategory() = screen == TvScreen.PlatformLive &&
            (platformLiveDestination == PlatformLiveDestination.Rooms ||
                platformLiveDestination == PlatformLiveDestination.Player) &&
            platformLiveSelectedSite?.id == site.id && platformLiveSelectedCategory?.id == category.id

        platformLiveJob?.cancel()
        platformLiveJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    platformLiveLoading = reset,
                    platformLiveLoadingMore = !reset,
                    platformLiveError = if (reset) null else it.platformLiveError,
                    platformLiveLoadMoreFailed = false,
                )
            }
            for (page in startPage until (startPage + maxPages)) {
                val result = try {
                    platformLiveRepository.getRooms(serviceUrl, site.id, category.id, page)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    _state.update {
                        when {
                            !it.isCurrentCategory() -> it
                            reset -> it.copy(
                                platformLiveLoading = false,
                                platformLiveError = error.userMessage(),
                            )
                            else -> it.copy(platformLiveLoadingMore = false, platformLiveLoadMoreFailed = true)
                        }
                    }
                    return@launch
                }
                currentCoroutineContext().ensureActive()
                if (!_state.value.isCurrentCategory()) return@launch
                val hasNextPage = result.page == page && page < result.pageCount
                val continueBatch = hasNextPage && page + 1 < startPage + maxPages &&
                    _state.value.platformLiveDestination == PlatformLiveDestination.Rooms
                _state.update {
                    val rooms = if (reset) result.rooms else
                        (it.platformLiveRooms + result.rooms).distinctBy(PlatformLiveRoom::id)
                    it.copy(
                        platformLiveRooms = rooms,
                        platformLiveRoomPage = maxOf(page, result.page),
                        platformLiveRoomPageCount = result.pageCount,
                        platformLiveLoading = false,
                        platformLiveLoadingMore = continueBatch,
                        platformLiveError = if (reset && rooms.isEmpty() && !hasNextPage) {
                            "这个分类暂时没有正在直播的房间"
                        } else {
                            it.platformLiveError
                        },
                    )
                }
                if (!continueBatch) break
            }
        }
    }

    private fun resolvePlatformLiveRoom(
        forceRefresh: Boolean = false,
        candidateIndex: Int = 0,
        isRecovery: Boolean = false,
    ) {
        val serviceUrl = platformLiveServiceUrl
        val state = _state.value
        val room = state.platformLiveRooms.getOrNull(state.platformLiveRoomIndex) ?: return
        if (serviceUrl.isBlank()) return
        if (!isRecovery) {
            platformLiveRecoveryCount = 0
            platformLiveRecoveryInProgress = false
        }
        platformLiveResolveJob?.cancel()
        platformLiveResolveJob = viewModelScope.launch {
            _state.update {
                if (it.platformLiveDestination != PlatformLiveDestination.Player ||
                    it.platformLiveRooms.getOrNull(it.platformLiveRoomIndex)?.id != room.id
                ) {
                    it
                } else {
                    it.copy(platformLiveResolving = true, platformLiveError = null, platformLiveStream = null)
                }
            }
            runCatching { platformLiveRepository.resolve(serviceUrl, room, forceRefresh) }
                .onSuccess { stream ->
                    currentCoroutineContext().ensureActive()
                    _state.update {
                        if (it.platformLiveDestination != PlatformLiveDestination.Player ||
                            it.platformLiveRooms.getOrNull(it.platformLiveRoomIndex)?.id != room.id
                        ) {
                            it
                        } else {
                            it.copy(
                                platformLiveResolving = false,
                                platformLiveError = null,
                                platformLiveStream = stream.withActiveCandidate(candidateIndex),
                            )
                        }
                    }
                    platformLiveRecoveryInProgress = false
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    currentCoroutineContext().ensureActive()
                    _state.update {
                        if (it.platformLiveDestination != PlatformLiveDestination.Player ||
                            it.platformLiveRooms.getOrNull(it.platformLiveRoomIndex)?.id != room.id
                        ) {
                            it
                        } else {
                            it.copy(
                                platformLiveResolving = false,
                                platformLiveStream = null,
                                platformLiveError = error.userMessage(),
                            )
                        }
                    }
                    platformLiveRecoveryInProgress = false
                }
        }
    }

    private fun resolveBestPlaybackSourceIndex(
        state: TvBoxUiState,
        movie: Movie,
        requestedSourceIndex: Int,
        episodeIndex: Int,
    ): Int {
        val safeRequestedSourceIndex = requestedSourceIndex.coerceIn(
            0,
            movie.playSources.lastIndex.coerceAtLeast(0),
        )
        if (!state.appSettings.playbackAgentAutoSwitchEnabled) {
            return safeRequestedSourceIndex
        }
        return playbackAgent.selectBestSource(
            movie = movie,
            requestedSourceIndex = safeRequestedSourceIndex,
            episodeIndex = episodeIndex,
            healthSnapshot = state.playbackHealth,
        )?.sourceIndex ?: safeRequestedSourceIndex
    }

    override fun onCleared() {
        closePhoneSearchDialog()
        activeAiConfigServer?.stop()
        activeAiConfigServer = null
        activeVideoConfigServer?.stop()
        activeVideoConfigServer = null
        super.onCleared()
    }
}

internal fun TvBoxUiState.historyPlaybackTarget(): Pair<Int, Int>? {
    val resume = historyResume ?: return null
    return detailMovie?.playSources.orEmpty().withIndex().firstNotNullOfOrNull { (index, source) ->
        val episodeIndex = source.resumeEpisodeIndex(resume.item)
        if (index != playerSourceIndex && episodeIndex != null &&
            source.episodes[episodeIndex].url !in resume.failedUrls
        ) index to episodeIndex else null
    }
}

private fun TvBoxUiState.applyResumeCandidate(item: WatchHistoryItem, candidate: Movie): TvBoxUiState {
    val resume = historyResume ?: return this
    if (!resume.recoveryEnabled || (screen != TvScreen.Detail && screen != TvScreen.Player)) return this
    val active = detailMovie
    val knownUrls = active?.playSources.orEmpty().mapNotNull { source ->
        source.resumeEpisodeIndex(item)?.let { source.episodes[it].url }
    }.toMutableSet()
    val matches = candidate.playSources.filter { source ->
        source.resumeEpisodeIndex(item)?.let { knownUrls.add(source.episodes[it].url) } == true
    }
    if (matches.isEmpty()) return this
    if (active != null) return copy(detailMovie = active.copy(playSources = active.playSources + matches))

    val episodeIndex = matches.first().resumeEpisodeIndex(item) ?: return this
    return copy(
        detailMovie = candidate.copy(playSources = matches),
        detailLoading = false, detailError = null,
        selectedSourceIndex = 0, playerSourceIndex = 0,
        selectedEpisodeIndex = episodeIndex, playerEpisodeIndex = episodeIndex,
        playerStartPositionMs = item.positionMs.coerceAtLeast(0L),
        playerCanSkipIntro = false, playerPlayWhenReady = true,
        screen = TvScreen.Player,
    )
}

private fun TvBoxUiState.appendDetailSources(update: MovieDetailUpdate): TvBoxUiState {
    val active = detailMovie ?: return this
    if (active.id != update.movie.id || active.apiLineId != update.movie.apiLineId ||
        (screen != TvScreen.Player && screen != TvScreen.Detail)
    ) return this

    // Keep every existing playlist at its original index so the player never reloads it.
    val knownPlaylists = active.playSources.mapTo(mutableSetOf()) { source -> source.episodes.map { it.url } }
    val newSources = update.movie.playSources.filter { source ->
        knownPlaylists.add(source.episodes.map { it.url })
    }
    return copy(
        detailMovie = if (newSources.isEmpty()) active else active.copy(playSources = active.playSources + newSources),
        detailCompletedSources = update.completedSources,
        detailTotalSources = update.totalSources,
    )
}

private fun Throwable.userMessage(): String {
    return localizedMessage?.takeIf { it.isNotBlank() } ?: "网络请求失败，请稍后重试"
}

private fun TvBoxUiState.childCategoryIdsForSelectedParent(): List<Int> {
    val parentId = selectedParentCategoryId ?: return emptyList()
    return categories
        .filter { it.parentId == parentId }
        .map { it.id }
}

private fun TvBoxUiState.playbackHealthKeyOrNull(): String? {
    val movie = detailMovie ?: return null
    val source = movie.playSources.getOrNull(playerSourceIndex) ?: return null
    if (source.episodes.getOrNull(playerEpisodeIndex)?.url.isNullOrBlank()) return null
    return playbackHealthKey(
        movieId = movie.id,
        episodeIndex = playerEpisodeIndex,
        source = source,
    )
}

private fun String.normalizeAiMatchText(): String {
    return lowercase().filter { it.isLetterOrDigit() }
}

private fun normalizeMacCmsApiUrl(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (!trimmed.startsWith("http://", ignoreCase = true) &&
        !trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return null
    }
    val withoutQuery = trimmed.substringBefore('?').substringBefore('#').trimEnd('/')
    if (withoutQuery.length <= "http://".length) return null
    return "$withoutQuery/"
}

private fun customVideoApiLineId(baseUrl: String): String {
    return "custom_" + Integer.toUnsignedString(baseUrl.lowercase().hashCode(), 36)
}

private const val PLATFORM_LIVE_SEARCH_PAGES_PER_BATCH = 5
private const val PLATFORM_LIVE_RETRY_PER_LINE = 2
private const val PLATFORM_LIVE_SECOND_RETRY_DELAY_MS = 1_000L

private class DefaultAppUpdateRepositoryPlaceholder : AppUpdateRepository {
    override suspend fun checkForUpdate(currentVersionCode: Long): AppUpdate? = null
    override suspend fun downloadUpdate(update: AppUpdate, onProgress: (Int) -> Unit): java.io.File {
        error("AppUpdateRepository is not configured")
    }
}

private class DefaultAppSettingsRepositoryPlaceholder : AppSettingsRepository {
    override suspend fun getSettings(): AppSettings = AppSettings()
    override suspend fun saveSettings(settings: AppSettings): AppSettings = settings
}

private class DefaultPlaybackHealthRepositoryPlaceholder : PlaybackHealthRepository {
    override suspend fun getSnapshot(): PlaybackHealthSnapshot = PlaybackHealthSnapshot()
    override suspend fun recordIssue(
        key: String,
        issueType: PlaybackIssueType,
        nowMs: Long,
    ): PlaybackHealthSnapshot = PlaybackHealthSnapshot()

    override suspend fun recordSuccess(key: String, nowMs: Long): PlaybackHealthSnapshot = PlaybackHealthSnapshot()

    override suspend fun clear(): PlaybackHealthSnapshot = PlaybackHealthSnapshot()
}
