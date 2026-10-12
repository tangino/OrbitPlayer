#pragma once

#include "BiquadFilter.h"
#include "Limiter.h"
#include <algorithm>
#include <cmath>

namespace dsp {

/**
 * 分频带低频限幅器 (Bass-band Limiter)
 *
 * 架构：Linkwitz-Riley 4 阶分频 → 低频段独立峰值限幅 → 与高频段等幅无损相加
 * 目的：低音提升后的过冲能量全部由低频段自身吸收，中高频 (人声/乐器) 完全不被牵连，
 *       彻底解决"低音开大就把人声压小"的宽带抽吸问题。
 *
 * 说明：Linkwitz-Riley 4 阶 = 两级 Q = 0.7071 的二阶 Butterworth 级联 (24dB/oct)，
 *       两级级联后高通支路在分频点相对低通支路相位差为 360°，两路直接相加即得
 *       全频段平直幅频响应 (不得做极性反转，否则分频点会自相抵消成陷波)。
 *       采用 4 阶而非 2 阶：2 阶高通支路在 60Hz 处仍有约 -12dB 泄漏，
 *       低音提升的能量会"绕开"低频限幅器，导致限幅失效。
 */
class BassLimiter {
public:
    explicit BassLimiter(float sampleRate = 44100.0f);
    ~BassLimiter() = default;

    void setSampleRate(float sampleRate);
    void setEnabled(bool enabled);
    void setCrossoverFrequency(float freq);
    void setThresholdDb(float thresholdDb);
    void setMaxReductionDb(float maxReductionDb);

    bool isEnabled() const { return enabled_; }
    float getCrossoverFrequency() const { return crossoverHz_; }
    float getThresholdDb() const { return thresholdDb_; }
    // 当前低频段增益衰减量 (dB, ≤ 0)，用于监测 / 曲线绘制
    float getGainReductionDb() const;

    void processStereo(float* buffer, int numFrames);
    void reset();

private:
    static constexpr int kChunkFrames = 256;              // 栈上分块处理，实时路径零堆分配
    static constexpr float kButterworthQ = 0.70710678f;   // Linkwitz-Riley 各支路 Q 值

    void updateCrossover();

    bool enabled_ = false;
    float sampleRate_ = 44100.0f;
    float crossoverHz_ = 180.0f;
    float thresholdDb_ = -0.5f;

    // Linkwitz-Riley 4 阶：每路两级 Butterworth 级联
    BiquadFilter lowPassL_[2];
    BiquadFilter lowPassR_[2];
    BiquadFilter highPassL_[2];
    BiquadFilter highPassR_[2];
    Limiter lowBandLimiter_;                              // 只作用于低频段的峰值限幅器
};

} // namespace dsp
