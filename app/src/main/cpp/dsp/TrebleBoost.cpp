#include "TrebleBoost.h"

namespace dsp {

TrebleBoost::TrebleBoost(float sampleRate) : sampleRate_(sampleRate) {
    frequency_ = 10000.0f; // Poweramp 原版高音中心频点 10kHz
    updateFilter();
}

void TrebleBoost::setSampleRate(float sampleRate) {
    sampleRate_ = sampleRate > 0.0f ? sampleRate : 44100.0f;
    // 采样率变化后旧采样率下的滤波器状态已失效，必须清空，避免切换瞬间的爆音
    reset();
    updateFilter();
}

void TrebleBoost::setStrength(float strength) {
    strength_ = std::clamp(strength, 0.0f, 1.0f);

    const float newTarget = strength_ * kMaxGainDb;
    const bool wasSilent = gainDbTarget_ <= 0.001f;

    if (!enabled_) {
        // 旁路状态下直接对齐，确保下次开启时立即达到目标增益，无需爬升
        gainDbTarget_ = newTarget;
        gainDbCurrent_ = newTarget;
        updateFilter();
        return;
    }

    if (wasSilent && newTarget > 0.001f) {
        // 从 0 增益重新进入处理区：旁路期间滤波器状态未更新，先清空状态杜绝残留冲击
        reset();
        gainDbCurrent_ = 0.0f;
    }
    gainDbTarget_ = newTarget;
}

void TrebleBoost::setEnabled(bool enabled) {
    if (enabled_ == enabled) return;
    enabled_ = enabled;
    // 开启/关闭瞬间清空滤波器状态，消除冻结状态导致的爆音瞬态
    gainDbCurrent_ = gainDbTarget_;
    updateFilter();
    reset();
}

void TrebleBoost::setCenterFrequency(float freq) {
    frequency_ = std::clamp(freq, 4000.0f, 20000.0f);
    updateFilter();
}

void TrebleBoost::updateFilter() {
    // Poweramp 原版规格：High Shelf，Q 值为 0.8f，最大增益 +15.0dB
    const float gainDb = gainDbCurrent_;
    highShelfL_.configure(FilterType::HIGH_SHELF, frequency_, gainDb, 0.80f, sampleRate_);
    highShelfR_.configure(FilterType::HIGH_SHELF, frequency_, gainDb, 0.80f, sampleRate_);
}

void TrebleBoost::processStereo(float* buffer, int numFrames) {
    if (!enabled_ || numFrames <= 0) return;

    const bool needsRamp = std::abs(gainDbTarget_ - gainDbCurrent_) > 0.001f;

    if (!needsRamp && gainDbCurrent_ <= 0.001f) {
        // 0dB 提升量：完全旁路，同时保持滤波器状态干净
        reset();
        return;
    }

    if (!needsRamp) {
        // 增益已稳定：整块匀速处理 (零额外开销热路径)
        for (int f = 0; f < numFrames; ++f) {
            buffer[f * 2] = highShelfL_.processSample(buffer[f * 2]);
            buffer[f * 2 + 1] = highShelfR_.processSample(buffer[f * 2 + 1]);
        }
        return;
    }

    // 参数平滑：把音频块切成若干子块，逐子块逼近目标增益并刷新系数，
    // 彻底消除拖动旋钮时的系数跳变 (拉链噪声 / 咔哒声)
    const int steps = std::min(kMaxRampSteps, std::max(1, numFrames));
    const int stepFrames = std::max(1, (numFrames + steps - 1) / steps);
    const float rampCoeff = 1.0f - std::exp(-static_cast<float>(stepFrames) / (kSmoothTimeSec * sampleRate_));

    int processed = 0;
    while (processed < numFrames) {
        const int chunk = std::min(stepFrames, numFrames - processed);

        gainDbCurrent_ += (gainDbTarget_ - gainDbCurrent_) * rampCoeff;
        if (std::abs(gainDbTarget_ - gainDbCurrent_) < 0.001f) {
            gainDbCurrent_ = gainDbTarget_;
        }
        updateFilter();

        for (int i = 0; i < chunk; ++i) {
            const int idx = (processed + i) * 2;
            buffer[idx] = highShelfL_.processSample(buffer[idx]);
            buffer[idx + 1] = highShelfR_.processSample(buffer[idx + 1]);
        }
        processed += chunk;
    }
}

void TrebleBoost::reset() {
    highShelfL_.reset();
    highShelfR_.reset();
}

float TrebleBoost::getMagnitudeResponse(float frequency) const {
    if (!enabled_ || gainDbCurrent_ <= 0.001f) return 0.0f;
    return highShelfL_.getMagnitudeResponse(frequency, sampleRate_);
}

} // namespace dsp
