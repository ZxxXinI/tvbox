package com.tvbox.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tvbox.app.data.DefaultAppUpdateRepository
import com.tvbox.app.data.DefaultMovieRepository
import com.tvbox.app.data.SharedAppSettingsRepository
import com.tvbox.app.data.SharedHistoryRepository
import com.tvbox.app.data.SharedPlaybackHealthRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvbox.app.ui.TvBoxApp
import com.tvbox.app.ui.TvBoxViewModel
import com.tvbox.app.ui.theme.TVBoxTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: TvBoxViewModel by viewModels {
        TvBoxViewModelFactory(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TVBoxTheme {
                val state = viewModel.state.collectAsStateWithLifecycle().value
                BackHandler(enabled = state.screen != com.tvbox.app.ui.TvScreen.Home) {
                    viewModel.goBack()
                }
                TvBoxApp(
                    state = state,
                    actions = viewModel,
                    onInstallUpdate = ::installUpdateApk,
                )
            }
        }
    }

    private fun installUpdateApk(apkPath: String) {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            Toast.makeText(this, "安装包不存在，请重新下载", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "请允许 TVBox 安装未知应用后再次点击安装", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName"),
            )
            startActivity(intent)
            return
        }

        val apkUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, "无法打开系统安装器", Toast.LENGTH_SHORT).show()
            }
    }
}

private class TvBoxViewModelFactory(
    private val activity: ComponentActivity,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TvBoxViewModel(
            repository = DefaultMovieRepository(),
            appUpdateRepository = DefaultAppUpdateRepository(activity.applicationContext),
            appSettingsRepository = SharedAppSettingsRepository(activity.applicationContext),
            playbackHealthRepository = SharedPlaybackHealthRepository(activity.applicationContext),
            historyRepository = SharedHistoryRepository(activity.applicationContext),
        ) as T
    }
}

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
