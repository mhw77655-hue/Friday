/**
 * Silero VAD Engine Implementation
 * ONNX Runtime-based Voice Activity Detection
 */
#include "silero_vad_jni.h"
#include <onnxruntime_cxx_api.h>
#include <vector>
#include <mutex>
#include <atomic>
#include <chrono>
#include <cmath>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "SileroVadEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// ------------------------------------------------------------------
// Internal structures
// ------------------------------------------------------------------
struct SileroVadEngine {
    Ort::Env env;
    Ort::Session* session = nullptr;
    Ort::SessionOptions session_options;
    Ort::MemoryInfo memory_info;

    SileroVadConfig config;

    // Model I/O
    std::vector<int64_t> input_shape = {1, 1, 512};  // batch, channels, samples
    std::vector<int64_t> output_shape = {1, 1};      // batch, probability
    std::vector<float> input_buffer;
    std::vector<float> output_buffer;

    // State
    std::atomic<SileroVadState> state{SILERO_VAD_STATE_IDLE};
    std::atomic<float> last_speech_prob{0.0f};
    std::mutex mutex;
    std::string last_error;

    // Audio buffering for windowed processing
    std::vector<int16_t> audio_buffer;
    int window_size_samples = 512;
    int hop_size_samples = 256;  // 50% overlap

    // Speech detection state machine
    int speech_frames = 0;
    int silence_frames = 0;
    int min_speech_frames = 0;
    int min_silence_frames = 0;
    bool in_speech = false;
    int64_t speech_start_ts = 0;
    int64_t last_ts = 0;

    // Callbacks
    SileroVadSpeechStartCallback on_speech_start = nullptr;
    SileroVadSpeechEndCallback on_speech_end = nullptr;
    void* callback_user_data = nullptr;

    // Metrics
    int64_t total_chunks = 0;
    int64_t speech_chunks = 0;
    int64_t silence_chunks = 0;
    float avg_speech_prob = 0.0f;
    float avg_inference_ms = 0.0f;
    int64_t peak_ram = 0;
    std::chrono::steady_clock::time_point start_time;

    SileroVadEngine() : env(ORT_LOGGING_LEVEL_WARNING, "SileroVAD"),
                        memory_info(Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault)) {
        start_time = std::chrono::steady_clock::now();
    }

    ~SileroVadEngine() {
        if (session) {
            delete session;
            session = nullptr;
        }
    }
};

// ------------------------------------------------------------------
// Helper functions
// ------------------------------------------------------------------
static int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

static float compute_rms(const int16_t* audio, int num_samples) {
    if (num_samples <= 0) return 0.0f;
    float sum = 0.0f;
    for (int i = 0; i < num_samples; i++) {
        float v = audio[i] / 32768.0f;
        sum += v * v;
    }
    return sqrtf(sum / num_samples) * 32767.0f;
}

static void update_detection_state(SileroVadEngine* engine, float speech_prob, int64_t timestamp_ms) {
    bool speech = speech_prob > engine->config.threshold;

    if (speech && !engine->in_speech) {
        engine->speech_frames++;
        if (engine->speech_frames >= engine->min_speech_frames) {
            engine->in_speech = true;
            engine->state = SILERO_VAD_STATE_SPEECH;
            engine->speech_start_ts = timestamp_ms;
            engine->speech_chunks++;
            if (engine->on_speech_start) {
                engine->on_speech_start(timestamp_ms, engine->callback_user_data);
            }
            LOGI("Speech START at %lld ms (prob=%.3f)", timestamp_ms, speech_prob);
        }
    } else if (!speech && engine->in_speech) {
        engine->silence_frames++;
        if (engine->silence_frames >= engine->min_silence_frames) {
            engine->in_speech = false;
            engine->state = SILERO_VAD_STATE_IDLE;
            int duration = timestamp_ms - engine->speech_start_ts;
            if (engine->on_speech_end) {
                engine->on_speech_end(timestamp_ms, duration, engine->callback_user_data);
            }
            LOGI("Speech END at %lld ms, duration=%d ms", timestamp_ms, duration);
            engine->speech_frames = 0;
            engine->silence_frames = 0;
        }
    } else if (speech && engine->in_speech) {
        engine->silence_frames = 0;
    } else if (!speech && !engine->in_speech) {
        engine->speech_frames = 0;
        engine->silence_chunks++;
    }
}

