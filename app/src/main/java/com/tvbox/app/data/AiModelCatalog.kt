package com.tvbox.app.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal class AiApiEndpoints private constructor(private val base: HttpUrl) {
    val baseUrl: String get() = base.toString().trimEnd('/')
    val modelsUrl: HttpUrl get() = base.newBuilder().addPathSegment("models").build()
    val chatCompletionsUrl: String get() = base.newBuilder().addPathSegments("chat/completions").build().toString()

    companion object {
        fun parse(value: String): AiApiEndpoints? {
            val url = value.trim().takeIf { it.length <= 1024 }?.toHttpUrlOrNull() ?: return null
            if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) {
                return null
            }
            val path = url.encodedPath.trimEnd('/')
            val basePath = when {
                path.endsWith("/chat/completions") -> path.removeSuffix("/chat/completions")
                path.endsWith("/models") -> path.removeSuffix("/models")
                else -> path
            }
            return AiApiEndpoints(url.newBuilder().encodedPath("${basePath.trimEnd('/')}/").build())
        }
    }
}

internal fun String.isAiApiKey() = length in 1..1024 && all { it in '!'..'~' }
internal fun String.isAiModelId() = length in 1..256 && this == trim() && none(Char::isISOControl)

@Serializable
internal data class AiModelListResult(val models: List<String> = emptyList(), val error: String? = null)

private val aiModelClient = OkHttpClient.Builder()
    .callTimeout(10, TimeUnit.SECONDS)
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

internal suspend fun fetchAiModels(endpoints: AiApiEndpoints, apiKey: String): AiModelListResult =
    suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(endpoints.modelsUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()
        val call = aiModelClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(e.modelListError())
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { it.readModelList() }
                } catch (error: IOException) {
                    error.modelListError()
                } catch (_: SerializationException) {
                    AiModelListResult(error = "服务返回的模型列表格式不正确。")
                }
                continuation.resume(result)
            }
        })
    }

private fun IOException.modelListError(): AiModelListResult = AiModelListResult(
    error = if (this is InterruptedIOException) "获取模型超时，请重试。" else "无法连接模型服务，请检查 API 地址和网络。",
)

private fun Response.readModelList(): AiModelListResult {
    if (!isSuccessful) {
        val message = when (code) {
            401, 403 -> "API Key 无效或没有获取模型的权限（$code）。"
            404 -> "模型列表接口未找到（404）。"
            429 -> "请求过于频繁，请稍后重试（429）。"
            else -> "获取模型失败（HTTP $code）。"
        }
        return AiModelListResult(error = message)
    }
    val source = body.source()
    if (source.request(512 * 1024L + 1)) return AiModelListResult(error = "模型列表过大，请手动填写模型 ID。")
    val root = Json.parseToJsonElement(source.readUtf8().trimStart('\uFEFF')) as? JsonObject
    val data = root?.get("data") as? JsonArray
        ?: return AiModelListResult(error = "服务返回的模型列表格式不正确。")
    val ids = data.mapNotNull { entry ->
        ((entry as? JsonObject)?.get("id") as? JsonPrimitive)
            ?.takeIf { it.isString }?.content?.takeIf { it.isAiModelId() }
    }.distinct()
    return if (ids.isEmpty()) AiModelListResult(error = "服务未返回可选模型。") else AiModelListResult(models = ids)
}
