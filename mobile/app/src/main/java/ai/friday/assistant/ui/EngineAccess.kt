package ai.friday.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.friday.assistant.EngineReadinessState
import ai.friday.assistant.EngineSlot
import ai.friday.assistant.FridayEngine
import ai.friday.assistant.body.BodyCoordinator
import ai.friday.assistant.env.LiquidEnvironmentManager
import ai.friday.assistant.model.ModelManager
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType

/**
 * LAUNCH-WAKE-ORDER AC1 & AC2: The ONE readiness-gated accessor file.
 *
 * UI code must reach the engine ONLY through this file.
 * Every accessor returns a typed [EngineSlot] (or a typed value) and never throws.
 */
object EngineAccess {

    val readinessState get() = FridayEngine.readinessState
    val currentReadiness get() = FridayEngine.currentReadiness

    fun bodyCoordinator(): EngineSlot<BodyCoordinator> =
        FridayEngine.bodyCoordinatorSlot()

    fun environmentManager(): EngineSlot<LiquidEnvironmentManager> =
        FridayEngine.environmentManagerSlot()

    fun modelManager(): EngineSlot<ModelManager> =
        FridayEngine.modelManagerSlot()

    fun fridayIndividual(): EngineSlot<ai.friday.assistant.cognitive.seed.FridayIndividual> =
        FridayEngine.fridayIndividualSlot()

    fun memoryGraphStore(): EngineSlot<ai.friday.assistant.memory.MemoryGraphStore> =
        FridayEngine.memoryGraphStoreSlot()

    fun sessionManager(): EngineSlot<ai.friday.assistant.session.SessionManager> =
        FridayEngine.sessionManagerSlot()

    fun taskInbox(): EngineSlot<ai.friday.assistant.inbox.LocalTaskInbox> =
        FridayEngine.taskInboxSlot()

    fun approvalQueue(): EngineSlot<ai.friday.assistant.approvals.LocalApprovalQueue> =
        FridayEngine.approvalQueueSlot()

    fun alertStore(): EngineSlot<ai.friday.assistant.alerts.LocalAlertStore> =
        FridayEngine.alertStoreSlot()

    fun bootstrapManager(): EngineSlot<ai.friday.assistant.bootstrap.BootstrapManager> =
        FridayEngine.bootstrapManagerSlot()

    fun failureSurface(): EngineSlot<ai.friday.assistant.failure.FailureSurface> =
        FridayEngine.failureSurfaceSlot()

    fun voiceOrganismHost(): EngineSlot<ai.friday.assistant.voice.VoiceOrganismHost> =
        FridayEngine.voiceOrganismHostSlot()

    fun voiceOutputPath(): EngineSlot<ai.friday.assistant.voice.VoiceOutputPath> =
        FridayEngine.voiceOutputPathSlot()
}

/**
 * Waking surface rendered until the engine is ready, or showing recorded failure if waking failed.
 */
@Composable
fun WakingSurface(
    state: EngineReadinessState = EngineAccess.currentReadiness,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FridayColors.SurfaceBase),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            when (state) {
                is EngineReadinessState.FailedToWake -> {
                    Text(
                        text = "WAKE FAILED",
                        style = FridayType.Display.copy(color = FridayColors.CoreWarning),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.error.message ?: "Unknown error during initialization",
                        style = FridayType.Body.copy(color = FridayColors.TextSecondary),
                        textAlign = TextAlign.Center
                    )
                }
                is EngineReadinessState.Ready -> {
                    Text(
                        text = "READY",
                        style = FridayType.Display.copy(color = FridayColors.TextPrimary),
                        textAlign = TextAlign.Center
                    )
                }
                is EngineReadinessState.Waking, is EngineReadinessState.NotStarted -> {
                    Text(
                        text = "WAKING...",
                        style = FridayType.Display.copy(color = FridayColors.TextPrimary),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Initializing cognitive systems",
                        style = FridayType.Body.copy(color = FridayColors.TextSecondary),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
