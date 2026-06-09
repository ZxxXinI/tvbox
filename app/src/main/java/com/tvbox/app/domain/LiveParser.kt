package com.tvbox.app.domain

fun parseLiveChannels(raw: String): List<LiveChannel> {
    return raw
        .lineSequence()
        .mapNotNull { line -> line.toLiveChannelOrNull() }
        .distinctBy { "${it.name}-${it.url}" }
        .mapIndexed { index, channel -> channel.copy(number = index + 1) }
        .toList()
}

private fun String.toLiveChannelOrNull(): LiveChannel? {
    val line = trim()
    if (line.isBlank()) return null
    val commaIndex = line.indexOf(',')
    if (commaIndex <= 0 || commaIndex == line.lastIndex) return null

    val name = cleanHtml(line.substring(0, commaIndex))
        .trim()
        .replace(Regex("^\\d+(?=CCTV-)"), "")
    val url = line.substring(commaIndex + 1).trim()
    if (name.isBlank() || !url.startsWith("http://") && !url.startsWith("https://")) return null

    return LiveChannel(number = 0, name = name, url = url)
}
