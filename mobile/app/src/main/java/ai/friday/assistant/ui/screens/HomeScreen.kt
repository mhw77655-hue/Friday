package ai.friday.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.friday.assistant.FridayMic
import ai.friday.assistant.FridayEngine
import ai.friday.assistant.cognitive.CognitiveState
import ai.friday.assistant.ui.EngineAccess
import ai.friday.assistant.ui.components.GlassPanel
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Friday's home foregrounds her identity, present direction and direct ways to engage. */
@Composable
fun HomeScreen(
    onOpenTools: () -> Unit,
    onOpenConversation: () -> Unit,
    onOpenObjectives: () -> Unit,
    onOpenIndividual: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var maintenanceRunning by remember { mutableStateOf(false) }
    var maintenanceStatus by remember { mutableStateOf("") }
    val individual = FridayEngine.fridayIndividual
    val cognitiveEngine = FridayEngine.cognitiveEngine
    val cognitiveFlow = cognitiveEngine?.cognitiveState ?: flowOf(CognitiveState())
    val cognitiveState by cognitiveFlow.collectAsStateWithLifecycle(initialValue = CognitiveState())
    val experienceCount = remember(individual) { individual?.experience?.experienceCount() ?: 0 }
    val voiceSlot = EngineAccess.voiceOutputPath()
    val voiceOutput = voiceSlot.value
    val outputActive by (voiceOutput?.isSpeaking ?: flowOf(false)).collectAsStateWithLifecycle(initialValue = false)
    val outputLevel by (voiceOutput?.audioLevel ?: flowOf(0f)).collectAsStateWithLifecycle(initialValue = 0f)
    val inputLevel by FridayMic.lastLevel.collectAsStateWithLifecycle(initialValue = 0)
    val acousticActivity = outputActive || inputLevel > 35
    val acousticLevel = maxOf(outputLevel, (inputLevel / 120f).coerceIn(0f, 1f))
    val readiness by EngineAccess.readinessState.collectAsStateWithLifecycle()
    val readinessLabel = when (readiness) {
        is ai.friday.assistant.EngineReadinessState.Ready -> "PRESENT"
        else -> "INITIALISING"
    }
    val activeGoal = cognitiveState.currentGoal

    Column(
        modifier = Modifier.fillMaxSize().background(FridayColors.SurfaceBase)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("FRIDAY", style = FridayType.Label.copy(color = FridayColors.CoreIdle))
                Text("Your space", style = FridayType.Technical.copy(color = FridayColors.TextSecondary))
            }
            Text(readinessLabel, style = FridayType.Technical.copy(color = FridayColors.TextSecondary))
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ai.friday.assistant.ui.components.FridayAvatar(
                size = 224.dp,
                voiceActive = acousticActivity,
                voiceLevel = acousticLevel,
                morphologySeed = individual?.identity?.morphologySeed ?: 0L
            )
            Spacer(Modifier.height(10.dp))
            Text(individual?.identity?.name ?: "Friday", style = FridayType.Display, textAlign = TextAlign.Center)
            Text(
                if (acousticActivity) "I'm here with you." else "A continuing presence on this device.",
                style = FridayType.Body.copy(color = FridayColors.TextSecondary),
                modifier = Modifier.padding(top = 5.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassPanel(modifier = Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("EXPERIENCES", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                        Text(experienceCount.toString(), style = FridayType.Display)
                    }
                }
                GlassPanel(modifier = Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("ASSOCIATIONS", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                        Text((individual?.experience?.associationCount() ?: 0).toString(), style = FridayType.Display)
                    }
                }
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("CURRENT DIRECTION", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                Text(
                    activeGoal?.description ?: "No active objective",
                    style = FridayType.Body,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    activeGoal?.let { it.status.name.replace('_', ' ') + " · " + it.priority.name }
                        ?: "Choose something meaningful for Friday to work toward.",
                    style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onOpenObjectives) { Text("OBJECTIVES") }
                    TextButton(onClick = onOpenIndividual) { Text("INDIVIDUALITY") }
                    TextButton(onClick = onOpenConversation) { Text("TALK") }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onOpenTools) { Text("CAPABILITIES") }
            TextButton(
                onClick = {
                    if (!maintenanceRunning) {
                        maintenanceRunning = true
                        maintenanceStatus = "Starting maintenance lanes…"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { ai.friday.assistant.bootstrap.FridayOneButtonMaintenance.start(context) }
                            }
                            maintenanceStatus = result.fold(
                                onSuccess = { it.summary() },
                                onFailure = { "Maintenance could not start: " + (it.message ?: it.javaClass.simpleName) }
                            )
                            maintenanceRunning = false
                        }
                    }
                },
                enabled = !maintenanceRunning
            ) {
                Text(if (maintenanceRunning) "STARTING…" else "BUILD / REPAIR")
            }
        }
        if (maintenanceStatus.isNotBlank()) {
            Text(
                maintenanceStatus,
                style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}
