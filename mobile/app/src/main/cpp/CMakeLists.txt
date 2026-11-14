cmake_minimum_required(VERSION 3.22)
project(friday_native LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT ANDROID)
    message(FATAL_ERROR "Friday native inference must be built with the Android NDK.")
endif()

# The workflow fetches this exact source revision into mobile/llama.cpp.
# Fail the build if it is absent; never ship an APK with a missing LLM runtime.
set(LLAMA_CPP_SOURCE_DIR "${CMAKE_SOURCE_DIR}/../../../../llama.cpp" CACHE PATH "Pinned llama.cpp source")
if(NOT EXISTS "${LLAMA_CPP_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "Pinned llama.cpp source missing at ${LLAMA_CPP_SOURCE_DIR}; local inference cannot be packaged.")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_STATIC ON CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_SYCL OFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
add_subdirectory("${LLAMA_CPP_SOURCE_DIR}" "${CMAKE_BINARY_DIR}/llama.cpp" EXCLUDE_FROM_ALL)

find_library(log_lib log REQUIRED)
add_library(jarvis_llama_jni SHARED llama_jni.cpp local_inference_engine.cpp)
target_include_directories(jarvis_llama_jni PRIVATE
    "${LLAMA_CPP_SOURCE_DIR}/include"
    "${LLAMA_CPP_SOURCE_DIR}/ggml/include"
    "${CMAKE_SOURCE_DIR}"
)
target_link_libraries(jarvis_llama_jni PRIVATE llama ${log_lib} android)
target_compile_options(jarvis_llama_jni PRIVATE -fvisibility=hidden)

# Optional VAD wrapper; speech input is not a dependency of local text inference.
find_library(onnxruntime_lib onnxruntime
    PATHS "${CMAKE_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI}"
    NO_DEFAULT_PATH
)
if(onnxruntime_lib)
    add_library(jarvis_silero_vad SHARED
        silero_vad/silero_vad_jni.cpp
        silero_vad/silero_vad_engine.cpp
    )
    target_include_directories(jarvis_silero_vad PRIVATE "${CMAKE_SOURCE_DIR}" "${CMAKE_SOURCE_DIR}/silero_vad")
    target_link_libraries(jarvis_silero_vad PRIVATE ${onnxruntime_lib} ${log_lib} android)
    target_compile_options(jarvis_silero_vad PRIVATE -fvisibility=hidden -DORT_API_VERSION=1.16)
endif()

install(FILES "${CMAKE_SOURCE_DIR}/llama_jni.h" DESTINATION include)
