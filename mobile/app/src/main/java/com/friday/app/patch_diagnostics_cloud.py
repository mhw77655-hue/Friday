path = "DiagnosticsViewModel.kt"
with open(path) as f:
    content = f.read()

orig = content

content = content.replace(
    "import androidx.lifecycle.viewModelScope\n",
    "import androidx.lifecycle.viewModelScope\nimport android.media.MediaPlayer\nimport java.io.File\n",
    1
)

vosk_anchor = '''                val command = heard.substring(idx + "jarvis".length).trim()
                lastVoskText = "Vosk: heard \\"$heard\\""
                publishHealthText()
'''
assert content.count(vosk_anchor) == 1, f"vosk_anchor matches: {content.count(vosk_anchor)}"
vosk_replacement = '''                var command = heard.substring(idx + "jarvis".length).trim()
                lastVoskText = "Vosk: heard \\"$heard\\""
                publishHealthText()

                val cloudText = CloudVoiceClient.transcribe(JarvisMic.recentSamples())
                if (!cloudText.isNullOrBlank()) {
                    command = cloudText
                    lastVoskText = "Vosk: heard \\"$heard\\" (cloud: \\"$cloudText\\")"
                    publishHealthText()
                }
'''
content = content.replace(vosk_anchor, vosk_replacement, 1)

n_speak = content.count("TtsBridge.speak(")
assert n_speak == 5, f"expected 5 TtsBridge.speak( call sites, found {n_speak}"
content = content.replace("TtsBridge.speak(", "say(")

anchor = '''    private fun publishHealthText() {
        val battery = if (lastBatteryText.isNotEmpty()) lastBatteryText else "Reading battery state..."
        _healthText.value = "$battery. $lastNetworkText. $lastNotifText. $lastShizukuText. $lastMicText. $lastVoskText. $lastSherpaText. $lastBrainText. $lastTtsText. $lastScreenText. Real device signal — no cloud/memory wired yet."
    }
'''
assert content.count(anchor) == 1, f"publishHealthText anchor matches: {content.count(anchor)}"
replacement = anchor + '''
    private fun say(text: String) {
        viewModelScope.launch {
            val cloudAudio = CloudVoiceClient.speak(text)
            val played = cloudAudio?.let { playCloudAudio(it) } ?: false
            if (!played) {
                TtsBridge.speak(text)
            }
        }
    }

    private fun playCloudAudio(bytes: ByteArray): Boolean {
        return try {
            val tmp = File(getApplication<Application>().cacheDir, "cloud_tts_${System.currentTimeMillis()}.wav")
            tmp.writeBytes(bytes)
            val player = MediaPlayer()
            player.setDataSource(tmp.absolutePath)
            player.setOnCompletionListener {
                it.release()
                tmp.delete()
            }
            player.prepare()
            player.start()
            true
        } catch (e: Exception) {
            false
        }
    }
'''
content = content.replace(anchor, replacement, 1)

with open(path, "w") as f:
    f.write(content)

print("Patched OK.")
print(f"Lines: orig={len(orig.splitlines())} new={len(content.splitlines())}")
