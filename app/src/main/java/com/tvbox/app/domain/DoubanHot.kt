package com.tvbox.app.domain

data class DoubanHotItem(
    val doubanId: String,
    val title: String,
    val posterUrl: String,
    val score: Double,
    val ratingCount: Int,
    val subtitle: String,
)

data class PagedDoubanHotMovies(
    val page: Int,
    val pageCount: Int,
    val total: Int,
    val items: List<DoubanHotItem>,
)

fun findBestTitleMatchIndex(query: String, candidates: List<String>): Int? {
    val normalizedQuery = query.normalizeTitleForLookup()
    if (normalizedQuery.isBlank()) return null

    candidates.indexOfFirst { it.normalizeTitleForLookup() == normalizedQuery }
        .takeIf { it >= 0 }
        ?.let { return it }

    return candidates.indexOfFirst { candidate ->
        val normalizedCandidate = candidate.normalizeTitleForLookup()
        normalizedCandidate.isNotBlank() &&
            (normalizedCandidate.contains(normalizedQuery) || normalizedQuery.contains(normalizedCandidate))
    }.takeIf { it >= 0 }
}

private fun String.normalizeTitleForLookup(): String = lowercase().filter { it.isLetterOrDigit() }
