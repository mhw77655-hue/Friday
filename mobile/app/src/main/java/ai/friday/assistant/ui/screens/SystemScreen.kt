package ai.friday.assistant.ui.screens
import ai.friday.assistant.ui.EngineAccess

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
import ai.friday.assistant.DiagnosticsViewModel
import ai.friday.assistant.DiagnosticLevel
import ai.friday.assistant.ui.components.StateChip
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType

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
    val diagnosticRunStatus by diagnosticsViewModel.diagnosticRunStatus.collectAsStateWithLifecycle()
    val diagnosticRunCount by diagnosticsViewModel.diagnosticRunCount.collectAsStateWithLifecycle()
    val selfDiagnosticRunning by diagnosticsViewModel.selfDiagnosticRunning.collectAsStateWithLifecycle()
    val selfDiagnosticStatus by diagnosticsViewModel.selfDiagnosticStatus.collectAsStateWithLifecycle()
    val selfDiagnosticRunCount by diagnosticsViewModel.selfDiagnosticRunCount.collectAsStateWithLifecycle()
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
        SystemRow("Friday Memory", individualMemoryText.removePrefix("Friday individual: "), if (individualMemoryText.contains("present")) FridayColors.CoreSuccess else FridayColors.CoreWarning, individualMemoryText),
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
            .background(FridayColors.SurfaceBase)
            .padding(16.dp)
    ) {
        Button(
            onClick = { reportLauncher.launch("friday-device-report.json") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export full diagnostic report")
        }
        Text(
            "Includes device, resource, model, voice, memory, network and failure state. " +
                "Excludes chat messages, speech transcripts, notification contents and credentials.",
            style = FridayType.Technical,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Button(
            onClick = { diagnosticsViewModel.runFullDiagnostic() },
            enabled = !diagnosticRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (diagnosticRunning) "Running active diagnostics..." else "Run full system diagnosis")
        }
        Text(
            if (diagnosticRunning) "Active diagnosis run #$diagnosticRunCount running. $diagnosticRunStatus"
            else if (diagnosticRunCount > 0) "Active diagnosis run #$diagnosticRunCount completed. $diagnosticRunStatus"
            else diagnosticRunStatus,
            style = FridayType.Technical,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        Button(
            onClick = { diagnosticsViewModel.runSelfDiagnosis() },
            enabled = !selfDiagnosticRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (selfDiagnosticRunning) "Running architecture and autonomy diagnosis…"
                else "Run Friday architecture and autonomy diagnosis"
            )
        }
        Text(
            if (selfDiagnosticRunning) "Self-diagnosis run #$selfDiagnosticRunCount running: $selfDiagnosticStatus"
            else if (selfDiagnosticRunCount > 0) "Self-diagnosis run #$selfDiagnosticRunCount: $selfDiagnosticStatus"
            else selfDiagnosticStatus,
            style = FridayType.Technical,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        selfDiagnosticAt?.let { checkedAt ->
            Text(
                "Self-diagnosis: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(checkedAt))} · ${selfDiagnosticFindings.count { it.level == DiagnosticLevel.FAILURE }} failures · ${selfDiagnosticFindings.count { it.level == DiagnosticLevel.WARNING }} warnings",
                style = FridayType.Technical,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(rows) { row -> SystemRowView(row) }
            item {
                Text(
                    "DIAGNOSTIC FINDINGS (${diagnosticFindings.size})",
                    style = FridayType.Technical,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
                )
            }
            if (diagnosticFindings.isEmpty()) {
                item { Text("No diagnostic run has completed yet.", style = FridayType.Technical) }
            }
            items(diagnosticFindings) { finding ->
                val color = when (finding.severity) {
                    ai.friday.assistant.diagnostics.DiagnosticSeverity.PASS -> FridayColors.CoreSuccess
                    ai.friday.assistant.diagnostics.DiagnosticSeverity.INFO -> FridayColors.CoreIdle
                    ai.friday.assistant.diagnostics.DiagnosticSeverity.WARNING -> FridayColors.CoreAttention
                    ai.friday.assistant.diagnostics.DiagnosticSeverity.ERROR -> FridayColors.CoreWarning
                }
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("${finding.severity.name} · ${finding.title}", color = color, style = FridayType.Technical)
                    Text("Observed: ${finding.observed}", style = FridayType.Technical)
                    Text("Assessment: ${finding.likelyCause}", style = FridayType.Technical)
                    Text("Action: ${finding.recommendedAction}", style = FridayType.Technical)
                }
            }
            item {
                Text(
                    "FRIDAY SELF-DIAGNOSIS (${selfDiagnosticFindings.size})",
                    style = FridayType.Technical,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
                )
            }
            if (selfDiagnosticFindings.isEmpty()) {
                item { Text("No Friday self-diagnosis has completed yet.", style = FridayType.Technical) }
            }
            items(selfDiagnosticFindings, key = { it.id }) { finding ->
                val color = when (finding.level) {
                    DiagnosticLevel.PASS -> FridayColors.CoreSuccess
                    DiagnosticLevel.WARNING -> FridayColors.CoreAttention
                    DiagnosticLevel.FAILURE -> FridayColors.CoreWarning
                }
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("${finding.level.name} · ${finding.title}", color = color, style = FridayType.Technical)
                    Text(finding.detail, style = FridayType.Technical)
                    finding.recommendation?.let { Text("Action: $it", style = FridayType.Technical) }
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
        Text(text = row.name, style = FridayType.Technical)
        StateChip(label = row.stateLabel, dotColor = row.dotColor)
    }
}

private fun substrateDotColor(text: String): Color {
    val t = text.lowercase()
    return when {
        t.contains("bound") -> FridayColors.CoreSuccess
        t.contains("disabled") || t.contains("failed") || t.contains("offline") -> FridayColors.CoreWarning
        t.contains("rebinding") || t.contains("binding") -> FridayColors.CoreThinking
        else -> FridayColors.CoreIdle
    }
}

private fun brainDotColor(status: String) = when {
    status.startsWith("loaded:") -> FridayColors.CoreSuccess
    status.startsWith("model-free fallback") -> FridayColors.CoreIdle
    status.contains("no model confirmed loaded") -> FridayColors.CoreWarning
    status.contains("unavailable") -> FridayColors.CoreWarning
    else -> FridayColors.CoreUnknown
}

private fun batteryDotColor(text: String): Color {
    val t = text.lowercase()
    return when {
        t.contains("charging") -> FridayColors.CoreSuccess
        t.contains("low") -> FridayColors.CoreWarning
        t.contains("unavailable") -> FridayColors.CoreUnknown
        else -> FridayColors.CoreIdle
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
            FridayColors.CoreOffline

        t.contains("missed") || t.contains("no match") || t.contains("unreachable") ->
            FridayColors.CoreWarning

        t.contains("checking") || t.contains("loading") || t.contains("idle") ->
            FridayColors.CoreIdle

        t.contains("granted") || t.contains("capturing") || t.contains("connected") ||
            t.contains("heard") || t.contains("wifi") || t.contains("cellular") ||
            t.contains("energy monitor active") || t.contains("ready; waiting") ->
            FridayColors.CoreSuccess

        else -> FridayColors.CoreUnknown
    }
}

/** Failures row: green when clean, amber when degraded/warning, red on error. */
private fun failureDotColor(text: String): Color {
    val t = text.lowercase()
    return when {
        t.contains("critical") || t.contains("error") -> FridayColors.CoreWarning
        t.contains("warning") || t.contains("degraded") -> FridayColors.CoreAttention
        t.contains("none") -> FridayColors.CoreSuccess
        else -> FridayColors.CoreUnknown
    }
}
