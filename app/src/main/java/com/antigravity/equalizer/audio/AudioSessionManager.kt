package com.antigravity.equalizer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.util.Log

/**
 * 监听和管理系统中所有 AudioSession 与播放状态
 * 具备自适应去重防抖与内置播放器隔离机制，防止与系统 AudioPolicy 发生回环振荡
 */
class AudioSessionManager(
    private val context: Context,
    private val effectManager: AudioEffectManager
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    @Volatile
    private var lastHasExternalPlayback: Boolean = false
    @Volatile
    private var lastEffectEnsureTime: Long = 0L

    fun startListening() {
        // 首先挂载全局 Session 0 (覆盖绝大多数系统默认音频流)
        effectManager.attachSession(0)

        // Android 8.0 (API 26) 以上注册音频回放监听器
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playbackCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    super.onPlaybackConfigChanged(configs)
                    if (configs == null) return

                    // 1. 过滤识别是否有正在发声的外部播放流
                    val now = System.currentTimeMillis()
                    var hasExternalActivePlayback = false

                    for (config in configs) {
                        val sessionId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                val method = config.javaClass.getMethod("getClientAudioSessionId")
                                method.invoke(config) as? Int ?: 0
                            } catch (_: Exception) {
                                0
                            }
                        } else {
                            0
                        }

                        // 如果该 Session 已经作为本应用内置播放器私有绑定 (如当前正在播放的曲目)，则跳过对全局 Session 0 的重复唤醒
                        if (sessionId > 0 && effectManager.isSessionAttached(sessionId)) {
                            continue
                        }

                        val usage = config.audioAttributes?.usage ?: AudioAttributes.USAGE_UNKNOWN
                        if (usage == AudioAttributes.USAGE_MEDIA ||
                            usage == AudioAttributes.USAGE_GAME ||
                            usage == AudioAttributes.USAGE_UNKNOWN
                        ) {
                            hasExternalActivePlayback = true
                            break
                        }
                    }

                    // 2. 防抖过滤：如果状态未发生实质反转且距离上次刷新时间在 1500ms 内，直接跳过，切断死循环回环
                    val stateChanged = hasExternalActivePlayback != lastHasExternalPlayback
                    val isDebounced = (now - lastEffectEnsureTime) < 1500L

                    if (!stateChanged && isDebounced) {
                        return
                    }

                    lastHasExternalPlayback = hasExternalActivePlayback
                    lastEffectEnsureTime = now

                    Log.d(TAG, "Audio playback state changed: count=${configs.size}, externalPlayback=$hasExternalActivePlayback")

                    // 只有检测到外部媒体播放流有实质变化时，才同步保障 Session 0 音效健康
                    if (hasExternalActivePlayback) {
                        effectManager.ensureSession0Attached()
                    }
                }
            }
            try {
                audioManager.registerAudioPlaybackCallback(playbackCallback!!, null)
                Log.i(TAG, "Registered AudioPlaybackCallback with anti-oscillation debounce")
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
