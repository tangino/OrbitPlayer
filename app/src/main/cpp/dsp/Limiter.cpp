#include "Limiter.h"

namespace dsp {

Limiter::Limiter(float sampleRate) : sampleRate_(sampleRate) {
    setThresholdDb(-2.5f);
    updateCoefficients();
}

void Limiter::setSampleRate(float sampleRate) {
    sampleRate_ = sampleRate > 0.0f ? sampleRate : 44100.0f;
    updateCoefficients();
}

void Limiter::setThresholdDb(float thresholdDb) {
    thresholdDb_ = thresholdDb;
    thresholdLinear_ = std::pow(10.0f, thresholdDb / 20.0f);
}

void Limiter::setAttackTimeMs(float attackMs) {
    attackMs_ = std::max(0.05f, attackMs);
    updateCoefficients();
}

void Limiter::setReleaseTimeMs(float releaseMs) {
    releaseMs_ = std::max(5.0f, releaseMs);
    updateCoefficients();
}

void Limiter::updateCoefficients() {
    attackCoeff_ = std::exp(-1.0f / (attackMs_ * 0.001f * sampleRate_));
    releaseCoeff_ = std::exp(-1.0f / (releaseMs_ * 0.001f * sampleRate_));
}

void Limiter::processStereo(float* buffer, int numFrames) {
    if (!enabled_) return;

    for (int f = 0; f < numFrames; ++f) {
        float sampleL = buffer[f * 2];
        float sampleR = buffer[f * 2 + 1];

        // 探测立体声最大瞬时绝对值
        float peak = std::max(std::abs(sampleL), std::abs(sampleR));

        // 峰值检波器：快速追踪峰值，平滑释放
        if (peak > envelope_) {
            envelope_ = attackCoeff_ * envelope_ + (1.0f - attackCoeff_) * peak;
        } else {
            envelope_ = releaseCoeff_ * envelope_ + (1.0f - releaseCoeff_) * peak;
        }

        // 目标增益：仅在包络超过阈值时才发生衰减，否则严格保持 1.0f 透明直通
        float targetGain = 1.0f;
        if (envelope_ > thresholdLinear_) {
            targetGain = thresholdLinear_ / envelope_;
        }

        // 增益平滑应用：消除采样点级的剧烈微抖动（调制失真）
        if (targetGain < gainReduction_) {
            gainReduction_ = attackCoeff_ * gainReduction_ + (1.0f - attackCoeff_) * targetGain;
        } else {
            gainReduction_ = releaseCoeff_ * gainReduction_ + (1.0f - releaseCoeff_) * targetGain;
        }

        float outL = sampleL * gainReduction_;
        float outR = sampleR * gainReduction_;

        // 最终硬限幅保护（兜底保障绝不发生数字溢出）
        buffer[f * 2] = std::clamp(outL, -1.0f, 1.0f);
        buffer[f * 2 + 1] = std::clamp(outR, -1.0f, 1.0f);
    }
}

void Limiter::reset() {
    envelope_ = 0.0f;
    gainReduction_ = 1.0f;
}

} // namespace dsp
