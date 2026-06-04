package com.tvbox.app.domain

private val blockedKeywords = listOf("伦理", "电影解说")

fun isBlockedContent(vararg fields: String?): Boolean {
    return fields.any { field ->
        val value = field.orEmpty()
        blockedKeywords.any { keyword -> value.contains(keyword, ignoreCase = true) }
    }
}
