/**
 * JNI Bridge for LocalInferenceEngine (llama.cpp GGUF)
 * Maps Kotlin LocalInferenceEngine calls to native C++ engine
 */
#include "llama_jni.h"
#include <jni.h>
#include <string>
#include <vector>
#include <cstdint>
#include <limits>
#include <android/log.h>

#define LOG_TAG "LocalInferenceEngineJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ------------------------------------------------------------------
// JNI Helper macros
// ------------------------------------------------------------------
#define JNI_CLASS(name) Java_ai_friday_assistant_model_adapters_LocalInferenceEngine_##name

// ------------------------------------------------------------------
// Native handle management
// ------------------------------------------------------------------
static inline LlamaModel* getModel(jlong handle) {
    return reinterpret_cast<LlamaModel*>(handle);
}

static inline LlamaTokenizer* getTokenizer(jlong handle) {
    return reinterpret_cast<LlamaTokenizer*>(handle);
}

// JNI's GetStringUTFChars/NewStringUTF use Modified UTF-8, while llama.cpp
// consumes and returns standard UTF-8. Convert explicitly so supplementary
// Unicode (emoji, historic scripts) is not corrupted at the native boundary.
static void append_utf8(std::string& out, uint32_t cp) {
    if (cp <= 0x7F) {
        out.push_back(static_cast<char>(cp));
    } else if (cp <= 0x7FF) {
        out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else if (cp <= 0xFFFF) {
        out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    } else {
        out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
    }
}

static std::string jstring_to_utf8(JNIEnv* env, jstring value) {
    std::string out;
    if (!env || !value) return out;
    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (!chars) return out;
    out.reserve(static_cast<size_t>(length) * 3);
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < length) {
            const uint32_t low = chars[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                cp = 0x10000 + ((cp - 0xD800) << 10) + (low - 0xDC00);
                ++i;
            } else {
                cp = 0xFFFD;
            }
        } else if (cp >= 0xD800 && cp <= 0xDFFF) {
            cp = 0xFFFD;
        }
        append_utf8(out, cp);
    }
    env->ReleaseStringChars(value, chars);
    return out;
}

