import wave, json, subprocess
from vosk import Model, KaldiRecognizer
import sherpa_onnx
import soundfile as sf

WAV = "test16k.wav"

# ---- Track 1: Vosk (grammar) ----
wf = wave.open(WAV, "rb")
vosk_model = Model("/storage/emulated/0/jarvis-repo/vosk-model-small-en-us-0.15")
grammar = json.dumps(["jarvis status", "jarvis stop", "jarvis go", "jarvis halt", "[unk]"])
rec = KaldiRecognizer(vosk_model, wf.getframerate(), grammar)
rec.AcceptWaveform(wf.readframes(wf.getnframes()))
vosk_result = json.loads(rec.Result())["text"]

# ---- Track 2: sherpa-onnx Zipformer ----
base = "/storage/emulated/0/jarvis-repo/models/sherpa-onnx-streaming-zipformer-en-2023-06-21"
recognizer = sherpa_onnx.OnlineRecognizer.from_transducer(
    tokens=f"{base}/tokens.txt",
    encoder=f"{base}/encoder-epoch-99-avg-1.int8.onnx",
    decoder=f"{base}/decoder-epoch-99-avg-1.onnx",
    joiner=f"{base}/joiner-epoch-99-avg-1.int8.onnx",
    num_threads=2,
    sample_rate=16000,
    feature_dim=80,
)
samples, sr = sf.read(WAV, dtype="float32")
stream = recognizer.create_stream()
stream.accept_waveform(sr, samples)
while recognizer.is_ready(stream):
    recognizer.decode_stream(stream)
sherpa_result = recognizer.get_result(stream)

# ---- Track 3: whisper.cpp tiny.en ----
subprocess.run([
    "/root/whisper.cpp/build/bin/whisper-cli",
    "-m", "/root/whisper.cpp/models/ggml-tiny.en.bin",
    "-f", WAV, "-l", "en", "-otxt", "-of", "whisper_out"
], check=True)
with open("whisper_out.txt") as f:
    whisper_result = f.read().strip()

# ---- Combined output ----
print("\n=== RESULTS ===")
print("VOSK    :", vosk_result)
print("SHERPA  :", sherpa_result)
print("WHISPER :", whisper_result)
