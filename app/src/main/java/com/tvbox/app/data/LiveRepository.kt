package com.tvbox.app.data

import com.tvbox.app.domain.LiveChannel
import com.tvbox.app.domain.parseLiveChannels
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
    private val sourceUrl: String = LIVE_SOURCE_URL,
) : LiveRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun getChannels(): List<LiveChannel> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(sourceUrl)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("直播源加载失败：${response.code}")
            }
            parseLiveChannels(response.body.string())
        }
    }
}

private const val LIVE_SOURCE_URL = "https://nav.zhenxx.de5.net/iptv.txt"
