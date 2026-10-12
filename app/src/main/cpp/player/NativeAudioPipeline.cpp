#include "NativeAudioPipeline.h"
#include <android/log.h>
#include <chrono>
#include <algorithm>
#include <cstring>

#define LOG_TAG "NativeAudioPipeline"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace player {

constexpr size_t RING_BUFFER_CAPACITY_FRAMES = 192000; // 约 4 秒 48kHz 立体声缓冲

NativeAudioPipeline::NativeAudioPipeline()
    : mDecoder(std::make_unique<AudioDecoder>()),
      mRingBuffer(std::make_unique<AudioRingBuffer>(RING_BUFFER_CAPACITY_FRAMES, 2)),
      mOutput(std::make_unique<AAudioOutput>()) {
    // 默认开启 DSP 链
    mDSPChain.setEnabled(true);
}

NativeAudioPipeline::~NativeAudioPipeline() {
    reset();
}

void NativeAudioPipeline::reset() {
    stop();

    mState.store(PlayerState::IDLE, std::memory_order_relaxed);
    if (mOutput) {
        mOutput->close();
    }
    if (mDecoder) {
        mDecoder->close();
    }
    if (mRingBuffer) {
        mRingBuffer->reset();
    }
    mRenderedFrames.store(0, std::memory_order_relaxed);
}

bool NativeAudioPipeline::open(const std::string& path) {
    reset();

    if (!mDecoder->openPath(path)) {
        LOGE("打开文件路径失败: %s", path.c_str());
        mState.store(PlayerState::ERROR, std::memory_order_relaxed);
        return false;
    }

    mSampleRate = mDecoder->getSampleRate();
    mDSPChain.setSampleRate(static_cast<float>(mSampleRate));

    // 打开 AAudio 输出
    bool ok = mOutput->open(mSampleRate, [this](float* outBuffer, int32_t numFrames) {
        onRenderAudio(outBuffer, numFrames);
    });

    if (!ok) {
        LOGE("初始化 AAudio 输出失败");
        mState.store(PlayerState::ERROR, std::memory_order_relaxed);
        return false;
    }

    mState.store(PlayerState::PREPARED, std::memory_order_relaxed);
    startDecodeThread();
    LOGI("音频资源准备就绪, 采样率: %d, 时长: %lld ms", mSampleRate, static_cast<long long>(getDurationMs()));
    return true;
}

bool NativeAudioPipeline::openFd(int fd, int64_t offset, int64_t length) {
    reset();

    if (!mDecoder->openFd(fd, offset, length)) {
        LOGE("打开文件描述符失败, fd: %d", fd);
        mState.store(PlayerState::ERROR, std::memory_order_relaxed);
        return false;
    }

    mSampleRate = mDecoder->getSampleRate();
    mDSPChain.setSampleRate(static_cast<float>(mSampleRate));

    // 打开 AAudio 输出
    bool ok = mOutput->open(mSampleRate, [this](float* outBuffer, int32_t numFrames) {
        onRenderAudio(outBuffer, numFrames);
    });

    if (!ok) {
        LOGE("初始化 AAudio 输出失败");
        mState.store(PlayerState::ERROR, std::memory_order_relaxed);
        return false;
    }

    mState.store(PlayerState::PREPARED, std::memory_order_relaxed);
    startDecodeThread();
    LOGI("FD 音频资源准备就绪, 采样率: %d, 时长: %lld ms", mSampleRate, static_cast<long long>(getDurationMs()));
    return true;
}

bool NativeAudioPipeline::play() {
    PlayerState curState = mState.load(std::memory_order_relaxed);
    if (curState != PlayerState::PREPARED && curState != PlayerState::PAUSED && curState != PlayerState::COMPLETED) {
        LOGW("当前状态无法执行 play: %d", static_cast<int>(curState));
        return false;
    }

    if (curState == PlayerState::COMPLETED) {
        seekTo(0);
    }

    if (!mOutput->start()) {
        LOGE("启动音频输出失败");
        return false;
    }

    mState.store(PlayerState::PLAYING, std::memory_order_relaxed);
    mDecodeCv.notify_all();
    LOGI("播放已开始");
    return true;
}

bool NativeAudioPipeline::pause() {
    PlayerState curState = mState.load(std::memory_order_relaxed);
    if (curState != PlayerState::PLAYING) {
        return false;
    }

    mOutput->pause();
    mState.store(PlayerState::PAUSED, std::memory_order_relaxed);
    LOGI("播放已暂停");
    return true;
}

bool NativeAudioPipeline::stop() {
    stopDecodeThread();

    if (mOutput) {
        mOutput->stop();
    }

    mState.store(PlayerState::STOPPED, std::memory_order_relaxed);
    LOGI("播放已停止");
    return true;
}

