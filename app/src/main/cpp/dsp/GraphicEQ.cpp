#include "GraphicEQ.h"
#include <algorithm>

namespace dsp {

GraphicEQ::GraphicEQ(GraphicEQMode mode) : mode_(mode) {
    initializeBands();
}

void GraphicEQ::setMode(GraphicEQMode mode) {
    if (mode_ != mode) {
        mode_ = mode;
        initializeBands();
    }
}

void GraphicEQ::setSampleRate(float sampleRate) {
    sampleRate_ = sampleRate;
    initializeBands();
}

void GraphicEQ::initializeBands() {
    switch (mode_) {
        case GraphicEQMode::BAND_10:
            // 1 Octave
            frequencies_ = { 31.25f, 62.5f, 125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f };
            q_ = 1.414f;
            break;
        case GraphicEQMode::BAND_15:
            // 2/3 Octave
            frequencies_ = { 25.0f, 40.0f, 63.0f, 100.0f, 160.0f, 250.0f, 400.0f, 630.0f, 1000.0f, 1600.0f, 2500.0f, 4000.0f, 6300.0f, 10000.0f, 16000.0f };
            q_ = 2.14f;
            break;
        case GraphicEQMode::BAND_20:
            // 1/2 Octave
            frequencies_ = { 31.5f, 45.0f, 63.0f, 90.0f, 125.0f, 180.0f, 250.0f, 355.0f, 500.0f, 710.0f, 1000.0f, 1400.0f, 2000.0f, 2800.0f, 4000.0f, 5600.0f, 8000.0f, 11200.0f, 16000.0f, 20000.0f };
            q_ = 2.87f;
            break;
        case GraphicEQMode::BAND_31:
            // 1/3 Octave
            frequencies_ = { 20.0f, 25.0f, 31.5f, 40.0f, 50.0f, 63.0f, 80.0f, 100.0f, 125.0f, 160.0f, 200.0f, 250.0f, 315.0f, 400.0f, 500.0f, 630.0f, 800.0f, 1000.0f, 1250.0f, 1600.0f, 2000.0f, 2500.0f, 3150.0f, 4000.0f, 5000.0f, 6300.0f, 8000.0f, 10000.0f, 12500.0f, 16000.0f, 20000.0f };
            q_ = 4.318f;
            break;
    }

    size_t count = frequencies_.size();
    gains_.assign(count, 0.0f);
    filtersLeft_.resize(count);
    filtersRight_.resize(count);

    for (size_t i = 0; i < count; ++i) {
        filtersLeft_[i].configure(FilterType::PEAKING, frequencies_[i], 0.0f, q_, sampleRate_);
        filtersRight_[i].configure(FilterType::PEAKING, frequencies_[i], 0.0f, q_, sampleRate_);
    }
}

void GraphicEQ::setBandGain(int bandIndex, float gainDb) {
    if (bandIndex >= 0 && bandIndex < static_cast<int>(gains_.size())) {
        gains_[bandIndex] = gainDb;
        filtersLeft_[bandIndex].configure(FilterType::PEAKING, frequencies_[bandIndex], gainDb, q_, sampleRate_);
        filtersRight_[bandIndex].configure(FilterType::PEAKING, frequencies_[bandIndex], gainDb, q_, sampleRate_);
    }
}

float GraphicEQ::getBandGain(int bandIndex) const {
    if (bandIndex >= 0 && bandIndex < static_cast<int>(gains_.size())) {
        return gains_[bandIndex];
    }
    return 0.0f;
}

float GraphicEQ::getBandFrequency(int bandIndex) const {
    if (bandIndex >= 0 && bandIndex < static_cast<int>(frequencies_.size())) {
        return frequencies_[bandIndex];
    }
    return 0.0f;
}

void GraphicEQ::processStereo(float* buffer, int numFrames) {
    size_t numBands = frequencies_.size();
    for (int f = 0; f < numFrames; ++f) {
        float sampleL = buffer[f * 2];
        float sampleR = buffer[f * 2 + 1];

        for (size_t b = 0; b < numBands; ++b) {
            // 如果增益极小接近 0，可直接过滤器或计算
            if (std::abs(gains_[b]) > 0.01f) {
                sampleL = filtersLeft_[b].processSample(sampleL);
                sampleR = filtersRight_[b].processSample(sampleR);
            }
        }

        buffer[f * 2] = sampleL;
        buffer[f * 2 + 1] = sampleR;
    }
}

float GraphicEQ::getTotalMagnitudeResponse(float frequency) const {
    float totalDb = 0.0f;
    for (size_t b = 0; b < frequencies_.size(); ++b) {
        totalDb += filtersLeft_[b].getMagnitudeResponse(frequency, sampleRate_);
    }
    return totalDb;
}

void GraphicEQ::reset() {
    for (auto& f : filtersLeft_) f.reset();
    for (auto& f : filtersRight_) f.reset();
}

} // namespace dsp
