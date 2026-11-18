"""Static regression guards for the shipped JNI/native inference lifecycle.

These checks prevent known source-level hazards from silently returning. They do
not replace native runtime tests on an Android device.
"""
import unittest
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CPP = (ROOT / "mobile/app/src/main/cpp/local_inference_engine.cpp").read_text(encoding="utf-8")
JNI = (ROOT / "mobile/app/src/main/cpp/llama_jni.cpp").read_text(encoding="utf-8")
KOTLIN = (ROOT / "mobile/app/src/main/java/com/jarvis/app/model/adapters/LocalInferenceEngine.kt").read_text(encoding="utf-8")


def function_body(source: str, start: str, end: str) -> str:
    return source.split(start, 1)[1].split(end, 1)[0]


class NativeInferenceLifecycleContractTests(unittest.TestCase):
    def test_native_generation_does_not_relock_the_mutex_it_already_holds(self):
        body = function_body(CPP, "const char* llama_model_generate(", "\nvoid llama_model_cancel(")
        locked_section = body[body.index("std::lock_guard<std::mutex> lock(model->mutex)"):]
        self.assertIn("set_state_unlocked(model, LLAMA_STATE_GENERATING)", locked_section)
        self.assertIn("set_state_unlocked(model, LLAMA_STATE_IDLE)", locked_section)
        self.assertNotIn("set_state_locked(model,", locked_section)
        self.assertNotIn("set_error_locked(model,", locked_section)

    def test_suspend_and_destroy_use_unlocked_state_transitions_inside_their_lock(self):
        suspend = function_body(CPP, "bool llama_model_suspend(", "\nbool llama_model_resume(")
        destroy = function_body(CPP, "void llama_model_destroy(", "\n// ------------------------------------------------------------------\n// Tokenizer implementation")
        self.assertIn("set_state_unlocked(model, LLAMA_STATE_SUSPENDED)", suspend)
        self.assertNotIn("set_state_locked(model,", suspend)
        self.assertIn("set_state_unlocked(model, LLAMA_STATE_UNLOADING)", destroy)
        self.assertNotIn("set_state_locked(model,", destroy)
        self.assertNotIn(".detach()", CPP)
        self.assertLess(destroy.index("} // Release prewarm_mutex"), destroy.index("delete model;"))

    def test_backend_initialization_is_process_scoped_not_per_model(self):
        self.assertIn("std::call_once(g_backend_once", CPP)
        self.assertIn("std::atexit(release_backend_at_process_exit)", CPP)
        self.assertNotIn("release_backend();", CPP)

    def test_prewarm_thread_is_serialized_and_joined_before_model_release(self):
        self.assertIn("std::mutex prewarm_mutex", CPP)
        self.assertIn("std::lock_guard<std::mutex> thread_lock(model->prewarm_mutex)", CPP)
        self.assertIn("if (model->prewarm_thread.joinable()) model->prewarm_thread.join();", CPP)
        self.assertNotIn("model->prewarm_thread.detach()", CPP)

    def test_companion_native_declarations_are_exported_as_outer_class_statics(self):
        declarations = re.findall(r"^\\s*external fun native\\w+", KOTLIN, re.MULTILINE)
        annotated = re.findall(r"@JvmStatic\\s+external fun native\\w+", KOTLIN)
        self.assertGreater(len(declarations), 0)
        self.assertEqual(len(declarations), len(annotated))

    def test_health_jni_uses_real_companion_classes_enum_method_and_constructor_shape(self):
        self.assertIn('LocalInferenceEngine$Companion$NativeHealth', JNI)
        self.assertIn('LocalInferenceEngine$Companion$NativeState', JNI)
        self.assertIn('GetStaticMethodID(\\n        stateClass,\\n        "values"', JNI)
        self.assertNotIn('GetStaticFieldID(stateClass, "values"', JNI)
        self.assertIn('(Lcom/jarvis/app/model/adapters/LocalInferenceEngine$Companion$NativeState;ZZLjava/lang/String;JJJFJJJJJF)V', JNI)

    def test_tokenizer_decode_owned_buffer_is_freed_after_java_copy(self):
        decode = function_body(JNI, 'JNI_CLASS(nativeDecode)', '\\n// Version')
        self.assertIn('NewStringUTF(result)', decode)
        self.assertIn('free((void*)result)', decode)

    def test_jni_model_path_remains_valid_until_native_create_copies_it(self):
        create = function_body(JNI, "JNI_CLASS(nativeCreate)", "\n// Get state")
        self.assertLess(create.index("llama_model_create(&nativeConfig)"), create.index("ReleaseStringUTFChars(modelPathStr, modelPath)"))

    def test_every_jni_tokenizer_wrapper_is_released(self):
        self.assertEqual(JNI.count("llama_tokenizer_get(model)"), JNI.count("llama_tokenizer_free(tokenizer)"))
        self.assertEqual(JNI.count("llama_tokenizer_get(model)"), 3)

    def test_streaming_callback_and_stop_sequences_reach_native_generation(self):
        self.assertIn("tokenCb = forward_token_to_java", JNI)
        self.assertIn('GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;IZ)V")', JNI)
        self.assertIn("nativeConfig.stop_sequences = stopPointers.empty() ? nullptr : stopPointers.data()", JNI)


if __name__ == "__main__":
    unittest.main()
