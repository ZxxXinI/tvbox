package com.tvbox.app.data

import android.content.Context
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import com.tvbox.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import com.tvbox.app.domain.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

interface AppUpdateRepository {
    suspend fun checkForUpdate(currentVersionCode: Long): AppUpdate?
    suspend fun downloadUpdate(update: AppUpdate, onProgress: (Int) -> Unit): File
    suspend fun pendingDownload(): AppUpdate? = null
    suspend fun shouldAutoInstall(apkPath: String): Boolean = true
    suspend fun markInstallPrompted(apkPath: String) {}
}

class DefaultAppUpdateRepository(
    context: Context,
    private val manifestUrl: String = UPDATE_MANIFEST_URL,
) : AppUpdateRepository {
    private val appContext = context.applicationContext
    private val downloadPrefs = appContext.getSharedPreferences("background_update", Context.MODE_PRIVATE)
    private val downloadMutex = Mutex()
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
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

    override suspend fun pendingDownload(): AppUpdate? = withContext(Dispatchers.IO) {
        val task = readTask() ?: return@withContext null
        if (task.manifest.versionCode <= BuildConfig.VERSION_CODE) {
            downloadPrefs.edit().remove("task").commit()
            null
        } else task.manifest.toDomain()
    }

    override suspend fun shouldAutoInstall(apkPath: String): Boolean = withContext(Dispatchers.IO) {
        readTask()?.let { it.filePath == apkPath && !it.installPrompted } ?: true
    }

    override suspend fun markInstallPrompted(apkPath: String) = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            readTask()?.takeIf { it.filePath == apkPath }?.let { saveTask(it.copy(installPrompted = true)) }
        }
        Unit
    }

    override suspend fun downloadUpdate(update: AppUpdate, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val manager = downloadManager ?: throw IOException("设备暂不支持后台下载")
        val task = downloadMutex.withLock {
            val existing = readTask()
            if (existing != null && existing.manifest.versionCode == update.versionCode &&
                existing.manifest.apkUrl == update.apkUrl && existing.manifest.apkSha256 == update.apkSha256) existing
            else {
                if (update.apkUrl.isBlank()) throw IOException("安装包地址为空")
                val name = "updates/TVBox-${update.versionCode}-${System.currentTimeMillis()}.apk"
                val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: throw IOException("无法访问更新下载目录")
                val target = File(directory, name)
                val request = DownloadManager.Request(Uri.parse(update.apkUrl))
                    .setTitle("TVBox ${update.versionName}")
                    .setDescription("应用更新正在后台下载")
                    .setMimeType("application/vnd.android.package-archive")
                    .addRequestHeader("User-Agent", USER_AGENT)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, name)
                PendingSystemDownload(manager.enqueue(request), target.absolutePath, UpdateManifestDto(
                    update.versionCode, update.versionName, update.apkUrl, update.apkSha256,
                    update.apkSize, update.force, update.changelog,
                )).also(::saveTask)
            }
        }
        try {
            var lastProgress = Int.MIN_VALUE
            while (true) {
                var status = DownloadManager.STATUS_PENDING
                var reason = 0
                var received = 0L
                var total = update.apkSize
                manager.query(DownloadManager.Query().setFilterById(task.id)).use { cursor ->
                    if (cursor == null || !cursor.moveToFirst()) throw IOException("下载任务已被移除，请重新下载")
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    received = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: total
                }
                if (status == DownloadManager.STATUS_FAILED) {
                    throw IOException(if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) "设备存储空间不足，请清理后重试"
                        else "更新下载失败，请检查网络后重试")
                }
                val progress = if (total > 0) ((received * 100) / total).toInt().coerceIn(0, 99) else -1
                if (lastProgress != progress) { onProgress(progress); lastProgress = progress }
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val apk = File(task.filePath)
                    if (!apk.isFile || apk.length() == 0L || (update.apkSize > 0 && apk.length() != update.apkSize)) {
                        throw IOException("安装包下载不完整，请重新下载")
                    }
                    if (update.apkSha256.isNotBlank() && !apk.sha256().equals(update.apkSha256, true)) {
                        throw IOException("安装包校验失败，请重新下载")
                    }
                    onProgress(100)
                    return@withContext apk
                }
                delay(750)
            }
            @Suppress("UNREACHABLE_CODE") throw IOException("下载任务已终止")
        } catch (cancelled: CancellationException) {
            // The system download is deliberately retained when UI observation stops.
            throw cancelled
        } catch (error: Throwable) {
            downloadMutex.withLock {
                if (readTask()?.id == task.id) downloadPrefs.edit().remove("task").commit()
            }
            throw error
        }
    }

    private fun readTask(): PendingSystemDownload? {
        val raw = downloadPrefs.getString("task", null) ?: return null
        return runCatching { json.decodeFromString<PendingSystemDownload>(raw) }.getOrNull()
    }

    private fun saveTask(task: PendingSystemDownload) {
        check(downloadPrefs.edit().putString("task", json.encodeToString(task)).commit()) {
            "Unable to save background download state"
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

private const val UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/ZxxXinI/tvbox/main/update.json"
private const val USER_AGENT = "TVBox-Android"

@Serializable
private data class PendingSystemDownload(
    val id: Long,
    val filePath: String,
    val manifest: UpdateManifestDto,
    val installPrompted: Boolean = false,
)

private fun UpdateManifestDto.toDomain() = AppUpdate(
    versionCode, versionName, apkUrl, apkSha256, apkSize, force, changelog,
)
