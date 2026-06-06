package com.tvbox.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvbox.app.data.DefaultMovieRepository
import com.tvbox.app.data.HistoryRepository
import com.tvbox.app.data.MovieRepository
import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.Category
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.WatchHistoryItem
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
) {
    val canLoadMore: Boolean
        get() = page < pageCount && !homeLoading && !loadingMore

    val selectedApiLine: ApiLine?
        get() = apiLines.firstOrNull { it.id == selectedApiLineId }
}

class TvBoxViewModel(
    private val repository: MovieRepository = DefaultMovieRepository(),
    private val historyRepository: HistoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TvBoxUiState())
    val state: StateFlow<TvBoxUiState> = _state.asStateFlow()

    private var homeJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var historyResumeJob: Job? = null

    init {
        _state.update {
            it.copy(
                apiLines = repository.apiLines,
                selectedApiLineId = repository.apiLines.firstOrNull()?.id.orEmpty(),
            )
        }
        loadHistory()
        refreshHome()
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

    fun openHistory() {
        loadHistory()
        _state.update { it.copy(screen = TvScreen.History) }
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
                    _state.update {
                        val sourceIndex = movie?.preferredSourceIndex() ?: 0
                        it.copy(
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
            val maxSourceIndex = (state.detailMovie?.playSources?.lastIndex ?: 0).coerceAtLeast(0)
            val boundedSourceIndex = sourceIndex.coerceIn(0, maxSourceIndex)
            val source = state.detailMovie?.playSources?.getOrNull(boundedSourceIndex)
            val boundedEpisodeIndex = episodeIndex.coerceIn(0, (source?.episodes?.lastIndex ?: 0).coerceAtLeast(0))
            state.copy(
                screen = TvScreen.Player,
                selectedSourceIndex = boundedSourceIndex,
                selectedEpisodeIndex = boundedEpisodeIndex,
                playerSourceIndex = boundedSourceIndex,
                playerEpisodeIndex = boundedEpisodeIndex,
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
            TvScreen.Detail, TvScreen.Search, TvScreen.History -> {
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

private const val DEFAULT_ALL_CATEGORY_TYPE_ID = 13

private val playbackSpeeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
