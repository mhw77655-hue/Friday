/**
 * JNI Bridge for LocalInferenceEngine (llama.cpp GGUF)
 * Maps Kotlin LocalInferenceEngine calls to native C++ engine
 */
#include "llama_jni.h"
#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "LocalInferenceEngineJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ------------------------------------------------------------------
// JNI Helper macros
// ------------------------------------------------------------------
#define JNI_CLASS(name) Java_com_jarvis_app_model_adapters_LocalInferenceEngine_##name

// ------------------------------------------------------------------
// Native handle management
// ------------------------------------------------------------------
static inline LlamaModel* getModel(jlong handle) {
    return reinterpret_cast<LlamaModel*>(handle);
}

static inline LlamaTokenizer* getTokenizer(jlong handle) {
    return reinterpret_cast<LlamaTokenizer*>(handle);
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

    jclass healthClass = env->FindClass("com/jarvis/app/model/adapters/LocalInferenceEngine$NativeHealth");
    if (!healthClass) return nullptr;

    jmethodID constructor = env->GetMethodID(healthClass, "<init>", "(Lcom/jarvis/app/model/adapters/LocalInferenceEngine$NativeState;ZZLjava/lang/String;JJJFJJJJF)V");
    if (!constructor) return nullptr;

    // Map state
    jclass stateClass = env->FindClass("com/jarvis/app/model/adapters/LocalInferenceEngine$NativeState");
    jobject stateEnum = nullptr;
    if (stateClass) {
        jfieldID valuesField = env->GetStaticFieldID(stateClass, "values", "[Lcom/jarvis/app/model/adapters/LocalInferenceEngine$NativeState;");
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
        health.load_time_ms,
        health.first_token_ms,
        health.tokens_per_sec,
        health.peak_ram_bytes,
        health.current_ram_bytes,
        health.prompt_tokens,
        health.completion_tokens,
        health.total_tokens,
        health.cpu_percent
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
    if (!model) return nullptr;

    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);

    // Parse generation config
    LlamaGenerationConfig nativeConfig{};
    nativeConfig.max_tokens = 512;
    nativeConfig.temperature = 0.7f;
    nativeConfig.top_p = 0.9f;
    nativeConfig.top_k = 40;
    nativeConfig.repeat_penalty = 1.1f;
    nativeConfig.stop_sequences = nullptr;
    nativeConfig.stop_sequence_count = 0;
    nativeConfig.stream = false;

    if (genConfig) {
        jclass configClass = env->GetObjectClass(genConfig);

        jfieldID maxTokensField = env->GetFieldID(configClass, "maxTokens", "I");
        nativeConfig.max_tokens = env->GetIntField(genConfig, maxTokensField);

        jfieldID tempField = env->GetFieldID(configClass, "temperature", "F");
        nativeConfig.temperature = env->GetFloatField(genConfig, tempField);

        jfieldID topPField = env->GetFieldID(configClass, "topP", "F");
        nativeConfig.top_p = env->GetFloatField(genConfig, topPField);

        jfieldID topKField = env->GetFieldID(configClass, "topK", "I");
        nativeConfig.top_k = env->GetIntField(genConfig, topKField);

        jfieldID repeatPenaltyField = env->GetFieldID(configClass, "repeatPenalty", "F");
        nativeConfig.repeat_penalty = env->GetFloatField(genConfig, repeatPenaltyField);

        jfieldID streamField = env->GetFieldID(configClass, "stream", "Z");
        nativeConfig.stream = env->GetBooleanField(genConfig, streamField);

        // Stop sequences
        jfieldID stopSeqField = env->GetFieldID(configClass, "stopSequences", "[Ljava/lang/String;");
        jobjectArray stopSeqArray = (jobjectArray)env->GetObjectField(genConfig, stopSeqField);
        if (stopSeqArray) {
            int count = env->GetArrayLength(stopSeqArray);
            nativeConfig.stop_sequence_count = count;
            if (count > 0) {
                // Note: We can't easily pass stop sequences to native without storing them
                // For now, we'll use a static buffer approach
            }
        }
    }

    // For streaming callback, we'd need to store the callback and use JNI to call back
    // For now, we'll implement non-streaming version
    LlamaTokenCallback tokenCb = nullptr;
    void* userData = nullptr;

    const char* result = llama_model_generate(model, promptStr, &nativeConfig, tokenCb, userData);

    env->ReleaseStringUTFChars(prompt, promptStr);

    if (result) {
        jstring jResult = env->NewStringUTF(result);
        free((void*)result);  // Native strdup
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
    if (!model) return 0;

    const char* textStr = env->GetStringUTFChars(text, nullptr);
    int count = llama_tokenizer_count(llama_tokenizer_get(model), textStr);
    env->ReleaseStringUTFChars(text, textStr);
    return count;
}

// Encode
JNIEXPORT jint JNICALL
JNI_CLASS(nativeEncode)(JNIEnv* env, jobject thiz, jlong handle, jstring text, jintArray output) {
    LlamaModel* model = getModel(handle);
    if (!model) return -1;

    const char* textStr = env->GetStringUTFChars(text, nullptr);
    jint* outData = env->GetIntArrayElements(output, nullptr);
    int count = llama_tokenizer_encode(llama_tokenizer_get(model), textStr, reinterpret_cast<int32_t*>(outData), env->GetArrayLength(output));
    env->ReleaseStringUTFChars(text, textStr);
    env->ReleaseIntArrayElements(output, outData, 0);
    return count;
}

// Decode
JNIEXPORT jstring JNICALL
JNI_CLASS(nativeDecode)(JNIEnv* env, jobject thiz, jlong handle, jintArray tokens) {
    LlamaModel* model = getModel(handle);
    if (!model) return nullptr;

    jint* tokenData = env->GetIntArrayElements(tokens, nullptr);
    int count = env->GetArrayLength(tokens);
    const char* result = llama_tokenizer_decode(llama_tokenizer_get(model), reinterpret_cast<const int32_t*>(tokenData), count);
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
    return env->NewStringUTF(llama_version());
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