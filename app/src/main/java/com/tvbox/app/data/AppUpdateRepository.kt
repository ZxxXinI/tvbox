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
import java.util.concurrent.TimeUnit

interface AppUpdateRepository {
    suspend fun checkForUpdate(currentVersionCode: Long): AppUpdate?
    suspend fun downloadUpdate(update: AppUpdate, onProgress: (Int) -> Unit): File
    suspend fun pendingDownload(): AppUpdate? = null
    suspend fun shouldAutoInstall(apkPath: String): Boolean = true
    suspend fun isAwaitingInstallPermission(apkPath: String): Boolean = false
    suspend fun setAwaitingInstallPermission(apkPath: String, awaiting: Boolean) {}
    suspend fun forgetDownload(apkPath: String) {}
    suspend fun markInstallPrompted(apkPath: String) {}
    suspend fun chooseManualInstall(apkPath: String) {}
    suspend fun exportUpdateApk(apkPath: String, destination: Uri?): String = throw IOException("暂不支持保存安装包")
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
        readTask()?.let { it.filePath == apkPath && !it.installPrompted && !it.awaitingInstallPermission && !it.manualInstallOnly } ?: true
    }

    override suspend fun isAwaitingInstallPermission(apkPath: String): Boolean = withContext(Dispatchers.IO) {
        readTask()?.let { it.filePath == apkPath && it.awaitingInstallPermission && !it.manualInstallOnly } ?: false
    }

    override suspend fun setAwaitingInstallPermission(apkPath: String, awaiting: Boolean) = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            readTask()?.takeIf { it.filePath == apkPath }?.let {
                saveTask(it.copy(awaitingInstallPermission = awaiting, installPrompted = false, manualInstallOnly = false))
            }
        }
        Unit
    }

    override suspend fun forgetDownload(apkPath: String) = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            if (readTask()?.filePath == apkPath) downloadPrefs.edit().remove("task").commit()
        }
        Unit
    }

    override suspend fun markInstallPrompted(apkPath: String) = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            readTask()?.takeIf { it.filePath == apkPath }?.let {
                saveTask(it.copy(installPrompted = true, awaitingInstallPermission = false))
            }
        }
        Unit
    }

    override suspend fun downloadUpdate(update: AppUpdate, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val manager = downloadManager
        var task = downloadMutex.withLock {
            val existing = readTask()
            if (existing != null && existing.manifest.versionCode == update.versionCode &&
                existing.manifest.apkUrl == update.apkUrl && existing.manifest.apkSha256 == update.apkSha256) existing
            else {
                if (update.apkUrl.isBlank()) throw IOException("安装包地址为空")
                val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?.let { File(it, "updates") } ?: File(appContext.cacheDir, "updates")
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建更新下载目录")
                val target = File(directory, "TVBox-${update.versionCode}-${System.currentTimeMillis()}.apk")
                val id = try {
                    val availableManager = manager ?: throw IOException("系统下载组件不可用")
                    availableManager.enqueue(DownloadManager.Request(Uri.parse(update.apkUrl))
                        .setTitle("TVBox ${update.versionName}")
                        .setDescription("应用更新正在后台下载")
                        .setMimeType("application/vnd.android.package-archive")
                        .addRequestHeader("User-Agent", USER_AGENT)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationUri(Uri.fromFile(target)))
                } catch (error: Exception) {
                    android.util.Log.w("TVBoxUpdate", "System downloader unavailable; using app download", error)
                    0L
                }
                PendingSystemDownload(id, target.absolutePath, UpdateManifestDto(
                    update.versionCode, update.versionName, update.apkUrl, update.apkSha256,
                    update.apkSize, update.force, update.changelog,
                ), appDownloader = id == 0L).also(::saveTask)
            }
        }
        try {
            if (task.appDownloader) return@withContext downloadInApp(task, onProgress)
            var lastProgress = Int.MIN_VALUE
            while (true) {
                var status = DownloadManager.STATUS_PENDING
                var reason = 0
                var received = 0L
                var total = update.apkSize
                try {
                    val availableManager = manager ?: throw IOException("系统下载组件不可用")
                    availableManager.query(DownloadManager.Query().setFilterById(task.id)).use { cursor ->
                        if (cursor == null || !cursor.moveToFirst()) throw IOException("系统下载任务已失效")
                        status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        received = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: total
                    }
                } catch (error: Exception) {
                    android.util.Log.w("TVBoxUpdate", "Cannot observe system download; switching to app download", error)
                    // Separate paths prevent an old system job from writing into the fallback file.
                    task = task.copy(id = 0, appDownloader = true, filePath = "${task.filePath}.fallback.apk")
                    downloadMutex.withLock { saveTask(task) }
                    return@withContext downloadInApp(task, onProgress)
                }
                if (status == DownloadManager.STATUS_FAILED) {
                    throw IOException(if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) "设备存储空间不足，请清理后重试"
                        else "更新下载失败（系统错误 $reason），请重试")
                }
                val progress = if (total > 0) ((received * 100) / total).toInt().coerceIn(0, 99) else -1
                if (lastProgress != progress) { onProgress(progress); lastProgress = progress }
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val apk = File(task.filePath)
                    verifyUpdateApk(apk, update.apkSize, update.apkSha256)
                    onProgress(100)
                    return@withContext apk
                }
                delay(750)
            }
            @Suppress("UNREACHABLE_CODE") throw IOException("下载任务已终止")
        } catch (cancelled: CancellationException) {
            // DownloadManager / foreground service outlives this UI observer.
            throw cancelled
        } catch (error: Throwable) {
            android.util.Log.w("TVBoxUpdate", "Update download failed", error)
            downloadMutex.withLock {
                if (readTask()?.filePath == task.filePath) downloadPrefs.edit().remove("task").commit()
            }
            throw error
        }
    }

    private suspend fun downloadInApp(task: PendingSystemDownload, onProgress: (Int) -> Unit): File {
        val apk = File(task.filePath)
        if (apk.isFile && runCatching { verifyUpdateApk(apk, task.manifest.apkSize, task.manifest.apkSha256) }.isSuccess) {
            onProgress(100)
            return apk
        }
        try {
            UpdateDownloadService.start(appContext, task.manifest.apkUrl, task.filePath,
                task.manifest.apkSize, task.manifest.apkSha256)
        } catch (error: Exception) {
            android.util.Log.w("TVBoxUpdate", "Unable to start app download service", error)
            throw IOException("无法启动应用下载服务，请返回应用后重试", error)
        }
        var lastProgress = Int.MIN_VALUE
        while (true) {
            val status = UpdateDownloadService.status(task.filePath)
            if (status?.error != null) throw IOException(status.error)
            if (status?.complete == true) {
                verifyUpdateApk(apk, task.manifest.apkSize, task.manifest.apkSha256)
                onProgress(100)
                return apk
            }
            val total = status?.total?.takeIf { it > 0 } ?: task.manifest.apkSize
            val progress = if (total > 0) (((status?.received ?: 0) * 100) / total).toInt().coerceIn(0, 99) else -1
            if (progress != lastProgress) { onProgress(progress); lastProgress = progress }
            delay(750)
        }
    }

    override suspend fun chooseManualInstall(apkPath: String) = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            readTask()?.takeIf { it.filePath == apkPath }?.let {
                saveTask(it.copy(manualInstallOnly = true, awaitingInstallPermission = false))
            }
        }
        Unit
    }

    override suspend fun exportUpdateApk(apkPath: String, destination: Uri?): String = withContext(Dispatchers.IO) {
        val task = readTask()?.takeIf { it.filePath == apkPath } ?: throw IOException("安装包记录已失效，请重新下载")
        val source = File(apkPath)
        verifyUpdateApk(source, task.manifest.apkSize, task.manifest.apkSha256)
        val version = task.manifest.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        UpdateApkExporter(appContext).save(source, destination, "TVBox-v$version.apk")
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
    val awaitingInstallPermission: Boolean = false,
    val appDownloader: Boolean = false,
    val manualInstallOnly: Boolean = false,
)

private fun UpdateManifestDto.toDomain() = AppUpdate(
    versionCode, versionName, apkUrl, apkSha256, apkSize, force, changelog,
)
