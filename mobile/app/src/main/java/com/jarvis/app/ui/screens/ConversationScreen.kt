package com.jarvis.app.ui.screens

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
import com.jarvis.app.body.BodyCoordinator
import com.jarvis.app.ui.components.StateChip
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import com.jarvis.app.ui.viewmodel.ConversationViewModel
import com.jarvis.app.ui.viewmodel.Turn

@Composable
fun ConversationScreen() {
    val conversationViewModel: ConversationViewModel = viewModel()
    val bodySlot = com.jarvis.app.ui.EngineAccess.bodyCoordinator()
    val bodyCoordinator = bodySlot.value
    if (bodyCoordinator == null) {
        com.jarvis.app.ui.WakingSurface(bodySlot.readiness)
        return
    }
    val turns by conversationViewModel.turns.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    fun sendCurrentInput() {
        if (input.isNotBlank()) {
            conversationViewModel.send(input)
            input = ""
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(JarvisColors.SurfaceBase)) {
        ContextStrip()

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(turns) { turn -> TurnRow(turn) }
        }

        InputRow(
            value = input,
            onValueChange = { input = it },
            onSend = { sendCurrentInput() },
            bodyCoordinator = bodyCoordinator
        )
    }
}

@Composable
private fun ContextStrip() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(JarvisColors.SurfacePanel)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = "FRIDAY · conversation", style = JarvisType.Label)
    }
}

@Composable
private fun TurnRow(turn: Turn) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (turn.fromJarvis) Arrangement.Start else Arrangement.End
    ) {
        if (turn.fromJarvis) {
            Column(modifier = Modifier.widthIn(max = 320.dp)) {
                StateChip(
                    label = if (turn.verified) "verified" else "uncertain",
                    dotColor = if (turn.verified) JarvisColors.CoreSuccess else JarvisColors.CoreOffline
                )
                Text(text = turn.text, style = JarvisType.Body, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(JarvisColors.SurfacePanel, RoundedCornerShape(4.dp))
                    .padding(12.dp)
            ) {
                Text(text = turn.text, style = JarvisType.Body)
            }
        }
    }
}

@Composable
private fun InputRow(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, bodyCoordinator: BodyCoordinator) {
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
            placeholder = { Text("Message Friday", style = JarvisType.Body) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisColors.SurfaceHairline,
                unfocusedBorderColor = JarvisColors.SurfaceHairline
            )
        )
        TextButton(onClick = onSend, enabled = value.isNotBlank()) {
            Text(text = "SEND", style = JarvisType.Label)
        }
        TextButton(
            onClick = { bodyCoordinator.toggleListening() },
            enabled = true
        ) {
            Text(text = "MIC", style = JarvisType.Label)
        }
    }
}
