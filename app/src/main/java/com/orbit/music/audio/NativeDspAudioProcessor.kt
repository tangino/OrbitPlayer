package com.orbit.music.audio

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.orbit.music.native.NativeDSP
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 专为 ExoPlayer 设计的高性能原生 C++ 浮点 DSP 音频处理器
 * 直接实现 AudioProcessor 规范接口，确保始终处于活跃状态 (Active)，零延迟接管原始 PCM 音频流：
 * 1. 拦截解码后的音频数据，直接交由自研双二阶高精度 DSP、软拐点限幅器处理；
 * 2. 彻底摆脱 Android 系统 DynamicsProcessing 的劣质频域涂抹与发闷感；
 * 3. 完美支持 32-bit Float PCM 与 16-bit PCM 零拷贝就地处理。
 */
class NativeDspAudioProcessor : AudioProcessor {

    private val dsp = NativeDSP.instance

    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_FLOAT && encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        // 仅处理双声道立体声；若非双声道，由上游 ChannelMappingAudioProcessor 处理
        if (inputAudioFormat.channelCount != 2) {
            inputFormat = AudioProcessor.AudioFormat.NOT_SET
            outputFormat = AudioProcessor.AudioFormat.NOT_SET
            return AudioProcessor.AudioFormat.NOT_SET
        }

        inputFormat = inputAudioFormat
        outputFormat = inputAudioFormat

        if (inputAudioFormat.sampleRate > 0) {
            dsp.setSampleRate(inputAudioFormat.sampleRate.toFloat())
            Log.i(TAG, "NativeDspAudioProcessor 成功配置: 采样率=${inputAudioFormat.sampleRate}, 编码=${if (encoding == C.ENCODING_PCM_FLOAT) "32-bit Float" else "16-bit PCM"}")
        }

        return outputFormat
    }

    override fun isActive(): Boolean {
        // 关键核心：只要已配置了合法音频格式，始终保持 Active 活跃状态，严禁被 ExoPlayer 跳过！
        return outputFormat != AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // 确保分配足够容量的直接内存 (DirectByteBuffer)
        if (buffer.capacity() < remaining) {
            buffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        // 拷贝输入数据到工作缓冲区
        buffer.put(inputBuffer)
        buffer.flip()

        val bytesPerFrame = if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) 8 else 4
        val numFrames = remaining / bytesPerFrame

        if (numFrames > 0 && dsp.isReady()) {
            if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) {
                dsp.processDirectFloatBuffer(buffer, 0, numFrames)
            } else {
                dsp.processDirectInt16Buffer(buffer, 0, numFrames)
            }
        }

        outputBuffer = buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer == AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        outputFormat = AudioProcessor.AudioFormat.NOT_SET
    }

    companion object {
        private const val TAG = "NativeDspAudioProcessor"
    }
}
