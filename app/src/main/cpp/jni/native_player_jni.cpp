#include <jni.h>
#include <android/log.h>
#include "../player/NativeAudioPipeline.h"

#define TAG "NativePlayerJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace player;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativeCreatePlayer(JNIEnv* /*env*/, jobject /*thiz*/) {
    auto* pipeline = new NativeAudioPipeline();
    return reinterpret_cast<jlong>(pipeline);
}

JNIEXPORT void JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativeDestroyPlayer(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle != 0) {
        auto* pipeline = reinterpret_cast<NativeAudioPipeline*>(handle);
        delete pipeline;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativeOpenPath(JNIEnv* env, jobject /*thiz*/, jlong handle, jstring path) {
    if (handle == 0 || path == nullptr) return JNI_FALSE;

    const char* nativePath = env->GetStringUTFChars(path, nullptr);
    if (!nativePath) return JNI_FALSE;

    auto* pipeline = reinterpret_cast<NativeAudioPipeline*>(handle);
    bool success = pipeline->open(nativePath);

    env->ReleaseStringUTFChars(path, nativePath);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativeOpenFd(
    JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint fd, jlong offset, jlong length) {
    if (handle == 0 || fd < 0) return JNI_FALSE;

    auto* pipeline = reinterpret_cast<NativeAudioPipeline*>(handle);
    bool success = pipeline->openFd(fd, offset, length);
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativePlay(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return JNI_FALSE;
    auto* pipeline = reinterpret_cast<NativeAudioPipeline*>(handle);
    return pipeline->play() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativePause(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return JNI_FALSE;
    auto* pipeline = reinterpret_cast<NativeAudioPipeline*>(handle);
    return pipeline->pause() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativeSeekTo(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jlong positionMs) {
    if (handle == 0) return JNI_FALSE;
    auto* pipeline = reinterpret_cast<NativeAudioPipeline*>(handle);
    return pipeline->seekTo(positionMs) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativeStop(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return JNI_FALSE;
    auto* pipeline = reinterpret_cast<NativeAudioPipeline*>(handle);
    return pipeline->stop() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativeReset(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle != 0) {
        reinterpret_cast<NativeAudioPipeline*>(handle)->reset();
    }
}

JNIEXPORT void JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativeSetVolume(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jfloat volume) {
    if (handle != 0) {
        reinterpret_cast<NativeAudioPipeline*>(handle)->setVolume(volume);
    }
}

JNIEXPORT jlong JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativeGetDuration(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return 0;
    return reinterpret_cast<NativeAudioPipeline*>(handle)->getDurationMs();
}

JNIEXPORT jlong JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativeGetPosition(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return 0;
    return reinterpret_cast<NativeAudioPipeline*>(handle)->getPositionMs();
}

JNIEXPORT jint JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativeGetState(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return 0;
    return static_cast<jint>(reinterpret_cast<NativeAudioPipeline*>(handle)->getState());
}

JNIEXPORT jlong JNICALL
Java_com_orbit_music_native_NativeAudioPlayerEngine_nativeGetDSPChainHandle(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return 0;
    auto* dspChain = reinterpret_cast<NativeAudioPipeline*>(handle)->getDSPChain();
    return reinterpret_cast<jlong>(dspChain);
}

} // extern "C"
