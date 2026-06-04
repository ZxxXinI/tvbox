package com.tvbox.app.domain

private val htmlTagRegex = Regex("<[^>]+>")
private val whitespaceRegex = Regex("\\s+")

fun parsePlaySources(playFrom: String?, playUrl: String?): List<PlaySource> {
    val sourceNames = playFrom.orEmpty()
        .split("$$$")
        .mapIndexed { index, name -> name.trim().ifBlank { "source-${index + 1}" } }

    val urlGroups = playUrl.orEmpty().split("$$$")
    val count = maxOf(sourceNames.size, urlGroups.size)
    if (count == 0) return emptyList()

    return (0 until count).mapNotNull { index ->
        val name = sourceNames.getOrNull(index) ?: "source-${index + 1}"
        val episodes = parseEpisodes(urlGroups.getOrNull(index).orEmpty())
        if (episodes.isEmpty()) null else PlaySource(name = name, episodes = episodes)
    }
}

private fun parseEpisodes(rawGroup: String): List<PlayEpisode> {
    return rawGroup.split("#")
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { segment ->
            val delimiterIndex = segment.indexOf('$')
            val (title, rawUrl) = if (delimiterIndex >= 0) {
                segment.substring(0, delimiterIndex).trim() to segment.substring(delimiterIndex + 1).trim()
            } else {
                "播放" to segment
            }
            val url = normalizePlayableUrl(rawUrl)
            if (url.isBlank()) null else PlayEpisode(title = title.ifBlank { "播放" }, url = url)
        }
        .toList()
}

private fun normalizePlayableUrl(rawUrl: String): String {
    val markdownUrl = Regex("""\((https?://[^)]+)\)""").find(rawUrl)?.groupValues?.getOrNull(1)
    val bracketUrl = Regex("""\[(https?://[^]]+)]""").find(rawUrl)?.groupValues?.getOrNull(1)
    return (markdownUrl ?: bracketUrl ?: rawUrl)
        .trim()
        .trim('[', ']')
}

fun cleanHtml(input: String?): String {
    if (input.isNullOrBlank()) return ""
    return input
        .replace(htmlTagRegex, " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(whitespaceRegex, " ")
        .trim()
}

