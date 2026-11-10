package com.jarvis.app.ui

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
import com.jarvis.app.EngineReadinessState
import com.jarvis.app.EngineSlot
import com.jarvis.app.JarvisEngine
import com.jarvis.app.body.BodyCoordinator
import com.jarvis.app.env.LiquidEnvironmentManager
import com.jarvis.app.model.ModelManager
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType

/**
 * LAUNCH-WAKE-ORDER AC1 & AC2: The ONE readiness-gated accessor file.
 *
 * UI code must reach the engine ONLY through this file.
 * Every accessor returns a typed [EngineSlot] (or a typed value) and never throws.
 */
object EngineAccess {

    val readinessState get() = JarvisEngine.readinessState
    val currentReadiness get() = JarvisEngine.currentReadiness

    fun bodyCoordinator(): EngineSlot<BodyCoordinator> =
        JarvisEngine.bodyCoordinatorSlot()

    fun environmentManager(): EngineSlot<LiquidEnvironmentManager> =
        JarvisEngine.environmentManagerSlot()

    fun modelManager(): EngineSlot<ModelManager> =
        JarvisEngine.modelManagerSlot()

    fun sessionManager(): EngineSlot<com.jarvis.app.session.SessionManager> =
        JarvisEngine.sessionManagerSlot()

    fun taskInbox(): EngineSlot<com.jarvis.app.inbox.LocalTaskInbox> =
        JarvisEngine.taskInboxSlot()

    fun approvalQueue(): EngineSlot<com.jarvis.app.approvals.LocalApprovalQueue> =
        JarvisEngine.approvalQueueSlot()

    fun alertStore(): EngineSlot<com.jarvis.app.alerts.LocalAlertStore> =
        JarvisEngine.alertStoreSlot()

    fun bootstrapManager(): EngineSlot<com.jarvis.app.bootstrap.BootstrapManager> =
        JarvisEngine.bootstrapManagerSlot()

    fun failureSurface(): EngineSlot<com.jarvis.app.failure.FailureSurface> =
        JarvisEngine.failureSurfaceSlot()
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
            .background(JarvisColors.SurfaceBase),
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
                        style = JarvisType.Display.copy(color = JarvisColors.StateDegraded),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.error.message ?: "Unknown error during initialization",
                        style = JarvisType.Body.copy(color = JarvisColors.TextSecondary),
                        textAlign = TextAlign.Center
                    )
                }
                is EngineReadinessState.Ready -> {
                    Text(
                        text = "READY",
                        style = JarvisType.Display.copy(color = JarvisColors.TextPrimary),
                        textAlign = TextAlign.Center
                    )
                }
                is EngineReadinessState.Waking, is EngineReadinessState.NotStarted -> {
                    Text(
                        text = "WAKING...",
                        style = JarvisType.Display.copy(color = JarvisColors.TextPrimary),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Initializing cognitive systems",
                        style = JarvisType.Body.copy(color = JarvisColors.TextSecondary),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
