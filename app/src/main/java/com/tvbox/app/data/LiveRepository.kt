package com.tvbox.app.data

import com.tvbox.app.domain.LiveChannel
import com.tvbox.app.domain.parseLiveChannels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

interface LiveRepository {
    suspend fun getChannels(): List<LiveChannel>
}

class DefaultLiveRepository(
    private val sourceUrls: List<String> = LIVE_SOURCE_URLS,
) : LiveRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun getChannels(): List<LiveChannel> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        sourceUrls.forEach { sourceUrl ->
            try {
                val request = Request.Builder()
                    .url(sourceUrl)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("直播源加载失败：${response.code}")
                    }
                    val channels = parseLiveChannels(response.body.string())
                    if (channels.isNotEmpty()) return@withContext channels
                    throw IOException("直播源未返回可用频道")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw IOException("所有直播源均加载失败", lastError)
    }
}

private val LIVE_SOURCE_URLS = listOf(
    "http://20.205.10.127:8787/tvbox/result.txt",
    "https://tv.iill.top/m3u/Gather",
)
