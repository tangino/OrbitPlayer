package com.orbit.music.audio.pipeline

import android.util.Log
import java.nio.ByteBuffer

/**
 * 原生 Oboe 输出流的 JNI 包装类（对应 cpp/player/oboe_sink.cpp）。
 * 由 [OboeAudioSink] 调用，将 ExoPlayer 解码后的 PCM 推送至 AAudio / OpenSL ES 底层驱动。
 *
 * PCM [encoding] 标识：0 = 16-bit, 1 = 24-bit (packed), 2 = 32-bit, 3 = float32。
 * [audioApi] 标识：0 = 自动, 1 = AAudio, 2 = OpenSL ES。
 *
 * 当 [exclusive] 为 true 时，若硬件 HAL 未授予独占模式，则打开直接判定失败，保证 Bit-Perfect 不被篡改。
 */
class OboeAudioOutput {

    private var handle: Long = 0L

    fun open(
        audioApi: Int,
        sampleRate: Int,
        channelCount: Int,
        encoding: Int,
        exclusive: Boolean,
        deviceId: Int
    ): Boolean {
        if (!ensureLoaded()) return false
        handle = nativeOpen(audioApi, sampleRate, channelCount, encoding, exclusive, deviceId)
        return handle != 0L
    }

    val isOpen: Boolean get() = handle != 0L

    /** 仅当底层实际获取到 Exclusive 独占共享模式时返回 true */
    val isExclusive: Boolean
        get() = handle != 0L && nativeIsExclusive(handle)

    /** 阻塞写入 Direct ByteBuffer 区域，返回消费的字节数，-1 表示错误，-2 表示设备断开 */
    fun write(buffer: ByteBuffer, offset: Int, length: Int, timeoutNanos: Long): Int =
        if (handle == 0L) -1 else nativeWrite(handle, buffer, offset, length, timeoutNanos)

    fun framesRead(): Long = if (handle == 0L) 0L else nativeGetFramesRead(handle)

    fun outputSampleRate(): Int = if (handle == 0L) 0 else nativeGetSampleRate(handle)

    fun start() { if (handle != 0L) nativeStart(handle) }
    fun pause() { if (handle != 0L) nativePause(handle) }
    fun flush() { if (handle != 0L) nativeFlush(handle) }

    fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    private external fun nativeOpen(
        audioApi: Int, sampleRate: Int, channelCount: Int, encoding: Int, exclusive: Boolean, deviceId: Int
    ): Long
    private external fun nativeIsExclusive(handle: Long): Boolean
    private external fun nativeWrite(handle: Long, buffer: ByteBuffer, offset: Int, length: Int, timeoutNanos: Long): Int
    private external fun nativeGetFramesRead(handle: Long): Long
    private external fun nativeGetSampleRate(handle: Long): Int
    private external fun nativeStart(handle: Long)
    private external fun nativePause(handle: Long)
    private external fun nativeFlush(handle: Long)
    private external fun nativeClose(handle: Long)

    companion object {
        private const val TAG = "OboeAudioOutput"

        @Volatile
        private var loaded = false

        fun ensureLoaded(): Boolean {
            if (loaded) return true
            return synchronized(this) {
                if (loaded) return@synchronized true
                runCatching {
                    runCatching { System.loadLibrary("c++_shared") }
                    runCatching { System.loadLibrary("oboe") }
                    System.loadLibrary("orbit_oboe")
                }
                    .onSuccess {
                        loaded = true
                        Log.i(TAG, "liborbit_oboe.so loaded successfully")
                    }
                    .onFailure {
                        Log.w(TAG, "Failed to load liborbit_oboe.so: ${it.message}")
                    }
                    .isSuccess
            }
        }
    }
}
