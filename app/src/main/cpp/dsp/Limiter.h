#pragma once

#include <cmath>
#include <algorithm>

namespace dsp {

class Limiter {
public:
    explicit Limiter(float sampleRate = 44100.0f);
    ~Limiter() = default;

    void setSampleRate(float sampleRate);
    void setThresholdDb(float thresholdDb);
    void setAttackTimeMs(float attackMs);
    void setReleaseTimeMs(float releaseMs);
    void setEnabled(bool enabled) { enabled_ = enabled; }
    bool isEnabled() const { return enabled_; }

    void processStereo(float* buffer, int numFrames);
    void reset();

private:
    void updateCoefficients();
    float applySoftSaturation(float x) const;

    bool enabled_ = true;
    float sampleRate_ = 44100.0f;
    float thresholdDb_ = -1.5f;          // 工业标准 -1.5dBFS 安全真峰值防削波余量
    float thresholdLinear_ = 0.841395f;  // 10^(-1.5 / 20)
    float attackMs_ = 1.2f;              // 1.2ms 柔和温润起控，避免单周期调制失真
    float releaseMs_ = 65.0f;            // 65ms 音乐性自适应平滑释放常数
    float kneeDb_ = 2.0f;                // 2.0dB 软拐点宽度 (Soft Knee)

    float attackCoeff_ = 0.0f;
    float releaseCoeff_ = 0.0f;

    float envelope_ = 0.0f;
    float gainReduction_ = 1.0f;
};

} // namespace dsp
