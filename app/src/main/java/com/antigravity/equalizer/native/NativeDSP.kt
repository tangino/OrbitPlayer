package com.antigravity.equalizer.native

import android.util.Log

/**
 * Kotlin 封装的 C++ DSP 核心引擎桥接单例/实例
 */
class NativeDSP private constructor() {

    private var nativeHandle: Long = 0

    init {
        try {
            System.loadLibrary("native_dsp")
            nativeHandle = nativeCreateEngine()
            Log.i(TAG, "NativeDSP initialized successfully with handle: $nativeHandle")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library 'native_dsp'", e)
        }
    }

    fun isReady(): Boolean = nativeHandle != 0L

    fun setSampleRate(sampleRate: Float) {
        if (nativeHandle != 0L) nativeSetSampleRate(nativeHandle, sampleRate)
    }

    fun setEnabled(enabled: Boolean) {
        if (nativeHandle != 0L) nativeSetEnabled(nativeHandle, enabled)
    }

    fun setEngineMode(mode: Int) {
        if (nativeHandle != 0L) nativeSetEngineMode(nativeHandle, mode)
    }

    fun setGraphicMode(numBands: Int) {
        if (nativeHandle != 0L) nativeSetGraphicMode(nativeHandle, numBands)
    }

    fun setGraphicBandGain(bandIndex: Int, gainDb: Float) {
        if (nativeHandle != 0L) nativeSetGraphicBandGain(nativeHandle, bandIndex, gainDb)
    }

    fun getGraphicFrequencies(): FloatArray {
        return if (nativeHandle != 0L) {
            nativeGetGraphicFrequencies(nativeHandle) ?: floatArrayOf()
        } else {
            floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        }
    }

    fun setPreampGain(gainDb: Float) {
        if (nativeHandle != 0L) nativeSetPreampGain(nativeHandle, gainDb)
    }

    fun setBassBoost(enabled: Boolean, strength: Float = 0.5f) {
        if (nativeHandle != 0L) nativeSetBassBoost(nativeHandle, enabled, strength)
    }

    fun setTrebleBoost(enabled: Boolean, strength: Float = 0.5f) {
        if (nativeHandle != 0L) nativeSetTrebleBoost(nativeHandle, enabled, strength)
    }

    fun setCompressor(
        enabled: Boolean,
        thresholdDb: Float = -18f,
        ratio: Float = 4f,
        attackMs: Float = 10f,
        releaseMs: Float = 100f,
        makeupGainDb: Float = 0f
    ) {
        if (nativeHandle != 0L) {
            nativeSetCompressor(nativeHandle, enabled, thresholdDb, ratio, attackMs, releaseMs, makeupGainDb)
        }
    }

    fun isClipping(): Boolean {
        return if (nativeHandle != 0L) nativeIsClipping(nativeHandle) else false
    }

    fun setLimiter(enabled: Boolean, thresholdDb: Float = -0.2f) {
        if (nativeHandle != 0L) nativeSetLimiter(nativeHandle, enabled, thresholdDb)
    }

    fun processBuffer(buffer: FloatArray, numFrames: Int) {
        if (nativeHandle != 0L) nativeProcessBuffer(nativeHandle, buffer, numFrames)
    }

    fun getCurve(freqPoints: FloatArray, outMagnitudes: FloatArray) {
        if (nativeHandle != 0L) nativeGetCurve(nativeHandle, freqPoints, outMagnitudes)
    }

    fun getSpectrum(outBands: FloatArray) {
        if (nativeHandle != 0L) nativeGetSpectrum(nativeHandle, outBands)
    }

    fun getPeakLevels(): Pair<Float, Float> {
        if (nativeHandle != 0L) {
            val levels = nativeGetPeakLevels(nativeHandle)
            if (levels != null && levels.size >= 2) {
                return Pair(levels[0], levels[1])
            }
        }
        return Pair(-60f, -60f)
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeDestroyEngine(nativeHandle)
            nativeHandle = 0
        }
    }

    protected fun finalize() {
        release()
    }

    // --- Native 函数声明 ---
    private external fun nativeCreateEngine(): Long
    private external fun nativeDestroyEngine(handle: Long)
    private external fun nativeSetSampleRate(handle: Long, sampleRate: Float)
    private external fun nativeSetEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetEngineMode(handle: Long, mode: Int)
    private external fun nativeSetGraphicMode(handle: Long, numBands: Int)
    private external fun nativeSetGraphicBandGain(handle: Long, bandIndex: Int, gainDb: Float)
    private external fun nativeGetGraphicFrequencies(handle: Long): FloatArray?
    private external fun nativeSetParametricBand(handle: Long, bandIndex: Int, enabled: Boolean, type: Int, freq: Float, gainDb: Float, q: Float)
    private external fun nativeSetBassBoost(handle: Long, enabled: Boolean, strength: Float)
    private external fun nativeSetTrebleBoost(handle: Long, enabled: Boolean, strength: Float)
    private external fun nativeSetCompressor(
        handle: Long,
        enabled: Boolean,
        thresholdDb: Float,
        ratio: Float,
        attackMs: Float,
        releaseMs: Float,
        makeupGainDb: Float
    )
    private external fun nativeSetPreampGain(handle: Long, gainDb: Float)
    private external fun nativeIsClipping(handle: Long): Boolean
    private external fun nativeSetLimiter(handle: Long, enabled: Boolean, thresholdDb: Float)
    private external fun nativeProcessBuffer(handle: Long, buffer: FloatArray, numFrames: Int)
    private external fun nativeGetCurve(handle: Long, freqPoints: FloatArray, outMagnitudes: FloatArray)
    private external fun nativeGetSpectrum(handle: Long, outBands: FloatArray)
    private external fun nativeGetPeakLevels(handle: Long): FloatArray?

    companion object {
        private const val TAG = "NativeDSP"
        val instance: NativeDSP by lazy { NativeDSP() }
    }
}
