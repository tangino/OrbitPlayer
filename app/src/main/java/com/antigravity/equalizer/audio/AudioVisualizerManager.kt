package com.antigravity.equalizer.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 实时频谱帧数据
 */
data class VisualizerFrame(
    val rawMagnitudes: FloatArray = FloatArray(BAR_COUNT),
    val peakCaps: FloatArray = FloatArray(BAR_COUNT),
    val isRealFft: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VisualizerFrame
        return rawMagnitudes.contentEquals(other.rawMagnitudes) &&
                peakCaps.contentEquals(other.peakCaps) &&
                isRealFft == other.isRealFft
    }

    override fun hashCode(): Int {
        var result = rawMagnitudes.contentHashCode()
        result = 31 * result + peakCaps.contentHashCode()
        result = 31 * result + isRealFft.hashCode()
        return result
    }

    companion object {
        const val BAR_COUNT = 32
    }
}

/**
 * 仿 Poweramp 殿堂级音频频谱采集与物理运动计算引擎
 * 1. 挂载原生 Visualizer 监听 1024 频点 FFT 数据；
 * 2. 对数频段映射 (Logarithmic Binning，贴合人耳听觉曲线)；
 * 3. 仿 Poweramp 经典悬浮顶峰缓降算法 (Peak Hold & Gravity Falloff)；
 * 4. 智能保底拟真律动合成器 (无权限或设备静默时无缝降级平滑驱动)。
 */
class AudioVisualizerManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var visualizer: Visualizer? = null
    private var currentSessionId: Int = -1

    private val smoothedBars = FloatArray(VisualizerFrame.BAR_COUNT)
    private val peakCaps = FloatArray(VisualizerFrame.BAR_COUNT)
    private val peakVelocities = FloatArray(VisualizerFrame.BAR_COUNT)
    private val peakHoldCounters = IntArray(VisualizerFrame.BAR_COUNT)

    private val _visualizerFlow = MutableStateFlow(VisualizerFrame())
    val visualizerFlow: StateFlow<VisualizerFrame> = _visualizerFlow.asStateFlow()

    @Volatile
    private var isPlaying: Boolean = false
    @Volatile
    private var lastFftReceivedTimestamp: Long = 0L

    private var fallbackJob: Job? = null
    private var simulationPhase: Float = 0f

    init {
        startFallbackLoop()
    }

    /**
     * 判断当前是否正在稳定接收真实的有效 FFT 音频数据
     */
    fun isReceivingRealFft(): Boolean {
        return isPlaying && (System.currentTimeMillis() - lastFftReceivedTimestamp < 600L)
    }

    /**
     * 绑定目标音频 Session (支持 force 强制重连)
     */
    @Synchronized
    fun attachSession(sessionId: Int, force: Boolean = false) {
        if (sessionId <= 0) return
        // 若当前已有健康且正在接收真实音频的连接，且 Session ID 一致，直接保持连接，避免无谓销毁与重连冲突
        if (!force && currentSessionId == sessionId && visualizer != null) return
        if (!force && currentSessionId == sessionId && isReceivingRealFft()) return

        detachSession()

        currentSessionId = sessionId

        // 检查录音权限
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "No RECORD_AUDIO permission; Visualizer using dynamic simulator fallback")
            return
        }

        try {
            val range = Visualizer.getCaptureSizeRange()
            val captureSize = if (range.size >= 2) range[1].coerceIn(512, 1024) else 1024

            val maxRate = try {
                Visualizer.getMaxCaptureRate()
            } catch (e: Exception) {
                20000
            }
            val captureRate = (maxRate * 0.95f).toInt().coerceAtLeast(10000)

            val viz = Visualizer(sessionId).apply {
                this.captureSize = captureSize
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}

                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            if (fft != null && fft.isNotEmpty()) {
                                processFft(fft)
                            }
                        }
                    },
                    captureRate,
                    false,
                    true
                )
                enabled = true
            }
            visualizer = viz
            Log.i(TAG, "AudioVisualizer attached successfully to session $sessionId with size $captureSize (force=$force)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Visualizer for session $sessionId: ${e.message}")
            // 若私有 Session 挂载失败，尝试安全降级至全局 Session 0
            if (sessionId != 0) {
                try {
                    val viz0 = Visualizer(0).apply {
                        this.captureSize = 1024
                        setDataCaptureListener(
                            object : Visualizer.OnDataCaptureListener {
                                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                                    if (fft != null && fft.isNotEmpty()) processFft(fft)
                                }
                            },
                            15000,
                            false,
                            true
                        )
                        enabled = true
                    }
                    visualizer = viz0
                    Log.i(TAG, "AudioVisualizer fallback successfully to global session 0")
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * 释放 Visualizer
     */
    @Synchronized
    fun detachSession() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing visualizer", e)
        } finally {
            visualizer = null
            currentSessionId = -1
        }
    }

    /**
     * 同步当前播放状态 (用于暂停时归零与恢复时动效)
     */
    fun setPlaying(playing: Boolean) {
        this.isPlaying = playing
        if (!playing) {
            // 暂停播放时阻尼下落归零
            for (i in 0 until VisualizerFrame.BAR_COUNT) {
                smoothedBars[i] = 0f
                peakCaps[i] = 0f
                peakVelocities[i] = 0f
                peakHoldCounters[i] = 0
            }
            _visualizerFlow.value = VisualizerFrame()
        } else {
            // 恢复播放时若长时间无真实数据，重置时间戳让保底发生器先行平滑接管，避免静默卡死
            if (System.currentTimeMillis() - lastFftReceivedTimestamp > 1000L) {
                lastFftReceivedTimestamp = 0L
            }
        }
    }

    /**
     * 解析原生 1024 点 FFT 数据并计算对数频段与顶峰落差
     * 智能识别有效音频能量，过滤底层静默包以防动画抽搐
     */
    private fun processFft(fft: ByteArray) {
        if (!isPlaying) return

        val n = fft.size / 2
        val count = VisualizerFrame.BAR_COUNT
        val binnedMags = FloatArray(count)

        // 对数区间映射：低频采样精细，高频宽带平滑
        val minFreq = 1.0
        val maxFreq = n.toDouble().coerceAtLeast(2.0)
        val logMin = ln(minFreq)
        val logMax = ln(maxFreq)

        var totalEnergy = 0f

        for (b in 0 until count) {
            val startIdx = (Math.exp(logMin + (logMax - logMin) * (b.toDouble() / count))).toInt().coerceIn(1, n - 1)
            val endIdx = (Math.exp(logMin + (logMax - logMin) * ((b + 1.toDouble()) / count))).toInt().coerceIn(startIdx + 1, n)

            var maxMag = 0f
            for (k in startIdx until endIdx) {
                val rIndex = 2 * k
                val iIndex = 2 * k + 1
                if (rIndex < fft.size && iIndex < fft.size) {
                    val re = fft[rIndex].toFloat()
                    val im = fft[iIndex].toFloat()
                    val mag = hypot(re, im)
                    if (mag > maxMag) maxMag = mag
                }
            }

            // 分贝曲线归一化与高频增益补偿 (人耳高频敏感度补偿)
            val highFreqBoost = 1.0f + (b.toFloat() / count) * 1.8f
            val normalized = ((maxMag / 128f) * highFreqBoost).coerceIn(0f, 1f)
            binnedMags[b] = normalized
            totalEnergy += normalized
        }

        // 仅当含有真实音频能量时才标记有效真实 FFT 并发射，过滤底层静默包以防动画抽搐与保底波打架
        if (totalEnergy > 0.010f) {
            lastFftReceivedTimestamp = System.currentTimeMillis()
            updatePhysicsAndEmit(binnedMags, isRealFft = true)
        }
    }

    /**
     * 物理 Attack / Decay 与 Poweramp 悬浮顶峰缓降算法
     */
    private fun updatePhysicsAndEmit(targetMags: FloatArray, isRealFft: Boolean) {
        val count = VisualizerFrame.BAR_COUNT
        val currentBars = FloatArray(count)
        val currentPeaks = FloatArray(count)

        val attackRate = 0.82f
        val decayRate = 0.68f

        for (i in 0 until count) {
            val target = targetMags[i].coerceIn(0f, 1f)

            // 1. 柱体运动：急升缓降
            if (target > smoothedBars[i]) {
                smoothedBars[i] += (target - smoothedBars[i]) * attackRate
            } else {
                smoothedBars[i] = smoothedBars[i] * decayRate
            }
            if (smoothedBars[i] < 0.02f) smoothedBars[i] = 0f
            currentBars[i] = smoothedBars[i]

            // 2. Poweramp 顶峰缓降机制 (Peak Hold & Gravity)
            if (smoothedBars[i] >= peakCaps[i]) {
                peakCaps[i] = smoothedBars[i]
                peakVelocities[i] = 0f
                peakHoldCounters[i] = 5 // 停留约 80~100ms
            } else {
                if (peakHoldCounters[i] > 0) {
                    peakHoldCounters[i]--
                } else {
                    peakVelocities[i] += 0.0035f // 物理重力加速度
                    peakCaps[i] = (peakCaps[i] - peakVelocities[i]).coerceAtLeast(smoothedBars[i])
                }
            }
            currentPeaks[i] = peakCaps[i].coerceIn(0f, 1f)
        }

        _visualizerFlow.value = VisualizerFrame(
            rawMagnitudes = currentBars,
            peakCaps = currentPeaks,
            isRealFft = isRealFft
        )
    }

    /**
     * 保底拟真律动波发生器 (在无原生 FFT 数据时平滑激活)
     */
    private fun startFallbackLoop() {
        fallbackJob?.cancel()
        fallbackJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                // 如果超过 250ms 没有收到真实 FFT 且当前正在播放，激活高质感模拟合成波
                if (isPlaying && (now - lastFftReceivedTimestamp > 250L)) {
                    simulationPhase += 0.16f
                    val simMags = FloatArray(VisualizerFrame.BAR_COUNT)
                    for (i in 0 until VisualizerFrame.BAR_COUNT) {
                        val freqRatio = i.toFloat() / VisualizerFrame.BAR_COUNT
                        // 低频轰鸣基波 + 中频交织 + 高频跳跃
                        val bass = sin(simulationPhase * 2.5f + freqRatio * 3.0f).coerceAtLeast(0f) * (1f - freqRatio * 0.7f)
                        val mid = cos(simulationPhase * 3.8f - freqRatio * 5.0f).coerceAtLeast(0f) * 0.6f
                        val treble = sin(simulationPhase * 5.2f + freqRatio * 8.0f).coerceAtLeast(0f) * (freqRatio * 0.5f)
                        val total = ((bass * 0.75f + mid * 0.5f + treble * 0.4f) * 0.95f).coerceIn(0.05f, 0.95f)
                        simMags[i] = total
                    }
                    updatePhysicsAndEmit(simMags, isRealFft = false)
                }
                delay(16L) // 约 60 FPS 刷新率
            }
        }
    }

    companion object {
        private const val TAG = "AudioVisualizerManager"

        @Volatile
        private var instance: AudioVisualizerManager? = null

        fun getInstance(context: Context): AudioVisualizerManager {
            return instance ?: synchronized(this) {
                instance ?: AudioVisualizerManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
