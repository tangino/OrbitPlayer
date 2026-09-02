#include "Preamp.h"

namespace dsp {

Preamp::Preamp() {
    setGainDb(0.0f);
}

void Preamp::setGainDb(float gainDb) {
    gainDb_ = gainDb;
    linearGain_ = std::pow(10.0f, gainDb_ / 20.0f);
}

void Preamp::processStereo(float* buffer, int numFrames) {
    if (std::abs(linearGain_ - 1.0f) < 0.0001f) {
        // 增益为 0dB 时只做 Peak 监测
        float currentMax = 0.0f;
        for (int i = 0; i < numFrames * 2; ++i) {
            float absVal = std::abs(buffer[i]);
            if (absVal > currentMax) currentMax = absVal;
        }
        if (currentMax > 1.0f) clippingDetected_.store(true, std::memory_order_relaxed);
        float oldPeak = maxPeak_.load(std::memory_order_relaxed);
        if (currentMax > oldPeak) maxPeak_.store(currentMax, std::memory_order_relaxed);
        return;
    }

    float currentMax = 0.0f;
    for (int i = 0; i < numFrames * 2; ++i) {
        buffer[i] *= linearGain_;
        float absVal = std::abs(buffer[i]);
        if (absVal > currentMax) {
            currentMax = absVal;
        }
    }

    if (currentMax > 1.0f) {
        clippingDetected_.store(true, std::memory_order_relaxed);
    }

    float oldPeak = maxPeak_.load(std::memory_order_relaxed);
    if (currentMax > oldPeak) {
        maxPeak_.store(currentMax, std::memory_order_relaxed);
    }
}

bool Preamp::isClippingAndReset() {
    return clippingDetected_.exchange(false, std::memory_order_relaxed);
}

float Preamp::getMaxPeakAndReset() {
    return maxPeak_.exchange(0.0f, std::memory_order_relaxed);
}

void Preamp::reset() {
    clippingDetected_.store(false, std::memory_order_relaxed);
    maxPeak_.store(0.0f, std::memory_order_relaxed);
}

} // namespace dsp
