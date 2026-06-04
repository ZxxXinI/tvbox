package com.tvbox.app.data

import com.tvbox.app.domain.Category
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.PagedMovies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MovieRepository {
    suspend fun getCategories(): List<Category>
    suspend fun getMovies(page: Int, typeId: Int? = null, keyword: String? = null): PagedMovies
    suspend fun getDetail(id: Int): Movie?
}

class DefaultMovieRepository(
    private val api: MacCmsApi = MacCmsNetwork.api,
) : MovieRepository {
    override suspend fun getCategories(): List<Category> = withContext(Dispatchers.IO) {
        api.getVod(action = "list").categories.mapNotNull { it.toDomainOrNull() }
    }

    override suspend fun getMovies(page: Int, typeId: Int?, keyword: String?): PagedMovies = withContext(Dispatchers.IO) {
        api.getVod(
            action = "videolist",
            page = page.coerceAtLeast(1),
            typeId = typeId,
            keyword = keyword?.takeIf { it.isNotBlank() },
        ).toPagedMovies()
    }

    override suspend fun getDetail(id: Int): Movie? = withContext(Dispatchers.IO) {
        api.getVod(action = "videolist", ids = id.toString())
            .list
            .firstOrNull()
            ?.toDomainOrNull()
    }
}

