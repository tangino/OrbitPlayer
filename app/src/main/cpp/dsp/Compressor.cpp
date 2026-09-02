#include "Compressor.h"

namespace dsp {

Compressor::Compressor(float sampleRate) : sampleRate_(sampleRate) {
    setMakeupGainDb(0.0f);
    updateCoefficients();
}

void Compressor::setSampleRate(float sampleRate) {
    sampleRate_ = sampleRate > 0.0f ? sampleRate : 44100.0f;
    updateCoefficients();
}

void Compressor::setThresholdDb(float thresholdDb) {
    thresholdDb_ = thresholdDb;
}

void Compressor::setRatio(float ratio) {
    ratio_ = std::max(1.0f, ratio);
}

void Compressor::setAttackTimeMs(float attackMs) {
    attackMs_ = std::max(0.1f, attackMs);
    updateCoefficients();
}

void Compressor::setReleaseTimeMs(float releaseMs) {
    releaseMs_ = std::max(1.0f, releaseMs);
    updateCoefficients();
}

void Compressor::setMakeupGainDb(float makeupGainDb) {
    makeupGainDb_ = makeupGainDb;
    makeupGainLinear_ = std::pow(10.0f, makeupGainDb_ / 20.0f);
}

void Compressor::setKneeWidthDb(float kneeWidthDb) {
    kneeWidthDb_ = std::max(0.0f, kneeWidthDb);
}

void Compressor::updateCoefficients() {
    attackCoeff_ = std::exp(-1.0f / (attackMs_ * 0.001f * sampleRate_));
    releaseCoeff_ = std::exp(-1.0f / (releaseMs_ * 0.001f * sampleRate_));
}

float Compressor::computeGainDb(float inputDb) const {
    if (kneeWidthDb_ > 0.0f) {
        float lower = thresholdDb_ - kneeWidthDb_ * 0.5f;
        float upper = thresholdDb_ + kneeWidthDb_ * 0.5f;

        if (inputDb < lower) {
            return 0.0f;
        } else if (inputDb > upper) {
            return (thresholdDb_ + (inputDb - thresholdDb_) / ratio_) - inputDb;
        } else {
            // 软膝曲线 (Soft Knee Quadratic Interpolation)
            float diff = inputDb - lower;
            float kneeFraction = (diff * diff) / (2.0f * kneeWidthDb_);
            float targetDb = inputDb + (1.0f / ratio_ - 1.0f) * kneeFraction;
            return targetDb - inputDb;
        }
    } else {
        // 硬膝 (Hard Knee)
        if (inputDb > thresholdDb_) {
            return (thresholdDb_ + (inputDb - thresholdDb_) / ratio_) - inputDb;
        }
        return 0.0f;
    }
}

void Compressor::processStereo(float* buffer, int numFrames) {
    if (!enabled_) return;

    for (int f = 0; f < numFrames; ++f) {
        float sampleL = buffer[f * 2];
        float sampleR = buffer[f * 2 + 1];

        // 探测立体声峰值
        float peak = std::max(std::abs(sampleL), std::abs(sampleR));
        float inputDb = 20.0f * std::log10(std::max(1e-5f, peak));

        // 包络检波器 (dB 域)
        if (inputDb > envelopeDb_) {
            envelopeDb_ = attackCoeff_ * envelopeDb_ + (1.0f - attackCoeff_) * inputDb;
        } else {
            envelopeDb_ = releaseCoeff_ * envelopeDb_ + (1.0f - releaseCoeff_) * inputDb;
        }

        // 计算压缩衰减量 (Gain Reduction)
        gainReductionDb_ = computeGainDb(envelopeDb_);
        float gainLinear = std::pow(10.0f, gainReductionDb_ / 20.0f) * makeupGainLinear_;

        buffer[f * 2] = sampleL * gainLinear;
        buffer[f * 2 + 1] = sampleR * gainLinear;
    }
}

void Compressor::reset() {
    envelopeDb_ = -96.0f;
    gainReductionDb_ = 0.0f;
}

} // namespace dsp
