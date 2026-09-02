#include "FFTAnalyzer.h"
#include <cmath>
#include <algorithm>

namespace dsp {

constexpr float PI = 3.14159265358979323846f;

FFTAnalyzer::FFTAnalyzer(int fftSize) {
    setFFTSize(fftSize);
}

void FFTAnalyzer::setFFTSize(int fftSize) {
    // 强制保证为 2 的幂次
    int powerOf2 = 1;
    while (powerOf2 < fftSize) powerOf2 <<= 1;
    fftSize_ = powerOf2;

    inputRingBuffer_.assign(fftSize_, 0.0f);
    writeIndex_ = 0;
    magnitudes_.assign(fftSize_ / 2, 0.0f);
    smoothBands_.assign(64, 0.0f);

    // 预计算汉宁窗 (Hanning Window)
    window_.resize(fftSize_);
    for (int i = 0; i < fftSize_; ++i) {
        window_[i] = 0.5f * (1.0f - std::cos(2.0f * PI * i / (fftSize_ - 1)));
    }
}

void FFTAnalyzer::pushSamples(const float* buffer, int numFrames) {
    float localMaxL = 0.0f;
    float localMaxR = 0.0f;

    for (int i = 0; i < numFrames; ++i) {
        float l = buffer[i * 2];
        float r = buffer[i * 2 + 1];
        float mono = (l + r) * 0.5f;

        inputRingBuffer_[writeIndex_] = mono;
        writeIndex_ = (writeIndex_ + 1) % fftSize_;

        if (std::abs(l) > localMaxL) localMaxL = std::abs(l);
        if (std::abs(r) > localMaxR) localMaxR = std::abs(r);
    }

    // 平滑更新左右声道 Peak
    peakLeft_ = peakLeft_ * 0.8f + localMaxL * 0.2f;
    peakRight_ = peakRight_ * 0.8f + localMaxR * 0.2f;
}

void FFTAnalyzer::computeFFT(std::vector<std::complex<float>>& a) {
    int n = static_cast<int>(a.size());
    for (int i = 1, j = 0; i < n; ++i) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1) {
            j ^= bit;
        }
        j ^= bit;
        if (i < j) std::swap(a[i], a[j]);
    }

    for (int len = 2; len <= n; len <<= 1) {
        float ang = 2.0f * PI / len * -1.0f;
        std::complex<float> wlen(std::cos(ang), std::sin(ang));
        for (int i = 0; i < n; i += len) {
            std::complex<float> w(1.0f, 0.0f);
            for (int j = 0; j < len / 2; ++j) {
                std::complex<float> u = a[i + j];
                std::complex<float> v = a[i + j + len / 2] * w;
                a[i + j] = u + v;
                a[i + j + len / 2] = u - v;
                w *= wlen;
            }
        }
    }
}

void FFTAnalyzer::calculateSpectrum(float* outBands, int numBands) {
    if (numBands <= 0) return;
    if (static_cast<int>(smoothBands_.size()) != numBands) {
        smoothBands_.assign(numBands, 0.0f);
    }

    std::vector<std::complex<float>> fftBuffer(fftSize_);
    int readIdx = writeIndex_;
    for (int i = 0; i < fftSize_; ++i) {
        float sample = inputRingBuffer_[(readIdx + i) % fftSize_];
        fftBuffer[i] = std::complex<float>(sample * window_[i], 0.0f);
    }

    computeFFT(fftBuffer);

    int halfSize = fftSize_ / 2;
    for (int i = 0; i < halfSize; ++i) {
        magnitudes_[i] = std::abs(fftBuffer[i]) / (fftSize_ * 0.5f);
    }

    // 按对数频率分布映射到 numBands 条频段柱
    for (int b = 0; b < numBands; ++b) {
        float lowFraction = std::pow(static_cast<float>(b) / numBands, 2.0f);
        float highFraction = std::pow(static_cast<float>(b + 1) / numBands, 2.0f);

        int lowBin = std::clamp(static_cast<int>(lowFraction * halfSize), 0, halfSize - 1);
        int highBin = std::clamp(static_cast<int>(highFraction * halfSize), lowBin + 1, halfSize);

        float sum = 0.0f;
        for (int bin = lowBin; bin < highBin; ++bin) {
            sum += magnitudes_[bin];
        }
        float avg = sum / (highBin - lowBin);

        // 转换为对数 dB 并归一化 0.0 ~ 1.0 (动态范围约 -60dB ~ 0dB)
        float db = 20.0f * std::log10(std::max(1e-4f, avg));
        float normalized = std::clamp((db + 60.0f) / 60.0f, 0.0f, 1.0f);

        // 动态回弹平滑
        if (normalized > smoothBands_[b]) {
            smoothBands_[b] = smoothBands_[b] * 0.3f + normalized * 0.7f; // 快速上升
        } else {
            smoothBands_[b] = smoothBands_[b] * 0.85f + normalized * 0.15f; // 缓慢下降
        }

        outBands[b] = smoothBands_[b];
    }
}

void FFTAnalyzer::getPeakLevels(float& outLeftDb, float& outRightDb) {
    outLeftDb = 20.0f * std::log10(std::max(1e-4f, peakLeft_));
    outRightDb = 20.0f * std::log10(std::max(1e-4f, peakRight_));
}

} // namespace dsp
