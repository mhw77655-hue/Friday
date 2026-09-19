import json
import os
import signal
import subprocess
import time
import urllib.request
import urllib.error

REPO = "/sdcard/jarvis-repo"
LLAMA = "/root/llama.cpp/build/bin/llama-server"
MODEL_DIR = f"{REPO}/models"

MODELS = {
    "lfm": {
        "id": "lfm2.5-1.2b-instruct",
        "path": f"{MODEL_DIR}/LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
        "port": 8080,
    },
    "qwen": {
        "id": "qwen3-1.7b-q4",
        "path": f"{MODEL_DIR}/Qwen3-1.7B-Q4_K_M.gguf",
        "port": 8080,
    },
    "qwen-q8": {
        "id": "qwen3-1.7b-q8",
        "path": f"{MODEL_DIR}/Qwen3-1.7B-Q8_0.gguf",
        "port": 8080,
    },
}


def server_running():
    try:
        with urllib.request.urlopen(
            "http://127.0.0.1:8080/health",
            timeout=2
        ) as r:
            return r.status == 200
    except Exception:
        return False


def current_processes():
    try:
        result = subprocess.check_output(
            ["pgrep", "-af", "llama-server"],
            text=True
        )
        return result.strip()
    except subprocess.CalledProcessError:
        return ""


def stop():
    subprocess.run(
        ["pkill", "-x", "llama-server"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )

    for _ in range(20):
        if not current_processes():
            return True
        time.sleep(0.25)

    return False


def start(name):
    if name not in MODELS:
        raise ValueError(f"Unknown model: {name}")

    model = MODELS[name]

    stop()

    command = [
        LLAMA,
        "-m", model["path"],
        "--host", "127.0.0.1",
        "--port", str(model["port"]),
        "-c", "4096",
        "-ngl", "0",
        "-np", "1",
    ]

    process = subprocess.Popen(
        command,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
    )

    for _ in range(120):
        if server_running():
            return {
                "status": "online",
                "model": name,
                "pid": process.pid,
                "port": model["port"],
            }

        if process.poll() is not None:
            raise RuntimeError(
                f"Model process exited with code {process.returncode}"
            )

        time.sleep(0.5)

    process.kill()
    raise RuntimeError("Timed out waiting for model server")


def status():
    return {
        "server_online": server_running(),
        "processes": current_processes(),
    }


if __name__ == "__main__":
    print(json.dumps(status(), indent=2))
