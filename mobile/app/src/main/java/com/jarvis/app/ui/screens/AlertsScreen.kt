package com.jarvis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.JarvisEngine
import com.jarvis.app.alerts.AlertEntry
import com.jarvis.app.alerts.LocalAlertStore
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.flow.map

/**
 * Alerts Screen — displays system alerts and warnings.
 * Wired to the LocalAlertStore backend.
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
            .padding(16.dp)
    ) {
        Text(
            text = "ALERTS",
            style = JarvisType.Label
        )

        Text(
            text = "${alerts.size} alert(s)",
            style = JarvisType.Body.copy(fontSize = 14.sp),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn {
            items(alerts) { alert ->
                AlertRow(alert = alert)
            }
        }
    }
}

@Composable
private fun AlertRow(alert: AlertEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisColors.SurfacePanel)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = alert.title,
                    style = JarvisType.Body.copy(fontSize = 14.sp)
                )
                Text(
                    text = alert.level,
                    style = JarvisType.Technical.copy(fontSize = 10.sp)
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = alert.message,
                style = JarvisType.Body.copy(fontSize = 12.sp, color = JarvisColors.TextSecondary)
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Source: ${alert.source}",
                    style = JarvisType.Technical.copy(fontSize = 10.sp, color = JarvisColors.TextSecondary)
                )
                Text(
                    text = alert.timestamp,
                    style = JarvisType.Technical.copy(fontSize = 10.sp, color = JarvisColors.TextSecondary)
                )
            }
        }
    }
}

/** Engine-held process-scoped singleton (nav graph injection). */
fun getAlertStore(): LocalAlertStore = JarvisEngine.alertStore
    ?: throw IllegalStateException("JarvisEngine.init() must run before getAlertStore()")