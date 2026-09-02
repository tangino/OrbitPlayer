#pragma once

#include "BiquadFilter.h"
#include <vector>

namespace dsp {

struct BandConfig {
    int id = 0;
    bool enabled = true;
    FilterType type = FilterType::PEAKING;
    float frequency = 1000.0f;
    float gainDb = 0.0f;
    float q = 1.0f;
};

class ParametricEQ {
public:
    ParametricEQ();
    ~ParametricEQ() = default;

    void setSampleRate(float sampleRate);
    void setBands(const std::vector<BandConfig>& configs);
    void setBandGain(int bandIndex, float gainDb);
    void setBandFrequency(int bandIndex, float frequency);
    void setBandQ(int bandIndex, float q);
    void setBandEnabled(int bandIndex, bool enabled);

    // 立体声交织音频处理 (Interleaved Stereo L, R, L, R...)
    void processStereo(float* buffer, int numFrames);

    // 计算指定频率下所有已启用频段的级联总增益响应 (dB)
    float getTotalMagnitudeResponse(float frequency) const;

    int getBandCount() const { return static_cast<int>(configs_.size()); }
    const std::vector<BandConfig>& getConfigs() const { return configs_; }
    void reset();

private:
    float sampleRate_ = 44100.0f;
    std::vector<BandConfig> configs_;
    
    // 双声道各自的滤波器实例
    std::vector<BiquadFilter> filtersLeft_;
    std::vector<BiquadFilter> filtersRight_;
};

} // namespace dsp
