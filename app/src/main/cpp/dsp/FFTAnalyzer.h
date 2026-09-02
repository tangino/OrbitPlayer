#pragma once

#include <vector>
#include <complex>

namespace dsp {

class FFTAnalyzer {
public:
    explicit FFTAnalyzer(int fftSize = 1024);
    ~FFTAnalyzer() = default;

    void setFFTSize(int fftSize);
    int getFFTSize() const { return fftSize_; }

    // 推入音频样本并计算频段能量（归一化为 0.0f ~ 1.0f）
    void pushSamples(const float* buffer, int numFrames);
    void calculateSpectrum(float* outBands, int numBands);

    // 峰值电平检测 (Peak Meter L & R in dB)
    void getPeakLevels(float& outLeftDb, float& outRightDb);

private:
    void computeFFT(std::vector<std::complex<float>>& data);

    int fftSize_ = 1024;
    std::vector<float> inputRingBuffer_;
    int writeIndex_ = 0;

    std::vector<float> window_;
    std::vector<float> magnitudes_;
    std::vector<float> smoothBands_;

    float peakLeft_ = 0.0f;
    float peakRight_ = 0.0f;
};

} // namespace dsp
