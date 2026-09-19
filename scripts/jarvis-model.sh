#!/bin/bash

set -e

LLAMA="/root/llama.cpp/build/bin/llama-server"
MODEL_DIR="/sdcard/jarvis-repo/models"

stop_models() {
    pkill -x llama-server 2>/dev/null || true
    sleep 2
}

start_lfm() {
    stop_models

    exec "$LLAMA" \
      -m "$MODEL_DIR/LFM2.5-1.2B-Instruct-Q4_K_M.gguf" \
      --host 127.0.0.1 \
      --port 8080 \
      -c 4096 \
      -ngl 0 \
      -np 1
}

start_qwen() {
    stop_models

    exec "$LLAMA" \
      -m "$MODEL_DIR/Qwen3-1.7B-Q4_K_M.gguf" \
      --host 127.0.0.1 \
      --port 8080 \
      -c 4096 \
      -ngl 0 \
      -np 1
}

start_qwen_q8() {
    stop_models

    exec "$LLAMA" \
      -m "$MODEL_DIR/Qwen3-1.7B-Q8_0.gguf" \
      --host 127.0.0.1 \
      --port 8080 \
      -c 4096 \
      -ngl 0 \
      -np 1
}

case "${1:-}" in
    lfm)
        start_lfm
        ;;
    qwen)
        start_qwen
        ;;
    qwen-q8)
        start_qwen_q8
        ;;
    stop)
        stop_models
        ;;
    *)
        echo "Usage: $0 {lfm|qwen|qwen-q8|stop}"
        exit 1
        ;;
esac
