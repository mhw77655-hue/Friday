/**
 * JNI Bridge for LocalInferenceEngine (llama.cpp GGUF)
 * Maps Kotlin LocalInferenceEngine calls to native C++ engine
 */
#include "llama_jni.h"
#include <jni.h>
#include <string>
#include <vector>
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
    jstring token_text = env->NewStringUTF(token ? token : "");
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
    LlamaModelConfig nativeConfig{};

    jclass configClass = env->GetObjectClass(config);

    // Extract config fields
    jfieldID modelPathField = env->GetFieldID(configClass, "modelPath", "Ljava/lang/String;");
    jstring modelPathStr = (jstring)env->GetObjectField(config, modelPathField);
    const char* modelPath = env->GetStringUTFChars(modelPathStr, nullptr);
    nativeConfig.model_path = modelPath;

    jfieldID nCtxField = env->GetFieldID(configClass, "nCtx", "I");
    nativeConfig.n_ctx = env->GetIntField(config, nCtxField);

    jfieldID nBatchField = env->GetFieldID(configClass, "nBatch", "I");
    nativeConfig.n_batch = env->GetIntField(config, nBatchField);

    jfieldID nThreadsField = env->GetFieldID(configClass, "nThreads", "I");
    nativeConfig.n_threads = env->GetIntField(config, nThreadsField);

    jfieldID nGpuLayersField = env->GetFieldID(configClass, "nGpuLayers", "I");
    nativeConfig.n_gpu_layers = env->GetIntField(config, nGpuLayersField);

    jfieldID useMmapField = env->GetFieldID(configClass, "useMmap", "Z");
    nativeConfig.use_mmap = env->GetBooleanField(config, useMmapField);

    jfieldID useMlockField = env->GetFieldID(configClass, "useMlock", "Z");
    nativeConfig.use_mlock = env->GetBooleanField(config, useMlockField);

    jfieldID flashAttnField = env->GetFieldID(configClass, "flashAttn", "Z");
    nativeConfig.flash_attn = env->GetBooleanField(config, flashAttnField);

    jfieldID seedField = env->GetFieldID(configClass, "seed", "I");
    nativeConfig.seed = env->GetIntField(config, seedField);

    jfieldID tempField = env->GetFieldID(configClass, "temp", "F");
    nativeConfig.temp = env->GetFloatField(config, tempField);

    jfieldID topPField = env->GetFieldID(configClass, "topP", "F");
    nativeConfig.top_p = env->GetFloatField(config, topPField);

    jfieldID topKField = env->GetFieldID(configClass, "topK", "I");
    nativeConfig.top_k = env->GetIntField(config, topKField);

    jfieldID repeatPenaltyField = env->GetFieldID(configClass, "repeatPenalty", "F");
    nativeConfig.repeat_penalty = env->GetFloatField(config, repeatPenaltyField);

    jfieldID repeatLastNField = env->GetFieldID(configClass, "repeatLastN", "I");
    nativeConfig.repeat_last_n = env->GetIntField(config, repeatLastNField);

    env->ReleaseStringUTFChars(modelPathStr, modelPath);

    LlamaModel* model = llama_model_create(&nativeConfig);
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

    jmethodID constructor = env->GetMethodID(healthClass, "<init>", "(Lai/friday/assistant/model/adapters/LocalInferenceEngine$NativeState;ZZLjava/lang/String;JJJFJJJJF)V");
    if (!constructor) return nullptr;

    // Map state
    jclass stateClass = env->FindClass("ai/friday/assistant/model/adapters/LocalInferenceEngine$NativeState");
    jobject stateEnum = nullptr;
    if (stateClass) {
        jfieldID valuesField = env->GetStaticFieldID(stateClass, "values", "[Lai/friday/assistant/model/adapters/LocalInferenceEngine$NativeState;");
        if (valuesField) {
            jobjectArray states = (jobjectArray)env->GetStaticObjectField(stateClass, valuesField);
            if (states && health.state < env->GetArrayLength(states)) {
                stateEnum = env->GetObjectArrayElement(states, health.state);
            }
        }
    }

    jstring errorMsg = health.error_message ? env->NewStringUTF(health.error_message) : nullptr;

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

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    if (!promptStr) return nullptr;

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
            env->ReleaseStringUTFChars(prompt, promptStr);
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
            env->ReleaseStringUTFChars(prompt, promptStr);
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
            env->ReleaseStringUTFChars(prompt, promptStr);
            return nullptr;
        }
        if (stopSeqArray) {
            const jsize count = env->GetArrayLength(stopSeqArray);
            stopStrings.reserve(static_cast<size_t>(count));
            for (jsize i = 0; i < count; ++i) {
                jstring stop = static_cast<jstring>(env->GetObjectArrayElement(stopSeqArray, i));
                if (!stop) continue;
                const char* stopChars = env->GetStringUTFChars(stop, nullptr);
                if (!stopChars) {
                    env->DeleteLocalRef(stop);
                    env->DeleteLocalRef(stopSeqArray);
                    env->DeleteLocalRef(configClass);
                    env->ReleaseStringUTFChars(prompt, promptStr);
                    return nullptr;
                }
                stopStrings.emplace_back(stopChars);
                env->ReleaseStringUTFChars(stop, stopChars);
                env->DeleteLocalRef(stop);
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
            env->ReleaseStringUTFChars(prompt, promptStr);
            return nullptr;
        }
        callbackContext.env = env;
        callbackContext.callback = env->NewGlobalRef(callback);
        callbackContext.on_token = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;IZ)V");
        callbackContext.model = model;
        env->DeleteLocalRef(callbackClass);
        if (env->ExceptionCheck() || !callbackContext.callback || !callbackContext.on_token) {
            if (callbackContext.callback) env->DeleteGlobalRef(callbackContext.callback);
            env->ReleaseStringUTFChars(prompt, promptStr);
            return nullptr;
        }
        tokenCb = java_token_callback;
    }

    const char* result = llama_model_generate(model, promptStr, &nativeConfig, tokenCb, &callbackContext);
    env->ReleaseStringUTFChars(prompt, promptStr);
    if (callbackContext.callback) env->DeleteGlobalRef(callbackContext.callback);

    // Do not make further JNI calls while a Java callback exception is pending.
    if (env->ExceptionCheck()) {
        if (result) free((void*)result);
        return nullptr;
    }

    if (result) {
        jstring jResult = env->NewStringUTF(result);
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

    const char* textStr = env->GetStringUTFChars(text, nullptr);
    if (!textStr) return -1;
    LlamaTokenizer* tokenizer = llama_tokenizer_get(model);
    if (!tokenizer) {
        env->ReleaseStringUTFChars(text, textStr);
        return -1;
    }
    const int count = llama_tokenizer_count(tokenizer, textStr);
    llama_tokenizer_destroy(tokenizer);
    env->ReleaseStringUTFChars(text, textStr);
    return count;
}

// Encode
JNIEXPORT jint JNICALL
JNI_CLASS(nativeEncode)(JNIEnv* env, jobject thiz, jlong handle, jstring text, jintArray output) {
    LlamaModel* model = getModel(handle);
    if (!model || !text || !output) return -1;

    const char* textStr = env->GetStringUTFChars(text, nullptr);
    if (!textStr) return -1;
    jint* outData = env->GetIntArrayElements(output, nullptr);
    if (!outData) {
        env->ReleaseStringUTFChars(text, textStr);
        return -1;
    }
    LlamaTokenizer* tokenizer = llama_tokenizer_get(model);
    const int count = tokenizer
        ? llama_tokenizer_encode(tokenizer, textStr, reinterpret_cast<int32_t*>(outData), env->GetArrayLength(output))
        : -1;
    if (tokenizer) llama_tokenizer_destroy(tokenizer);
    env->ReleaseStringUTFChars(text, textStr);
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
        jstring jResult = env->NewStringUTF(result);
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