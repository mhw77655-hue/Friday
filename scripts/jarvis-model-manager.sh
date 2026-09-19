#!/bin/bash

set -e

LLAMA="/root/llama.cpp/build/bin/llama-server"
MODEL_DIR="/sdcard/jarvis-repo/models"

LFM="$MODEL_DIR/LFM2.5-1.2B-Instruct-Q4_K_M.gguf"
QWEN="$MODEL_DIR/Qwen3-1.7B-Q4_K_M.gguf"
QWEN_Q8="$MODEL_DIR/Qwen3-1.7B-Q8_0.gguf"

stop_model() {
    pkill -x llama-server 2>/dev/null || true
    sleep 2
}

health() {
    curl -sf "http://127.0.0.1:8080/health" >/dev/null
}

start_lfm() {
    stop_model

    "$LLAMA" \
      -m "$LFM" \
      --host 127.0.0.1 \
      --port 8080 \
      -c 4096 \
      -ngl 0 \
      -np 1
}

start_qwen() {
    stop_model

    "$LLAMA" \
      -m "$QWEN" \
      --host 127.0.0.1 \
      --port 8080 \
      -c 4096 \
      -ngl 0 \
      -np 1
}

start_qwen_q8() {
    stop_model

    "$LLAMA" \
      -m "$QWEN_Q8" \
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
    stop_model
    ;;
  health)
    if health; then
      echo "MODEL SERVER: ONLINE"
    else
      echo "MODEL SERVER: OFFLINE"
      exit 1
    fi
    ;;
  *)
    echo "Usage: bash $0 {lfm|qwen|qwen-q8|stop|health}"
    exit 1
    ;;
esac