static jstring utf8_to_jstring(JNIEnv* env, const char* bytes) {
    if (!env || !bytes) return nullptr;
    const auto* data = reinterpret_cast<const unsigned char*>(bytes);
    const size_t length = std::strlen(bytes);
    std::vector<jchar> utf16;
    utf16.reserve(length);
    for (size_t i = 0; i < length;) {
        uint32_t cp = 0xFFFD;
        size_t consumed = 1;
        const unsigned char first = data[i];
        if (first <= 0x7F) {
            cp = first;
        } else if (first >= 0xC2 && first <= 0xDF && i + 1 < length &&
                   (data[i + 1] & 0xC0) == 0x80) {
            cp = ((first & 0x1F) << 6) | (data[i + 1] & 0x3F);
            consumed = 2;
        } else if (first >= 0xE0 && first <= 0xEF && i + 2 < length &&
                   (data[i + 1] & 0xC0) == 0x80 && (data[i + 2] & 0xC0) == 0x80) {
            const uint32_t candidate = ((first & 0x0F) << 12) |
                ((data[i + 1] & 0x3F) << 6) | (data[i + 2] & 0x3F);
            if (candidate >= 0x800 && !(candidate >= 0xD800 && candidate <= 0xDFFF)) {
                cp = candidate;
                consumed = 3;
            }
        } else if (first >= 0xF0 && first <= 0xF4 && i + 3 < length &&
                   (data[i + 1] & 0xC0) == 0x80 &&
                   (data[i + 2] & 0xC0) == 0x80 &&
                   (data[i + 3] & 0xC0) == 0x80) {
            const uint32_t candidate = ((first & 0x07) << 18) |
                ((data[i + 1] & 0x3F) << 12) |
                ((data[i + 2] & 0x3F) << 6) | (data[i + 3] & 0x3F);
            if (candidate >= 0x10000 && candidate <= 0x10FFFF) {
                cp = candidate;
                consumed = 4;
            }
        }
        i += consumed;
        if (cp <= 0xFFFF) {
            utf16.push_back(static_cast<jchar>(cp));
        } else {
            cp -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
        }
    }
    if (utf16.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;
    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

// JNI callbacks are invoked synchronously on the thread that called
// nativeGenerate. The callback must not re-enter model APIs while generation
// holds the model mutex; Kotlin reads health only after nativeGenerate returns.
struct JavaTokenCallbackContext {
    JNIEnv* env = nullptr;
    jobject callback = nullptr; // global reference, owned by nativeGenerate
    jmethodID on_token = nullptr;
    LlamaModel* model = nullptr;
    bool failed = false;
};

static void java_token_callback(const char* token, int token_id, bool is_final, void* user_data) {
    auto* context = static_cast<JavaTokenCallbackContext*>(user_data);
    if (!context || context->failed || !context->env || !context->callback || !context->on_token) return;

    JNIEnv* env = context->env;
    jstring token_text = utf8_to_jstring(env, token ? token : "");
    if (!token_text) {
        context->failed = true;
        llama_model_cancel(context->model);
        return;
    }

    env->CallVoidMethod(
        context->callback,
        context->on_token,
        token_text,
        static_cast<jint>(token_id),
        static_cast<jboolean>(is_final ? JNI_TRUE : JNI_FALSE)
    );
    env->DeleteLocalRef(token_text);
    if (env->ExceptionCheck()) {
        context->failed = true;
        llama_model_cancel(context->model);
    }
}

// ------------------------------------------------------------------
// JNI Implementation
// ------------------------------------------------------------------
extern "C" {

// Create
JNIEXPORT jlong JNICALL
JNI_CLASS(nativeCreate)(JNIEnv* env, jobject thiz, jobject config) {
    if (!config) return 0;

    jclass configClass = env->GetObjectClass(config);
    if (!configClass) return 0;
    auto getField = [&](const char* name, const char* signature) -> jfieldID {
        if (env->ExceptionCheck()) return nullptr;
        return env->GetFieldID(configClass, name, signature);
    };

    jfieldID modelPathField = getField("modelPath", "Ljava/lang/String;");
    jfieldID nCtxField = getField("nCtx", "I");
    jfieldID nBatchField = getField("nBatch", "I");
    jfieldID nThreadsField = getField("nThreads", "I");
    jfieldID nGpuLayersField = getField("nGpuLayers", "I");
    jfieldID useMmapField = getField("useMmap", "Z");
    jfieldID useMlockField = getField("useMlock", "Z");
    jfieldID flashAttnField = getField("flashAttn", "Z");
    jfieldID seedField = getField("seed", "I");
    jfieldID tempField = getField("temp", "F");
    jfieldID topPField = getField("topP", "F");
    jfieldID topKField = getField("topK", "I");
    jfieldID repeatPenaltyField = getField("repeatPenalty", "F");
    jfieldID repeatLastNField = getField("repeatLastN", "I");
    if (env->ExceptionCheck() || !modelPathField || !nCtxField || !nBatchField ||
        !nThreadsField || !nGpuLayersField || !useMmapField || !useMlockField ||
        !flashAttnField || !seedField || !tempField || !topPField || !topKField ||
        !repeatPenaltyField || !repeatLastNField) {
        env->DeleteLocalRef(configClass);
        return 0;
    }

    jstring modelPathStr = static_cast<jstring>(env->GetObjectField(config, modelPathField));
    if (!modelPathStr) {
        env->DeleteLocalRef(configClass);
        return 0;
    }
    const std::string modelPath = jstring_to_utf8(env, modelPathStr);
    if (env->ExceptionCheck()) {
        env->DeleteLocalRef(modelPathStr);
        env->DeleteLocalRef(configClass);
        return 0;
    }

    LlamaModelConfig nativeConfig{};
    nativeConfig.model_path = modelPath.c_str();
    nativeConfig.n_ctx = env->GetIntField(config, nCtxField);
    nativeConfig.n_batch = env->GetIntField(config, nBatchField);
    nativeConfig.n_threads = env->GetIntField(config, nThreadsField);
    nativeConfig.n_gpu_layers = env->GetIntField(config, nGpuLayersField);
    nativeConfig.use_mmap = env->GetBooleanField(config, useMmapField);
    nativeConfig.use_mlock = env->GetBooleanField(config, useMlockField);
    nativeConfig.flash_attn = env->GetBooleanField(config, flashAttnField);
    nativeConfig.seed = env->GetIntField(config, seedField);
    nativeConfig.temp = env->GetFloatField(config, tempField);
    nativeConfig.top_p = env->GetFloatField(config, topPField);
    nativeConfig.top_k = env->GetIntField(config, topKField);
    nativeConfig.repeat_penalty = env->GetFloatField(config, repeatPenaltyField);
    nativeConfig.repeat_last_n = env->GetIntField(config, repeatLastNField);

    LlamaModel* model = nullptr;
    if (!env->ExceptionCheck()) model = llama_model_create(&nativeConfig);

    env->DeleteLocalRef(modelPathStr);
    env->DeleteLocalRef(configClass);
    return reinterpret_cast<jlong>(model);
}

// Get state
JNIEXPORT jint JNICALL
JNI_CLASS(nativeGetState)(JNIEnv* env, jobject thiz, jlong handle) {
    LlamaModel* model = getModel(handle);
    if (!model) return -1;
    return static_cast<jint>(llama_model_get_state(model));
}

// Get health
JNIEXPORT jobject JNICALL
JNI_CLASS(nativeGetHealth)(JNIEnv* env, jobject thiz, jlong handle) {
    LlamaModel* model = getModel(handle);
    if (!model) return nullptr;

    LlamaHealth health = llama_model_get_health(model);

    jclass healthClass = env->FindClass("ai/friday/assistant/model/adapters/LocalInferenceEngine$NativeHealth");
    if (!healthClass) return nullptr;

    jmethodID constructor = env->GetMethodID(healthClass, "<init>", "(Lai/friday/assistant/model/adapters/LocalInferenceEngine$NativeState;ZZLjava/lang/String;JJJFJJJJJF)V");
    if (!constructor) return nullptr;

    // Map state
    jclass stateClass = env->FindClass("ai/friday/assistant/model/adapters/LocalInferenceEngine$NativeState");
    jobject stateEnum = nullptr;
    if (stateClass) {
        jmethodID valuesMethod = env->GetStaticMethodID(
            stateClass, "values", "()[Lai/friday/assistant/model/adapters/LocalInferenceEngine$NativeState;"
        );
        if (valuesMethod) {
            jobjectArray states = static_cast<jobjectArray>(env->CallStaticObjectMethod(stateClass, valuesMethod));
            if (states && health.state >= 0 && health.state < env->GetArrayLength(states)) {
                stateEnum = env->GetObjectArrayElement(states, health.state);
            }
            if (states) env->DeleteLocalRef(states);
        }
    }

    if (!stateEnum) return nullptr;
    jstring errorMsg = health.error_message ? utf8_to_jstring(env, health.error_message) : nullptr;
    if (health.error_message && !errorMsg) return nullptr;

    jobject healthObj = env->NewObject(healthClass, constructor,
        stateEnum,
        health.model_loaded,
        health.context_ready,
        errorMsg,
        health.last_activity_ms,
        health.metrics.load_time_ms,
        health.metrics.first_token_ms,
        health.metrics.tokens_per_sec,
        health.metrics.peak_ram_bytes,
        health.metrics.current_ram_bytes,
        health.metrics.prompt_tokens,
        health.metrics.completion_tokens,
        health.metrics.total_tokens,
        health.metrics.cpu_percent
    );

    if (errorMsg) env->DeleteLocalRef(errorMsg);
    return healthObj;
}

// Prewarm
JNIEXPORT jboolean JNICALL
JNI_CLASS(nativePrewarm)(JNIEnv* env, jobject thiz, jlong handle) {
    LlamaModel* model = getModel(handle);
    if (!model) return JNI_FALSE;
    return llama_model_prewarm(model, nullptr, nullptr) ? JNI_TRUE : JNI_FALSE;
}

// Wait ready
JNIEXPORT jboolean JNICALL
JNI_CLASS(nativeWaitReady)(JNIEnv* env, jobject thiz, jlong handle, jint timeoutMs) {
    LlamaModel* model = getModel(handle);
    if (!model) return JNI_FALSE;
    return llama_model_wait_ready(model, timeoutMs) ? JNI_TRUE : JNI_FALSE;
}

// Generate
JNIEXPORT jstring JNICALL
JNI_CLASS(nativeGenerate)(JNIEnv* env, jobject thiz, jlong handle, jstring prompt, jobject genConfig, jobject callback) {
    LlamaModel* model = getModel(handle);
    if (!model || !prompt) return nullptr;

    const std::string promptStr = jstring_to_utf8(env, prompt);
    if (env->ExceptionCheck()) return nullptr;

    LlamaGenerationConfig nativeConfig{};
    nativeConfig.max_tokens = 512;
    nativeConfig.temperature = 0.7f;
    nativeConfig.top_p = 0.9f;
    nativeConfig.top_k = 40;
    nativeConfig.repeat_penalty = 1.1f;
    nativeConfig.stop_sequences = nullptr;
    nativeConfig.stop_sequence_count = 0;
    nativeConfig.stream = false;

    std::vector<std::string> stopStrings;
    std::vector<const char*> stopPointers;
    if (genConfig) {
        jclass configClass = env->GetObjectClass(genConfig);
        if (!configClass) {
            
            return nullptr;
        }

        jfieldID maxTokensField = env->GetFieldID(configClass, "maxTokens", "I");
        jfieldID tempField = env->GetFieldID(configClass, "temperature", "F");
        jfieldID topPField = env->GetFieldID(configClass, "topP", "F");
        jfieldID topKField = env->GetFieldID(configClass, "topK", "I");
        jfieldID repeatPenaltyField = env->GetFieldID(configClass, "repeatPenalty", "F");
        jfieldID streamField = env->GetFieldID(configClass, "stream", "Z");
        jfieldID stopSeqField = env->GetFieldID(configClass, "stopSequences", "[Ljava/lang/String;");
        if (env->ExceptionCheck() || !maxTokensField || !tempField || !topPField ||
            !topKField || !repeatPenaltyField || !streamField || !stopSeqField) {
            env->DeleteLocalRef(configClass);
            
            return nullptr;
        }

        nativeConfig.max_tokens = env->GetIntField(genConfig, maxTokensField);
        nativeConfig.temperature = env->GetFloatField(genConfig, tempField);
        nativeConfig.top_p = env->GetFloatField(genConfig, topPField);
        nativeConfig.top_k = env->GetIntField(genConfig, topKField);
        nativeConfig.repeat_penalty = env->GetFloatField(genConfig, repeatPenaltyField);
        nativeConfig.stream = env->GetBooleanField(genConfig, streamField);

        jobjectArray stopSeqArray = static_cast<jobjectArray>(env->GetObjectField(genConfig, stopSeqField));
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(configClass);
            
            return nullptr;
        }
        if (stopSeqArray) {
            const jsize count = env->GetArrayLength(stopSeqArray);
            stopStrings.reserve(static_cast<size_t>(count));
            for (jsize i = 0; i < count; ++i) {
                jstring stop = static_cast<jstring>(env->GetObjectArrayElement(stopSeqArray, i));
                if (!stop) continue;
                const std::string stopText = jstring_to_utf8(env, stop);
                env->DeleteLocalRef(stop);
                if (env->ExceptionCheck()) {
                    env->DeleteLocalRef(stopSeqArray);
                    env->DeleteLocalRef(configClass);
                    return nullptr;
                }
                if (!stopText.empty()) stopStrings.emplace_back(stopText);
            }
            env->DeleteLocalRef(stopSeqArray);
        }
        env->DeleteLocalRef(configClass);
        stopPointers.reserve(stopStrings.size());
        for (const auto& stop : stopStrings) stopPointers.push_back(stop.c_str());
        nativeConfig.stop_sequences = stopPointers.empty() ? nullptr : stopPointers.data();
        nativeConfig.stop_sequence_count = static_cast<int>(stopPointers.size());
    }

    JavaTokenCallbackContext callbackContext{};
    LlamaTokenCallback tokenCb = nullptr;
    if (callback && nativeConfig.stream) {
        jclass callbackClass = env->GetObjectClass(callback);
        if (!callbackClass) {
            
            return nullptr;
        }
        callbackContext.env = env;
        callbackContext.callback = env->NewGlobalRef(callback);
        callbackContext.on_token = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;IZ)V");
        callbackContext.model = model;
        env->DeleteLocalRef(callbackClass);
        if (env->ExceptionCheck() || !callbackContext.callback || !callbackContext.on_token) {
            if (callbackContext.callback) env->DeleteGlobalRef(callbackContext.callback);
            
            return nullptr;
        }
        tokenCb = java_token_callback;
    }

    const char* result = llama_model_generate(model, promptStr.c_str(), &nativeConfig, tokenCb, &callbackContext);
    
    if (callbackContext.callback) env->DeleteGlobalRef(callbackContext.callback);

    // Do not make further JNI calls while a Java callback exception is pending.
    if (env->ExceptionCheck()) {
        if (result) free((void*)result);
        return nullptr;
    }

    if (result) {
        jstring jResult = utf8_to_jstring(env, result);
        free((void*)result);
        return jResult;
    }
    return nullptr;
}

// Cancel
JNIEXPORT void JNICALL
JNI_CLASS(nativeCancel)(JNIEnv* env, jobject thiz, jlong handle) {
    LlamaModel* model = getModel(handle);
    if (model) {
        llama_model_cancel(model);
    }
}

// Suspend
JNIEXPORT jboolean JNICALL
JNI_CLASS(nativeSuspend)(JNIEnv* env, jobject thiz, jlong handle) {
    LlamaModel* model = getModel(handle);
    if (!model) return JNI_FALSE;
    return llama_model_suspend(model) ? JNI_TRUE : JNI_FALSE;
}

// Resume
JNIEXPORT jboolean JNICALL
JNI_CLASS(nativeResume)(JNIEnv* env, jobject thiz, jlong handle) {
    LlamaModel* model = getModel(handle);
    if (!model) return JNI_FALSE;
    return llama_model_resume(model) ? JNI_TRUE : JNI_FALSE;
}

// Destroy
JNIEXPORT void JNICALL
JNI_CLASS(nativeDestroy)(JNIEnv* env, jobject thiz, jlong handle) {
    LlamaModel* model = getModel(handle);
    if (model) {
        llama_model_destroy(model);
    }
}

// Token count
JNIEXPORT jint JNICALL
JNI_CLASS(nativeTokenCount)(JNIEnv* env, jobject thiz, jlong handle, jstring text) {
    LlamaModel* model = getModel(handle);
    if (!model || !text) return -1;

    const std::string textStr = jstring_to_utf8(env, text);
    if (env->ExceptionCheck()) return -1;
    LlamaTokenizer* tokenizer = llama_tokenizer_get(model);
    if (!tokenizer) return -1;
    const int count = llama_tokenizer_count(tokenizer, textStr.c_str());
    llama_tokenizer_destroy(tokenizer);
    return count;
}

// Encode
JNIEXPORT jint JNICALL
JNI_CLASS(nativeEncode)(JNIEnv* env, jobject thiz, jlong handle, jstring text, jintArray output) {
    LlamaModel* model = getModel(handle);
    if (!model || !text || !output) return -1;

    const std::string textStr = jstring_to_utf8(env, text);
    if (env->ExceptionCheck()) return -1;
    jint* outData = env->GetIntArrayElements(output, nullptr);
    if (!outData) return -1;
    LlamaTokenizer* tokenizer = llama_tokenizer_get(model);
    const int count = tokenizer
        ? llama_tokenizer_encode(tokenizer, textStr.c_str(), reinterpret_cast<int32_t*>(outData), env->GetArrayLength(output))
        : -1;
    if (tokenizer) llama_tokenizer_destroy(tokenizer);
    env->ReleaseIntArrayElements(output, outData, count >= 0 ? 0 : JNI_ABORT);
    return count;
}

// Decode
JNIEXPORT jstring JNICALL
JNI_CLASS(nativeDecode)(JNIEnv* env, jobject thiz, jlong handle, jintArray tokens) {
    LlamaModel* model = getModel(handle);
    if (!model || !tokens) return nullptr;

    jint* tokenData = env->GetIntArrayElements(tokens, nullptr);
    if (!tokenData) return nullptr;
    const int count = env->GetArrayLength(tokens);
    LlamaTokenizer* tokenizer = llama_tokenizer_get(model);
    const char* result = tokenizer
        ? llama_tokenizer_decode(tokenizer, reinterpret_cast<const int32_t*>(tokenData), count)
        : nullptr;
    if (tokenizer) llama_tokenizer_destroy(tokenizer);
    env->ReleaseIntArrayElements(tokens, tokenData, JNI_ABORT);

    if (result) {
        jstring jResult = utf8_to_jstring(env, result);
        free((void*)result);
        return jResult;
    }
    return nullptr;
}

// Version
JNIEXPORT jstring JNICALL
JNI_CLASS(nativeVersion)(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(friday_llama_version());
}

JNIEXPORT jstring JNICALL
JNI_CLASS(nativeSystemInfo)(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(llama_system_info());
}

JNIEXPORT jstring JNICALL
JNI_CLASS(nativeBenchmark)(JNIEnv* env, jobject thiz, jlong handle, jint nThreads, jint nPrompt, jint nGen) {
    LlamaModel* model = getModel(handle);
    if (!model) return env->NewStringUTF("{}");
    return env->NewStringUTF(llama_benchmark(model, nThreads, nPrompt, nGen));
}

} // extern "C"