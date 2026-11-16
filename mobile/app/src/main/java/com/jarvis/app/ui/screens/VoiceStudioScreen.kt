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
import androidx.compose.material3.OutlinedTextField
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
    var referencePath by remember { mutableStateOf(initialProfile.referenceAudioPath ?: prefs.getString("reference_path", null)) }
    var referenceTranscript by remember { mutableStateOf(initialProfile.referenceTranscript ?: prefs.getString("reference_transcript", "") ?: "") }
    var status by remember { mutableStateOf("Ready") }
    var speechRate by remember { mutableFloatStateOf(profileStore.get(DefaultJarvisVoiceProfile.id)?.speechRate ?: 1f) }
    var busy by remember { mutableStateOf(false) }

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = "Importing reference audio…"
                val imported = withContext(Dispatchers.IO) {
                    runCatching {
                        val directory = File(context.filesDir, "voice-studio").apply { mkdirs() }
                        val extension = when (context.contentResolver.getType(uri)?.lowercase()) {
                            "audio/wav", "audio/x-wav" -> "wav"
                            "audio/mpeg" -> "mp3"
                            "audio/mp4", "audio/x-m4a" -> "m4a"
                            "audio/ogg" -> "ogg"
                            "audio/flac" -> "flac"
                            else -> "audio"
                        }
                        val target = File(directory, "reference-audio.$extension")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("Could not open selected audio")
                        target.absolutePath
                    }.getOrNull()
                }
                if (imported != null) {
                    referencePath = imported
                    prefs.edit().putString("reference_path", imported).apply()
                    val current = profileStore.get(DefaultJarvisVoiceProfile.id) ?: DefaultJarvisVoiceProfile
                    profileStore.save(current.copy(
                        referenceAudioPath = imported,
                        referenceTranscript = referenceTranscript.trim().ifBlank { null }
                    ))
                    status = "Reference audio saved locally as part of Friday's voice identity"
                } else {
                    status = "Audio import failed"
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
                Text("Provider: Piper VITS / English US (Amy · Low)")
                Text("Voice process: ${host?.state?.value?.state ?: "not ready"}")
                Text("This is Friday’s speed-first on-device voice. Kokoro remains available as an optional higher-quality provider.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && host != null, onClick = {
                        scope.launch {
                            busy = true
                            status = "Generating preview…"
                            val audio = host?.synthesizeText(
                                text = "Hello. I'm Friday. This is a preview of my current voice.",
                                language = "en",
                                voiceId = "speaker_0"
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
                    OutlinedButton(enabled = !busy && referencePath != null, onClick = {
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
                Text("Voice: Amy · English US (Piper Low)")
                Button(enabled = !busy, onClick = {
                    val current = profileStore.get(DefaultJarvisVoiceProfile.id) ?: DefaultJarvisVoiceProfile
                    profileStore.save(current.copy(
                        language = "en",
                        preferredProvider = "piper",
                        preferredVoice = "speaker_0",
                        speechRate = speechRate,
                        referenceAudioPath = referencePath,
                        referenceTranscript = referenceTranscript.trim().ifBlank { null }
                    ))
                    status = "Friday's active voice profile saved"
                }) { Text("Save active voice") }
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Voice reference library", style = MaterialTheme.typography.titleMedium)
                Text("Upload a recording to define Friday's chosen voice reference. The audio stays inside this app. The transcript is optional and is retained only for local synthesis engines that require it. Important: the active Piper/Kokoro voices do not yet clone from this sample; reference-conditioned synthesis is a separate integration still in progress.")
                OutlinedButton(enabled = !busy, onClick = { pickAudio.launch(arrayOf("audio/*")) }) {
                    Text(if (referencePath == null) "Import reference audio" else "Replace reference audio")
                }
                Text(referencePath?.let { File(it).name } ?: "No reference recording selected")
                OutlinedTextField(
                    value = referenceTranscript,
                    onValueChange = { referenceTranscript = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Exact words spoken in the recording") },
                    minLines = 2
                )
                Button(enabled = !busy && referencePath != null, onClick = {
                    prefs.edit().putString("reference_path", referencePath).putString("reference_transcript", referenceTranscript.trim()).apply()
                    val current = profileStore.get(DefaultJarvisVoiceProfile.id) ?: DefaultJarvisVoiceProfile
                    profileStore.save(current.copy(
                        referenceAudioPath = referencePath,
                        referenceTranscript = referenceTranscript.trim().ifBlank { null }
                    ))
                    status = "Friday's voice reference and optional transcript saved locally"
                }) { Text("Save voice reference") }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LuxTTS — experimental voice cloning", style = MaterialTheme.typography.titleMedium)
                Text("Candidate: LuxTTS ONNX (English voice cloning, 48 kHz output, Apache-2.0).")
                Text("Status: research/integration pending. The ONNX graphs are available, but the public ONNX pipeline still relies on Python audio loading, mel extraction, English phonemization/tokenization and NumPy post-processing. ONNX Runtime alone does not provide those pieces.")
                Text("The reference clip and transcript above are now stored locally in the format needed for the next integration step. LuxTTS is not yet generating audio in this Android build, and Piper remains the active default; Kokoro remains an optional higher-quality provider.")
                Text("Device policy: do not load LuxTTS automatically. First benchmark peak RAM, thermal load, latency and quality on this phone; enable it only when the user explicitly selects it.")
                OutlinedButton(onClick = {
                    status = "LuxTTS reference is prepared; Android inference adapter is not yet implemented"
                }) { Text("Check LuxTTS integration status") }
            }
        }

        Text("Status: $status", style = MaterialTheme.typography.bodyMedium)
        if (busy) Text("Working…", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))
    }
}
