package com.orbit.music.audio

import android.content.Context
import android.media.audiofx.BassBoost as AndroidBassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import com.orbit.music.native.NativeDSP
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
    private var currentBassBoostStrength = 0f
    private var isTrebleBoostEnabled = false
    private var currentTrebleBoostStrength = 0f

    // 内部播放器 Session ID (由 NativeDspAudioProcessor 接管高精度 PCM 浮点运算)
    private var internalSessionId: Int = -1

    @Synchronized
    fun registerInternalSession(sessionId: Int) {
        internalSessionId = sessionId
        // 防御性处理：若该 session 先前已按外部播放器挂载过系统音效，
        // 立即摘除，确保内部播放只经过自研 C++ 浮点 DSP，绝不双重处理
        activeSessions[sessionId]?.let { session ->
            val hasSystemProcessing = session.dynamicsProcessing != null ||
                    session.systemEqualizer != null ||
                    session.systemBassBoost?.enabled == true
            if (hasSystemProcessing) {
                Log.i(TAG, "session $sessionId 注册为内部播放通道，摘除其系统级音效 (避免与 C++ DSP 双重处理)")
                purgeSystemEffects(session)
            }
        }
    }

    /**
     * 释放并清空某个 session 上的全部系统级音效。
     * 内部播放通道必须保持纯净直通：音调 (Bass/Treble)、均衡、前级均由 C++ 浮点 DSP 处理，
     * 若同时在系统层再挂一次，会造成增益被叠加两次 (低音/高音听感翻倍) 与严重的相位/抽吸问题。
     */
    private fun purgeSystemEffects(session: SessionEffects) {
        try {
            session.dynamicsProcessing?.enabled = false
            session.dynamicsProcessing?.release()
        } catch (e: Exception) {
            Log.w(TAG, "释放 DynamicsProcessing 失败 (session ${session.sessionId})", e)
        }
        session.dynamicsProcessing = null

        try {
            session.systemEqualizer?.enabled = false
            session.systemEqualizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "释放系统 Equalizer 失败 (session ${session.sessionId})", e)
        }
        session.systemEqualizer = null

        try {
            session.systemBassBoost?.enabled = false
            session.systemBassBoost?.release()
        } catch (e: Exception) {
            Log.w(TAG, "释放系统 BassBoost 失败 (session ${session.sessionId})", e)
        }
        session.systemBassBoost = null
    }

    // 限幅器参数：设置 -0.3dB 工业安全真峰值门限，普通与大动态低音 100% 无损直通，超限时无痕保护
    private var isLimiterEnabled = false
    private var limiterThresholdDb = -0.3f
    private var limiterAttackMs = 0.5f
    private var limiterReleaseMs = 20.0f
    private var limiterRatio = 10.0f

    // 动态压缩器优化参数：
    // 门限 -10dB 保留自然动态范围；压缩比 2.0:1 柔和通透；
    // Attack 20ms 保留打击乐瞬态；Release 35ms 快速平滑消除抽吸感；
    // Makeup Gain 2.0dB 温和补偿，防止与原声形成断崖级落差。
    private var isCompressorEnabled = false
    private var compressorThresholdDb = -10.0f
    private var compressorRatio = 2.0f
    private var compressorAttackMs = 20.0f
    private var compressorReleaseMs = 35.0f
    private var compressorMakeupGainDb = 2.0f

    data class SessionEffects(
        val sessionId: Int,
        var systemEqualizer: Equalizer? = null,
        var systemBassBoost: AndroidBassBoost? = null,
        var dynamicsProcessing: DynamicsProcessing? = null
    )

    init {
        // 核心升级：原生 C++ DSP 默认跟随总开关，并默认激活 -0.3dBFS 软限幅保护 (保留充足动态，不提前压缩全频)
        nativeDSP.setEnabled(this.isEnabled)
        nativeDSP.setGraphicMode(10)
        nativeDSP.setLimiter(true, -0.3f)
    }

    fun isSessionAttached(sessionId: Int): Boolean {
        return activeSessions.containsKey(sessionId)
    }

    /**
     * 为指定 AudioSessionId 挂载音效
     * 架构升级：ExoPlayer 已通过 NativeDspAudioProcessor 在 PCM 核心层直通自研 C++ 浮点 DSP。
     * 因此系统层默认保持纯净直通，彻底废弃导致声音发闷发硬的系统级 DynamicsProcessing STFT 频域分频！
     */
    @Synchronized
    fun attachSession(sessionId: Int) {
        if (activeSessions.containsKey(sessionId)) {
            return
        }

        Log.i(TAG, "Attaching AudioEffects to session: $sessionId, isEnabled=$isEnabled")
        val effects = SessionEffects(sessionId)
        val isInternal = (sessionId == internalSessionId)

        // 1. 在 Android 9+ 优先初始化高通透、低相位失真的 DynamicsProcessing (Pre-EQ 10 bands + 软拐点防削波 Limiter)
        // 内部播放通道由自研 C++ 浮点 DSP 全权接管 (均衡 + 音调 + 前级 + 压缩 + 限幅)，
        // 系统层必须保持纯净直通，否则低音/高音/均衡会被叠加两次，音色与响度完全失控
        var isDynamicsProcessingAttached = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !isInternal) {
            try {
                // 广泛兼容模式：Android 系统级标准 VARIANT_FAVOR_FREQUENCY_RESOLUTION (绝大多数定制 ROM 仅支持此模式)
                val dpConfig = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,         // 立体声
                    true, 10,  // Pre-EQ 10 bands
                    true, 1,   // 启用 1 个波段作为动态管理
                    false, 0,  // 关闭冗余 Post-EQ
                    true       // 包含 Limiter 模块定义
                ).build()

                val dp = DynamicsProcessing(0, sessionId, dpConfig)
                dp.enabled = this.isEnabled
                syncAllGainsToDynamicsProcessing(dp)
                syncCompressorToDynamicsProcessing(dp)
                syncLimiterToDynamicsProcessing(dp)
                updateDynamicsProcessingInputGain(dp)
                effects.dynamicsProcessing = dp
                isDynamicsProcessingAttached = true
                Log.i(TAG, "成功挂载 DynamicsProcessing 到 session $sessionId (10-Band Pre-EQ + 智能余量直通)")
            } catch (e: Exception) {
                Log.w(TAG, "创建 DynamicsProcessing 失败，降级回退: ${e.message}")
            }
        }

        // 2. 回退机制：若无 DynamicsProcessing 且非内部通道，启用标准硬件级 Equalizer 滤波器
        if (!isDynamicsProcessingAttached && !isInternal) {
            try {
                val eq = Equalizer(0, sessionId)
                eq.enabled = this.isEnabled
                syncAllGainsToSystemEqualizer(eq)
                effects.systemEqualizer = eq
                Log.i(TAG, "成功挂载系统原生 Equalizer 到 session $sessionId, 频段数: ${eq.numberOfBands}")
            } catch (e: Exception) {
                Log.w(TAG, "创建 Equalizer 失败: ${e.message}")
            }
        }

        // 3. 系统级 BassBoost：外部播放器正常挂载并启用；内部播放器由自研 C++ 浮点 DSP 接管，保持旁路避免双重处理
        try {
            val bb = AndroidBassBoost(0, sessionId)
            bb.enabled = !isInternal && this.isBassBoostEnabled
            if (bb.strengthSupported && !isInternal) {
                val targetStrength = (this.currentBassBoostStrength * 1000).toInt().coerceIn(0, 1000).toShort()
                bb.setStrength(targetStrength)
            }
            effects.systemBassBoost = bb
            Log.i(TAG, "成功挂载 BassBoost 到 session $sessionId (isInternal=$isInternal)")
        } catch (e: Exception) {
            Log.w(TAG, "创建 BassBoost 失败: ${e.message}")
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
    }

    /**
     * 确保全局 Session 0 (Global Mix) 音效健康附着并处于激活状态
     * 当外部播放器切歌、播放/暂停切换或系统路由变更时调用
     */
    @Synchronized
    fun ensureSession0Attached() {
        val session0 = activeSessions[0]
        if (session0 == null) {
            Log.i(TAG, "Session 0 missing, re-attaching Session 0...")
            attachSession(0)
            return
        }

        try {
            if (this.isEnabled) {
                session0.dynamicsProcessing?.let { dp ->
                    if (!dp.enabled) {
                        dp.enabled = true
                        syncAllGainsToDynamicsProcessing(dp)
                        syncCompressorToDynamicsProcessing(dp)
                        syncLimiterToDynamicsProcessing(dp)
                    }
                }
                session0.systemEqualizer?.let { eq ->
                    if (!eq.enabled) {
                        eq.enabled = true
                        syncAllGainsToSystemEqualizer(eq)
                    }
                }
                session0.systemBassBoost?.let { bb ->
                    if (this.isBassBoostEnabled && !bb.enabled) {
                        bb.enabled = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying Session 0 health", e)
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
                if (session.sessionId == internalSessionId) {
                    // 内部播放通道始终旁路系统音效：音调独立于总开关，且完全由 C++ DSP 处理，
                    // 绝不能在此处把系统 BassBoost/Equalizer 重新打开 (会造成低音双重提升)
                    session.systemEqualizer?.enabled = false
                    session.systemBassBoost?.enabled = false
                    session.dynamicsProcessing?.enabled = false
                } else {
                    session.systemEqualizer?.enabled = enabled
                    session.systemBassBoost?.enabled = isBassBoostEnabled
                    session.dynamicsProcessing?.let { dp ->
                        dp.enabled = enabled
                        syncAllGainsToDynamicsProcessing(dp)
                        syncCompressorToDynamicsProcessing(dp)
                        syncLimiterToDynamicsProcessing(dp)
                        updateDynamicsProcessingInputGain(dp)
                    }
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
            session.dynamicsProcessing?.let { dp ->
                syncAllGainsToDynamicsProcessing(dp)
                updateDynamicsProcessingInputGain(dp)
            }
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

        // 动态分流：内部 ExoPlayer 由自研 C++ 浮点 DSP 高精度处理；外部播放器由系统 BassBoost 提供增强
        activeSessions.values.forEach { session ->
            val isInternal = (session.sessionId == internalSessionId)
            session.systemBassBoost?.let { bb ->
                try {
                    if (isInternal) {
                        bb.enabled = false
                    } else {
                        bb.enabled = enabled
                        if (bb.strengthSupported) {
                            val targetStrength = (strength * 1000).toInt().coerceIn(0, 1000).toShort()
                            bb.setStrength(targetStrength)
                        }
                    }
                    Unit
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating system BassBoost for session ${session.sessionId}", e)
                }
            }
            session.systemEqualizer?.let { eq -> syncAllGainsToSystemEqualizer(eq) }
            session.dynamicsProcessing?.let { dp ->
                syncAllGainsToDynamicsProcessing(dp)
                updateDynamicsProcessingInputGain(dp)
            }
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
                        // 音调提升：直接套用与自带播放器 (C++ DSP) 完全同源的 Shelf 曲线，
                        // 避免外部播放器只有 +3dB 而自带播放器 +15dB 的音调落差
                        var extraBoost = 0f
                        if (isBassBoostEnabled) {
                            extraBoost += ToneResponse.bassBoostDb(
                                f.toDouble(), currentBassBoostStrength, SYSTEM_EFFECT_SAMPLE_RATE
                            ).toFloat()
                        }
                        if (isTrebleBoostEnabled) {
                            extraBoost += ToneResponse.trebleBoostDb(
                                f.toDouble(), currentTrebleBoostStrength, SYSTEM_EFFECT_SAMPLE_RATE
                            ).toFloat()
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

                    // 音调提升：与自带播放器 C++ DSP 使用同一条 Shelf 曲线
                    if (isBassBoostEnabled) {
                        gain += ToneResponse.bassBoostDb(
                            freq.toDouble(), currentBassBoostStrength, SYSTEM_EFFECT_SAMPLE_RATE
                        ).toFloat()
                    }
                    if (isTrebleBoostEnabled) {
                        gain += ToneResponse.trebleBoostDb(
                            freq.toDouble(), currentTrebleBoostStrength, SYSTEM_EFFECT_SAMPLE_RATE
                        ).toFloat()
                    }

                    // 修复核心：频段始终保持就绪 (true)，由外层 dp.enabled 统一控制激活与直通旁路
                    // 彻底解决 Android 底层驱动因频段 disabled 拒绝响应动态增益滑动的严重缺陷！
                    val bandConfig = DynamicsProcessing.EqBand(true, freq, gain)
                    dp.setPreEqBandAllChannelsTo(i, bandConfig)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in syncAllGainsToDynamicsProcessing", e)
            }
        }
    }

    /**
     * 将动态压缩器参数同步至 DynamicsProcessing 的 MBC (Multi-Band Compressor)
     * 关键修复：除了配置具体的 MbcBand 外，必须显式构建 Mbc Stage 并设置其 enabled 属性！
     * 否则底层 AudioDynamicsProcessing 的 channel->mbc->isEnabled() 始终处于未激活状态，导致压缩器被完全旁路！
     */
    private fun syncCompressorToDynamicsProcessing(dp: DynamicsProcessing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val shouldEnable = this.isEnabled && this.isCompressorEnabled
                val mbcBand = DynamicsProcessing.MbcBand(
                    shouldEnable,                                 // 仅在开关开启时启用
                    22000.0f,                                     // 22kHz 截止频率，确保 20kHz 人耳全高频完全畅通无阻无滚降
                    compressorAttackMs,                           // 20ms 瞬态通透起控
                    compressorReleaseMs,                          // 60ms 快速平滑释放
                    if (shouldEnable) compressorRatio else 1.0f,  // 关闭时 1:1 无压缩
                    if (shouldEnable) compressorThresholdDb else 0.0f, // 关闭时 0dB 门限
                    8.0f,                                         // 8dB 宽软拐点 (Soft Knee) 平滑渐进
                    -90.0f,                                       // 噪声门
                    1.0f,                                         // 扩展比 1:1
                    0.0f,                                         // preGain 0dB
                    if (shouldEnable) compressorMakeupGainDb else 0.0f // 开启时补偿 4.0dB，关闭严格 0dB
                )

                // 核心：显式构建包含 enabled 状态的 Mbc Stage 对象，彻底唤醒底层 DSP 压缩算法管线！
                val mbc = DynamicsProcessing.Mbc(
                    true,         // inUse: 必须与 dpConfig 保持一致为 true，避免系统抛出 IllegalArgumentException
                    shouldEnable, // enabled: 运行时总开关，关闭时底层完全旁路
                    1             // 1 band
                )
                mbc.setBand(0, mbcBand)

                dp.setMbcAllChannelsTo(mbc)
                dp.setMbcBandAllChannelsTo(0, mbcBand)
            } catch (e: Exception) {
                Log.e(TAG, "Error in syncCompressorToDynamicsProcessing", e)
            }
        }
    }

    /**
     * 将限幅器参数同步至 DynamicsProcessing 的 Limiter
     */
    private fun syncLimiterToDynamicsProcessing(dp: DynamicsProcessing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val userLimiterActive = this.isEnabled && this.isLimiterEnabled
                // 开启时：-0.3dBFS 安全防削波门限 + 20ms 快速释放；关闭时：+6.0dBFS 且 1:1 比率，完全透明旁路直通
                val targetThreshold = if (userLimiterActive) -0.3f else 6.0f
                val targetRatio = if (userLimiterActive) 10.0f else 1.0f
                val targetAttack = if (userLimiterActive) 0.5f else 10.0f
                val targetRelease = if (userLimiterActive) 20.0f else 100.0f

                val limiter = DynamicsProcessing.Limiter(
                    true,              // inUse: 必须与 dpConfig 保持一致为 true
                    userLimiterActive, // enabled: 运行时开关，真正由用户压限器开关控制！关闭时完全 bypass 旁路
                    0,                 // linkGroup
                    targetAttack,      // 快速瞬态起控
                    targetRelease,     // 平滑释放
                    targetRatio,       // 限幅比率
                    targetThreshold,   // 门限
                    0.0f               // postGain
                )
                dp.setLimiterAllChannelsTo(limiter)
            } catch (e: Exception) {
                Log.e(TAG, "Error in syncLimiterToDynamicsProcessing", e)
            }
        }
    }

    /**
     * 根据当前均衡器与音调正增益，动态注入平滑的输入动态余量 (Auto-Headroom)
     * 彻底解决 4096点 STFT 频域 bin 能量因低音正增益打满而触发系统频谱压缩与低音发闷
     */
    private fun updateDynamicsProcessingInputGain(dp: DynamicsProcessing) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                var maxPosGain = 0f
                for (i in currentBandGains.indices) {
                    var g = currentBandGains[i]
                    if (isBassBoostEnabled) {
                        g += ToneResponse.bassBoostDb(
                            defaultFrequencies[i].toDouble(),
                            currentBassBoostStrength,
                            SYSTEM_EFFECT_SAMPLE_RATE
                        ).toFloat()
                    }
                    if (g > maxPosGain) maxPosGain = g
                }
                val autoHeadroom = (maxPosGain * 0.5f).coerceIn(0f, 3.5f)
                dp.setInputGainAllChannelsTo(currentPreampDb - autoHeadroom)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update DynamicsProcessing input gain", e)
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
                    updateDynamicsProcessingInputGain(dp)
                    syncLimiterToDynamicsProcessing(dp)
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
        thresholdDb: Float = -15f,
        ratio: Float = 3f,
        attackMs: Float = 20f,
        releaseMs: Float = 60f,
        makeupGainDb: Float = 4f
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
    fun setLimiterEnabled(enabled: Boolean, thresholdDb: Float = -0.3f) {
        this.isLimiterEnabled = enabled
        this.limiterThresholdDb = thresholdDb

        // 同步给 Native C++ DSP 砖墙真峰值限幅器
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

        // 系统音效 (DynamicsProcessing / Equalizer) 工作采样率，用于计算音调 Shelf 曲线
        private const val SYSTEM_EFFECT_SAMPLE_RATE = 48000.0

        @Volatile
        private var INSTANCE: AudioEffectManager? = null

        fun getInstance(context: Context): AudioEffectManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioEffectManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
