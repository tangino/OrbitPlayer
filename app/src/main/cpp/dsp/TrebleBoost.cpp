#include "TrebleBoost.h"

namespace dsp {

TrebleBoost::TrebleBoost(float sampleRate) : sampleRate_(sampleRate) {
    frequency_ = 10000.0f; // Poweramp 原版高音中心频点 10kHz
    updateFilter();
}

void TrebleBoost::setSampleRate(float sampleRate) {
    sampleRate_ = sampleRate > 0.0f ? sampleRate : 44100.0f;
    updateFilter();
}

void TrebleBoost::setStrength(float strength) {
    strength_ = std::clamp(strength, 0.0f, 1.0f);
    updateFilter();
}

void TrebleBoost::setCenterFrequency(float freq) {
    frequency_ = std::clamp(freq, 4000.0f, 20000.0f);
    updateFilter();
}

void TrebleBoost::updateFilter() {
    // Poweramp 原版规格：High Shelf，Q 值为 0.8f，最大增益 +15.0dB
    float gainDb = strength_ * 15.0f;
    highShelfL_.configure(FilterType::HIGH_SHELF, frequency_, gainDb, 0.80f, sampleRate_);
    highShelfR_.configure(FilterType::HIGH_SHELF, frequency_, gainDb, 0.80f, sampleRate_);
}

void TrebleBoost::processStereo(float* buffer, int numFrames) {
    if (!enabled_ || strength_ <= 0.001f) return;

    for (int f = 0; f < numFrames; ++f) {
        float sampleL = buffer[f * 2];
        float sampleR = buffer[f * 2 + 1];

        buffer[f * 2] = highShelfL_.processSample(sampleL);
        buffer[f * 2 + 1] = highShelfR_.processSample(sampleR);
    }
}

void TrebleBoost::reset() {
    highShelfL_.reset();
    highShelfR_.reset();
}

} // namespace dsp
