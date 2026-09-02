#include <jni.h>
#include <android/log.h>
#include "../dsp/DSPChain.h"

#define TAG "NativeDSP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace dsp;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeCreateEngine(JNIEnv* /*env*/, jobject /*thiz*/) {
    auto* chain = new DSPChain();
    return reinterpret_cast<jlong>(chain);
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeDestroyEngine(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle != 0) {
        auto* chain = reinterpret_cast<DSPChain*>(handle);
        delete chain;
    }
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeSetSampleRate(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jfloat sampleRate) {
    if (handle != 0) {
        reinterpret_cast<DSPChain*>(handle)->setSampleRate(sampleRate);
    }
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeSetEnabled(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jboolean enabled) {
    if (handle != 0) {
        reinterpret_cast<DSPChain*>(handle)->setEnabled(enabled);
    }
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeSetEngineMode(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint mode) {
    if (handle != 0) {
        reinterpret_cast<DSPChain*>(handle)->setEngineMode(static_cast<EQEngineMode>(mode));
    }
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeSetGraphicMode(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint numBands) {
    if (handle != 0) {
        GraphicEQMode mode = GraphicEQMode::BAND_10;
        if (numBands == 15) mode = GraphicEQMode::BAND_15;
        else if (numBands == 20) mode = GraphicEQMode::BAND_20;
        else if (numBands == 31) mode = GraphicEQMode::BAND_31;
        reinterpret_cast<DSPChain*>(handle)->setGraphicMode(mode);
    }
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeSetGraphicBandGain(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint bandIndex, jfloat gainDb) {
    if (handle != 0) {
        reinterpret_cast<DSPChain*>(handle)->setGraphicBandGain(bandIndex, gainDb);
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeGetGraphicFrequencies(JNIEnv* env, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return nullptr;
    const auto& freqs = reinterpret_cast<DSPChain*>(handle)->getGraphicFrequencies();
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(freqs.size()));
    if (result != nullptr && !freqs.empty()) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(freqs.size()), freqs.data());
    }
    return result;
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeSetParametricBand(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle,
    jint bandIndex, jboolean enabled, jint filterType, jfloat frequency, jfloat gainDb, jfloat q) {
    if (handle != 0) {
        auto* chain = reinterpret_cast<DSPChain*>(handle);
        chain->setParametricBandFrequency(bandIndex, frequency);
        chain->setParametricBandGain(bandIndex, gainDb);
        chain->setParametricBandQ(bandIndex, q);
    }
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeSetPreampGain(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jfloat gainDb) {
    if (handle != 0) {
        reinterpret_cast<DSPChain*>(handle)->setPreampGainDb(gainDb);
    }
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeSetBassBoost(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jboolean enabled, jfloat strength) {
    if (handle != 0) {
        auto* chain = reinterpret_cast<DSPChain*>(handle);
        chain->setBassBoostEnabled(enabled);
        chain->setBassBoostStrength(strength);
    }
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeSetTrebleBoost(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jboolean enabled, jfloat strength) {
    if (handle != 0) {
        auto* chain = reinterpret_cast<DSPChain*>(handle);
        chain->setTrebleBoostEnabled(enabled);
        chain->setTrebleBoostStrength(strength);
    }
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeSetCompressor(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle,
    jboolean enabled, jfloat thresholdDb, jfloat ratio, jfloat attackMs, jfloat releaseMs, jfloat makeupGainDb) {
    if (handle != 0) {
        auto* chain = reinterpret_cast<DSPChain*>(handle);
        chain->setCompressorEnabled(enabled);
        chain->setCompressorParams(thresholdDb, ratio, attackMs, releaseMs, makeupGainDb);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeIsClipping(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle != 0) {
        return reinterpret_cast<DSPChain*>(handle)->isClippingAndReset();
    }
    return JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeSetLimiter(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jboolean enabled, jfloat thresholdDb) {
    if (handle != 0) {
        auto* chain = reinterpret_cast<DSPChain*>(handle);
        chain->setLimiterEnabled(enabled);
        chain->setLimiterThresholdDb(thresholdDb);
    }
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeProcessBuffer(JNIEnv* env, jobject /*thiz*/, jlong handle, jfloatArray buffer, jint numFrames) {
    if (handle != 0 && buffer != nullptr) {
        jfloat* bufPtr = env->GetFloatArrayElements(buffer, nullptr);
        if (bufPtr != nullptr) {
            reinterpret_cast<DSPChain*>(handle)->process(bufPtr, numFrames);
            env->ReleaseFloatArrayElements(buffer, bufPtr, 0);
        }
    }
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeGetCurve(
    JNIEnv* env, jobject /*thiz*/, jlong handle, jfloatArray freqPoints, jfloatArray outMagnitudes) {
    if (handle != 0 && freqPoints != nullptr && outMagnitudes != nullptr) {
        jsize count = env->GetArrayLength(freqPoints);
        jfloat* freqs = env->GetFloatArrayElements(freqPoints, nullptr);
        jfloat* outMags = env->GetFloatArrayElements(outMagnitudes, nullptr);

        if (freqs != nullptr && outMags != nullptr) {
            auto* chain = reinterpret_cast<DSPChain*>(handle);
            for (int i = 0; i < count; ++i) {
                outMags[i] = chain->getMagnitudeResponse(freqs[i]);
            }
        }

        if (freqs) env->ReleaseFloatArrayElements(freqPoints, freqs, JNI_ABORT);
        if (outMags) env->ReleaseFloatArrayElements(outMagnitudes, outMags, 0);
    }
}

JNIEXPORT void JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeGetSpectrum(JNIEnv* env, jobject /*thiz*/, jlong handle, jfloatArray outBands) {
    if (handle != 0 && outBands != nullptr) {
        jsize count = env->GetArrayLength(outBands);
        jfloat* bands = env->GetFloatArrayElements(outBands, nullptr);
        if (bands != nullptr) {
            reinterpret_cast<DSPChain*>(handle)->getSpectrum(bands, count);
            env->ReleaseFloatArrayElements(outBands, bands, 0);
        }
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_antigravity_equalizer_native_NativeDSP_nativeGetPeakLevels(JNIEnv* env, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return nullptr;
    float l = -60.0f, r = -60.0f;
    reinterpret_cast<DSPChain*>(handle)->getPeakLevels(l, r);
    jfloatArray result = env->NewFloatArray(2);
    if (result != nullptr) {
        jfloat levels[2] = {l, r};
        env->SetFloatArrayRegion(result, 0, 2, levels);
    }
    return result;
}

} // extern "C"
