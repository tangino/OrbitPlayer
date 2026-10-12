#include "AAudioOutput.h"
#include <android/log.h>
#include <dlfcn.h>
#include <cstring>
#include <mutex>

#define LOG_TAG "AAudioOutput"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace player {

// AAudio 常量定义
constexpr int32_t AAUDIO_OK_VAL = 0;
constexpr int32_t AAUDIO_CALLBACK_RESULT_CONTINUE_VAL = 0;
constexpr int32_t AAUDIO_CALLBACK_RESULT_STOP_VAL = 1;
constexpr int32_t AAUDIO_FORMAT_PCM_FLOAT_VAL = 2;
constexpr int32_t AAUDIO_SHARING_MODE_EXCLUSIVE_VAL = 0;
constexpr int32_t AAUDIO_SHARING_MODE_SHARED_VAL = 1;
constexpr int32_t AAUDIO_PERFORMANCE_MODE_LOW_LATENCY_VAL = 12;
constexpr int32_t AAUDIO_STREAM_STATE_PLAYING_VAL = 4;

// 函数指针类型定义
typedef aaudio_data_callback_result_t (*AAudioStream_dataCallback)(
    AAudioStream* stream, void* userData, void* audioData, int32_t numFrames);

typedef void (*AAudioStream_errorCallback)(
    AAudioStream* stream, void* userData, aaudio_result_t error);

typedef aaudio_result_t (*fn_AAudio_createStreamBuilder)(AAudioStreamBuilder** builder);
typedef void (*fn_AAudioStreamBuilder_setFormat)(AAudioStreamBuilder* builder, int32_t format);
typedef void (*fn_AAudioStreamBuilder_setChannelCount)(AAudioStreamBuilder* builder, int32_t channelCount);
typedef void (*fn_AAudioStreamBuilder_setSampleRate)(AAudioStreamBuilder* builder, int32_t sampleRate);
typedef void (*fn_AAudioStreamBuilder_setPerformanceMode)(AAudioStreamBuilder* builder, int32_t mode);
typedef void (*fn_AAudioStreamBuilder_setSharingMode)(AAudioStreamBuilder* builder, int32_t mode);
typedef void (*fn_AAudioStreamBuilder_setDataCallback)(AAudioStreamBuilder* builder, AAudioStream_dataCallback callback, void* userData);
typedef void (*fn_AAudioStreamBuilder_setErrorCallback)(AAudioStreamBuilder* builder, AAudioStream_errorCallback callback, void* userData);
typedef aaudio_result_t (*fn_AAudioStreamBuilder_openStream)(AAudioStreamBuilder* builder, AAudioStream** stream);
typedef aaudio_result_t (*fn_AAudioStreamBuilder_delete)(AAudioStreamBuilder* builder);

typedef int32_t (*fn_AAudioStream_getSampleRate)(AAudioStream* stream);
typedef int32_t (*fn_AAudioStream_getBufferSizeInFrames)(AAudioStream* stream);
typedef int32_t (*fn_AAudioStream_getState)(AAudioStream* stream);
typedef aaudio_result_t (*fn_AAudioStream_requestStart)(AAudioStream* stream);
typedef aaudio_result_t (*fn_AAudioStream_requestPause)(AAudioStream* stream);
typedef aaudio_result_t (*fn_AAudioStream_requestStop)(AAudioStream* stream);
typedef aaudio_result_t (*fn_AAudioStream_close)(AAudioStream* stream);
typedef const char* (*fn_AAudio_convertResultToText)(aaudio_result_t returnCode);

static void* sAAudioLibHandle = nullptr;
static std::mutex sLoaderMutex;
static bool sSymbolsLoaded = false;

