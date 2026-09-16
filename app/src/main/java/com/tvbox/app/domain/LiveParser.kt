package com.tvbox.app.domain

fun parseLiveChannels(raw: String): List<LiveChannel> {
    if (raw.lineSequence().any { it.trimStart().startsWith("#EXTINF", ignoreCase = true) }) {
        return parseM3uLiveChannels(raw)
    }

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

private fun parseM3uLiveChannels(raw: String): List<LiveChannel> {
    val channels = linkedMapOf<LiveChannelKey, MutableList<LiveChannelLine>>()
    var pendingChannel: M3uChannelMetadata? = null

    raw.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                pendingChannel = line.toM3uChannelMetadata()
            }
            line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true) -> {
                val metadata = pendingChannel ?: return@forEach
                val key = LiveChannelKey(
                    groupName = metadata.groupName,
                    channelName = metadata.channelName,
                )
                val lines = channels.getOrPut(key) { mutableListOf() }
                if (lines.none { it.url == line }) {
                    lines += LiveChannelLine(
                        name = "线路${lines.size + 1}",
                        url = line,
                    )
                }
                pendingChannel = null
            }
            line.isNotBlank() && !line.startsWith("#") -> pendingChannel = null
        }
    }

    return channels.entries.mapIndexed { index, (key, lines) ->
        LiveChannel(
            number = index + 1,
            groupName = key.groupName,
            name = key.channelName,
            lines = lines.toList(),
        )
    }
}

private fun String.toM3uChannelMetadata(): M3uChannelMetadata? {
    val displayName = substringAfter(',', missingDelimiterValue = "").trim()
    val channelName = extractM3uAttribute("tvg-name")
        .ifBlank { displayName }
        .ifBlank { return null }
    return M3uChannelMetadata(
        groupName = extractM3uAttribute("group-title").ifBlank { "其他" },
        channelName = cleanHtml(channelName).trim(),
    )
}

private fun String.extractM3uAttribute(name: String): String {
    val match = Regex("(?:^|\\s)${Regex.escape(name)}=\\\"([^\\\"]*)\\\"", RegexOption.IGNORE_CASE)
        .find(this)
    return match?.groupValues?.getOrNull(1).orEmpty().trim()
}

private data class LiveChannelKey(
    val groupName: String,
    val channelName: String,
)

private data class M3uChannelMetadata(
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
