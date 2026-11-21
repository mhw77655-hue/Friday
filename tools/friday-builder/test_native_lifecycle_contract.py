from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
CPP = ROOT / "mobile/app/src/main/cpp/local_inference_engine.cpp"
JNI = ROOT / "mobile/app/src/main/cpp/llama_jni.cpp"
HEADER = ROOT / "mobile/app/src/main/cpp/llama_jni.h"
KOTLIN = ROOT / "mobile/app/src/main/java/ai/friday/assistant/model/adapters/LocalInferenceEngine.kt"


class NativeLifecycleContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cpp = CPP.read_text(encoding="utf-8")
        cls.jni = JNI.read_text(encoding="utf-8")
        cls.header = HEADER.read_text(encoding="utf-8")
        cls.kotlin = KOTLIN.read_text(encoding="utf-8")

    def test_prewarm_thread_is_owned_and_joined(self):
        self.assertNotIn(".detach()", self.cpp)
        self.assertIn("prewarm_thread.join()", self.cpp)
        self.assertIn("prewarm_thread_mutex", self.cpp)

    def test_locked_native_paths_use_non_recursive_state_helpers(self):
        generate = self.cpp.split("const char* llama_model_generate(", 1)[1].split(
            "void llama_model_cancel(", 1
        )[0]
        suspend = self.cpp.split("bool llama_model_suspend(", 1)[1].split(
            "bool llama_model_resume(", 1
        )[0]
        destroy = self.cpp.split("void llama_model_destroy(", 1)[1].split(
            "// Tokenizer implementation", 1
        )[0]
        self.assertIn("set_state_under_lock", generate)
        self.assertIn("set_error_under_lock", generate)
        self.assertNotIn("set_state_locked", generate)
        self.assertNotIn("set_error_locked", generate)
        self.assertIn("set_state_under_lock", suspend)
        self.assertIn("set_state_under_lock", destroy)

    def test_model_lifetime_owns_jni_path_and_backend_reference(self):
        self.assertIn("std::string model_path", self.cpp)
        self.assertIn("model->config.model_path = model->model_path.c_str()", self.cpp)
        self.assertIn("acquire_llama_backend()", self.cpp)
        self.assertIn("release_llama_backend()", self.cpp)
        self.assertIn("g_backend_users", self.cpp)

    def test_ready_wait_accepts_repeated_generation_from_idle(self):
        wait = self.cpp.split("bool llama_model_wait_ready(", 1)[1].split(
            "const char* llama_model_generate(", 1
        )[0]
        self.assertIn("LLAMA_STATE_READY || model->state == LLAMA_STATE_IDLE", wait)
        self.assertIn("return model->state == LLAMA_STATE_READY || model->state == LLAMA_STATE_IDLE", wait)

    def test_jni_streaming_callback_is_connected_and_handle_is_guarded(self):
        self.assertIn('GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;IZ)V")', self.jni)
        self.assertIn("tokenCb = java_token_callback", self.jni)
        self.assertIn("nativeGenerate(handle, prompt, genConfig, object : TokenCallback", self.kotlin)
        self.assertIn("ReentrantReadWriteLock", self.kotlin)
        self.assertIn("nativeLock.writeLock()", self.kotlin)
        self.assertIn("cancelNativeIfAlive()", self.kotlin)

    def test_tokenizer_handles_have_an_explicit_release_path(self):
        self.assertIn("void llama_tokenizer_destroy(LlamaTokenizer* tokenizer)", self.header)
        self.assertIn("void llama_tokenizer_destroy(LlamaTokenizer* tokenizer)", self.cpp)
        self.assertGreaterEqual(self.jni.count("llama_tokenizer_destroy(tokenizer)"), 3)


if __name__ == "__main__":
    unittest.main()
