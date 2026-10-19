package com.orbit.music.audio.pipeline

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 音频输出管道设置：
 * - backend: "AudioTrack", "AAudio", "OpenSL ES"
 * - usbExclusive: 是否在连接外置 USB DAC 时启用 AAudio 独占模式（源码直通 Bit-Perfect）
 * - preferFfmpeg: 是否优先使用本地 FFmpeg 软解（用于避开各种设备上残缺的系统硬件解码器）
 */
data class AudioOutputConfig(
    val backend: String = BACKEND_AUDIOTRACK,
    val usbExclusive: Boolean = false,
    val preferFfmpeg: Boolean = false,
) {
    companion object {
        const val BACKEND_AUDIOTRACK = "AudioTrack"
        const val BACKEND_AAUDIO = "AAudio"
        const val BACKEND_OPENSLES = "OpenSL ES"
    }
}

class AudioOutputSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<AudioOutputConfig> = _config.asStateFlow()

    private fun loadConfig(): AudioOutputConfig {
        return AudioOutputConfig(
            backend = prefs.getString(KEY_BACKEND, AudioOutputConfig.BACKEND_AUDIOTRACK)
                ?: AudioOutputConfig.BACKEND_AUDIOTRACK,
            usbExclusive = prefs.getBoolean(KEY_USB_EXCLUSIVE, false),
            preferFfmpeg = prefs.getBoolean(KEY_PREFER_FFMPEG, false),
        )
    }

    fun setBackend(backend: String) {
        prefs.edit().putString(KEY_BACKEND, backend).apply()
        _config.value = _config.value.copy(backend = backend)
    }

    fun setUsbExclusive(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USB_EXCLUSIVE, enabled).apply()
        _config.value = _config.value.copy(usbExclusive = enabled)
    }

    fun setPreferFfmpeg(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREFER_FFMPEG, enabled).apply()
        _config.value = _config.value.copy(preferFfmpeg = enabled)
    }

    companion object {
        private const val PREFS_NAME = "audio_output_prefs"
        private const val KEY_BACKEND = "audio_backend"
        private const val KEY_USB_EXCLUSIVE = "usb_exclusive_mode"
        private const val KEY_PREFER_FFMPEG = "prefer_ffmpeg_soft_dec"

        @Volatile
        private var instance: AudioOutputSettings? = null

        fun getInstance(context: Context): AudioOutputSettings {
            return instance ?: synchronized(this) {
                instance ?: AudioOutputSettings(context).also { instance = it }
            }
        }
    }
}
