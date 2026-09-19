cmake_minimum_required(VERSION 3.22)
project(jarvis_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Android NDK toolchain
set(CMAKE_ANDROID_NDK_TOOLCHAIN_VERSION "clang")

# ------------------------------------------------------------------
# Find ONNX Runtime (prebuilt in jniLibs)
# ------------------------------------------------------------------
find_library(onnxruntime_lib onnxruntime
    PATHS ${CMAKE_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI}
    NO_DEFAULT_PATH
)

if(NOT onnxruntime_lib)
    message(WARNING "ONNX Runtime not found in jniLibs/${ANDROID_ABI}")
endif()

# ------------------------------------------------------------------
# llama.cpp subdirectory (vendored or fetched)
# We assume llama.cpp source is available at ${CMAKE_SOURCE_DIR}/../../../../llama.cpp
# (relative to mobile/app/src/main/cpp -> /mnt/sdcard/llama.cpp)
# ------------------------------------------------------------------
set(LLAMA_CPP_SOURCE_DIR "${CMAKE_SOURCE_DIR}/../../../../llama.cpp" CACHE PATH "Path to llama.cpp source")

if(NOT EXISTS "${LLAMA_CPP_SOURCE_DIR}/CMakeLists.txt")
    message(WARNING "llama.cpp not found at ${LLAMA_CPP_SOURCE_DIR}. JNI build will fail.")
endif()

# Build llama.cpp as static library
if(EXISTS "${LLAMA_CPP_SOURCE_DIR}/CMakeLists.txt")
    add_subdirectory(${LLAMA_CPP_SOURCE_DIR} ${CMAKE_BINARY_DIR}/llama.cpp EXCLUDE_FROM_ALL)
endif()

# ------------------------------------------------------------------
# JNI wrapper library: LocalInferenceEngine (llama.cpp GGUF)
# ------------------------------------------------------------------
if(EXISTS "${LLAMA_CPP_SOURCE_DIR}/CMakeLists.txt")
    add_library(jarvis_llama_jni SHARED
        llama_jni.cpp
        local_inference_engine.cpp
        tokenizer_wrapper.cpp
    )

    target_link_libraries(jarvis_llama_jni
        PRIVATE
            llama
            ggml
            ggml-cpu
            ${CMAKE_DL_LIBS}
            ${log_lib}
            android
    )

    target_include_directories(jarvis_llama_jni
        PRIVATE
            ${LLAMA_CPP_SOURCE_DIR}/include
            ${LLAMA_CPP_SOURCE_DIR}/ggml/include
            ${LLAMA_CPP_SOURCE_DIR}/ggml/src
            ${CMAKE_SOURCE_DIR}
    )

    if(ANDROID)
        target_compile_options(jarvis_llama_jni PRIVATE
            -DGGML_USE_NEON=ON
            -DGGML_USE_ARM_FP16=ON
            -DGGML_USE_ACCELERATE=OFF
            -DGGML_USE_METAL=OFF
            -DGGML_USE_CUDA=OFF
            -DGGML_USE_VULKAN=OFF
            -DGGML_USE_OPENCL=OFF
            -DGGML_USE_SYCL=OFF
            -DGGML_USE_RPC=OFF
            -DGGML_USE_MPI=OFF
            -fvisibility=hidden
        )
        find_library(log_lib log)
        target_link_libraries(jarvis_llama_jni ${log_lib})
    endif()
endif()

# ------------------------------------------------------------------
# JNI wrapper library: Silero VAD (ONNX Runtime)
# ------------------------------------------------------------------
if(onnxruntime_lib)
    add_library(jarvis_silero_vad SHARED
        silero_vad/silero_vad_jni.cpp
        silero_vad/silero_vad_engine.cpp
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
            ${CMAKE_SOURCE_DIR}/silero_vad
    )

    if(ANDROID)
        target_compile_options(jarvis_silero_vad PRIVATE
            -fvisibility=hidden
            -DORT_API_VERSION=1.16
        )
        find_library(log_lib log)
        target_link_libraries(jarvis_silero_vad ${log_lib})
    endif()
endif()

# ------------------------------------------------------------------
# Install headers
# ------------------------------------------------------------------
install(FILES
    ${CMAKE_SOURCE_DIR}/llama_jni.h
    DESTINATION include
)

if(EXISTS "${CMAKE_SOURCE_DIR}/silero_vad/silero_vad_jni.h")
    install(FILES
        ${CMAKE_SOURCE_DIR}/silero_vad/silero_vad_jni.h
        DESTINATION include
    )
endif()