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
    void setStrength(float strength); // 0.0f ~ 1.0f (对应 0dB ~ +12dB)
    void setCenterFrequency(float freq); // 默认 80Hz
    void setEnabled(bool enabled) { enabled_ = enabled; }

    bool isEnabled() const { return enabled_; }
    float getStrength() const { return strength_; }
    float getCenterFrequency() const { return frequency_; }

    void processStereo(float* buffer, int numFrames);
    void reset();

private:
    void updateFilter();

    bool enabled_ = false;
    float sampleRate_ = 44100.0f;
    float strength_ = 0.0f;       // 0.0 ~ 1.0
    float frequency_ = 80.0f;     // Hz

    BiquadFilter lowShelfL_;
    BiquadFilter lowShelfR_;
};

} // namespace dsp
