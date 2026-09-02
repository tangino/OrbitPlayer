package com.antigravity.equalizer.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppProfile(
    val packageName: String,
    val appName: String,
    val presetId: String,
    val enabled: Boolean = true
)

/**
 * 针对不同音乐 App (Spotify, YouTube Music, 网易云音乐, QQ音乐) 独立记忆 EQ 预设
 */
class AppProfileRepository {

    private val _appProfiles = MutableStateFlow<Map<String, AppProfile>>(
        mapOf(
            "com.spotify.music" to AppProfile("com.spotify.music", "Spotify", "rock"),
            "com.google.android.apps.youtube.music" to AppProfile("com.google.android.apps.youtube.music", "YouTube Music", "pop"),
            "com.netease.cloudmusic" to AppProfile("com.netease.cloudmusic", "网易云音乐", "vocal"),
            "com.tencent.qqmusic" to AppProfile("com.tencent.qqmusic", "QQ音乐", "bass_boost"),
            "com.apple.android.music" to AppProfile("com.apple.android.music", "Apple Music", "classical")
        )
    )
    val appProfiles: StateFlow<Map<String, AppProfile>> = _appProfiles.asStateFlow()

    fun getProfileForApp(packageName: String): AppProfile? {
        return _appProfiles.value[packageName]
    }

    fun saveAppProfile(profile: AppProfile) {
        val current = _appProfiles.value.toMutableMap()
        current[profile.packageName] = profile
        _appProfiles.value = current
    }

    companion object {
        val instance: AppProfileRepository by lazy { AppProfileRepository() }
    }
}
