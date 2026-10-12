package com.orbit.music.native

import android.util.Log

/**
 * 原生 C++ 音频播放引擎 JNI 桥接层
 * 封装底层的 AMediaCodec 解码、无锁环形缓冲、浮点 DSP 处理链与 AAudio 低延迟输出
 */
class NativeAudioPlayerEngine {

    private var nativeHandle: Long = 0

    init {
        try {
            System.loadLibrary("native_dsp")
            nativeHandle = nativeCreatePlayer()
            Log.i(TAG, "NativeAudioPlayerEngine 创建成功, 句柄: $nativeHandle")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "加载 native_dsp 动态库失败", e)
        }
    }

    fun isReady(): Boolean = nativeHandle != 0L

    fun openPath(path: String): Boolean {
        if (nativeHandle == 0L) return false
        return nativeOpenPath(nativeHandle, path)
    }

    fun openFd(fd: Int, offset: Long, length: Long): Boolean {
        if (nativeHandle == 0L) return false
        return nativeOpenFd(nativeHandle, fd, offset, length)
    }

    fun play(): Boolean {
        if (nativeHandle == 0L) return false
        return nativePlay(nativeHandle)
    }

    fun pause(): Boolean {
        if (nativeHandle == 0L) return false
        return nativePause(nativeHandle)
    }

    fun seekTo(positionMs: Long): Boolean {
        if (nativeHandle == 0L) return false
        return nativeSeekTo(nativeHandle, positionMs)
    }

    fun stop(): Boolean {
        if (nativeHandle == 0L) return false
        return nativeStop(nativeHandle)
    }

    fun reset() {
        if (nativeHandle != 0L) {
            nativeReset(nativeHandle)
        }
    }

    fun setVolume(volume: Float) {
        if (nativeHandle != 0L) {
            nativeSetVolume(nativeHandle, volume)
        }
    }

    fun getDuration(): Long {
        if (nativeHandle == 0L) return 0L
        return nativeGetDuration(nativeHandle)
    }

    fun getPosition(): Long {
        if (nativeHandle == 0L) return 0L
        return nativeGetPosition(nativeHandle)
    }

    fun getState(): Int {
        if (nativeHandle == 0L) return 0
        return nativeGetState(nativeHandle)
    }

    fun getDSPChainHandle(): Long {
        if (nativeHandle == 0L) return 0L
        return nativeGetDSPChainHandle(nativeHandle)
    }

    fun release() {
        if (nativeHandle != 0L) {
            nativeDestroyPlayer(nativeHandle)
            nativeHandle = 0L
            Log.i(TAG, "NativeAudioPlayerEngine 已销毁释放")
        }
    }

    // JNI 原生声明
    private external fun nativeCreatePlayer(): Long
    private external fun nativeDestroyPlayer(handle: Long)
    private external fun nativeOpenPath(handle: Long, path: String): Boolean
    private external fun nativeOpenFd(handle: Long, fd: Int, offset: Long, length: Long): Boolean
    private external fun nativePlay(handle: Long): Boolean
    private external fun nativePause(handle: Long): Boolean
    private external fun nativeSeekTo(handle: Long, positionMs: Long): Boolean
    private external fun nativeStop(handle: Long): Boolean
    private external fun nativeReset(handle: Long)
    private external fun nativeSetVolume(handle: Long, volume: Float)
    private external fun nativeGetDuration(handle: Long): Long
    private external fun nativeGetPosition(handle: Long): Long
    private external fun nativeGetState(handle: Long): Int
    private external fun nativeGetDSPChainHandle(handle: Long): Long

    companion object {
        private const val TAG = "NativeAudioPlayerEngine"

        const val STATE_IDLE = 0
        const val STATE_INITIALIZED = 1
        const val STATE_PREPARED = 2
        const val STATE_PLAYING = 3
        const val STATE_PAUSED = 4
        const val STATE_STOPPED = 5
        const val STATE_COMPLETED = 6
        const val STATE_ERROR = 7
    }
}
