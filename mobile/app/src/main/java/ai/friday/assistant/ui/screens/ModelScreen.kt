package ai.friday.assistant.ui.screens

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalContext
import ai.friday.assistant.model.storage.OfflineModelCatalog
import ai.friday.assistant.model.storage.OfflineModelDownloader
import kotlinx.coroutines.Job
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.friday.assistant.FridayEngine
import ai.friday.assistant.env.ModelProviderType
import ai.friday.assistant.model.ModelManager
import ai.friday.assistant.model.ModelProvider
import ai.friday.assistant.model.OrganRole
import ai.friday.assistant.model.ProviderHealth
import ai.friday.assistant.model.config.ProviderConfig
import ai.friday.assistant.ui.theme.FridayColors
import ai.friday.assistant.ui.theme.FridayType
import kotlinx.coroutines.launch

/**
 * ModelScreen - UI for managing model providers.
 *
 * Every action flows through ModelManager — the screen never talks to an
 * adapter or runtime directly. Provider profiles come from ModelManager's
 * registered adapters; per-provider health comes from ModelManager's health
 * sweep; activating a provider calls ModelManager.setActiveProvider, which is
 * the same path the Environment Manager uses on environment switch.
 */
@Composable
fun ModelScreen(
    modelManager: ModelManager,
    onNavigate: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val activeProvider by modelManager.activeProvider.collectAsStateWithLifecycle()
    val availableProviders by modelManager.availableProviders.collectAsStateWithLifecycle()
    val providerHealth by modelManager.providerHealth.collectAsStateWithLifecycle()
    val providerHealths by modelManager.providerHealths.collectAsStateWithLifecycle()
    val isLoading by modelManager.isLoading.collectAsStateWithLifecycle()

    var selectedProvider by remember { mutableStateOf<ModelProvider?>(null) }
    var showConfigDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FridayColors.SurfaceBase)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MODEL MANAGER",
                style = FridayType.Label,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { modelManager.refreshHealth() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh health", tint = FridayColors.TextSecondary)
            }
        }

        OfflineModelLibrary()

        ActiveProviderCard(
            provider = activeProvider,
            health = providerHealth,
            isLoading = isLoading,
            onUnload = { scope.launch { modelManager.release(OrganRole.REASONING) } },
            onConfigure = {
                selectedProvider = activeProvider
                showConfigDialog = true
            }
        )

        Text(
            text = "Available Providers",
            style = FridayType.Label,
            modifier = Modifier.padding(16.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableProviders) { provider ->
                ProviderCard(
                    provider = provider,
                    health = providerHealths[provider.providerType],
                    isActive = activeProvider.providerType == provider.providerType,
                    onActivate = { scope.launch { modelManager.setActiveProvider(provider.providerType) } },
                    onConfigure = {
                        selectedProvider = provider
                        showConfigDialog = true
                    }
                )
            }
        }
    }

    if (showConfigDialog && selectedProvider != null) {
        ProviderConfigDialog(
            provider = selectedProvider!!,
            onSave = { modelId, endpoint, authToken ->
                scope.launch {
                    modelManager.configureProvider(
                        selectedProvider!!.providerType,
                        ProviderConfig(
                            modelId = modelId.ifBlank { null },
                            parameters = if (endpoint.isNotBlank()) mapOf("endpoint" to endpoint) else emptyMap(),
                            authToken = authToken.ifBlank { null }
                        )
                    )
                }
                showConfigDialog = false
                selectedProvider = null
            },
            onDismiss = {
                showConfigDialog = false
                selectedProvider = null
            }
        )
    }
}

/** Card showing the currently active provider */
@Composable
private fun ActiveProviderCard(
    provider: ModelProvider,
    health: ProviderHealth?,
    isLoading: Boolean,
    onUnload: () -> Unit,
    onConfigure: () -> Unit
) {
    val healthColor = when (health?.isHealthy) {
        true -> FridayColors.CoreSuccess
        false -> FridayColors.CoreWarning
        null -> FridayColors.CoreUnknown
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = FridayColors.SurfacePanel),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderDot(provider.providerType)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        text = "Active: ${provider.providerType.name}",
                        style = FridayType.Body.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Model: ${provider.config?.modelId ?: health?.currentModel ?: "none configured"}",
                        style = FridayType.Technical.copy(fontSize = 11.sp, color = FridayColors.TextSecondary)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HealthBadge(
                    label = "Health",
                    isHealthy = health?.isHealthy ?: false,
                    details = health?.error ?: health?.currentModel ?: "Unknown"
                )
                if (health?.latencyMs != null && health.latencyMs >= 0) {
                    HealthBadge(label = "Latency", isHealthy = true, details = "${health.latencyMs}ms")
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = healthColor)
                } else {
                    Button(onClick = onUnload) { Text("Unload") }
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = onConfigure) { Text("Configure") }
                }
            }
        }
    }
}

