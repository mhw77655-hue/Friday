/**
 * LocalInferenceEngine C++ Implementation
 * JNI wrapper for llama.cpp GGUF inference with full lifecycle management
 */
#include "llama_jni.h"
#include "llama.h"
#include "ggml.h"
#include "gguf.h"
#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <thread>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <unordered_map>
#include <memory>
#include <android/log.h>

#define LOG_TAG "LocalInferenceEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// ------------------------------------------------------------------
// Internal structures
// ------------------------------------------------------------------
struct LlamaModel {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    LlamaModelConfig config;
    LlamaState state = LLAMA_STATE_DORMANT;
    std::mutex mutex;
    std::condition_variable cv;
    std::atomic<bool> cancel_flag{false};
    std::atomic<bool> prewarm_done{false};
    std::atomic<bool> prewarm_failed{false};
    std::string last_error;
    LlamaMetrics metrics{};
    std::chrono::steady_clock::time_point load_start;
    std::chrono::steady_clock::time_point generation_start;
    int64_t prompt_tokens = 0;
    int64_t completion_tokens = 0;
    std::thread prewarm_thread;
    std::vector<int32_t> prompt_tokens_cache;
};

struct LlamaTokenizer {
    llama_model* model = nullptr;
    const llama_vocab* vocab = nullptr;
};

// ------------------------------------------------------------------
// Helper functions
// ------------------------------------------------------------------
static LlamaState get_state_locked(LlamaModel* model) {
    std::lock_guard<std::mutex> lock(model->mutex);
    return model->state;
}

static void set_state_locked(LlamaModel* model, LlamaState new_state) {
    std::lock_guard<std::mutex> lock(model->mutex);
    model->state = new_state;
    model->cv.notify_all();
}

static void set_error_locked(LlamaModel* model, const char* fmt, ...) {
    std::lock_guard<std::mutex> lock(model->mutex);
    va_list args;
    va_start(args, fmt);
    char buffer[1024];
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    model->last_error = buffer;
    va_end(args);
    model->state = LLAMA_STATE_FAILED;
    LOGE("Error: %s", buffer);
}

static int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

static int64_t elapsed_ms(std::chrono::steady_clock::time_point start) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - start).count();
}

// ------------------------------------------------------------------
// llama.cpp logging callback
// ------------------------------------------------------------------
static void llama_log_callback(ggml_log_level level, const char* text, void* user_data) {
    switch (level) {
        case GGML_LOG_LEVEL_DEBUG: LOGI("%s", text); break;
        case GGML_LOG_LEVEL_INFO:  LOGI("%s", text); break;
        case GGML_LOG_LEVEL_WARN:  LOGW("%s", text); break;
        case GGML_LOG_LEVEL_ERROR: LOGE("%s", text); break;
        default: LOGI("%s", text); break;
    }
}

// ------------------------------------------------------------------
// Prewarm thread function
// ------------------------------------------------------------------
static void prewarm_thread_func(LlamaModel* model) {
    model->load_start = std::chrono::steady_clock::now();

    // Model is already loaded by llama_model_create, just create context
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = model->config.n_ctx;
    ctx_params.n_batch = model->config.n_batch;
    ctx_params.n_threads = model->config.n_threads;
    ctx_params.n_threads_batch = model->config.n_threads;
    ctx_params.rope_scaling_type = LLAMA_ROPE_SCALING_TYPE_NONE;
    ctx_params.rope_freq_base = 0.0f;
    ctx_params.rope_freq_scale = 0.0f;
    ctx_params.yarn_ext_factor = -1.0f;
    ctx_params.yarn_attn_factor = 1.0f;
    ctx_params.yarn_beta_fast = 32.0f;
    ctx_params.yarn_beta_slow = 1.0f;
    ctx_params.yarn_orig_ctx = 0;
    ctx_params.defrag_thold = -1.0f;
    ctx_params.callbacks = nullptr;
    ctx_params.type_k = GGML_TYPE_F16;
    ctx_params.type_v = GGML_TYPE_F16;
    ctx_params.offload_kqv = model->config.n_gpu_layers > 0;
    ctx_params.flash_attn = model->config.flash_attn;

    model->ctx = llama_init_from_model(model->model, ctx_params);

    if (!model->ctx) {
        set_error_locked(model, "Failed to create llama context");
        model->prewarm_failed = true;
        model->prewarm_done = true;
        model->cv.notify_all();
        return;
    }

    model->metrics.load_time_ms = elapsed_ms(model->load_start);
    model->metrics.peak_ram_bytes = ggml_used_mem(model->ctx);
    model->metrics.current_ram_bytes = model->metrics.peak_ram_bytes;

    set_state_locked(model, LLAMA_STATE_READY);
    model->prewarm_done = true;
    model->cv.notify_all();
    LOGI("Model prewarmed in %lld ms, RAM: %.2f MB",
         model->metrics.load_time_ms, model->metrics.peak_ram_bytes / (1024.0 * 1024.0));
}

