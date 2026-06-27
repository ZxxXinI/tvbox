package com.tvbox.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AiRecommendationResult(
    val title: String,
    val items: List<AiRecommendationItem>,
)

data class AiRecommendationItem(
    val name: String,
    val year: String,
    val area: String,
    val genre: String,
    val reason: String,
    val score: String,
    val searchKeyword: String,
)

internal fun parseAiRecommendationContent(
    raw: String,
    json: Json = aiRecommendationJson,
): AiRecommendationResult {
    val root = json.parseToJsonElement(raw.extractJsonObject()).jsonObject
    val title = root.stringField("title").ifBlank { "AI 推荐" }
    val items = root["items"]
        ?.jsonArray
        .orEmpty()
        .mapNotNull { element ->
            val item = element.jsonObject
            val name = item.stringField("name").ifBlank { item.stringField("title") }
            if (name.isBlank()) return@mapNotNull null
            AiRecommendationItem(
                name = name,
                year = item.stringField("year"),
                area = item.stringField("area"),
                genre = item.stringField("type").ifBlank { item.stringField("genre") },
                reason = item.stringField("reason"),
                score = item.stringField("score"),
                searchKeyword = item.stringField("searchKeyword").ifBlank { name },
            )
        }
        .take(MAX_AI_RECOMMENDATION_ITEMS)

    return AiRecommendationResult(title = title, items = items)
}

private fun String.extractJsonObject(): String {
    val withoutFence = trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = withoutFence.indexOf('{')
    val end = withoutFence.lastIndexOf('}')
    require(start >= 0 && end > start) { "AI 推荐结果格式不正确" }
    return withoutFence.substring(start, end + 1)
}

private fun JsonObject.stringField(name: String): String {
    return get(name)?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
}

private val aiRecommendationJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

private const val MAX_AI_RECOMMENDATION_ITEMS = 10
