#include "BassBoost.h"

namespace dsp {

BassBoost::BassBoost(float sampleRate) : sampleRate_(sampleRate) {
    frequency_ = 110.0f; // 黄金低频浑厚温暖中枢 (60Hz ~ 160Hz 能量辐射，深沉浑厚且有弹性)
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
    frequency_ = std::clamp(freq, 60.0f, 220.0f);
    updateFilter();
}

void BassBoost::updateFilter() {
    // 丰满深沉的低音能量辐射：0 ~ +10dB 纯净低架增益，Q 值为 0.707 临界阻尼 (饱满浑厚不轰头)
    float gainDb = strength_ * 10.0f;
    lowShelfL_.configure(FilterType::LOW_SHELF, frequency_, gainDb, 0.707f, sampleRate_);
    lowShelfR_.configure(FilterType::LOW_SHELF, frequency_, gainDb, 0.707f, sampleRate_);
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