// ------------------------------------------------------------------
// JNI Implementation
// ------------------------------------------------------------------
extern "C" {

SileroVadEngine* silero_vad_create(const SileroVadConfig* config) {
    if (!config || !config->model_path) {
        LOGE("Invalid config or model path");
        return nullptr;
    }

    auto* engine = new SileroVadEngine();
    engine->config = *config;
    engine->window_size_samples = config->window_size_samples > 0 ? config->window_size_samples : 512;
    engine->hop_size_samples = engine->window_size_samples / 2;
    engine->input_buffer.resize(engine->window_size_samples);
    engine->output_buffer.resize(1);
    engine->audio_buffer.reserve(engine->window_size_samples * 2);

    // Calculate frame thresholds
    int frame_duration_ms = (engine->window_size_samples * 1000) / config->sample_rate;
    engine->min_speech_frames = std::max(1, config->min_speech_duration_ms / frame_duration_ms);
    engine->min_silence_frames = std::max(1, config->min_silence_duration_ms / frame_duration_ms);

    // Configure ONNX session
    engine->session_options.SetIntraOpNumThreads(config->num_threads > 0 ? config->num_threads : 2);
    engine->session_options.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);

    // Try to use CPU EP (Vulkan/GPU can be added later)
    Ort::ThrowOnError(OrtSessionOptionsAppendExecutionProvider_CPU(engine->session_options, 0));

    try {
        engine->session = new Ort::Session(engine->env, config->model_path, engine->session_options);
        LOGI("Silero VAD model loaded from %s", config->model_path);

        // Verify input/output
        size_t num_inputs = engine->session->GetInputCount();
        size_t num_outputs = engine->session->GetOutputCount();
        LOGI("Model inputs: %zu, outputs: %zu", num_inputs, num_outputs);

        // Get input name
        Ort::AllocatorWithDefaultOptions allocator;
        const char* input_name = engine->session->GetInputNameAllocated(0, allocator).get();
        const char* output_name = engine->session->GetOutputNameAllocated(0, allocator).get();
        LOGI("Input: %s, Output: %s", input_name, output_name);

    } catch (const Ort::Exception& e) {
        engine->last_error = std::string("ONNX session creation failed: ") + e.what();
        LOGE("%s", engine->last_error.c_str());
        delete engine;
        return nullptr;
    }

    engine->state = SILERO_VAD_STATE_IDLE;
    return engine;
}

void silero_vad_destroy(SileroVadEngine* engine) {
    if (engine) delete engine;
}

SileroVadState silero_vad_get_state(SileroVadEngine* engine) {
    if (!engine) return SILERO_VAD_STATE_FAILED;
    return engine->state.load();
}

