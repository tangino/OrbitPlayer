package com.antigravity.equalizer.data.model

/**
 * 播放页进度条滑块拖尾动效样式
 */
enum class ProgressTrailStyle(val id: String) {
    /** 图片同款高能霓虹脉冲波形与交织拱波 */
    NEON_PULSE("neon_pulse"),

    /** 彗星双螺旋 3D 立体光轨与星尘微粒 */
    COMET_HELIX("comet_helix"),

    /** 经典极简（无额外拖尾动效） */
    MINIMAL("minimal");

    companion object {
        fun fromId(id: String?): ProgressTrailStyle {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: NEON_PULSE
        }
    }
}
