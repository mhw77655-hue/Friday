cmake_minimum_required(VERSION 3.22)
project(jarvis_native LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Friday owns its offline inference runtime. Fetch a pinned llama.cpp source
# revision at build time; never assume /sdcard/llama.cpp, Termux, Ollama or a
# localhost inference server exists on the user's phone.
include(FetchContent)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG b2960
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(llama_cpp)

find_library(log_lib log REQUIRED)
add_library(jarvis_llama_jni SHARED llama_jni.cpp local_inference_engine.cpp tokenizer_wrapper.cpp)
target_link_libraries(jarvis_llama_jni PRIVATE llama ggml ggml-cpu ${CMAKE_DL_LIBS} ${log_lib} android)
target_include_directories(jarvis_llama_jni PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
    ${llama_cpp_SOURCE_DIR}/ggml/src
    ${CMAKE_SOURCE_DIR}
)
target_compile_options(jarvis_llama_jni PRIVATE -fvisibility=hidden)

# Build the optional standalone Silero JNI bridge only when a matching
# ONNX Runtime library is supplied for this ABI.
find_library(onnxruntime_lib onnxruntime PATHS ${CMAKE_SOURCE_DIR}/../../jniLibs/${ANDROID_ABI} NO_DEFAULT_PATH)
if(onnxruntime_lib)
    add_library(jarvis_silero_vad SHARED silero_vad/silero_vad_jni.cpp silero_vad/silero_vad_engine.cpp)
    target_link_libraries(jarvis_silero_vad PRIVATE ${onnxruntime_lib} ${log_lib} android)
    target_include_directories(jarvis_silero_vad PRIVATE ${CMAKE_SOURCE_DIR} ${CMAKE_SOURCE_DIR}/silero_vad)
    target_compile_options(jarvis_silero_vad PRIVATE -fvisibility=hidden)
endif()
