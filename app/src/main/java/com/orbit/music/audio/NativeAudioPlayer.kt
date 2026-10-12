package com.orbit.music.audio

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.orbit.music.native.NativeAudioPlayerEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 原生全 Native 播放控制器 (参考 Poweramp 播放流水线架构)
 * 具备 Linux FD 直通、32位全浮点管线、AAudio 低延迟输出以及直通 Native DSP
 */
class NativeAudioPlayer private constructor() {

    private val engine = NativeAudioPlayerEngine()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private var currentPfd: ParcelFileDescriptor? = null

    // 播放状态流
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _engineState = MutableStateFlow(NativeAudioPlayerEngine.STATE_IDLE)
    val engineState: StateFlow<Int> = _engineState.asStateFlow()

    // 播放完成监听器
    var onCompletionListener: (() -> Unit)? = null
    var onErrorListener: ((String) -> Unit)? = null

    /**
     * 通过文件路径打开
     */
    fun openPath(path: String): Boolean {
        closeCurrentPfd()
        val ok = engine.openPath(path)
        if (ok) {
            updateTrackInfo()
        } else {
            _engineState.value = NativeAudioPlayerEngine.STATE_ERROR
            onErrorListener?.invoke("打开文件失败: $path")
        }
        return ok
    }

    /**
     * 通过 Uri 打开（支持 Android SAF content:// 与 file://，直接提取 FD 零拷贝送入底层）
     */
    fun openUri(context: Context, uri: Uri): Boolean {
        closeCurrentPfd()

        if (uri.scheme == "file") {
            return openPath(uri.path ?: "")
        }

        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                currentPfd = pfd
                val fd = pfd.fd
                val size = pfd.statSize
                val ok = engine.openFd(fd, 0, if (size > 0) size else 0x7ffffffffffffffL)
                if (ok) {
                    updateTrackInfo()
                } else {
                    _engineState.value = NativeAudioPlayerEngine.STATE_ERROR
                    onErrorListener?.invoke("打开文件描述符失败")
                }
                ok
            } else {
                Log.e(TAG, "获取 ParcelFileDescriptor 失败: $uri")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "openUri 抛出异常", e)
            false
        }
    }

    fun play(): Boolean {
        val ok = engine.play()
        if (ok) {
            _isPlaying.value = true
            _engineState.value = NativeAudioPlayerEngine.STATE_PLAYING
            startProgressPolling()
        }
        return ok
    }

    fun pause(): Boolean {
        val ok = engine.pause()
        if (ok) {
            _isPlaying.value = false
            _engineState.value = NativeAudioPlayerEngine.STATE_PAUSED
            stopProgressPolling()
        }
        return ok
    }

    fun seekTo(positionMs: Long): Boolean {
        val ok = engine.seekTo(positionMs)
        if (ok) {
            _currentPositionMs.value = positionMs
        }
        return ok
    }

    fun stop(): Boolean {
        val ok = engine.stop()
        _isPlaying.value = false
        _engineState.value = NativeAudioPlayerEngine.STATE_STOPPED
        stopProgressPolling()
        return ok
    }

    fun reset() {
        stopProgressPolling()
        closeCurrentPfd()
        engine.reset()
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _engineState.value = NativeAudioPlayerEngine.STATE_IDLE
    }

    fun setVolume(volume: Float) {
        engine.setVolume(volume)
    }

    fun getDSPChainHandle(): Long {
        return engine.getDSPChainHandle()
    }

    private fun updateTrackInfo() {
        _durationMs.value = engine.getDuration()
        _currentPositionMs.value = engine.getPosition()
        _engineState.value = engine.getState()
        _isPlaying.value = false
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                val pos = engine.getPosition()
                val state = engine.getState()
                _currentPositionMs.value = pos
                _engineState.value = state

                if (state == NativeAudioPlayerEngine.STATE_COMPLETED) {
                    _isPlaying.value = false
                    stopProgressPolling()
                    onCompletionListener?.invoke()
                    break
                }
                delay(200)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun closeCurrentPfd() {
        try {
            currentPfd?.close()
        } catch (e: Exception) {
            Log.w(TAG, "关闭旧 ParcelFileDescriptor 出现异常", e)
        }
        currentPfd = null
    }

    fun release() {
        reset()
        scope.cancel()
        engine.release()
    }

    companion object {
        private const val TAG = "NativeAudioPlayer"
        val instance: NativeAudioPlayer by lazy { NativeAudioPlayer() }
    }
}
