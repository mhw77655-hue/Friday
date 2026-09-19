cmake_minimum_required(VERSION 3.22)
project(jarvis_silero_vad LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ------------------------------------------------------------------
# ONNX Runtime
# ------------------------------------------------------------------
# We link against the prebuilt libonnxruntime.so in jniLibs
find_library(onnxruntime_lib onnxruntime
    PATHS ${CMAKE_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI}
    NO_DEFAULT_PATH
)

if(NOT onnxruntime_lib)
    message(FATAL_ERROR "ONNX Runtime not found in jniLibs/${ANDROID_ABI}")
endif()

# ------------------------------------------------------------------
# Silero VAD JNI wrapper
# ------------------------------------------------------------------
add_library(jarvis_silero_vad SHARED
    silero_vad_jni.cpp
    silero_vad_engine.cpp
)

target_link_libraries(jarvis_silero_vad
    PRIVATE
        ${onnxruntime_lib}
        ${log_lib}
        android
)

target_include_directories(jarvis_silero_vad
    PRIVATE
        ${CMAKE_SOURCE_DIR}
        ${CMAKE_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI}/include
)

if(ANDROID)
    target_compile_options(jarvis_silero_vad PRIVATE
        -fvisibility=hidden
        -DORT_API_VERSION=1.16
    )
    find_library(log_lib log)
    target_link_libraries(jarvis_silero_vad ${log_lib})
endif()