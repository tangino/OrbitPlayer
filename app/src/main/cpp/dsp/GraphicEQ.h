#pragma once

#include "BiquadFilter.h"
#include <vector>

namespace dsp {

enum class GraphicEQMode {
    BAND_10 = 10,
    BAND_15 = 15,
    BAND_20 = 20,
    BAND_31 = 31
};

class GraphicEQ {
public:
    explicit GraphicEQ(GraphicEQMode mode = GraphicEQMode::BAND_10);
    ~GraphicEQ() = default;

    void setMode(GraphicEQMode mode);
    GraphicEQMode getMode() const { return mode_; }

    void setSampleRate(float sampleRate);
    void setBandGain(int bandIndex, float gainDb);
    float getBandGain(int bandIndex) const;
    float getBandFrequency(int bandIndex) const;
    int getBandCount() const { return static_cast<int>(frequencies_.size()); }

    const std::vector<float>& getFrequencies() const { return frequencies_; }
    const std::vector<float>& getGains() const { return gains_; }

    void processStereo(float* buffer, int numFrames);
    float getTotalMagnitudeResponse(float frequency) const;
    void reset();

private:
    void initializeBands();

    GraphicEQMode mode_ = GraphicEQMode::BAND_10;
    float sampleRate_ = 44100.0f;
    float q_ = 1.414f;

    std::vector<float> frequencies_;
    std::vector<float> gains_;
    
    std::vector<BiquadFilter> filtersLeft_;
    std::vector<BiquadFilter> filtersRight_;
};

} // namespace dsp
