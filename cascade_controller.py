#!/usr/bin/env python3
"""
JARVIS STT Cascade Controller
Tier 1: Vosk (grammar-constrained)  -- fast, safe, never returns garbage
Tier 2: sherpa-onnx Zipformer       -- open vocabulary, catches unknown phrasing
Tier 3: whisper.cpp tiny.en         -- last resort, most accurate on noisy audio

Run: python3 cascade_controller.py <path_to_wav>
Requires 16kHz mono 16-bit PCM wav input (same as test16k.wav from testing).

Run this inside Ubuntu (proot-distro login ubuntu) -- Vosk and sherpa-onnx
do not have Termux-native wheels.
"""

import sys
import os
import json
import wave
import time
import tempfile
import subprocess
from difflib import SequenceMatcher

from vosk import Model, KaldiRecognizer
import sherpa_onnx
import soundfile as sf

# ---- Paths confirmed working in this session ----
VOSK_MODEL_PATH = "/storage/emulated/0/jarvis-repo/vosk-model-small-en-us-0.15"
SHERPA_BASE = "/storage/emulated/0/jarvis-repo/models/sherpa-onnx-streaming-zipformer-en-2023-06-21"
WHISPER_BIN = "/root/whisper.cpp/build/bin/whisper-cli"
WHISPER_MODEL = "/root/whisper.cpp/models/ggml-tiny.en.bin"

# ---- Known commands JARVIS should recognize ----
KNOWN_INTENTS = ["jarvis status", "jarvis stop", "jarvis go", "jarvis halt"]
GRAMMAR = json.dumps(KNOWN_INTENTS + ["[unk]"])

# Minimum similarity (0-1) for tier 2's fuzzy match to count as a real hit
SHERPA_MATCH_THRESHOLD = 0.6


def best_intent_match(text):
    """Return (best_matching_intent, score) using simple string similarity."""
    text = text.lower().strip()
    if not text:
        return None, 0.0
    best, best_score = None, 0.0
    for intent in KNOWN_INTENTS:
        score = SequenceMatcher(None, text, intent).ratio()
        if score > best_score:
            best, best_score = intent, score
    return best, best_score


def validate_wav(wav_path):
    """Confirm the input is 16kHz mono 16-bit PCM, as every tier assumes."""
    if not os.path.isfile(wav_path):
        raise FileNotFoundError(f"No such file: {wav_path}")
    with wave.open(wav_path, "rb") as wf:
        rate, channels, width = wf.getframerate(), wf.getnchannels(), wf.getsampwidth()
    problems = []
    if rate != 16000:
        problems.append(f"sample rate is {rate}Hz, expected 16000Hz")
    if channels != 1:
        problems.append(f"{channels} channels, expected mono (1)")
    if width != 2:
        problems.append(f"{width*8}-bit samples, expected 16-bit")
    if problems:
        raise ValueError(f"{wav_path} is not valid cascade input: " + "; ".join(problems))


def tier1_vosk(wav_path):
    with wave.open(wav_path, "rb") as wf:
        model = Model(VOSK_MODEL_PATH)
        rec = KaldiRecognizer(model, wf.getframerate(), GRAMMAR)
        rec.AcceptWaveform(wf.readframes(wf.getnframes()))
        text = json.loads(rec.Result())["text"].strip()

    # Vosk grammar mode sometimes repeats/scrambles words (seen in testing:
    # "jarvis go jarvis go jarvis status") -- treat any exact known-intent
    # substring as a hit, don't require an exact full-string match.
    for intent in KNOWN_INTENTS:
        if intent in text:
            return intent, text
    return None, text


def tier2_sherpa(wav_path):
    recognizer = sherpa_onnx.OnlineRecognizer.from_transducer(
        tokens=f"{SHERPA_BASE}/tokens.txt",
        encoder=f"{SHERPA_BASE}/encoder-epoch-99-avg-1.int8.onnx",
        decoder=f"{SHERPA_BASE}/decoder-epoch-99-avg-1.onnx",
        joiner=f"{SHERPA_BASE}/joiner-epoch-99-avg-1.int8.onnx",
        num_threads=2,
        sample_rate=16000,
        feature_dim=80,
    )
    samples, sr = sf.read(wav_path, dtype="float32")
    stream = recognizer.create_stream()
    stream.accept_waveform(sr, samples)
    while recognizer.is_ready(stream):
        recognizer.decode_stream(stream)
    text = recognizer.get_result(stream).strip()

    intent, score = best_intent_match(text)
    if score >= SHERPA_MATCH_THRESHOLD:
        return intent, text
    return None, text


def tier3_whisper(wav_path):
    with tempfile.TemporaryDirectory() as tmpdir:
        out_prefix = os.path.join(tmpdir, "whisper_out")
        subprocess.run(
            [WHISPER_BIN, "-m", WHISPER_MODEL, "-f", wav_path,
             "-l", "en", "-otxt", "-of", out_prefix],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        with open(out_prefix + ".txt") as f:
            text = f.read().strip()

    # Last resort still deserves a shot at resolving a known intent rather
    # than always reporting "no match" when whisper actually heard one.
    intent, score = best_intent_match(text)
    if score >= SHERPA_MATCH_THRESHOLD:
        return intent, text
    return None, text


def resolve(wav_path):
    validate_wav(wav_path)

    intent, raw = tier1_vosk(wav_path)
    if intent:
        return {"tier": 1, "engine": "vosk", "intent": intent, "raw": raw}

    intent, raw = tier2_sherpa(wav_path)
    if intent:
        return {"tier": 2, "engine": "sherpa-onnx", "intent": intent, "raw": raw}

    intent, raw = tier3_whisper(wav_path)
    return {"tier": 3, "engine": "whisper-tiny.en", "intent": intent, "raw": raw}


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python3 cascade_controller.py <path_to_16khz_mono_wav>")
        sys.exit(1)

    result = resolve(sys.argv[1])
    print("\n=== CASCADE RESULT ===")
    print(f"Resolved at tier {result['tier']} ({result['engine']})")
    print(f"Matched intent: {result['intent']}")
    print(f"Raw transcript: {result['raw']}")
