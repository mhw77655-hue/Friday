/**
 * JNI Header for LocalInferenceEngine
 * Provides Android-native GGUF inference via llama.cpp
 */
#ifndef JARVIS_LLAMA_JNI_H
#define JARVIS_LLAMA_JNI_H

#include <jni.h>
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// ------------------------------------------------------------------
// Opaque handle types
// ------------------------------------------------------------------
typedef struct LlamaModel LlamaModel;
typedef struct LlamaContext LlamaContext;
typedef struct LlamaTokenizer LlamaTokenizer;

// ------------------------------------------------------------------
// Lifecycle states (matching Kotlin state machine)
// ------------------------------------------------------------------
typedef enum {
    LLAMA_STATE_DORMANT = 0,
    LLAMA_STATE_PREWARMING = 1,
    LLAMA_STATE_READY = 2,
    LLAMA_STATE_GENERATING = 3,
    LLAMA_STATE_IDLE = 4,
    LLAMA_STATE_SUSPENDED = 5,
    LLAMA_STATE_UNLOADING = 6,
    LLAMA_STATE_FAILED = 7
} LlamaState;

// ------------------------------------------------------------------
// Model load configuration
// ------------------------------------------------------------------
typedef struct {
    const char* model_path;           // Path to GGUF file
    int n_ctx;                        // Context window (default: 4096)
    int n_batch;                      // Batch size (default: 512)
    int n_threads;                    // CPU threads (default: 4)
    int n_gpu_layers;                 // GPU layers (0 = CPU only)
    bool use_mmap;                    // Use mmap (default: true)
    bool use_mlock;                   // Lock in RAM (default: false)
    bool flash_attn;                  // Flash attention (default: false)
    int seed;                         // Random seed (-1 = random)
    float temp;                       // Temperature (default: 0.7)
    float top_p;                      // Top-p sampling (default: 0.9)
    int top_k;                        // Top-k sampling (default: 40)
    float repeat_penalty;             // Repeat penalty (default: 1.1)
    int repeat_last_n;                // Repeat window (default: 64)
} LlamaModelConfig;

// ------------------------------------------------------------------
// Generation configuration
// ------------------------------------------------------------------
typedef struct {
    int max_tokens;                   // Max tokens to generate
    float temperature;                // Override model temp
    float top_p;                      // Override model top_p
    int top_k;                        // Override model top_k
    float repeat_penalty;             // Override model repeat_penalty
    const char** stop_sequences;      // Array of stop strings
    int stop_sequence_count;          // Number of stop strings
    bool stream;                      // Stream tokens via callback
} LlamaGenerationConfig;

// ------------------------------------------------------------------
// Inference metrics
// ------------------------------------------------------------------
typedef struct {
    int64_t load_time_ms;             // Model load time
    int64_t first_token_ms;           // Time to first token
    float tokens_per_sec;             // Generation speed
    int64_t peak_ram_bytes;           // Peak RAM usage
    int64_t current_ram_bytes;        // Current RAM usage
    int64_t prompt_tokens;            // Prompt token count
    int64_t completion_tokens;        // Completion token count
    int64_t total_tokens;             // Total tokens
    float cpu_percent;                // CPU utilization
} LlamaMetrics;

// ------------------------------------------------------------------
// Health status
// ------------------------------------------------------------------
typedef struct {
    LlamaState state;
    bool model_loaded;
    bool context_ready;
    const char* error_message;        // NULL if healthy
    int64_t last_activity_ms;         // Last operation timestamp
    LlamaMetrics metrics;
} LlamaHealth;

// ------------------------------------------------------------------
// Token callback for streaming
// ------------------------------------------------------------------
typedef void (*LlamaTokenCallback)(const char* token, int token_id, bool is_final, void* user_data);
typedef void (*LlamaProgressCallback)(float progress, void* user_data);

// ------------------------------------------------------------------
// Model lifecycle functions
// ------------------------------------------------------------------

/**
 * Create a new model instance.
 * Returns opaque handle, or NULL on failure.
 * State transitions: DORMANT -> PREWARMING -> READY
 */
LlamaModel* llama_model_create(const LlamaModelConfig* config);

/**
 * Get current model state.
 */
LlamaState llama_model_get_state(LlamaModel* model);

/**
 * Get health status (includes metrics).
 */
LlamaHealth llama_model_get_health(LlamaModel* model);

/**
 * Prewarm the model (load weights, initialize context).
 * Async - returns immediately, state becomes PREWARMING then READY.
 */
bool llama_model_prewarm(LlamaModel* model, LlamaProgressCallback progress_cb, void* user_data);

/**
 * Wait for prewarm to complete (blocking).
 */
bool llama_model_wait_ready(LlamaModel* model, int timeout_ms);

/**
 * Generate completion for a prompt.
 * If config.stream=true, tokens delivered via token_cb.
 * Returns generated text (full) or NULL on error.
 * State: READY -> GENERATING -> IDLE
 */
const char* llama_model_generate(
    LlamaModel* model,
    const char* prompt,
    const LlamaGenerationConfig* gen_config,
    LlamaTokenCallback token_cb,
    void* user_data
);

/**
 * Cancel ongoing generation.
 * Safe to call from another thread.
 */
void llama_model_cancel(LlamaModel* model);

/**
 * Suspend model (keep weights, release context).
 * State: IDLE -> SUSPENDED
 */
bool llama_model_suspend(LlamaModel* model);

/**
 * Resume suspended model.
 * State: SUSPENDED -> READY
 */
bool llama_model_resume(LlamaModel* model);

/**
 * Unload model completely.
 * State: any -> UNLOADING -> DORMANT
 */
void llama_model_destroy(LlamaModel* model);

// ------------------------------------------------------------------
// Tokenizer functions (for context budgeting)
// ------------------------------------------------------------------

/**
 * Get tokenizer for model (for token counting, truncation).
 */
LlamaTokenizer* llama_tokenizer_get(LlamaModel* model);

/**
 * Encode text to token IDs.
 * Returns number of tokens written, or -1 on error.
 * Output buffer must be allocated by caller (max n_tokens).
 */
int llama_tokenizer_encode(LlamaTokenizer* tokenizer, const char* text, int32_t* output, int max_tokens);

/**
 * Decode token IDs to text.
 */
const char* llama_tokenizer_decode(LlamaTokenizer* tokenizer, const int32_t* tokens, int count);

/**
 * Get token count for text (without allocating full array).
 */
int llama_tokenizer_count(LlamaTokenizer* tokenizer, const char* text);

// ------------------------------------------------------------------
// Utility functions
// ------------------------------------------------------------------

/**
 * Get llama.cpp version string.
 */
const char* llama_version();

/**
 * Get system info (CPU features, etc).
 */
const char* llama_system_info();

/**
 * Benchmark model (run standardized test).
 * Returns JSON string with results.
 */
const char* llama_benchmark(LlamaModel* model, int n_threads, int n_prompt, int n_gen);

#ifdef __cplusplus
}
#endif

#endif // JARVIS_LLAMA_JNI_H