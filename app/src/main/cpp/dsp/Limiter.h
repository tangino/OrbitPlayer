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
    // 允许的最大宽带增益衰减 (dB, 传负值)。音调 (Bass/Treble) 提升量越大，需要允许的衰减越深，
    // 否则限幅器无法把提升后的峰值压回门限内，只能靠软饱和硬吃，产生失真。
    void setMaxReductionDb(float maxReductionDb);
    void setEnabled(bool enabled) { enabled_ = enabled; }
    bool isEnabled() const { return enabled_; }
    float getMaxReductionDb() const { return maxReductionDb_; }
    float getGainReduction() const { return gainReduction_; } // 当前增益衰减系数 (1.0 = 无衰减)

    void processStereo(float* buffer, int numFrames);
    void reset();

private:
    void updateCoefficients();
    float applySoftSaturation(float x) const;

    bool enabled_ = true;
    float sampleRate_ = 44100.0f;
    float thresholdDb_ = -0.3f;            // -0.3dBFS 安全真峰值砖墙天花板
    float thresholdLinear_ = 0.966051f;    // 10^(-0.3 / 20)
    float attackMs_ = 0.5f;                // 0.5ms 快速捕捉瞬态峰值
    float releaseMs_ = 20.0f;              // 20ms 快速平滑释放，低音冲击后即刻复原，消除抽吸感
    float kneeDb_ = 0.8f;                  // 0.8dB 软拐点宽度 (Soft Knee)
    float maxReductionDb_ = -2.0f;         // 默认最大衰减 -2.0dB
    float maxReductionLinear_ = 0.794328f; // 10^(-2.0 / 20)

    float attackCoeff_ = 0.0f;
    float releaseCoeff_ = 0.0f;

    float envelope_ = 0.0f;
    float gainReduction_ = 1.0f;
};

} // namespace dsp
