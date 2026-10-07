package com.orbit.installer.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val apkSize: Long,
    val hasLaunchIntent: Boolean
)
