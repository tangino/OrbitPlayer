#pragma once

#include <cmath>
#include <algorithm>

namespace dsp {

class Limiter {
public:
    explicit Limiter(float sampleRate = 44100.0f);
    ~Limiter() = default;

    void setSampleRate(float sampleRate);
    void setThresholdDb(float thresholdDb);
    void setAttackTimeMs(float attackMs);
    void setReleaseTimeMs(float releaseMs);
    void setEnabled(bool enabled) { enabled_ = enabled; }
    bool isEnabled() const { return enabled_; }

    void processStereo(float* buffer, int numFrames);
    void reset();

private:
    void updateCoefficients();

    bool enabled_ = true;
    float sampleRate_ = 44100.0f;
    float thresholdDb_ = -1.0f;          // Poweramp 规格 -1.0dBFS 砖墙安全裕量
    float thresholdLinear_ = 0.89125f;
    float attackMs_ = 0.001f;            // 1 微秒级超高速瞬态峰值拦截
    float releaseMs_ = 75.0f;            // Poweramp 原版标准 75ms 释放时间常数

    float attackCoeff_ = 0.0f;
    float releaseCoeff_ = 0.0f;

    float envelope_ = 0.0f;
    float gainReduction_ = 1.0f;
};

} // namespace dsp
