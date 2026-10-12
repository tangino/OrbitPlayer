#pragma once

#include "AudioDecoder.h"
#include "AudioRingBuffer.h"
#include "AAudioOutput.h"
#include "../dsp/DSPChain.h"
#include <thread>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <memory>
#include <string>

namespace player {

enum class PlayerState {
    IDLE = 0,
    INITIALIZED = 1,
    PREPARED = 2,
    PLAYING = 3,
    PAUSED = 4,
    STOPPED = 5,
    COMPLETED = 6,
    ERROR = 7
};

/**
 * 原生音频播放管道中枢
 * 架构：解码线程 (AMediaCodec/FD) -> SPSC 浮点环形缓冲 -> DSP 效果链 -> AAudio 低延迟回调
 */
class NativeAudioPipeline {
public:
    NativeAudioPipeline();
    ~NativeAudioPipeline();

    // 禁用拷贝
    NativeAudioPipeline(const NativeAudioPipeline&) = delete;
    NativeAudioPipeline& operator=(const NativeAudioPipeline&) = delete;

    /**
     * 打开本地路径音频文件
     */
    bool open(const std::string& path);

    /**
     * 通过 Linux 文件描述符直接打开（支持零拷贝直传）
     */
    bool openFd(int fd, int64_t offset, int64_t length);

    /**
     * 开始或恢复播放
     */
    bool play();

    /**
     * 暂停播放
     */
    bool pause();

    /**
     * 定位播放时间 (毫秒)
     */
    bool seekTo(int64_t positionMs);

    /**
     * 停止播放
     */
    bool stop();

    /**
     * 重置播放器到闲置状态
     */
    void reset();

    /**
     * 设置主音量 (0.0f ~ 1.0f)，直接在浮点域应用 DVC
     */
    void setVolume(float volume);
    float getVolume() const { return mMasterVolume.load(std::memory_order_relaxed); }

    int64_t getDurationMs() const;
    int64_t getPositionMs() const;
    PlayerState getState() const { return mState.load(std::memory_order_relaxed); }

    /**
     * 获取绑定的 DSP 链指针
     */
    dsp::DSPChain* getDSPChain() { return &mDSPChain; }

private:
    void startDecodeThread();
    void stopDecodeThread();
    void decodeLoop();
    void onRenderAudio(float* outBuffer, int32_t numFrames);

    std::unique_ptr<AudioDecoder> mDecoder;
    std::unique_ptr<AudioRingBuffer> mRingBuffer;
    std::unique_ptr<AAudioOutput> mOutput;
    dsp::DSPChain mDSPChain;

    std::thread mDecodeThread;
    std::mutex mDecodeMutex;
    std::condition_variable mDecodeCv;

    std::atomic<PlayerState> mState{PlayerState::IDLE};
    std::atomic<bool> mShouldStopDecode{false};
    std::atomic<bool> mIsSeeking{false};
    std::atomic<int64_t> mSeekTargetUs{0};

    std::atomic<float> mMasterVolume{1.0f};
    std::atomic<int64_t> mRenderedFrames{0};
    int mSampleRate = 48000;
};

} // namespace player
