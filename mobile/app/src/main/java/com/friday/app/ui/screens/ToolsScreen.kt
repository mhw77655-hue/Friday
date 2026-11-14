package com.friday.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.friday.app.ui.components.CapabilityRow
import com.friday.app.ui.components.rememberCapabilitySnapshot
import com.friday.app.ui.theme.FridayColors
import com.friday.app.ui.theme.FridayType

@Composable
fun ToolsScreen() {
    val capsSnapshot by rememberCapabilitySnapshot()
    val entries = capsSnapshot?.toList().orEmpty().sortedBy { it.first }
    val active = entries.filter { it.second }
    val cannotYet = entries.filter { !it.second }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FridayColors.SurfaceBase)
            .padding(16.dp)
    ) {
        LazyColumn {
            item { SectionHeader("ACTIVE") }
            items(active) { (name, _) -> CapabilityRow(name = name, active = true) }

            item { SectionHeader("CANNOT YET") }
            items(cannotYet) { (name, _) -> CapabilityRow(name = name, active = false) }

            item { SectionHeader("EXPERIMENTAL") }
            item {
                Text(
                    text = "None yet",
                    style = FridayType.Technical,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = FridayType.Label,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}