SileroVadResult silero_vad_process(SileroVadEngine* engine, const int16_t* audio, int num_samples, int64_t timestamp_ms) {
    SileroVadResult result{};
    result.state = SILERO_VAD_STATE_FAILED;
    result.timestamp_ms = timestamp_ms;
    result.audio_level = 0;

    if (!engine || !audio || num_samples <= 0) {
        result.error_message = "Invalid parameters";
        return result;
    }

    if (engine->state == SILERO_VAD_STATE_FAILED) {
        result.error_message = engine->last_error.c_str();
        return result;
    }

    auto infer_start = std::chrono::steady_clock::now();

    // Compute audio level
    result.audio_level = static_cast<int>(compute_rms(audio, num_samples));

    // Add to buffer
    engine->audio_buffer.insert(engine->audio_buffer.end(), audio, audio + num_samples);

    // Process complete windows
    float speech_prob = 0.0f;
    int windows_processed = 0;

    while (engine->audio_buffer.size() >= engine->window_size_samples) {
        // Copy window to input buffer (normalize to [-1, 1])
        for (int i = 0; i < engine->window_size_samples; i++) {
            engine->input_buffer[i] = engine->audio_buffer[i] / 32768.0f;
        }

        // Create input tensor
        std::vector<int64_t> input_shape = {1, 1, static_cast<int64_t>(engine->window_size_samples)};
        Ort::Value input_tensor = Ort::Value::CreateTensor<float>(
            engine->memory_info,
            engine->input_buffer.data(),
            engine->input_buffer.size(),
            input_shape.data(),
            input_shape.size()
        );

        // Run inference
        try {
            const char* input_names[] = {"input"};
            const char* output_names[] = {"output"};

            auto output_tensors = engine->session->Run(
                Ort::RunOptions{nullptr},
                input_names, &input_tensor, 1,
                output_names, 1
            );

            float* output_data = output_tensors[0].GetTensorMutableData<float>();
            speech_prob = output_data[0];
            windows_processed++;

        } catch (const Ort::Exception& e) {
            engine->last_error = std::string("Inference failed: ") + e.what();
            engine->state = SILERO_VAD_STATE_FAILED;
            result.error_message = engine->last_error.c_str();
            return result;
        }

        // Remove processed samples (keep overlap)
        engine->audio_buffer.erase(
            engine->audio_buffer.begin(),
            engine->audio_buffer.begin() + engine->hop_size_samples
        );
    }

    // Average probability across windows
    if (windows_processed > 0) {
        engine->last_speech_prob = speech_prob;
        result.speech_probability = speech_prob;
        result.speech_detected = speech_prob > engine->config.threshold;

        // Update state machine
        update_detection_state(engine, speech_prob, timestamp_ms);
        result.state = engine->state.load();

        // Update metrics
        engine->total_chunks++;
        engine->avg_speech_prob = (engine->avg_speech_prob * (engine->total_chunks - 1) + speech_prob) / engine->total_chunks;
    } else {
        result.speech_probability = engine->last_speech_prob;
        result.speech_detected = false;
        result.state = engine->state.load();
    }

    // Update inference time metric
    auto infer_time = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now() - infer_start).count() / 1000.0f;
    engine->avg_inference_ms = (engine->avg_inference_ms * (engine->total_chunks - 1) + infer_time) / std::max<int64_t>(1, engine->total_chunks);

    return result;
}

void silero_vad_process_with_callbacks(
    SileroVadEngine* engine,
    const int16_t* audio,
    int num_samples,
    int64_t timestamp_ms,
    SileroVadSpeechStartCallback on_speech_start,
    SileroVadSpeechEndCallback on_speech_end,
    void* user_data
) {
    if (!engine) return;

    engine->on_speech_start = on_speech_start;
    engine->on_speech_end = on_speech_end;
    engine->callback_user_data = user_data;

    silero_vad_process(engine, audio, num_samples, timestamp_ms);
}

void silero_vad_reset(SileroVadEngine* engine) {
    if (!engine) return;

    std::lock_guard<std::mutex> lock(engine->mutex);
    engine->audio_buffer.clear();
    engine->speech_frames = 0;
    engine->silence_frames = 0;
    engine->in_speech = false;
    engine->speech_start_ts = 0;
    engine->state = SILERO_VAD_STATE_IDLE;
    engine->last_speech_prob = 0.0f;
    LOGI("VAD state reset");
}

bool silero_vad_update_config(SileroVadEngine* engine, const SileroVadConfig* config) {
    if (!engine || !config) return false;

    std::lock_guard<std::mutex> lock(engine->mutex);
    engine->config = *config;
    engine->window_size_samples = config->window_size_samples > 0 ? config->window_size_samples : 512;
    engine->hop_size_samples = engine->window_size_samples / 2;
    engine->input_buffer.resize(engine->window_size_samples);

    int frame_duration_ms = (engine->window_size_samples * 1000) / config->sample_rate;
    engine->min_speech_frames = std::max(1, config->min_speech_duration_ms / frame_duration_ms);
    engine->min_silence_frames = std::max(1, config->min_silence_duration_ms / frame_duration_ms);

    LOGI("VAD config updated: threshold=%.2f, min_speech=%dms, min_silence=%dms",
         config->threshold, config->min_speech_duration_ms, config->min_silence_duration_ms);
    return true;
}

