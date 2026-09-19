/**
 * JNI Header for Silero VAD Engine
 * Provides Android-native Voice Activity Detection via ONNX Runtime
 */
#ifndef JARVIS_SILERO_VAD_JNI_H
#define JARVIS_SILERO_VAD_JNI_H

#include <jni.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// ------------------------------------------------------------------
// Opaque handle
// ------------------------------------------------------------------
typedef struct SileroVadEngine SileroVadEngine;

// ------------------------------------------------------------------
// VAD configuration
// ------------------------------------------------------------------
typedef struct {
    const char* model_path;           // Path to silero_vad.onnx
    int sample_rate;                  // Audio sample rate (8000 or 16000)
    float threshold;                  // Speech probability threshold (0.0-1.0)
    int min_speech_duration_ms;       // Minimum speech duration
    int min_silence_duration_ms;      // Minimum silence duration
    int speech_pad_ms;                // Padding around speech segments
    int window_size_samples;          // Model window size (256, 512, 768, etc)
    bool use_gpu;                     // Use GPU delegate if available
    int num_threads;                  // Inference threads
} SileroVadConfig;

// ------------------------------------------------------------------
// VAD state
// ------------------------------------------------------------------
typedef enum {
    SILERO_VAD_STATE_IDLE = 0,        // Not processing
    SILERO_VAD_STATE_LISTENING = 1,   // Waiting for speech
    SILERO_VAD_STATE_SPEECH = 2,      // Speech detected
    SILERO_VAD_STATE_SILENCE = 3,     // Silence after speech
    SILERO_VAD_STATE_FAILED = 4
} SileroVadState;

// ------------------------------------------------------------------
// VAD result
// ------------------------------------------------------------------
typedef struct {
    SileroVadState state;
    float speech_probability;         // 0.0 - 1.0
    bool speech_detected;             // True if speech in current window
    int64_t timestamp_ms;             // Audio timestamp
    int audio_level;                  // RMS audio level (0-32767)
} SileroVadResult;

// ------------------------------------------------------------------
// Callbacks
// ------------------------------------------------------------------
typedef void (*SileroVadSpeechStartCallback)(int64_t timestamp_ms, void* user_data);
typedef void (*SileroVadSpeechEndCallback)(int64_t timestamp_ms, int duration_ms, void* user_data);
typedef void (*SileroVadResultCallback)(const SileroVadResult* result, void* user_data);

// ------------------------------------------------------------------
// Engine lifecycle
// ------------------------------------------------------------------

/**
 * Create VAD engine.
 * Returns opaque handle or NULL on failure.
 */
SileroVadEngine* silero_vad_create(const SileroVadConfig* config);

/**
 * Destroy VAD engine.
 */
void silero_vad_destroy(SileroVadEngine* engine);

/**
 * Get current state.
 */
SileroVadState silero_vad_get_state(SileroVadEngine* engine);

/**
 * Process audio chunk (16-bit PCM, mono).
 * Returns VAD result for this chunk.
 * Thread-safe - can be called from audio thread.
 */
SileroVadResult silero_vad_process(SileroVadEngine* engine, const int16_t* audio, int num_samples, int64_t timestamp_ms);

/**
 * Process audio with callbacks for speech start/end events.
 */
void silero_vad_process_with_callbacks(
    SileroVadEngine* engine,
    const int16_t* audio,
    int num_samples,
    int64_t timestamp_ms,
    SileroVadSpeechStartCallback on_speech_start,
    SileroVadSpeechEndCallback on_speech_end,
    void* user_data
);

/**
 * Reset internal state (between utterances).
 */
void silero_vad_reset(SileroVadEngine* engine);

/**
 * Update configuration at runtime (threshold, durations, etc).
 */
bool silero_vad_update_config(SileroVadEngine* engine, const SileroVadConfig* config);

/**
 * Get current configuration.
 */
SileroVadConfig silero_vad_get_config(SileroVadEngine* engine);

// ------------------------------------------------------------------
// Environmental profiles
// ------------------------------------------------------------------
typedef enum {
    SILERO_PROFILE_QUIET = 0,      // Quiet indoor
    SILERO_PROFILE_NORMAL = 1,     // Normal indoor
    SILERO_PROFILE_NOISY = 2,      // Noisy environment
    SILERO_PROFILE_VEHICLE = 3,    // Car/vehicle
    SILERO_PROFILE_OUTDOOR = 4,    // Outdoor
    SILERO_PROFILE_CUSTOM = 5      // Custom settings
} SileroVadProfile;

/**
 * Apply predefined environmental profile.
 */
bool silero_vad_apply_profile(SileroVadEngine* engine, SileroVadProfile profile);

/**
 * Get recommended config for profile.
 */
SileroVadConfig silero_vad_get_profile_config(SileroVadProfile profile, int sample_rate);

// ------------------------------------------------------------------
// Metrics and health
// ------------------------------------------------------------------
typedef struct {
    int64_t total_chunks_processed;
    int64_t speech_chunks;
    int64_t silence_chunks;
    float avg_speech_probability;
    float avg_inference_ms;
    int64_t peak_ram_bytes;
    int64_t current_ram_bytes;
    bool model_loaded;
    const char* error_message;
} SileroVadMetrics;

/**
 * Get engine metrics.
 */
SileroVadMetrics silero_vad_get_metrics(SileroVadEngine* engine);

/**
 * Get engine health status.
 */
bool silero_vad_is_healthy(SileroVadEngine* engine);

// ------------------------------------------------------------------
// Version info
// ------------------------------------------------------------------
const char* silero_vad_version();
const char* silero_vad_onnxruntime_version();

#ifdef __cplusplus
}
#endif

#endif // JARVIS_SILERO_VAD_JNI_H