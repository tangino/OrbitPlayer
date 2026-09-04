#include <iostream>
#include <cassert>
#include <vector>
#include <cmath>
#include "../../main/cpp/dsp/BiquadFilter.h"
#include "../../main/cpp/dsp/GraphicEQ.h"
#include "../../main/cpp/dsp/ParametricEQ.h"
#include "../../main/cpp/dsp/BassBoost.h"
#include "../../main/cpp/dsp/Preamp.h"
#include "../../main/cpp/dsp/Compressor.h"
#include "../../main/cpp/dsp/Limiter.h"
#include "../../main/cpp/dsp/FFTAnalyzer.h"
#include "../../main/cpp/dsp/DSPChain.h"

using namespace dsp;

void testBiquadFilter() {
    std::cout << "Testing BiquadFilter..." << std::endl;
    BiquadFilter filter;
    filter.configure(FilterType::PEAKING, 1000.0f, 6.0f, 1.414f, 44100.0f);
    
    float magAtCenter = filter.getMagnitudeResponse(1000.0f, 44100.0f);
    assert(std::abs(magAtCenter - 6.0f) < 0.2f);

    float magFar = filter.getMagnitudeResponse(100.0f, 44100.0f);
    assert(std::abs(magFar) < 0.5f);
    std::cout << "BiquadFilter tests passed!" << std::endl;
}

void testGraphicEQ() {
    std::cout << "Testing GraphicEQ..." << std::endl;
    GraphicEQ eq(GraphicEQMode::BAND_10);
    assert(eq.getBandCount() == 10);
    
    eq.setBandGain(5, 5.0f);
    assert(eq.getBandGain(5) == 5.0f);

    std::vector<float> buffer(512 * 2, 0.5f);
    eq.processStereo(buffer.data(), 512);
    for (float s : buffer) {
        assert(!std::isnan(s) && !std::isinf(s));
    }
    std::cout << "GraphicEQ tests passed!" << std::endl;
}

void testBassBoost() {
    std::cout << "Testing BassBoost..." << std::endl;
    BassBoost bb(44100.0f);
    bb.setEnabled(true);
    bb.setStrength(0.8f);
    bb.setCenterFrequency(80.0f);

    std::vector<float> buffer(512 * 2, 0.2f);
    bb.processStereo(buffer.data(), 512);
    for (float s : buffer) {
        assert(!std::isnan(s) && !std::isinf(s));
    }
    std::cout << "BassBoost tests passed!" << std::endl;
}

void testCompressor() {
    std::cout << "Testing Dynamic Compressor..." << std::endl;
    Compressor comp(44100.0f);
    comp.setEnabled(true);
    comp.setThresholdDb(-12.0f);
    comp.setRatio(4.0f);
    comp.setAttackTimeMs(1.0f);
    comp.setReleaseTimeMs(50.0f);
    comp.setMakeupGainDb(0.0f);

    // 0dB 大信号 (1.0f) 经过 -12dB 阈值和 4:1 压缩
    std::vector<float> buffer(1024 * 2, 1.0f);
    comp.processStereo(buffer.data(), 1024);

    // 验证压缩后样本输出有明显的 Gain Reduction
    float lastSample = buffer[buffer.size() - 1];
    assert(lastSample < 1.0f);
    std::cout << "Compressor tests passed! Output reduced to: " << lastSample << std::endl;
}

void testPreampAndLimiter() {
    std::cout << "Testing Preamp and Limiter..." << std::endl;
    Preamp preamp;
    preamp.setGainDb(6.0f);

    Limiter limiter(44100.0f);
    limiter.setEnabled(true);
    limiter.setThresholdDb(-0.2f);

    std::vector<float> buffer(1024 * 2, 0.8f);
    preamp.processStereo(buffer.data(), 1024);
    assert(preamp.isClippingAndReset() == true);

    limiter.processStereo(buffer.data(), 1024);
    for (float s : buffer) {
        assert(std::abs(s) <= 1.0f);
    }
    std::cout << "Preamp & Limiter tests passed!" << std::endl;
}

void testDSPChainComplete() {
    std::cout << "Testing Full DSPChain Pipeline..." << std::endl;
    DSPChain chain;
    chain.setSampleRate(44100.0f);
    chain.setGraphicMode(GraphicEQMode::BAND_10);
    chain.setGraphicBandGain(0, 4.0f);
    chain.setBassBoostEnabled(true);
    chain.setBassBoostStrength(0.5f);
    chain.setPreampGainDb(-1.0f);
    chain.setCompressorEnabled(true);
    chain.setLimiterEnabled(true);

    std::vector<float> buffer(512 * 2, 0.3f);
    chain.process(buffer.data(), 512);

    for (float s : buffer) {
        assert(!std::isnan(s) && !std::isinf(s));
        assert(std::abs(s) <= 1.0f);
    }

    float spectrum[32];
    chain.getSpectrum(spectrum, 32);
    for (int i = 0; i < 32; ++i) {
        assert(spectrum[i] >= 0.0f && spectrum[i] <= 1.0f);
    }

    std::cout << "Full DSPChain Pipeline tests passed!" << std::endl;
}

int main() {
    std::cout << "========================================" << std::endl;
    std::cout << "Running Complete C++ DSP Unit Tests" << std::endl;
    std::cout << "========================================" << std::endl;
    testBiquadFilter();
    testGraphicEQ();
    testBassBoost();
    testCompressor();
    testPreampAndLimiter();
    testDSPChainComplete();
    std::cout << "All complete DSP unit tests PASSED successfully!" << std::endl;
    return 0;
}
