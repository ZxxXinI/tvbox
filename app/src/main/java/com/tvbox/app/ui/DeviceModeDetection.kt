package com.tvbox.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.tvbox.app.domain.DeviceMode

/** Advisory only: an explicit user setting always controls playback. */
@Suppress("DEPRECATION")
internal fun Context.detectDeviceMode(): DeviceMode {
    val uiModeType = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val television = uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    return if (television) DeviceMode.Television else DeviceMode.Mobile
}
