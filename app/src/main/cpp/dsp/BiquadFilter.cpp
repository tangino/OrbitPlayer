#include "BiquadFilter.h"
#include <cmath>
#include <algorithm>

namespace dsp {

constexpr float PI = 3.14159265358979323846f;

BiquadFilter::BiquadFilter() {
    reset();
}

void BiquadFilter::configure(FilterType type, float frequency, float gainDb, float q, float sampleRate) {
    type_ = type;
    // 频率限制在奈奎斯特频率以内
    frequency_ = std::clamp(frequency, 10.0f, sampleRate * 0.49f);
    gainDb_ = gainDb;
    q_ = std::max(0.1f, q);
    sampleRate_ = sampleRate > 0.0f ? sampleRate : 44100.0f;
    recalculateCoefficients();
}

void BiquadFilter::reset() {
    s1_ = 0.0f;
    s2_ = 0.0f;
}

void BiquadFilter::processBlock(const float* in, float* out, int numSamples) {
    for (int i = 0; i < numSamples; ++i) {
        out[i] = processSample(in[i]);
    }
}

void BiquadFilter::recalculateCoefficients() {
    float A = std::pow(10.0f, gainDb_ / 40.0f); // 对 Shelf / Peaking 为 sqrt(10^(gain/20))
    float w0 = 2.0f * PI * frequency_ / sampleRate_;
    float cos_w0 = std::cos(w0);
    float sin_w0 = std::sin(w0);
    float alpha = sin_w0 / (2.0f * q_);

    float a0 = 1.0f;

    switch (type_) {
        case FilterType::PEAKING: {
            b0_ = 1.0f + alpha * A;
            b1_ = -2.0f * cos_w0;
            b2_ = 1.0f - alpha * A;
            a0  = 1.0f + alpha / A;
            a1_ = -2.0f * cos_w0;
            a2_ = 1.0f - alpha / A;
            break;
        }
        case FilterType::LOW_SHELF: {
            float sqrtA = std::sqrt(A);
            b0_ = A * ((A + 1.0f) - (A - 1.0f) * cos_w0 + 2.0f * sqrtA * alpha);
            b1_ = 2.0f * A * ((A - 1.0f) - (A + 1.0f) * cos_w0);
            b2_ = A * ((A + 1.0f) - (A - 1.0f) * cos_w0 - 2.0f * sqrtA * alpha);
            a0  = (A + 1.0f) + (A - 1.0f) * cos_w0 + 2.0f * sqrtA * alpha;
            a1_ = -2.0f * ((A - 1.0f) + (A + 1.0f) * cos_w0);
            a2_ = (A + 1.0f) + (A - 1.0f) * cos_w0 - 2.0f * sqrtA * alpha;
            break;
        }
        case FilterType::HIGH_SHELF: {
            float sqrtA = std::sqrt(A);
            b0_ = A * ((A + 1.0f) + (A - 1.0f) * cos_w0 + 2.0f * sqrtA * alpha);
            b1_ = -2.0f * A * ((A - 1.0f) + (A + 1.0f) * cos_w0);
            b2_ = A * ((A + 1.0f) + (A - 1.0f) * cos_w0 - 2.0f * sqrtA * alpha);
            a0  = (A + 1.0f) - (A - 1.0f) * cos_w0 + 2.0f * sqrtA * alpha;
            a1_ = 2.0f * ((A - 1.0f) - (A + 1.0f) * cos_w0);
            a2_ = (A + 1.0f) - (A - 1.0f) * cos_w0 - 2.0f * sqrtA * alpha;
            break;
        }
        case FilterType::LOW_PASS: {
            b0_ = (1.0f - cos_w0) * 0.5f;
            b1_ = 1.0f - cos_w0;
            b2_ = (1.0f - cos_w0) * 0.5f;
            a0  = 1.0f + alpha;
            a1_ = -2.0f * cos_w0;
            a2_ = 1.0f - alpha;
            break;
        }
        case FilterType::HIGH_PASS: {
            b0_ = (1.0f + cos_w0) * 0.5f;
            b1_ = -(1.0f + cos_w0);
            b2_ = (1.0f + cos_w0) * 0.5f;
            a0  = 1.0f + alpha;
            a1_ = -2.0f * cos_w0;
            a2_ = 1.0f - alpha;
            break;
        }
        case FilterType::BAND_PASS: {
            b0_ = alpha;
            b1_ = 0.0f;
            b2_ = -alpha;
            a0  = 1.0f + alpha;
            a1_ = -2.0f * cos_w0;
            a2_ = 1.0f - alpha;
            break;
        }
        case FilterType::NOTCH: {
            b0_ = 1.0f;
            b1_ = -2.0f * cos_w0;
            b2_ = 1.0f;
            a0  = 1.0f + alpha;
            a1_ = -2.0f * cos_w0;
            a2_ = 1.0f - alpha;
            break;
        }
    }

    // 归一化系数
    float inv_a0 = 1.0f / a0;
    b0_ *= inv_a0;
    b1_ *= inv_a0;
    b2_ *= inv_a0;
    a1_ *= inv_a0;
    a2_ *= inv_a0;
}

float BiquadFilter::getMagnitudeResponse(float freq, float sampleRate) const {
    float w = 2.0f * PI * freq / sampleRate;
    float cos_w = std::cos(w);
    float cos_2w = std::cos(2.0f * w);
    float sin_w = std::sin(w);
    float sin_2w = std::sin(2.0f * w);

    float numReal = b0_ + b1_ * cos_w + b2_ * cos_2w;
    float numImag = -b1_ * sin_w - b2_ * sin_2w;
    float denReal = 1.0f + a1_ * cos_w + a2_ * cos_2w;
    float denImag = -a1_ * sin_w - a2_ * sin_2w;

    float numMagSq = numReal * numReal + numImag * numImag;
    float denMagSq = denReal * denReal + denImag * denImag;

    if (denMagSq < 1e-12f) return 0.0f;

    float magSq = numMagSq / denMagSq;
    return 10.0f * std::log10(std::max(1e-12f, magSq));
}

} // namespace dsp
