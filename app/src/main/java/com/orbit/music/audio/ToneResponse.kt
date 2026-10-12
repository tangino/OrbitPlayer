package com.orbit.music.audio

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 音调 (Bass / Treble) 目标增益曲线 —— 与原生 C++ 侧完全同源
 *
 * 参数严格对齐 C++ 的 BassBoost / TrebleBoost / BassLimiter：
 *  - Bass   : 90Hz Low Shelf (RBJ)，Q = 0.8，最大 +15.0dB，提升只作用在分频带 180Hz 以下
 *  - Treble : 10kHz High Shelf (RBJ)，Q = 0.8，最大 +15.0dB
 *
 * 用途：外部播放器 (网易云 / QQ 音乐等) 走系统音效通道时，把与自带播放器一致的
 * Shelf 形状映射到 DynamicsProcessing Pre-EQ / 系统 Equalizer 频段上，
 * 修掉"自带播放器 +15dB、外部播放器只有 +3dB"的音调落差。
 */
object ToneResponse {

    const val MAX_BOOST_DB = 15.0
    const val BASS_CENTER_HZ = 90.0
    const val TREBLE_CENTER_HZ = 10000.0
    const val SHELF_Q = 0.8
    /** 分频带低频限幅的分频点，与 C++ BassLimiter 保持一致 */
    const val BASS_BAND_CROSSOVER_HZ = 180.0

    private const val MAX_GAIN_DB = 40.0

    /**
     * 低音旋钮在指定频点的实际提升量 (dB)，已计入 180Hz 分频带限制
     */
    fun bassBoostDb(
        frequencyHz: Double,
        strength: Float,
        sampleRateHz: Double = 48000.0
    ): Double {
        if (strength <= 0.0f) return 0.0
        val shelfDb = bassShelfDb(frequencyHz, strength, sampleRateHz)
        val bandWeight = lowBandWeight(frequencyHz, BASS_BAND_CROSSOVER_HZ)
        val linear = 1.0 + (10.0.pow(shelfDb / 20.0) - 1.0) * bandWeight
        return 20.0 * log10(linear.coerceAtLeast(1e-6))
    }

    /**
     * 低音 Shelf 本身（不含分频带限制）的提升量 (dB)，用于和 C++ 数值逐点核对
     */
    fun bassShelfDb(
        frequencyHz: Double,
        strength: Float,
        sampleRateHz: Double = 48000.0
    ): Double {
        if (strength <= 0.0f) return 0.0
        return shelfResponseDb(
            lowShelf = true,
            centerHz = BASS_CENTER_HZ,
            gainDb = strength * MAX_BOOST_DB,
            sampleRateHz = sampleRateHz,
            evaluateHz = frequencyHz
        )
    }

    /**
     * 高音旋钮在指定频点的实际提升量 (dB)。高音 Shelf 作用于全频段，无分频限制
     */
    fun trebleBoostDb(
        frequencyHz: Double,
        strength: Float,
        sampleRateHz: Double = 48000.0
    ): Double {
        if (strength <= 0.0f) return 0.0
        return shelfResponseDb(
            lowShelf = false,
            centerHz = TREBLE_CENTER_HZ,
            gainDb = strength * MAX_BOOST_DB,
            sampleRateHz = sampleRateHz,
            evaluateHz = frequencyHz
        )
    }

    /**
     * Linkwitz-Riley 4 阶低通支路的幅度权重：|H| = |Butterworth2|² = 1 / (1 + (f/fc)^4)
     * 分频点处为 0.5，远低于分频点趋近 1，远高于分频点趋近 0
     */
    fun lowBandWeight(frequencyHz: Double, crossoverHz: Double): Double {
        if (frequencyHz <= 0.0 || crossoverHz <= 0.0) return 0.0
        val r = frequencyHz / crossoverHz
        return 1.0 / (1.0 + r.pow(4))
    }

    /**
     * RBJ Shelf 滤波器在指定频点的幅频响应 (dB) —— 与 C++ BiquadFilter 完全一致
     */
    private fun shelfResponseDb(
        lowShelf: Boolean,
        centerHz: Double,
        gainDb: Double,
        sampleRateHz: Double,
        evaluateHz: Double
    ): Double {
        val sampleRate = if (sampleRateHz > 0.0) sampleRateHz else 44100.0
        val fc = centerHz.coerceIn(10.0, sampleRate * 0.49)

        val a = 10.0.pow(gainDb / MAX_GAIN_DB)
        val sqrtA = sqrt(a)
        val w0 = 2.0 * Math.PI * fc / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * SHELF_Q)

        val b0: Double
        val b1: Double
        val b2: Double
        val a0: Double
        val a1: Double
        val a2: Double

        if (lowShelf) {
            b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
            b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
            b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
            a0 = (a + 1.0) + (a - 1.0) * cosW0 + 2.0 * sqrtA * alpha
            a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
            a2 = (a + 1.0) + (a - 1.0) * cosW0 - 2.0 * sqrtA * alpha
        } else {
            b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + 2.0 * sqrtA * alpha)
            b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
            b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - 2.0 * sqrtA * alpha)
            a0 = (a + 1.0) - (a - 1.0) * cosW0 + 2.0 * sqrtA * alpha
            a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
            a2 = (a + 1.0) - (a - 1.0) * cosW0 - 2.0 * sqrtA * alpha
        }

        val invA0 = 1.0 / a0
        val nb0 = b0 * invA0
        val nb1 = b1 * invA0
        val nb2 = b2 * invA0
        val na1 = a1 * invA0
        val na2 = a2 * invA0

        val w = 2.0 * Math.PI * evaluateHz / sampleRate
        val cosW = cos(w)
        val cosW2 = cos(2.0 * w)
        val sinW = sin(w)
        val sinW2 = sin(2.0 * w)

        val numReal = nb0 + nb1 * cosW + nb2 * cosW2
        val numImag = -(nb1 * sinW + nb2 * sinW2)
        val denReal = 1.0 + na1 * cosW + na2 * cosW2
        val denImag = -(na1 * sinW + na2 * sinW2)

        val numMagSq = numReal * numReal + numImag * numImag
        val denMagSq = denReal * denReal + denImag * denImag
        if (denMagSq < 1e-12) return 0.0

        return 10.0 * log10((numMagSq / denMagSq).coerceAtLeast(1e-12))
    }
}
