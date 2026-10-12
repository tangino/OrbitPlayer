#pragma once

#include <functional>
#include <atomic>
#include <cstdint>

// 前置类型声明，避免低版本 NDK (API 21) 找不到 aaudio/AAudio.h
struct AAudioStreamStruct;
typedef struct AAudioStreamStruct AAudioStream;
struct AAudioStreamBuilderStruct;
typedef struct AAudioStreamBuilderStruct AAudioStreamBuilder;

typedef int32_t aaudio_result_t;
typedef int32_t aaudio_data_callback_result_t;

namespace player {

/**
 * 音频数据请求回调类型
 * @param outBuffer 目标浮点交织立体声缓冲区
 * @param numFrames 本次需要渲染的帧数
 */
using AudioRenderCallback = std::function<void(float* outBuffer, int32_t numFrames)>;

/**
 * 动态加载 libaaudio.so 的原生低延迟音频输出后端
 * 兼容 Android 21+ 编译，并在 Android 8.0+ 运行时自动激活 AAudio 硬件低延迟直推
 */
class AAudioOutput {
public:
    AAudioOutput();
    ~AAudioOutput();

    // 禁用拷贝
    AAudioOutput(const AAudioOutput&) = delete;
    AAudioOutput& operator=(const AAudioOutput&) = delete;

    /**
     * 打开并初始化 AAudioStream
     * @param sampleRate 采样率 (如 44100 或 48000)
     * @param renderCallback 数据渲染填充回调
     */
    bool open(int32_t sampleRate, AudioRenderCallback renderCallback);

    /**
     * 开始播放音频流
     */
    bool start();

    /**
     * 暂停音频流
     */
    bool pause();

    /**
     * 停止音频流
     */
    bool stop();

    /**
     * 关闭并释放流资源
     */
    void close();

    int32_t getSampleRate() const { return mSampleRate; }
    int32_t getChannelCount() const { return 2; }
    bool isPlaying() const { return mIsPlaying.load(std::memory_order_relaxed); }
    static bool isSupported();

private:
    static bool loadAAudioSymbols();

    static aaudio_data_callback_result_t dataCallback(
        AAudioStream* stream,
        void* userData,
        void* audioData,
        int32_t numFrames);

    static void errorCallback(
        AAudioStream* stream,
        void* userData,
        aaudio_result_t error);

    AAudioStream* mStream = nullptr;
    int32_t mSampleRate = 48000;
    AudioRenderCallback mRenderCallback = nullptr;
    std::atomic<bool> mIsPlaying{false};
};

} // namespace player
