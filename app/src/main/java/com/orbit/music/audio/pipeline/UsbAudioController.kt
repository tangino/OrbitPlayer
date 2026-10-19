package com.orbit.music.audio.pipeline

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 负责检测外置 USB DAC / 声卡热插拔，并在开启独占直通时协调系统媒体路由与音频焦点。
 */
class UsbAudioController private constructor(
    private val context: Context,
    private val settings: AudioOutputSettings
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val usbAudioDevices: Flow<List<AudioDeviceInfo>> = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                trySend(getUsbAudioDevices(audioManager))
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                trySend(getUsbAudioDevices(audioManager))
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        trySend(getUsbAudioDevices(audioManager))
        awaitClose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    val preferredUsbDevice: Flow<AudioDeviceInfo?> = usbAudioDevices.map { devices ->
        devices.firstOrNull { isUsbDevice(it) }
    }

    /**
     * 根据当前设置及外接硬件状态更新 USB 路由与独占会话
     */
    suspend fun applyUsbRoutingIfEnabled() {
        val enabled = settings.config.value.usbExclusive
        if (!enabled) {
            clearUsbRouting()
            return
        }
        val devices = getUsbAudioDevices(audioManager)
        val preferred = devices.firstOrNull { isUsbDevice(it) }
        if (preferred != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!setDevicesForMediaInternal(audioManager, listOf(preferred))) {
                    Log.w(TAG, "Failed to route media to USB DAC ${preferred.productName ?: preferred.id}")
                }
            }
            UsbExclusiveSession.updateRoutingOnly(preferred)
            UsbExclusiveSession.requestExclusiveFocus(context)
            Log.i(TAG, "Routed audio to USB DAC: ${preferred.productName} (id=${preferred.id})")
        } else {
            clearUsbRouting()
        }
    }

    fun clearUsbRouting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!clearUsbRoutingInternal(audioManager)) {
                Log.w(TAG, "Failed to clear USB DAC media routing")
            }
        }
        UsbExclusiveSession.abandonExclusiveFocus(context)
        UsbExclusiveSession.clear()
    }

    /**
     * 在全局协程作用域中持续观察外置设备插拔与设置变更，自动协同路由
     */
    fun startObserving(scope: CoroutineScope) {
        scope.launch {
            preferredUsbDevice.distinctUntilChanged().collectLatest {
                applyUsbRoutingIfEnabled()
            }
        }
        scope.launch {
            settings.config.map { it.usbExclusive }.distinctUntilChanged().collectLatest {
                applyUsbRoutingIfEnabled()
            }
        }
    }

    companion object {
        private const val TAG = "UsbAudioController"

        @Volatile
        private var instance: UsbAudioController? = null

        fun getInstance(context: Context): UsbAudioController =
            instance ?: synchronized(this) {
                instance ?: UsbAudioController(
                    context.applicationContext,
                    AudioOutputSettings.getInstance(context)
                ).also { instance = it }
            }
    }
}

private fun getUsbAudioDevices(audioManager: AudioManager): List<AudioDeviceInfo> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { isUsbDevice(it) }
    } else {
        emptyList()
    }
}

private fun isUsbDevice(device: AudioDeviceInfo): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
    } else {
        false
    }
}

private fun setDevicesForMediaInternal(audioManager: AudioManager, devices: List<AudioDeviceInfo>): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val attributes = devices.mapNotNull(::toAudioDeviceAttributes)
    if (attributes.isEmpty()) return false
    val routed = runCatching {
        val method = AudioManager::class.java.getMethod("setDevicesForMedia", List::class.java)
        method.invoke(audioManager, attributes)
        true
    }.getOrElse { false }
    if (routed) return true
    return setPreferredDevicesForMediaStrategy(audioManager, attributes)
}

private fun clearUsbRoutingInternal(audioManager: AudioManager): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val cleared = runCatching {
        val method = AudioManager::class.java.getMethod("clearDevicesForMedia")
        method.invoke(audioManager)
        true
    }.getOrElse { false }
    if (cleared) return true
    return clearPreferredDevicesForMediaStrategy(audioManager)
}

private fun toAudioDeviceAttributes(device: AudioDeviceInfo): Any? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return runCatching {
        val clazz = Class.forName("android.media.AudioDeviceAttributes")
        val ctor = clazz.getConstructor(AudioDeviceInfo::class.java)
        ctor.newInstance(device)
    }.getOrNull()
}

private fun setPreferredDevicesForMediaStrategy(
    audioManager: AudioManager,
    deviceAttributes: List<Any>
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || deviceAttributes.isEmpty()) return false
    val strategyClass = runCatching {
        Class.forName("android.media.audiopolicy.AudioProductStrategy")
    }.getOrNull() ?: return false
    val mediaStrategy = resolveMediaAudioProductStrategy(strategyClass) ?: return false
    return runCatching {
        val method = AudioManager::class.java.getMethod(
            "setPreferredDevicesForStrategy",
            strategyClass,
            List::class.java
        )
        (method.invoke(audioManager, mediaStrategy, deviceAttributes) as? Boolean) == true
    }.getOrElse { false }
}

private fun clearPreferredDevicesForMediaStrategy(audioManager: AudioManager): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val strategyClass = runCatching {
        Class.forName("android.media.audiopolicy.AudioProductStrategy")
    }.getOrNull() ?: return false
    val mediaStrategy = resolveMediaAudioProductStrategy(strategyClass) ?: return false
    return runCatching {
        val method = AudioManager::class.java.getMethod(
            "removePreferredDeviceForStrategy",
            strategyClass
        )
        (method.invoke(audioManager, mediaStrategy) as? Boolean) == true
    }.getOrElse { false }
}

private fun resolveMediaAudioProductStrategy(strategyClass: Class<*>): Any? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return runCatching {
        val getStrategies = strategyClass.getMethod("getAudioProductStrategies")
        val supportsAudioAttributes = strategyClass.getMethod("supportsAudioAttributes", AudioAttributes::class.java)
        val mediaAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val strategies = getStrategies.invoke(null) as? List<*> ?: return@runCatching null
        strategies.firstOrNull { strategy ->
            (supportsAudioAttributes.invoke(strategy, mediaAttributes) as? Boolean) == true
        }
    }.getOrNull()
}
