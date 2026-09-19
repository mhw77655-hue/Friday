#!/bin/bash
set -e

REPO="/sdcard/jarvis-repo"
LLAMA="/root/llama.cpp/build/bin/llama-server"
MODEL="$REPO/models/LFM2.5-1.2B-Instruct-Q4_K_M.gguf"
PORT=8080

echo "=== JARVIS LOCAL STARTUP ==="
echo "Repository: $REPO"
echo "Model: LFM2.5-1.2B-Instruct-Q4_K_M"
echo "Endpoint: http://127.0.0.1:$PORT"

if pgrep -x llama-server >/dev/null 2>&1; then
    echo "llama-server already running."
else
    echo "Starting LFM..."
    "$LLAMA" \
      -m "$MODEL" \
      --host 127.0.0.1 \
      --port "$PORT" \
      -c 4096 \
      -ngl 0 \
      -np 1 \
      > "$REPO/logs_lfm_server.txt" 2>&1 &
fi

echo "Waiting for model server..."

for i in $(seq 1 120); do
    if curl -sf "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
        echo "JARVIS LOCAL MODEL: ONLINE"
        exit 0
    fi
    sleep 0.5
done

echo "ERROR: model server did not become ready."
echo "Check: $REPO/logs_lfm_server.txt"
exit 1