SileroVadConfig silero_vad_get_config(SileroVadEngine* engine) {
    SileroVadConfig config{};
    if (engine) {
        std::lock_guard<std::mutex> lock(engine->mutex);
        config = engine->config;
    }
    return config;
}

// ------------------------------------------------------------------
// Environmental profiles
// ------------------------------------------------------------------
bool silero_vad_apply_profile(SileroVadEngine* engine, SileroVadProfile profile) {
    if (!engine) return false;

    SileroVadConfig config = silero_vad_get_profile_config(profile, engine->config.sample_rate);
    return silero_vad_update_config(engine, &config);
}

SileroVadConfig silero_vad_get_profile_config(SileroVadProfile profile, int sample_rate) {
    SileroVadConfig config{};
    config.sample_rate = sample_rate;
    config.window_size_samples = 512;
    config.use_gpu = false;
    config.num_threads = 2;

    switch (profile) {
        case SILERO_PROFILE_QUIET:
            config.threshold = 0.3f;
            config.min_speech_duration_ms = 100;
            config.min_silence_duration_ms = 300;
            config.speech_pad_ms = 100;
            break;
        case SILERO_PROFILE_NORMAL:
            config.threshold = 0.5f;
            config.min_speech_duration_ms = 150;
            config.min_silence_duration_ms = 500;
            config.speech_pad_ms = 200;
            break;
        case SILERO_PROFILE_NOISY:
            config.threshold = 0.6f;
            config.min_speech_duration_ms = 200;
            config.min_silence_duration_ms = 800;
            config.speech_pad_ms = 300;
            break;
        case SILERO_PROFILE_VEHICLE:
            config.threshold = 0.55f;
            config.min_speech_duration_ms = 250;
            config.min_silence_duration_ms = 1000;
            config.speech_pad_ms = 400;
            break;
        case SILERO_PROFILE_OUTDOOR:
            config.threshold = 0.5f;
            config.min_speech_duration_ms = 200;
            config.min_silence_duration_ms = 600;
            config.speech_pad_ms = 250;
            break;
        case SILERO_PROFILE_CUSTOM:
        default:
            config.threshold = 0.5f;
            config.min_speech_duration_ms = 150;
            config.min_silence_duration_ms = 500;
            config.speech_pad_ms = 200;
            break;
    }

    return config;
}

// ------------------------------------------------------------------
// Metrics and health
// ------------------------------------------------------------------
SileroVadMetrics silero_vad_get_metrics(SileroVadEngine* engine) {
    SileroVadMetrics metrics{};
    if (!engine) return metrics;

    metrics.total_chunks_processed = engine->total_chunks;
    metrics.speech_chunks = engine->speech_chunks;
    metrics.silence_chunks = engine->silence_chunks;
    metrics.avg_speech_probability = engine->avg_speech_prob;
    metrics.avg_inference_ms = engine->avg_inference_ms;
    metrics.model_loaded = (engine->session != nullptr);
    metrics.error_message = engine->last_error.empty() ? nullptr : engine->last_error.c_str();

    // Approximate RAM (ONNX session + buffers)
    metrics.current_ram_bytes = engine->input_buffer.size() * sizeof(float) +
                                engine->output_buffer.size() * sizeof(float) +
                                engine->audio_buffer.size() * sizeof(int16_t) +
                                1024 * 1024;  // ~1MB for session
    metrics.peak_ram_bytes = metrics.current_ram_bytes;

    return metrics;
}

bool silero_vad_is_healthy(SileroVadEngine* engine) {
    return engine && engine->session && engine->state != SILERO_VAD_STATE_FAILED;
}

// ------------------------------------------------------------------
// Version info
// ------------------------------------------------------------------
const char* silero_vad_version() {
    return "1.0.0";
}

const char* silero_vad_onnxruntime_version() {
    return OrtGetApiBase()->GetVersionString();
}

} // extern "C"