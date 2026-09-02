package com.antigravity.equalizer.audio

import android.content.Context
import android.media.audiofx.BassBoost as AndroidBassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.os.Build
import android.util.Log
import com.antigravity.equalizer.native.NativeDSP
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * 统筹管理 Android 系统 AudioEffect 与 Native C++ DSP 的核心管理器
 * 1. 发烧级声学调谐：高频温润顺滑、消除 3k~6k 刺耳尖锐感；
 * 2. 低频浑厚饱满：精准辐射 60Hz~160Hz 丰满温暖频段，下潜深沉有弹性；
 * 3. 彻底禁用 MBC 动态压缩，恢复纯净 Hi-Fi 声场与大动态。
 */
class AudioEffectManager private constructor(private val context: Context) {

    private val nativeDSP = NativeDSP.instance
    private val activeSessions = ConcurrentHashMap<Int, SessionEffects>()

    private var isEnabled = true
    private var currentPreampDb = 0f
    private val defaultFrequencies = floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private var currentBandGains = FloatArray(10) { 0f }
    private var isBassBoostEnabled = false
    private var currentBassBoostStrength = 0.5f
    private var isTrebleBoostEnabled = false
    private var currentTrebleBoostStrength = 0.5f

    // 限幅器参数
    private var isLimiterEnabled = false
    private var limiterThresholdDb = -2.5f  // 设为 -2.5dB 实用防削波门限，开启时明显消除过载毛刺，关闭时 1:1 完全直通
    private var limiterAttackMs = 1.0f
    private var limiterReleaseMs = 60.0f
    private var limiterRatio = 10.0f

    // 动态压缩器参数
    private var isCompressorEnabled = false
    private var compressorThresholdDb = -18.0f
    private var compressorRatio = 4.0f
    private var compressorAttackMs = 10.0f
    private var compressorReleaseMs = 100.0f
    private var compressorMakeupGainDb = 2.0f

    data class SessionEffects(
        val sessionId: Int,
        var systemEqualizer: Equalizer? = null,
        var systemBassBoost: AndroidBassBoost? = null,
        var dynamicsProcessing: DynamicsProcessing? = null,
        var visualizer: Visualizer? = null
    )

    init {
        nativeDSP.setEnabled(false)
        nativeDSP.setGraphicMode(10)
        nativeDSP.setLimiter(false)
    }

    /**
     * 为指定 AudioSessionId 挂载音效
     */
    @Synchronized
    fun attachSession(sessionId: Int) {
        if (activeSessions.containsKey(sessionId)) {
            Log.d(TAG, "AudioSession $sessionId is already attached.")
            return
        }

        Log.i(TAG, "Attaching AudioEffects to session: $sessionId")
        val effects = SessionEffects(sessionId)

        // 1. 在 Android 9+ 优先初始化 DynamicsProcessing (覆盖 Pre-EQ + 动态压缩器 MBC + 安全防削波 Limiter)
        var isDynamicsProcessingAttached = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                // 关键：在 Android 系统规范中，只有 VARIANT_FAVOR_FREQUENCY_RESOLUTION 才能完整支持 MBC 多频带/全频带动态压缩器！
                // 我们通过将 0dB 平直频段完全置为 Bypass 旁路，彻底解决频域分频在 Flat 模式下的发闷损耗！
                val dpConfig = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,         // 立体声
                    true, 10,  // Pre-EQ 10 bands
                    true, 1,   // 启用 1 个波段的动态压缩器 (Full-band Dynamic Compressor)
                    false, 0,  // 关闭 Post-EQ
                    true       // 启用 Limiter 防削波
                ).build()

