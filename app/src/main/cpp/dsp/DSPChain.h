#include "GraphicEQ.h"
#include "ParametricEQ.h"
#include "BassBoost.h"
#include "TrebleBoost.h"
#include "BassLimiter.h"
#include "Preamp.h"
#include "Compressor.h"
#include "Limiter.h"
#include "FFTAnalyzer.h"
#include <algorithm>
#include <mutex>
#include <vector>

namespace dsp {

enum class EQEngineMode {
    GRAPHIC = 0,
    PARAMETRIC = 1
};

class DSPChain {
public:
    DSPChain();
    ~DSPChain() = default;

    void setSampleRate(float sampleRate);
    float getSampleRate() const { return sampleRate_; }

    void setEngineMode(EQEngineMode mode);
    EQEngineMode getEngineMode() const { return engineMode_; }

    // 均衡器 (Graphic/Parametric EQ) 独立开关
    void setEnabled(bool enabled);
    bool isEnabled() const { return equalizerEnabled_; }

    // Graphic EQ 操作
    void setGraphicMode(GraphicEQMode mode);
    void setGraphicBandGain(int bandIndex, float gainDb);
    float getGraphicBandGain(int bandIndex) const;
    const std::vector<float>& getGraphicFrequencies() const;

    // Parametric EQ 操作
    void setParametricBands(const std::vector<BandConfig>& configs);
    void setParametricBandGain(int bandIndex, float gainDb);
    void setParametricBandFrequency(int bandIndex, float freq);
    void setParametricBandQ(int bandIndex, float q);

    // Bass Boost 操作
    void setBassBoostEnabled(bool enabled);
    bool isBassBoostEnabled() const;
    void setBassBoostStrength(float strength);

    // Treble Boost 操作
    void setTrebleBoostEnabled(bool enabled);
    bool isTrebleBoostEnabled() const;
    void setTrebleBoostStrength(float strength);

    // Preamp 操作
    void setPreampGainDb(float gainDb);
    float getPreampGainDb() const;
    bool isClippingAndReset();

    // Compressor 操作
    void setCompressorEnabled(bool enabled);
    bool isCompressorEnabled() const;
    void setCompressorParams(float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupGainDb);

    // Limiter 操作
    void setLimiterEnabled(bool enabled);
    bool isLimiterEnabled() const;
    void setLimiterThresholdDb(float thresholdDb);

    // 音频核心处理（Zero Allocation / Real-Time Safe）
    void process(float* buffer, int numFrames);

    // 获取特定频点幅频响应 (包含 EQ + Preamp)
    float getMagnitudeResponse(float frequency) const;

    // 频谱与电平
    void getSpectrum(float* outBands, int numBands);
    void getPeakLevels(float& outLeftDb, float& outRightDb);

    void reset();

private:
    // 音调 (Bass/Treble) 状态变化时，联动更新分频带低频限幅器与主限幅器的衰减权限
    void updateToneDependentStages();

    static constexpr float kToneFreeHeadroomDb = 2.0f;      // 音调关闭时的默认衰减下限 (-2.0dB)
    static constexpr float kMaxLimiterReductionDb = 4.5f;   // 允许的最深衰减 (温和防削波，严禁深压致音量塌陷)

    static inline float applyMasterCeiling(float x) {
        constexpr float kLimit = 0.96f;     // 线性安全区 (-0.35 dBFS)，以下完全 100% 直通无损
        constexpr float kHeadroom = 0.039f; // 极限严格渐近收敛于 0.96 + 0.039 = 0.999f，永不突破 1.0f
        if (x > kLimit) {
            const float excess = x - kLimit;
            return kLimit + kHeadroom * (excess / (excess + kHeadroom));
        } else if (x < -kLimit) {
            const float excess = -kLimit - x;
            return -kLimit - kHeadroom * (excess / (excess + kHeadroom));
        }
        return x;
    }

    mutable std::mutex configMutex_;

    bool equalizerEnabled_ = true;
    float sampleRate_ = 44100.0f;
    EQEngineMode engineMode_ = EQEngineMode::GRAPHIC;

    GraphicEQ graphicEQ_;
    ParametricEQ parametricEQ_;
    BassBoost bassBoost_;
    TrebleBoost trebleBoost_;
    BassLimiter bassLimiter_;
    Preamp preamp_;
    Compressor compressor_;
    Limiter limiter_;
    FFTAnalyzer fftAnalyzer_;
};

} // namespace dsp
