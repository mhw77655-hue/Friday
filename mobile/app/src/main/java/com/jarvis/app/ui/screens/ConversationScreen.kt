package com.jarvis.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.app.body.BodyCoordinator
import com.jarvis.app.ui.components.FridayBrandMark
import com.jarvis.app.ui.components.FridayNavigationGlyph
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
            bodyCoordinator.submitText(input)
            input = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(JarvisColors.SurfaceBase)
    ) {
        ConversationHeader()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (turns.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 42.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .background(JarvisColors.CoreIdle.copy(alpha = 0.10f), RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            FridayBrandMark(Modifier.size(58.dp))
                        }
                        Text("Start anywhere.", style = JarvisType.Headline)
                        Text(
                            "Ask a question, share an idea, or tell Friday what happened.",
                            style = JarvisType.Body.copy(color = JarvisColors.TextSecondary),
                            modifier = Modifier.widthIn(max = 290.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
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
private fun ConversationHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(JarvisColors.SurfacePanel)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            FridayBrandMark(Modifier.size(30.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("FRIDAY", style = JarvisType.Headline.copy(fontSize = 17.sp))
                Text("A CONVERSATION", style = JarvisType.Label)
            }
        }
        Text("PRIVATE SPACE", style = JarvisType.Label.copy(color = JarvisColors.CoreThinking))
    }
}

@Composable
private fun TurnRow(turn: Turn) {
    if (turn.fromJarvis) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            FridayBrandMark(Modifier.size(23.dp).padding(top = 2.dp))
            Column(
                modifier = Modifier.padding(start = 10.dp).widthIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                StateChip(
                    label = if (turn.verified) "verified" else "needs review",
                    dotColor = if (turn.verified) JarvisColors.CoreSuccess else JarvisColors.CoreAttention
                )
                Surface(
                    color = JarvisColors.SurfaceRaised,
                    shape = RoundedCornerShape(
                        topStart = 5.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp
                    ),
                    border = BorderStroke(1.dp, JarvisColors.SurfaceHairline)
                ) {
                    Text(
                        text = turn.text,
                        style = JarvisType.Body,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)
                    )
                }
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                modifier = Modifier.widthIn(max = 320.dp),
                color = JarvisColors.UserBubble,
                shape = RoundedCornerShape(
                    topStart = 22.dp, topEnd = 5.dp, bottomEnd = 22.dp, bottomStart = 22.dp
                ),
                border = BorderStroke(1.dp, JarvisColors.CoreIdle.copy(alpha = 0.18f))
            ) {
                Text(
                    text = turn.text,
                    style = JarvisType.Body,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)
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
    bodyCoordinator: BodyCoordinator
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        color = JarvisColors.SurfacePanel,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, JarvisColors.SurfaceHairline)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Write to Friday…", style = JarvisType.Body.copy(color = JarvisColors.TextMuted))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = JarvisColors.CoreThinking
                )
            )
            IconButton(onClick = { bodyCoordinator.toggleListening() }) {
                FridayNavigationGlyph(
                    kind = "mic",
                    color = JarvisColors.CoreThinking,
                    modifier = Modifier.size(21.dp)
                )
            }
            IconButton(onClick = onSend, enabled = value.isNotBlank()) {
                FridayNavigationGlyph(
                    kind = "send",
                    color = if (value.isNotBlank()) JarvisColors.CoreIdle else JarvisColors.TextMuted,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}
