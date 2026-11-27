package com.tvbox.app.domain

fun correspondingEpisodeIndex(source: PlaySource, title: String, fallbackIndex: Int): Int? {
    val exact = source.episodes.indexOfFirst { it.title == title }
    if (exact >= 0) return exact
    val number = episodeNumber(title)
    if (number != null && source.episodes.any { episodeNumber(it.title) != null }) {
        return source.episodes.indexOfFirst { episodeNumber(it.title) == number }.takeIf { it >= 0 }
    }
    return fallbackIndex.takeIf { it in source.episodes.indices }
}

internal fun PlaySource.resumeEpisodeIndex(item: WatchHistoryItem): Int? {
    val available = episodes.withIndex().filter { it.value.url.isNotBlank() }
    val byUrl = available.firstOrNull { it.value.url == item.episodeUrl }
    if (byUrl != null) return byUrl.index

    val episodeName = normalizeResumeTitle(item.episodeTitle)
    if (episodeName.isEmpty()) return null
    val byName = available.filter { normalizeResumeTitle(it.value.title) == episodeName }
    if (byName.isNotEmpty()) return byName.distinctBy { it.value.url }.singleOrNull()?.index

    val number = episodeNumber(item.episodeTitle) ?: return null
    return available.filter { episodeNumber(it.value.title) == number }
        .distinctBy { it.value.url }.singleOrNull()?.index
}

internal fun normalizeResumeTitle(title: String): String = title.lowercase().filter { it.isLetterOrDigit() }

private fun episodeNumber(title: String): Int? =
    Regex("^(?:第\\s*|EP(?:ISODE)?\\s*)?0*(\\d+)(?:\\s*[集话期])?$", RegexOption.IGNORE_CASE)
        .matchEntire(title.trim())?.groupValues?.get(1)?.toIntOrNull()
