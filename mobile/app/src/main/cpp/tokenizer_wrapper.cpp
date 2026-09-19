/**
 * Tokenizer wrapper for Qwen3 tokenizer
 * Provides token counting and encoding for context budgeting
 */
#include "llama_jni.h"
#include <string>
#include <vector>
#include <unordered_map>
#include <fstream>
#include <sstream>
#include <android/log.h>

#define LOG_TAG "Qwen3Tokenizer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct Qwen3Tokenizer {
    std::unordered_map<std::string, int> vocab;
    std::unordered_map<int, std::string> id_to_token;
    std::vector<std::string> merges;
    int vocab_size = 0;
    int unk_token_id = 0;
    int bos_token_id = 1;
    int eos_token_id = 2;
    int pad_token_id = 3;
};

extern "C" {

// Simple BPE tokenizer for Qwen3 (uses tokenizer.json)
Qwen3Tokenizer* qwen3_tokenizer_create(const char* tokenizer_json_path) {
    if (!tokenizer_json_path) return nullptr;

    auto* tokenizer = new Qwen3Tokenizer();

    // Parse tokenizer.json
    std::ifstream file(tokenizer_json_path);
    if (!file.is_open()) {
        LOGE("Failed to open tokenizer.json: %s", tokenizer_json_path);
        delete tokenizer;
        return nullptr;
    }

    std::stringstream buffer;
    buffer << file.rdbuf();
    std::string json = buffer.str();

    // Simple JSON parsing for vocab (in production, use proper JSON parser)
    // This is a minimal implementation - for production, integrate rapidjson or similar
    size_t vocab_pos = json.find("\"vocab\"");
    if (vocab_pos != std::string::npos) {
        size_t start = json.find('{', vocab_pos);
        size_t end = json.find('}', start);
        if (start != std::string::npos && end != std::string::npos) {
            std::string vocab_str = json.substr(start, end - start + 1);
            // Parse key-value pairs (very simplified)
            // In reality, this needs a proper JSON parser
            LOGI("Tokenizer vocab section found, size: %zu", vocab_str.size());
        }
    }

    size_t merges_pos = json.find("\"merges\"");
    if (merges_pos != std::string::npos) {
        size_t start = json.find('[', merges_pos);
        size_t end = json.find(']', start);
        if (start != std::string::npos && end != std::string::npos) {
            LOGI("Tokenizer merges section found");
        }
    }

    // For now, use a simple character-level fallback
    // Real implementation would parse the full BPE vocab
    tokenizer->vocab_size = 151643; // Qwen3 vocab size
    LOGI("Qwen3Tokenizer created (fallback mode)");
    return tokenizer;
}

void qwen3_tokenizer_destroy(Qwen3Tokenizer* tokenizer) {
    if (tokenizer) delete tokenizer;
}

int qwen3_tokenizer_encode(Qwen3Tokenizer* tokenizer, const char* text, int32_t* output, int max_tokens) {
    if (!tokenizer || !text || !output || max_tokens <= 0) return -1;

    // Fallback: simple UTF-8 byte tokenization
    // Real implementation would use BPE
    const unsigned char* bytes = reinterpret_cast<const unsigned char*>(text);
    int count = 0;
    while (*bytes && count < max_tokens) {
        output[count++] = *bytes + 3; // Offset by special tokens
        bytes++;
    }
    return count;
}

const char* qwen3_tokenizer_decode(Qwen3Tokenizer* tokenizer, const int32_t* tokens, int count) {
    if (!tokenizer || !tokens || count <= 0) return nullptr;

    std::string result;
    result.reserve(count);
    for (int i = 0; i < count; i++) {
        int id = tokens[i] - 3;
        if (id >= 0 && id < 256) {
            result.push_back(static_cast<char>(id));
        }
    }
    return strdup(result.c_str());
}

int qwen3_tokenizer_count(Qwen3Tokenizer* tokenizer, const char* text) {
    if (!tokenizer || !text) return -1;
    // Rough estimate: ~4 chars per token for English, ~1.5 for Chinese
    int len = strlen(text);
    return (len + 3) / 4;
}

// Integration with llama.cpp tokenizer
int llama_qwen_tokenize(const char* text, int32_t* output, int max_tokens) {
    // Use llama.cpp's tokenizer if available, else fallback
    return qwen3_tokenizer_count(nullptr, text);
}

} // extern "C"