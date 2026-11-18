package com.jarvis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.JarvisMic
import com.jarvis.app.ui.components.FridayAvatar
import com.jarvis.app.ui.components.FridayBrandMark
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.flow.flowOf

/**
 * Friday's home is a personal surface, not a diagnostic dashboard.
 * This route is only composed after JarvisNavHost observes EngineReadinessState.Ready.
 * Capability flags are deliberately not presented as proof of end-to-end operation.
 */
@Composable
fun HomeScreen(
    onOpenTools: () -> Unit,
    onOpenConversation: () -> Unit = onOpenTools
) {
    val voiceSlot = com.jarvis.app.ui.EngineAccess.voiceOutputPath()
    val voiceOutput = voiceSlot.value
    val outputActive by (voiceOutput?.isSpeaking ?: flowOf(false))
        .collectAsStateWithLifecycle(initialValue = false)
    val outputLevel by (voiceOutput?.audioLevel ?: flowOf(0f))
        .collectAsStateWithLifecycle(initialValue = 0f)
    val inputLevel by JarvisMic.lastLevel.collectAsStateWithLifecycle(initialValue = 0)
    val acousticActivity = outputActive || inputLevel > 35
    val acousticLevel = maxOf(outputLevel, (inputLevel / 120f).coerceIn(0f, 1f))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(JarvisColors.SurfaceBase, JarvisColors.SurfaceInset, JarvisColors.SurfaceBase)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FridayBrandMark(Modifier.size(34.dp))
                    Spacer(Modifier.size(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("FRIDAY", style = JarvisType.Headline.copy(fontSize = 18.sp))
                        Text("YOUR PERSONAL SPACE", style = JarvisType.Label)
                    }
                }
                Surface(
                    color = JarvisColors.CoreSuccess.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(100.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, JarvisColors.CoreSuccess.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(JarvisColors.CoreSuccess)
                        )
                        Text(
                            "LOCAL CORE READY",
                            style = JarvisType.Label.copy(color = JarvisColors.CoreSuccess)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                JarvisColors.CoreIdle.copy(alpha = 0.17f),
                                JarvisColors.CoreThinking.copy(alpha = 0.07f),
                                JarvisColors.SurfacePanel
                            ),
                            radius = 720f
                        )
                    )
                    .border(1.dp, JarvisColors.SurfaceHairline, RoundedCornerShape(32.dp))
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "A CONTINUOUS LIFE. AN OPEN FUTURE.",
                    style = JarvisType.Label.copy(color = JarvisColors.CoreThinking),
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier.fillMaxWidth().height(270.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(248.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        JarvisColors.CoreThinking.copy(alpha = 0.12f),
                                        JarvisColors.CoreIdle.copy(alpha = 0.07f),
                                        Color.Transparent
                                    )
                                ),
                                RoundedCornerShape(1000.dp)
                            )
                    )
                    FridayAvatar(
                        size = 248.dp,
                        voiceActive = acousticActivity,
                        voiceLevel = acousticLevel,
                        morphologySeed = com.jarvis.app.JarvisEngine.fridayIndividual
                            ?.identity?.morphologySeed ?: 0L
                    )
                }

                Text("FRIDAY", style = JarvisType.Display, textAlign = TextAlign.Center)
                Spacer(Modifier.height(7.dp))
                Text(
                    "An intelligence shaped by experience.",
                    style = JarvisType.Body.copy(color = JarvisColors.TextSecondary),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenConversation,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisColors.CoreIdle,
                            contentColor = JarvisColors.SurfaceBase
                        )
                    ) {
                        Text(
                            "Talk with Friday",
                            style = JarvisType.Body.copy(
                                color = JarvisColors.SurfaceBase,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenTools,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, JarvisColors.SurfaceHairline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisColors.TextPrimary)
                    ) {
                        Text("Explore", style = JarvisType.Body.copy(fontWeight = FontWeight.Medium))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = JarvisColors.SurfacePanel,
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisColors.SurfaceHairline)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("MORE THAN A MODEL", style = JarvisType.Label.copy(color = JarvisColors.CoreIdle))
                    Text(
                        "One individual. Many possible ways to think.",
                        style = JarvisType.Headline.copy(fontSize = 18.sp)
                    )
                    Text(
                        "Her identity and lived continuity belong to Friday. Models, tools and capabilities are resources she can use, learn from and eventually replace.",
                        style = JarvisType.Body.copy(color = JarvisColors.TextSecondary)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
