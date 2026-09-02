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
    float thresholdDb_ = -2.5f;          // 实用防削波门限 -2.5dB
    float thresholdLinear_ = 0.74989f;
    float attackMs_ = 0.5f;              // 快速平滑瞬态响应
    float releaseMs_ = 80.0f;            // 柔和自然释放

    float attackCoeff_ = 0.0f;
    float releaseCoeff_ = 0.0f;

    float envelope_ = 0.0f;
    float gainReduction_ = 1.0f;
};

} // namespace dsp
