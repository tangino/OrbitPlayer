#pragma once

#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <string>
#include <vector>
#include <cstdint>
#include <memory>

namespace player {

/**
 * 解码出的音频帧数据块
 */
struct DecodedAudioChunk {
    std::vector<float> samples; // 交织浮点立体声样本
    int64_t presentationTimeUs = 0;
    bool isEndOfStream = false;
};

/**
 * 基于 Android NDK AMediaExtractor + AMediaCodec 的高性能音频硬件解码器
 */
class AudioDecoder {
public:
    AudioDecoder();
    ~AudioDecoder();

    // 禁用拷贝
    AudioDecoder(const AudioDecoder&) = delete;
    AudioDecoder& operator=(const AudioDecoder&) = delete;

    /**
     * 通过文件路径打开音频
     */
    bool openPath(const std::string& path);

    /**
     * 通过文件描述符打开音频（支持零拷贝直通）
     * @param fd 文件描述符
     * @param offset 偏移量
     * @param length 长度
     */
    bool openFd(int fd, int64_t offset, int64_t length);

    /**
     * 读取并解码下一批音频帧（转换为 Float32 立体声）
     * @param outChunk 输出帧容器
     * @return 是否成功读取（返回 false 且 isEndOfStream 为 true 代表播放结束）
     */
    bool decodeNextChunk(DecodedAudioChunk& outChunk);

    /**
     * 精确定位时间戳
     * @param timeUs 微秒时间戳
     */
    bool seekTo(int64_t timeUs);

    /**
     * 关闭并释放解码器资源
     */
    void close();

    int getSampleRate() const { return mSampleRate; }
    int getChannelCount() const { return mChannelCount; }
    int64_t getDurationUs() const { return mDurationUs; }
    int64_t getCurrentPositionUs() const { return mCurrentPositionUs; }
    bool isEndOfStream() const { return mSawInputEOS && mSawOutputEOS; }

private:
    bool initCodec();
    void convertPcmToFloatStereo(const uint8_t* pcmBytes, size_t byteCount, std::vector<float>& outFloatSamples);

    AMediaExtractor* mExtractor = nullptr;
    AMediaCodec* mCodec = nullptr;
    AMediaFormat* mFormat = nullptr;

    int mAudioTrackIndex = -1;
    int mSampleRate = 44100;
    int mChannelCount = 2;
    int mPcmEncoding = 2; // 2: 16-bit, 4: Float, 21: 24-bit packed
    int64_t mDurationUs = 0;
    int64_t mCurrentPositionUs = 0;

    bool mSawInputEOS = false;
    bool mSawOutputEOS = false;

    // 格式暂存缓冲区
    std::vector<float> mTempFloatBuffer;
};

} // namespace player
