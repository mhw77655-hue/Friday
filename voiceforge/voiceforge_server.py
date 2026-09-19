#!/usr/bin/env python3
"""VoiceForge server — local Chatterbox Multilingual TTS for JARVIS's spoken
replies (VOICE-FORGE-EGYPTIAN-KAREN-TTS + VOICE-FORGE-ACTIVATION-REAL-SPEECH).

Runs inside Termux on the same host as JARVIS. Backs the Kotlin
VoiceForgeAdapter (com.jarvis.app.voice) over plain HTTP. Zero platform TTS:
every JARVIS reply is synthesized here from the configured checkpoint, and an
Egyptian-fine-tuned checkpoint is routed the same Egyptian turns the Kotlin
backend already routes there.

Run (Termux):
    pkg install python ffmpeg
    pip install chatterbox-tts  # the runtime; server probes for it
    python voiceforge/voiceforge_server.py \
        --checkpoint NAMAA-Egyptian-TTS \
        --port 8765 \
        --token <shared-secret>

When chatterbox-tts is not importable the server stays up but answers /health
with healthy:false and /v1/synthesize with HTTP 503 — an honest, explicit
unavailable state (the Kotlin side falls back to platform TTS).

Security (VOICE-FORGE-ACTIVATION-REAL-SPEECH AC7-AC10):
- /health and /v1/synthesize require Authorization: Bearer <token> header.
- audio_prompt_path validated against an allow-listed directory.
- HuggingFace snapshot_download pins an explicit revision.
- Text payload capped at 2000 chars; concurrent synthesis serialized.
"""

import argparse
import base64
import io
import json
import os
import threading
import time
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOAD_LOCK = threading.Lock()
INFERENCE_LOCK = threading.Lock()
RUNTIME = {"model": None, "error": None, "checkpoint": None,
           "sample_rate": 24000, "languages": None}

DEFAULT_CHECKPOINT = "NAMAA-Egyptian-TTS"  # base weights = Egyptian fine-tune (AC1)
EGYPTIAN_CHECKPOINT = "NAMAA-Egyptian-TTS"

# AC9: the NAMAA-Space checkpoint is pinned to an explicit commit (queried via
# https://huggingface.co/api/models/NAMAA-Space/NAMAA-Egyptian-TTS on
# 2026-09-16) so a supply-chain change upstream can never silently swap what a
# fresh install loads. Only files under allow_patterns are downloaded.
PINNED_REVISION = "bee585b6bc3a3180e257f57d5fd84fa14c31db83"
NAMA_DL_ALLOW_PATTERNS = ["t3_mtl23ls_v2.safetensors"]

# AC8: audio_prompt_path is validated against this directory (the committed
# assets slot mobile/app/src/main/assets/voiceforge/). Relative prompts are
# resolved against the app-assets root (APP_ASSETS_ROOT) so
# "assets/voiceforge/venon_voice_reference.wav" — the path the Kotlin backend
# sends — resolves inside the allow-listed directory.
ASSETS_VOICEFORGE_DIR = os.path.realpath(
    os.path.join(os.path.dirname(__file__), "..", "mobile", "app", "src", "main",
                 "assets", "voiceforge")
)
# app-assets root is two levels up from the allow-listed dir:
#   mobile/app/src/main/assets/voiceforge -> mobile/app/src/main
APP_ASSETS_ROOT = os.path.realpath(os.path.join(ASSETS_VOICEFORGE_DIR, "..", ".."))

MAX_TEXT_LENGTH = 2000  # AC10: reject payloads exceeding this character count


def _try_load(checkpoint):
    try:
        from chatterbox.mtl_tts import ChatterboxMultilingualTTS
        from huggingface_hub import snapshot_download
        from safetensors.torch import load_file as load_safetensors
    except Exception as exc:  # noqa: BLE001 - surface as honest 503, not a crash
        RUNTIME["error"] = "chatterbox-tts not importable: %s" % exc
        return
    try:
        model = ChatterboxMultilingualTTS.from_pretrained(device="cpu")
        ckpt_dir = snapshot_download(
            repo_id="NAMAA-Space/" + checkpoint,
            repo_type="model",
            revision=PINNED_REVISION,
            allow_patterns=NAMA_DL_ALLOW_PATTERNS,
        )
        t3_state = load_safetensors(ckpt_dir + "/t3_mtl23ls_v2.safetensors", device="cpu")
        model.t3.load_state_dict(t3_state)
        model.t3.eval()
        RUNTIME["model"] = model
        RUNTIME["checkpoint"] = checkpoint
        RUNTIME["sample_rate"] = int(getattr(model, "sr", 24000))
        try:
            RUNTIME["languages"] = set(
                ChatterboxMultilingualTTS.get_supported_languages().keys())
        except Exception:  # noqa: BLE001 - languages stay unknown; sample still works
            RUNTIME["languages"] = None
    except Exception as exc:  # noqa: BLE001 - model download/first-load failure
        RUNTIME["error"] = "model load failed: %s" % exc


def _normalize_language(language):
    """Map an ISO-tagged language ("ar-EG", "en") onto a SUPPORTED_LANGUAGES
    key ("ar", "en")."""
    if not language:
        return None
    return language.split("-")[0].lower()


