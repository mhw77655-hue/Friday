package com.jarvis.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jarvis.app.modelsim.DeviceSnapshot
import com.jarvis.app.modelsim.FitVerdict
import com.jarvis.app.modelsim.ModelFreeDeviceSimulator
import com.jarvis.app.modelsim.ModelHubCatalog
import com.jarvis.app.modelsim.ModelCandidate
import com.jarvis.app.modelsim.ModelSimulation
import com.jarvis.app.modelsim.SyntheticBenchmark
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Model-free screening lab: measures this phone without downloading model weights. */
@Composable
fun ModelSimulationScreen() {
    val context = LocalContext.current
    val simulator = remember(context) { ModelFreeDeviceSimulator(context) }
    val hubCatalog = remember { ModelHubCatalog() }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<DeviceSnapshot?>(null) }
    var benchmark by remember { mutableStateOf<SyntheticBenchmark?>(null) }
    var simulations by remember { mutableStateOf<List<ModelSimulation>>(emptyList()) }
    var contextTokens by remember { mutableIntStateOf(2048) }
    var searchQuery by remember { mutableStateOf("instruct") }
    var discoveredCandidates by remember { mutableStateOf<List<ModelCandidate>>(emptyList()) }
    var discovering by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            running = true
            error = null
            try {
                val measured = withContext(Dispatchers.IO) { simulator.snapshot() }
                simulator.persistSnapshot(measured)
                snapshot = measured
                simulations = simulator.simulate(measured, contextTokens, discoveredCandidates.ifEmpty { ModelFreeDeviceSimulator.CANDIDATES })
            } catch (failure: Exception) {
                error = failure.message ?: "Could not read device resources."
            } finally {
                running = false
            }
        }
    }

    fun discoverModels() {
        scope.launch {
            discovering = true
            error = null
            try {
                val found = hubCatalog.discover(searchQuery)
                discoveredCandidates = found
                snapshot?.let {
                    simulations = simulator.simulate(
                        it,
                        contextTokens,
                        found.ifEmpty { ModelFreeDeviceSimulator.CANDIDATES }
                    )
                }
                if (found.isEmpty()) {
                    error = "No sized GGUF files were returned. Try another search term; unknown file sizes are intentionally not guessed."
                }
            } catch (failure: Exception) {
                error = failure.message ?: "Could not reach the public model catalog."
            } finally {
                discovering = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(JarvisColors.SurfaceBase)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("MODEL-FREE DEVICE LAB", style = JarvisType.Label)
        Text(
            "Measure the phone first. Screen candidate models without downloading or loading their weights.",
            style = JarvisType.Body
        )

        snapshot?.let { device ->
            Card(colors = CardDefaults.cardColors(containerColor = JarvisColors.SurfacePanel)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(device.device, style = JarvisType.Label)
                    Text("SoC: ${device.soc}", style = JarvisType.Body)
                    Text("Android API ${device.androidApi} · ${device.cpuCores} CPU cores · ${device.abi}", style = JarvisType.Technical)
                    Text("RAM available: ${device.availableRamMb} MB / ${device.totalRamMb} MB", style = JarvisType.Body)
                    Text("App memory class: ${device.appMemoryClassMb} MB", style = JarvisType.Technical)
                    Text("Free storage: ${device.freeStorageMb} MB", style = JarvisType.Body)
                    Text("Battery: ${device.batteryPercent}% · Charging: ${device.isCharging}", style = JarvisType.Technical)
                    Text("Thermal status: ${device.thermalStatus}", style = JarvisType.Technical)
                }
            }
        }

        Text("Synthetic hardware benchmark", style = JarvisType.Label)
        Text(
            "Optional short CPU, 16 MB memory-touch and 4 MB storage test. No model is loaded. It does not measure LLM tokens/second.",
            style = JarvisType.Technical
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { refresh() }, enabled = !running) { Text("Refresh device") }
            Button(
                onClick = {
                    scope.launch {
                        running = true
                        error = null
                        try {
                            val measuredBenchmark = simulator.runSyntheticBenchmark()
                            benchmark = measuredBenchmark
                            simulator.persistBenchmark(measuredBenchmark)
                            val measured = withContext(Dispatchers.IO) { simulator.snapshot() }
                            simulator.persistSnapshot(measured)
                            snapshot = measured
                            simulations = simulator.simulate(measured, contextTokens)
                        } catch (failure: Exception) {
                            error = failure.message ?: "Benchmark could not complete."
                        } finally {
                            running = false
                        }
                    }
                },
                enabled = !running && snapshot != null
            ) { Text("Run short test") }
        }
        if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = JarvisColors.TextSecondary, style = JarvisType.Technical) }

        benchmark?.let { result ->
            Card(colors = CardDefaults.cardColors(containerColor = JarvisColors.SurfacePanel)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Measured synthetic workload", style = JarvisType.Label)
                    Text("CPU operations/sec: ${result.cpuOperationsPerSecond}", style = JarvisType.Body)
                    Text("Memory touch: ${"%.1f".format(result.memoryTouchMbPerSecond)} MB/s", style = JarvisType.Body)
                    Text("Storage write/read: ${"%.1f".format(result.storageWriteMbPerSecond)} MB/s", style = JarvisType.Body)
                    Text("App PSS: ${result.processPssBeforeMb} MB → ${result.processPssAfterMb} MB", style = JarvisType.Technical)
                    Text("Test duration: ${result.elapsedMs} ms", style = JarvisType.Technical)
                }
            }
        }

        Text("Candidate context", style = JarvisType.Label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1024, 2048, 4096).forEach { tokens ->
                Button(onClick = {
                    contextTokens = tokens
                    snapshot?.let { simulations = simulator.simulate(it, tokens) }
                }) { Text("${tokens / 1024}K") }
            }
        }

        Text("Discover models from Hugging Face", style = JarvisType.Label)
        Text(
            "Searches public GGUF repositories and reads published file sizes. It does not download weights or run a model. Repository metadata is not an independent quality or license audit.",
            style = JarvisType.Technical
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Model search") },
            singleLine = true
        )
        Button(
            onClick = { discoverModels() },
            enabled = !running && !discovering && searchQuery.isNotBlank()
        ) { Text(if (discovering) "Searching catalog…" else "Discover GGUF models") }
        if (discovering) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            if (discoveredCandidates.isEmpty()) "Showing the built-in screening set until discovery returns results."
            else "Discovered ${discoveredCandidates.size} quantized GGUF file candidates. File size is exact only when Hub metadata provides it.",
            style = JarvisType.Technical
        )

        Text("Model screening", style = JarvisType.Label)
        Text(
            "These are resource estimates based on live RAM, exact listed weight-file size where available, and conservative overhead assumptions. They are not model performance tests.",
            style = JarvisType.Technical
        )
        simulations.forEach { result ->
            val status = when (result.verdict) {
                FitVerdict.LIKELY_FIT -> "LIKELY FIT"
                FitVerdict.CONDITIONAL -> "BORDERLINE"
                FitVerdict.NOT_RECOMMENDED -> "NOT RECOMMENDED"
            }
            Card(colors = CardDefaults.cardColors(containerColor = JarvisColors.SurfacePanel)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(result.candidate.name, style = JarvisType.Label)
                    Text("${result.candidate.parameterLabel} · ${result.candidate.weightMb} MB weights · ${result.candidate.quantization}", style = JarvisType.Technical)
                    Text("${result.candidate.runtime} · ${result.candidate.modalities}", style = JarvisType.Technical)
                    Text(status, style = JarvisType.Label)
                    Text("Estimated working set: ${result.estimatedWorkingSetMb} MB", style = JarvisType.Body)
                    Text("Available after reserve: ${result.availableAfterReserveMb} MB", style = JarvisType.Body)
                    Text(result.explanation, style = JarvisType.Technical)
                    Text(result.candidate.notes, style = JarvisType.Technical)
                }
            }
        }

        Text(
            "The device profile and synthetic results are saved privately on this phone. Discovery reads public catalog metadata only. This lab does not download model weights, infer exact architecture-specific KV cache, or predict tokens/second. Actual model loading and sustained inference are separate, later tests.",
            style = JarvisType.Technical
        )
    }
}
