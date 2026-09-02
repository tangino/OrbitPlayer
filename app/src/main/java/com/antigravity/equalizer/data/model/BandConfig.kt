package com.antigravity.equalizer.data.model

enum class FilterType(val id: Int, val displayName: String) {
    PEAKING(0, "Peaking"),
    LOW_SHELF(1, "Low Shelf"),
    HIGH_SHELF(2, "High Shelf"),
    LOW_PASS(3, "Low Pass"),
    HIGH_PASS(4, "High Pass"),
    BAND_PASS(5, "Band Pass"),
    NOTCH(6, "Notch")
}

data class BandConfig(
    val id: Int,
    val enabled: Boolean = true,
    val type: FilterType = FilterType.PEAKING,
    val frequency: Float,
    val gainDb: Float = 0f,
    val q: Float = 1.0f
)