/** Individual provider card */
@Composable
private fun ProviderCard(
    provider: ModelProvider,
    health: ProviderHealth?,
    isActive: Boolean,
    onActivate: () -> Unit,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) FridayColors.CoreIdle.copy(alpha = 0.12f) else FridayColors.SurfacePanel
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProviderDot(provider.providerType)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(
                            text = provider.providerType.name,
                            style = FridayType.Body.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = provider.config?.modelId ?: health?.currentModel ?: "No model loaded",
                            style = FridayType.Technical.copy(fontSize = 11.sp, color = FridayColors.TextSecondary)
                        )
                    }
                }
                if (isActive) {
                    HealthIndicator(isHealthy = health?.isHealthy ?: false, size = 16.dp)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                capabilitiesFor(provider.providerType).forEach { CapabilityChip(it) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isActive) {
                    Button(onClick = onActivate, modifier = Modifier.weight(1f)) { Text("Activate") }
                }
                Button(onClick = onConfigure, modifier = Modifier.weight(1f)) { Text("Configure") }
            }
        }
    }
}

/** Colored status dot for a provider type */
@Composable
private fun ProviderDot(providerType: ModelProviderType) {
    val color = when (providerType) {
        ModelProviderType.LLAMA_CPP -> FridayColors.CoreThinking
        ModelProviderType.OLLAMA -> FridayColors.CoreAttention
        ModelProviderType.REMOTE_FRIDAY -> FridayColors.CoreIdle
        ModelProviderType.CLOUD -> FridayColors.CoreSuccess
        ModelProviderType.HEURISTIC -> FridayColors.CoreUnknown
        ModelProviderType.NONE -> FridayColors.CoreOffline
    }
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(color, CircleShape)
    )
}

/** Health indicator dot */
@Composable
private fun HealthIndicator(isHealthy: Boolean, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(size)) {
        drawCircle(
            color = if (isHealthy) FridayColors.CoreSuccess else FridayColors.CoreWarning,
            radius = size.value / 2
        )
    }
}

/** Health badge with label and details */
@Composable
private fun HealthBadge(label: String, isHealthy: Boolean, details: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HealthIndicator(isHealthy, 8.dp)
        Column {
            Text(text = label, style = FridayType.Technical.copy(fontSize = 9.sp, color = FridayColors.TextSecondary))
            Text(text = details, style = FridayType.Technical.copy(fontSize = 11.sp, color = FridayColors.TextPrimary, fontWeight = FontWeight.Medium))
        }
    }
}

/** Capability chip */
@Composable
private fun CapabilityChip(text: String) {
    Box(
        modifier = Modifier
            .background(FridayColors.CoreIdle.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, style = FridayType.Technical.copy(fontSize = 10.sp, color = FridayColors.CoreIdle))
    }
}

/** Static capability labels per provider type (avoids a network call per row). */
private fun capabilitiesFor(type: ModelProviderType): List<String> = when (type) {
    ModelProviderType.LLAMA_CPP -> listOf("Streaming", "Tools", "Local")
    ModelProviderType.OLLAMA -> listOf("Streaming", "Local")
    ModelProviderType.REMOTE_FRIDAY -> listOf("Streaming", "Tools", "Remote")
    ModelProviderType.CLOUD -> listOf("Remote", "Managed")
    ModelProviderType.HEURISTIC -> listOf("Offline", "Deterministic")
    ModelProviderType.NONE -> emptyList()
}

