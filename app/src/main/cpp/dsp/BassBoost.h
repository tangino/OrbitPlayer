#pragma once

#include "BiquadFilter.h"
#include <cmath>
#include <algorithm>

namespace dsp {

class BassBoost {
public:
    explicit BassBoost(float sampleRate = 44100.0f);
    ~BassBoost() = default;

    void setSampleRate(float sampleRate);
    void setStrength(float strength); // 0.0f ~ 1.0f (Poweramp 规格：映射至 0dB ~ +15.0dB)
    void setCenterFrequency(float freq); // Poweramp 规格默认 90Hz
    void setEnabled(bool enabled);

    bool isEnabled() const { return enabled_; }
    float getStrength() const { return strength_; }
    float getMaxGainDb() const { return kMaxGainDb; }        // 满强度对应的最大增益 (dB)
    float getCurrentGainDb() const { return gainDbCurrent_; } // 当前平滑后的实际增益 (dB)
    float getCenterFrequency() const { return frequency_; }

    void processStereo(float* buffer, int numFrames);
    void reset();

    // 当前生效的低频增益曲线 (dB)，用于绘制音调响应曲线
    float getMagnitudeResponse(float frequency) const;

private:
    static constexpr float kMaxGainDb = 15.0f;      // Poweramp 原版低音最大增益 +15.0dB
    static constexpr float kSmoothTimeSec = 0.02f;  // 20ms 参数平滑时间常数 (消除拉链噪声)
    static constexpr int kMaxRampSteps = 8;         // 单个音频块内最多 8 次系数更新

    void updateFilter();

    bool enabled_ = false;
    float sampleRate_ = 44100.0f;
    float strength_ = 0.0f;       // 0.0 ~ 1.0
    float frequency_ = 90.0f;     // Poweramp 原版标准低音中心频点 90Hz
    float gainDbTarget_ = 0.0f;   // 目标增益 (dB) = strength_ * kMaxGainDb
    float gainDbCurrent_ = 0.0f;  // 平滑跟踪中的实际增益 (dB)

    BiquadFilter lowShelfL_;
    BiquadFilter lowShelfR_;
};

} // namespace dsp
