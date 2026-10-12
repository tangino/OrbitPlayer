#pragma once

#include <vector>
#include <atomic>
#include <cstddef>
#include <algorithm>
#include <cstring>

namespace player {

/**
 * 无锁单生产者单消费者 (SPSC) 浮点音频环形缓冲区
 * 专门用于解码线程 (写入者) 与音频渲染回调线程 (读取者) 之间的高并发安全传输
 */
class AudioRingBuffer {
public:
    explicit AudioRingBuffer(size_t capacityFrames, size_t channels = 2)
        : mChannels(channels),
          mCapacityFrames(capacityFrames),
          mTotalCapacitySamples(capacityFrames * channels),
          mBuffer(mTotalCapacitySamples + 1), // 多分配一个位置用于区分空与满
          mWriteHead(0),
          mReadHead(0) {}

    ~AudioRingBuffer() = default;

    /**
     * 写入浮点样本（由解码线程调用）
     * @param data 来源交织浮点 PCM 样本
     * @param frames 写入帧数
     * @return 实际写入的帧数
     */
    size_t write(const float* data, size_t frames) {
        if (!data || frames == 0) return 0;

        size_t samplesToWrite = frames * mChannels;
        size_t writePos = mWriteHead.load(std::memory_order_relaxed);
        size_t readPos = mReadHead.load(std::memory_order_acquire);

        size_t availableSamples = getAvailableWriteSamples(writePos, readPos);
        size_t actualSamples = std::min(samplesToWrite, availableSamples);
        if (actualSamples == 0) return 0;

        size_t bufferSize = mBuffer.size();
        size_t firstChunk = std::min(actualSamples, bufferSize - writePos);
        std::memcpy(&mBuffer[writePos], data, firstChunk * sizeof(float));

        if (actualSamples > firstChunk) {
            size_t secondChunk = actualSamples - firstChunk;
            std::memcpy(&mBuffer[0], data + firstChunk, secondChunk * sizeof(float));
        }

        size_t newWritePos = (writePos + actualSamples) % bufferSize;
        mWriteHead.store(newWritePos, std::memory_order_release);

        return actualSamples / mChannels;
    }

    /**
     * 读取浮点样本（由 AAudio 数据回调线程调用）
     * @param outData 输出缓冲区
     * @param frames 期望读取的帧数
     * @return 实际读取的帧数
     */
    size_t read(float* outData, size_t frames) {
        if (!outData || frames == 0) return 0;

        size_t samplesToRead = frames * mChannels;
        size_t writePos = mWriteHead.load(std::memory_order_acquire);
        size_t readPos = mReadHead.load(std::memory_order_relaxed);

        size_t availableSamples = getAvailableReadSamples(writePos, readPos);
        size_t actualSamples = std::min(samplesToRead, availableSamples);
        if (actualSamples == 0) return 0;

        size_t bufferSize = mBuffer.size();
        size_t firstChunk = std::min(actualSamples, bufferSize - readPos);
        std::memcpy(outData, &mBuffer[readPos], firstChunk * sizeof(float));

        if (actualSamples > firstChunk) {
            size_t secondChunk = actualSamples - firstChunk;
            std::memcpy(outData + firstChunk, &mBuffer[0], secondChunk * sizeof(float));
        }

        size_t newReadPos = (readPos + actualSamples) % bufferSize;
        mReadHead.store(newReadPos, std::memory_order_release);

        return actualSamples / mChannels;
    }

    /**
     * 清空缓冲区（例如定位 Seek 或切歌停止时）
     */
    void reset() {
        mReadHead.store(0, std::memory_order_relaxed);
        mWriteHead.store(0, std::memory_order_relaxed);
    }

    /**
     * 获取当前缓冲的可读帧数
     */
    size_t getAvailableReadFrames() const {
        size_t writePos = mWriteHead.load(std::memory_order_relaxed);
        size_t readPos = mReadHead.load(std::memory_order_relaxed);
        return getAvailableReadSamples(writePos, readPos) / mChannels;
    }

    /**
     * 获取当前缓冲剩余可写帧数
     */
    size_t getAvailableWriteFrames() const {
        size_t writePos = mWriteHead.load(std::memory_order_relaxed);
        size_t readPos = mReadHead.load(std::memory_order_relaxed);
        return getAvailableWriteSamples(writePos, readPos) / mChannels;
    }

    size_t getChannels() const { return mChannels; }
    size_t getCapacityFrames() const { return mCapacityFrames; }

private:
    size_t getAvailableReadSamples(size_t writePos, size_t readPos) const {
        if (writePos >= readPos) {
            return writePos - readPos;
        }
        return mBuffer.size() - readPos + writePos;
    }

    size_t getAvailableWriteSamples(size_t writePos, size_t readPos) const {
        size_t bufferSize = mBuffer.size();
        if (writePos >= readPos) {
            return bufferSize - 1 - (writePos - readPos);
        }
        return readPos - writePos - 1;
    }

    const size_t mChannels;
    const size_t mCapacityFrames;
    const size_t mTotalCapacitySamples;
    std::vector<float> mBuffer;
    std::atomic<size_t> mWriteHead;
    std::atomic<size_t> mReadHead;
};

} // namespace player
