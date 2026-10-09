package com.orbit.music.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.orbit.music.audio.MusicPlayerManager

/**
 * 车载方向盘按键与系统媒体按键广播接收器
 * 参考 Flyme Auto 版 QQ音乐 MediaButtonReceiver 设计
 *
 * 核心功能：
 * 1. 拦截底层车载方向盘多功能按键广播 (上一曲/下一曲/播放/暂停/滚轮按压)
 * 2. 拦截系统 MEDIA_BUTTON 硬件事件
 * 3. 严格防抖机制（过滤 ACTION_UP 重复触发，避免单次按键跳两首歌）
 * 4. 有序广播消费截断，防止车载原生默认播放器抢占
 */
class OrbitMediaButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        Log.i(TAG, "★ 收到媒体按键广播: action=$action")

        val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }

        if (keyEvent != null) {
            handleKeyEvent(context, keyEvent)
        } else if (intent.hasExtra(KEY_CODE)) {
            val keyCode = intent.getIntExtra(KEY_CODE, 0)
            val keyAction = intent.getIntExtra(KEY_ACTION, KeyEvent.ACTION_DOWN)
            val event = KeyEvent(keyAction, keyCode)
            handleKeyEvent(context, event)
        }

        if (isOrderedBroadcast) {
            abortBroadcast()
        }
    }

    private fun handleKeyEvent(context: Context, keyEvent: KeyEvent) {
        val keyCode = keyEvent.keyCode
        val keyAction = keyEvent.action
        Log.i(TAG, "handleKeyEvent: keyCode=$keyCode, keyAction=$keyAction, repeat=${keyEvent.repeatCount}")

        // 仅在 ACTION_DOWN 时触发控制，防止一次按下弹起触发两次操作
        if (keyAction != KeyEvent.ACTION_DOWN) return

        // 简易时间窗口防抖 (200ms 内同一按键忽略)
        val now = SystemClock.uptimeMillis()
        if (keyCode == lastKeyCode && now - lastClickTime < 250) {
            Log.d(TAG, "按键抖动已忽略: keyCode=$keyCode")
            return
        }
        lastClickTime = now
        lastKeyCode = keyCode

        val playerManager = MusicPlayerManager.getInstance(context)

        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                Log.i(TAG, "★ 方向盘切歌: 下一首 (NEXT)")
                EcarxMediaBridge.takeFocus()
                playerManager.playNext()
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                Log.i(TAG, "★ 方向盘切歌: 上一首 (PREVIOUS)")
                EcarxMediaBridge.takeFocus()
                playerManager.playPrevious()
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                Log.i(TAG, "★ 方向盘控制: 播放 (PLAY)")
                EcarxMediaBridge.takeFocus()
                playerManager.play()
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                Log.i(TAG, "★ 方向盘控制: 暂停 (PAUSE/STOP)")
                playerManager.pause()
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                Log.i(TAG, "★ 方向盘控制: 播放/暂停切换 (TOGGLE)")
                EcarxMediaBridge.takeFocus()
                playerManager.togglePlayPause()
            }

            else -> {
                Log.d(TAG, "未处理的媒体按键: keyCode=$keyCode")
            }
        }
    }

    companion object {
        private const val TAG = "OrbitMediaButtonReceiver"
        private const val KEY_CODE = "KEY_CODE"
        private const val KEY_ACTION = "KEY_ACTION"

        private var lastClickTime = 0L
        private var lastKeyCode = 0
    }
}
