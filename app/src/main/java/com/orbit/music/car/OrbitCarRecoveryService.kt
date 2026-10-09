package com.orbit.music.car

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * 车载媒体恢复与音源唤起服务
 * 参考 Flyme Auto 版 QQ音乐 RecoveryMediaService 设计
 *
 * 核心功能：
 * 当车主在车机屏幕顶栏选择本音源、或车机从休眠唤醒恢复播放时，
 * 车机会通过 registerMusicRecoveryIntent 绑定的 Intent 拉起或绑定本服务，
 * 本服务捕获后立即触发播放恢复与焦点夺取。
 */
class OrbitCarRecoveryService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "OrbitCarRecoveryService onCreate")
        EcarxMediaBridge.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "★ OrbitCarRecoveryService onStartCommand: action=${intent?.action}")
        handleIntent(intent)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.i(TAG, "★ OrbitCarRecoveryService onBind: action=${intent?.action}")
        handleIntent(intent)
        return null
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        Log.i(TAG, "handleRecoveryIntent: action=$action")

        val sourceType = intent.getIntExtra("sourceType", 6)
        EcarxMediaBridge.onCarSourceSwitched(sourceType)
    }

    companion object {
        private const val TAG = "OrbitCarRecoveryService"
    }
}
