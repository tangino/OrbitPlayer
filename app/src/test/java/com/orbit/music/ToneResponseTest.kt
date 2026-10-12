package com.orbit.music

import com.orbit.music.audio.ToneResponse
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 音调曲线 (Kotlin 侧) 与原生 C++ BassBoost/TrebleBoost 数值对齐校验。
 * 期望值来自 C++ 实测频响 (BiquadFilter::getMagnitudeResponse)：
 * 低音 100% (90Hz Low Shelf, Q=0.8, +15dB)、高音 100% (10kHz High Shelf, Q=0.8, +15dB)
 */
class ToneResponseTest {

    private val tolerance = 0.06

    @Test
    fun bassShelfMatchesNativeDsp() {
        val strength = 1.0f
        val sr = 44100.0
        // 频点 -> C++ 实测 dB
        val expected = mapOf(
            20.0 to 15.12,
            30.0 to 15.13,
            60.0 to 12.62,
            90.0 to 7.50,
            125.0 to 3.17,
            200.0 to 0.22
        )
        for ((freq, expectDb) in expected) {
            val actual = ToneResponse.bassShelfDb(freq, strength, sr)
            assertEquals("低音曲线 $freq Hz 与 C++ 不一致", expectDb, actual, tolerance)
        }
    }

    @Test
    fun trebleShelfMatchesNativeDsp() {
        val strength = 1.0f
        val sr = 44100.0
        val expected = mapOf(
            8000.0 to 3.50,
            10000.0 to 7.50,
            14000.0 to 13.95,
            16000.0 to 15.00
        )
        for ((freq, expectDb) in expected) {
            val actual = ToneResponse.trebleBoostDb(freq, strength, sr)
            assertEquals("高音曲线 $freq Hz 与 C++ 不一致", expectDb, actual, tolerance)
        }
    }

    @Test
    fun halfStrengthIsHalfGain() {
        val full = ToneResponse.bassShelfDb(30.0, 1.0f, 44100.0)
        val half = ToneResponse.bassShelfDb(30.0, 0.5f, 44100.0)
        assertEquals(full / 2.0, half, 0.05)
    }

    @Test
    fun strengthZeroMeansNoBoost() {
        assertEquals(0.0, ToneResponse.bassBoostDb(30.0, 0f, 44100.0), 1e-9)
        assertEquals(0.0, ToneResponse.trebleBoostDb(16000.0, 0f, 44100.0), 1e-9)
    }

    @Test
    fun bassBoostIsBandLimitedAboveCrossover() {
        val strength = 1.0f
        // 分频带内几乎全额提升，分频点以上迅速衰减，1kHz 以上几乎无提升
        val low = ToneResponse.bassBoostDb(30.0, strength, 48000.0)
        val mid = ToneResponse.bassBoostDb(1000.0, strength, 48000.0)
        assertEquals(15.1, low, 0.15)
        assertEquals(0.0, mid, 0.1)
    }

    @Test
    fun lowBandWeightMatchesLinkwitzRiley4() {
        val fc = ToneResponse.BASS_BAND_CROSSOVER_HZ
        assertEquals(0.5, ToneResponse.lowBandWeight(fc, fc), 1e-9)
        assertEquals(1.0, ToneResponse.lowBandWeight(20.0, fc), 0.005)
        assertEquals(0.0, ToneResponse.lowBandWeight(4000.0, fc), 0.005)
    }
}
