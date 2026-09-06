package com.antigravity.equalizer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.antigravity.equalizer.R
import com.antigravity.equalizer.audio.AudioEffectManager
import com.antigravity.equalizer.audio.AudioSessionManager
import com.antigravity.equalizer.audio.DeviceManager
import com.antigravity.equalizer.ui.MainActivity

/**
 * 均衡器后台 DSP 前台保活服务：
 * 1. 采用低打扰（IMPORTANCE_LOW）常驻通知，杜绝系统因后台执行限制（Background Execution Limits）强制终止服务；
 * 2. 保证作为第三方播放器（网易云、QQ音乐、Spotify等）全局均衡器时，在后台长期稳定生效不失效；
 * 3. 实时联动音频输出设备（耳机、蓝牙、扬声器）并在通知栏动态呈现当前状态；
 * 4. 支持点击通知栏直达主界面。
 */
class EqualizerService : Service() {

    private val binder = LocalBinder()
    private lateinit var effectManager: AudioEffectManager
    private lateinit var sessionManager: AudioSessionManager
    private lateinit var deviceManager: DeviceManager
    private var currentDeviceName: String? = null

    inner class LocalBinder : Binder() {
        fun getService(): EqualizerService = this@EqualizerService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "EqualizerService onCreate (Foreground DSP Daemon for External Players)")
        createNotificationChannel()

        // 立即拉起前台服务，防止退到后台被系统 LMK/Doze 杀死
        val initialNotification = buildNotification(currentDeviceName)
        safeStartForeground(initialNotification)

        effectManager = AudioEffectManager.getInstance(this)
        sessionManager = AudioSessionManager(this, effectManager)
        deviceManager = DeviceManager(this)

        sessionManager.startListening()
        deviceManager.startMonitoring { deviceType, deviceName ->
            Log.i(TAG, "Device profile auto-switched to: $deviceName ($deviceType)")
            currentDeviceName = deviceName
            updateNotification(deviceName)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 每次触发 startCommand 时确保前台状态
        safeStartForeground(buildNotification(currentDeviceName))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.i(TAG, "EqualizerService onDestroy")
        deviceManager.stopMonitoring()
        sessionManager.stopListening()
        effectManager.releaseAll()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(deviceName: String?): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (!deviceName.isNullOrBlank()) {
            getString(R.string.service_active_device, deviceName)
        } else {
            getString(R.string.service_ready)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.service_running))
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun safeStartForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                }
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground on EqualizerService", e)
        }
    }

    private fun updateNotification(deviceName: String?) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildNotification(deviceName))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update EqualizerService notification", e)
        }
    }

    companion object {
        private const val TAG = "EqualizerService"
        private const val CHANNEL_ID = "channel_equalizer_service"
        private const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, EqualizerService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting EqualizerService as ForegroundService", e)
            }
        }
    }
}

