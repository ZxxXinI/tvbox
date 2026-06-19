package com.tvbox.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvbox.app.data.AppSettingsRepository
import com.tvbox.app.BuildConfig
import com.tvbox.app.data.AppUpdateRepository
import com.tvbox.app.data.DefaultMovieRepository
import com.tvbox.app.data.DefaultLiveRepository
import com.tvbox.app.data.HistoryRepository
import com.tvbox.app.data.LiveRepository
import com.tvbox.app.data.MovieRepository
import com.tvbox.app.data.PlaybackHealthRepository
import com.tvbox.app.domain.AppSettings
import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.AppUpdate
import com.tvbox.app.domain.Category
import com.tvbox.app.domain.LiveChannel
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.PlaybackAgent
import com.tvbox.app.domain.PlaybackAgentDecision
import com.tvbox.app.domain.PlaybackHealthSnapshot
import com.tvbox.app.domain.PlaybackIssueType
import com.tvbox.app.domain.WatchHistoryItem
import com.tvbox.app.domain.playbackHealthKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class TvScreen {
    Home,
    History,
    Search,
    Detail,
    Player,
    Live,
    Settings,
}

data class TvBoxUiState(
    val screen: TvScreen = TvScreen.Home,
    val apiLines: List<ApiLine> = emptyList(),
    val selectedApiLineId: String = "",
    val categories: List<Category> = emptyList(),
    val selectedParentCategoryId: Int? = null,
    val selectedCategoryId: Int? = null,
    val movies: List<Movie> = emptyList(),
    val page: Int = 1,
    val pageCount: Int = 1,
    val total: Int = 0,
    val homeLoading: Boolean = false,
    val loadingMore: Boolean = false,
    val homeError: String? = null,
    val historyItems: List<WatchHistoryItem> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Movie> = emptyList(),
    val searchLoading: Boolean = false,
    val searchError: String? = null,
    val detailMovie: Movie? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val selectedSourceIndex: Int = 0,
    val selectedEpisodeIndex: Int = 0,
    val playerSourceIndex: Int = 0,
    val playerEpisodeIndex: Int = 0,
    val playerStartPositionMs: Long = 0L,
    val playerSpeed: Float = 1f,
    val liveChannels: List<LiveChannel> = emptyList(),
    val liveChannelIndex: Int = 0,
    val liveLoading: Boolean = false,
    val liveError: String? = null,
    val availableUpdate: AppUpdate? = null,
    val updateDialogVisible: Boolean = false,
    val updateChecking: Boolean = false,
    val updateDownloading: Boolean = false,
    val updateDownloadProgress: Int? = null,
    val updateDownloadedApkPath: String? = null,
    val updateError: String? = null,
    val appSettings: AppSettings = AppSettings(),
    val playbackHealth: PlaybackHealthSnapshot = PlaybackHealthSnapshot(),
) {
    val canLoadMore: Boolean
        get() = page < pageCount && !homeLoading && !loadingMore

    val selectedApiLine: ApiLine?
        get() = apiLines.firstOrNull { it.id == selectedApiLineId }
}

