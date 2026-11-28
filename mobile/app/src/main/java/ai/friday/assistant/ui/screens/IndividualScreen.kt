package ai.friday.assistant.ui.screens

import ai.friday.assistant.FridayEngine
import ai.friday.assistant.ui.components.GlassPanel
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Inspect Friday's real local identity, durable experiences and learned associations. */
@Composable
fun IndividualScreen() {
    val individual = FridayEngine.fridayIndividual
    var refreshKey by remember { mutableIntStateOf(0) }
    val experiences = remember(individual, refreshKey) {
        individual?.experience?.recentExperiences(20)?.reversed().orEmpty()
    }
    val associations = remember(individual, refreshKey) {
        individual?.experience?.associationSnapshot(40).orEmpty()
    }
    val dateFormat = remember {
        DateTimeFormatter.ofPattern("d MMM · HH:mm").withZone(ZoneId.systemDefault())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("INDIVIDUALITY", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                Text("Friday's continuity", style = FridayType.Display)
                Text(
                    "A transparent view of the local identity and experience substrate—not a claim that every association is understanding.",
                    style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
        item {
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("PERSISTENT IDENTITY", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                    Text(individual?.identity?.name ?: "Identity not ready", style = FridayType.Display)
                    Text(
                        individual?.identity?.id ?: "No identity instance is available.",
                        style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("EXPERIENCES   ${individual?.experience?.experienceCount() ?: 0}", style = FridayType.Body)
                    Text("ASSOCIATIONS   ${individual?.experience?.associationCount() ?: 0}", style = FridayType.Body)
                    TextButton(onClick = { refreshKey++ }) { Text("REFRESH LOCAL RECORD") }
                }
            }
        }
        item { Text("RECENT EXPERIENCES", style = FridayType.Label.copy(color = FridayColors.TextSecondary)) }
        if (experiences.isEmpty()) {
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Text("No local seed experiences are available yet.", style = FridayType.Body, modifier = Modifier.padding(16.dp))
                }
            }
        } else {
            items(experiences, key = { it.id }) { experience ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("#${experience.id} · ${dateFormat.format(Instant.ofEpochMilli(experience.timestampMs))}", style = FridayType.Technical.copy(color = FridayColors.TextSecondary))
                        Text(
                            "Observed features: " + experience.features.keys.take(8).joinToString(", "),
                            style = FridayType.Body,
                            modifier = Modifier.padding(top = 5.dp)
                        )
                        if (experience.outcomes.isNotEmpty()) {
                            Text(
                                "Observed outcome: " + experience.outcomes.keys.take(6).joinToString(", "),
                                style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        item { Text("LEARNED ASSOCIATIONS", style = FridayType.Label.copy(color = FridayColors.TextSecondary)) }
        if (associations.isEmpty()) {
            item { Text("No associations have been formed yet.", style = FridayType.Body) }
        } else {
            items(associations, key = { it.source + "->" + it.target }) { association ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(association.source + "  →  " + association.target, style = FridayType.Body)
                        Text(
                            "strength " + association.strength.toString() + " · " + association.observations + " observation(s)",
                            style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
        item {
            Text(
                "These are durable feature links learned from observed co-occurrence and outcomes. They are not, by themselves, verified meanings or proof of general understanding.",
                style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
