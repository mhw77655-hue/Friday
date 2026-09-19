#!/bin/bash

REPO="/sdcard/jarvis-repo"

echo "===== JARVIS ENVIRONMENT ====="
echo "Repository: $REPO"
echo

echo "Node:"
node --version 2>/dev/null || echo "missing"

echo "Python:"
python3 --version 2>/dev/null || echo "missing"

echo "Git:"
git --version 2>/dev/null || echo "missing"

echo "Cline:"
cline --version 2>/dev/null || echo "not configured/available"

echo "llama-server:"
/root/llama.cpp/build/bin/llama-server --version 2>/dev/null | head -1 || echo "missing"

echo
echo "===== MODEL FILES ====="
ls -lh \
  "$REPO/models/LFM2.5-1.2B-Instruct-Q4_K_M.gguf" \
  "$REPO/models/Qwen3-1.7B-Q4_K_M.gguf"

echo
echo "===== MODEL SERVER ====="
if curl -sf http://127.0.0.1:8080/health >/dev/null 2>&1; then
    echo "LFM: ONLINE"
else
    echo "LFM: OFFLINE"
fi

echo
echo "===== MEMORY ====="
free -h

echo
echo "===== GIT ====="
git status --short | head -80
