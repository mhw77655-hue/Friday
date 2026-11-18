package com.jarvis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.alerts.AlertEntry
import com.jarvis.app.alerts.LocalAlertStore
import com.jarvis.app.ui.components.FridayEmptyPanel
import com.jarvis.app.ui.components.FridayMetricTile
import com.jarvis.app.ui.components.FridayPanel
import com.jarvis.app.ui.components.FridayScreenHeader
import com.jarvis.app.ui.components.FridaySectionHeading
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.flow.map

/**
 * Alerts screen backed by the real LocalAlertStore.
 * Only undismissed stored alerts are shown; the visual treatment does not
 * infer or create additional alert states.
 */
@Composable
fun AlertsScreen(
    alertStore: LocalAlertStore,
    onNavigate: (String) -> Unit
) {
    val alerts by alertStore.alerts
        .map { it.filter { alert -> !alert.dismissed }.map { AlertEntry.from(it) } }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
    ) {
        FridayScreenHeader(
            eyebrow = "Attention and events",
            title = "Alerts",
            description = "Signals that Friday's local alert store has not yet marked as dismissed."
        )
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FridayMetricTile(
                label = "Undismissed",
                value = alerts.size.toString(),
                accent = if (alerts.isEmpty()) JarvisColors.CoreSuccess else JarvisColors.CoreAttention,
                modifier = Modifier.weight(1f)
            )
            FridayMetricTile(
                label = "Source",
                value = "LOCAL",
                accent = JarvisColors.CoreThinking,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item { FridaySectionHeading("Current alerts", "${alerts.size} stored") }
            if (alerts.isEmpty()) {
                item {
                    FridayEmptyPanel(
                        title = "Nothing needs attention",
                        description = "No undismissed alerts are currently recorded."
                    )
                }
            } else {
                items(alerts) { alert -> AlertRow(alert = alert) }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: AlertEntry) {
    FridayPanel {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(alert.title, style = JarvisType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
            Text(
                alert.level.uppercase(),
                style = JarvisType.Label.copy(color = alertLevelColor(alert.level))
            )
        }
        Text(alert.message, style = JarvisType.Body.copy(color = JarvisColors.TextSecondary))
        Text(
            "SOURCE  ${alert.source}",
            style = JarvisType.Technical.copy(color = JarvisColors.TextMuted)
        )
        Text(
            alert.timestamp,
            style = JarvisType.Technical.copy(color = JarvisColors.TextMuted)
        )
    }
}

private fun alertLevelColor(level: String) = when (level.lowercase()) {
    "critical", "error", "failure" -> JarvisColors.CoreWarning
    "warning", "attention" -> JarvisColors.CoreAttention
    "info", "notice" -> JarvisColors.CoreThinking
    else -> JarvisColors.TextSecondary
}

/** Engine-held process-scoped singleton (nav graph injection). */
fun getAlertStore(): LocalAlertStore? = com.jarvis.app.ui.EngineAccess.alertStore().value