/** Dialog for configuring a provider */
@Composable
private fun ProviderConfigDialog(
    provider: ModelProvider,
    onSave: (modelId: String, endpoint: String, authToken: String) -> Unit,
    onDismiss: () -> Unit
) {
    var modelId by remember { mutableStateOf(provider.config?.modelId ?: "") }
    var endpoint by remember { mutableStateOf(provider.config?.parameters?.get("endpoint") ?: "") }
    var authToken by remember { mutableStateOf(provider.config?.authToken ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${provider.providerType.name}") },
        text = {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                TextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Endpoint (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                TextField(
                    value = authToken,
                    onValueChange = { authToken = it },
                    label = { Text("Auth Token (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(modelId, endpoint, authToken) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Factory function for nav graph injection */
fun getModelManager(): ModelManager? {
    return ai.friday.assistant.ui.EngineAccess.modelManager().value
}


/**
 * Offline model installation is owned by Friday: weights are downloaded into
 * app-private storage, not a Termux/Ollama directory or shared Downloads folder.
 */
@Composable
private fun OfflineModelLibrary() {
    val context = LocalContext.current
    val downloader = remember(context) { OfflineModelDownloader(context) }
    val downloadState by downloader.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = FridayColors.SurfacePanel),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("FRIDAY'S OFFLINE MODELS", style = FridayType.Label)
            Text(
                "Model weights are installed inside Friday's private app storage. They are not included in the APK.",
                style = FridayType.Technical.copy(fontSize = 11.sp, color = FridayColors.TextSecondary)
            )
            val installedAssets = remember(downloadState.modelId, downloadState.complete) {
            OfflineModelCatalog.all.filter { asset -> downloader.isInstalled(asset) }
        }
        val noModelsInstalled = installedAssets.isEmpty()
        if (noModelsInstalled) {
            Card(
                colors = CardDefaults.cardColors(containerColor = FridayColors.SurfaceBase),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "NO OFFLINE MODEL INSTALLED",
                        style = FridayType.Label,
                        color = FridayColors.CoreWarning
                    )
                    Text(
                        "Friday currently has no local language-model weights. The APK does not contain a model. Download one below before expecting offline LLM responses.",
                        style = FridayType.Technical
                    )
                    Text(
                        "Storage available to Friday: ${downloader.availableStorageBytes() / (1024L * 1024L)} MB. Downloads are stored privately and can resume after interruption; Friday checks the GGUF header and size, plus SHA-256 where the catalog pins one.",
                        style = FridayType.Technical.copy(fontSize = 11.sp, color = FridayColors.TextSecondary)
                    )
                }
            }
        } else {
            Text(
                "${installedAssets.size} of ${OfflineModelCatalog.all.size} catalog models installed and GGUF-validated.",
                style = FridayType.Technical.copy(fontSize = 11.sp, color = FridayColors.CoreSuccess)
            )
        }
        OfflineModelCatalog.all.forEach { asset ->
                val installed = remember(downloadState.modelId, downloadState.complete, asset.id) {
                    downloader.isInstalled(asset)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(asset.displayName, style = FridayType.Body.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        "${asset.license} · ${if (asset.resident) "Resident candidate" else "On-demand reasoning"}",
                        style = FridayType.Technical.copy(fontSize = 10.sp, color = FridayColors.TextSecondary)
                    )
                    Text(asset.description, style = FridayType.Technical.copy(fontSize = 11.sp))
                    Text(
                        "Minimum download size: ${asset.expectedMinimumBytes / (1024L * 1024L)} MB",
                        style = FridayType.Technical.copy(fontSize = 10.sp, color = FridayColors.TextSecondary)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val active = downloadState.modelId == asset.id && !downloadState.complete
                        when {
                            installed -> Text("Installed", color = FridayColors.CoreSuccess, style = FridayType.Technical)
                            active -> {
                                Text(
                                    downloadState.fraction?.let { "${(it * 100).toInt()}%" } ?: "Downloading",
                                    style = FridayType.Technical
                                )
                                Spacer(Modifier.weight(1f))
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                            else -> {
                                Button(
                                    enabled = downloadJob == null,
                                    onClick = {
                                        downloadError = null
                                        downloadJob = scope.launch {
                                            try {
                                                downloader.download(asset)
                                            } catch (error: Exception) {
                                                downloadError = error.message ?: "Download failed"
                                            } finally {
                                                downloadJob = null
                                            }
                                        }
                                    }
                                ) { Text("Download") }
                            }
                        }
                    }
                    if (downloadState.modelId == asset.id && !downloadState.complete && downloadState.fraction != null) {
                        LinearProgressIndicator(
                            progress = { downloadState.fraction ?: 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            downloadError?.let {
                Text(it, color = FridayColors.CoreWarning, style = FridayType.Technical)
            }
        }
    }
}
