package com.jarvis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.JarvisEngine
import com.jarvis.app.bootstrap.BootstrapManager
import com.jarvis.app.bootstrap.BootstrapState
import com.jarvis.app.bootstrap.BootstrapStep
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.launch

/**
 * Bootstrap Screen — first-run setup and boot status.
 * Wired to the BootstrapManager backend.
 */
@Composable
fun BootstrapScreen(
    bootstrapManager: BootstrapManager,
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val state by bootstrapManager.bootstrapState.collectAsStateWithLifecycle()
    val currentStep by bootstrapManager.currentStep.collectAsStateWithLifecycle()
    val progress by bootstrapManager.progress.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Build,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = JarvisColors.CoreIdle
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))

        Text(
            text = "JARVIS",
            style = JarvisType.Display.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = "Bootstrapping...",
            style = JarvisType.Body.copy(color = JarvisColors.TextSecondary)
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(32.dp))

        when (val s = state) {
            BootstrapState.NOT_STARTED -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = JarvisColors.CoreIdle
                )
            }
            BootstrapState.IN_PROGRESS -> {
                CircularProgressWithLabel(
                    progress = progress,
                    label = currentStep?.label ?: "Starting...",
                    stepNumber = currentStep?.ordinal?.plus(1) ?: 0,
                    totalSteps = BootstrapStep.values().size
                )
            }
            BootstrapState.COMPLETED -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = JarvisColors.CoreSuccess
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = "Bootstrap Complete",
                    style = JarvisType.Display.copy(fontSize = 20.sp)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Tap to continue",
                    style = JarvisType.Body.copy(color = JarvisColors.TextSecondary)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))
                Button(onClick = onComplete) {
                    Text("Continue")
                }
            }
            is BootstrapState.FAILED -> {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = JarvisColors.CoreWarning
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = "Bootstrap Failed",
                    style = JarvisType.Display.copy(fontSize = 20.sp, color = JarvisColors.CoreWarning)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = s.error?.message ?: "Unknown error",
                    style = JarvisType.Body.copy(color = JarvisColors.TextSecondary),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))
                Button(onClick = { scope.launch { bootstrapManager.retryCurrentStep() } }) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun CircularProgressWithLabel(
    progress: Float,
    label: String,
    stepNumber: Int,
    totalSteps: Int
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(120.dp)
        ) {
            val strokeWidth = 8f
            val radius = (120f - strokeWidth) / 2
            val center = androidx.compose.ui.geometry.Offset(60f, 60f)

            // Background circle
            drawCircle(
                color = JarvisColors.SurfacePanel,
                radius = radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )

            // Progress arc
            val sweepAngle = 360f * progress
            drawArc(
                color = JarvisColors.CoreIdle,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(120f, 120f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "${(progress * 100).toInt()}%", style = JarvisType.Display.copy(fontSize = 20.sp))
            Text(text = label, style = JarvisType.Body.copy(color = JarvisColors.TextSecondary))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(4.dp))
            Text(text = "Step $stepNumber of $totalSteps", style = JarvisType.Technical.copy(color = JarvisColors.TextSecondary))
        }
    }
}

/** Engine-held process-scoped singleton (nav graph injection). */
fun getBootstrapManager(): BootstrapManager = JarvisEngine.bootstrapManager
    ?: throw IllegalStateException("JarvisEngine.init() must run before getBootstrapManager()")