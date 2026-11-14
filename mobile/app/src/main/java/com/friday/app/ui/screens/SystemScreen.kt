package com.friday.app.ui.screens
import com.friday.app.ui.EngineAccess

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.friday.app.DiagnosticsViewModel
import com.friday.app.FridayEngine
import com.friday.app.ui.components.StateChip
import com.friday.app.ui.theme.FridayColors
import com.friday.app.ui.theme.FridayType

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
    val brainStatus by remember { EngineAccess.modelManager().value?.status ?: MutableStateFlow("idle") }
        .collectAsStateWithLifecycle()
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
        LazyColumn {
            items(rows) { row -> SystemRowView(row) }
        }
        Button(
            onClick = { diagnosticsViewModel.crashVoiceSubstrate() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Crash voice process (R1 isolation demo)")
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
    status == "sending (local)" -> FridayColors.CoreThinking
    status.startsWith("local brain unreachable") -> FridayColors.CoreWarning
    status == "ok (local)" -> FridayColors.CoreSuccess
    else -> FridayColors.CoreIdle
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
            t.contains("heard") || t.contains("wifi") || t.contains("cellular") ->
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
