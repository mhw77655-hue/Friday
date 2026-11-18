package com.jarvis.app.ui.screens
import com.jarvis.app.ui.EngineAccess

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.app.DiagnosticsViewModel
import com.jarvis.app.DiagnosticLevel
import com.jarvis.app.ui.components.StateChip
import com.jarvis.app.ui.components.FridayPanel
import com.jarvis.app.ui.components.FridayScreenHeader
import com.jarvis.app.ui.components.FridaySectionHeading
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType

private data class SystemRow(val name: String, val stateLabel: String, val dotColor: Color, val detail: String)

/**
 * All rows now read real per-signal StateFlows exposed from
 * DiagnosticsViewModel (networkText/shizukuText/micText/sttText/
 * hotwordText/ttsText/screenText) instead of hardcoded "not yet
 * reporting" placeholders. Brain and Battery were already real and are
 * unchanged.
 */
@Composable
fun SystemScreen() {
    val diagnosticsViewModel: DiagnosticsViewModel = viewModel()
    val context = LocalContext.current
    val reportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(diagnosticsViewModel.createExportReport())
                } ?: error("Could not open the selected destination.")
            }
            val message = if (result.isSuccess) "Friday diagnostic report exported."
                else "Report export failed: " + (result.exceptionOrNull()?.message ?: "unknown error")
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    val brainStatus by diagnosticsViewModel.modelRuntimeText.collectAsStateWithLifecycle()
    val networkText by diagnosticsViewModel.networkText.collectAsStateWithLifecycle()
    val batteryText by diagnosticsViewModel.batteryText.collectAsStateWithLifecycle()
    val shizukuText by diagnosticsViewModel.shizukuText.collectAsStateWithLifecycle()
    val micText by diagnosticsViewModel.micText.collectAsStateWithLifecycle()
    val sttText by diagnosticsViewModel.sttText.collectAsStateWithLifecycle()
    val hotwordText by diagnosticsViewModel.hotwordText.collectAsStateWithLifecycle()
    val ttsText by diagnosticsViewModel.ttsText.collectAsStateWithLifecycle()
    val voiceSubstrateText by diagnosticsViewModel.voiceSubstrateText.collectAsStateWithLifecycle()
    val screenText by diagnosticsViewModel.screenText.collectAsStateWithLifecycle()
    val failureText by diagnosticsViewModel.failureText.collectAsStateWithLifecycle()
    val individualMemoryText by diagnosticsViewModel.individualMemoryText.collectAsStateWithLifecycle()
    val diagnosticFindings by diagnosticsViewModel.diagnosticFindings.collectAsStateWithLifecycle()
    val diagnosticRunning by diagnosticsViewModel.diagnosticRunning.collectAsStateWithLifecycle()
    val selfDiagnosticFindings by diagnosticsViewModel.selfDiagnosticFindings.collectAsStateWithLifecycle()
    val selfDiagnosticAt by diagnosticsViewModel.selfDiagnosticAt.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { diagnosticsViewModel.runSelfDiagnosis() }

    val rows = listOf(
        SystemRow("Brain", brainStatus, brainDotColor(brainStatus), brainStatus),
        SystemRow("Battery", batteryText.removePrefix("Battery: "), batteryDotColor(batteryText), batteryText),
        SystemRow("Network", networkText.removePrefix("Net: "), genericDotColor(networkText), networkText),
        SystemRow("Shizuku", shizukuText.removePrefix("Shizuku: "), genericDotColor(shizukuText), shizukuText),
        SystemRow("Mic", micText.removePrefix("Mic: "), genericDotColor(micText), micText),
        SystemRow("STT", sttText.removePrefix("STT: "), genericDotColor(sttText), sttText),
        SystemRow("Hotword", hotwordText.removePrefix("Hotword: "), genericDotColor(hotwordText), hotwordText),
        SystemRow("TTS", ttsText.removePrefix("Tts: "), genericDotColor(ttsText), ttsText),
        SystemRow(
            "Voice Substrate",
            voiceSubstrateText.removePrefix("Voice Substrate: "),
            substrateDotColor(voiceSubstrateText),
            voiceSubstrateText
        ),
        SystemRow("Screen", screenText.removePrefix("Screen: "), genericDotColor(screenText), screenText),
        SystemRow("Friday Memory", individualMemoryText.removePrefix("Friday individual: "), if (individualMemoryText.contains("present")) JarvisColors.CoreSuccess else JarvisColors.CoreWarning, individualMemoryText),
        SystemRow(
            "Failures",
            failureText.removePrefix("Failures: "),
            failureDotColor(failureText),
            failureText
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
    ) {
        FridayScreenHeader(
            eyebrow = "Device and runtime",
            title = "System health",
            description = "Live signals and diagnostic evidence from Friday's current Android environment."
        )

        FridayPanel(modifier = Modifier.padding(horizontal = 20.dp)) {
            Button(
                onClick = { reportLauncher.launch("friday-device-report.json") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export diagnostic report")
            }
            Text(
                "Includes device, resource, model, voice, memory, network and failure state. Excludes chat messages, speech transcripts, notification contents and credentials.",
                style = JarvisType.Technical
            )
            Button(
                onClick = { diagnosticsViewModel.runFullDiagnostic() },
                enabled = !diagnosticRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (diagnosticRunning) "Running active diagnostics..." else "Run full system diagnosis")
            }
            Button(
                onClick = { diagnosticsViewModel.runSelfDiagnosis() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Run Friday architecture and autonomy diagnosis")
            }
            selfDiagnosticAt?.let { checkedAt ->
                Text(
                    java.text.DateFormat.getDateTimeInstance().format(java.util.Date(checkedAt)) +
                        " · " + selfDiagnosticFindings.count { it.level == DiagnosticLevel.FAILURE } +
                        " failures · " + selfDiagnosticFindings.count { it.level == DiagnosticLevel.WARNING } +
                        " warnings",
                    style = JarvisType.Technical
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 28.dp)
        ) {
            item {
                FridaySectionHeading("Live status", "${rows.size} signals")
            }
            items(rows) { row ->
                FridayPanel { SystemRowView(row) }
            }
            item {
                FridaySectionHeading("Diagnostic findings", diagnosticFindings.size.toString())
            }
            if (diagnosticFindings.isEmpty()) {
                item {
                    FridayPanel {
                        Text("No diagnostic run has completed yet.", style = JarvisType.Body)
                    }
                }
            }
            items(diagnosticFindings) { finding ->
                val color = when (finding.severity) {
                    com.jarvis.app.diagnostics.DiagnosticSeverity.PASS -> JarvisColors.CoreSuccess
                    com.jarvis.app.diagnostics.DiagnosticSeverity.INFO -> JarvisColors.CoreIdle
                    com.jarvis.app.diagnostics.DiagnosticSeverity.WARNING -> JarvisColors.CoreAttention
                    com.jarvis.app.diagnostics.DiagnosticSeverity.ERROR -> JarvisColors.CoreWarning
                }
                FridayPanel {
                    Text("${finding.severity.name} · ${finding.title}", color = color, style = JarvisType.Label)
                    Text("Observed: ${finding.observed}", style = JarvisType.Technical)
                    Text("Assessment: ${finding.likelyCause}", style = JarvisType.Technical)
                    Text("Action: ${finding.recommendedAction}", style = JarvisType.Technical)
                }
            }
            item {
                FridaySectionHeading("Friday self-diagnosis", selfDiagnosticFindings.size.toString())
            }
            if (selfDiagnosticFindings.isEmpty()) {
                item {
                    FridayPanel {
                        Text("No Friday self-diagnosis has completed yet.", style = JarvisType.Body)
                    }
                }
            }
            items(selfDiagnosticFindings, key = { it.id }) { finding ->
                val color = when (finding.level) {
                    DiagnosticLevel.PASS -> JarvisColors.CoreSuccess
                    DiagnosticLevel.WARNING -> JarvisColors.CoreAttention
                    DiagnosticLevel.FAILURE -> JarvisColors.CoreWarning
                }
                FridayPanel {
                    Text("${finding.level.name} · ${finding.title}", color = color, style = JarvisType.Label)
                    Text(finding.detail, style = JarvisType.Technical)
                    finding.recommendation?.let { Text("Action: $it", style = JarvisType.Technical) }
                }
            }
        }
    }
}

@Composable
private fun SystemRowView(row: SystemRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = row.name, style = JarvisType.Technical)
        StateChip(label = row.stateLabel, dotColor = row.dotColor)
    }
}

