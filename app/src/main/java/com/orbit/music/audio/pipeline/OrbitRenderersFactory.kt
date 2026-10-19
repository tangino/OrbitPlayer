package com.orbit.music.audio.pipeline

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.util.Log
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.util.ArrayList

/**
 * OrBitPlayer 定制渲染器工厂：
 * 1. 过滤系统已知存在严重崩溃缺陷的硬解码器（如高通/小米平台的 c2.qti.alac.sw.decoder）；
 * 2. 注入 FFmpeg 软解码器，支持大帧 FLAC、ALAC、特殊封装格式高保真解码；
 * 3. 动态配置音频输出后端（Oboe AAudio / OpenSL ES 或系统默认 AudioTrack），支持 USB 独占（Bit-Perfect）源码输出；
 * 4. 串联 OrBitPlayer 核心均衡器与音效 DSP 处理器。
 */
@UnstableApi
class OrbitRenderersFactory(
    private val context: Context,
    private val customAudioProcessors: List<AudioProcessor> = emptyList()
) : DefaultRenderersFactory(context) {

    private val settings = AudioOutputSettings.getInstance(context)

    companion object {
        private const val TAG = "OrbitRenderersFactory"
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        // 过滤系统中有已知致命缺陷的硬件/软解厂商实现（如高通平台 c2.qti.alac.sw.decoder 瞬崩问题）
        val safeMediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val decoders = mediaCodecSelector.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            decoders.filterNot { decoder ->
                decoder.name.equals("c2.qti.alac.sw.decoder", ignoreCase = true) ||
                    (mimeType.contains("alac", ignoreCase = true) && decoder.name.contains("alac", ignoreCase = true))
            }
        }

        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            safeMediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )

        val config = settings.config.value
        val shouldPreferFfmpeg = config.preferFfmpeg || extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER

        if (out.none { it is FfmpegAudioRenderer }) {
            val renderer = FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
            if (shouldPreferFfmpeg) {
                out.add(0, renderer)
                Log.i(TAG, "FfmpegAudioRenderer 挂载至解码队列首位（优先软解）")
            } else {
                out.add(renderer)
                Log.i(TAG, "FfmpegAudioRenderer 挂载至解码队列末位（兜底软解）")
            }
        }

        // 大帧 FLAC 与部分复杂 MPEG 音频前置挂载兼容性软解
        out.add(0, FfmpegAudioRenderer(eventHandler, eventListener, audioSink, null, ::needsCompatibleSoftwareAudioDecoder))
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink? {
        Log.i(TAG, "创建 OrbitDelegatingAudioSink（智能后端调度与热切换）")
        return OrbitDelegatingAudioSink(
            context = context,
            customAudioProcessors = customAudioProcessors,
            enableFloatOutput = enableFloatOutput,
            enableAudioTrackPlaybackParams = enableAudioTrackPlaybackParams
        )
    }
}
