package com.antigravity.equalizer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var lastHasExternalPlayback: Boolean = false
    @Volatile
    private var lastEffectEnsureTime: Long = 0L

    fun startListening() {
        // 首先挂载全局 Session 0 (覆盖绝大多数系统默认音频流)
        scope.launch {
            effectManager.attachSession(0)
        }

        // Android 8.0 (API 26) 以上注册音频回放监听器
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            playbackCallback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    super.onPlaybackConfigChanged(configs)
                    if (configs == null) return

                    val now = System.currentTimeMillis()
                    var hasExternalActivePlayback = false

                    val myUid = Process.myUid()
                    val internalIsPlaying = try {
                        MusicPlayerManager.getInstance(context).playbackState.value.isPlaying
                    } catch (_: Exception) {
                        false
                    }

                    for (config in configs) {
                        // 1. 优先通过 UID 排除本应用自身的内置播放器音频流
                        val uid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                val method = config.javaClass.getMethod("getClientUid")
                                method.invoke(config) as? Int ?: -1
                            } catch (_: Exception) {
                                -1
                            }
                        } else {
                            -1
                        }
                        if (uid == myUid) {
                            continue
                        }

                        // 2. 检查 SessionId 是否已作为本应用私有 Session 挂载
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
                        if (sessionId > 0 && effectManager.isSessionAttached(sessionId)) {
                            continue
                        }

                        // 3. 兜底保护：若本应用内置播放器正在发声且整个系统仅有 1 个播放流，百分百属于本应用自身的音频流，杜绝误判
                        if (internalIsPlaying && configs.size <= 1) {
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

                    // 4. 防抖过滤：如果状态未发生实质反转且距离上次刷新时间在 1500ms 内，直接跳过，切断死循环回环
                    val stateChanged = hasExternalActivePlayback != lastHasExternalPlayback
                    val isDebounced = (now - lastEffectEnsureTime) < 1500L

                    if (!stateChanged && isDebounced) {
                        return
                    }

                    lastHasExternalPlayback = hasExternalActivePlayback
                    lastEffectEnsureTime = now

                    Log.d(TAG, "Audio playback state changed: count=${configs.size}, externalPlayback=$hasExternalActivePlayback, internalPlaying=$internalIsPlaying")

                    // 5. 只有检测到外部媒体播放流有实质变化时，才在异步协程中保障 Session 0 音效健康，绝不阻塞主线程
                    if (hasExternalActivePlayback) {
                        scope.launch {
                            effectManager.ensureSession0Attached()
                        }
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
        scope.launch {
            effectManager.detachSession(0)
        }
        scope.cancel()
    }

    companion object {
        private const val TAG = "AudioSessionManager"
    }
}
