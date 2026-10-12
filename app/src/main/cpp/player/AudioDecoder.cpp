#include "AudioDecoder.h"
#include <android/log.h>
#include <cstring>
#include <algorithm>

#define LOG_TAG "AudioDecoder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace player {

AudioDecoder::AudioDecoder() = default;

AudioDecoder::~AudioDecoder() {
    close();
}

void AudioDecoder::close() {
    if (mCodec) {
        AMediaCodec_stop(mCodec);
        AMediaCodec_delete(mCodec);
        mCodec = nullptr;
    }
    if (mFormat) {
        AMediaFormat_delete(mFormat);
        mFormat = nullptr;
    }
    if (mExtractor) {
        AMediaExtractor_delete(mExtractor);
        mExtractor = nullptr;
    }
    mAudioTrackIndex = -1;
    mSawInputEOS = false;
    mSawOutputEOS = false;
    mCurrentPositionUs = 0;
    mDurationUs = 0;
}

bool AudioDecoder::openPath(const std::string& path) {
    close();

    mExtractor = AMediaExtractor_new();
    if (!mExtractor) {
        LOGE("创建 AMediaExtractor 失败");
        return false;
    }

    media_status_t status = AMediaExtractor_setDataSource(mExtractor, path.c_str());
    if (status != AMEDIA_OK) {
        LOGE("设置数据源路径失败: %s, 错误码: %d", path.c_str(), status);
        close();
        return false;
    }

    return initCodec();
}

bool AudioDecoder::openFd(int fd, int64_t offset, int64_t length) {
    close();

    mExtractor = AMediaExtractor_new();
    if (!mExtractor) {
        LOGE("创建 AMediaExtractor 失败");
        return false;
    }

    media_status_t status = AMediaExtractor_setDataSourceFd(mExtractor, fd, offset, length);
    if (status != AMEDIA_OK) {
        LOGE("设置 FD 数据源失败, 错误码: %d", status);
        close();
        return false;
    }

    return initCodec();
}

bool AudioDecoder::initCodec() {
    size_t numTracks = AMediaExtractor_getTrackCount(mExtractor);
    for (size_t i = 0; i < numTracks; ++i) {
        AMediaFormat* format = AMediaExtractor_getTrackFormat(mExtractor, i);
        const char* mime = nullptr;
        if (AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime) && mime) {
            if (std::strncmp(mime, "audio/", 6) == 0) {
                mAudioTrackIndex = static_cast<int>(i);
                mFormat = format;
                AMediaExtractor_selectTrack(mExtractor, i);

                AMediaFormat_getInt32(mFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &mSampleRate);
                AMediaFormat_getInt32(mFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &mChannelCount);
                AMediaFormat_getInt64(mFormat, AMEDIAFORMAT_KEY_DURATION, &mDurationUs);

                LOGI("找到音频轨道: %zu, mime: %s, 采样率: %d, 声道数: %d, 时长: %lld us",
                     i, mime, mSampleRate, mChannelCount, static_cast<long long>(mDurationUs));

                mCodec = AMediaCodec_createDecoderByType(mime);
                if (!mCodec) {
                    LOGE("无法创建解码器: %s", mime);
                    close();
                    return false;
                }

                media_status_t codecStatus = AMediaCodec_configure(mCodec, mFormat, nullptr, nullptr, 0);
                if (codecStatus != AMEDIA_OK) {
                    LOGE("配置解码器失败, 错误码: %d", codecStatus);
                    close();
                    return false;
                }

                codecStatus = AMediaCodec_start(mCodec);
                if (codecStatus != AMEDIA_OK) {
                    LOGE("启动解码器失败, 错误码: %d", codecStatus);
                    close();
                    return false;
                }

                mSawInputEOS = false;
                mSawOutputEOS = false;
                return true;
            }
        }
        AMediaFormat_delete(format);
    }

    LOGE("未找到可播放的音频轨道");
    close();
    return false;
}

bool AudioDecoder::decodeNextChunk(DecodedAudioChunk& outChunk) {
    if (!mCodec || !mExtractor || mSawOutputEOS) {
        outChunk.isEndOfStream = true;
        return false;
    }

    constexpr int64_t TIMEOUT_US = 2000; // 2毫秒超时

    // 1. 投递未解码数据到解码器
    if (!mSawInputEOS) {
        ssize_t inputIndex = AMediaCodec_dequeueInputBuffer(mCodec, TIMEOUT_US);
        if (inputIndex >= 0) {
            size_t inputBufferSize = 0;
            uint8_t* inputBuffer = AMediaCodec_getInputBuffer(mCodec, inputIndex, &inputBufferSize);

            ssize_t sampleSize = AMediaExtractor_readSampleData(mExtractor, inputBuffer, inputBufferSize);
            if (sampleSize < 0) {
                // 读到文件末尾
                mSawInputEOS = true;
                AMediaCodec_queueInputBuffer(mCodec, inputIndex, 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
            } else {
                int64_t presentationTimeUs = AMediaExtractor_getSampleTime(mExtractor);
                AMediaCodec_queueInputBuffer(mCodec, inputIndex, 0, sampleSize, presentationTimeUs, 0);
                AMediaExtractor_advance(mExtractor);
            }
        }
    }

    // 2. 从解码器提取解码完成的 PCM 数据
    AMediaCodecBufferInfo info;
    ssize_t outputIndex = AMediaCodec_dequeueOutputBuffer(mCodec, &info, TIMEOUT_US);

    if (outputIndex >= 0) {
        if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
            mSawOutputEOS = true;
        }

        if (info.size > 0) {
            size_t outputBufferSize = 0;
            uint8_t* outputBuffer = AMediaCodec_getOutputBuffer(mCodec, outputIndex, &outputBufferSize);

            if (outputBuffer) {
                outChunk.presentationTimeUs = info.presentationTimeUs;
                mCurrentPositionUs = info.presentationTimeUs;

                // 转换并将 PCM 统一规整为 Float32 立体声
                convertPcmToFloatStereo(outputBuffer + info.offset, info.size, outChunk.samples);
            }
        }

        AMediaCodec_releaseOutputBuffer(mCodec, outputIndex, false);
        outChunk.isEndOfStream = mSawOutputEOS;
        return !outChunk.samples.empty();
    } else if (outputIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
        AMediaFormat* newFormat = AMediaCodec_getOutputFormat(mCodec);
        AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, &mSampleRate);
        AMediaFormat_getInt32(newFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &mChannelCount);
        if (!AMediaFormat_getInt32(newFormat, "pcm-encoding", &mPcmEncoding)) {
            mPcmEncoding = 2; // 未提供时默认 16-bit PCM
        }
        LOGI("解码器格式变更 -> 采样率: %d, 声道数: %d, PCM编码: %d", mSampleRate, mChannelCount, mPcmEncoding);
        AMediaFormat_delete(newFormat);
        return false;
    }

    outChunk.isEndOfStream = mSawOutputEOS;
    return false;
}

