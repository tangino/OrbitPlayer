#include "Limiter.h"

namespace dsp {

Limiter::Limiter(float sampleRate) : sampleRate_(sampleRate) {
    setThresholdDb(-0.3f); // 提升门限至 -0.3dBFS，保留充足动态
    attackMs_ = 0.5f;      // 0.5ms 迅捷起控
    releaseMs_ = 20.0f;    // 20ms 快速平滑释放，低音冲击后即刻恢复，消除抽吸泵感
    maxReductionDb_ = -2.0f;
    maxReductionLinear_ = std::pow(10.0f, maxReductionDb_ / 20.0f);
    kneeDb_ = 0.8f;
    updateCoefficients();
}

void Limiter::setSampleRate(float sampleRate) {
    sampleRate_ = sampleRate > 0.0f ? sampleRate : 44100.0f;
    updateCoefficients();
}

void Limiter::setThresholdDb(float thresholdDb) {
    thresholdDb_ = std::min(0.0f, thresholdDb);
    thresholdLinear_ = std::pow(10.0f, thresholdDb_ / 20.0f);
}

void Limiter::setAttackTimeMs(float attackMs) {
    attackMs_ = std::max(0.1f, attackMs);
    updateCoefficients();
}

void Limiter::setReleaseTimeMs(float releaseMs) {
    releaseMs_ = std::max(5.0f, releaseMs);
    updateCoefficients();
}

void Limiter::setMaxReductionDb(float maxReductionDb) {
    maxReductionDb_ = std::clamp(maxReductionDb, -24.0f, 0.0f);
    maxReductionLinear_ = std::pow(10.0f, maxReductionDb_ / 20.0f);
}

void Limiter::updateCoefficients() {
    attackCoeff_ = std::exp(-1.0f / (attackMs_ * 0.001f * sampleRate_));
    releaseCoeff_ = std::exp(-1.0f / (releaseMs_ * 0.001f * sampleRate_));
}

// 平滑软饱和函数：在拐点处一阶连续 (斜率恒为 1)，且渐近线严格收敛到 ±1.0
// 关键修复：旧实现 excess/(1+1.5*excess) 的渐近值为 0.98+1/1.5≈1.65，
// 输出会突破 0dBFS 后在 AudioTrack/HAL 被硬削波，造成低音开大就"糊/爆"。
float Limiter::applySoftSaturation(float x) const {
    constexpr float limit = 0.95f;          // 软拐点起点 (-0.45dBFS)
    constexpr float headroom = 1.0f - limit; // 拐点以上的最大可压缩余量
    if (x > limit) {
        const float excess = x - limit;
        return limit + headroom * (excess / (excess + headroom));
    } else if (x < -limit) {
        const float excess = -limit - x;
        return -limit - headroom * (excess / (excess + headroom));
    }
    return x;
}

void Limiter::processStereo(float* buffer, int numFrames) {
    if (!enabled_) return;

    const float lowerThreshold = thresholdLinear_ * std::pow(10.0f, -kneeDb_ / 20.0f);
    const float kneeWidthLinear = thresholdLinear_ - lowerThreshold;

    for (int f = 0; f < numFrames; ++f) {
        float sampleL = buffer[f * 2];
        float sampleR = buffer[f * 2 + 1];

        // 探测立体声瞬时包络峰值
        float peak = std::max(std::abs(sampleL), std::abs(sampleR));

        // 峰值检波器：平滑跟踪
        if (peak > envelope_) {
            envelope_ = attackCoeff_ * envelope_ + (1.0f - attackCoeff_) * peak;
        } else {
            envelope_ = releaseCoeff_ * envelope_ + (1.0f - releaseCoeff_) * peak;
        }

        // 计算目标增益（带软拐点 Soft Knee 平滑过渡）
        float targetGain = 1.0f;
        if (envelope_ > thresholdLinear_) {
            targetGain = thresholdLinear_ / envelope_;
        } else if (envelope_ > lowerThreshold && kneeWidthLinear > 1e-5f) {
            // 二次平滑软拐点插值
            float x = (envelope_ - lowerThreshold) / (thresholdLinear_ - lowerThreshold);
            targetGain = 1.0f - 0.5f * (x * x) * (1.0f - thresholdLinear_ / envelope_);
        }

        // 增益衰减下限保护：默认 -3.0dB 保护下限 (杜绝低音把人声压小)；
        // 当音调/前级存在提升时，DSPChain 会通过 setMaxReductionDb() 放宽该下限，
        // 让限幅器有能力把提升后的峰值真正压回门限内，避免只能靠软饱和硬吃而产生失真
        targetGain = std::max(targetGain, maxReductionLinear_);

        // 增益控制平滑滤波（杜绝包络快速变化引起的拉链声或互调失真）
        if (targetGain < gainReduction_) {
            gainReduction_ = attackCoeff_ * gainReduction_ + (1.0f - attackCoeff_) * targetGain;
        } else {
            gainReduction_ = releaseCoeff_ * gainReduction_ + (1.0f - releaseCoeff_) * targetGain;
        }

        float outL = sampleL * gainReduction_;
        float outR = sampleR * gainReduction_;

        // 经过柔和软饱和处理输出
        buffer[f * 2] = applySoftSaturation(outL);
        buffer[f * 2 + 1] = applySoftSaturation(outR);
    }
}

void Limiter::reset() {
    envelope_ = 0.0f;
    gainReduction_ = 1.0f;
}

} // namespace dsp
