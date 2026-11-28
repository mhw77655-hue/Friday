package ai.friday.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ai.friday.assistant.memory.MemoryNode
import ai.friday.assistant.ui.EngineAccess
import ai.friday.assistant.ui.components.GlassPanel
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val graphStore = EngineAccess.memoryGraphStore().value
    var refresh by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var facts by remember { mutableStateOf<List<MemoryNode>>(emptyList()) }
    var historyChains by remember { mutableStateOf<List<List<MemoryNode>>>(emptyList()) }
    var totalRows by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(graphStore, refresh) {
        if (graphStore == null) {
            facts = emptyList()
            historyChains = emptyList()
            totalRows = 0L
            loading = false
            loadError = "The local memory graph is not ready."
        } else {
            loading = true
            loadError = null
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    val current = graphStore.query()
                    val keys = current.map { it.subject to it.predicate }.distinct()
                    val histories = keys.map { (subject, predicate) ->
                        graphStore.getHistory(subject, predicate)
                    }.filter { it.size > 1 }
                    Triple(current, histories, graphStore.nodeCount())
                }
                facts = snapshot.first.sortedByDescending { it.validFrom }
                historyChains = snapshot.second
                totalRows = snapshot.third
            } catch (failure: Exception) {
                loadError = failure.message ?: "Friday could not read the local memory graph."
            } finally {
                loading = false
            }
        }
    }

    val query = search.trim()
    val visibleFacts = remember(facts, query) {
        facts.filter { node ->
            query.isBlank() || listOf(node.subject, node.predicate, node.`object`, node.source)
                .any { it.contains(query, ignoreCase = true) }
        }
    }
    val resurfaced = remember(visibleFacts) {
        visibleFacts.sortedWith(compareByDescending<MemoryNode> { it.salience }.thenBy { it.validFrom }).take(6)
    }
    val connections = remember(visibleFacts) { visibleFacts.groupBy { it.subject }.toSortedMap() }
    val sources = remember(visibleFacts) {
        visibleFacts.groupBy { it.source.ifBlank { "Unattributed" } }
            .toList()
            .sortedWith(compareByDescending<Pair<String, List<MemoryNode>>> { it.second.size }.thenBy { it.first })
    }
    val tensions = remember(historyChains, query) {
        historyChains.filter { chain ->
            chain.size > 1 && (query.isBlank() || chain.any { node ->
                listOf(node.subject, node.predicate, node.`object`, node.source)
                    .any { it.contains(query, ignoreCase = true) }
            })
        }.sortedByDescending { it.lastOrNull()?.validFrom ?: 0L }
    }
    val experiences = remember(individual, refresh) {
        individual?.experience?.recentExperiences(12)?.asReversed().orEmpty()
    }
    val experienceCount = individual?.experience?.experienceCount() ?: 0
    val historicalRows = (totalRows - facts.size).coerceAtLeast(0L)
    val tabs = listOf("Resurface", "Connections", "Sources", "Tensions")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FridayColors.SurfaceBase)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("FRIDAY'S MEMORY", style = FridayType.Display.copy(fontWeight = FontWeight.SemiBold))
                Text("A living view of what she has retained", style = FridayType.Technical.copy(color = FridayColors.TextSecondary))
            }
            Button(onClick = { refresh += 1 }) { Text("Refresh") }
        }

        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            MemoryMetric("ACTIVE FACTS", facts.size.toString(), Modifier.weight(1f))
            MemoryMetric("HISTORY", historicalRows.toString(), Modifier.weight(1f))
            MemoryMetric("EXPERIENCES", experienceCount.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search remembered facts and sources") }
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
        ) {
            tabs.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = FridayType.Label.copy(color = if (selectedTab == index) FridayColors.TextPrimary else FridayColors.TextSecondary),
                    modifier = Modifier.clickable { selectedTab = index }.padding(vertical = 8.dp)
                )
            }
        }

        if (loading) {
            Text("Reading Friday's local memory graph…", style = FridayType.Body.copy(color = FridayColors.TextSecondary))
        } else if (loadError != null) {
            GlassPanel(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("GRAPH UNAVAILABLE", style = FridayType.Label)
                    Text(loadError.orEmpty(), style = FridayType.Body.copy(color = FridayColors.TextSecondary))
                }
            }
        }

        when (selectedTab) {
            0 -> {
                MemorySectionHeading("RESURFACE", "High-salience facts worth revisiting")
                if (resurfaced.isEmpty()) MemoryEmpty("No graph memories match this search yet.")
                else resurfaced.forEach { node -> MemoryFactCard(node) }

                Spacer(Modifier.height(18.dp))
                MemorySectionHeading("RECENT EXPERIENCES", "The model-independent experience record")
                if (experiences.isEmpty()) MemoryEmpty("No local experiences have been recorded yet.")
                else experiences.forEach { experience ->
                    val features = experience.features.keys.filter { it.startsWith("word:") }
                        .take(8).joinToString(", ") { it.removePrefix("word:") }
                    val outcomes = experience.outcomes.keys.take(5).joinToString(", ")
                    GlassPanel(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Experience #${experience.id} · ${Instant.ofEpochMilli(experience.timestampMs)}", style = FridayType.Technical.copy(color = FridayColors.TextSecondary))
                            Text(features.ifBlank { "No lexical features recorded" }, style = FridayType.Body)
                            if (outcomes.isNotBlank()) Text("Observed outcome · $outcomes", style = FridayType.Technical)
                        }
                    }
                }
            }
            1 -> {
                MemorySectionHeading("CONNECTIONS", "Subject → relationship → remembered value")
                if (connections.isEmpty()) MemoryEmpty("Connections will appear as the graph receives source-linked facts.")
                else connections.forEach { (subject, edges) ->
                    GlassPanel(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Text(subject, style = FridayType.Body.copy(fontWeight = FontWeight.SemiBold))
                            edges.forEach { edge ->
                                Spacer(Modifier.height(8.dp))
                                Text(edge.predicate.uppercase(), style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                                Text("→  ${edge.`object`}", style = FridayType.Body)
                                if (edge.source.isNotBlank()) Text("Source · ${edge.source}", style = FridayType.Technical.copy(color = FridayColors.TextSecondary))
                            }
                        }
                    }
                }
            }
            2 -> {
                MemorySectionHeading("SOURCE INDEX", "Where remembered information came from")
                if (sources.isEmpty()) MemoryEmpty("No source-attributed graph facts are available yet.")
                else sources.forEach { (source, sourceFacts) ->
                    GlassPanel(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(source, style = FridayType.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                                Text("${sourceFacts.size}", style = FridayType.Technical)
                            }
                            sourceFacts.take(5).forEach { node ->
                                Spacer(Modifier.height(7.dp))
                                Text("${node.subject} · ${node.predicate}", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                                Text(node.`object`, style = FridayType.Body)
                            }
                            if (sourceFacts.size > 5) Text("+ ${sourceFacts.size - 5} more", style = FridayType.Technical.copy(color = FridayColors.TextSecondary))
                        }
                    }
                }
            }
            else -> {
                MemorySectionHeading("TENSIONS & CHANGES", "Stored supersession history, not an automatic conflict verdict")
                if (tensions.isEmpty()) MemoryEmpty("No supersession chains are available for this search.")
                else tensions.forEach { chain ->
                    val previous = chain.dropLast(1).lastOrNull()
                    val current = chain.last()
                    GlassPanel(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Text("${current.subject} · ${current.predicate}", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                            if (previous != null) {
                                Text(previous.`object`, style = FridayType.Body.copy(color = FridayColors.TextSecondary))
                                Text("↓", style = FridayType.Display)
                            }
                            Text(current.`object`, style = FridayType.Body.copy(fontWeight = FontWeight.SemiBold))
                            Text("Updated ${Instant.ofEpochMilli(current.validFrom)}", style = FridayType.Technical.copy(color = FridayColors.TextSecondary))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "This screen reads Friday's existing SQLite memory graph and experience records. It does not create a second memory store or infer facts that are not present.",
            style = FridayType.Technical.copy(color = FridayColors.TextSecondary)
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun MemoryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(end = 8.dp)) {
        Text(label, style = FridayType.Label.copy(color = FridayColors.TextSecondary))
        Text(value, style = FridayType.Display.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun MemorySectionHeading(title: String, subtitle: String) {
    Spacer(Modifier.height(18.dp))
    Text(title, style = FridayType.Label)
    Text(subtitle, style = FridayType.Technical.copy(color = FridayColors.TextSecondary))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun MemoryEmpty(message: String) {
    Text(message, style = FridayType.Body.copy(color = FridayColors.TextSecondary), modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun MemoryFactCard(node: MemoryNode) {
    GlassPanel(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(node.subject, style = FridayType.Body.copy(fontWeight = FontWeight.SemiBold))
            Text(node.predicate.uppercase(), style = FridayType.Label.copy(color = FridayColors.TextSecondary))
            Spacer(Modifier.height(4.dp))
            Text(node.`object`, style = FridayType.Body)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(node.source.ifBlank { "Source not recorded" }, style = FridayType.Technical.copy(color = FridayColors.TextSecondary), modifier = Modifier.weight(1f))
                Text(Instant.ofEpochMilli(node.validFrom).toString().take(10), style = FridayType.Technical.copy(color = FridayColors.TextSecondary))
            }
        }
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
