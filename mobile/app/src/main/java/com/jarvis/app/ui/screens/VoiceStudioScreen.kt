package com.jarvis.app.ui.screens

import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.app.ui.EngineAccess
import com.jarvis.app.voice.DefaultJarvisVoiceProfile
import com.jarvis.app.voice.VoiceProfileStore
import com.jarvis.app.voice.provider.VoiceSynthesisParams
import com.jarvis.app.voice.provider.ReferenceAudioLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Voice Studio: manage Friday's persistent voice preference and a local
 * reference recording. The current production synthesis path remains the
 * isolated native speech habitat. Piper is the speed-first English default; LuxTTS is surfaced as an experimental
 * voice-cloning candidate, not falsely advertised as an Android provider:
 * its public ONNX implementation still depends on Python audio/tokenization
 * preprocessing that has not been ported to this app.
 */
@Composable
fun VoiceStudioScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("voice_studio", 0) }
    val profileStore = remember { VoiceProfileStore(context) }
    val hostSlot = EngineAccess.voiceOrganismHost()
    val host = hostSlot.getOrNull()

    val initialProfile = remember { profileStore.get(DefaultJarvisVoiceProfile.id) ?: DefaultJarvisVoiceProfile }
    var referencePath by remember { mutableStateOf((initialProfile.referenceAudioPath ?: prefs.getString("reference_path", null))?.takeIf { File(it).isFile }) }
    val referenceAvailable = referencePath?.let { File(it).isFile } == true
    var status by remember { mutableStateOf(if (referenceAvailable) "Reference sample available; Pocket TTS loads on first use" else "Piper active; no reference sample selected") }
    var speechRate by remember { mutableFloatStateOf(profileStore.get(DefaultJarvisVoiceProfile.id)?.speechRate ?: 1f) }
    var busy by remember { mutableStateOf(false) }

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = "Importing reference audio…"
                val importResult = withContext(Dispatchers.IO) {
                    runCatching {
                        val directory = File(context.filesDir, "voice-studio").apply { mkdirs() }
                        val mime = context.contentResolver.getType(uri)?.lowercase()
                        val isWav = mime in setOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave") ||
                            uri.lastPathSegment?.endsWith(".wav", ignoreCase = true) == true
                        require(isWav) { "Choose a PCM WAV recording for voice cloning" }
                        val target = File(directory, "reference-audio.wav")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("Could not open selected audio")
                        ReferenceAudioLoader.load(target) // validate format, duration and bounds before saving
                        target.absolutePath
                    }
                }
                if (importResult.isSuccess) {
                    val imported = importResult.getOrThrow()
                    referencePath = imported
                    prefs.edit().putString("reference_path", imported).apply()
                    val current = profileStore.get(DefaultJarvisVoiceProfile.id) ?: DefaultJarvisVoiceProfile
                    profileStore.save(current.copy(
                        referenceAudioPath = imported,
                        preferredProvider = "pocket",
                        preferredVoice = "reference"
                    ))
                    status = "Reference WAV saved. Pocket TTS will clone it on the next synthesis."
                } else {
                    status = "Reference WAV rejected: " + (importResult.exceptionOrNull()?.message ?: "unknown error")
                }
                busy = false
            }
        }
    }

    fun playFile(file: File, onDone: () -> Unit = {}) {
        runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { player -> player.release(); onDone() }
                setOnErrorListener { player, _, _ -> player.release(); onDone(); true }
                prepareAsync()
            }
        }.onFailure { status = "Playback failed: ${it.message}" }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Voice Studio", style = MaterialTheme.typography.headlineMedium)
        Text("Shape, preserve and test Friday's spoken voice. Voice models remain replaceable; her voice profile is kept separately.")

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Friday's active voice", style = MaterialTheme.typography.titleMedium)
                Text(if (referenceAvailable) "Provider: Pocket TTS · your reference voice" else "Provider: Piper VITS / English US (Amy · Low)")
                Text("Voice process: ${host?.state?.value?.state ?: "not ready"}")
                Text(if (referenceAvailable) "Friday uses your locally saved voice sample. The Pocket TTS pack is downloaded on first synthesis." else "Piper is active. No reference sample is selected, so voice cloning is not active.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && host != null, onClick = {
                        scope.launch {
                            busy = true
                            status = "Generating preview…"
                            val audio = host?.synthesizeText(
                                text = "Hello. I'm Friday. This is a preview of my current voice.",
                                language = "en",
                                voiceId = if (referenceAvailable) "reference" else "speaker_0",
                                params = VoiceSynthesisParams(referenceAudioPath = referencePath.takeIf { referenceAvailable })
                            )
                            if (audio != null) {
                                status = "Playing Friday voice preview"
                                withContext(Dispatchers.Main) {
                                    playFile(audio.audioFile) { status = "Preview complete"; busy = false }
                                }
                            } else {
                                status = "Voice habitat unavailable; preview could not be generated"
                                busy = false
                            }
                        }
                    }) { Text("Preview Friday") }
                    OutlinedButton(enabled = !busy && referenceAvailable, onClick = {
                        val file = referencePath?.let(::File)
                        if (file != null && file.exists()) playFile(file)
                    }) { Text("Play reference") }
                }
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Voice identity settings", style = MaterialTheme.typography.titleMedium)
                Text("Speech rate: ${"%.2f".format(speechRate)}×")
                Slider(value = speechRate, onValueChange = { speechRate = it }, valueRange = 0.75f..1.25f)
                Text(if (referenceAvailable) "Voice: your uploaded reference" else "Voice: Amy · English US (Piper Low)")
                Button(enabled = !busy, onClick = {
                    val current = profileStore.get(DefaultJarvisVoiceProfile.id) ?: DefaultJarvisVoiceProfile
                    profileStore.save(current.copy(
                        language = "en",
                        preferredProvider = if (referenceAvailable) "pocket" else "piper",
                        preferredVoice = if (referenceAvailable) "reference" else "speaker_0",
                        speechRate = speechRate,
                        referenceAudioPath = referencePath,
                    ))
                    status = "Friday's active voice profile saved"
                }) { Text("Save active voice") }
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Friday's reference voice", style = MaterialTheme.typography.titleMedium)
                Text("Upload a clean 1–12 second PCM WAV recording. Pocket TTS clones the voice fully offline and does not need a transcript. The sample stays in this app. Its optional English model pack is about 99 MB and downloads only when first used.")
                OutlinedButton(enabled = !busy, onClick = { pickAudio.launch(arrayOf("audio/wav", "audio/x-wav")) }) {
                    Text(if (referencePath == null) "Import reference WAV" else "Replace reference WAV")
                }
                Text(referencePath?.takeIf { referenceAvailable }?.let { File(it).name } ?: "No reference recording selected")
                Button(enabled = !busy && referencePath != null, onClick = {
                    prefs.edit().putString("reference_path", referencePath).apply()
                    val current = profileStore.get(DefaultJarvisVoiceProfile.id) ?: DefaultJarvisVoiceProfile
                    profileStore.save(current.copy(
                        referenceAudioPath = referencePath,
                        preferredProvider = "pocket",
                        preferredVoice = "reference"
                    ))
                    status = "Friday's reference voice saved. Pocket TTS will clone it on the next synthesis."
                }) { Text("Save reference voice") }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pocket TTS · reference-conditioned synthesis", style = MaterialTheme.typography.titleMedium)
                Text("Integrated through sherpa-onnx in Friday's isolated voice process. No transcript, cloud account or separate TTS app is required.")
                Text("The model is optional and lazy-loaded. Without a reference sample, Friday continues using compact Piper. With a saved sample, Pocket TTS is selected for English speech.")
                Text("Use a clean recording with one speaker and little background noise. This first integration accepts PCM16 WAV only; compressed-audio conversion is not included yet.")
            }
        }

        Text("Current voice status: $status", style = MaterialTheme.typography.bodyMedium)
        if (busy) Text("Working…", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))
    }
}
