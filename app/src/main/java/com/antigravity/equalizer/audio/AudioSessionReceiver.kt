package com.antigravity.equalizer.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log

/**
 * 接收 Spotify、系统播放器发送的 AudioEffect 广播
 */
class AudioSessionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown"

        if (sessionId <= 0) return

        val effectManager = AudioEffectManager.getInstance(context)

        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                // 如果已经附着，静默忽略，杜绝重复拉起前台服务与日志风暴
                if (effectManager.isSessionAttached(sessionId)) {
                    return
                }
                Log.i(TAG, "Opening audio effect control session: $sessionId from $packageName")
                // 关键保活：确保 EqualizerService 处于前台运行，防止广播接收完成后进程被系统 LMK 瞬杀
                com.antigravity.equalizer.service.EqualizerService.start(context)
                effectManager.attachSession(sessionId)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                if (!effectManager.isSessionAttached(sessionId)) {
                    return
                }
                Log.i(TAG, "Closing audio effect control session: $sessionId from $packageName")
                effectManager.detachSession(sessionId)
            }
        }
    }

    companion object {
        private const val TAG = "AudioSessionReceiver"
    }
}
