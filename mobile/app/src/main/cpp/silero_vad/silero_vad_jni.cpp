/**
 * JNI Bridge for Silero VAD
 * Maps Kotlin SileroVadManager calls to native C++ engine
 */
#include "silero_vad_jni.h"
#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "SileroVadJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ------------------------------------------------------------------
// JNI Helper macros
// ------------------------------------------------------------------
#define JNI_CLASS(name) Java_com_jarvis_app_vad_SileroVadManager_##name
#define JNIEXPORT_JNICALL JNIEXPORT jlong JNICALL

// ------------------------------------------------------------------
// Native handle management
// ------------------------------------------------------------------
static inline SileroVadEngine* getEngine(jlong handle) {
    return reinterpret_cast<SileroVadEngine*>(handle);
}

// ------------------------------------------------------------------
// JNI Implementation
// ------------------------------------------------------------------
extern "C" {

// Create
JNIEXPORT jlong JNICALL
JNI_CLASS(nativeCreate)(JNIEnv* env, jobject thiz, jobject config) {
    SileroVadEngine::SileroVadConfig nativeConfig{};

    // Extract config fields
    jclass configClass = env->GetObjectClass(config);

    jfieldID modelPathField = env->GetFieldID(configClass, "modelPath", "Ljava/lang/String;");
    jstring modelPathStr = (jstring)env->GetObjectField(config, modelPathField);
    const char* modelPath = env->GetStringUTFChars(modelPathStr, nullptr);
    nativeConfig.model_path = modelPath;

    jfieldID sampleRateField = env->GetFieldID(configClass, "sampleRate", "I");
    nativeConfig.sample_rate = env->GetIntField(config, sampleRateField);

    jfieldID thresholdField = env->GetFieldID(configClass, "threshold", "F");
    nativeConfig.threshold = env->GetFloatField(config, thresholdField);

    jfieldID minSpeechField = env->GetFieldID(configClass, "minSpeechDurationMs", "I");
    nativeConfig.min_speech_duration_ms = env->GetIntField(config, minSpeechField);

    jfieldID minSilenceField = env->GetFieldID(configClass, "minSilenceDurationMs", "I");
    nativeConfig.min_silence_duration_ms = env->GetIntField(config, minSilenceField);

    jfieldID speechPadField = env->GetFieldID(configClass, "speechPadMs", "I");
    nativeConfig.speech_pad_ms = env->GetIntField(config, speechPadField);

    jfieldID windowSizeField = env->GetFieldID(configClass, "windowSizeSamples", "I");
    nativeConfig.window_size_samples = env->GetIntField(config, windowSizeField);

    jfieldID useGpuField = env->GetFieldID(configClass, "useGpu", "Z");
    nativeConfig.use_gpu = env->GetBooleanField(config, useGpuField);

    jfieldID numThreadsField = env->GetFieldID(configClass, "numThreads", "I");
    nativeConfig.num_threads = env->GetIntField(config, numThreadsField);

    env->ReleaseStringUTFChars(modelPathStr, modelPath);

    SileroVadEngine* engine = silero_vad_create(&nativeConfig);
    return reinterpret_cast<jlong>(engine);
}

// Destroy
JNIEXPORT void JNICALL
JNI_CLASS(nativeDestroy)(JNIEnv* env, jobject thiz, jlong handle) {
    SileroVadEngine* engine = getEngine(handle);
    if (engine) {
        silero_vad_destroy(engine);
    }
}

// Get state
JNIEXPORT jint JNICALL
JNI_CLASS(nativeGetState)(JNIEnv* env, jobject thiz, jlong handle) {
    SileroVadEngine* engine = getEngine(handle);
    if (!engine) return -1;
    return static_cast<jint>(silero_vad_get_state(engine));
}

// Process audio
JNIEXPORT jobject JNICALL
JNI_CLASS(nativeProcess)(JNIEnv* env, jobject thiz, jlong handle, jshortArray audio, jint numSamples, jlong timestampMs) {
    SileroVadEngine* engine = getEngine(handle);
    if (!engine) return nullptr;

    jshort* audioData = env->GetShortArrayElements(audio, nullptr);
    SileroVadResult result = silero_vad_process(engine, reinterpret_cast<const int16_t*>(audioData), numSamples, timestampMs);
    env->ReleaseShortArrayElements(audio, audioData, JNI_ABORT);

    // Create Result object
    jclass resultClass = env->FindClass("com/jarvis/app/vad/SileroVadManager$Result");
    if (!resultClass) {
        LOGE("Result class not found");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(Lcom/jarvis/app/vad/SileroVadManager$State;FZJI)V");
    if (!constructor) {
        LOGE("Result constructor not found");
        return nullptr;
    }

    // Map state ordinal to State enum
    jclass stateClass = env->FindClass("com/jarvis/app/vad/SileroVadManager$State");
    jobject stateEnum = nullptr;
    if (stateClass) {
        jfieldID stateField = env->GetStaticFieldID(stateClass, "values", "[Lcom/jarvis/app/vad/SileroVadManager$State;");
        if (stateField) {
            jobjectArray states = (jobjectArray)env->GetStaticObjectField(stateClass, stateField);
            if (states && result.state < env->GetArrayLength(states)) {
                stateEnum = env->GetObjectArrayElement(states, result.state);
            }
        }
    }

    jobject resultObj = env->NewObject(resultClass, constructor,
        stateEnum,
        result.speech_probability,
        result.speech_detected,
        result.timestamp_ms,
        result.audio_level
    );

    return resultObj;
}

// Process with callbacks - simplified for now
JNIEXPORT void JNICALL
JNI_CLASS(nativeProcessWithCallbacks)(JNIEnv* env, jobject thiz, jlong handle, jshortArray audio, jint numSamples, jlong timestampMs, jobject onSpeechStart, jobject onSpeechEnd, jlong userData) {
    // Callbacks are handled on Kotlin side for simplicity
    SileroVadEngine* engine = getEngine(handle);
    if (!engine) return;

    jshort* audioData = env->GetShortArrayElements(audio, nullptr);
    silero_vad_process(engine, reinterpret_cast<const int16_t*>(audioData), numSamples, timestampMs);
    env->ReleaseShortArrayElements(audio, audioData, JNI_ABORT);
}

// Reset
JNIEXPORT void JNICALL
JNI_CLASS(nativeReset)(JNIEnv* env, jobject thiz, jlong handle) {
    SileroVadEngine* engine = getEngine(handle);
    if (engine) {
        silero_vad_reset(engine);
    }
}

// Update config
JNIEXPORT jboolean JNICALL
JNI_CLASS(nativeUpdateConfig)(JNIEnv* env, jobject thiz, jlong handle, jobject config) {
    SileroVadEngine* engine = getEngine(handle);
    if (!engine) return JNI_FALSE;

    // Same extraction as nativeCreate
    SileroVadEngine::SileroVadConfig nativeConfig{};
    jclass configClass = env->GetObjectClass(config);

    jfieldID modelPathField = env->GetFieldID(configClass, "modelPath", "Ljava/lang/String;");
    jstring modelPathStr = (jstring)env->GetObjectField(config, modelPathField);
    const char* modelPath = env->GetStringUTFChars(modelPathStr, nullptr);
    nativeConfig.model_path = modelPath;

    jfieldID sampleRateField = env->GetFieldID(configClass, "sampleRate", "I");
    nativeConfig.sample_rate = env->GetIntField(config, sampleRateField);

    jfieldID thresholdField = env->GetFieldID(configClass, "threshold", "F");
    nativeConfig.threshold = env->GetFloatField(config, thresholdField);

    jfieldID minSpeechField = env->GetFieldID(configClass, "minSpeechDurationMs", "I");
    nativeConfig.min_speech_duration_ms = env->GetIntField(config, minSpeechField);

    jfieldID minSilenceField = env->GetFieldID(configClass, "minSilenceDurationMs", "I");
    nativeConfig.min_silence_duration_ms = env->GetIntField(config, minSilenceField);

    jfieldID speechPadField = env->GetFieldID(configClass, "speechPadMs", "I");
    nativeConfig.speech_pad_ms = env->GetIntField(config, speechPadField);

    jfieldID windowSizeField = env->GetFieldID(configClass, "windowSizeSamples", "I");
    nativeConfig.window_size_samples = env->GetIntField(config, windowSizeField);

    jfieldID useGpuField = env->GetFieldID(configClass, "useGpu", "Z");
    nativeConfig.use_gpu = env->GetBooleanField(config, useGpuField);

    jfieldID numThreadsField = env->GetFieldID(configClass, "numThreads", "I");
    nativeConfig.num_threads = env->GetIntField(config, numThreadsField);

    env->ReleaseStringUTFChars(modelPathStr, modelPath);

    return silero_vad_update_config(engine, &nativeConfig) ? JNI_TRUE : JNI_FALSE;
}

// Get config
JNIEXPORT jobject JNICALL
JNI_CLASS(nativeGetConfig)(JNIEnv* env, jobject thiz, jlong handle) {
    SileroVadEngine* engine = getEngine(handle);
    if (!engine) return nullptr;

    SileroVadConfig config = silero_vad_get_config(engine);

    jclass configClass = env->FindClass("com/jarvis/app/vad/SileroVadManager$Config");
    if (!configClass) return nullptr;

    jmethodID constructor = env->GetMethodID(configClass, "<init>", "(Ljava/lang/String;IFFIIIIZI)V");
    if (!constructor) return nullptr;

    jstring modelPath = env->NewStringUTF(config.model_path ? config.model_path : "");
    jobject configObj = env->NewObject(configClass, constructor,
        modelPath,
        config.sample_rate,
        config.threshold,
        config.min_speech_duration_ms,
        config.min_silence_duration_ms,
        config.speech_pad_ms,
        config.window_size_samples,
        config.use_gpu,
        config.num_threads
    );

    env->DeleteLocalRef(modelPath);
    return configObj;
}

// Apply profile
JNIEXPORT jboolean JNICALL
JNI_CLASS(nativeApplyProfile)(JNIEnv* env, jobject thiz, jlong handle, jint profile) {
    SileroVadEngine* engine = getEngine(handle);
    if (!engine) return JNI_FALSE;
    return silero_vad_apply_profile(engine, static_cast<SileroVadProfile>(profile)) ? JNI_TRUE : JNI_FALSE;
}

// Get profile config
JNIEXPORT jobject JNICALL
JNI_CLASS(nativeGetProfileConfig)(JNIEnv* env, jobject thiz, jint profile, jint sampleRate) {
    SileroVadConfig config = silero_vad_get_profile_config(static_cast<SileroVadProfile>(profile), sampleRate);

    jclass configClass = env->FindClass("com/jarvis/app/vad/SileroVadManager$Config");
    if (!configClass) return nullptr;

    jmethodID constructor = env->GetMethodID(configClass, "<init>", "(Ljava/lang/String;IFFIIIIZI)V");
    if (!constructor) return nullptr;

    jstring modelPath = env->NewStringUTF("");
    jobject configObj = env->NewObject(configClass, constructor,
        modelPath,
        config.sample_rate,
        config.threshold,
        config.min_speech_duration_ms,
        config.min_silence_duration_ms,
        config.speech_pad_ms,
        config.window_size_samples,
        config.use_gpu,
        config.num_threads
    );

    env->DeleteLocalRef(modelPath);
    return configObj;
}

// Get metrics
JNIEXPORT jobject JNICALL
JNI_CLASS(nativeGetMetrics)(JNIEnv* env, jobject thiz, jlong handle) {
    SileroVadEngine* engine = getEngine(handle);
    if (!engine) return nullptr;

    SileroVadMetrics metrics = silero_vad_get_metrics(engine);

    jclass metricsClass = env->FindClass("com/jarvis/app/vad/SileroVadManager$Metrics");
    if (!metricsClass) return nullptr;

    jmethodID constructor = env->GetMethodID(metricsClass, "<init>", "(JJJFFJJZLjava/lang/String;)V");
    if (!constructor) return nullptr;

    jstring errorMsg = metrics.error_message ? env->NewStringUTF(metrics.error_message) : nullptr;
    jobject metricsObj = env->NewObject(metricsClass, constructor,
        metrics.total_chunks_processed,
        metrics.speech_chunks,
        metrics.silence_chunks,
        metrics.avg_speech_probability,
        metrics.avg_inference_ms,
        metrics.peak_ram_bytes,
        metrics.current_ram_bytes,
        metrics.model_loaded,
        errorMsg
    );

    if (errorMsg) env->DeleteLocalRef(errorMsg);
    return metricsObj;
}

// Is healthy
JNIEXPORT jboolean JNICALL
JNI_CLASS(nativeIsHealthy)(JNIEnv* env, jobject thiz, jlong handle) {
    SileroVadEngine* engine = getEngine(handle);
    if (!engine) return JNI_FALSE;
    return silero_vad_is_healthy(engine) ? JNI_TRUE : JNI_FALSE;
}

// Version
JNIEXPORT jstring JNICALL
JNI_CLASS(nativeVersion)(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(silero_vad_version());
}

JNIEXPORT jstring JNICALL
JNI_CLASS(nativeOnnxVersion)(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(silero_vad_onnxruntime_version());
}

} // extern "C"