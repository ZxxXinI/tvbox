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

private fun episodeNumber(title: String): Int? =
    Regex("^(?:第\\s*|EP(?:ISODE)?\\s*)?0*(\\d+)(?:\\s*[集话期])?$", RegexOption.IGNORE_CASE)
        .matchEntire(title.trim())?.groupValues?.get(1)?.toIntOrNull()
