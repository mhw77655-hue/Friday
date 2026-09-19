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
import com.jarvis.app.body.BodyCoordinator
import com.jarvis.app.body.BodyState
import com.jarvis.app.body.VisualState
import com.jarvis.app.companioncore.contract.PresenceMode
import com.jarvis.app.companioncore.presence.CompanionCoreHolder
import com.jarvis.app.companioncore.render.OrbRenderParams
import com.jarvis.app.companioncore.resource.MobileResourceManagement
import com.jarvis.app.companioncore.ui.PresenceOrb
import com.jarvis.app.JarvisEngine
import com.jarvis.app.ui.components.HorizonArc
import com.jarvis.app.ui.components.pressScale
import com.jarvis.app.ui.components.rememberCapabilitySnapshot
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Home — the presence surface.
 *
 * The orb is driven entirely by the Companion Core (Phase 2): [PresenceOrb]
 * collects the Engine Tick Lane's resolved [OrbRenderParams] internally, so
 * 30Hz tick emissions never recompose this screen (audit R-P2). The label
 * below collects only the rarely-changing presence mode. The old
 * battery/network/brain → OrbState label chains that drove the orb
 * (`HomeViewModel`/`DiagnosticsViewModel`) are retired — the orb is state-
 * driven from the presence engine, not fabricated from collectors.
 */
@Composable
fun HomeScreen(onOpenTools: () -> Unit) {
    val core = remember { CompanionCoreHolder.instance() }
    val bodyCoordinator = JarvisEngine.getBodyCoordinator()
    val bodyState by bodyCoordinator.state.collectAsStateWithLifecycle()
    val visualState by bodyCoordinator.visualState.collectAsStateWithLifecycle()

    // Presence-orb input flow. MainActivity ensures the core is built before
    // first composition; the static-idle fallback keeps this robust to any
    // entry point that composes Home without the engine (e.g. previews/tests).
    val orbParams = remember {
        core?.renderParams ?: MutableStateFlow(OrbRenderParams.idle()).asStateFlow()
    }

    // Rarely-changing label source (mode changes on explicit transitions only).
    val modeFlow = remember { core?.presenceMode ?: flowOf(PresenceMode.ASLEEP) }
    val mode: PresenceMode by modeFlow.collectAsStateWithLifecycle(
        initialValue = core?.currentPresenceMode ?: PresenceMode.ASLEEP
    )

    // §2.31 render-lane inputs (budget for fps pacing, suspension for
    // background). Fallbacks are static-idle when the engine is absent.
    val orbBudget = remember {
        core?.budget ?: MutableStateFlow<MobileResourceManagement.ResourceBudget?>(null)
    }
    val orbSuspended = remember { core?.suspended ?: MutableStateFlow(false) }

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
            PresenceOrb(
                params = orbParams,
                size = 200.dp,
                onFrameReport = { start, duration -> core?.reportFrame(start, duration) },
                budget = orbBudget,
                suspended = orbSuspended,
                masterTimeOf = core?.let { c -> { n: Long -> c.masterElapsedNanos(n) } }
                    ?: { n: Long -> n },
                framePeriodNanos = core?.let { c -> { f: Double -> c.framePeriodNanos(f) } }
                    ?: { f: Double -> if (f > 0.0) (1_000_000_000L / f).toLong() else 0L }
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizonArc(
                progress = if (totalCount > 0) activeCount.toFloat() / totalCount else 0f,
                color = JarvisColors.CoreIdle
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "JARVIS — ${presenceLabel(mode)}",
                style = JarvisType.Display,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Body: ${bodyStateLabel(bodyState)}",
                style = JarvisType.Label.copy(color = JarvisColors.TextSecondary),
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

/** Readable label for the single presence mode (§2.1). */
private fun presenceLabel(mode: PresenceMode): String = when (mode) {
    PresenceMode.ASLEEP -> "asleep"
    PresenceMode.WAKING -> "waking"
    PresenceMode.IDLE -> "idle"
    PresenceMode.LISTENING -> "listening"
    PresenceMode.THINKING -> "thinking"
    PresenceMode.SPEAKING -> "speaking"
    PresenceMode.SHUTTING_DOWN -> "shutting down"
}

/** Readable label for body state. */
private fun bodyStateLabel(state: BodyState): String = when (state) {
    BodyState.IDLE -> "idle"
    BodyState.WAKE -> "wake detected"
    BodyState.LISTENING -> "listening"
    BodyState.HEARING -> "processing speech"
    BodyState.THINKING -> "thinking"
    BodyState.RETRIEVING -> "retrieving memory"
    BodyState.RESPONDING -> "preparing response"
    BodyState.SPEAKING -> "speaking"
    BodyState.INTERRUPTED -> "interrupted"
    BodyState.LEARNING -> "learning"
    BodyState.ERROR -> "error"
    BodyState.USER_PRESENT -> "user present"
    BodyState.USER_ABSENT -> "user absent"
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
