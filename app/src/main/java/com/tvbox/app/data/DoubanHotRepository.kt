package com.tvbox.app.data

import android.content.Context
import com.tvbox.app.domain.DoubanHotItem
import com.tvbox.app.domain.PagedDoubanHotMovies
import com.tvbox.app.domain.isBlockedContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

interface DoubanHotRepository {
    suspend fun getRecentHot(
        page: Int,
        forceRefresh: Boolean = false,
        category: DoubanHotCategory = DoubanHotCategory.Tv,
    ): PagedDoubanHotMovies
}

enum class DoubanHotCategory(
    val category: String,
    val type: String,
) {
    Tv(category = "tv", type = "tv"),
}

class DefaultDoubanHotRepository(
    private val cache: DoubanHotCache = InMemoryDoubanHotCache(),
    private val client: OkHttpClient = defaultDoubanClient,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : DoubanHotRepository {
    override suspend fun getRecentHot(
        page: Int,
        forceRefresh: Boolean,
        category: DoubanHotCategory,
    ): PagedDoubanHotMovies = withContext(Dispatchers.IO) {
        val request = DoubanHotRequest(page = page, category = category)
        val cacheKey = request.cacheKey
        val now = nowMs()
        if (!forceRefresh) {
            cache.get(cacheKey, now)?.let { cached ->
                if (cached.isFailure) throw IOException(cached.payload)
                return@withContext parseDoubanHotPage(cached.payload, request)
            }
        }

        runCatching {
            val rawResponse = client.newCall(request.toOkHttpRequest()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("豆瓣热播暂时不可用（${response.code}）")
                }
                response.body.string()
            }
            cache.put(
                key = cacheKey,
                record = DoubanHotCacheRecord(
                    payload = rawResponse,
                    expiresAtEpochMs = now + successCacheTtlMs(cacheKey),
                ),
            )
            parseDoubanHotPage(rawResponse, request)
        }.getOrElse { error ->
            val message = error.message?.takeIf { it.isNotBlank() } ?: "豆瓣热播暂时不可用，请稍后重试"
            cache.put(
                key = cacheKey,
                record = DoubanHotCacheRecord(
                    payload = message,
                    expiresAtEpochMs = now + FAILURE_CACHE_TTL_MS,
                    isFailure = true,
                ),
            )
            throw IOException(message, error)
        }
    }
}

interface DoubanHotCache {
    fun get(key: String, nowMs: Long): DoubanHotCacheRecord?
    fun put(key: String, record: DoubanHotCacheRecord)
}

@Serializable
data class DoubanHotCacheRecord(
    val payload: String,
    val expiresAtEpochMs: Long,
    val isFailure: Boolean = false,
)

class SharedDoubanHotCache(context: Context) : DoubanHotCache {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override fun get(key: String, nowMs: Long): DoubanHotCacheRecord? {
        val raw = preferences.getString(key, null).orEmpty()
        val record = runCatching { json.decodeFromString<DoubanHotCacheRecord>(raw) }.getOrNull() ?: return null
        if (record.expiresAtEpochMs > nowMs) return record
        preferences.edit().remove(key).apply()
        return null
    }

    override fun put(key: String, record: DoubanHotCacheRecord) {
        preferences.edit().putString(key, json.encodeToString(record)).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "douban_hot_cache"
    }
}

class InMemoryDoubanHotCache : DoubanHotCache {
    private val entries = mutableMapOf<String, DoubanHotCacheRecord>()

    override fun get(key: String, nowMs: Long): DoubanHotCacheRecord? {
        val record = entries[key] ?: return null
        return record.takeIf { it.expiresAtEpochMs > nowMs } ?: run {
            entries.remove(key)
            null
        }
    }

    override fun put(key: String, record: DoubanHotCacheRecord) {
        entries[key] = record
    }
}

internal data class DoubanHotRequest(
    val page: Int,
    val category: DoubanHotCategory = DoubanHotCategory.Tv,
    val limit: Int = DOUBAN_PAGE_SIZE,
) {
    val normalizedPage: Int = page.coerceAtLeast(1)
    val start: Int = (normalizedPage - 1) * limit
    val cacheKey: String = "douban-hot:${category.category}:${category.type}:start:$start:limit:$limit"

    fun toOkHttpRequest(): Request {
        val url = DOUBAN_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("start", start.toString())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("category", category.category)
            .addQueryParameter("type", category.type)
            .build()
        return Request.Builder()
            .url(url)
            .header("User-Agent", DOUBAN_USER_AGENT)
            .header("Referer", DOUBAN_REFERER)
            .header("Origin", DOUBAN_ORIGIN)
            .header("Accept", "application/json, text/plain, */*")
            .build()
    }
}

internal fun parseDoubanHotPage(rawResponse: String, request: DoubanHotRequest): PagedDoubanHotMovies {
    val response = doubanJson.decodeFromString<DoubanHotResponse>(rawResponse)
    val total = response.total.coerceAtLeast(0)
    val pageCount = maxOf(1, ceil(total / request.limit.toDouble()).toInt())
    return PagedDoubanHotMovies(
        page = request.normalizedPage,
        pageCount = pageCount,
        total = total,
        items = response.items.mapNotNull { item -> item.toDomainOrNull() },
    )
}

@Serializable
private data class DoubanHotResponse(
    val total: Int = 0,
    val items: List<DoubanHotItemDto> = emptyList(),
)

@Serializable
private data class DoubanHotItemDto(
    val id: String = "",
    val title: String = "",
    val pic: DoubanPictureDto? = null,
    val rating: DoubanRatingDto? = null,
    @SerialName("card_subtitle") val cardSubtitle: String = "",
) {
    fun toDomainOrNull(): DoubanHotItem? {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank() || isBlockedContent(normalizedTitle, cardSubtitle)) return null
        return DoubanHotItem(
            doubanId = id.trim(),
            title = normalizedTitle,
            posterUrl = pic?.let { image ->
                image.large.ifBlank { image.normal }
            }.orEmpty().trim(),
            score = rating?.value?.takeIf { it > 0.0 } ?: 0.0,
            ratingCount = rating?.count?.coerceAtLeast(0) ?: 0,
            subtitle = cardSubtitle.trim(),
        )
    }
}

@Serializable
private data class DoubanPictureDto(
    val large: String = "",
    val normal: String = "",
)

@Serializable
private data class DoubanRatingDto(
    val count: Int = 0,
    val value: Double = 0.0,
)

private fun successCacheTtlMs(cacheKey: String): Long {
    val offsetMinutes = 10L + ((cacheKey.hashCode().toLong() and Int.MAX_VALUE.toLong()) % 11L)
    return SUCCESS_CACHE_TTL_MS + offsetMinutes * MINUTE_MS
}

internal const val DOUBAN_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 9; TVBox) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
internal const val DOUBAN_REFERER = "https://movie.douban.com/tv/"
private const val DOUBAN_ORIGIN = "https://movie.douban.com"
private const val DOUBAN_ENDPOINT = "https://m.douban.com/rexxar/api/v2/subject/recent_hot/tv"
private const val DOUBAN_PAGE_SIZE = 20
private const val MINUTE_MS = 60_000L
private const val SUCCESS_CACHE_TTL_MS = 20L * 60L * MINUTE_MS
private const val FAILURE_CACHE_TTL_MS = 6L * MINUTE_MS

private val doubanJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

private val defaultDoubanClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .build()
