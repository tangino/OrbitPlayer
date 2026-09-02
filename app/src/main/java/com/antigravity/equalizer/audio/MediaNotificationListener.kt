package com.antigravity.equalizer.audio

import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.antigravity.equalizer.data.repository.AppProfileRepository

/**
 * 增强媒体会话感知服务（类似 Wavelet / Poweramp Equalizer）
 * 实时感知 Spotify, YouTube Music, 网易云, QQ 音乐等播放状态并自动切换专属 EQ
 */
class MediaNotificationListener : NotificationListenerService() {

    private val appProfileRepo = AppProfileRepository.instance

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val pkg = sbn?.packageName ?: return

        // 过滤非媒体类通知
        val isMedia = sbn.notification.extras.containsKey("android.mediaSession") ||
                sbn.notification.category == "transport" ||
                pkg.contains("music") || pkg.contains("spotify") || pkg.contains("audio")

        if (isMedia) {
            Log.i(TAG, "Detected active media playback from package: $pkg")
            val profile = appProfileRepo.getProfileForApp(pkg)
            if (profile != null && profile.enabled) {
                Log.i(TAG, "Auto-switching EQ preset for $pkg -> ${profile.presetId}")
                // 发送广播或更新音频管理器应用该预设
                val intent = Intent("com.antigravity.equalizer.ACTION_APPLY_APP_PROFILE").apply {
                    putExtra("EXTRA_PACKAGE", pkg)
                    putExtra("EXTRA_PRESET_ID", profile.presetId)
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    companion object {
        private const val TAG = "MediaNotifyListener"
    }
}
