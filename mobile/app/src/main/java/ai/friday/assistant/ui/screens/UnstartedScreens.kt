package ai.friday.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.friday.assistant.ui.EngineAccess
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType
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
            .background(FridayColors.SurfaceBase)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FRIDAY'S LOCAL MEMORY",
                style = FridayType.Display.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { refresh += 1 }) { Text("Refresh") }
        }
        Spacer(Modifier.height(16.dp))
        Text("Identity: ${individual.identity.name}", style = FridayType.Body)
        Text("Experience records: ${individual.experience.experienceCount()}", style = FridayType.Technical)
        Text("Learned feature links: ${individual.experience.associationCount()}", style = FridayType.Technical)
        Spacer(Modifier.height(12.dp))
        Text(
            "This is Friday's model-independent memory substrate. It currently stores feature observations and associations, not full conversation transcripts or verified human-like semantic memories.",
            style = FridayType.Body.copy(color = FridayColors.TextSecondary)
        )

        Spacer(Modifier.height(24.dp))
        Text("RECENT EXPERIENCES", style = FridayType.Label)
        if (experiences.isEmpty()) {
            Text("No local experiences recorded yet.", style = FridayType.Body)
        } else {
            experiences.forEach { experience ->
                val features = experience.features.keys
                    .filter { it.startsWith("word:") }
                    .take(10)
                    .joinToString(", ") { it.removePrefix("word:") }
                val outcomes = experience.outcomes.keys.take(6).joinToString(", ")
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("Experience #${experience.id} · ${Instant.ofEpochMilli(experience.timestampMs)}", style = FridayType.Technical)
                    Text(features.ifBlank { "No lexical features" }, style = FridayType.Body)
                    if (outcomes.isNotBlank()) {
                        Text("Observed outcome: $outcomes", style = FridayType.Technical)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("ASSOCIATIONS", style = FridayType.Label)
        if (associations.isEmpty()) {
            Text("Associations will appear after distinct experiences are connected.", style = FridayType.Body)
        } else {
            associations.forEach { association ->
                Text(
                    "${association.source}  →  ${association.target}  ·  ${association.observations} observation(s)",
                    style = FridayType.Technical,
                    modifier = Modifier.padding(vertical = 5.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmptyStateScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FridayColors.SurfaceBase)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = FridayType.Body.copy(color = FridayColors.TextSecondary),
            textAlign = TextAlign.Center
        )
    }
}
