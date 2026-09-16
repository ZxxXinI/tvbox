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
            id = "liangzi",
            name = "量子",
            baseUrls = listOf("https://cj.lziapi.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "ruyi",
            name = "如意",
            baseUrls = listOf("https://cj.rycjapi.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "360",
            name = "360",
            baseUrls = listOf(
                "https://360zyzz.com/api.php/provide/vod/",
                "https://360zy.com/api.php/provide/vod/",
            ),
        ),
        ApiLine(
            id = "dyttzy",
            name = "电影天堂",
            baseUrls = listOf("http://caiji.dyttzyapi.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "bfzy",
            name = "暴风",
            baseUrls = listOf("https://bfzyapi.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "tyyszy",
            name = "天涯",
            baseUrls = listOf("https://tyyszy.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "maotaizy",
            name = "茅台",
            baseUrls = listOf("https://caiji.maotaizy.cc/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "wolong",
            name = "卧龙",
            baseUrls = listOf("https://wolongzyw.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "jisu",
            name = "极速",
            baseUrls = listOf("https://jszyapi.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "dbzy",
            name = "豆瓣",
            baseUrls = listOf("https://dbzy.tv/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "mozhua",
            name = "魔爪",
            baseUrls = listOf("https://mozhuazy.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "mdzy",
            name = "魔都",
            baseUrls = listOf("https://www.mdzyapi.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "zuid",
            name = "最大",
            baseUrls = listOf("https://api.zuidapi.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "yinghua",
            name = "樱花",
            baseUrls = listOf("https://m3u8.apiyhzy.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "wujin",
            name = "无尽",
            baseUrls = listOf("https://api.wujinapi.me/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "wwzy",
            name = "旺旺短剧",
            baseUrls = listOf("https://wwzy.tv/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "ikun",
            name = "iKun",
            baseUrls = listOf("https://ikunzyapi.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "bdzy",
            name = "百度",
            baseUrls = listOf("https://api.apibdzy.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "xinlangaa",
            name = "新浪",
            baseUrls = listOf("https://api.xinlangapi.com/xinlangapi.php/provide/vod/"),
        ),
        ApiLine(
            id = "ckzy",
            name = "CK",
            baseUrls = listOf("https://ckzy.me/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "ukuapi",
            name = "U酷",
            baseUrls = listOf("https://api.ukuapi.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "1080zyk",
            name = "1080",
            baseUrls = listOf("https://api.1080zyku.com/inc/apijson.php/"),
        ),
        ApiLine(
            id = "hhzyapi",
            name = "豪华",
            baseUrls = listOf("https://hhzyapi.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "subocaiji",
            name = "速博",
            baseUrls = listOf("https://subocaiji.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "p2100",
            name = "飘零",
            baseUrls = listOf("https://p2100.net/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "aqyzy",
            name = "爱奇艺",
            baseUrls = listOf("https://iqiyizyapi.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "yzzy",
            name = "优质",
            baseUrls = listOf("https://api.yzzy-api.com/inc/apijson.php/"),
        ),
        ApiLine(
            id = "myzy",
            name = "猫眼",
            baseUrls = listOf("https://api.maoyanapi.top/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "jinyingzy",
            name = "金鹰点播",
            baseUrls = listOf("https://jinyingzy.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "guangsuapi",
            name = "光速",
            baseUrls = listOf("https://api.guangsuapi.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "niuniu",
            name = "牛牛",
            baseUrls = listOf("https://api.niuniuzy.me/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "yaya",
            name = "鸭鸭",
            baseUrls = listOf("https://cj.yayazy.net/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "hongniu",
            name = "红牛",
            baseUrls = listOf("https://www.hongniuzy2.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "suoni",
            name = "索尼",
            baseUrls = listOf("https://suoniapi.com/api.php/provide/vod/"),
        ),
        ApiLine(
            id = "ffzy",
            name = "非凡",
            baseUrls = listOf(
                "https://api.ffzyapi.com/api.php/provide/vod/",
                "http://api.ffzyapi.com/api.php/provide/vod/",
            ),
        ),
    )
}
