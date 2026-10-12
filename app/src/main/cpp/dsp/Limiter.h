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
    float thresholdDb_ = -1.5f;          // 工业标准 -1.5dBFS 安全真峰值防削波余量
    float thresholdLinear_ = 0.841395f;  // 10^(-1.5 / 20)
    float attackMs_ = 1.2f;              // 1.2ms 柔和温润起控，避免单周期调制失真
    float releaseMs_ = 65.0f;            // 65ms 音乐性自适应平滑释放常数
    float kneeDb_ = 2.0f;                // 2.0dB 软拐点宽度 (Soft Knee)
    float maxReductionDb_ = -3.0f;       // 默认最大衰减 -3.0dB (音调/前级关闭时的原设计保护下限)
    float maxReductionLinear_ = 0.707945f; // 10^(-3.0 / 20)

    float attackCoeff_ = 0.0f;
    float releaseCoeff_ = 0.0f;

    float envelope_ = 0.0f;
    float gainReduction_ = 1.0f;
};

} // namespace dsp
