#pragma once

#include <cmath>
#include <algorithm>

namespace dsp {

class Compressor {
public:
    explicit Compressor(float sampleRate = 44100.0f);
    ~Compressor() = default;

    void setSampleRate(float sampleRate);
    void setThresholdDb(float thresholdDb);
    void setRatio(float ratio);
    void setAttackTimeMs(float attackMs);
    void setReleaseTimeMs(float releaseMs);
    void setMakeupGainDb(float makeupGainDb);
    void setKneeWidthDb(float kneeWidthDb);
    void setEnabled(bool enabled) { enabled_ = enabled; }

    bool isEnabled() const { return enabled_; }
    float getThresholdDb() const { return thresholdDb_; }
    float getRatio() const { return ratio_; }
    float getGainReductionDb() const { return gainReductionDb_; }

    void processStereo(float* buffer, int numFrames);
    void reset();

private:
    void updateCoefficients();
    float computeGainDb(float inputDb) const;

    bool enabled_ = false;
    float sampleRate_ = 44100.0f;
    float thresholdDb_ = -18.0f;
    float ratio_ = 4.0f;
    float attackMs_ = 10.0f;
    float releaseMs_ = 100.0f;
    float makeupGainDb_ = 0.0f;
    float makeupGainLinear_ = 1.0f;
    float kneeWidthDb_ = 4.0f;

    float attackCoeff_ = 0.0f;
    float releaseCoeff_ = 0.0f;

    float envelopeDb_ = -96.0f;
    float gainReductionDb_ = 0.0f;
};

} // namespace dsp
