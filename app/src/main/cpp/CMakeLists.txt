cmake_minimum_required(VERSION 3.22.1)

project("music_equalizer_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    dsp
    jni
)

add_library(
    native_dsp
    SHARED
    dsp/BassBoost.cpp
    dsp/TrebleBoost.cpp
    dsp/BiquadFilter.cpp
    dsp/Compressor.cpp
    dsp/DSPChain.cpp
    dsp/FFTAnalyzer.cpp
    dsp/GraphicEQ.cpp
    dsp/Limiter.cpp
    dsp/ParametricEQ.cpp
    dsp/Preamp.cpp
    jni/native_lib.cpp
)

find_library(
    log-lib
    log
)

find_library(
    android-lib
    android
)

target_link_libraries(
    native_dsp
    ${log-lib}
    ${android-lib}
    m
)
