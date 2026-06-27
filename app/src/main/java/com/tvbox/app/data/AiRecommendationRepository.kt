package com.tvbox.app.data

import com.tvbox.app.domain.AiRecommendationResult
import com.tvbox.app.domain.parseAiRecommendationContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

interface AiRecommendationRepository {
    suspend fun getRecommendations(query: String): AiRecommendationResult
}

class DefaultAiRecommendationRepository(
    private val apiKey: String,
    private val client: OkHttpClient = defaultAiClient,
) : AiRecommendationRepository {
    override suspend fun getRecommendations(query: String): AiRecommendationResult = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        require(trimmedQuery.isNotBlank()) { "请输入找片需求" }
        require(apiKey.isNotBlank()) { "请先在 local.properties 配置 TVBOX_AI_API_KEY" }

        val requestBody = ChatCompletionRequest(
            model = AI_MODEL,
            messages = listOf(
                ChatMessage(role = "system", content = buildSystemPrompt()),
                ChatMessage(role = "user", content = trimmedQuery),
            ),
            temperature = 0.4,
            maxTokens = 1200,
        )
        val request = Request.Builder()
            .url(AI_CHAT_COMPLETIONS_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(aiJson.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw IllegalStateException("AI 请求失败：HTTP ${response.code}")
            }
            val content = body.extractAssistantContent()
            parseAiRecommendationContent(content)
        }
    }

    private fun buildSystemPrompt(): String {
        return """
            [角色]
            你是 TVBox 的 AI 观影助手，擅长把用户一句话找片需求转成可搜索的影视推荐列表。

            [任务]
            根据用户需求推荐 6 到 8 部影视作品，并为每部作品给出可用于影视资源站搜索的关键词。

            [上下文]
            用户在 Android TV 应用中找片，应用会拿你的 searchKeyword 去影视资源站搜索可播放资源。
            用户可能会说“悬疑电视剧推荐”“最近高分电影”“适合小孩看的动画电影”等。

            [要求]
            优先推荐口碑较好、热度较高、比较容易在资源站搜索到的作品。
            如果用户提到“最近”，优先选择近几年作品。
            不要推荐伦理、电影解说、新闻资讯、演员资料。
            reason 用一句简短中文说明推荐理由。
            searchKeyword 只写片名，不要带年份、季数、标点或额外说明。
            不要输出 Markdown，不要输出解释性文字。

            [输出格式]
            只输出 JSON，格式如下：
            {
              "title": "为你推荐的悬疑电视剧",
              "items": [
                {
                  "name": "片名",
                  "year": "年份",
                  "area": "地区",
                  "type": "类型",
                  "reason": "推荐理由",
                  "score": "评分或口碑描述",
                  "searchKeyword": "片名"
                }
              ]
            }
        """.trimIndent()
    }
}

private fun String.extractAssistantContent(): String {
    val root = aiJson.parseToJsonElement(this).jsonObject
    return root["choices"]
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("message")
        ?.jsonObject
        ?.get("content")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("AI 响应为空")
}

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    @SerialName("max_tokens") val maxTokens: Int,
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
)

private val defaultAiClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .build()

private val aiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private const val AI_CHAT_COMPLETIONS_URL = "https://apihub.agnes-ai.com/v1/chat/completions"
private const val AI_MODEL = "agnes-2.0-flash"
