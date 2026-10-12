cmake_minimum_required(VERSION 3.22.1)

project("music_equalizer_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    dsp
    player
    jni
)

add_library(
    native_dsp
    SHARED
    dsp/BassBoost.cpp
    dsp/TrebleBoost.cpp
    dsp/BassLimiter.cpp
    dsp/BiquadFilter.cpp
    dsp/Compressor.cpp
    dsp/DSPChain.cpp
    dsp/FFTAnalyzer.cpp
    dsp/GraphicEQ.cpp
    dsp/Limiter.cpp
    dsp/ParametricEQ.cpp
    dsp/Preamp.cpp
    player/AudioDecoder.cpp
    player/AAudioOutput.cpp
    player/NativeAudioPipeline.cpp
    jni/native_lib.cpp
    jni/native_player_jni.cpp
)

find_library(
    log-lib
    log
)

find_library(
    android-lib
    android
)

find_library(
    mediandk-lib
    mediandk
)

target_link_libraries(
    native_dsp
    ${log-lib}
    ${android-lib}
    ${mediandk-lib}
    dl
    m
)

target_link_options(
    native_dsp
    PRIVATE
    "-Wl,-z,max-page-size=16384"
)
