package com.jarvis.app.ui.screens

import androidx.compose.foundation.background
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
import com.jarvis.app.ui.components.HorizonArc
import com.jarvis.app.ui.components.pressScale
import com.jarvis.app.ui.components.rememberCapabilitySnapshot
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.flow.flowOf

/**
 * Home — Friday's persistent visual embodiment. It is intentionally not
 * gated by the model, body coordinator, or a finite presence-state enum.
 * Optional voice telemetry may modulate the continuously evolving topology,
 * but the visual identity remains present when those subsystems are absent.
 */
@Composable
fun HomeScreen(onOpenTools: () -> Unit) {
    val voiceSlot = com.jarvis.app.ui.EngineAccess.voiceOutputPath()
    val voiceOutput = voiceSlot.value
    val voiceActive by (voiceOutput?.isSpeaking ?: flowOf(false)).collectAsStateWithLifecycle(initialValue = false)
    val voiceLevel by (voiceOutput?.audioLevel ?: flowOf(0f)).collectAsStateWithLifecycle(initialValue = 0f)


    val capsSnapshot by rememberCapabilitySnapshot()
    val activeCount = remember(capsSnapshot) { capsSnapshot?.count { it.value } ?: 0 }
    val totalCount = remember(capsSnapshot) { capsSnapshot?.size ?: 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase),
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
            com.jarvis.app.ui.components.FridayAvatar(
                size = 280.dp,
                voiceActive = voiceActive,
                voiceLevel = voiceLevel,
                morphologySeed = com.jarvis.app.JarvisEngine.fridayIndividual?.identity?.morphologySeed ?: 0L
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizonArc(
                progress = if (totalCount > 0) activeCount.toFloat() / totalCount else 0f,
                color = JarvisColors.CoreIdle
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "FRIDAY",
                style = JarvisType.Display,
                textAlign = TextAlign.Center
            )
        }

        CapabilitySummaryBar(
            active = activeCount,
            total = totalCount,
            onTap = onOpenTools
        )
    }
}

@Composable
private fun CapabilitySummaryBar(active: Int, total: Int, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(JarvisColors.SurfacePanel)
            .border(1.dp, JarvisColors.SurfaceHairline)
            .pressScale(onClick = onTap)
            .padding(PaddingValues(horizontal = 16.dp, vertical = 16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$active of $total capabilities active",
            style = JarvisType.Body.copy(color = JarvisColors.TextSecondary)
        )
    }
}
