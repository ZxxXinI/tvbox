package com.tvbox.app.data

import android.content.Context
import com.tvbox.app.domain.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

interface AppUpdateRepository {
    suspend fun checkForUpdate(currentVersionCode: Long): AppUpdate?
    suspend fun downloadUpdate(update: AppUpdate, onProgress: (Int) -> Unit): File
}

class DefaultAppUpdateRepository(
    context: Context,
    private val manifestUrl: String = UPDATE_MANIFEST_URL,
) : AppUpdateRepository {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun checkForUpdate(currentVersionCode: Long): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(manifestUrl)
            .header("Cache-Control", "no-cache")
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("更新检查失败：${response.code}")
            }
            parseAppUpdateManifest(
                raw = response.body.string(),
                currentVersionCode = currentVersionCode,
                json = json,
            )
        }
    }

    override suspend fun downloadUpdate(update: AppUpdate, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        if (update.apkUrl.isBlank()) {
            throw IOException("安装包地址为空")
        }
        val request = Request.Builder()
            .url(update.apkUrl)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("安装包下载失败：${response.code}")
            }
            val body = response.body
            val outputDir = File(appContext.cacheDir, "updates").apply {
                if (!exists() && !mkdirs()) {
                    throw IOException("无法创建更新缓存目录")
                }
            }
            val outputFile = File(outputDir, "TVBox-${update.versionName}.apk")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: update.apkSize
            var downloadedBytes = 0L
            var lastProgress = -1

            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }

            if (update.apkSha256.isNotBlank()) {
                val actualSha256 = outputFile.sha256()
                if (!actualSha256.equals(update.apkSha256, ignoreCase = true)) {
                    throw IOException("安装包校验失败")
                }
            }
            onProgress(100)
            outputFile
        }
    }
}

internal fun parseAppUpdateManifest(
    raw: String,
    currentVersionCode: Long,
    json: Json = Json { ignoreUnknownKeys = true },
): AppUpdate? {
    val dto = json.decodeFromString<UpdateManifestDto>(raw.trimStart('\uFEFF'))
    if (dto.versionCode <= currentVersionCode) return null
    return AppUpdate(
        versionCode = dto.versionCode,
        versionName = dto.versionName.trim(),
        apkUrl = dto.apkUrl.trim(),
        apkSha256 = dto.apkSha256.trim(),
        apkSize = dto.apkSize.coerceAtLeast(0L),
        force = dto.force,
        changelog = dto.changelog.map { it.trim() }.filter { it.isNotBlank() },
    )
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { "%02x".format(Locale.US, it) }
}

@Serializable
private data class UpdateManifestDto(
    @SerialName("versionCode") val versionCode: Long,
    @SerialName("versionName") val versionName: String,
    @SerialName("apkUrl") val apkUrl: String,
    @SerialName("apkSha256") val apkSha256: String = "",
    @SerialName("apkSize") val apkSize: Long = 0,
    @SerialName("force") val force: Boolean = false,
    @SerialName("changelog") val changelog: List<String> = emptyList(),
)

private const val UPDATE_MANIFEST_URL = "https://github.com/xin577934014/tvbox/releases/latest/download/update.json"
private const val USER_AGENT = "TVBox-Android"
