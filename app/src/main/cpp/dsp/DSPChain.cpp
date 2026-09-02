#include "DSPChain.h"

namespace dsp {

DSPChain::DSPChain() {
    setSampleRate(44100.0f);
}

void DSPChain::setSampleRate(float sampleRate) {
    std::lock_guard<std::mutex> lock(configMutex_);
    sampleRate_ = sampleRate;
    graphicEQ_.setSampleRate(sampleRate_);
    parametricEQ_.setSampleRate(sampleRate_);
    bassBoost_.setSampleRate(sampleRate_);
    trebleBoost_.setSampleRate(sampleRate_);
    compressor_.setSampleRate(sampleRate_);
    limiter_.setSampleRate(sampleRate_);
}

void DSPChain::setEngineMode(EQEngineMode mode) {
    std::lock_guard<std::mutex> lock(configMutex_);
    engineMode_ = mode;
}

void DSPChain::setEnabled(bool enabled) {
    enabled_ = enabled;
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
}

bool DSPChain::isBassBoostEnabled() const {
    return bassBoost_.isEnabled();
}

void DSPChain::setBassBoostStrength(float strength) {
    std::lock_guard<std::mutex> lock(configMutex_);
    bassBoost_.setStrength(strength);
}

void DSPChain::setTrebleBoostEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(configMutex_);
    trebleBoost_.setEnabled(enabled);
}

bool DSPChain::isTrebleBoostEnabled() const {
    return trebleBoost_.isEnabled();
}

void DSPChain::setTrebleBoostStrength(float strength) {
    std::lock_guard<std::mutex> lock(configMutex_);
    trebleBoost_.setStrength(strength);
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
    if (enabled_) {
        // 1. Equalizer (Graphic or Parametric)
        if (engineMode_ == EQEngineMode::GRAPHIC) {
            graphicEQ_.processStereo(buffer, numFrames);
        } else {
            parametricEQ_.processStereo(buffer, numFrames);
        }

        // 2. Bass Boost & Treble Boost
        bassBoost_.processStereo(buffer, numFrames);
        trebleBoost_.processStereo(buffer, numFrames);

        // 3. Preamp
        preamp_.processStereo(buffer, numFrames);

        // 4. Dynamic Compressor
        compressor_.processStereo(buffer, numFrames);

        // 5. Lookahead / Peak Limiter
        limiter_.processStereo(buffer, numFrames);
    }

    // 实时推入 FFT 频谱与 Peak 监测
    fftAnalyzer_.pushSamples(buffer, numFrames);
}

float DSPChain::getMagnitudeResponse(float frequency) const {
    if (!enabled_) return 0.0f;
    std::lock_guard<std::mutex> lock(configMutex_);
    float eqDb = 0.0f;
    if (engineMode_ == EQEngineMode::GRAPHIC) {
        eqDb = graphicEQ_.getTotalMagnitudeResponse(frequency);
    } else {
        eqDb = parametricEQ_.getTotalMagnitudeResponse(frequency);
    }
    return eqDb + preamp_.getGainDb();
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
    preamp_.reset();
    limiter_.reset();
}

} // namespace dsp
