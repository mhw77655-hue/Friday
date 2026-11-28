package ai.friday.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import ai.friday.assistant.ui.components.StateChip
import ai.friday.assistant.ui.components.GlassPanel
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
    val context = LocalContext.current
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) bodyCoordinator.toggleListening()
        else Toast.makeText(context, "Microphone access was not granted. Typed conversation remains available.", Toast.LENGTH_LONG).show()
    }
    val turns by conversationViewModel.turns.collectAsStateWithLifecycle()
    val partialTranscript by ai.friday.assistant.SpeechBridge.partialTranscript.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun sendCurrentInput() {
        val message = input.trim()
        if (message.isNotEmpty()) {
            bodyCoordinator.submitText(message)
            input = ""
        }
    }

    LaunchedEffect(turns.size, partialTranscript) {
        val lastIndex = turns.lastIndex
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize().background(FridayColors.SurfaceBase)) {
        ConversationHeader(turnCount = turns.size)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (turns.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Start anywhere.", style = FridayType.Display)
                            Text(
                                "Talk naturally. Friday will respond using the capabilities that are actually available.",
                                style = FridayType.Body.copy(color = FridayColors.TextSecondary),
                                modifier = Modifier.padding(top = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
            items(turns) { turn -> TurnRow(turn) }
        }

        if (partialTranscript.isNotBlank()) {
            Text(
                text = "LISTENING  ·  $partialTranscript",
                style = FridayType.Technical.copy(color = Color(0xFF73D8F4)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp)
            )
        }

        InputRow(
            value = input,
            onValueChange = { input = it },
            onSend = { sendCurrentInput() },
            onMicClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    bodyCoordinator.toggleListening()
                } else {
                    microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        )
    }
}

@Composable
private fun ConversationHeader(turnCount: Int) {
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("FRIDAY", style = FridayType.Label.copy(color = Color(0xFF73D8F4)))
                Text("Conversation", style = FridayType.Body)
            }
            Text(
                if (turnCount == 0) "NEW SPACE" else "$turnCount TURNS",
                style = FridayType.Technical.copy(color = FridayColors.TextSecondary)
            )
        }
    }
}

@Composable
private fun TurnRow(turn: Turn) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (turn.fromFriday) Arrangement.Start else Arrangement.End
    ) {
        Column(modifier = Modifier.widthIn(max = 340.dp)) {
            Text(
                if (turn.fromFriday) "FRIDAY" else "YOU",
                style = FridayType.Technical.copy(color = if (turn.fromFriday) Color(0xFF73D8F4) else FridayColors.TextSecondary),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
            )
            Column(
                modifier = Modifier
                    .background(
                        color = if (turn.fromFriday) Color(0xFF111F2B) else Color(0xFF123B4C),
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomEnd = if (turn.fromFriday) 18.dp else 5.dp,
                            bottomStart = if (turn.fromFriday) 5.dp else 18.dp
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (turn.fromFriday) Color(0xFF294454) else Color(0xFF24647A),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 15.dp, vertical = 12.dp)
            ) {
                Text(text = turn.text, style = FridayType.Body)
            }
            if (turn.fromFriday) {
                Spacer(Modifier.height(4.dp))
                StateChip(
                    label = if (turn.verified) "verified response" else "not independently verified",
                    dotColor = if (turn.verified) FridayColors.CoreSuccess else FridayColors.CoreOffline
                )
            }
        }
    }
}

@Composable
private fun InputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit
) {
    GlassPanel(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Friday…", style = FridayType.Body) },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF73D8F4),
                    unfocusedBorderColor = FridayColors.SurfaceHairline,
                    cursorColor = Color(0xFF73D8F4)
                )
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = onSend, enabled = value.isNotBlank()) {
                    Text("SEND", style = FridayType.Label)
                }
                TextButton(onClick = onMicClick) {
                    Text("VOICE", style = FridayType.Label)
                }
            }
        }
    }
}
