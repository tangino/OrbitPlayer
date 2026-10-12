#include "BassLimiter.h"

namespace dsp {

BassLimiter::BassLimiter(float sampleRate) : sampleRate_(sampleRate) {
    // 分频点 180Hz：完整覆盖 Bass Boost (90Hz Low Shelf) 的提升区间
    crossoverHz_ = 180.0f;
    // 低频段限幅设为 -0.5dBFS 门限：保留充分动态余量，正常低音不被过度削波
    thresholdDb_ = -0.5f;
    updateCrossover();
    lowBandLimiter_.setSampleRate(sampleRate_);
    lowBandLimiter_.setThresholdDb(thresholdDb_);
    lowBandLimiter_.setAttackTimeMs(0.8f);
    lowBandLimiter_.setReleaseTimeMs(22.0f);   // 22ms 迅捷平滑释放，节拍过后瞬间恢复，彻底杜绝抽吸泵感
    lowBandLimiter_.setMaxReductionDb(-4.0f);  // 最大衰减严格限制在 -4.0dB，保证低频底盘饱满，绝不让音量断崖式下跌
    lowBandLimiter_.setEnabled(true);
}

void BassLimiter::setSampleRate(float sampleRate) {
    sampleRate_ = sampleRate > 0.0f ? sampleRate : 44100.0f;
    lowBandLimiter_.setSampleRate(sampleRate_);
    reset();
    updateCrossover();
}

void BassLimiter::setEnabled(bool enabled) {
    if (enabled_ == enabled) return;
    enabled_ = enabled;
    // 分频/限幅状态在旁路期间未更新，切换瞬间必须清空，避免残留爆音
    reset();
}

void BassLimiter::setCrossoverFrequency(float freq) {
    crossoverHz_ = std::clamp(freq, 60.0f, 400.0f);
    updateCrossover();
}

void BassLimiter::setThresholdDb(float thresholdDb) {
    thresholdDb_ = std::clamp(thresholdDb, -24.0f, 0.0f);
    lowBandLimiter_.setThresholdDb(thresholdDb_);
}

void BassLimiter::setMaxReductionDb(float maxReductionDb) {
    lowBandLimiter_.setMaxReductionDb(maxReductionDb);
}

float BassLimiter::getGainReductionDb() const {
    const float gr = lowBandLimiter_.getGainReduction();
    return gr > 1e-6f ? 20.0f * std::log10(gr) : -60.0f;
}

void BassLimiter::updateCrossover() {
    // Linkwitz-Riley 4 阶：每路两级 Q = 0.7071 的二阶 Butterworth 级联
    for (int i = 0; i < 2; ++i) {
        lowPassL_[i].configure(FilterType::LOW_PASS, crossoverHz_, 0.0f, kButterworthQ, sampleRate_);
        lowPassR_[i].configure(FilterType::LOW_PASS, crossoverHz_, 0.0f, kButterworthQ, sampleRate_);
        highPassL_[i].configure(FilterType::HIGH_PASS, crossoverHz_, 0.0f, kButterworthQ, sampleRate_);
        highPassR_[i].configure(FilterType::HIGH_PASS, crossoverHz_, 0.0f, kButterworthQ, sampleRate_);
    }
}

void BassLimiter::processStereo(float* buffer, int numFrames) {
    if (!enabled_ || numFrames <= 0) return;

    float lowChunk[kChunkFrames * 2];

    for (int offset = 0; offset < numFrames; offset += kChunkFrames) {
        const int chunk = std::min(kChunkFrames, numFrames - offset);
        const int samples = chunk * 2;
        float* chunkBase = buffer + static_cast<size_t>(offset) * 2;

        for (int i = 0; i < samples; i += 2) {
            const float l = chunkBase[i];
            const float r = chunkBase[i + 1];

            lowChunk[i] = lowPassL_[1].processSample(lowPassL_[0].processSample(l));
            lowChunk[i + 1] = lowPassR_[1].processSample(lowPassR_[0].processSample(r));

            // Linkwitz-Riley 4 阶：两路在分频点同相，直接相加即幅频平直 (不可反转极性)
            chunkBase[i] = highPassL_[1].processSample(highPassL_[0].processSample(l));
            chunkBase[i + 1] = highPassR_[1].processSample(highPassR_[0].processSample(r));
        }

        // 仅对低频段做峰值限幅：增益衰减只影响低频，中高频 (人声) 完全不受影响
        lowBandLimiter_.processStereo(lowChunk, chunk);

        for (int i = 0; i < samples; i += 2) {
            chunkBase[i] += lowChunk[i];
            chunkBase[i + 1] += lowChunk[i + 1];
        }
    }
}

void BassLimiter::reset() {
    for (int i = 0; i < 2; ++i) {
        lowPassL_[i].reset();
        lowPassR_[i].reset();
        highPassL_[i].reset();
        highPassR_[i].reset();
    }
    lowBandLimiter_.reset();
}

} // namespace dsp
