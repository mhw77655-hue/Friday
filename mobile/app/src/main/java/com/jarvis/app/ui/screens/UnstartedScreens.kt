package com.jarvis.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.app.ui.EngineAccess
import com.jarvis.app.ui.components.FridayBrandMark
import com.jarvis.app.ui.components.FridayMetricTile
import com.jarvis.app.ui.components.FridayPanel
import com.jarvis.app.ui.components.FridayScreenHeader
import com.jarvis.app.ui.components.FridaySectionHeading
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import java.time.Instant

/**
 * Honest empty states — only real backends are presented as live capabilities.
 */
@Composable
fun MissionsScreen() {
    EmptyStateScreen(message = "No missions yet — Friday has not identified a verified capability gap.")
}

@Composable
fun SandboxScreen() {
    EmptyStateScreen(
        message = "The component-cycle preview is a developer test, not Friday's live presence. Her persistent visual embodiment is on Home."
    )
}

@Composable
fun MemoryScreen() {
    val individual = EngineAccess.fridayIndividual().value
    if (individual == null) {
        EmptyStateScreen("Friday's local individual is not ready yet.")
        return
    }

    var refresh by remember { mutableIntStateOf(0) }
    val experiences = remember(individual, refresh) {
        individual.experience.recentExperiences(16).asReversed()
    }
    val associations = remember(individual, refresh) {
        individual.experience.associationSnapshot(24)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp)
    ) {
        FridayScreenHeader(
            eyebrow = "Continuity and experience",
            title = "Memory",
            description = "A window into Friday's local experience substrate and the associations it has recorded."
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FridayMetricTile(
                label = "Experiences",
                value = individual.experience.experienceCount().toString(),
                accent = JarvisColors.CoreThinking,
                modifier = Modifier.weight(1f)
            )
            FridayMetricTile(
                label = "Associations",
                value = individual.experience.associationCount().toString(),
                accent = JarvisColors.CoreIdle,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("IDENTITY: ${individual.identity.name}", style = JarvisType.Technical)
            TextButton(onClick = { refresh += 1 }) {
                Text("Refresh", style = JarvisType.Label.copy(color = JarvisColors.CoreThinking))
            }
        }

        FridayPanel(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                "WHAT THIS MEMORY IS",
                style = JarvisType.Label.copy(color = JarvisColors.CoreIdle)
            )
            Text(
                "This is Friday's model-independent memory substrate. It currently stores feature observations and associations, not full conversation transcripts or verified human-like semantic memories.",
                style = JarvisType.Body.copy(color = JarvisColors.TextSecondary)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            FridaySectionHeading("Recent experiences", "${experiences.size} shown")
            if (experiences.isEmpty()) {
                FridayPanel {
                    Text("No local experiences recorded yet.", style = JarvisType.Body)
                    Text(
                        "Experience records will appear after Friday observes and persists an event.",
                        style = JarvisType.Technical
                    )
                }
            } else {
                experiences.forEach { experience ->
                    val features = experience.features.keys
                        .filter { it.startsWith("word:") }
                        .take(10)
                        .joinToString(", ") { it.removePrefix("word:") }
                    val outcomes = experience.outcomes.keys.take(6).joinToString(", ")
                    FridayPanel(modifier = Modifier.padding(bottom = 9.dp)) {
                        Text(
                            "EXPERIENCE #${experience.id}",
                            style = JarvisType.Label.copy(color = JarvisColors.CoreThinking)
                        )
                        Text(
                            Instant.ofEpochMilli(experience.timestampMs).toString(),
                            style = JarvisType.Technical.copy(color = JarvisColors.TextMuted)
                        )
                        Text(
                            features.ifBlank { "No lexical features" },
                            style = JarvisType.Body
                        )
                        if (outcomes.isNotBlank()) {
                            Text(
                                "Observed outcome: $outcomes",
                                style = JarvisType.Technical
                            )
                        }
                    }
                }
            }

            FridaySectionHeading("Associations", "${associations.size} shown")
            if (associations.isEmpty()) {
                FridayPanel {
                    Text("No associations recorded yet.", style = JarvisType.Body)
                    Text(
                        "Associations appear when distinct observations are linked.",
                        style = JarvisType.Technical
                    )
                }
            } else {
                associations.forEach { association ->
                    FridayPanel(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            "${association.source}  →  ${association.target}",
                            style = JarvisType.Body
                        )
                        Text(
                            "${association.observations} observation(s)",
                            style = JarvisType.Technical.copy(color = JarvisColors.TextSecondary)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun EmptyStateScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(84.dp),
                contentAlignment = Alignment.Center
            ) {
                FridayBrandMark(Modifier.fillMaxSize())
            }
            Text("NOT AVAILABLE YET", style = JarvisType.Label.copy(color = JarvisColors.CoreThinking))
            Text(
                text = message,
                style = JarvisType.Body.copy(color = JarvisColors.TextSecondary),
                textAlign = TextAlign.Center
            )
        }
    }
}
