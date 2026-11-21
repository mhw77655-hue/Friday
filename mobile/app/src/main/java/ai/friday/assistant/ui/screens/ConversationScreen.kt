package ai.friday.assistant.ui.screens

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
import ai.friday.assistant.body.BodyCoordinator
import ai.friday.assistant.ui.components.StateChip
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType
import ai.friday.assistant.ui.viewmodel.ConversationViewModel
import ai.friday.assistant.ui.viewmodel.Turn

@Composable
fun ConversationScreen() {
    val conversationViewModel: ConversationViewModel = viewModel()
    val bodySlot = ai.friday.assistant.ui.EngineAccess.bodyCoordinator()
    val bodyCoordinator = bodySlot.value
    if (bodyCoordinator == null) {
        ai.friday.assistant.ui.WakingSurface(bodySlot.readiness)
        return
    }
    val turns by conversationViewModel.turns.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    fun sendCurrentInput() {
        if (input.isNotBlank()) {
            bodyCoordinator.submitText(input)
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
            .background(FridayColors.SurfacePanel)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = "FRIDAY · conversation", style = FridayType.Label)
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
            placeholder = { Text("Message Friday", style = FridayType.Body) },
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
            onClick = { bodyCoordinator.toggleListening() },
            enabled = true
        ) {
            Text(text = "MIC", style = FridayType.Label)
        }
    }
}
