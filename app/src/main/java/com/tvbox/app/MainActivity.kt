package com.tvbox.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
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
    private var pendingInstallPermissionAction: InstallPermissionAction? = null
    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val action = pendingInstallPermissionAction ?: return@registerForActivityResult
        pendingInstallPermissionAction = null
        handleInstallPermissionResult(action)
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
                    onStartUpdateDownload = ::startUpdateDownloadWithPermission,
                    onInstallUpdate = ::installUpdateApk,
                )
            }
        }
        window.decorView.post {
            requestInstallPermissionOnFirstLaunch()
        }
    }

    private fun requestInstallPermissionOnFirstLaunch() {
        if (hasRequestedInstallPermissionOnFirstLaunch() || canInstallUnknownApps()) return
        markInstallPermissionRequestedOnFirstLaunch()
        requestInstallPermission(
            action = InstallPermissionAction.FirstLaunch,
            message = "请允许 TVBox 安装未知应用，用于以后应用内更新。你也可以选择拒绝。",
        )
    }

    private fun startUpdateDownloadWithPermission() {
        if (canInstallUnknownApps()) {
            viewModel.startUpdateDownload()
            return
        }
        requestInstallPermission(
            action = InstallPermissionAction.StartUpdateDownload,
            message = "请先允许 TVBox 安装未知应用，允许后将开始下载更新。",
        )
    }

    private fun installUpdateApk(apkPath: String) {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            Toast.makeText(this, "安装包不存在，请重新下载", Toast.LENGTH_SHORT).show()
            return
        }
        if (!canInstallUnknownApps()) {
            requestInstallPermission(
                action = InstallPermissionAction.InstallDownloadedApk(apkPath),
                message = "请允许 TVBox 安装未知应用，允许后将继续安装更新。",
            )
            return
        }
        openSystemInstaller(apkFile)
    }

    private fun openSystemInstaller(apkFile: File) {
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

    private fun requestInstallPermission(action: InstallPermissionAction, message: String) {
        if (canInstallUnknownApps()) {
            handleInstallPermissionGranted(action)
            return
        }

        pendingInstallPermissionAction = action
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        val appPermissionIntent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:$packageName"),
        )
        runCatching {
            installPermissionLauncher.launch(appPermissionIntent)
        }.onFailure {
            runCatching {
                installPermissionLauncher.launch(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }.onFailure {
                pendingInstallPermissionAction = null
                Toast.makeText(this, "无法打开安装权限设置，请在系统设置中允许 TVBox 安装未知应用", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleInstallPermissionResult(action: InstallPermissionAction) {
        if (canInstallUnknownApps()) {
            handleInstallPermissionGranted(action)
            return
        }

        val message = when (action) {
            InstallPermissionAction.FirstLaunch -> "未开启安装权限，发现新版本时会再次提示。"
            InstallPermissionAction.StartUpdateDownload -> "未获得安装权限，暂不下载更新。"
            is InstallPermissionAction.InstallDownloadedApk -> "未获得安装权限，暂不安装更新。"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun handleInstallPermissionGranted(action: InstallPermissionAction) {
        when (action) {
            InstallPermissionAction.FirstLaunch -> {
                Toast.makeText(this, "安装权限已允许，以后可直接应用内更新。", Toast.LENGTH_SHORT).show()
            }
            InstallPermissionAction.StartUpdateDownload -> viewModel.startUpdateDownload()
            is InstallPermissionAction.InstallDownloadedApk -> installUpdateApk(action.apkPath)
        }
    }

    private fun canInstallUnknownApps(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()
    }

    private fun hasRequestedInstallPermissionOnFirstLaunch(): Boolean {
        return getSharedPreferences(INSTALL_PERMISSION_PREFS, MODE_PRIVATE)
            .getBoolean(KEY_INSTALL_PERMISSION_FIRST_LAUNCH_REQUESTED, false)
    }

    private fun markInstallPermissionRequestedOnFirstLaunch() {
        getSharedPreferences(INSTALL_PERMISSION_PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_INSTALL_PERMISSION_FIRST_LAUNCH_REQUESTED, true)
            .apply()
    }
}

private sealed class InstallPermissionAction {
    data object FirstLaunch : InstallPermissionAction()
    data object StartUpdateDownload : InstallPermissionAction()
    data class InstallDownloadedApk(val apkPath: String) : InstallPermissionAction()
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
private const val INSTALL_PERMISSION_PREFS = "install_permission"
private const val KEY_INSTALL_PERMISSION_FIRST_LAUNCH_REQUESTED = "first_launch_requested"
