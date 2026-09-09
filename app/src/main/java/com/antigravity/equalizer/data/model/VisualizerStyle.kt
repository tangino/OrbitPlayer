package com.antigravity.equalizer.data.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.antigravity.equalizer.R

/**
 * 频谱可视化形态样式
 */
enum class VisualizerStyle(
    val id: String,
    @StringRes val titleRes: Int
) {
    BARS_WITH_PEAKS(
        id = "bars_with_peaks",
        titleRes = R.string.visualizer_style_bars_with_peaks
    ),
    AURORA_MOUNTAIN(
        id = "aurora_mountain",
        titleRes = R.string.visualizer_style_aurora_mountain
    ),
    MIRRORED_BARS(
        id = "mirrored_bars",
        titleRes = R.string.visualizer_style_mirrored_bars
    ),
    OFF(
        id = "off",
        titleRes = R.string.visualizer_style_off
    );

    companion object {
        fun fromId(id: String?): VisualizerStyle {
            return values().find { it.id == id } ?: BARS_WITH_PEAKS
        }
    }
}

/**
 * 频谱发光配色模式
 */
enum class VisualizerColorScheme(
    val id: String,
    @StringRes val titleRes: Int,
    val primaryColor: Color,
    val secondaryColor: Color,
    val peakColor: Color
) {
    FOLLOW_BACKGROUND(
        id = "follow_background",
        titleRes = R.string.visualizer_color_follow_background,
        primaryColor = Color(0xFF00E5FF),
        secondaryColor = Color(0xFF7C4DFF),
        peakColor = Color(0xFFFFFFFF)
    ),
    NEON_CYAN_PURPLE(
        id = "neon_cyan_purple",
        titleRes = R.string.visualizer_color_neon_cyan_purple,
        primaryColor = Color(0xFF00F0FF),
        secondaryColor = Color(0xFFB026FF),
        peakColor = Color(0xFFE0FFFF)
    ),
    FIRE_AMBER(
        id = "fire_amber",
        titleRes = R.string.visualizer_color_fire_amber,
        primaryColor = Color(0xFFFF9100),
        secondaryColor = Color(0xFFFF1744),
        peakColor = Color(0xFFFFD54F)
    ),
    ELECTRIC_GREEN(
        id = "electric_green",
        titleRes = R.string.visualizer_color_electric_green,
        primaryColor = Color(0xFF00E676),
        secondaryColor = Color(0xFF00B0FF),
        peakColor = Color(0xFFB9F6CA)
    ),
    CUSTOM(
        id = "custom",
        titleRes = R.string.visualizer_color_custom,
        primaryColor = Color(0xFF00E5FF),
        secondaryColor = Color(0xFF7C4DFF),
        peakColor = Color(0xFFFFFFFF)
    );

    companion object {
        fun fromId(id: String?): VisualizerColorScheme {
            return values().find { it.id == id } ?: FOLLOW_BACKGROUND
        }
    }
}
