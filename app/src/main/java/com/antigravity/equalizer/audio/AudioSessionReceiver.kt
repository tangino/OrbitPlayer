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

        Log.i(TAG, "Received audio session broadcast: action=$action, sessionId=$sessionId, package=$packageName")

        if (sessionId <= 0) return

        val effectManager = AudioEffectManager.getInstance(context)

        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.i(TAG, "Opening audio effect control session: $sessionId from $packageName")
                effectManager.attachSession(sessionId)
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.i(TAG, "Closing audio effect control session: $sessionId from $packageName")
                effectManager.detachSession(sessionId)
            }
        }
    }

    companion object {
        private const val TAG = "AudioSessionReceiver"
    }
}
