package com.friday.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.friday.app.body.BodyCoordinator
import com.friday.app.body.BodyState
import com.friday.app.body.VisualState
import com.friday.app.FridayEngine
import com.friday.app.latency.LatencyLayer
import com.friday.app.ui.components.StateChip
import com.friday.app.ui.theme.FridayColors
import com.friday.app.ui.theme.FridayType
import com.friday.app.ui.viewmodel.ConversationViewModel
import com.friday.app.ui.viewmodel.Turn

@Composable
fun ConversationScreen() {
    val conversationViewModel: ConversationViewModel = viewModel()
    val bodySlot = com.friday.app.ui.EngineAccess.bodyCoordinator()
    val bodyCoordinator = bodySlot.value
    if (bodyCoordinator == null) {
        com.friday.app.ui.WakingSurface(bodySlot.readiness)
        return
    }
    val bodyState by bodyCoordinator.state.collectAsStateWithLifecycle()
    val visualState by bodyCoordinator.visualState.collectAsStateWithLifecycle()
    val turns by conversationViewModel.turns.collectAsStateWithLifecycle()
    val phase by conversationViewModel.phase.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    fun sendCurrentInput() {
        if (input.isNotBlank()) {
            conversationViewModel.send(input)
            input = ""
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(FridayColors.SurfaceBase)) {
        ContextStrip()

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(turns) { turn -> TurnRow(turn) }
        }

        // Latency-first affordance: as soon as input is sent, FRIDAY is
        // acknowledged and visibly working even while the deep reasoning is
        // still producing the full answer. Hidden at IDLE.
        ThinkingRow(phase)

        InputRow(
            value = input,
            onValueChange = { input = it },
            onSend = { sendCurrentInput() },
            bodyCoordinator = bodyCoordinator,
            bodyState = bodyState
        )
    }
}

@Composable
private fun ContextStrip() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(FridayColors.SurfacePanel)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = "Synced to Obsidian vault", style = FridayType.Label)
    }
}

@Composable
private fun ThinkingRow(phase: LatencyLayer.Phase) {
    val label = when (phase) {
        LatencyLayer.Phase.ACKNOWLEDGING -> "FRIDAY is listening"
        LatencyLayer.Phase.THINKING -> "FRIDAY is thinking"
        LatencyLayer.Phase.THINKING_LONG -> "FRIDAY is still thinking"
        LatencyLayer.Phase.REPLYING -> "FRIDAY is replying"
        LatencyLayer.Phase.IDLE -> null
    } ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(text = label, style = FridayType.Label, color = FridayColors.TextSecondary)
    }
}

@Composable
private fun TurnRow(turn: Turn) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (turn.fromFriday) Arrangement.Start else Arrangement.End
    ) {
        if (turn.fromFriday) {
            Column(modifier = Modifier.widthIn(max = 320.dp)) {
                StateChip(
                    label = if (turn.verified) "verified" else "uncertain",
                    dotColor = if (turn.verified) FridayColors.CoreSuccess else FridayColors.CoreOffline
                )
                Text(text = turn.text, style = FridayType.Body, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(FridayColors.SurfacePanel, RoundedCornerShape(4.dp))
                    .padding(12.dp)
            ) {
                Text(text = turn.text, style = FridayType.Body)
            }
        }
    }
}

@Composable
private fun InputRow(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, bodyCoordinator: BodyCoordinator, bodyState: BodyState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message FRIDAY", style = FridayType.Body) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FridayColors.SurfaceHairline,
                unfocusedBorderColor = FridayColors.SurfaceHairline
            )
        )
        TextButton(onClick = onSend, enabled = value.isNotBlank()) {
            Text(text = "SEND", style = FridayType.Label)
        }
        TextButton(
            onClick = {
                // Wire mic to body coordinator - toggle listening
                if (bodyState == BodyState.LISTENING || bodyState == BodyState.WAKE) {
                    // Already listening, could stop
                } else {
                    // Signal body coordinator to start listening
                    bodyCoordinator.emitEvent(com.friday.app.body.BodyEvent.SpeechStart)
                }
            },
            enabled = true
        ) {
            Text(
                text = when (bodyState) {
                    BodyState.LISTENING, BodyState.WAKE -> "STOP"
                    BodyState.SPEAKING -> "INTERRUPT"
                    else -> "MIC"
                },
                style = FridayType.Label
            )
        }
    }
}
