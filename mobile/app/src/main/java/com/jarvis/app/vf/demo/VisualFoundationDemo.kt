package com.jarvis.app.vf.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.app.vf.components.JvButton
import com.jarvis.app.vf.components.JvCard
import com.jarvis.app.vf.components.JvChip
import com.jarvis.app.vf.components.JvDialog
import com.jarvis.app.vf.hud.HudRenderer
import com.jarvis.app.vf.components.JvInput
import com.jarvis.app.vf.components.JvNotification
import com.jarvis.app.vf.components.JvPanel
import com.jarvis.app.vf.components.JvTieredPanel
import com.jarvis.app.vf.orb.OrbRenderer
import com.jarvis.app.vf.reactor.ReactorEngine
import com.jarvis.app.vf.tokens.JvTokens
import com.jarvis.app.vf.typography.TypographyEngine
import kotlinx.coroutines.delay

/**
 * Visual Foundation Demo — exercises every renderer and component
 * with static or derived state. No business logic, no brain wiring.
 *
 * Cycles through the 12 reactor states at a gentle pace, rendering
 * the orb + a representative HUD readout + all component types, so
 * every subsystem is device-verifiable in one screen.
 */
@Composable
fun VisualFoundationDemo() {
    var currentIndex by remember { mutableStateOf(0) }
    val states = ReactorEngine.ReactorSpecState.entries
    val currentState = states[currentIndex]

    // Cycle through states every 3 seconds (demo, not production)
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000L)
            currentIndex = (currentIndex + 1) % states.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JvTokens.VoidBlack)
            .padding(JvTokens.Space6)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(JvTokens.Space6)
    ) {
        // ---- Orb + state label ----
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            OrbRenderer(
                state = currentState,
                size = 200.dp
            )
        }
        Text(
            text = "STATE: ${currentState.name}",
            style = TypographyEngine.Heading.copy(color = currentState.color.color)
        )

        // ---- HUD readout ----
        Text("HUD Readout", style = TypographyEngine.Label)
        HudRenderer.Readout(label = "STATUS", value = currentState.name, accent = currentState.color.color)
        Spacer(modifier = Modifier.height(JvTokens.Space4))

        // ---- Panels ----
        Text("Panels", style = TypographyEngine.Label)
        JvTieredPanel(
            label = "SYSTEM STATUS",
            title = currentState.name,
            description = "Visual Foundation demo — all 12 reactor states cycle automatically.",
            accent = currentState.color.color
        )
        Spacer(modifier = Modifier.height(JvTokens.Space4))

        // ---- Buttons ----
        Text("Buttons", style = TypographyEngine.Label)
        Row(horizontalArrangement = Arrangement.spacedBy(JvTokens.Space3)) {
            JvButton(onClick = {}, accent = currentState.color.color) { Text("Glass Button") }
            JvButton(onClick = {}, accent = JvTokens.StateIdle) { Text("Idle Accent") }
            JvButton(onClick = {}, accent = currentState.color.color) { Text("Warning", style = TypographyEngine.Body.copy(color = JvTokens.StateWarning)) }
        }

        // ---- Input ----
        Text("Input", style = TypographyEngine.Label)
        JvInput(value = "", onValueChange = {}, placeholder = "Type here...", accent = currentState.color.color)

        // ---- Notification ----
        Text("Notification", style = TypographyEngine.Label)
        JvNotification(
            title = "System Alert",
            message = "All subsystems nominal — reactor is nominal.",
            accent = currentState.color.color
        )

        // ---- Dialog ----
        Text("Dialog", style = TypographyEngine.Label)
        JvDialog(
            title = "Confirm action?",
            message = "This will run a background task. Proceed?"
        )

        // ---- Cards ----
        Text("Cards", style = TypographyEngine.Label)
        Row(horizontalArrangement = Arrangement.spacedBy(JvTokens.Space3)) {
            JvCard(accent = JvTokens.StateIdle, selected = true) { Text("Device", style = TypographyEngine.Label) }
            JvCard(accent = JvTokens.StateThinking) { Text("Sensor", style = TypographyEngine.Label) }
            JvCard(accent = JvTokens.StateWarning) { Text("Alert", style = TypographyEngine.Label) }
        }

        // ---- Chips ----
        Text("Chips", style = TypographyEngine.Label)
        Row(horizontalArrangement = Arrangement.spacedBy(JvTokens.Space3)) {
            JvChip(label = "IDLE", value = "24.1°C", accent = JvTokens.StateIdle)
            JvChip(label = "TEMP", value = "73.8°C", accent = JvTokens.StateWarning)
            JvChip(label = "CPU", value = "14%", accent = JvTokens.StateIdle)
        }

        // ---- Typography sample ----
        Text("Typography", style = TypographyEngine.Label)
        Text("Display XL", style = TypographyEngine.DisplayXl)
        Text("Display", style = TypographyEngine.Display)
        Text("Heading", style = TypographyEngine.Heading)
        Text("Body text here.", style = TypographyEngine.Body)
        Text("caption text", style = TypographyEngine.Caption)
        Text("terminal: 127.0.0.1:8080", style = TypographyEngine.DataMono)
        Text("HUD: 42.0 Hz", style = TypographyEngine.Hud)
    }
}
