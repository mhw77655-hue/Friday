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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.friday.assistant.FridayMic
import ai.friday.assistant.ui.EngineAccess
import ai.friday.assistant.ui.components.GlassPanel
import ai.friday.assistant.ui.components.pressScale
import ai.friday.assistant.ui.components.rememberCapabilitySnapshot
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Friday's home is a living dashboard, not a full-screen avatar and one button. */
@Composable
fun HomeScreen(
    onOpenTools: () -> Unit,
    onOpenConversation: () -> Unit = {},
    onOpenMissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var maintenanceRunning by remember { mutableStateOf(false) }
    var maintenanceStatus by remember { mutableStateOf("") }

    val voiceSlot = EngineAccess.voiceOutputPath()
    val voiceOutput = voiceSlot.value
    val outputActive by (voiceOutput?.isSpeaking ?: flowOf(false)).collectAsStateWithLifecycle(initialValue = false)
    val outputLevel by (voiceOutput?.audioLevel ?: flowOf(0f)).collectAsStateWithLifecycle(initialValue = 0f)
    val inputLevel by FridayMic.lastLevel.collectAsStateWithLifecycle(initialValue = 0)
    val acousticActivity = outputActive || inputLevel > 35
    val acousticLevel = maxOf(outputLevel, (inputLevel / 120f).coerceIn(0f, 1f))

    val readiness by EngineAccess.readinessState.collectAsStateWithLifecycle()
    val readinessLabel = when (readiness) {
        is ai.friday.assistant.EngineReadinessState.Ready -> "PRESENT · CORE READY"
        else -> "INITIALISING · CORE STARTING"
    }
    val capsSnapshot by rememberCapabilitySnapshot()
    val enabledCount = remember(capsSnapshot) { capsSnapshot?.count { it.value } ?: 0 }
    val manifestCount = remember(capsSnapshot) { capsSnapshot?.size ?: 0 }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF101D2A), Color(0xFF080D14), FridayColors.SurfaceBase)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("FRIDAY", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                Text("Your space together", style = FridayType.Body)
            }
            Text(readinessLabel, style = FridayType.Technical.copy(color = FridayColors.TextSecondary), textAlign = TextAlign.End)
        }

        Spacer(Modifier.height(18.dp))
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ai.friday.assistant.ui.components.FridayAvatar(
                    size = 164.dp,
                    voiceActive = acousticActivity,
                    voiceLevel = acousticLevel,
                    morphologySeed = ai.friday.assistant.FridayEngine.fridayIndividual?.identity?.morphologySeed ?: 0L
                )
                Spacer(Modifier.height(8.dp))
                Text("I'm here.", style = FridayType.Display, textAlign = TextAlign.Center)
                Text(
                    "A place to talk, build, remember and grow.",
                    style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeActionTile(Modifier.weight(1f), "TALK", "Open conversation", onOpenConversation)
            HomeActionTile(Modifier.weight(1f), "OBJECTIVES", "Work in progress", onOpenMissions)
            HomeActionTile(Modifier.weight(1f), "CAPABILITIES", "$enabledCount / $manifestCount", onOpenTools)
        }

        Spacer(Modifier.height(18.dp))
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("DEVELOPMENT", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                Spacer(Modifier.height(6.dp))
                Text("Build the next version of Friday", style = FridayType.Body)
                Text(
                    "Run the phone's maintenance, Builder and repair lanes.",
                    style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Button(
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
                    enabled = !maintenanceRunning,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF176B83))
                ) {
                    Text(if (maintenanceRunning) "STARTING…" else "BUILD / REPAIR FRIDAY")
                }
                if (maintenanceStatus.isNotBlank()) {
                    Text(
                        maintenanceStatus,
                        style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        GlassPanel(modifier = Modifier.fillMaxWidth().pressScale(onClick = onOpenTools)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("CAPABILITY FIELD", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                Text(
                    "$enabledCount of $manifestCount capability entries currently enabled",
                    style = FridayType.Body,
                    modifier = Modifier.padding(top = 5.dp)
                )
                Text(
                    "Inspect what is available. Enabled flags are not proof of end-to-end operation.",
                    style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HomeActionTile(
    modifier: Modifier,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    GlassPanel(modifier = modifier.pressScale(onClick = onClick)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = FridayType.Label, textAlign = TextAlign.Center)
            Text(
                subtitle,
                style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}
