package ai.friday.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.friday.assistant.FridayEngine
import ai.friday.assistant.bootstrap.plan.ObjectiveExecutionLedgerStore
import ai.friday.assistant.cognitive.CognitiveState
import ai.friday.assistant.cognitive.Goal
import ai.friday.assistant.cognitive.GoalPriority
import ai.friday.assistant.github.reporting.ReaInvestigationCoordinator
import ai.friday.assistant.memory.MemoryNode
import ai.friday.assistant.ui.EngineAccess
import ai.friday.assistant.ui.components.GlassPanel
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import org.json.JSONObject

/**
 * Honest empty states — only real backends are presented as live capabilities.
 */
@Composable
fun MissionsScreen() {
    val engine = FridayEngine.cognitiveEngine
    val stateFlow = engine?.cognitiveState ?: flowOf(CognitiveState())
    val cognitiveState by stateFlow.collectAsStateWithLifecycle(initialValue = CognitiveState())
    val appContext = LocalContext.current.applicationContext
    var refreshKey by remember { mutableIntStateOf(0) }
    val ledger = remember(appContext, refreshKey) {
        runCatching { ObjectiveExecutionLedgerStore(appContext).snapshot() }.getOrNull()
    }
    val objectives = remember(ledger) {
        val array = ledger?.optJSONArray("objectives")
        (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
    }
    val phases = remember(ledger) {
        val array = ledger?.optJSONArray("phases")
        (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
    }
    val tasks = remember(ledger) {
        val array = ledger?.optJSONArray("tasks")
        (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
    }
    var objectiveDraft by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FridayColors.SurfaceBase)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text("BUILD", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
        Text("Objectives", style = FridayType.Display)
        Text(
            "What Friday is currently working toward, from her live cognitive state.",
            style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
            modifier = Modifier.padding(top = 5.dp)
        )

        Spacer(Modifier.height(18.dp))
        val goal = cognitiveState.currentGoal
        if (goal == null) {
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("NO ACTIVE OBJECTIVE", style = FridayType.Label)
                    Text(
                        "Friday has no active goal in her cognitive state yet. Create an objective below to give her a durable direction.",
                        style = FridayType.Body.copy(color = FridayColors.TextSecondary),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        } else {
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("CURRENT OBJECTIVE", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                    Text(goal.description, style = FridayType.Display, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        goal.status.name.replace('_', ' ') + "  ·  " + goal.priority.name,
                        style = FridayType.Technical.copy(color = FridayColors.CoreIdle),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (goal.successCriteria.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("SUCCESS CRITERIA", style = FridayType.Label)
                        goal.successCriteria.forEach { criterion ->
                            Text("•  " + criterion, style = FridayType.Body, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("SUBGOALS  ·  " + cognitiveState.activeSubgoals.size, style = FridayType.Label)
            if (cognitiveState.activeSubgoals.isEmpty()) {
                Text(
                    "No decomposed subgoals are recorded for this objective.",
                    style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                cognitiveState.activeSubgoals.forEach { subgoal ->
                    GlassPanel(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(subgoal.description, style = FridayType.Body)
                            Text(
                                subgoal.status.name.replace('_', ' ') + "  ·  effort " + subgoal.estimatedEffort,
                                style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CANONICAL DELIVERY PLAN", style = FridayType.Label)
                    TextButton(onClick = { refreshKey++ }) { Text("REFRESH") }
                }
                if (ledger == null) {
                    Text(
                        "The canonical plan has not been imported into this installation yet. Start BUILD / REPAIR FRIDAY to run the plan reconciliation lane.",
                        style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Text(
                        objectives.size.toString() + " objectives  ·  " +
                            phases.size.toString() + " phases  ·  " +
                            tasks.size.toString() + " work packets",
                        style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        ledger.optString("execution_state", "UNKNOWN").replace('_', ' '),
                        style = FridayType.Technical.copy(color = FridayColors.CoreIdle),
                        modifier = Modifier.padding(top = 5.dp)
                    )
                    if (objectives.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        objectives.forEach { objective ->
                            Text(
                                objective.optString("id") + " · " + objective.optString("title"),
                                style = FridayType.Body,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                            Text(
                                objective.optString("status").replace('_', ' '),
                                style = FridayType.Technical.copy(color = FridayColors.TextSecondary)
                            )
                        }
                    }
                    if (phases.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("OBJECTIVE A PHASES", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                        phases.forEach { phase ->
                            Text(
                                "Phase " + phase.optInt("id") + " · " + phase.optString("title") +
                                    " · " + phase.optString("status").replace('_', ' '),
                                style = FridayType.Technical,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                    }
                    if (tasks.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("LATEST WORK PACKETS", style = FridayType.Label.copy(color = FridayColors.TextSecondary))
                        tasks.takeLast(5).asReversed().forEach { task ->
                            Text(
                                task.optString("goal", task.optString("task_id")),
                                style = FridayType.Body,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Text(
                                task.optString("status").replace('_', ' ') + " · " +
                                    task.optString("executor", "UNASSIGNED").replace('_', ' '),
                                style = FridayType.Technical.copy(color = FridayColors.TextSecondary)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("SET A DIRECTION", style = FridayType.Label)
        OutlinedTextField(
            value = objectiveDraft,
            onValueChange = { objectiveDraft = it; createError = null },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("What should Friday work toward?") },
            minLines = 2,
            maxLines = 4
        )
        createError?.let {
            Text(it, style = FridayType.Technical.copy(color = FridayColors.CoreOffline), modifier = Modifier.padding(top = 6.dp))
        }
        Button(
            onClick = {
                val description = objectiveDraft.trim()
                if (description.isBlank()) {
                    createError = "Enter an objective first."
                } else if (engine == null) {
                    createError = "Friday's cognitive engine is not ready yet."
                } else if (cognitiveState.currentGoal != null) {
                    createError = "Friday already has an objective. It has been preserved; this version does not overwrite it or queue a second one."
                } else {
                    runCatching {
                        engine.setGoal(
                            Goal(
                                id = UUID.randomUUID().toString(),
                                description = description.take(2_000),
                                priority = GoalPriority.NORMAL,
                                successCriteria = emptyList()
                            )
                        )
                    }.onSuccess {
                        objectiveDraft = ""
                        createError = null
                    }.onFailure {
                        createError = it.message ?: "Friday could not save this objective."
                    }
                }
            },
            enabled = engine != null && cognitiveState.currentGoal == null && objectiveDraft.isNotBlank(),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Text("CREATE OBJECTIVE")
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Objectives are stored by Friday's existing continuity system. This screen does not claim a goal is being executed merely because it is recorded.",
            style = FridayType.Technical.copy(color = FridayColors.TextSecondary)
        )
    }
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
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val reaCoordinator = remember(context) { ReaInvestigationCoordinator(context) }
    val reaScope = rememberCoroutineScope()
    var reaSnapshot by remember { mutableStateOf(reaCoordinator.current()) }
    var reaBusy by remember { mutableStateOf(false) }
    var reaError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reaCoordinator) {
        reaSnapshot = withContext(Dispatchers.IO) {
            reaCoordinator.refresh() ?: reaCoordinator.current()
        }
    }
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
        MemorySectionHeading(
            "REFERENCE STUDY",
            "Friday can use REA to inspect the Neomas memory experience and preserve the findings for adaptation."
        )
        GlassPanel(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(15.dp)) {
                val activeRea = reaSnapshot != null && (
                    reaSnapshot!!.status in setOf("dispatched", "queued", "in_progress") ||
                        (reaSnapshot!!.workflowKind == "neomas" &&
                            reaSnapshot!!.status == "completed" &&
                            reaSnapshot!!.conclusion == "success" &&
                            !reaSnapshot!!.packetAvailable)
                    )
                Text(
                    when {
                        reaBusy -> "REA is working…"
                        reaSnapshot?.packetAvailable == true -> "Evidence packet saved in Friday's private inbox."
                        activeRea -> "Investigation status · " + reaSnapshot!!.status
                        reaSnapshot?.conclusion != null -> "Last investigation · " + (reaSnapshot?.conclusion ?: "unknown")
                        else -> "No Neomas reference study has been run from this app yet."
                    },
                    style = FridayType.Body
                )
                reaSnapshot?.packetError?.let { error ->
                    Spacer(Modifier.height(6.dp))
                    Text("Packet ingestion · " + error, style = FridayType.Technical.copy(color = FridayColors.TextSecondary))
                }
                reaError?.let { error ->
                    Spacer(Modifier.height(6.dp))
                    Text(error, style = FridayType.Technical.copy(color = FridayColors.TextSecondary))
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    enabled = !reaBusy,
                    onClick = {
                        reaScope.launch {
                            reaBusy = true
                            reaError = null
                            try {
                                val before = reaCoordinator.current()
                                val shouldRefresh = before != null && (
                                    before.status in setOf("dispatched", "queued", "in_progress") ||
                                        (before.workflowKind == "neomas" &&
                                            before.status == "completed" &&
                                            before.conclusion == "success" &&
                                            !before.packetAvailable)
                                    )
                                reaSnapshot = withContext(Dispatchers.IO) {
                                    if (shouldRefresh) reaCoordinator.refresh() else reaCoordinator.startNeomas()
                                }
                            } catch (failure: Exception) {
                                reaError = failure.message ?: "Friday could not start the REA reference study."
                            } finally {
                                reaBusy = false
                            }
                        }
                    }
                ) {
                    Text(
                        when {
                            reaBusy -> "Working…"
                            activeRea -> "Refresh investigation"
                            reaSnapshot?.packetAvailable == true -> "Run REA study again"
                            else -> "Study Neomas with REA"
                        }
                    )
                }
                reaSnapshot?.url?.let { url ->
                    Text(
                        "Open GitHub workflow",
                        style = FridayType.Technical.copy(color = FridayColors.TextSecondary),
                        modifier = Modifier.clickable { uriHandler.openUri(url) }.padding(top = 8.dp)
                    )
                }
                if (reaSnapshot?.packetAvailable == true) {
                    val candidateCount = runCatching {
                        JSONObject(reaSnapshot?.summary.orEmpty()).optInt("candidate_count")
                    }.getOrDefault(0)
                    Text(
                        "Friday adaptation plan saved · " + candidateCount + " proposals pending review.",
                        style = FridayType.Body
                    )
                    Text(
                        "The packet is evidence for Friday's adaptation process. It is not executed or promoted automatically.",
                        style = FridayType.Technical.copy(color = FridayColors.TextSecondary)
                    )
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
