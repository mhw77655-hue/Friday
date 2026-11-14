package com.friday.app.ui.screens

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
import com.friday.app.FridayEngine
import com.friday.app.alerts.AlertEntry
import com.friday.app.alerts.LocalAlertStore
import com.friday.app.ui.theme.FridayColors
import com.friday.app.ui.theme.FridayType
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
            .background(FridayColors.SurfaceBase)
            .padding(16.dp)
    ) {
        Text(
            text = "ALERTS",
            style = FridayType.Label
        )

        Text(
            text = "${alerts.size} alert(s)",
            style = FridayType.Body.copy(fontSize = 14.sp),
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
        colors = CardDefaults.cardColors(containerColor = FridayColors.SurfacePanel)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = alert.title,
                    style = FridayType.Body.copy(fontSize = 14.sp)
                )
                Text(
                    text = alert.level,
                    style = FridayType.Technical.copy(fontSize = 10.sp)
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = alert.message,
                style = FridayType.Body.copy(fontSize = 12.sp, color = FridayColors.TextSecondary)
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Source: ${alert.source}",
                    style = FridayType.Technical.copy(fontSize = 10.sp, color = FridayColors.TextSecondary)
                )
                Text(
                    text = alert.timestamp,
                    style = FridayType.Technical.copy(fontSize = 10.sp, color = FridayColors.TextSecondary)
                )
            }
        }
    }
}

/** Engine-held process-scoped singleton (nav graph injection). */
fun getAlertStore(): LocalAlertStore? = com.friday.app.ui.EngineAccess.alertStore().value