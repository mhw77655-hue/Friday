package ai.friday.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import ai.friday.assistant.ui.EngineAccess
import ai.friday.assistant.ui.components.GlassPanel
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The phone home stays sparse: Friday's reactor first, one useful panel when needed. */
@Composable
fun HomeScreen(onOpenTools: () -> Unit) {
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
        is ai.friday.assistant.EngineReadinessState.Ready -> "CORE READY"
        else -> "INITIALISING"
    }

    Column(
        modifier = Modifier.fillMaxSize().background(FridayColors.SurfaceBase),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ai.friday.assistant.ui.components.FridayAvatar(
                size = 280.dp,
                voiceActive = false,
                voiceLevel = 0f,
                morphologySeed = ai.friday.assistant.FridayEngine.fridayIndividual?.identity?.morphologySeed ?: 0L
            )
            Spacer(Modifier.height(20.dp))
            Text("FRIDAY", style = FridayType.Display, textAlign = TextAlign.Center)
            Text(
                readinessLabel,
                style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        GlassPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("DEVELOPMENT", style = FridayType.Label)
                Text(
                    "Build / repair Friday",
                    style = FridayType.Body,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "Start the phone's maintenance, Builder and repair lanes.",
                    style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
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
                    Text(if (maintenanceRunning) "STARTING…" else "BUILD / REPAIR FRIDAY")
                }
                if (maintenanceStatus.isNotBlank()) {
                    Text(
                        maintenanceStatus,
                        style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                TextButton(onClick = onOpenTools) {
                    Text("CAPABILITIES")
                }
            }
        }
    }
}
