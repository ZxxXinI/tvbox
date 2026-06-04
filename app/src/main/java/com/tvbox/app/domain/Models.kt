package com.tvbox.app.domain

data class Category(
    val id: Int,
    val name: String,
)

data class Movie(
    val id: Int,
    val name: String,
    val typeId: Int,
    val typeName: String,
    val posterUrl: String,
    val remarks: String,
    val year: String,
    val area: String,
    val language: String,
    val actor: String,
    val director: String,
    val duration: String,
    val description: String,
    val playSources: List<PlaySource>,
) {
    val subtitle: String
        get() = listOf(year, area, language, remarks)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" / ")

    fun preferredSourceIndex(): Int {
        val m3u8Index = playSources.indexOfFirst {
            it.episodes.isNotEmpty() && it.name.contains("m3u8", ignoreCase = true)
        }
        if (m3u8Index >= 0) return m3u8Index
        return playSources.indexOfFirst { it.episodes.isNotEmpty() }.coerceAtLeast(0)
    }
}

data class PlaySource(
    val name: String,
    val episodes: List<PlayEpisode>,
)

data class PlayEpisode(
    val title: String,
    val url: String,
)

data class PagedMovies(
    val page: Int,
    val pageCount: Int,
    val total: Int,
    val categories: List<Category>,
    val movies: List<Movie>,
)