static fn_AAudio_createStreamBuilder pAAudio_createStreamBuilder = nullptr;
static fn_AAudioStreamBuilder_setFormat pAAudioStreamBuilder_setFormat = nullptr;
static fn_AAudioStreamBuilder_setChannelCount pAAudioStreamBuilder_setChannelCount = nullptr;
static fn_AAudioStreamBuilder_setSampleRate pAAudioStreamBuilder_setSampleRate = nullptr;
static fn_AAudioStreamBuilder_setPerformanceMode pAAudioStreamBuilder_setPerformanceMode = nullptr;
static fn_AAudioStreamBuilder_setSharingMode pAAudioStreamBuilder_setSharingMode = nullptr;
static fn_AAudioStreamBuilder_setDataCallback pAAudioStreamBuilder_setDataCallback = nullptr;
static fn_AAudioStreamBuilder_setErrorCallback pAAudioStreamBuilder_setErrorCallback = nullptr;
static fn_AAudioStreamBuilder_openStream pAAudioStreamBuilder_openStream = nullptr;
static fn_AAudioStreamBuilder_delete pAAudioStreamBuilder_delete = nullptr;
static fn_AAudioStream_getSampleRate pAAudioStream_getSampleRate = nullptr;
static fn_AAudioStream_getBufferSizeInFrames pAAudioStream_getBufferSizeInFrames = nullptr;
static fn_AAudioStream_getState pAAudioStream_getState = nullptr;
static fn_AAudioStream_requestStart pAAudioStream_requestStart = nullptr;
static fn_AAudioStream_requestPause pAAudioStream_requestPause = nullptr;
static fn_AAudioStream_requestStop pAAudioStream_requestStop = nullptr;
static fn_AAudioStream_close pAAudioStream_close = nullptr;
static fn_AAudio_convertResultToText pAAudio_convertResultToText = nullptr;

bool AAudioOutput::loadAAudioSymbols() {
    std::lock_guard<std::mutex> lock(sLoaderMutex);
    if (sSymbolsLoaded) return (sAAudioLibHandle != nullptr);

    sSymbolsLoaded = true;
    sAAudioLibHandle = dlopen("libaaudio.so", RTLD_NOW | RTLD_LOCAL);
    if (!sAAudioLibHandle) {
        LOGW("当前系统未找到 libaaudio.so (需 Android 8.0 / API 26 以上)");
        return false;
    }

#define LOAD_SYMBOL(name) \
    p##name = reinterpret_cast<fn_##name>(dlsym(sAAudioLibHandle, #name)); \
    if (!p##name) { \
        LOGE("无法加载符号: %s", #name); \
        dlclose(sAAudioLibHandle); \
        sAAudioLibHandle = nullptr; \
        return false; \
    }

    LOAD_SYMBOL(AAudio_createStreamBuilder)
    LOAD_SYMBOL(AAudioStreamBuilder_setFormat)
    LOAD_SYMBOL(AAudioStreamBuilder_setChannelCount)
    LOAD_SYMBOL(AAudioStreamBuilder_setSampleRate)
    LOAD_SYMBOL(AAudioStreamBuilder_setPerformanceMode)
    LOAD_SYMBOL(AAudioStreamBuilder_setSharingMode)
    LOAD_SYMBOL(AAudioStreamBuilder_setDataCallback)
    LOAD_SYMBOL(AAudioStreamBuilder_setErrorCallback)
    LOAD_SYMBOL(AAudioStreamBuilder_openStream)
    LOAD_SYMBOL(AAudioStreamBuilder_delete)
    LOAD_SYMBOL(AAudioStream_getSampleRate)
    LOAD_SYMBOL(AAudioStream_getBufferSizeInFrames)
    LOAD_SYMBOL(AAudioStream_getState)
    LOAD_SYMBOL(AAudioStream_requestStart)
    LOAD_SYMBOL(AAudioStream_requestPause)
    LOAD_SYMBOL(AAudioStream_requestStop)
    LOAD_SYMBOL(AAudioStream_close)
    LOAD_SYMBOL(AAudio_convertResultToText)

#undef LOAD_SYMBOL

    LOGI("成功动态加载 libaaudio.so 原生 API 接口");
    return true;
}

bool AAudioOutput::isSupported() {
    return loadAAudioSymbols();
}

AAudioOutput::AAudioOutput() = default;

AAudioOutput::~AAudioOutput() {
    close();
}

bool AAudioOutput::open(int32_t sampleRate, AudioRenderCallback renderCallback) {
    close();

    if (!loadAAudioSymbols()) {
        LOGE("AAudio 在当前系统不可用");
        return false;
    }

    mSampleRate = sampleRate;
    mRenderCallback = std::move(renderCallback);

    // 优先尝试 EXCLUSIVE 独占直通模式 (Bit-Perfect, 绕过 AudioFlinger 混音器与劣质重采样)
    auto buildAndOpen = [this](int32_t sharingMode) -> bool {
        AAudioStreamBuilder* builder = nullptr;
        aaudio_result_t result = pAAudio_createStreamBuilder(&builder);
        if (result != AAUDIO_OK_VAL || !builder) {
            return false;
        }

        pAAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT_VAL);
        pAAudioStreamBuilder_setChannelCount(builder, 2);
        pAAudioStreamBuilder_setSampleRate(builder, mSampleRate);
        pAAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY_VAL);
        pAAudioStreamBuilder_setSharingMode(builder, sharingMode);
        pAAudioStreamBuilder_setDataCallback(builder, dataCallback, this);
        pAAudioStreamBuilder_setErrorCallback(builder, errorCallback, this);

        result = pAAudioStreamBuilder_openStream(builder, &mStream);
        pAAudioStreamBuilder_delete(builder);

        if (result != AAUDIO_OK_VAL || !mStream) {
            mStream = nullptr;
            return false;
        }
        return true;
    };

    bool opened = buildAndOpen(AAUDIO_SHARING_MODE_EXCLUSIVE_VAL);
    if (opened) {
        LOGI("AAudio 以 EXCLUSIVE 独占直通模式成功打开！(源码直通)");
    } else {
        LOGI("EXCLUSIVE 模式不可用，降级为 SHARED 共享模式打开...");
        opened = buildAndOpen(AAUDIO_SHARING_MODE_SHARED_VAL);
    }

    if (!opened || !mStream) {
        LOGE("打开 AAudioStream 失败");
        mStream = nullptr;
        return false;
    }

    mSampleRate = pAAudioStream_getSampleRate(mStream);
    LOGI("AAudioStream 打开成功, 实际采样率: %d, 缓冲区帧数: %d",
         mSampleRate, pAAudioStream_getBufferSizeInFrames(mStream));

    return true;
}

