package com.antigravity.equalizer.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.antigravity.equalizer.audio.AudioEffectManager
import com.antigravity.equalizer.audio.AudioSessionManager
import com.antigravity.equalizer.audio.DeviceManager

/**
 * 均衡器后台 DSP 伴随服务（保持静默运行，去除多余常驻通知，通知栏由主播放服务统一呈现）
 */
class EqualizerService : Service() {

    private val binder = LocalBinder()
    private lateinit var effectManager: AudioEffectManager
    private lateinit var sessionManager: AudioSessionManager
    private lateinit var deviceManager: DeviceManager

    inner class LocalBinder : Binder() {
        fun getService(): EqualizerService = this@EqualizerService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "EqualizerService onCreate (Silent Background DSP Daemon)")
        effectManager = AudioEffectManager.getInstance(this)
        sessionManager = AudioSessionManager(this, effectManager)
        deviceManager = DeviceManager(this)

        sessionManager.startListening()
        deviceManager.startMonitoring { deviceType, deviceName ->
            Log.i(TAG, "Device profile auto-switched to: $deviceName ($deviceType)")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

    companion object {
        private const val TAG = "EqualizerService"

        fun start(context: Context) {
            val intent = Intent(context, EqualizerService::class.java)
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting EqualizerService", e)
            }
        }
    }
}
