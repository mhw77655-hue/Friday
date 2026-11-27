package ai.friday.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.friday.assistant.ui.components.pressScale
import ai.friday.assistant.ui.components.GlassPanel
import ai.friday.assistant.ui.components.rememberCapabilitySnapshot
import ai.friday.assistant.FridayMic
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType
import kotlinx.coroutines.flow.flowOf

/**
 * Home — Friday's persistent visual embodiment. It is intentionally not
 * gated by the model, body coordinator, or a finite presence-state enum.
 * Optional voice telemetry may modulate the continuously evolving topology,
 * but the visual identity remains present when those subsystems are absent.
 */
@Composable
fun HomeScreen(onOpenTools: () -> Unit) {
    val voiceSlot = ai.friday.assistant.ui.EngineAccess.voiceOutputPath()
    val voiceOutput = voiceSlot.value
    val outputActive by (voiceOutput?.isSpeaking ?: flowOf(false)).collectAsStateWithLifecycle(initialValue = false)
    val outputLevel by (voiceOutput?.audioLevel ?: flowOf(0f)).collectAsStateWithLifecycle(initialValue = 0f)
    // Use the actual incoming microphone energy as well as outgoing PCM energy.
    // The avatar responds to acoustic evidence, not a fixed listening/thinking/speaking pose.
    val inputLevel by FridayMic.lastLevel.collectAsStateWithLifecycle(initialValue = 0)
    val acousticActivity = outputActive || inputLevel > 35
    val acousticLevel = maxOf(outputLevel, (inputLevel / 120f).coerceIn(0f, 1f))

    val capsSnapshot by rememberCapabilitySnapshot()
    val enabledCount = remember(capsSnapshot) { capsSnapshot?.count { it.value } ?: 0 }
    val manifestCount = remember(capsSnapshot) { capsSnapshot?.size ?: 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0x182E9BFF), Color(0x0800D9C0), FridayColors.SurfaceBase))),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ai.friday.assistant.ui.components.FridayAvatar(
                size = 280.dp,
                voiceActive = acousticActivity,
                voiceLevel = acousticLevel,
                morphologySeed = ai.friday.assistant.FridayEngine.fridayIndividual?.identity?.morphologySeed ?: 0L
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "FRIDAY",
                style = FridayType.Display,
                textAlign = TextAlign.Center
            )
        }

        CapabilitySummaryBar(
            enabled = enabledCount,
            total = manifestCount,
            onTap = onOpenTools
        )
    }
}

@Composable
private fun CapabilitySummaryBar(enabled: Int, total: Int, onTap: () -> Unit) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$enabled of $total capability entries enabled",
                style = FridayType.Body.copy(color = FridayColors.TextSecondary)
            )
            Text(
                text = "Manifest flags are not proof of end-to-end operation",
                style = FridayType.Technical.copy(color = FridayColors.TextSecondary)
            )
        }
    }
}
