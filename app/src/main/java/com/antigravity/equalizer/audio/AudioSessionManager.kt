package com.antigravity.equalizer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.util.Log

/**
 * 监听和管理系统中所有 AudioSession 与播放状态
 */
class AudioSessionManager(
    private val context: Context,
    private val effectManager: AudioEffectManager
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    fun startListening() {
        // 首先挂载全局 Session 0 (覆盖绝大多数系统默认音频流)
        effectManager.attachSession(0)

        // Android 8.0 (API 26) 以上注册音频回放监听器
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playbackCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    super.onPlaybackConfigChanged(configs)
                    val hasPlayback = configs != null && configs.isNotEmpty()
                    Log.d(TAG, "Audio playback state changed: count=${configs?.size ?: 0}")

                    // 只要检测到系统有正在发声或状态变动的播放器，确保 Session 0 音效持续激活
                    if (hasPlayback) {
                        effectManager.ensureSession0Attached()
                    }
                }
            }
            try {
                audioManager.registerAudioPlaybackCallback(playbackCallback!!, null)
                Log.i(TAG, "Registered AudioPlaybackCallback")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register AudioPlaybackCallback", e)
            }
        }
    }

    fun stopListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playbackCallback?.let {
                try {
                    audioManager.unregisterAudioPlaybackCallback(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unregister AudioPlaybackCallback", e)
                }
            }
        }
        effectManager.detachSession(0)
    }

    companion object {
        private const val TAG = "AudioSessionManager"
    }
}
