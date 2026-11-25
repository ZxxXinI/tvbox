package com.tvbox.app.ui

import com.tvbox.app.data.DefaultPlatformLiveRepository
import com.tvbox.app.data.DoubanHotCategory
import com.tvbox.app.data.DoubanHotRepository
import com.tvbox.app.data.HistoryRepository
import com.tvbox.app.data.MovieDetailUpdate
import com.tvbox.app.data.MovieRepository
import com.tvbox.app.data.MultiSourceSearchUpdate
import com.tvbox.app.data.PlatformLiveCategoryTree
import com.tvbox.app.data.PlatformLiveFavoritesRepository
import com.tvbox.app.data.PlatformLiveRepository
import com.tvbox.app.data.PlatformLiveRoomsPage
import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.AppSettings
import com.tvbox.app.domain.Category
import com.tvbox.app.domain.DoubanHotItem
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.PagedDoubanHotMovies
import com.tvbox.app.domain.PagedMovies
import com.tvbox.app.domain.PlayEpisode
import com.tvbox.app.domain.PlaySource
import com.tvbox.app.domain.PlatformLiveCategory
import com.tvbox.app.domain.PlatformLiveParentCategory
import com.tvbox.app.domain.PlatformLiveRoom
import com.tvbox.app.domain.PlatformLiveSite
import com.tvbox.app.domain.ResolvedPlatformLiveStream
import com.tvbox.app.domain.TvTheme
import com.tvbox.app.domain.WatchHistoryItem
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TvBoxBrowseRecoveryTest {
    @Test
    fun cinemaPlatformLiveUsesItsOwnNavigationAndHomeKeepsTheAppRail() {
        val browsing = TvBoxUiState(
            screen = TvScreen.PlatformLive,
            platformLiveDestination = PlatformLiveDestination.Rooms,
            appSettings = AppSettings(theme = TvTheme.Cinema),
        )
        assertFalse(browsing.showCinemaNavigation)
        assertFalse(browsing.copy(platformLiveDestination = PlatformLiveDestination.Player).showCinemaNavigation)
        assertFalse(browsing.copy(platformLiveDestination = PlatformLiveDestination.Favorites).showCinemaNavigation)
        assertTrue(browsing.copy(screen = TvScreen.Home).showCinemaNavigation)
    }

    @Test
    fun searchDistinguishesUnavailableSourcesFromNoMatchesAndKeepsPartialResults() = runBlocking {
        val requests = mutableListOf<Pair<String, Boolean>>()
        val movies = Movies().apply {
            search = { keyword, retry ->
                requests += keyword to retry
                val found = if (retry) listOf(movie()) else emptyList()
                val successCount = if (retry || keyword == "no match") 1 else 0
                flowOf(MultiSourceSearchUpdate(found, completedSources = 2, totalSources = 2, successfulSources = successCount))
            }
        }

        withViewModel(movies = movies) { vm ->
            vm.openSearch()
            vm.updateSearchQuery("offline")
            vm.submitSearch()
            val unavailable = vm.await { it.searchError != null }
            assertEquals("暂时无法连接视频源，请重试", unavailable.searchError)
            assertEquals(2, unavailable.searchFailedSources)

            vm.retrySearch()
            val partial = vm.await { it.searchResults.isNotEmpty() }
            assertEquals(1, partial.searchFailedSources)
            assertNull(partial.searchError)

            vm.updateSearchQuery("no match")
            vm.submitSearch()
            val noMatch = vm.await { it.searchError != null }
            assertEquals("可用视频源未找到相关影片，部分视频源请求失败", noMatch.searchError)
            assertEquals(listOf("offline" to false, "offline" to true, "no match" to false), requests)
        }
    }

    @Test
    fun lateSourceUpdatesAppendWithoutChangingThePlayingPlaylistOrEpisode() = runBlocking {
        val primary = playlist("primary")
        val selected = playlist("selected")
        val late = playlist("late")
        val finish = CompletableDeferred<Unit>()
        val repository = Movies().apply {
            detail = { _, _ ->
                flow {
                    emit(MovieDetailUpdate(movie(sources = listOf(primary, selected)), 2, 3))
                    finish.await()
                    emit(MovieDetailUpdate(movie(sources = listOf(primary, late, selected)), 3, 3))
                }
            }
        }

        withViewModel(movies = repository) { vm ->
            vm.await { !it.homeLoading && it.movies.isNotEmpty() }
            vm.updatePlaybackAgentAutoSwitch(false)
            vm.openDetail(movieId = 7, apiLineId = apiLine.id)
            vm.await { it.detailMovie != null }
            vm.openPlayer(sourceIndex = 1, episodeIndex = 4, startPositionMs = 32_000L)
            finish.complete(Unit)

            val state = vm.await { it.detailCompletedSources == 3 }
            assertEquals(listOf(primary, selected, late), state.detailMovie?.playSources)
            assertSame(selected, state.detailMovie?.playSources?.get(state.playerSourceIndex))
            assertEquals(TvScreen.Player, state.screen)
            assertEquals(4, state.playerEpisodeIndex)
            assertEquals(4, state.selectedEpisodeIndex)
            assertEquals(32_000L, state.playerStartPositionMs)
            vm.goBack()
            assertEquals(1, vm.state.value.selectedSourceIndex)
            assertEquals(4, vm.state.value.selectedEpisodeIndex)
        }
    }

    @Test
    fun retryDetailsKeepsTheRequestedSourceAndTheReturnScreen() = runBlocking {
        val requests = mutableListOf<Pair<String, Int>>()
        val repository = Movies().apply {
            detail = { source, id ->
                flow {
                    requests += source to id
                    if (requests.size == 1) throw IOException("Detail failed")
                    emit(MovieDetailUpdate(movie(id), 1, 1))
                }
            }
        }

        withViewModel(movies = repository) { vm ->
            vm.openSearch()
            vm.openDetail(movieId = 7, apiLineId = apiLine.id)
            assertNull(vm.await { it.detailError != null }.detailMovie)
            vm.retryCurrent()

            val state = vm.await { it.detailMovie != null }
            assertEquals(listOf(apiLine.id to 7, apiLine.id to 7), requests)
            assertNull(state.detailError)
            vm.goBack()
            assertEquals(TvScreen.Search, vm.state.value.screen)
        }
    }

    @Test
    fun retryResumeUsesTheOriginalEpisodeAndProgress() = runBlocking {
        val requests = mutableListOf<Pair<String, Int>>()
        val repository = Movies().apply {
            resume = { source, id ->
                requests += source to id
                if (requests.size == 1) throw IOException("Resume failed")
                movie(id, listOf(playlist("resume")))
            }
        }

        withViewModel(movies = repository) { vm ->
            vm.resumeHistory(history, TvScreen.History)
            assertNull(vm.await { it.detailError != null }.detailMovie)
            vm.retryCurrent()

            val state = vm.await { it.screen == TvScreen.Player }
            assertEquals(listOf(apiLine.id to 7, apiLine.id to 7), requests)
            assertEquals(history.episodeIndex, state.playerEpisodeIndex)
            assertEquals(history.positionMs, state.playerStartPositionMs)
            assertFalse(state.playerCanSkipIntro)
            vm.goBack()
            vm.goBack()
            assertEquals(TvScreen.History, vm.state.value.screen)
        }
    }

    @Test
    fun localHistoryLoadsDuringAnOnlineRequestAndCatalogPaginationRetriesTheSamePage() = runBlocking {
        val firstPage = CompletableDeferred<Unit>()
        val requests = mutableListOf<Int>()
        val repository = Movies().apply {
            catalog = { page ->
                requests += page
                if (page == 1) firstPage.await()
                if (requests.size == 2) throw IOException("Page failed")
                PagedMovies(page, 2, 2, apiLine, listOf(category), listOf(movie(page)))
            }
        }

        withViewModel(movies = repository) { vm ->
            val loading = vm.await { it.homeLoading && it.historyItems.isNotEmpty() }
            assertEquals(listOf(history), loading.historyItems)
            firstPage.complete(Unit)
            vm.await { !it.homeLoading && it.movies.isNotEmpty() }
            vm.loadNextPage()

            val failed = vm.await { it.homeLoadMoreFailed }
            assertNull(failed.homeError)
            assertEquals(listOf(1), failed.movies.map { it.id })
            assertEquals(listOf(history), failed.historyItems)
            assertEquals(1, failed.page)
            assertTrue(failed.canLoadMore)
            vm.loadNextPage()

            val result = vm.await { it.page == 2 }
            assertEquals(listOf(1, 2, 2), requests)
            assertEquals(listOf(1, 2), result.movies.map { it.id })
            assertFalse(result.homeLoadMoreFailed)
        }
    }

    @Test
    fun doubanPaginationKeepsResultsAndForcesAFreshRetry() = runBlocking {
        val requests = mutableListOf<Pair<Int, Boolean>>()
        val douban = Douban { page, refresh ->
            requests += page to refresh
            if (page == 2 && !refresh) throw IOException("Page failed")
            PagedDoubanHotMovies(page, 2, 2, listOf(DoubanHotItem("$page", "Show $page", "", 0.0, 0, "")))
        }

        withViewModel(douban = douban) { vm ->
            vm.await { it.doubanHotMovies.isNotEmpty() }
            vm.loadNextPage()

            val failed = vm.await { it.homeLoadMoreFailed }
            assertNull(failed.homeError)
            assertTrue(failed.isShowingDoubanHot)
            assertEquals(listOf("1"), failed.doubanHotMovies.map { it.doubanId })
            vm.loadNextPage()

            val result = vm.await { it.page == 2 }
            assertEquals(listOf(1 to false, 2 to false, 2 to true), requests)
            assertEquals(listOf("1", "2"), result.doubanHotMovies.map { it.doubanId })
            assertFalse(result.homeLoadMoreFailed)
        }
    }

    @Test
    fun liveRoomPaginationKeepsItsCategoryAndRetriesOnlyTheFailedPage() = runBlocking {
        val site = PlatformLiveSite("site", "Site", "")
        val parent = PlatformLiveParentCategory("parent", "Parent", "")
        val liveCategory = PlatformLiveCategory("category", "Category", parent.id, parent.name, "")
        val requests = mutableListOf<Int>()
        val live = object : PlatformLiveRepository by DefaultPlatformLiveRepository() {
            override suspend fun getCategoryTree(serviceUrl: String, site: String) =
                PlatformLiveCategoryTree(listOf(parent), listOf(liveCategory))

            override suspend fun getRooms(serviceUrl: String, site: String, categoryId: String, page: Int): PlatformLiveRoomsPage {
                requests += page
                if (requests.size == 2) throw IOException("Page failed")
                val room = PlatformLiveRoom("$page", site, "$page", "Room $page", "", "", 0, categoryId, "")
                return PlatformLiveRoomsPage(page, 2, listOf(room))
            }
        }

        withViewModel(platformLive = live) { vm ->
            vm.selectPlatformLiveSite(site)
            vm.await { it.platformLiveRooms.isNotEmpty() }
            vm.loadMorePlatformLiveRooms()

            val failed = vm.await { it.platformLiveLoadMoreFailed }
            assertEquals(PlatformLiveDestination.Rooms, failed.platformLiveDestination)
            assertEquals(liveCategory, failed.platformLiveSelectedCategory)
            assertNull(failed.platformLiveError)
            assertEquals(1, failed.platformLiveRoomPage)
            assertEquals(listOf("1"), failed.platformLiveRooms.map { it.roomId })
            vm.loadMorePlatformLiveRooms()

            val result = vm.await { it.platformLiveRoomPage == 2 }
            assertEquals(listOf(1, 2, 2), requests)
            assertEquals(listOf("1", "2"), result.platformLiveRooms.map { it.roomId })
            assertFalse(result.platformLiveLoadMoreFailed)
        }
    }

    @Test
    fun liveRoomSearchMatchesAnchorAndTitleAcrossPagesAndClearsInPlace() = runBlocking {
        val site = PlatformLiveSite("site", "Site", "")
        val parent = PlatformLiveParentCategory("parent", "Parent", "")
        val category = PlatformLiveCategory("arena", "Arena", parent.id, parent.name, "")
        val requests = mutableListOf<Int>()
        val live = object : PlatformLiveRepository by DefaultPlatformLiveRepository() {
            override suspend fun getCategoryTree(serviceUrl: String, site: String) =
                PlatformLiveCategoryTree(listOf(parent), listOf(category))

            override suspend fun getRooms(serviceUrl: String, site: String, categoryId: String, page: Int): PlatformLiveRoomsPage {
                requests += page
                assertEquals(category.id, categoryId)
                val anchor = if (page == 3) "VideoFox" else "Host $page"
                val title = if (page == 8) "Night FOX Show" else "Room $page"
                val room = PlatformLiveRoom("$page", site, "$page", title, anchor, "", 0, categoryId, "")
                return PlatformLiveRoomsPage(page, 8, listOf(room))
            }
        }

        withViewModel(platformLive = live) { vm ->
            vm.selectPlatformLiveSite(site)
            vm.await { !it.platformLiveLoading && it.platformLiveRooms.isNotEmpty() }
            vm.searchPlatformLiveRooms("  fox ")

            val firstBatch = vm.await { it.platformLiveRoomPage == 6 && !it.platformLiveLoadingMore }
            assertEquals("fox", firstBatch.platformLiveRoomQuery)
            assertEquals((1..6).toList(), requests)
            assertEquals(listOf("3"), firstBatch.visiblePlatformLiveRooms.map { it.id })
            vm.loadMorePlatformLiveRooms()

            val complete = vm.await { it.platformLiveRoomPage == 8 && !it.platformLiveLoadingMore }
            assertEquals(listOf("3", "8"), complete.visiblePlatformLiveRooms.map { it.id })
            vm.searchPlatformLiveRooms("")
            assertEquals((1..8).map(Int::toString), vm.state.value.visiblePlatformLiveRooms.map { it.id })
            assertEquals((1..8).toList(), requests)
        }
    }

    @Test
    fun bothLiveBrowsersRestoreTheCategoryAfterPlaybackAndFavorites() = runBlocking {
        val sites = listOf(PlatformLiveSite("first", "First", ""), PlatformLiveSite("second", "Second", ""))
        val empty = PlatformLiveParentCategory("empty", "Empty", "")
        val initial = PlatformLiveParentCategory("initial", "Initial", "")
        val another = PlatformLiveParentCategory("another", "Another", "")
        val start = PlatformLiveCategory("start", "Start", initial.id, initial.name, "")
        val firstChild = PlatformLiveCategory("child", "Child", another.id, another.name, "")
        val secondChild = PlatformLiveCategory("other-child", "Other child", another.id, another.name, "")
        val requests = mutableListOf<Pair<String, String>>()
        val live = object : PlatformLiveRepository by DefaultPlatformLiveRepository() {
            override suspend fun getSites(serviceUrl: String) = sites

            override suspend fun getCategoryTree(serviceUrl: String, site: String) =
                PlatformLiveCategoryTree(listOf(empty, initial, another), listOf(start, firstChild, secondChild))

            override suspend fun getRooms(serviceUrl: String, site: String, categoryId: String, page: Int): PlatformLiveRoomsPage {
                requests += site to categoryId
                val room = PlatformLiveRoom("$site:$categoryId", site, "room", "Room", "Anchor", "", 0, categoryId, "")
                return PlatformLiveRoomsPage(page, page, listOf(room))
            }

            override suspend fun resolve(
                serviceUrl: String,
                room: PlatformLiveRoom,
                forceRefresh: Boolean,
            ): ResolvedPlatformLiveStream = awaitCancellation()
        }

        for (theme in TvTheme.entries) {
            requests.clear()
            withViewModel(platformLive = live) { vm ->
                vm.await { !it.homeLoading && it.movies.isNotEmpty() }
                vm.updateTheme(theme)
                vm.openPlatformLive()
                val opened = vm.await {
                    it.platformLiveDestination == PlatformLiveDestination.Rooms && it.platformLiveRooms.isNotEmpty()
                }
                assertEquals(sites.first(), opened.platformLiveSelectedSite)
                assertEquals(initial, opened.platformLiveSelectedParentCategory)
                assertEquals(start, opened.platformLiveSelectedCategory)
                val openedRoom = opened.platformLiveRooms.single()
                vm.openPlatformLiveRoom(openedRoom)
                assertEquals(PlatformLiveDestination.Player, vm.state.value.platformLiveDestination)
                vm.goBack()
                val fromPlayer = vm.state.value
                assertEquals(PlatformLiveDestination.Rooms, fromPlayer.platformLiveDestination)
                assertEquals(openedRoom, fromPlayer.platformLiveRooms[fromPlayer.platformLiveRoomIndex])

                vm.selectPlatformLiveParentCategory(another)
                vm.await { it.platformLiveSelectedCategory == firstChild && !it.platformLiveLoading }
                vm.selectPlatformLiveCategory(secondChild)
                vm.await { it.platformLiveSelectedCategory == secondChild && !it.platformLiveLoading }
                vm.openPlatformLiveFavorites()
                assertEquals(PlatformLiveDestination.Favorites, vm.state.value.platformLiveDestination)
                vm.goBack()
                val restored = vm.await {
                    it.platformLiveDestination == PlatformLiveDestination.Rooms && it.platformLiveRooms.isNotEmpty()
                }
                assertEquals(another, restored.platformLiveSelectedParentCategory)
                assertEquals(secondChild, restored.platformLiveSelectedCategory)

                vm.selectPlatformLiveSite(sites.last())
                vm.await { it.platformLiveSelectedSite == sites.last() && it.platformLiveRooms.isNotEmpty() }
                vm.goBack()
                assertEquals(TvScreen.Home, vm.state.value.screen)
                vm.openPlatformLive()
                val reopened = vm.await { it.platformLiveDestination == PlatformLiveDestination.Rooms && !it.platformLiveLoading }
                assertEquals(sites.last(), reopened.platformLiveSelectedSite)
                assertEquals(start, reopened.platformLiveSelectedCategory)
                assertEquals(
                    listOf("first" to "start", "first" to "child", "first" to "other-child", "first" to "other-child",
                        "second" to "start", "second" to "start"),
                    requests,
                )
            }
        }
    }

    private suspend fun CoroutineScope.withViewModel(
        movies: Movies = Movies(),
        douban: DoubanHotRepository = Douban { _, _ -> throw IOException("Douban unavailable") },
        platformLive: PlatformLiveRepository = DefaultPlatformLiveRepository(),
        test: suspend (TvBoxViewModel) -> Unit,
    ) {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val vm = TvBoxViewModel(
                repository = movies,
                doubanHotRepository = douban,
                platformLiveRepository = platformLive,
                historyRepository = object : HistoryRepository {
                    override suspend fun getHistory() = listOf(history)
                    override suspend fun saveProgress(item: WatchHistoryItem) = listOf(item)
                    override suspend fun clearHistory() = emptyList<WatchHistoryItem>()
                },
                platformLiveFavoritesRepository = object : PlatformLiveFavoritesRepository {
                    override suspend fun getFavorites() = emptyList<PlatformLiveRoom>()
                    override suspend fun toggleFavorite(room: PlatformLiveRoom) = listOf(room)
                },
                platformLiveServiceUrl = "https://test.invalid/",
                scope = scope,
            )
            test(vm)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun TvBoxViewModel.await(predicate: (TvBoxUiState) -> Boolean): TvBoxUiState =
        withTimeout(2_000L) { state.first { predicate(it) } }

    private class Douban(private val load: suspend (Int, Boolean) -> PagedDoubanHotMovies) : DoubanHotRepository {
        override suspend fun getRecentHot(page: Int, forceRefresh: Boolean, category: DoubanHotCategory) =
            load(page, forceRefresh)
    }

    private class Movies : MovieRepository {
        override val apiLines = listOf(apiLine)
        var catalog: suspend (Int) -> PagedMovies =
            { page -> PagedMovies(page, 1, 1, apiLine, listOf(category), listOf(movie())) }
        var detail: (String, Int) -> Flow<MovieDetailUpdate> = { _, _ -> emptyFlow() }
        var resume: suspend (String, Int) -> Movie? = { _, _ -> null }
        var search: (String, Boolean) -> Flow<MultiSourceSearchUpdate> = { _, _ -> emptyFlow() }

        override fun updateCustomApiLines(customApiLines: List<ApiLine>) = Unit
        override suspend fun getCategories(apiLineId: String) = listOf(category)
        override suspend fun getMovies(apiLineId: String, page: Int, typeId: Int?, keyword: String?) = catalog(page)
        override suspend fun getMoviesByTypeIds(apiLineId: String, page: Int, typeIds: List<Int>) = catalog(page)
        override suspend fun getDetail(apiLineId: String, id: Int): Movie? = null
        override suspend fun getResumeDetail(apiLineId: String, id: Int) = resume(apiLineId, id)
        override fun getDetailProgressively(apiLineId: String, id: Int) = detail(apiLineId, id)
        override fun searchProgressively(apiLineId: String, page: Int, keyword: String, retryUnavailableSources: Boolean) =
            search(keyword, retryUnavailableSources)
    }

    private companion object {
        val apiLine = ApiLine("liangzi", "Primary", listOf("https://test.invalid/"))
        val category = Category(1, 0, "TV")

        fun playlist(id: String) = PlaySource(
            name = id,
            episodes = (1..6).map { PlayEpisode("Episode $it", "https://test.invalid/$id/$it") },
            lineId = id,
        )

        fun movie(id: Int = 7, sources: List<PlaySource> = listOf(playlist("primary"))) = Movie(
            id = id,
            apiLineId = apiLine.id,
            apiLineName = apiLine.name,
            name = "Show $id",
            typeId = category.id,
            typeName = category.name,
            posterUrl = "",
            remarks = "",
            year = "",
            area = "",
            language = "",
            actor = "",
            director = "",
            duration = "",
            description = "",
            playSources = sources,
        )

        val history = WatchHistoryItem(
            movieId = 7, apiLineId = apiLine.id, apiLineName = apiLine.name, movieName = "Show 7",
            posterUrl = "", typeName = category.name, remarks = "", sourceIndex = 0, sourceName = "resume",
            episodeIndex = 4, episodeTitle = "Episode 5", episodeUrl = "https://test.invalid/resume/5",
            positionMs = 50_000L, durationMs = 100_000L, updatedAtEpochMs = 1L,
        )
    }
}
