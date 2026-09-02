package com.antigravity.equalizer.data.model

data class Preset(
    val id: String,
    val name: String,
    val isCustom: Boolean = false,
    val preampGainDb: Float = 0f,
    val bands10Gain: List<Float> = List(10) { 0f },
    val bands15Gain: List<Float>? = null,
    val bands20Gain: List<Float>? = null,
    val limiterEnabled: Boolean = true,
    val limiterThresholdDb: Float = -0.2f
)
