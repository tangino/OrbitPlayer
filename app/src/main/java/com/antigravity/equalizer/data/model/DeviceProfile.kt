package com.antigravity.equalizer.data.model

enum class AudioDeviceType {
    SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH_A2DP,
    BLUETOOTH_LE,
    USB_DAC,
    OTHER
}

data class DeviceProfile(
    val deviceId: String,
    val deviceName: String,
    val deviceType: AudioDeviceType,
    val presetId: String,
    val enabled: Boolean = true
)