void AudioDecoder::convertPcmToFloatStereo(const uint8_t* pcmBytes, size_t byteCount, std::vector<float>& outFloatSamples) {
    outFloatSamples.clear();
    if (!pcmBytes || byteCount == 0 || mChannelCount <= 0) return;

    if (mPcmEncoding == 4) {
        // 原生 32-bit Float PCM (4 字节浮点)
        size_t totalFloats = byteCount / sizeof(float);
        const float* floatPtr = reinterpret_cast<const float*>(pcmBytes);
        size_t frames = totalFloats / mChannelCount;
        outFloatSamples.resize(frames * 2);

        if (mChannelCount == 1) {
            for (size_t i = 0; i < frames; ++i) {
                float val = floatPtr[i];
                outFloatSamples[i * 2] = val;
                outFloatSamples[i * 2 + 1] = val;
            }
        } else if (mChannelCount == 2) {
            std::memcpy(outFloatSamples.data(), floatPtr, frames * 2 * sizeof(float));
        } else {
            for (size_t i = 0; i < frames; ++i) {
                outFloatSamples[i * 2] = floatPtr[i * mChannelCount];
                outFloatSamples[i * 2 + 1] = floatPtr[i * mChannelCount + 1];
            }
        }
    } else if (mPcmEncoding == 21) {
        // 24-bit 封装 PCM (每个样本 3 字节小端序)
        size_t totalSamples = byteCount / 3;
        size_t frames = totalSamples / mChannelCount;
        outFloatSamples.resize(frames * 2);

        auto read24 = [](const uint8_t* p) -> float {
            int32_t val = (static_cast<int32_t>(p[0])) |
                          (static_cast<int32_t>(p[1]) << 8) |
                          (static_cast<int32_t>(static_cast<int8_t>(p[2])) << 16);
            return static_cast<float>(val) / 8388608.0f;
        };

        if (mChannelCount == 1) {
            for (size_t i = 0; i < frames; ++i) {
                float val = read24(pcmBytes + i * 3);
                outFloatSamples[i * 2] = val;
                outFloatSamples[i * 2 + 1] = val;
            }
        } else if (mChannelCount == 2) {
            for (size_t i = 0; i < frames; ++i) {
                outFloatSamples[i * 2] = read24(pcmBytes + i * 6);
                outFloatSamples[i * 2 + 1] = read24(pcmBytes + i * 6 + 3);
            }
        } else {
            for (size_t i = 0; i < frames; ++i) {
                outFloatSamples[i * 2] = read24(pcmBytes + (i * mChannelCount) * 3);
                outFloatSamples[i * 2 + 1] = read24(pcmBytes + (i * mChannelCount + 1) * 3);
            }
        }
    } else {
        // 默认 16-bit 整数 PCM
        size_t numSamples = byteCount / sizeof(int16_t);
        const int16_t* pcm16 = reinterpret_cast<const int16_t*>(pcmBytes);

        if (mChannelCount == 1) {
            outFloatSamples.resize(numSamples * 2);
            for (size_t i = 0; i < numSamples; ++i) {
                float f = static_cast<float>(pcm16[i]) / 32768.0f;
                outFloatSamples[i * 2] = f;
                outFloatSamples[i * 2 + 1] = f;
            }
        } else if (mChannelCount == 2) {
            outFloatSamples.resize(numSamples);
            for (size_t i = 0; i < numSamples; ++i) {
                outFloatSamples[i] = static_cast<float>(pcm16[i]) / 32768.0f;
            }
        } else {
            size_t frames = numSamples / mChannelCount;
            outFloatSamples.resize(frames * 2);
            for (size_t i = 0; i < frames; ++i) {
                outFloatSamples[i * 2] = static_cast<float>(pcm16[i * mChannelCount]) / 32768.0f;
                outFloatSamples[i * 2 + 1] = static_cast<float>(pcm16[i * mChannelCount + 1]) / 32768.0f;
            }
        }
    }
}

bool AudioDecoder::seekTo(int64_t timeUs) {
    if (!mExtractor || !mCodec) return false;

    timeUs = std::max<int64_t>(0, std::min<int64_t>(timeUs, mDurationUs));
    media_status_t status = AMediaExtractor_seekTo(mExtractor, timeUs, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
    if (status != AMEDIA_OK) {
        LOGE("AMediaExtractor 定位失败: %d", status);
        return false;
    }

    AMediaCodec_flush(mCodec);
    mSawInputEOS = false;
    mSawOutputEOS = false;
    mCurrentPositionUs = timeUs;
    return true;
}

} // namespace player
