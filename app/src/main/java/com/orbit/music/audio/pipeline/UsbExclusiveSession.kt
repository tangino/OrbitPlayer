package com.orbit.music.audio.pipeline

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 协调外置 USB DAC 源码直通与独占模式：
 * 1) 锁定当前 USB 输出设备；
 * 2) 以硬件支持的原生采样率与声道发起 AAudio SharingMode::Exclusive 打开请求；
 * 3) 申请强音频焦点，防止其他应用混音侵入。
 */
enum class UsbExclusiveState {
    Off,
    /** 已路由至 USB 设备，但尚未获取底层 AAudio 独占权限（走 Shared 模式） */
    RoutingOnly,
    /** 已成功建立 AAudio SharingMode::Exclusive 独占输出直通 */
    ExclusiveActive,
    /** 请求独占失败，硬件 HAL 拒绝或降级为 Shared */
    ExclusiveFailed
}

data class UsbExclusiveStatus(
    val state: UsbExclusiveState = UsbExclusiveState.Off,
    val deviceName: String = "",
    val deviceId: Int = 0,
    val sampleRate: Int = 0,
    val channelCount: Int = 0,
    val detail: String = ""
)

object UsbExclusiveSession {
    private const val TAG = "UsbExclusiveSession"
    private val _status = MutableStateFlow(UsbExclusiveStatus())
    val status: StateFlow<UsbExclusiveStatus> = _status.asStateFlow()

    @Volatile
    private var focusRequest: AudioFocusRequest? = null

    fun updateRoutingOnly(device: AudioDeviceInfo?) {
        if (device == null) {
            _status.value = UsbExclusiveStatus(state = UsbExclusiveState.Off)
            return
        }
        val current = _status.value
        if (current.state == UsbExclusiveState.ExclusiveActive && current.deviceId == device.id) return
        _status.value = UsbExclusiveStatus(
            state = UsbExclusiveState.RoutingOnly,
            deviceName = device.productName?.toString().orEmpty().ifBlank { "USB DAC" },
            deviceId = device.id,
            detail = "routed"
        )
    }

    fun markExclusiveActive(deviceId: Int, deviceName: String, sampleRate: Int, channelCount: Int) {
        _status.value = UsbExclusiveStatus(
            state = UsbExclusiveState.ExclusiveActive,
            deviceName = deviceName,
            deviceId = deviceId,
            sampleRate = sampleRate,
            channelCount = channelCount,
            detail = "aaudio-exclusive"
        )
        Log.i(TAG, "Exclusive Active: device=$deviceName ($deviceId), rate=$sampleRate, ch=$channelCount")
    }

    fun markExclusiveFailed(deviceId: Int, deviceName: String, detail: String) {
        _status.value = UsbExclusiveStatus(
            state = UsbExclusiveState.ExclusiveFailed,
            deviceName = deviceName,
            deviceId = deviceId,
            detail = detail
        )
        Log.w(TAG, "Exclusive Failed: device=$deviceName ($deviceId), reason=$detail")
    }

    fun clear() {
        _status.value = UsbExclusiveStatus(state = UsbExclusiveState.Off)
    }

    fun requestExclusiveFocus(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener { /* 由 MusicPlayerManager 管理主音频焦点 */ }
                .build()
            focusRequest = req
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun abandonExclusiveFocus(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    /**
     * 生成候选 PCM 配置（优先保持原始采样率，不发生 SRC 重采样）
     */
    fun exclusiveOpenCandidates(
        device: AudioDeviceInfo?,
        preferredRate: Int,
        preferredChannels: Int,
        encodingId: Int
    ): List<Triple<Int, Int, Int>> {
        val rate = if (preferredRate > 0) preferredRate else 44100
        val channels = linkedSetOf<Int>()
        if (preferredChannels > 0) channels += preferredChannels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && device != null) {
            device.channelCounts?.filter { it in 1..8 }?.forEach { channels += it }
        }
        channels += listOf(2, 1)
        val encodings = linkedSetOf(encodingId, 0, 1, 2, 3)
        val out = ArrayList<Triple<Int, Int, Int>>(channels.size * encodings.size)
        for (enc in encodings) {
            for (ch in channels) {
                out += Triple(rate, ch, enc)
            }
        }
        return out
    }

    fun resolveUsbDevice(context: Context): AudioDeviceInfo? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
    }
}