// ------------------------------------------------------------------
// Model lifecycle implementation
// ------------------------------------------------------------------
extern "C" {

LlamaModel* llama_model_create(const LlamaModelConfig* config) {
    if (!config || !config->model_path) {
        LOGE("Invalid config or model path");
        return nullptr;
    }

    auto* model = new LlamaModel();
    model->config = *config;

    // Initialize llama.cpp backend
    llama_backend_init();
    llama_log_set(llama_log_callback, nullptr);

    // Model load params
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = config->n_gpu_layers;
    model_params.use_mmap = config->use_mmap;
    model_params.use_mlock = config->use_mlock;
    model_params.vocab_only = false;
    model_params.progress_callback = nullptr;
    model_params.progress_user_data = nullptr;

    LOGI("Loading model from %s", config->model_path);
    model->model = llama_model_load_from_file(config->model_path, model_params);

    if (!model->model) {
        set_error_locked(model, "Failed to load GGUF model from %s", config->model_path);
        delete model;
        return nullptr;
    }

    set_state_locked(model, LLAMA_STATE_PREWARMING);

    // Start prewarm in background
    model->prewarm_thread = std::thread(prewarm_thread_func, model);
    model->prewarm_thread.detach();

    LOGI("Model loaded, prewarming started");
    return model;
}

LlamaState llama_model_get_state(LlamaModel* model) {
    if (!model) return LLAMA_STATE_FAILED;
    return get_state_locked(model);
}

LlamaHealth llama_model_get_health(LlamaModel* model) {
    LlamaHealth health{};
    if (!model) {
        health.state = LLAMA_STATE_FAILED;
        health.error_message = "Model handle is null";
        return health;
    }

    std::lock_guard<std::mutex> lock(model->mutex);
    health.state = model->state;
    health.model_loaded = (model->model != nullptr);
    health.context_ready = (model->ctx != nullptr);
    health.error_message = model->last_error.empty() ? nullptr : model->last_error.c_str();
    health.last_activity_ms = now_ms();
    health.metrics = model->metrics;
    return health;
}

bool llama_model_prewarm(LlamaModel* model, LlamaProgressCallback progress_cb, void* user_data) {
    if (!model) return false;

    LlamaState current = get_state_locked(model);
    if (current == LLAMA_STATE_READY || current == LLAMA_STATE_IDLE || current == LLAMA_STATE_GENERATING) {
        if (progress_cb) progress_cb(1.0f, user_data);
        return true;
    }
    if (current == LLAMA_STATE_PREWARMING) {
        // Already prewarming
        return true;
    }
    if (current == LLAMA_STATE_FAILED) {
        return false;
    }

    // Should not reach here if create() was called properly
    set_state_locked(model, LLAMA_STATE_PREWARMING);
    model->prewarm_thread = std::thread(prewarm_thread_func, model);
    model->prewarm_thread.detach();
    return true;
}

bool llama_model_wait_ready(LlamaModel* model, int timeout_ms) {
    if (!model) return false;

    std::unique_lock<std::mutex> lock(model->mutex);
    auto timeout = std::chrono::milliseconds(timeout_ms);
    auto result = model->cv.wait_for(lock, timeout, [model] {
        return model->state == LLAMA_STATE_READY || model->state == LLAMA_STATE_FAILED;
    });

    if (!result) {
        LOGE("Prewarm timeout after %d ms", timeout_ms);
        return false;
    }
    return model->state == LLAMA_STATE_READY;
}

const char* llama_model_generate(
    LlamaModel* model,
    const char* prompt,
    const LlamaGenerationConfig* gen_config,
    LlamaTokenCallback token_cb,
    void* user_data
) {
    if (!model || !prompt) {
        LOGE("Invalid parameters");
        return nullptr;
    }

    // Wait for ready state
    if (get_state_locked(model) != LLAMA_STATE_READY) {
        if (!llama_model_wait_ready(model, 30000)) {
            set_error_locked(model, "Model not ready for generation");
            return nullptr;
        }
    }

    std::lock_guard<std::mutex> lock(model->mutex);
    if (!model->ctx) {
        set_error_locked(model, "Context not initialized");
        return nullptr;
    }

    set_state_locked(model, LLAMA_STATE_GENERATING);
    model->generation_start = std::chrono::steady_clock::now();
    model->cancel_flag = false;
    model->completion_tokens = 0;

    // Default generation config
    LlamaGenerationConfig default_config{};
    default_config.max_tokens = 512;
    default_config.temperature = model->config.temp;
    default_config.top_p = model->config.top_p;
    default_config.top_k = model->config.top_k;
    default_config.repeat_penalty = model->config.repeat_penalty;
    default_config.stop_sequences = nullptr;
    default_config.stop_sequence_count = 0;
    default_config.stream = false;

    const LlamaGenerationConfig* gc = gen_config ? gen_config : &default_config;

    // Tokenize prompt
    const llama_vocab* vocab = llama_model_get_vocab(model->model);
    int n_tokens = llama_tokenize(vocab, prompt, strlen(prompt), nullptr, 0, true, true);
    if (n_tokens <= 0) {
        set_error_locked(model, "Failed to tokenize prompt");
        set_state_locked(model, LLAMA_STATE_IDLE);
        return nullptr;
    }

    model->prompt_tokens_cache.resize(n_tokens);
    llama_tokenize(vocab, prompt, strlen(prompt), model->prompt_tokens_cache.data(), n_tokens, true, true);
    model->prompt_tokens = n_tokens;

    // Check context window
    int n_ctx = llama_n_ctx(model->ctx);
    int max_gen = std::min(gc->max_tokens, n_ctx - n_tokens - 4);
    if (max_gen <= 0) {
        set_error_locked(model, "Prompt too long for context window");
        set_state_locked(model, LLAMA_STATE_IDLE);
        return nullptr;
    }

    // Sampling params
    llama_sampling_params sparams = llama_sampling_default_params();
    sparams.temp = gc->temperature > 0 ? gc->temperature : model->config.temp;
    sparams.top_k = gc->top_k > 0 ? gc->top_k : model->config.top_k;
    sparams.top_p = gc->top_p > 0 ? gc->top_p : model->config.top_p;
    sparams.penalty_repeat = gc->repeat_penalty > 0 ? gc->repeat_penalty : model->config.repeat_penalty;
    sparams.penalty_last_n = model->config.repeat_last_n;
    sparams.penalty_freq = 0.0f;
    sparams.penalty_present = 0.0f;
    sparams.mirostat = 0;
    sparams.mirostat_tau = 5.0f;
    sparams.mirostat_eta = 0.1f;
    sparams.grammar = nullptr;
    sparams.n_probs = 0;

    // Stop sequences
    std::vector<std::string> stop_strings;
    if (gc->stop_sequences && gc->stop_sequence_count > 0) {
        for (int i = 0; i < gc->stop_sequence_count; i++) {
            stop_strings.emplace_back(gc->stop_sequences[i]);
        }
    }
    // Add EOS token as stop
    stop_strings.emplace_back("</s>");

    // Create sampling context
    llama_sampling_context* ctx_sampling = llama_sampling_init(sparams);
    if (!ctx_sampling) {
        set_error_locked(model, "Failed to create sampling context");
        set_state_locked(model, LLAMA_STATE_IDLE);
        return nullptr;
    }

    // Feed prompt
    llama_batch batch = llama_batch_get_one(model->prompt_tokens_cache.data(), n_tokens);
    if (llama_decode(model->ctx, batch) != 0) {
        set_error_locked(model, "Failed to process prompt");
        llama_sampling_free(ctx_sampling);
        set_state_locked(model, LLAMA_STATE_IDLE);
        return nullptr;
    }

    model->metrics.first_token_ms = elapsed_ms(model->generation_start);
    model->metrics.prompt_tokens = n_tokens;

    // Generate tokens
    std::string generated_text;
    std::vector<int32_t> generated_tokens;
    generated_tokens.reserve(max_gen);

    bool first_token = true;
    int64_t first_token_time = 0;

    for (int i = 0; i < max_gen; i++) {
        if (model->cancel_flag) {
            LOGI("Generation cancelled");
            break;
        }

        // Sample next token
        llama_token token = llama_sampling_sample(ctx_sampling, model->ctx, nullptr);
        if (token == llama_token_eos(vocab)) {
            LOGI("EOS token generated");
            break;
        }

        // Decode token to text
        char token_str[256];
        int n = llama_token_to_piece(vocab, token, token_str, sizeof(token_str), 0, true);
        if (n > 0) {
            token_str[n] = '\0';
            generated_text += token_str;
            generated_tokens.push_back(token);

            if (first_token) {
                first_token_time = elapsed_ms(model->generation_start);
                model->metrics.first_token_ms = first_token_time;
                first_token = false;
            }

            // Stream callback
            if (gc->stream && token_cb) {
                token_cb(token_str, token, false, user_data);
            }
        }

        // Check stop sequences
        bool should_stop = false;
        for (const auto& stop : stop_strings) {
            if (generated_text.size() >= stop.size()) {
                if (generated_text.substr(generated_text.size() - stop.size()) == stop) {
                    should_stop = true;
                    break;
                }
            }
        }
        if (should_stop) {
            LOGI("Stop sequence matched");
            break;
        }

        // Feed token back for next iteration
        llama_batch next_batch = llama_batch_get_one(&token, 1);
        if (llama_decode(model->ctx, next_batch) != 0) {
            LOGE("Decode failed during generation");
            break;
        }

        model->completion_tokens++;
    }

    model->metrics.completion_tokens = model->completion_tokens;
    model->metrics.total_tokens = model->prompt_tokens + model->completion_tokens;

    auto gen_time = elapsed_ms(model->generation_start);
    model->metrics.tokens_per_sec = model->completion_tokens / (gen_time / 1000.0f);
    model->metrics.current_ram_bytes = ggml_used_mem(model->ctx);
    if (model->metrics.current_ram_bytes > model->metrics.peak_ram_bytes) {
        model->metrics.peak_ram_bytes = model->metrics.current_ram_bytes;
    }

    llama_sampling_free(ctx_sampling);

    // Final callback
    if (gc->stream && token_cb) {
        token_cb("", -1, true, user_data);
    }

    set_state_locked(model, LLAMA_STATE_IDLE);

    // Return generated text (caller must free)
    char* result = strdup(generated_text.c_str());
    return result;
}

void llama_model_cancel(LlamaModel* model) {
    if (!model) return;
    model->cancel_flag = true;
    LOGI("Generation cancel requested");
}

bool llama_model_suspend(LlamaModel* model) {
    if (!model) return false;

    LlamaState current = get_state_locked(model);
    if (current != LLAMA_STATE_IDLE) {
        LOGW("Cannot suspend: model not in IDLE state (%d)", current);
        return false;
    }

    std::lock_guard<std::mutex> lock(model->mutex);
    if (model->ctx) {
        llama_free(model->ctx);
        model->ctx = nullptr;
    }
    set_state_locked(model, LLAMA_STATE_SUSPENDED);
    LOGI("Model suspended");
    return true;
}

bool llama_model_resume(LlamaModel* model) {
    if (!model) return false;

    LlamaState current = get_state_locked(model);
    if (current != LLAMA_STATE_SUSPENDED) {
        LOGW("Cannot resume: model not in SUSPENDED state (%d)", current);
        return false;
    }

    set_state_locked(model, LLAMA_STATE_PREWARMING);
    model->prewarm_thread = std::thread(prewarm_thread_func, model);
    model->prewarm_thread.detach();
    LOGI("Model resume initiated");
    return true;
}

void llama_model_destroy(LlamaModel* model) {
    if (!model) return;

    // Cancel any ongoing generation
    model->cancel_flag = true;

    // Wait for prewarm thread if running
    if (model->prewarm_thread.joinable()) {
        model->prewarm_thread.join();
    }

    std::lock_guard<std::mutex> lock(model->mutex);
    set_state_locked(model, LLAMA_STATE_UNLOADING);

    if (model->ctx) {
        llama_free(model->ctx);
        model->ctx = nullptr;
    }
    if (model->model) {
        llama_model_free(model->model);
        model->model = nullptr;
    }

    llama_backend_free();
    set_state_locked(model, LLAMA_STATE_DORMANT);
    LOGI("Model destroyed");

    delete model;
}

// ------------------------------------------------------------------
// Tokenizer implementation
// ------------------------------------------------------------------
LlamaTokenizer* llama_tokenizer_get(LlamaModel* model) {
    if (!model || !model->model) return nullptr;

    auto* tokenizer = new LlamaTokenizer();
    tokenizer->model = model->model;
    tokenizer->vocab = llama_model_get_vocab(model->model);
    return tokenizer;
}

int llama_tokenizer_encode(LlamaTokenizer* tokenizer, const char* text, int32_t* output, int max_tokens) {
    if (!tokenizer || !text || !output || max_tokens <= 0) return -1;

    int n = llama_tokenize(tokenizer->vocab, text, strlen(text), nullptr, 0, true, true);
    if (n <= 0 || n > max_tokens) return -1;

    return llama_tokenize(tokenizer->vocab, text, strlen(text), output, max_tokens, true, true);
}

const char* llama_tokenizer_decode(LlamaTokenizer* tokenizer, const int32_t* tokens, int count) {
    if (!tokenizer || !tokens || count <= 0) return nullptr;

    std::string result;
    result.reserve(count * 4);

    for (int i = 0; i < count; i++) {
        char buffer[256];
        int n = llama_token_to_piece(tokenizer->vocab, tokens[i], buffer, sizeof(buffer), 0, true);
        if (n > 0) {
            result.append(buffer, n);
        }
    }

    return strdup(result.c_str());
}

int llama_tokenizer_count(LlamaTokenizer* tokenizer, const char* text) {
    if (!tokenizer || !text) return -1;
    return llama_tokenize(tokenizer->vocab, text, strlen(text), nullptr, 0, true, true);
}

// ------------------------------------------------------------------
// Utility functions
// ------------------------------------------------------------------
const char* llama_version() {
    static std::string version = "llama.cpp " + std::string(llama_version_string());
    return version.c_str();
}

const char* llama_system_info() {
    return llama_print_system_info();
}

const char* llama_benchmark(LlamaModel* model, int n_threads, int n_prompt, int n_gen) {
    // Simplified benchmark - could be expanded
    static std::string result;
    result = "{";
    result += "\"n_threads\":" + std::to_string(n_threads) + ",";
    result += "\"n_prompt\":" + std::to_string(n_prompt) + ",";
    result += "\"n_gen\":" + std::to_string(n_gen) + ",";
    result += "\"status\":\"not_implemented\"";
    result += "}";
    return result.c_str();
}

} // extern "C"