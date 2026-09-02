#pragma once

#include <cmath>
#include <vector>

namespace dsp {

enum class FilterType {
    PEAKING = 0,
    LOW_SHELF = 1,
    HIGH_SHELF = 2,
    LOW_PASS = 3,
    HIGH_PASS = 4,
    BAND_PASS = 5,
    NOTCH = 6
};

class BiquadFilter {
public:
    BiquadFilter();
    ~BiquadFilter() = default;

    void configure(FilterType type, float frequency, float gainDb, float q, float sampleRate);
    
    // Direct Form II Transposed 单样本处理
    inline float processSample(float inSample) {
        float outSample = b0_ * inSample + s1_;
        s1_ = b1_ * inSample - a1_ * outSample + s2_;
        s2_ = b2_ * inSample - a2_ * outSample;
        return outSample;
    }

    void processBlock(const float* in, float* out, int numSamples);
    void reset();

    // 计算特定频率下的幅频响应（单位：dB）
    float getMagnitudeResponse(float frequency, float sampleRate) const;

    FilterType getType() const { return type_; }
    float getFrequency() const { return frequency_; }
    float getGainDb() const { return gainDb_; }
    float getQ() const { return q_; }

private:
    void recalculateCoefficients();

    FilterType type_ = FilterType::PEAKING;
    float frequency_ = 1000.0f;
    float gainDb_ = 0.0f;
    float q_ = 1.0f;
    float sampleRate_ = 44100.0f;

    // 归一化双二阶滤波器系数
    float b0_ = 1.0f;
    float b1_ = 0.0f;
    float b2_ = 0.0f;
    float a1_ = 0.0f;
    float a2_ = 0.0f;

    // 状态缓存变量（Direct Form II Transposed）
    float s1_ = 0.0f;
    float s2_ = 0.0f;
};

} // namespace dsp
