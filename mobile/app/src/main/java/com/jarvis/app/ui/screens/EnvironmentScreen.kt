package com.jarvis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.app.JarvisEngine
import com.jarvis.app.env.EnvironmentProfile
import com.jarvis.app.env.LatencyPreference
import com.jarvis.app.env.LiquidEnvironmentManager
import com.jarvis.app.env.MemoryPolicy
import com.jarvis.app.env.ModelProviderType
import com.jarvis.app.env.ModelSource
import com.jarvis.app.env.OfflineBehavior
import com.jarvis.app.env.PermissionPolicy
import com.jarvis.app.env.SafetyRestrictions
import com.jarvis.app.env.SyncBehavior
import com.jarvis.app.env.ToolCapability
import com.jarvis.app.env.UIBehavior
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.launch

/**
 * EnvironmentScreen - UI for viewing and switching environments.
 *
 * Wired to LiquidEnvironmentManager: activate/register/delete all flow through
 * the manager (the UI never mutates environment config directly). Activating a
 * profile switches the active provider on ModelManager without an app restart.
 */
@Composable
fun EnvironmentScreen(
    environmentManager: LiquidEnvironmentManager,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val activeEnv by environmentManager.activeEnvironment.collectAsStateWithLifecycle()
    val availableEnvs by environmentManager.availableEnvironments.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingEnv by remember { mutableStateOf<EnvironmentProfile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
    ) {
        Text(
            text = "ENVIRONMENTS",
            style = JarvisType.Label,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
        )

        // Active environment card
        activeEnv?.let { env ->
            ActiveEnvironmentCard(environment = env)
        }

        Text(
            text = "Available Environments",
            style = JarvisType.Label,
            modifier = Modifier.padding(16.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableEnvs) { env ->
                EnvironmentCard(
                    environment = env,
                    isActive = activeEnv?.id == env.id,
                    onActivate = { scope.launch { environmentManager.activate(env) } },
                    onEdit = {
                        editingEnv = env
                        showCreateDialog = true
                    },
                    onDelete = { scope.launch { environmentManager.delete(env.id) } }
                )
            }
        }

        Button(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text("New Environment")
        }
    }

    if (showCreateDialog) {
        EnvironmentEditorDialog(
            environment = editingEnv,
            onSave = { profile ->
                scope.launch { environmentManager.register(profile) }
                showCreateDialog = false
                editingEnv = null
            },
            onDismiss = {
                showCreateDialog = false
                editingEnv = null
            }
        )
    }
}

/** Card showing the currently active environment */
@Composable
private fun ActiveEnvironmentCard(environment: EnvironmentProfile) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisColors.SurfacePanel),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProviderDot(environment.provider, 10.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = environment.label,
                            style = JarvisType.Body.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Text(
                        text = environment.id,
                        style = JarvisType.Technical.copy(fontSize = 11.sp, color = JarvisColors.TextSecondary)
                    )
                }
                Text(
                    text = "ACTIVE",
                    style = JarvisType.Technical.copy(fontSize = 10.sp, color = JarvisColors.CoreSuccess)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoChip("Provider", environment.provider.name)
                InfoChip("Memory", environment.memoryPolicy.name)
                InfoChip("Tools", environment.toolsAvailable.size.toString())
                InfoChip("Latency", environment.latencyPreference.name)
            }
        }
    }
}

/** Individual environment card in the list */
@Composable
private fun EnvironmentCard(
    environment: EnvironmentProfile,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) JarvisColors.CoreIdle.copy(alpha = 0.12f) else JarvisColors.SurfacePanel
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = environment.label,
                        style = JarvisType.Body.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProviderDot(environment.provider, 8.dp)
                        Text(
                            text = "${environment.provider.name} • ${environment.memoryPolicy.name} • ${environment.latencyPreference.name}",
                            style = JarvisType.Technical.copy(fontSize = 11.sp, color = JarvisColors.TextSecondary)
                        )
                    }
                }
                if (isActive) {
                    Text(
                        text = "ACTIVE",
                        style = JarvisType.Technical.copy(fontSize = 10.sp, color = JarvisColors.CoreSuccess)
                    )
                }
            }

            if (!environment.isBuiltIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = JarvisColors.TextSecondary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = JarvisColors.CoreWarning)
                    }
                }
            } else if (!isActive) {
                Button(
                    onClick = onActivate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Activate")
                }
            }
        }
    }
}