                val dp = DynamicsProcessing(0, sessionId, dpConfig)
                dp.enabled = this.isEnabled
                syncAllGainsToDynamicsProcessing(dp)
                syncCompressorToDynamicsProcessing(dp)
                syncLimiterToDynamicsProcessing(dp)
                effects.dynamicsProcessing = dp
                isDynamicsProcessingAttached = true
                Log.i(TAG, "Attached Pure DynamicsProcessing to session $sessionId (EQ + Compressor + Limiter)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create DynamicsProcessing for session $sessionId: ${e.message}")
            }
        }

        // 2. 仅在不支持或无法创建 DynamicsProcessing 时，才启用传统系统 Equalizer 作为 Fallback！
        // 关键核心：绝对不能让 Equalizer 与 DynamicsProcessing 两个系统 EQ 同时生效，否则会造成双重滤波串联和严重的相位对冲、导致声音发闷浑浊！
        if (!isDynamicsProcessingAttached) {
            try {
                val eq = Equalizer(0, sessionId)
                eq.enabled = this.isEnabled
                syncAllGainsToSystemEqualizer(eq)
                effects.systemEqualizer = eq
                Log.i(TAG, "Attached Fallback System Equalizer to session $sessionId, bands: ${eq.numberOfBands}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create Equalizer for session $sessionId: ${e.message}")
            }
        }

        // 3. 初始化系统原生 BassBoost (强劲浑厚低音支持)
        try {
            val bb = AndroidBassBoost(0, sessionId)
            bb.enabled = this.isEnabled && this.isBassBoostEnabled
            if (bb.strengthSupported) {
                val targetStrength = (this.currentBassBoostStrength * 1000).toInt().coerceIn(0, 1000).toShort()
                bb.setStrength(targetStrength)
            }
            effects.systemBassBoost = bb
            Log.i(TAG, "Attached System BassBoost to session $sessionId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create BassBoost for session $sessionId: ${e.message}")
        }

        // 4. 初始化 Visualizer 频谱
        try {
            val visualizer = Visualizer(sessionId)
            visualizer.captureSize = Visualizer.getCaptureSizeRange()[1]
            visualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            visualizer.enabled = true
            effects.visualizer = visualizer
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create Visualizer for session $sessionId: ${e.message}")
        }

        activeSessions[sessionId] = effects
    }

    /**
     * 释放指定 AudioSession
     */
    @Synchronized
    fun detachSession(sessionId: Int) {
        val effects = activeSessions.remove(sessionId) ?: return
        Log.i(TAG, "Detaching effects for session: $sessionId")

        try {
            effects.systemEqualizer?.enabled = false
            effects.systemEqualizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Equalizer for session $sessionId", e)
        }

        try {
            effects.systemBassBoost?.enabled = false
            effects.systemBassBoost?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing BassBoost for session $sessionId", e)
        }

        try {
            effects.dynamicsProcessing?.enabled = false
            effects.dynamicsProcessing?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing DynamicsProcessing for session $sessionId", e)
        }

        try {
            effects.visualizer?.enabled = false
            effects.visualizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Visualizer for session $sessionId", e)
        }
    }

    /**
     * 全局开关切换
     */
    @Synchronized
    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
        nativeDSP.setEnabled(enabled)

        activeSessions.values.forEach { session ->
            try {
                session.systemEqualizer?.enabled = enabled
                session.systemBassBoost?.enabled = enabled && isBassBoostEnabled
                session.dynamicsProcessing?.let { dp ->
                    dp.enabled = enabled
                    syncAllGainsToDynamicsProcessing(dp)
                    syncCompressorToDynamicsProcessing(dp)
                    syncLimiterToDynamicsProcessing(dp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating enabled status on session ${session.sessionId}", e)
            }
        }
    }

    /**
     * 调节特定频段增益 (dB)
     */
    @Synchronized
    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex in currentBandGains.indices) {
            currentBandGains[bandIndex] = gainDb
        }
        nativeDSP.setGraphicBandGain(bandIndex, gainDb)

        activeSessions.values.forEach { session ->
            session.systemEqualizer?.let { eq -> syncAllGainsToSystemEqualizer(eq) }
            session.dynamicsProcessing?.let { dp -> syncAllGainsToDynamicsProcessing(dp) }
        }
    }

    /**
     * 调节低音增强 (Bass Boost) - 增强 60Hz~160Hz 浑厚度
     */
    @Synchronized
    fun setBassBoost(enabled: Boolean, strength: Float) {
        this.isBassBoostEnabled = enabled
        this.currentBassBoostStrength = strength
        nativeDSP.setBassBoost(enabled, strength)

        activeSessions.values.forEach { session ->
            session.systemBassBoost?.let { bb ->
                try {
                    bb.enabled = this.isEnabled && enabled
                    if (bb.strengthSupported) {
                        val targetStrength = (strength * 1000).toInt().coerceIn(0, 1000).toShort()
                        bb.setStrength(targetStrength)
                    }
                    Unit
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting system BassBoost strength", e)
                }
            }
            session.systemEqualizer?.let { eq -> syncAllGainsToSystemEqualizer(eq) }
            session.dynamicsProcessing?.let { dp -> syncAllGainsToDynamicsProcessing(dp) }
        }
    }

    /**
     * 调节高音增强 (Treble Boost) - 专注于 8kHz 以上超高频空气感，消除齿音尖锐感
     */
    @Synchronized
    fun setTrebleBoost(enabled: Boolean, strength: Float) {
        this.isTrebleBoostEnabled = enabled
        this.currentTrebleBoostStrength = strength
        nativeDSP.setTrebleBoost(enabled, strength)

        activeSessions.values.forEach { session ->
            session.systemEqualizer?.let { eq -> syncAllGainsToSystemEqualizer(eq) }
            session.dynamicsProcessing?.let { dp -> syncAllGainsToDynamicsProcessing(dp) }
        }
    }

    /**
     * 将 10 段增益平滑映射至系统原生 5 段 Equalizer
     */
    private fun syncAllGainsToSystemEqualizer(eq: Equalizer) {
        try {
            val bandRange = eq.bandLevelRange
            val minMb = bandRange[0].toInt()
            val maxMb = bandRange[1].toInt()
            val numSystemBands = eq.numberOfBands.toInt()

            for (sysBand in 0 until numSystemBands) {
                val centerFreqHz = eq.getCenterFreq(sysBand.toShort()) / 1000f // Hz

                var totalWeight = 0f
                var weightedGain = 0f

                for (i in defaultFrequencies.indices) {
                    val f = defaultFrequencies[i]
                    val distance = abs(Math.log((f / centerFreqHz).toDouble())).toFloat()
                    if (distance < 1.1f) {
                        val weight = 1f / (1f + distance * distance * 1.5f)
                        var extraBoost = 0f

                        // 高音增强：只在 >= 8000Hz 超高频段平滑提升，避开 3k~6k 尖刺齿音区
                        if (isTrebleBoostEnabled && f >= 8000f) {
                            extraBoost += currentTrebleBoostStrength * 3.5f
                        }
                        // 低音增强：在 60Hz~125Hz 饱满区给予温润浑厚补偿
                        if (isBassBoostEnabled && f in 60f..150f) {
                            extraBoost += currentBassBoostStrength * 3.0f
                        }

                        weightedGain += (currentBandGains[i] + extraBoost) * weight
                        totalWeight += weight
                    }
                }

                val finalGainDb = if (totalWeight > 0f) weightedGain / totalWeight else 0f
                val targetMilliBels = (finalGainDb * 100).toInt().coerceIn(minMb, maxMb).toShort()

                eq.setBandLevel(sysBand.toShort(), targetMilliBels)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in syncAllGainsToSystemEqualizer", e)
        }
    }

    /**
     * 将增益同步至 DynamicsProcessing Pre-EQ
     * 核心音质优化：当频段增益为 0.0dB（平直 Flat 状态）时，将该频段置为 Bypass 旁路，杜绝频域加窗分频导致的沉闷涂抹！
     */
    private fun syncAllGainsToDynamicsProcessing(dp: DynamicsProcessing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                for (i in currentBandGains.indices) {
                    val freq = defaultFrequencies[i]
                    var gain = currentBandGains[i]

                    // 超高频空气感提升 (>= 8000Hz)
                    if (isTrebleBoostEnabled && freq >= 8000f) {
                        gain += currentTrebleBoostStrength * 3.5f
                    }
                    // 低频温暖浑厚提升 (60Hz ~ 150Hz)
                    if (isBassBoostEnabled && freq in 60f..150f) {
                        gain += currentBassBoostStrength * 3.0f
                    }

                    // 增益绝对值极小视为平直直通，设为 false 彻底物理旁路该频段滤波器
                    val isBandActive = this.isEnabled && abs(gain) > 0.05f
                    val bandConfig = DynamicsProcessing.EqBand(isBandActive, freq, gain)
                    dp.setPreEqBandAllChannelsTo(i, bandConfig)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in syncAllGainsToDynamicsProcessing", e)
            }
        }
    }

    /**
     * 将动态压缩器参数同步至 DynamicsProcessing 的 MBC (Multi-Band Compressor)
     * 开启时：-18dB 门限 + 4:1 压缩比 + 3dB 增益补偿，弱音饱满浮现，效果极其显著！
     * 关闭时：完全 1:1 直通旁路，补偿 0dB，绝不损耗声音动态！
     */
    private fun syncCompressorToDynamicsProcessing(dp: DynamicsProcessing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val shouldEnable = this.isEnabled && this.isCompressorEnabled
                val mbcBand = DynamicsProcessing.MbcBand(
                    shouldEnable,                                 // 仅在开关开启时启用
                    20000.0f,                                     // 覆盖人耳全频段
                    compressorAttackMs,                           // 10ms 快速启控
                    compressorReleaseMs,                          // 100ms 平滑释放
                    if (shouldEnable) compressorRatio else 1.0f,  // 关闭时 1:1 无压缩
                    if (shouldEnable) compressorThresholdDb else 0.0f, // 关闭时 0dB 门限
                    6.0f,                                         // 6dB 软拐点
                    -90.0f,                                       // 噪声门
                    1.0f,                                         // 扩展比 1:1
                    0.0f,                                         // preGain 0dB
                    if (shouldEnable) compressorMakeupGainDb else 0.0f // 开启补偿 2.5dB~3dB，关闭严格 0dB
                )
                dp.setMbcBandAllChannelsTo(0, mbcBand)
            } catch (e: Exception) {
                Log.e(TAG, "Error in syncCompressorToDynamicsProcessing", e)
            }
        }
    }

    /**
     * 将限幅器参数同步至 DynamicsProcessing 的 Limiter
     * 开启时：-2.5dB 门限 + 10:1 砖墙比率，有效压平过载毛刺防爆音，听感显著！
     * 关闭时：1:1 直通，绝不对音频进行任何限制与压缩！
     */
    private fun syncLimiterToDynamicsProcessing(dp: DynamicsProcessing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val shouldEnable = this.isEnabled && this.isLimiterEnabled
                val limiter = DynamicsProcessing.Limiter(
                    true,                                         // inUse 必须为 true 保持底层管线激活
                    shouldEnable,                                 // enabled 随开关切换
                    0,                                            // linkGroup
                    limiterAttackMs,                              // 1ms 瞬态压制
                    limiterReleaseMs,                             // 60ms 释放
                    if (shouldEnable) limiterRatio else 1.0f,     // 开启 10:1 砖墙，关闭 1:1 直通
                    if (shouldEnable) limiterThresholdDb else 0.0f, // 开启 -2.5dB 门限，关闭 0dB
                    0.0f                                          // postGain 0dB
                )
                dp.setLimiterAllChannelsTo(limiter)
            } catch (e: Exception) {
                Log.e(TAG, "Error in syncLimiterToDynamicsProcessing", e)
            }
        }
    }

    /**
     * 调节前级放大 Preamp (dB)
     */
    @Synchronized
    fun setPreampGain(gainDb: Float) {
        currentPreampDb = gainDb
        nativeDSP.setPreampGain(gainDb)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activeSessions.values.forEach { session ->
                session.dynamicsProcessing?.let { dp ->
                    try {
                        dp.setInputGainAllChannelsTo(gainDb)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to set DynamicsProcessing input gain", e)
                    }
                }
            }
        }
    }

    /**
     * 开关与设置动态压缩器 (Compressor)
     */
    @Synchronized
    fun setCompressorEnabled(
        enabled: Boolean,
        thresholdDb: Float = -18f,
        ratio: Float = 4f,
        attackMs: Float = 10f,
        releaseMs: Float = 100f,
        makeupGainDb: Float = 3f
    ) {
        this.isCompressorEnabled = enabled
        this.compressorThresholdDb = thresholdDb
        this.compressorRatio = ratio
        this.compressorAttackMs = attackMs
        this.compressorReleaseMs = releaseMs
        this.compressorMakeupGainDb = makeupGainDb

        // 同步给 Native C++ DSP
        nativeDSP.setCompressor(enabled, thresholdDb, ratio, attackMs, releaseMs, makeupGainDb)

        // 核心：立即同步给所有活跃的系统 AudioSession
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activeSessions.values.forEach { session ->
                session.dynamicsProcessing?.let { dp ->
                    syncCompressorToDynamicsProcessing(dp)
                }
            }
        }
        Log.i(TAG, "Compressor updated: enabled=$enabled, threshold=$thresholdDb dB, ratio=$ratio:1")
    }

    /**
     * 开关与设置 Limiter 防削波
     */
    @Synchronized
    fun setLimiterEnabled(enabled: Boolean, thresholdDb: Float = -2.5f) {
        this.isLimiterEnabled = enabled
        this.limiterThresholdDb = thresholdDb

        // 同步给 Native C++ DSP
        nativeDSP.setLimiter(enabled, thresholdDb)

        // 核心：立即同步给所有活跃的系统 AudioSession
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activeSessions.values.forEach { session ->
                session.dynamicsProcessing?.let { dp ->
                    syncLimiterToDynamicsProcessing(dp)
                }
            }
        }
        Log.i(TAG, "Limiter updated: enabled=$enabled, threshold=$thresholdDb dB")
    }

    @Synchronized
    fun releaseAll() {
        val keys = activeSessions.keys.toList()
        keys.forEach { detachSession(it) }
        nativeDSP.release()
    }

    companion object {
        private const val TAG = "AudioEffectManager"

        @Volatile
        private var INSTANCE: AudioEffectManager? = null

        fun getInstance(context: Context): AudioEffectManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioEffectManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
