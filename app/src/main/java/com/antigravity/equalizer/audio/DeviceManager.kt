package com.antigravity.equalizer.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.antigravity.equalizer.data.model.AudioDeviceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 监听音频输出设备（扬声器、蓝牙、有线耳机、USB DAC）
 */
class DeviceManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _currentDeviceType = MutableStateFlow(AudioDeviceType.SPEAKER)
    val currentDeviceType: StateFlow<AudioDeviceType> = _currentDeviceType.asStateFlow()

    private val _currentDeviceName = MutableStateFlow("Phone Speaker")
    val currentDeviceName: StateFlow<String> = _currentDeviceName.asStateFlow()

    private var deviceCallback: Any? = null
    private var noisyReceiver: BroadcastReceiver? = null
    private var legacyDeviceReceiver: BroadcastReceiver? = null

    fun startMonitoring(onDeviceChanged: (AudioDeviceType, String) -> Unit) {
        updateCurrentDevice(onDeviceChanged)

        // 监听音频设备插入/拔出 (Android 6.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val callback = object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    updateCurrentDevice(onDeviceChanged)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    updateCurrentDevice(onDeviceChanged)
                }
            }
            audioManager.registerAudioDeviceCallback(callback, null)
            deviceCallback = callback
        } else {
            // Android 5.0 ~ 5.1 (API < 23) 向下兼容广播监听：有线耳机拔插与蓝牙连接状态
            legacyDeviceReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    updateCurrentDevice(onDeviceChanged)
                }
            }
            val legacyFilter = IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                @Suppress("DEPRECATION")
                addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            }
            context.registerReceiver(legacyDeviceReceiver, legacyFilter)
        }

        // 监听耳机拔出广播 ACTION_AUDIO_BECOMING_NOISY
        noisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    Log.i(TAG, "Audio becoming noisy (headphones unplugged)")
                    updateCurrentDevice(onDeviceChanged)
                }
            }
        }
        context.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    private fun updateCurrentDevice(onDeviceChanged: (AudioDeviceType, String) -> Unit) {
        var devType = AudioDeviceType.SPEAKER
        var devName = "Phone Speaker"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (dev in devices) {
                when (dev.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                        devType = AudioDeviceType.BLUETOOTH_A2DP
                        devName = if (dev.productName.isNotBlank()) dev.productName.toString() else "Bluetooth Audio"
                        break
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                        devType = AudioDeviceType.WIRED_HEADSET
                        devName = "Wired Headphones"
                        break
                    }
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> {
                        devType = AudioDeviceType.USB_DAC
                        devName = if (dev.productName.isNotBlank()) dev.productName.toString() else "USB DAC"
                        break
                    }
                }
            }
        } else {
            // Android 5.0 (API 21) 回退检测
            @Suppress("DEPRECATION")
            when {
                audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn -> {
                    devType = AudioDeviceType.BLUETOOTH_A2DP
                    devName = "Bluetooth Audio"
                }
                audioManager.isWiredHeadsetOn -> {
                    devType = AudioDeviceType.WIRED_HEADSET
                    devName = "Wired Headphones"
                }
                else -> {
                    devType = AudioDeviceType.SPEAKER
                    devName = "Phone Speaker"
                }
            }
        }

        _currentDeviceType.value = devType
        _currentDeviceName.value = devName
        Log.i(TAG, "Active output audio device: $devName ($devType)")
        onDeviceChanged(devType, devName)
    }

    fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCallback != null) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback as android.media.AudioDeviceCallback)
            deviceCallback = null
        }
        legacyDeviceReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering legacy receiver", e)
            }
            legacyDeviceReceiver = null
        }
        noisyReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
            noisyReceiver = null
        }
    }

    companion object {
        private const val TAG = "DeviceManager"
    }
}