/** Colored status dot for a provider type */
@Composable
private fun ProviderDot(provider: ModelProviderType, size: androidx.compose.ui.unit.Dp) {
    val color = when (provider) {
        ModelProviderType.LLAMA_CPP -> JarvisColors.CoreThinking
        ModelProviderType.OLLAMA -> JarvisColors.CoreAttention
        ModelProviderType.REMOTE_JARVIS -> JarvisColors.CoreIdle
        ModelProviderType.CLOUD -> JarvisColors.CoreSuccess
        ModelProviderType.HEURISTIC -> JarvisColors.CoreUnknown
        ModelProviderType.NONE -> JarvisColors.CoreOffline
    }
    Box(
        modifier = Modifier
            .size(size)
            .background(color, CircleShape)
    )
}

/** Small info chip */
@Composable
private fun InfoChip(label: String, value: String) {
    Column {
        Text(text = label, style = JarvisType.Technical.copy(fontSize = 9.sp, color = JarvisColors.TextSecondary))
        Text(text = value, style = JarvisType.Technical.copy(fontSize = 11.sp, color = JarvisColors.TextPrimary, fontWeight = FontWeight.Medium))
    }
}

/** Dialog for creating/editing environments */
@Composable
private fun EnvironmentEditorDialog(
    environment: EnvironmentProfile?,
    onSave: (EnvironmentProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(environment?.label ?: "") }
    var provider by remember { mutableStateOf(environment?.provider ?: ModelProviderType.LLAMA_CPP) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (environment != null) "Edit Environment" else "New Environment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Provider", style = JarvisType.Label)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModelProviderType.entries.forEach { p ->
                        FilterChip(
                            selected = provider == p,
                            onClick = { provider = p },
                            label = { Text(p.name, fontSize = 10.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = label.trim().ifBlank { provider.name }
                    val profile = if (environment != null) {
                        environment.copy(
                            label = trimmed,
                            provider = provider,
                            modelSource = defaultSourceFor(provider),
                            updatedAt = System.currentTimeMillis()
                        )
                    } else {
                        buildCustomProfile(trimmed, provider)
                    }
                    onSave(profile)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Build a new custom profile from the editor's fields, with safe defaults. */
private fun buildCustomProfile(label: String, provider: ModelProviderType): EnvironmentProfile {
    val now = System.currentTimeMillis()
    return EnvironmentProfile(
        id = "custom-${now}",
        label = label,
        provider = provider,
        modelSource = defaultSourceFor(provider),
        memoryPolicy = MemoryPolicy.FULL,
        permissionPolicy = PermissionPolicy.RESTRICTED,
        toolsAvailable = ToolCapability.LIMITED,
        syncBehavior = SyncBehavior.OFFLINE_FIRST,
        latencyPreference = LatencyPreference.BALANCED,
        offlineBehavior = OfflineBehavior.DEGRADE,
        uiBehavior = UIBehavior.NORMAL,
        safetyRestrictions = SafetyRestrictions.NONE,
        isBuiltIn = false,
        createdAt = now,
        updatedAt = now
    )
}

private fun defaultSourceFor(provider: ModelProviderType): ModelSource = when (provider) {
    ModelProviderType.LLAMA_CPP -> ModelSource.Local("127.0.0.1:8080")
    ModelProviderType.OLLAMA -> ModelSource.Local("127.0.0.1:11434")
    ModelProviderType.REMOTE_JARVIS, ModelProviderType.CLOUD -> ModelSource.Remote("auto-discover")
    ModelProviderType.HEURISTIC, ModelProviderType.NONE -> ModelSource.None
}

/** Factory function for nav graph injection */
fun getEnvironmentManager(): LiquidEnvironmentManager {
    return JarvisEngine.environmentManager
        ?: throw IllegalStateException("EnvironmentManager not initialized. Call JarvisEngine.init() first.")
}
