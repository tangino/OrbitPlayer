#pragma once

#include <cmath>
#include <atomic>
#include <algorithm>

namespace dsp {

class Preamp {
public:
    Preamp();
    ~Preamp() = default;

    void setGainDb(float gainDb);
    float getGainDb() const { return gainDb_; }
    float getLinearGain() const { return linearGain_; }

    void processStereo(float* buffer, int numFrames);

    // 检查并重置削波标志
    bool isClippingAndReset();
    float getMaxPeakAndReset();

    void reset();

private:
    float gainDb_ = 0.0f;
    float linearGain_ = 1.0f;
    
    std::atomic<bool> clippingDetected_{false};
    std::atomic<float> maxPeak_{0.0f};
};

} // namespace dsp
