package ai.friday.assistant.ui.screens

import ai.friday.assistant.aiworkforce.table.FridayTableLog
import android.content.Context
import org.json.JSONObject
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class FridayTableView { ROUNDTABLE, VISION }

@Composable
fun FridayTableScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var events by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var view by remember { mutableStateOf(FridayTableView.ROUNDTABLE) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshKey) {
        loading = true
        events = withContext(Dispatchers.IO) {
            runCatching { FridayTableLog(context.applicationContext).readRecent(250) }
                .getOrDefault(emptyList())
        }
        loading = false
    }

    val visibleEvents = when (view) {
        FridayTableView.ROUNDTABLE -> events
        // Vision keeps Friday's own turn and the guest critique visible while
        // hiding transport/triage bookkeeping. Guest advice remains inspectable.
        FridayTableView.VISION -> events.filter {
            it.optString("type") in setOf("friday_local_turn", "builder_guest_critique", "seat_reply")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("FRIDAY AI TABLE", style = MaterialTheme.typography.labelMedium)
        Text(
            "Friday is the conductor. Guest Seats are advisory voices; their text never authorizes phone actions or protected changes.",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (view == FridayTableView.ROUNDTABLE) {
                Button(modifier = Modifier.weight(1f), onClick = { view = FridayTableView.ROUNDTABLE }) { Text("Roundtable") }
            } else {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = { view = FridayTableView.ROUNDTABLE }) { Text("Roundtable") }
            }
            if (view == FridayTableView.VISION) {
                Button(modifier = Modifier.weight(1f), onClick = { view = FridayTableView.VISION }) { Text("Vision") }
            } else {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = { view = FridayTableView.VISION }) { Text("Vision") }
            }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { refreshKey++ }) { Text("Refresh") }
        }
        Text(
            if (view == FridayTableView.ROUNDTABLE) {
                "All recorded voices and Table events, in arrival order."
            } else {
                "Friday's local voice is foregrounded; guest critiques remain visible for inspection."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            loading -> Text("Loading Friday's local Table transcript…")
            visibleEvents.isEmpty() -> Text(
                "No Table turns have been recorded yet. Friday records a local turn and any permitted guest response when a Builder critique runs."
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(visibleEvents) { _, event ->
                    val type = event.optString("type", "table_event")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(eventTitle(type, event), style = MaterialTheme.typography.titleSmall)
                            event.optString("summary").takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                            event.optString("utterance").takeIf { it.isNotBlank() && it != "null" }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                            val stance = event.optString("stance").takeIf { it.isNotBlank() && it != "null" }
                            val reason = event.optString("stance_reason").takeIf { it.isNotBlank() && it != "null" }
                            if (stance != null) {
                                Text(
                                    "STANCE: " + stance + (reason?.let { " — $it" } ?: ""),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            val provider = event.optString("provider").takeIf { it.isNotBlank() && it != "null" }
                            val model = event.optString("model").takeIf { it.isNotBlank() && it != "null" }
                            if (provider != null || model != null) {
                                Text(
                                    listOfNotNull(provider, model).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val presence = event.optString("presence").takeIf { it.isNotBlank() && it != "null" }
                            val error = event.optString("error").takeIf { it.isNotBlank() && it != "null" }
                            if (presence != null || error != null) {
                                Text(
                                    listOfNotNull(presence, error).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun eventTitle(type: String, event: JSONObject): String = when (type) {
    "friday_local_turn" -> "Friday · local conductor"
    "seat_reply" -> "Guest Seat · " + event.optString("seat_id", "unknown")
    "builder_guest_critique" -> "Guest critique · " + event.optString("seat_id", "unknown")
    "guest_consult_skipped" -> "Friday · guest consultation skipped"
    else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
