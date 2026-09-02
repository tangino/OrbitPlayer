#include "TrebleBoost.h"

namespace dsp {

TrebleBoost::TrebleBoost(float sampleRate) : sampleRate_(sampleRate) {
    frequency_ = 8000.0f; // 黄金超高频空气感截止点 (避开 3kHz-6kHz 刺耳齿音区)
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
    frequency_ = std::clamp(freq, 6000.0f, 14000.0f);
    updateFilter();
}

void TrebleBoost::updateFilter() {
    // 柔和顺滑的超高频延伸 (0 ~ +8dB)，Q 值为 0.65 (更平滑开阔的高频倾斜，杜绝尖锐毛刺)
    float gainDb = strength_ * 8.0f;
    highShelfL_.configure(FilterType::HIGH_SHELF, frequency_, gainDb, 0.65f, sampleRate_);
    highShelfR_.configure(FilterType::HIGH_SHELF, frequency_, gainDb, 0.65f, sampleRate_);
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
