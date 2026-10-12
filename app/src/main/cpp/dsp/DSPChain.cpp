#include "DSPChain.h"

namespace dsp {

DSPChain::DSPChain() {
    setSampleRate(44100.0f);
    updateToneDependentStages();
}

/**
 * 音调状态联动两件事：
 * 1. 分频带低频限幅器：只要低频提升开启，就由它把低音过冲能量就地吸收，
 *    中高频 (人声/乐器) 完全不被牵连，从根上消除"低音开大把人声压小"。
 * 2. 主限幅器的衰减权限：作为最终安全网，仍按音调提升量放宽下限，
 *    确保任何情况下都不会突破 0dBFS 被硬削波。音调关闭时维持原有 -3dB 保护下限。
 */
void DSPChain::updateToneDependentStages() {
    float toneBoostDb = 0.0f;
    if (bassBoost_.isEnabled()) {
        toneBoostDb = std::max(toneBoostDb, bassBoost_.getMaxGainDb() * bassBoost_.getStrength());
    }
    if (trebleBoost_.isEnabled()) {
        toneBoostDb = std::max(toneBoostDb, trebleBoost_.getMaxGainDb() * trebleBoost_.getStrength());
    }

    // 低频提升开启时启用分频带低频限幅 (不提升时完全旁路，零开销、零音染)
    bassLimiter_.setEnabled(bassBoost_.isEnabled() && bassBoost_.getStrength() > 0.001f);

    const float maxReductionDb = -(std::min(kToneFreeHeadroomDb + toneBoostDb, kMaxLimiterReductionDb));
    limiter_.setMaxReductionDb(maxReductionDb);
}

void DSPChain::setSampleRate(float sampleRate) {
    std::lock_guard<std::mutex> lock(configMutex_);
    sampleRate_ = sampleRate;
    graphicEQ_.setSampleRate(sampleRate_);
    parametricEQ_.setSampleRate(sampleRate_);
    bassBoost_.setSampleRate(sampleRate_);
    trebleBoost_.setSampleRate(sampleRate_);
    bassLimiter_.setSampleRate(sampleRate_);
    compressor_.setSampleRate(sampleRate_);
    limiter_.setSampleRate(sampleRate_);
}

void DSPChain::setEngineMode(EQEngineMode mode) {
    std::lock_guard<std::mutex> lock(configMutex_);
    engineMode_ = mode;
}

void DSPChain::setEnabled(bool enabled) {
    equalizerEnabled_ = enabled;
}

void DSPChain::setGraphicMode(GraphicEQMode mode) {
    std::lock_guard<std::mutex> lock(configMutex_);
    graphicEQ_.setMode(mode);
}

void DSPChain::setGraphicBandGain(int bandIndex, float gainDb) {
    std::lock_guard<std::mutex> lock(configMutex_);
    graphicEQ_.setBandGain(bandIndex, gainDb);
}

float DSPChain::getGraphicBandGain(int bandIndex) const {
    std::lock_guard<std::mutex> lock(configMutex_);
    return graphicEQ_.getBandGain(bandIndex);
}

const std::vector<float>& DSPChain::getGraphicFrequencies() const {
    std::lock_guard<std::mutex> lock(configMutex_);
    return graphicEQ_.getFrequencies();
}

void DSPChain::setParametricBands(const std::vector<BandConfig>& configs) {
    std::lock_guard<std::mutex> lock(configMutex_);
    parametricEQ_.setBands(configs);
}

void DSPChain::setParametricBandGain(int bandIndex, float gainDb) {
    std::lock_guard<std::mutex> lock(configMutex_);
    parametricEQ_.setBandGain(bandIndex, gainDb);
}

void DSPChain::setParametricBandFrequency(int bandIndex, float freq) {
    std::lock_guard<std::mutex> lock(configMutex_);
    parametricEQ_.setBandFrequency(bandIndex, freq);
}

void DSPChain::setParametricBandQ(int bandIndex, float q) {
    std::lock_guard<std::mutex> lock(configMutex_);
    parametricEQ_.setBandQ(bandIndex, q);
}

void DSPChain::setBassBoostEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(configMutex_);
    bassBoost_.setEnabled(enabled);
    updateToneDependentStages();
}

bool DSPChain::isBassBoostEnabled() const {
    return bassBoost_.isEnabled();
}

void DSPChain::setBassBoostStrength(float strength) {
    std::lock_guard<std::mutex> lock(configMutex_);
    bassBoost_.setStrength(strength);
    updateToneDependentStages();
}

void DSPChain::setTrebleBoostEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(configMutex_);
    trebleBoost_.setEnabled(enabled);
    updateToneDependentStages();
}