private fun substrateDotColor(text: String): Color {
    val t = text.lowercase()
    return when {
        t.contains("bound") -> JarvisColors.CoreSuccess
        t.contains("disabled") || t.contains("failed") || t.contains("offline") -> JarvisColors.CoreWarning
        t.contains("rebinding") || t.contains("binding") -> JarvisColors.CoreThinking
        else -> JarvisColors.CoreIdle
    }
}

private fun brainDotColor(status: String) = when {
    status.startsWith("loaded:") -> JarvisColors.CoreSuccess
    status.startsWith("model-free fallback") -> JarvisColors.CoreIdle
    status.contains("no model confirmed loaded") -> JarvisColors.CoreWarning
    status.contains("unavailable") -> JarvisColors.CoreWarning
    else -> JarvisColors.CoreUnknown
}

private fun batteryDotColor(text: String): Color {
    val t = text.lowercase()
    return when {
        t.contains("charging") -> JarvisColors.CoreSuccess
        t.contains("low") -> JarvisColors.CoreWarning
        t.contains("unavailable") -> JarvisColors.CoreUnknown
        else -> JarvisColors.CoreIdle
    }
}

/**
 * Keyword-based color mapping for the newly-wired rows, which report
 * free-form status strings rather than a fixed enum. Negative/offline
 * patterns are checked first so e.g. "not granted" doesn't fall through
 * to the "granted" success match.
 */
private fun genericDotColor(text: String): Color {
    val t = text.lowercase()
    return when {
        t.contains("not granted") || t.contains("not running") || t.contains("not capturing") ||
            t.contains("offline") || t.contains("not read yet") || t.contains("not yet reporting") ->
            JarvisColors.CoreOffline

        t.contains("missed") || t.contains("no match") || t.contains("unreachable") ->
            JarvisColors.CoreWarning

        t.contains("checking") || t.contains("loading") || t.contains("idle") ->
            JarvisColors.CoreIdle

        t.contains("granted") || t.contains("capturing") || t.contains("connected") ||
            t.contains("heard") || t.contains("wifi") || t.contains("cellular") ||
            t.contains("energy monitor active") || t.contains("ready; waiting") ->
            JarvisColors.CoreSuccess

        else -> JarvisColors.CoreUnknown
    }
}

/** Failures row: green when clean, amber when degraded/warning, red on error. */
private fun failureDotColor(text: String): Color {
    val t = text.lowercase()
    return when {
        t.contains("critical") || t.contains("error") -> JarvisColors.CoreWarning
        t.contains("warning") || t.contains("degraded") -> JarvisColors.CoreAttention
        t.contains("none") -> JarvisColors.CoreSuccess
        else -> JarvisColors.CoreUnknown
    }
}
