package com.antigravity.equalizer.data.repository

import com.antigravity.equalizer.data.model.AudioDeviceType
import com.antigravity.equalizer.data.model.DeviceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设备专属 EQ 配置仓库（例如：Sony WH-1000XM5 -> Rock, Phone Speaker -> Bass Boost）
 */
class DeviceProfileRepository {

    private val _profiles = MutableStateFlow<Map<String, DeviceProfile>>(
        mapOf(
            "speaker_default" to DeviceProfile(
                deviceId = "speaker_default",
                deviceName = "Phone Speaker",
                deviceType = AudioDeviceType.SPEAKER,
                presetId = "pop",
                enabled = true
            ),
            "bluetooth_default" to DeviceProfile(
                deviceId = "bluetooth_default",
                deviceName = "Bluetooth Audio",
                deviceType = AudioDeviceType.BLUETOOTH_A2DP,
                presetId = "rock",
                enabled = true
            ),
            "wired_default" to DeviceProfile(
                deviceId = "wired_default",
                deviceName = "Wired Headphones",
                deviceType = AudioDeviceType.WIRED_HEADSET,
                presetId = "classical",
                enabled = true
            )
        )
    )
    val profiles: StateFlow<Map<String, DeviceProfile>> = _profiles.asStateFlow()

    fun getProfileForDevice(deviceName: String, type: AudioDeviceType): DeviceProfile? {
        val current = _profiles.value
        return current[deviceName] ?: current.values.firstOrNull { it.deviceType == type }
    }

    fun saveProfile(profile: DeviceProfile) {
        val current = _profiles.value.toMutableMap()
        current[profile.deviceName] = profile
        _profiles.value = current
    }

    companion object {
        val instance: DeviceProfileRepository by lazy { DeviceProfileRepository() }
    }
}