bool AAudioOutput::start() {
    if (!mStream || !pAAudioStream_requestStart) return false;

    int32_t state = pAAudioStream_getState(mStream);
    if (state == AAUDIO_STREAM_STATE_PLAYING_VAL) {
        mIsPlaying.store(true, std::memory_order_relaxed);
        return true;
    }

    aaudio_result_t result = pAAudioStream_requestStart(mStream);
    if (result != AAUDIO_OK_VAL) {
        LOGE("启动 AAudioStream 失败");
        return false;
    }

    mIsPlaying.store(true, std::memory_order_relaxed);
    LOGI("AAudioStream 已启动");
    return true;
}

bool AAudioOutput::pause() {
    if (!mStream || !pAAudioStream_requestPause) return false;

    aaudio_result_t result = pAAudioStream_requestPause(mStream);
    if (result != AAUDIO_OK_VAL) {
        LOGE("暂停 AAudioStream 失败");
        return false;
    }

    mIsPlaying.store(false, std::memory_order_relaxed);
    LOGI("AAudioStream 已暂停");
    return true;
}

bool AAudioOutput::stop() {
    if (!mStream || !pAAudioStream_requestStop) return false;

    aaudio_result_t result = pAAudioStream_requestStop(mStream);
    if (result != AAUDIO_OK_VAL) {
        LOGE("停止 AAudioStream 失败");
        return false;
    }

    mIsPlaying.store(false, std::memory_order_relaxed);
    LOGI("AAudioStream 已停止");
    return true;
}

void AAudioOutput::close() {
    mIsPlaying.store(false, std::memory_order_relaxed);
    if (mStream && pAAudioStream_close) {
        pAAudioStream_requestStop(mStream);
        pAAudioStream_close(mStream);
        mStream = nullptr;
        LOGI("AAudioStream 已关闭");
    }
    mRenderCallback = nullptr;
}

aaudio_data_callback_result_t AAudioOutput::dataCallback(
    AAudioStream* stream,
    void* userData,
    void* audioData,
    int32_t numFrames) {
    auto* self = static_cast<AAudioOutput*>(userData);
    if (!self || !audioData) {
        return AAUDIO_CALLBACK_RESULT_STOP_VAL;
    }

    float* outFloatBuffer = static_cast<float*>(audioData);

    if (self->mRenderCallback) {
        self->mRenderCallback(outFloatBuffer, numFrames);
    } else {
        std::memset(outFloatBuffer, 0, numFrames * 2 * sizeof(float));
    }

    return AAUDIO_CALLBACK_RESULT_CONTINUE_VAL;
}

void AAudioOutput::errorCallback(
    AAudioStream* stream,
    void* userData,
    aaudio_result_t error) {
    LOGE("AAudioStream 发生错误回调, 错误码: %d", error);
}

} // namespace player
