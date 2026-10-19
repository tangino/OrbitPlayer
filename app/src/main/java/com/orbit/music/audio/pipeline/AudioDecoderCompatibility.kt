package com.orbit.music.audio.pipeline

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * 解码器兼容性判定：
 * 1. 小米 / HyperOS 平台的 Codec2 FLAC 硬解会将输入缓冲区限制在 32 KiB，当单帧大于 32KB 时，
 *    Media3 会静默跳帧导致播放无声；因此超过 32KB 的大帧 FLAC 必须走软件解码；
 * 2. MPEG 音频（MP3）采用成熟的软解可保证各种复杂 ID3v2 标签与编码下的稳定性。
 */
@UnstableApi
internal fun needsCompatibleSoftwareAudioDecoder(format: Format): Boolean = when (format.sampleMimeType) {
    MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L1, MimeTypes.AUDIO_MPEG_L2 -> true
    MimeTypes.AUDIO_FLAC -> format.maxInputSize > 32 * 1024
    else -> false
}
