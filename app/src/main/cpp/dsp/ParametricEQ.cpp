#include "ParametricEQ.h"

namespace dsp {

ParametricEQ::ParametricEQ() {
    // 默认提供 5 段参数均衡初始化
    std::vector<BandConfig> defaultBands = {
        {0, true, FilterType::LOW_SHELF, 80.0f, 0.0f, 0.7f},
        {1, true, FilterType::PEAKING, 250.0f, 0.0f, 1.0f},
        {2, true, FilterType::PEAKING, 1000.0f, 0.0f, 1.0f},
        {3, true, FilterType::PEAKING, 4000.0f, 0.0f, 1.0f},
        {4, true, FilterType::HIGH_SHELF, 12000.0f, 0.0f, 0.7f}
    };
    setBands(defaultBands);
}

void ParametricEQ::setSampleRate(float sampleRate) {
    sampleRate_ = sampleRate;
    setBands(configs_);
}

void ParametricEQ::setBands(const std::vector<BandConfig>& configs) {
    configs_ = configs;
    size_t count = configs_.size();
    filtersLeft_.resize(count);
    filtersRight_.resize(count);

    for (size_t i = 0; i < count; ++i) {
        const auto& cfg = configs_[i];
        filtersLeft_[i].configure(cfg.type, cfg.frequency, cfg.enabled ? cfg.gainDb : 0.0f, cfg.q, sampleRate_);
        filtersRight_[i].configure(cfg.type, cfg.frequency, cfg.enabled ? cfg.gainDb : 0.0f, cfg.q, sampleRate_);
    }
}

void ParametricEQ::setBandGain(int bandIndex, float gainDb) {
    if (bandIndex >= 0 && bandIndex < static_cast<int>(configs_.size())) {
        configs_[bandIndex].gainDb = gainDb;
        float actualGain = configs_[bandIndex].enabled ? gainDb : 0.0f;
        filtersLeft_[bandIndex].configure(configs_[bandIndex].type, configs_[bandIndex].frequency, actualGain, configs_[bandIndex].q, sampleRate_);
        filtersRight_[bandIndex].configure(configs_[bandIndex].type, configs_[bandIndex].frequency, actualGain, configs_[bandIndex].q, sampleRate_);
    }
}

void ParametricEQ::setBandFrequency(int bandIndex, float frequency) {
    if (bandIndex >= 0 && bandIndex < static_cast<int>(configs_.size())) {
        configs_[bandIndex].frequency = frequency;
        float actualGain = configs_[bandIndex].enabled ? configs_[bandIndex].gainDb : 0.0f;
        filtersLeft_[bandIndex].configure(configs_[bandIndex].type, frequency, actualGain, configs_[bandIndex].q, sampleRate_);
        filtersRight_[bandIndex].configure(configs_[bandIndex].type, frequency, actualGain, configs_[bandIndex].q, sampleRate_);
    }
}

void ParametricEQ::setBandQ(int bandIndex, float q) {
    if (bandIndex >= 0 && bandIndex < static_cast<int>(configs_.size())) {
        configs_[bandIndex].q = q;
        float actualGain = configs_[bandIndex].enabled ? configs_[bandIndex].gainDb : 0.0f;
        filtersLeft_[bandIndex].configure(configs_[bandIndex].type, configs_[bandIndex].frequency, actualGain, q, sampleRate_);
        filtersRight_[bandIndex].configure(configs_[bandIndex].type, configs_[bandIndex].frequency, actualGain, q, sampleRate_);
    }
}

void ParametricEQ::setBandEnabled(int bandIndex, bool enabled) {
    if (bandIndex >= 0 && bandIndex < static_cast<int>(configs_.size())) {
        configs_[bandIndex].enabled = enabled;
        float actualGain = enabled ? configs_[bandIndex].gainDb : 0.0f;
        filtersLeft_[bandIndex].configure(configs_[bandIndex].type, configs_[bandIndex].frequency, actualGain, configs_[bandIndex].q, sampleRate_);
        filtersRight_[bandIndex].configure(configs_[bandIndex].type, configs_[bandIndex].frequency, actualGain, configs_[bandIndex].q, sampleRate_);
    }
}

void ParametricEQ::processStereo(float* buffer, int numFrames) {
    size_t numBands = configs_.size();
    for (int f = 0; f < numFrames; ++f) {
        float sampleL = buffer[f * 2];
        float sampleR = buffer[f * 2 + 1];

        for (size_t b = 0; b < numBands; ++b) {
            if (configs_[b].enabled) {
                sampleL = filtersLeft_[b].processSample(sampleL);
                sampleR = filtersRight_[b].processSample(sampleR);
            }
        }

        buffer[f * 2] = sampleL;
        buffer[f * 2 + 1] = sampleR;
    }
}

float ParametricEQ::getTotalMagnitudeResponse(float frequency) const {
    float totalDb = 0.0f;
    for (size_t b = 0; b < configs_.size(); ++b) {
        if (configs_[b].enabled) {
            totalDb += filtersLeft_[b].getMagnitudeResponse(frequency, sampleRate_);
        }
    }
    return totalDb;
}

void ParametricEQ::reset() {
    for (auto& f : filtersLeft_) f.reset();
    for (auto& f : filtersRight_) f.reset();
}

} // namespace dsp