def _synthesize(text, language, exaggeration, cfg_weight, audio_prompt_path):
    """Run the real ChatterboxMultilingualTTS engine. Uses the RUNTIME-loaded
    model only: multilingual T3 base + the configured NAMAA-Space fine-tune.
    'checkpoint' is not an engine parameter — the loaded weights ARE the
    checkpoint; language_id is what actually steers the voice."""
    import numpy as np
    with INFERENCE_LOCK:
        with LOAD_LOCK:
            model = RUNTIME["model"]
            err = RUNTIME["error"]
        if model is None:
            detail = err or "runtime has no loaded checkpoint"
            raise RuntimeError(detail)
        language_id = _normalize_language(language)
        supported = RUNTIME["languages"]
        if language_id is None or (
                supported is not None and language_id not in supported):
            raise ValueError(
                "unsupported language_id %r (supported: %s)"
                % (language_id, sorted(supported or [])))
        result = model.generate(
            text=text,
            language_id=language_id,
            exaggeration=exaggeration,
            cfg_weight=cfg_weight,
            audio_prompt_path=audio_prompt_path,
        )
    sample_rate = int(RUNTIME["sample_rate"])
    audio = result[0]
    if hasattr(audio, "numpy"):
        audio = audio.numpy()
    if audio.dtype in (np.float32, np.float64):
        audio = (audio * 32767.0).astype(np.int16)
    else:
        audio = audio.astype(np.int16)
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(audio.tobytes())
    wav = buf.getvalue()
    seconds = float(len(audio)) / float(sample_rate)
    return {
        "wav_base64": base64.b64encode(wav).decode("ascii"),
        "sample_rate": sample_rate,
        "channels": 1,
        "duration_ms": int(seconds * 1000.0),
        "checkpoint": RUNTIME["checkpoint"] or DEFAULT_CHECKPOINT,
    }


_AUTH_TOKEN = None  # set from CLI --token / env VOICEFORGE_TOKEN


class _Handler(BaseHTTPRequestHandler):
    _server_version = "VoiceForge/1.0"

    def log_message(self, fmt, *args):  # keep the log line compact
        syslog = "[voiceforge] " + (fmt % args)
        print(syslog, flush=True)

    def _send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    # AC7: bearer-token gate for every endpoint.
    def _check_auth(self):
        if _AUTH_TOKEN is None:
            return True  # no token configured → open (dev mode)
        auth = self.headers.get("Authorization", "")
        if auth.startswith("Bearer ") and auth[7:] == _AUTH_TOKEN:
            return True
        self._send_json(401, {"error": "unauthorized"})
        return False

    # AC8: validate audio_prompt_path against the allow-listed directory.
    def _validate_audio_path(self, prompt):
        if not prompt:
            return None
        candidates = [os.path.realpath(prompt)]
        if not os.path.isabs(prompt):
            candidates.append(os.path.realpath(os.path.join(APP_ASSETS_ROOT, prompt)))
        for cand in candidates:
            if cand.startswith(ASSETS_VOICEFORGE_DIR + os.sep):
                return cand
        raise ValueError(
            "audio_prompt_path outside allow-listed directory: %s" % prompt
        )

    def do_GET(self):
        if self.path.split("?")[0] != "/health":
            self._send_json(404, {"error": "not found"})
            return
        if not self._check_auth():
            return
        self._send_json(200, {
            "healthy": RUNTIME["model"] is not None,
            "checkpoint": RUNTIME["checkpoint"] or DEFAULT_CHECKPOINT,
            "error": RUNTIME["error"],
            "uptime_s": int(time.time() - _START),
        })

    def do_POST(self):
        if self.path.split("?")[0] != "/v1/synthesize":
            self._send_json(404, {"error": "not found"})
            return
        if not self._check_auth():
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length).decode("utf-8") or "{}")
        except Exception as exc:  # noqa: BLE001 - malformed request body
            self._send_json(400, {"error": "bad request: %s" % exc})
            return
        text = payload.get("text", "")
        if not text:
            self._send_json(400, {"error": "empty text"})
            return
        # AC10: reject text exceeding the length cap.
        if len(text) > MAX_TEXT_LENGTH:
            self._send_json(400, {
                "error": "text exceeds %d character limit (%d provided)"
                         % (MAX_TEXT_LENGTH, len(text))
            })
            return
        try:
            out = _synthesize(
                text=text,
                language=payload.get("language", "en"),
                exaggeration=float(payload.get("exaggeration", 0.65)),
                cfg_weight=float(payload.get("cfg_weight", 1.7)),
                audio_prompt_path=self._validate_audio_path(
                    payload.get("audio_prompt_path")
                ),
            )
            self._send_json(200, out)
        except ValueError as exc:
            self._send_json(400, {"error": str(exc)})
        except RuntimeError as exc:
            self._send_json(503, {"error": str(exc)})
        except Exception as exc:  # noqa: BLE001 - synthesis engine error
            self._send_json(500, {"error": "synthesis failed: %s" % exc})


_START = time.time()


def main():
    global _AUTH_TOKEN
    parser = argparse.ArgumentParser(description="VoiceForge TTS server")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--checkpoint", default=DEFAULT_CHECKPOINT)
    parser.add_argument("--token", default=None,
                        help="Shared-secret bearer token for AC7 auth gating")
    args = parser.parse_args()
    # AC7: prefer CLI --token, fall back to env VOICEFORGE_TOKEN.
    _AUTH_TOKEN = args.token or os.environ.get("VOICEFORGE_TOKEN")
    _try_load(args.checkpoint)
    server = ThreadingHTTPServer((args.host, args.port), _Handler)
    print("[voiceforge] serving on http://%s:%d checkpoint=%s auth=%s"
          % (args.host, args.port, args.checkpoint,
             "enabled" if _AUTH_TOKEN else "disabled"),
          flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("[voiceforge] shutting down", flush=True)


if __name__ == "__main__":
    main()