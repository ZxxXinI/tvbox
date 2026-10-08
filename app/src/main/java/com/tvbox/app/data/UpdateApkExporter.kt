package com.tvbox.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Copies a verified private APK to a user-selected document or public Downloads. */
internal class UpdateApkExporter(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun save(source: File, destination: Uri?, fileName: String): String = withContext(Dispatchers.IO) {
        if (destination != null) {
            try {
                val name = resolver.query(destination, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: fileName
                resolver.openOutputStream(destination, "w")?.use { copyVerified(source, it) }
                    ?: throw IOException("无法写入所选位置")
                name
            } catch (error: Exception) {
                // ACTION_CREATE_DOCUMENT creates a new document, never an existing user file.
                runCatching { DocumentsContract.deleteDocument(resolver, destination) }
                throw error
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/TVBox")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("系统下载目录不可用，请选择其他保存位置")
            try {
                resolver.openOutputStream(uri, "w")?.use { copyVerified(source, it) }
                    ?: throw IOException("无法写入下载目录")
                val finished = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                if (resolver.update(uri, finished, null, null) <= 0) throw IOException("无法完成安装包保存")
                "下载/TVBox/$fileName"
            } catch (error: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TVBox")
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建下载目录，请检查存储权限")
            var outputFile = File(directory, fileName)
            var suffix = 1
            while (!outputFile.createNewFile()) {
                outputFile = File(directory, "${fileName.removeSuffix(".apk")} (${suffix++}).apk")
            }
            try {
                outputFile.outputStream().use { copyVerified(source, it) }
                verifyUpdateApk(outputFile, source.length(), updateApkSha256(source))
                outputFile.absolutePath
            } catch (error: Exception) {
                outputFile.delete()
                throw error
            }
        }
    }

    private suspend fun copyVerified(source: File, output: OutputStream) {
        val expectedSize = source.length()
        val expectedSha = updateApkSha256(source)
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        source.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                written += count
            }
        }
        output.flush()
        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
        if (written != expectedSize || actualSha != expectedSha) throw IOException("保存校验失败，请重新保存")
    }
}
