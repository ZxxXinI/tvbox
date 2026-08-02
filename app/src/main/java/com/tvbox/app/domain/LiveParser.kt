package com.tvbox.app.domain

fun parseLiveChannels(raw: String): List<LiveChannel> {
    val channels = linkedMapOf<LiveChannelKey, MutableList<LiveChannelLine>>()
    var currentGroupName: String? = null

    raw.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isBlank()) return@forEach

        val commaIndex = line.indexOf(',')
        if (commaIndex <= 0 || commaIndex == line.lastIndex) return@forEach

        val name = cleanHtml(line.substring(0, commaIndex))
            .trim()
            .replace(Regex("^\\d+(?=CCTV-)"), "")
        val sourcePart = line.substring(commaIndex + 1).trim()
        if (name.isBlank()) return@forEach

        if (sourcePart.equals("#genre#", ignoreCase = true)) {
            currentGroupName = name
            return@forEach
        }

        val groupName = currentGroupName ?: return@forEach
        val parsedLine = sourcePart.toLiveChannelLineOrNull(channelName = name) ?: return@forEach
        val key = LiveChannelKey(groupName = groupName, channelName = parsedLine.channelName)
        val lines = channels.getOrPut(key) { mutableListOf() }
        if (lines.none { it.url == parsedLine.url }) {
            lines += LiveChannelLine(name = parsedLine.lineName, url = parsedLine.url)
        }
    }

    return channels.entries
        .filter { (_, lines) -> lines.isNotEmpty() }
        .mapIndexed { index, (key, lines) ->
            LiveChannel(
                number = index + 1,
                groupName = key.groupName,
                name = key.channelName,
                lines = lines.toList(),
            )
        }
}

private data class LiveChannelKey(
    val groupName: String,
    val channelName: String,
)

private data class ParsedLiveChannelLine(
    val channelName: String,
    val lineName: String,
    val url: String,
)

private fun String.toLiveChannelLineOrNull(channelName: String): ParsedLiveChannelLine? {
    val metadataIndex = indexOf('$')
    val url = substringBefore('$').trim()
    val lineName = if (metadataIndex >= 0 && metadataIndex < lastIndex) {
        substring(metadataIndex + 1)
            .substringBefore('$')
            .trim()
            .takeIf { it.startsWith(LIVE_LINE_METADATA_PREFIX) }
            .orEmpty()
    } else {
        ""
    }
    if (!url.startsWith("http://") && !url.startsWith("https://")) return null

    return ParsedLiveChannelLine(
        channelName = channelName,
        lineName = lineName,
        url = url,
    )
}

private const val LIVE_LINE_METADATA_PREFIX = "LR•IPV4•29『线路"
