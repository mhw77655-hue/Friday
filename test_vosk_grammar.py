from vosk import Model, KaldiRecognizer
import wave, json

wf = wave.open("test16k.wav", "rb")
model = Model("/storage/emulated/0/jarvis-repo/vosk-model-small-en-us-0.15")

grammar = json.dumps(["jarvis status", "jarvis stop", "jarvis go", "[unk]"])
rec_grammar = KaldiRecognizer(model, wf.getframerate(), grammar)
rec_default = KaldiRecognizer(model, wf.getframerate())

data = wf.readframes(wf.getnframes())
rec_grammar.AcceptWaveform(data)
rec_default.AcceptWaveform(data)

print("GRAMMAR:", rec_grammar.Result())
print("DEFAULT:", rec_default.Result())
