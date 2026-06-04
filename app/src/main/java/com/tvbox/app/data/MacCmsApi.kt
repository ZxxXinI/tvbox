package com.tvbox.app.data

import com.tvbox.app.domain.ApiLine
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface MacCmsApi {
    @GET(".")
    suspend fun getVod(
        @Query("ac") action: String,
        @Query("pg") page: Int? = null,
        @Query("t") typeId: Int? = null,
        @Query("wd") keyword: String? = null,
        @Query("h") hours: Int? = null,
        @Query("ids") ids: String? = null,
    ): MacCmsResponse
}

object MacCmsNetwork {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val apis = mutableMapOf<String, MacCmsApi>()

    fun api(baseUrl: String): MacCmsApi {
        return apis.getOrPut(baseUrl) {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(MacCmsApi::class.java)
        }
    }
}

object ApiLines {
    val defaults = listOf(
        ApiLine(
            id = "ruyi",
            name = "如意",
            baseUrls = listOf("https://cj.rycjapi.com/api.php/provide/vod/"),
        ),
    )
}
