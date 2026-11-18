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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.app.ui.components.CapabilityRow
import com.jarvis.app.ui.components.FridayEmptyPanel
import com.jarvis.app.ui.components.FridayMetricTile
import com.jarvis.app.ui.components.FridayPanel
import com.jarvis.app.ui.components.FridayScreenHeader
import com.jarvis.app.ui.components.FridaySectionHeading
import com.jarvis.app.ui.components.rememberCapabilitySnapshot
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType

@Composable
fun ToolsScreen() {
    val capsSnapshot by rememberCapabilitySnapshot()
    val entries = capsSnapshot?.toList().orEmpty().sortedBy { it.first }
    val active = entries.filter { it.second }
    val cannotYet = entries.filter { !it.second }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
    ) {
        FridayScreenHeader(
            eyebrow = "What she can use",
            title = "Capabilities",
            description = "Tools are resources Friday can use. Their presence in a registry is not proof of end-to-end operation."
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FridayMetricTile(
                label = "Enabled",
                value = active.size.toString(),
                accent = JarvisColors.CoreSuccess,
                modifier = Modifier.weight(1f)
            )
            FridayMetricTile(
                label = "Unavailable",
                value = cannotYet.size.toString(),
                accent = JarvisColors.CoreAttention,
                modifier = Modifier.weight(1f)
            )
            FridayMetricTile(
                label = "Recorded",
                value = entries.size.toString(),
                accent = JarvisColors.CoreThinking,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FridaySectionHeading("Available now", "${active.size} enabled")
            }
            if (active.isEmpty()) {
                item {
                    FridayEmptyPanel(
                        title = "No enabled capabilities reported",
                        description = "Friday's current capability registry has not reported an enabled entry."
                    )
                }
            } else {
                items(active, key = { it.first }) { (name, _) ->
                    FridayPanel {
                        CapabilityRow(name = name, active = true)
                    }
                }
            }

            item {
                FridaySectionHeading("Not available", "${cannotYet.size} disabled")
            }
            if (cannotYet.isEmpty()) {
                item {
                    Text(
                        "No disabled entries are currently reported.",
                        style = JarvisType.Technical,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(cannotYet, key = { it.first }) { (name, _) ->
                    FridayPanel {
                        CapabilityRow(name = name, active = false)
                    }
                }
            }

            item {
                FridaySectionHeading("Experimental")
                FridayEmptyPanel(
                    title = "No experimental entries",
                    description = "New capabilities will appear here only after Friday's runtime registers them."
                )
            }
        }
    }
}
