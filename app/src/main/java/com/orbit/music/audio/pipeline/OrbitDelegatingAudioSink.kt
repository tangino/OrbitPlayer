package com.orbit.music.audio.pipeline

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer

/**
 * 智能音频汇聚调度器（Delegating AudioSink）：
 * 1. 动态根据用户配置（AudioTrack / AAudio / OpenSL ES / USB 独占模式）将解码音频流路由至最底层；
 * 2. 支持设置项热切换（无需销毁重建上层 ExoPlayer 实例与寻源状态机）；
 * 3. 当 Oboe 原生通道加载失败或驱动异常时，自动无缝降级至系统 [DefaultAudioSink]，保证任何情况下均永不中断播放。
 */
@UnstableApi
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class OrbitDelegatingAudioSink(
    private val context: Context,
    private val customAudioProcessors: List<AudioProcessor>,
    private val enableFloatOutput: Boolean = true,
    private val enableAudioTrackPlaybackParams: Boolean = true
) : AudioSink {

    private val settings = AudioOutputSettings.getInstance(context)

    private val defaultAudioSink: DefaultAudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessors(customAudioProcessors.toTypedArray())
        .build()

    private var oboeAudioSink: OboeAudioSink? = null
    private var activeSink: AudioSink = defaultAudioSink
    private var isUsingOboe: Boolean = false

    private var currentListener: AudioSink.Listener? = null
    private var currentAudioAttributes: AudioAttributes = AudioAttributes.DEFAULT
    private var currentPlaybackParameters: PlaybackParameters = PlaybackParameters.DEFAULT
    private var currentSkipSilenceEnabled: Boolean = false
    private var currentVolume: Float = 1.0f
    private var currentAudioSessionId: Int = 0

    init {
        updateActiveSink(isInitial = true)
    }

    private fun updateActiveSink(isInitial: Boolean = false) {
        val config = settings.config.value
        val backend = config.backend
        val usbExclusive = config.usbExclusive

        val preferOboe = usbExclusive ||
            backend == AudioOutputConfig.BACKEND_AAUDIO ||
            backend == AudioOutputConfig.BACKEND_OPENSLES

        val canUseOboe = preferOboe && OboeAudioOutput.ensureLoaded()

        if (canUseOboe) {
            val audioApi = if (!usbExclusive && backend == AudioOutputConfig.BACKEND_OPENSLES) 2 else 1
            val deviceId = if (usbExclusive) resolveUsbOutputDeviceId(context) else 0

            if (!isUsingOboe || oboeAudioSink == null) {
                Log.i(TAG, "激活 OboeAudioSink: api=$audioApi, exclusive=$usbExclusive, deviceId=$deviceId")
                oboeAudioSink?.release()
                oboeAudioSink = OboeAudioSink(
                    audioApi = audioApi,
                    exclusive = usbExclusive,
                    processors = customAudioProcessors,
                    deviceId = deviceId,
                    appContext = context.applicationContext
                ).apply {
                    currentListener?.let { setListener(it) }
                    setAudioAttributes(currentAudioAttributes)
                    setPlaybackParameters(currentPlaybackParameters)
                    setSkipSilenceEnabled(currentSkipSilenceEnabled)
                    setVolume(currentVolume)
                    if (currentAudioSessionId != 0) setAudioSessionId(currentAudioSessionId)
                }
            }
            activeSink = oboeAudioSink!!
            isUsingOboe = true
        } else {
            if (isUsingOboe || isInitial) {
                Log.i(TAG, "激活系统 DefaultAudioSink (AudioTrack 路线)")
                oboeAudioSink?.let {
                    it.flush()
                    it.release()
                }
                oboeAudioSink = null
                activeSink = defaultAudioSink
                isUsingOboe = false
            }
        }
    }

    override fun setListener(listener: AudioSink.Listener) {
        currentListener = listener
        defaultAudioSink.setListener(listener)
        oboeAudioSink?.setListener(listener)
    }

    override fun setClock(clock: Clock) {
        defaultAudioSink.setClock(clock)
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        activeSink.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }

    override fun supportsFormat(format: Format): Boolean {
        updateActiveSink()
        return activeSink.supportsFormat(format)
    }

    override fun getFormatSupport(format: Format): Int {
        updateActiveSink()
        return activeSink.getFormatSupport(format)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        return activeSink.getCurrentPositionUs(sourceEnded)
    }

    @Throws(AudioSink.ConfigurationException::class)
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        updateActiveSink()
        try {
            activeSink.configure(inputFormat, specifiedBufferSize, outputChannels)
        } catch (e: Exception) {
            if (isUsingOboe) {
                Log.w(TAG, "OboeAudioSink 配置失败，自动降级至系统 DefaultAudioSink: ${e.message}")
                isUsingOboe = false
                activeSink = defaultAudioSink
                activeSink.configure(inputFormat, specifiedBufferSize, outputChannels)
            } else {
                throw e
            }
        }
    }

    override fun play() {
        activeSink.play()
    }

    override fun handleDiscontinuity() {
        activeSink.handleDiscontinuity()
    }

    @Throws(AudioSink.InitializationException::class, AudioSink.WriteException::class)
    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        return activeSink.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    @Throws(AudioSink.WriteException::class)
    override fun playToEndOfStream() {
        activeSink.playToEndOfStream()
    }

    override fun isEnded(): Boolean = activeSink.isEnded

    override fun hasPendingData(): Boolean = activeSink.hasPendingData()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        currentPlaybackParameters = playbackParameters
        activeSink.setPlaybackParameters(playbackParameters)
    }

    override fun getPlaybackParameters(): PlaybackParameters = activeSink.playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        currentSkipSilenceEnabled = skipSilenceEnabled
        activeSink.setSkipSilenceEnabled(skipSilenceEnabled)
    }

    override fun getSkipSilenceEnabled(): Boolean = activeSink.skipSilenceEnabled

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        currentAudioAttributes = audioAttributes
        defaultAudioSink.setAudioAttributes(audioAttributes)
        oboeAudioSink?.setAudioAttributes(audioAttributes)
    }

    override fun getAudioAttributes(): AudioAttributes = activeSink.audioAttributes ?: currentAudioAttributes

    override fun setAudioSessionId(audioSessionId: Int) {
        currentAudioSessionId = audioSessionId
        defaultAudioSink.setAudioSessionId(audioSessionId)
        oboeAudioSink?.setAudioSessionId(audioSessionId)
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        defaultAudioSink.setAuxEffectInfo(auxEffectInfo)
        oboeAudioSink?.setAuxEffectInfo(auxEffectInfo)
    }

    override fun enableTunnelingV21() {
        defaultAudioSink.enableTunnelingV21()
    }

    override fun disableTunneling() {
        defaultAudioSink.disableTunneling()
    }

    override fun setVolume(volume: Float) {
        currentVolume = volume
        activeSink.setVolume(volume)
    }

    override fun pause() {
        activeSink.pause()
    }

    override fun flush() {
        updateActiveSink()
        activeSink.flush()
    }

    override fun reset() {
        defaultAudioSink.reset()
        oboeAudioSink?.reset()
    }

    override fun release() {
        defaultAudioSink.release()
        oboeAudioSink?.release()
        oboeAudioSink = null
    }

    private fun resolveUsbOutputDeviceId(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return 0
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
            ?.id
            ?: 0
    }

    companion object {
        private const val TAG = "OrbitDelegatingSink"
    }
}