bool DSPChain::isTrebleBoostEnabled() const {
    return trebleBoost_.isEnabled();
}

void DSPChain::setTrebleBoostStrength(float strength) {
    std::lock_guard<std::mutex> lock(configMutex_);
    trebleBoost_.setStrength(strength);
    updateToneDependentStages();
}

void DSPChain::setPreampGainDb(float gainDb) {
    std::lock_guard<std::mutex> lock(configMutex_);
    preamp_.setGainDb(gainDb);
}

float DSPChain::getPreampGainDb() const {
    return preamp_.getGainDb();
}

bool DSPChain::isClippingAndReset() {
    return preamp_.isClippingAndReset();
}

void DSPChain::setCompressorEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(configMutex_);
    compressor_.setEnabled(enabled);
}

bool DSPChain::isCompressorEnabled() const {
    return compressor_.isEnabled();
}

void DSPChain::setCompressorParams(float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupGainDb) {
    std::lock_guard<std::mutex> lock(configMutex_);
    compressor_.setThresholdDb(thresholdDb);
    compressor_.setRatio(ratio);
    compressor_.setAttackTimeMs(attackMs);
    compressor_.setReleaseTimeMs(releaseMs);
    compressor_.setMakeupGainDb(makeupGainDb);
}

void DSPChain::setLimiterEnabled(bool enabled) {
    limiter_.setEnabled(enabled);
}

bool DSPChain::isLimiterEnabled() const {
    return limiter_.isEnabled();
}

void DSPChain::setLimiterThresholdDb(float thresholdDb) {
    std::lock_guard<std::mutex> lock(configMutex_);
    limiter_.setThresholdDb(thresholdDb);
}

void DSPChain::process(float* buffer, int numFrames) {
    // 1. Equalizer (Graphic or Parametric) - 独立受均衡器开关控制
    if (equalizerEnabled_) {
        if (engineMode_ == EQEngineMode::GRAPHIC) {
            graphicEQ_.processStereo(buffer, numFrames);
        } else {
            parametricEQ_.processStereo(buffer, numFrames);
        }
    }

    // 2. 音调模块 (Bass Boost & Treble Boost) - 独立于 EQ，内部各自管理 enabled_ 与 strength
    bassBoost_.processStereo(buffer, numFrames);
    trebleBoost_.processStereo(buffer, numFrames);

    // 2.5 分频带低频限幅：把低音提升的过冲就地压回安全区，中高频完全不受影响
    bassLimiter_.processStereo(buffer, numFrames);

    // 3. Preamp 前级增益
    preamp_.processStereo(buffer, numFrames);

    // 4. Dynamic Compressor 动态压缩器
    compressor_.processStereo(buffer, numFrames);

    // 5. Lookahead / Peak Limiter 峰值限制器防削波
    limiter_.processStereo(buffer, numFrames);

    // 实时推入 FFT 频谱与 Peak 监测
    fftAnalyzer_.pushSamples(buffer, numFrames);
}

float DSPChain::getMagnitudeResponse(float frequency) const {
    std::lock_guard<std::mutex> lock(configMutex_);
    float eqDb = 0.0f;
    if (equalizerEnabled_) {
        if (engineMode_ == EQEngineMode::GRAPHIC) {
            eqDb = graphicEQ_.getTotalMagnitudeResponse(frequency);
        } else {
            eqDb = parametricEQ_.getTotalMagnitudeResponse(frequency);
        }
    }
    // 音调 (Bass/Treble) 也计入响应曲线：低频段经分频带限幅处理，
    // 曲线按 Shelf 目标增益呈现 (实际低频路径在 180Hz 以上由分频器自然滚降)
    const float toneDb = bassBoost_.getMagnitudeResponse(frequency)
                       + trebleBoost_.getMagnitudeResponse(frequency);
    return eqDb + toneDb + preamp_.getGainDb();
}

void DSPChain::getSpectrum(float* outBands, int numBands) {
    fftAnalyzer_.calculateSpectrum(outBands, numBands);
}

void DSPChain::getPeakLevels(float& outLeftDb, float& outRightDb) {
    fftAnalyzer_.getPeakLevels(outLeftDb, outRightDb);
}

void DSPChain::reset() {
    std::lock_guard<std::mutex> lock(configMutex_);
    graphicEQ_.reset();
    parametricEQ_.reset();
    bassBoost_.reset();
    trebleBoost_.reset();
    bassLimiter_.reset();
    preamp_.reset();
    compressor_.reset();
    limiter_.reset();
}

} // namespace dsp