class TvBoxViewModel(
    private val repository: MovieRepository = DefaultMovieRepository(),
    private val liveRepository: LiveRepository = DefaultLiveRepository(),
    private val appUpdateRepository: AppUpdateRepository = DefaultAppUpdateRepositoryPlaceholder(),
    private val appSettingsRepository: AppSettingsRepository = DefaultAppSettingsRepositoryPlaceholder(),
    private val playbackHealthRepository: PlaybackHealthRepository = DefaultPlaybackHealthRepositoryPlaceholder(),
    private val historyRepository: HistoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TvBoxUiState())
    val state: StateFlow<TvBoxUiState> = _state.asStateFlow()

    private var homeJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var historyResumeJob: Job? = null
    private var liveJob: Job? = null
    private var updateJob: Job? = null
    private var updateDownloadJob: Job? = null
    private val playbackAgent = PlaybackAgent()

    init {
        _state.update {
            it.copy(
                apiLines = repository.apiLines,
                selectedApiLineId = repository.apiLines.firstOrNull()?.id.orEmpty(),
            )
        }
        viewModelScope.launch {
            val settings = runCatching { appSettingsRepository.getSettings() }
                .getOrDefault(AppSettings())
            val playbackHealth = runCatching { playbackHealthRepository.getSnapshot() }
                .getOrDefault(PlaybackHealthSnapshot())
            _state.update {
                it.copy(
                    appSettings = settings,
                    playbackHealth = playbackHealth,
                )
            }
            loadHistory()
            refreshHome()
            if (settings.checkUpdatesOnStartup) {
                checkForAppUpdate()
            }
        }
    }

    fun refreshHome() {
        loadCategoriesOnly()
        loadHomePage(reset = true)
    }

    fun selectAllCategories() {
        val current = _state.value
        if (current.selectedParentCategoryId == null && current.selectedCategoryId == null) return
        _state.update {
            it.copy(
                selectedParentCategoryId = null,
                selectedCategoryId = null,
                movies = emptyList(),
                page = 1,
                pageCount = 1,
                total = 0,
            )
        }
        refreshHome()
    }

    fun selectParentCategory(parentCategoryId: Int) {
        val current = _state.value
        if (current.selectedParentCategoryId == parentCategoryId && current.selectedCategoryId == null) return
        _state.update {
            it.copy(
                selectedParentCategoryId = parentCategoryId,
                selectedCategoryId = null,
                movies = emptyList(),
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
                selectedParentCategoryId = parentCategoryId,
                selectedCategoryId = categoryId,
                movies = emptyList(),
                page = 1,
                pageCount = 1,
                total = 0,
            )
        }
        loadHomePage(reset = true)
    }

    fun selectApiLine(apiLineId: String) {
        if (_state.value.selectedApiLineId == apiLineId) return
        _state.update {
            it.copy(
                selectedApiLineId = apiLineId,
                selectedParentCategoryId = null,
                selectedCategoryId = null,
                categories = emptyList(),
                movies = emptyList(),
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
        loadHomePage(reset = false)
    }

    fun openSearch() {
        _state.update { it.copy(screen = TvScreen.Search, searchError = null) }
    }

    fun openLive() {
        _state.update { it.copy(screen = TvScreen.Live, liveError = null) }
        if (_state.value.liveChannels.isEmpty()) {
            loadLiveChannels()
        }
    }

    fun openSettings() {
        _state.update { it.copy(screen = TvScreen.Settings, updateError = null) }
    }

    fun refreshLive() {
        loadLiveChannels()
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
        updateDownloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    updateDownloading = true,
                    updateDownloadProgress = 0,
                    updateDownloadedApkPath = null,
                    updateError = null,
                )
            }
            runCatching {
                appUpdateRepository.downloadUpdate(update) { progress ->
                    _state.update { it.copy(updateDownloadProgress = progress) }
                }
            }
                .onSuccess { apkFile ->
                    _state.update {
                        it.copy(
                            updateDownloading = false,
                            updateDownloadProgress = 100,
                            updateDownloadedApkPath = apkFile.absolutePath,
                            updateError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            updateDownloading = false,
                            updateDownloadProgress = null,
                            updateDownloadedApkPath = null,
                            updateError = error.userMessage(),
                        )
                    }
                }
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

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun submitSearch() {
        val query = _state.value.searchQuery.trim()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), searchError = "请输入搜索关键词") }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searchLoading = true, searchError = null) }
            runCatching {
                repository.getMovies(
                    apiLineId = _state.value.selectedApiLineId,
                    page = 1,
                    keyword = query,
                )
            }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            searchLoading = false,
                            searchResults = result.movies,
                            searchError = if (result.movies.isEmpty()) "没有找到相关影片" else null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(searchLoading = false, searchError = error.userMessage())
                    }
                }
        }
    }

    fun openDetail(movieId: Int) {
        detailJob?.cancel()
        _state.update {
            it.copy(
                screen = TvScreen.Detail,
                detailMovie = null,
                detailLoading = true,
                detailError = null,
                selectedSourceIndex = 0,
                selectedEpisodeIndex = 0,
                playerSourceIndex = 0,
                playerEpisodeIndex = 0,
                playerStartPositionMs = 0L,
            )
        }
        detailJob = viewModelScope.launch {
            runCatching { repository.getDetail(apiLineId = _state.value.selectedApiLineId, id = movieId) }
                .onSuccess { movie ->
                    _state.update { state ->
                        val sourceIndex = movie?.let { detailMovie ->
                            resolveBestPlaybackSourceIndex(
                                state = state,
                                movie = detailMovie,
                                requestedSourceIndex = detailMovie.preferredSourceIndex(),
                                episodeIndex = 0,
                            )
                        } ?: 0
                        state.copy(
                            detailMovie = movie,
                            detailLoading = false,
                            detailError = if (movie == null) "影片详情不存在" else null,
                            selectedSourceIndex = sourceIndex,
                            selectedEpisodeIndex = 0,
                            playerSourceIndex = sourceIndex,
                            playerEpisodeIndex = 0,
                            playerStartPositionMs = 0L,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(detailLoading = false, detailError = error.userMessage())
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
            )
        }
    }

    fun resumeHistory(item: WatchHistoryItem) {
        historyResumeJob?.cancel()
        _state.update {
            it.copy(
                screen = TvScreen.Detail,
                detailMovie = null,
                detailLoading = true,
                detailError = null,
                selectedSourceIndex = item.sourceIndex,
                selectedEpisodeIndex = item.episodeIndex,
            )
        }
        historyResumeJob = viewModelScope.launch {
            runCatching { repository.getDetail(apiLineId = item.apiLineId, id = item.movieId) }
                .onSuccess { movie ->
                    if (movie == null) {
                        _state.update {
                            it.copy(detailLoading = false, detailError = "影片详情不存在")
                        }
                        return@onSuccess
                    }
                    val (sourceIndex, episodeIndex) = resolveHistoryPosition(movie, item)
                    _state.update {
                        it.copy(
                            detailMovie = movie,
                            detailLoading = false,
                            detailError = null,
                            selectedSourceIndex = sourceIndex,
                            selectedEpisodeIndex = episodeIndex,
                            playerSourceIndex = sourceIndex,
                            playerEpisodeIndex = episodeIndex,
                            playerStartPositionMs = item.positionMs.coerceAtLeast(0L),
                            screen = TvScreen.Player,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(detailLoading = false, detailError = error.userMessage())
                    }
                }
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
            _state.update {
                val nextEpisodeIndex = it.playerEpisodeIndex + 1
                it.copy(
                    selectedSourceIndex = it.playerSourceIndex,
                    selectedEpisodeIndex = nextEpisodeIndex,
                    playerEpisodeIndex = nextEpisodeIndex,
                    playerStartPositionMs = 0L,
                )
            }
        }
    }

    fun playPreviousEpisode() {
        if (_state.value.playerEpisodeIndex > 0) {
            _state.update {
                val previousEpisodeIndex = it.playerEpisodeIndex - 1
                it.copy(
                    selectedSourceIndex = it.playerSourceIndex,
                    selectedEpisodeIndex = previousEpisodeIndex,
                    playerEpisodeIndex = previousEpisodeIndex,
                    playerStartPositionMs = 0L,
                )
            }
        }
    }

    fun switchToNextPlayableSource(
        blockedSourceIndexes: Set<Int>,
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

        _state.update {
            it.copy(
                selectedSourceIndex = nextSourceIndex,
                selectedEpisodeIndex = it.playerEpisodeIndex,
                playerSourceIndex = nextSourceIndex,
                playerStartPositionMs = 0L,
            )
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
            state.copy(liveChannelIndex = (state.liveChannelIndex + 1) % channels.size)
        }
    }

    fun playPreviousLiveChannel() {
        _state.update { state ->
            val channels = state.liveChannels
            if (channels.isEmpty()) return@update state
            val previousIndex = if (state.liveChannelIndex <= 0) channels.lastIndex else state.liveChannelIndex - 1
            state.copy(liveChannelIndex = previousIndex)
        }
    }

    fun selectLiveChannel(index: Int) {
        _state.update { state ->
            val channels = state.liveChannels
            if (channels.isEmpty()) return@update state
            state.copy(liveChannelIndex = index.coerceIn(0, channels.lastIndex))
        }
    }

    fun selectLiveChannelNumber(number: Int): Boolean {
        val index = number - 1
        val channels = _state.value.liveChannels
        if (index !in channels.indices) return false
        selectLiveChannel(index)
        return true
    }

    fun cyclePlaybackSpeed() {
        val currentSpeed = _state.value.playerSpeed
        val currentIndex = playbackSpeeds.indexOfFirst { abs(it - currentSpeed) < 0.01f }
        val nextSpeed = playbackSpeeds[(currentIndex + 1).coerceAtLeast(0) % playbackSpeeds.size]
        _state.update { it.copy(playerSpeed = nextSpeed) }
    }

    fun savePlaybackProgress(positionMs: Long, durationMs: Long) {
        val current = _state.value
        val movie = current.detailMovie ?: return
        val source = movie.playSources.getOrNull(current.playerSourceIndex) ?: return
        val episode = source.episodes.getOrNull(current.playerEpisodeIndex) ?: return
        if (episode.url.isBlank()) return

        val item = WatchHistoryItem(
            movieId = movie.id,
            apiLineId = movie.apiLineId,
            apiLineName = movie.apiLineName,
            movieName = movie.name,
            posterUrl = movie.posterUrl,
            typeName = movie.typeName,
            remarks = movie.remarks,
            sourceIndex = current.playerSourceIndex,
            sourceName = source.name,
            episodeIndex = current.playerEpisodeIndex,
            episodeTitle = episode.title,
            episodeUrl = episode.url,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            runCatching { historyRepository.saveProgress(item) }
                .onSuccess { history ->
                    _state.update { it.copy(historyItems = history) }
                }
        }
    }

    fun retryCurrent() {
        when (_state.value.screen) {
            TvScreen.Home -> refreshHome()
            TvScreen.History -> loadHistory()
            TvScreen.Search -> submitSearch()
            TvScreen.Detail -> _state.value.detailMovie?.let { openDetail(it.id) }
            TvScreen.Player -> Unit
            TvScreen.Live -> refreshLive()
            TvScreen.Settings -> checkForAppUpdate(showError = true)
        }
    }

    fun goBack(): Boolean {
        val current = _state.value
        return when (current.screen) {
            TvScreen.Player -> {
                _state.update {
                    it.copy(
                        screen = TvScreen.Detail,
                        selectedSourceIndex = it.playerSourceIndex,
                        selectedEpisodeIndex = it.playerEpisodeIndex,
                    )
                }
                true
            }
            TvScreen.Detail, TvScreen.Search, TvScreen.History, TvScreen.Settings -> {
                _state.update { it.copy(screen = TvScreen.Home) }
                true
            }
            TvScreen.Live -> {
                _state.update { it.copy(screen = TvScreen.Home) }
                true
            }
            TvScreen.Home -> false
        }
    }

    private fun loadHomePage(reset: Boolean) {
        homeJob?.cancel()
        val current = _state.value
        val nextPage = if (reset) 1 else current.page + 1
        homeJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    homeLoading = reset,
                    loadingMore = !reset,
                    homeError = null,
                    movies = if (reset) emptyList() else it.movies,
                )
            }
            runCatching {
                val latest = _state.value
                val selectedTypeId = latest.selectedCategoryId
                val parentTypeIds = latest.childCategoryIdsForSelectedParent()
                when {
                    selectedTypeId != null -> repository.getMovies(
                        apiLineId = latest.selectedApiLineId,
                        page = nextPage,
                        typeId = selectedTypeId,
                    )
                    parentTypeIds.isNotEmpty() -> repository.getMoviesByTypeIds(
                        apiLineId = latest.selectedApiLineId,
                        page = nextPage,
                        typeIds = parentTypeIds,
                    )
                    latest.selectedParentCategoryId != null -> repository.getMovies(
                        apiLineId = latest.selectedApiLineId,
                        page = nextPage,
                        typeId = latest.selectedParentCategoryId,
                    )
                    latest.isDefaultAllCategorySelection() -> repository.getMovies(
                        apiLineId = latest.selectedApiLineId,
                        page = nextPage,
                        typeId = DEFAULT_ALL_CATEGORY_TYPE_ID,
                    )
                    else -> repository.getMovies(
                        apiLineId = latest.selectedApiLineId,
                        page = nextPage,
                    )
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        categories = if (result.categories.isNotEmpty()) result.categories else it.categories,
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
                        homeError = if (result.movies.isEmpty() && reset) "当前没有影片" else null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        homeLoading = false,
                        loadingMore = false,
                        homeError = error.userMessage(),
                    )
                }
                if (_state.value.categories.isEmpty()) {
                    loadCategoriesOnly()
                }
            }
        }
    }

    private fun loadCategoriesOnly() {
        viewModelScope.launch {
            runCatching { repository.getCategories(apiLineId = _state.value.selectedApiLineId) }
                .onSuccess { categories ->
                    _state.update { it.copy(categories = categories) }
                }
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

    private fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
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

    private fun resolveHistoryPosition(movie: Movie, item: WatchHistoryItem): Pair<Int, Int> {
        val sourceIndexByUrl = movie.playSources.indexOfFirst { source ->
            source.episodes.any { episode -> episode.url == item.episodeUrl }
        }
        val sourceIndex = when {
            sourceIndexByUrl >= 0 -> sourceIndexByUrl
            item.sourceIndex in movie.playSources.indices -> item.sourceIndex
            else -> movie.preferredSourceIndex()
        }.coerceAtLeast(0)

        val episodes = movie.playSources.getOrNull(sourceIndex)?.episodes.orEmpty()
        val episodeIndexByUrl = episodes.indexOfFirst { it.url == item.episodeUrl }
        val episodeIndex = when {
            episodeIndexByUrl >= 0 -> episodeIndexByUrl
            item.episodeIndex in episodes.indices -> item.episodeIndex
            else -> 0
        }.coerceAtLeast(0)

        return sourceIndex to episodeIndex
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

private fun TvBoxUiState.isDefaultAllCategorySelection(): Boolean {
    return selectedParentCategoryId == null && selectedCategoryId == null
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

private const val DEFAULT_ALL_CATEGORY_TYPE_ID = 13

private val playbackSpeeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

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
}
