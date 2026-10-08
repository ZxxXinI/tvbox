package com.tvbox.app

import android.Manifest
import android.content.Intent
import android.content.ClipData
import android.util.Log
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import com.tvbox.app.data.DefaultAppUpdateRepository
import com.tvbox.app.data.DefaultDoubanHotRepository
import com.tvbox.app.data.DefaultMovieRepository
import com.tvbox.app.data.SharedAppSettingsRepository
import com.tvbox.app.data.SharedDoubanHotCache
import com.tvbox.app.data.SharedHistoryRepository
import com.tvbox.app.data.SharedPlaybackHealthRepository
import com.tvbox.app.data.SharedPlatformLiveFavoritesRepository
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
    private var permissionSettingsOpen = false
    private var pendingExportApkPath: String? = null
    private val saveApkLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument(APK_MIME_TYPE)) { uri ->
        val path = pendingExportApkPath ?: return@registerForActivityResult
        pendingExportApkPath = null
        if (uri != null) viewModel.saveUpdateApk(path, uri) else viewModel.cancelUpdateApkExport()
    }
    private val exportStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val path = pendingExportApkPath ?: return@registerForActivityResult
        pendingExportApkPath = null
        if (granted) viewModel.saveUpdateApk(path, null)
        else viewModel.cancelUpdateApkExport("未获得存储权限，无法保存到下载目录；安装包仍保留，可重试")
    }
    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        permissionSettingsOpen = false
        val action = pendingInstallPermissionAction ?: return@registerForActivityResult
        pendingInstallPermissionAction = null
        handleInstallPermissionResult(action)
    }
    private var speechRecognizer: SpeechRecognizer? = null
    private val speechPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startInAppSpeechRecognition()
        } else {
            viewModel.showAiMessage("没有麦克风权限，无法语音找片")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingExportApkPath = savedInstanceState?.getString("pendingExportApk")
        pendingInstallPermissionAction = savedInstanceState?.getString("pendingUpdateApk")
            ?.let { InstallPermissionAction.InstallDownloadedApk(it) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // Some TV settings apps return without an ActivityResult callback.
                permissionSettingsOpen = false
                viewModel.state.map {
                    it.updateAutoInstallPath to it.updateDownloadedApkPath.takeIf { _ -> it.updateAwaitingInstallPermission }
                }.distinctUntilChanged().collect { (automatic, awaitingPermission) ->
                    if (automatic != null) installUpdateApk(automatic)
                    else if (awaitingPermission != null && canInstallUnknownApps()) installUpdateApk(awaitingPermission)
                }
            }
        }
        setContent {
            val state = viewModel.state.collectAsStateWithLifecycle().value
            TVBoxTheme(
                theme = state.appSettings.theme,
                fontScale = state.appSettings.fontScale,
            ) {
                BackHandler(enabled = state.screen != com.tvbox.app.ui.TvScreen.Home) {
                    viewModel.goBack()
                }
                TvBoxApp(
                    state = state,
                    actions = viewModel,
                    onStartAiVoiceInput = ::startAiVoiceInput,
                    onStartUpdateDownload = ::startBackgroundUpdateDownload,
                    onInstallUpdate = ::installUpdateApk,
                    onOpenUpdateSettings = ::openUpdateSystemSettings,
                    onSaveUpdateApk = ::saveUpdateApk,
                )
            }
        }
    }

    private fun startAiVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            viewModel.showAiMessage("请允许麦克风权限后再语音找片")
            speechPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startInAppSpeechRecognition()
    }

    private fun startInAppSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            viewModel.showAiMessage("当前设备不支持语音识别，请使用文字输入")
            return
        }
        val speechServicePackage = resolveSpeechRecognitionServicePackage()
        if (speechServicePackage != null && !hasMicrophonePermission(speechServicePackage)) {
            viewModel.showAiMessage("系统语音服务缺少麦克风权限，请允许后再试")
            openSpeechServiceSettings(speechServicePackage)
            return
        }

        releaseSpeechRecognizer()
        viewModel.startAiVoiceListening()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(createSpeechRecognitionListener())
            startListening(createSpeechRecognitionIntent())
        }
    }

    private fun createSpeechRecognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出你的找片需求")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    private fun createSpeechRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                viewModel.startAiVoiceListening()
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                releaseSpeechRecognizer(cancel = false)
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    resolveSpeechRecognitionServicePackage()
                        ?.takeIf { !hasMicrophonePermission(it) }
                        ?.let(::openSpeechServiceSettings)
                }
                viewModel.showAiMessage(speechErrorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                releaseSpeechRecognizer(cancel = false)
                handleSpeechResults(results)
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
    }

    private fun handleSpeechResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (text.isBlank()) {
            viewModel.showAiMessage("没有听清，请再说一次")
        } else {
            viewModel.submitAiRecommendation(text)
        }
    }

    private fun releaseSpeechRecognizer(cancel: Boolean = true) {
        if (cancel) {
            speechRecognizer?.cancel()
        }
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun resolveSpeechRecognitionServicePackage(): String? {
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, 0)
        }
        return services.firstOrNull()?.serviceInfo?.packageName
    }

    private fun hasMicrophonePermission(packageName: String): Boolean {
        return packageManager.checkPermission(Manifest.permission.RECORD_AUDIO, packageName) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun openSpeechServiceSettings(packageName: String) {
        Toast.makeText(this, "请允许系统语音服务使用麦克风", Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, "无法打开系统语音服务设置，请在系统设置中手动允许麦克风权限", Toast.LENGTH_LONG).show()
            }
    }

    private fun saveUpdateApk(apkPath: String) {
        if (!viewModel.beginUpdateApkExport(apkPath)) return
        pendingInstallPermissionAction = null
        pendingExportApkPath = apkPath
        val version = viewModel.state.value.availableUpdate?.versionName.orEmpty()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        try {
            saveApkLauncher.launch("TVBox-v$version.apk")
        } catch (error: Exception) {
            Log.w("TVBoxUpdate", "No document picker; exporting to public Downloads", error)
            Toast.makeText(this, "系统没有可用的文件选择器，将保存到下载目录的 TVBox 文件夹", Toast.LENGTH_LONG).show()
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                try { exportStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                catch (permissionError: Exception) {
                    Log.w("TVBoxUpdate", "Cannot request legacy export storage permission", permissionError)
                    pendingExportApkPath = null
                    viewModel.cancelUpdateApkExport("无法申请保存所需的存储权限，安装包仍保留")
                }
            } else {
                pendingExportApkPath = null
                viewModel.saveUpdateApk(apkPath, null)
            }
        }
    }

    private fun startBackgroundUpdateDownload() {
        viewModel.startUpdateDownload()
    }

    private fun installUpdateApk(apkPath: String) {
        if (permissionSettingsOpen && !canInstallUnknownApps()) return
        if (!viewModel.beginUpdateInstallAttempt(apkPath)) return
        val apkFile = File(apkPath)
        if (!apkFile.isFile || apkFile.length() == 0L) {
            pendingInstallPermissionAction = null
            viewModel.reportUpdateInstallFailure(apkPath, "安装包不存在，请重新下载", fileMissing = true)
            return
        }
        if (!canInstallUnknownApps()) {
            requestInstallPermission(
                action = InstallPermissionAction.InstallDownloadedApk(apkPath),
                message = "请允许 TVBox 安装未知应用，允许后将继续安装更新。",
            )
            return
        }
        pendingInstallPermissionAction = null
        permissionSettingsOpen = false
        openSystemInstaller(apkFile)
    }

    @Suppress("DEPRECATION")
    private fun openSystemInstaller(apkFile: File) {
        val apkUri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
        } catch (error: Exception) {
            Log.e("TVBoxUpdate", "Unable to share update APK", error)
            viewModel.reportUpdateInstallFailure(apkFile.absolutePath, "无法读取安装包，请重新下载", fileMissing = true)
            return
        }
        for (action in listOf(Intent.ACTION_VIEW, Intent.ACTION_INSTALL_PACKAGE)) {
            val intent = Intent(action).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                clipData = ClipData.newRawUri("TVBox update", apkUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(intent)
                viewModel.markUpdateInstallPrompted(apkFile.absolutePath)
                return
            } catch (error: Exception) {
                Log.w("TVBoxUpdate", "Unable to open installer: $action", error)
            }
        }
        val message = "无法打开系统安装器，安装包已保留，可重试安装"
        viewModel.reportUpdateInstallFailure(apkFile.absolutePath, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun openUpdateSystemSettings() {
        val apkPath = viewModel.state.value.updateDownloadedApkPath ?: return
        if (permissionSettingsOpen) return
        requestInstallPermission(InstallPermissionAction.InstallDownloadedApk(apkPath),
            "请在系统设置中允许 TVBox 安装未知应用，授权后返回即可继续安装", generalSettingsOnly = true)
    }

    private fun requestInstallPermission(
        action: InstallPermissionAction,
        message: String,
        generalSettingsOnly: Boolean = false,
    ) {
        val apkPath = (action as InstallPermissionAction.InstallDownloadedApk).apkPath
        viewModel.awaitUpdateInstallPermission(apkPath)
        pendingInstallPermissionAction = action
        if (canInstallUnknownApps()) {
            viewModel.resumeUpdateInstallAfterPermission(apkPath)
            return
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        val packageUri = Uri.parse("package:$packageName")
        val candidates = if (generalSettingsOnly) listOf(
            Intent(Settings.ACTION_SETTINGS),
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
        ) else listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri),
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        // Try launching directly: package visibility can make resolveActivity unreliable.
        for (intent in candidates) {
            try {
                permissionSettingsOpen = true
                installPermissionLauncher.launch(intent)
                Log.i("TVBoxUpdate", "Opened install permission settings: ${intent.action}")
                return
            } catch (error: Exception) {
                permissionSettingsOpen = false
                Log.w("TVBoxUpdate", "Unable to open permission settings: ${intent.action}", error)
            }
        }
        val failureMessage = "无法打开安装权限设置。安装包已保留，请在系统设置中允许 TVBox 安装未知应用"
        viewModel.awaitUpdateInstallPermission(apkPath, failureMessage)
        Toast.makeText(this, failureMessage, Toast.LENGTH_LONG).show()
    }

    private fun handleInstallPermissionResult(action: InstallPermissionAction) {
        val apkPath = (action as InstallPermissionAction.InstallDownloadedApk).apkPath
        val state = viewModel.state.value
        if (state.updateDownloadedApkPath != apkPath || !state.updateAwaitingInstallPermission) return
        if (canInstallUnknownApps()) {
            // The RESUMED collector launches the installer, never this STARTED callback.
            viewModel.resumeUpdateInstallAfterPermission(apkPath)
            return
        }

        val message = "尚未获得安装授权，安装包已保留。授权后返回应用即可继续安装"
        viewModel.awaitUpdateInstallPermission(apkPath, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun canInstallUnknownApps(): Boolean {
        return try {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()
        } catch (error: Exception) {
            Log.w("TVBoxUpdate", "Unable to check unknown-app installation permission", error)
            false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pendingExportApk", pendingExportApkPath)
        val action = pendingInstallPermissionAction as? InstallPermissionAction.InstallDownloadedApk
        outState.putString("pendingUpdateApk", action?.apkPath)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        releaseSpeechRecognizer()
        super.onDestroy()
    }
}

private sealed class InstallPermissionAction {
    data class InstallDownloadedApk(val apkPath: String) : InstallPermissionAction()
}

private class TvBoxViewModelFactory(
    private val activity: ComponentActivity,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TvBoxViewModel(
            repository = DefaultMovieRepository(),
            doubanHotRepository = DefaultDoubanHotRepository(
                cache = SharedDoubanHotCache(activity.applicationContext),
            ),
            appUpdateRepository = DefaultAppUpdateRepository(activity.applicationContext),
            appSettingsRepository = SharedAppSettingsRepository(activity.applicationContext),
            playbackHealthRepository = SharedPlaybackHealthRepository(activity.applicationContext),
            historyRepository = SharedHistoryRepository(activity.applicationContext),
            platformLiveFavoritesRepository = SharedPlatformLiveFavoritesRepository(activity.applicationContext),
        ) as T
    }
}

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

private fun speechErrorMessage(error: Int): String {
    return when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "麦克风录音异常，请检查权限"
        SpeechRecognizer.ERROR_CLIENT -> "语音识别已取消，请再试一次"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "系统语音服务缺少麦克风权限，请允许后再试"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        -> "语音识别网络异常，请稍后再试"
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> "没有听清，请再说一次"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别正在准备，请稍后再试"
        else -> "语音识别失败，请再试一次"
    }
}