bool NativeAudioPipeline::seekTo(int64_t positionMs) {
    if (!mDecoder) return false;

    int64_t targetUs = positionMs * 1000;
    mSeekTargetUs.store(targetUs, std::memory_order_relaxed);
    mIsSeeking.store(true, std::memory_order_relaxed);

    mDecodeCv.notify_all();
    LOGI("执行定位 Seek 到: %lld ms", static_cast<long long>(positionMs));
    return true;
}

void NativeAudioPipeline::setVolume(float volume) {
    volume = std::max(0.0f, std::min(1.0f, volume));
    mMasterVolume.store(volume, std::memory_order_relaxed);
}

int64_t NativeAudioPipeline::getDurationMs() const {
    if (!mDecoder) return 0;
    return mDecoder->getDurationUs() / 1000;
}

int64_t NativeAudioPipeline::getPositionMs() const {
    if (!mDecoder || mSampleRate <= 0) return 0;
    int64_t frames = mRenderedFrames.load(std::memory_order_relaxed);
    return (frames * 1000) / mSampleRate;
}

void NativeAudioPipeline::startDecodeThread() {
    stopDecodeThread();

    mShouldStopDecode.store(false, std::memory_order_relaxed);
    mDecodeThread = std::thread(&NativeAudioPipeline::decodeLoop, this);
}

void NativeAudioPipeline::stopDecodeThread() {
    mShouldStopDecode.store(true, std::memory_order_relaxed);
    mDecodeCv.notify_all();

    if (mDecodeThread.joinable()) {
        mDecodeThread.join();
    }
}

void NativeAudioPipeline::decodeLoop() {
    DecodedAudioChunk chunk;

    while (!mShouldStopDecode.load(std::memory_order_relaxed)) {
        // 1. 处理 Seek 请求
        if (mIsSeeking.load(std::memory_order_relaxed)) {
            int64_t seekTarget = mSeekTargetUs.load(std::memory_order_relaxed);
            mDecoder->seekTo(seekTarget);
            mRingBuffer->reset();

            int64_t renderedFrames = (seekTarget * mSampleRate) / 1000000;
            mRenderedFrames.store(renderedFrames, std::memory_order_relaxed);
            mIsSeeking.store(false, std::memory_order_relaxed);
        }

        // 2. 检查环形缓冲区是否有写入空间
        size_t availableWriteFrames = mRingBuffer->getAvailableWriteFrames();
        if (availableWriteFrames < 4096) {
            std::unique_lock<std::mutex> lock(mDecodeMutex);
            mDecodeCv.wait_for(lock, std::chrono::milliseconds(10), [this]() {
                return mShouldStopDecode.load(std::memory_order_relaxed) ||
                       mIsSeeking.load(std::memory_order_relaxed) ||
                       mRingBuffer->getAvailableWriteFrames() >= 4096;
            });
            continue;
        }

        // 3. 解码下一个音频块
        bool hasData = mDecoder->decodeNextChunk(chunk);
        if (hasData && !chunk.samples.empty()) {
            size_t frames = chunk.samples.size() / 2;
            mRingBuffer->write(chunk.samples.data(), frames);
        } else if (chunk.isEndOfStream) {
            // 已读完所有样本，等待环形缓冲被消费完毕
            while (!mShouldStopDecode.load(std::memory_order_relaxed) &&
                   !mIsSeeking.load(std::memory_order_relaxed) &&
                   mRingBuffer->getAvailableReadFrames() > 0) {
                std::this_thread::sleep_for(std::chrono::milliseconds(20));
            }

            if (!mIsSeeking.load(std::memory_order_relaxed)) {
                mState.store(PlayerState::COMPLETED, std::memory_order_relaxed);
                mOutput->pause();
                LOGI("音频播放完毕 (EOS)");
                break;
            }
        } else {
            // 解码器暂时无输出，稍作休眠
            std::this_thread::sleep_for(std::chrono::milliseconds(2));
        }
    }
}

void NativeAudioPipeline::onRenderAudio(float* outBuffer, int32_t numFrames) {
    if (!outBuffer || numFrames <= 0) return;

    size_t framesRead = mRingBuffer->read(outBuffer, numFrames);

    // 缓冲区欠载时进行静音填充防破音
    if (framesRead < static_cast<size_t>(numFrames)) {
        size_t missingFrames = static_cast<size_t>(numFrames) - framesRead;
        std::memset(outBuffer + framesRead * 2, 0, missingFrames * 2 * sizeof(float));
    }

    // 关键核心：送入浮点 DSP 链进行全链路处理（EQ、Compressor、Limiter、Preamp 等）
    mDSPChain.process(outBuffer, numFrames);

    // 直接音量控制 (DVC) 浮点衰减
    float vol = mMasterVolume.load(std::memory_order_relaxed);
    if (vol < 0.999f) {
        size_t totalSamples = static_cast<size_t>(numFrames) * 2;
        for (size_t i = 0; i < totalSamples; ++i) {
            outBuffer[i] *= vol;
        }
    }

    mRenderedFrames.fetch_add(framesRead, std::memory_order_relaxed);
}

} // namespace player
