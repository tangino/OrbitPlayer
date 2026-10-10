#include "BassBoost.h"

namespace dsp {

BassBoost::BassBoost(float sampleRate) : sampleRate_(sampleRate) {
    frequency_ = 90.0f; // Poweramp 原版低音中心频点 90Hz
    updateFilter();
}

void BassBoost::setSampleRate(float sampleRate) {
    sampleRate_ = sampleRate > 0.0f ? sampleRate : 44100.0f;
    updateFilter();
}

void BassBoost::setStrength(float strength) {
    strength_ = std::clamp(strength, 0.0f, 1.0f);
    updateFilter();
}

void BassBoost::setCenterFrequency(float freq) {
    frequency_ = std::clamp(freq, 20.0f, 400.0f);
    updateFilter();
}

void BassBoost::updateFilter() {
    // Poweramp 原版规格：Low Shelf，Q 值为 0.8f，最大增益 +22.5dB
    float gainDb = strength_ * 22.5f;
    lowShelfL_.configure(FilterType::LOW_SHELF, frequency_, gainDb, 0.80f, sampleRate_);
    lowShelfR_.configure(FilterType::LOW_SHELF, frequency_, gainDb, 0.80f, sampleRate_);
}

void BassBoost::processStereo(float* buffer, int numFrames) {
    if (!enabled_ || strength_ <= 0.001f) return;

    for (int f = 0; f < numFrames; ++f) {
        float sampleL = buffer[f * 2];
        float sampleR = buffer[f * 2 + 1];

        buffer[f * 2] = lowShelfL_.processSample(sampleL);
        buffer[f * 2 + 1] = lowShelfR_.processSample(sampleR);
    }
}

void BassBoost::reset() {
    lowShelfL_.reset();
    lowShelfR_.reset();
}

} // namespace dsp
