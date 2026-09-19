package com.jarvis.app.model

import android.content.Context
import com.jarvis.app.env.EnvironmentProfile
import com.jarvis.app.env.ModelProviderType
import com.jarvis.app.env.ModelSource
import com.jarvis.app.model.adapters.HeuristicAdapter
import com.jarvis.app.model.adapters.LlamaCppAdapter
import com.jarvis.app.model.adapters.OllamaAdapter
import com.jarvis.app.model.adapters.RemoteJarvisAdapter
import com.jarvis.app.model.config.ProviderConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * ModelManager — orchestrates model providers and delegates to the active adapter.
 * Provider-agnostic: HumanCore, LatencyLayer, and UI only talk to ModelManager.
 *
 * Since Stage 04 ModelManager is the SINGLE load/unload authority over the one
 * [ModelBackend], gated by the [ResourceGovernor]. [request] wakes an organ in
 * its tier (degrading down the ladder on DENY/DEGRADE_TO), [release] starts a
 * per-tier cooldown, and the auto-sweep unloads idle on-demand organs. The
 * legacy provider-profile API (load/unload envelopes, switch/configure) is
 * preserved for the Model Manager UI and env switching — the loading envelopes
 * delegate to the tiered path; they are NOT a parallel loading mechanism.
 */
class ModelManager(
    // Currently unused by every adapter (they derive their base URL from the
    // model source at configure time) — nullable so JVM tests can construct
    // the manager without an Android environment.
    private val context: Context?,
    private val scope: CoroutineScope,
    // The ONE model-loading contract. When absent, an AdapterModelBackend
    // bridges the pre-existing provider adapters — ModelManager never owns a
    // second, parallel loading path.
    private val backend: ModelBackend? = null,
    // Admission authority for every non-resident organ wake.
    private val resourceGovernor: ResourceGovernor = ResourceGovernor(),
    // Idle time after release() before the sweep unloads an on-demand organ.
    private val cooldownMs: Long = 60_000L,
    // Injected clock — deterministic cooldown tests advance it by hand.
    private val clock: () -> Long = { System.currentTimeMillis() },
    // Launch the background cooldown sweep on this manager's scope. Tests run
    // sweeps manually (autoSweep = false) to stay deterministic.
    private val autoSweep: Boolean = true
) {
    // Provider adapters
    private val llamaCppAdapter = LlamaCppAdapter(context)
    private val ollamaAdapter = OllamaAdapter(context)
    private val remoteJarvisAdapter = RemoteJarvisAdapter(context)
    private val heuristicAdapter = HeuristicAdapter()

    // All provider profiles known to the manager (one per adapter).
    private val providersByType: Map<ModelProviderType, ModelProvider> = mapOf(
        ModelProviderType.LLAMA_CPP to llamaCppAdapter,
        ModelProviderType.OLLAMA to ollamaAdapter,
        ModelProviderType.REMOTE_JARVIS to remoteJarvisAdapter,
        ModelProviderType.HEURISTIC to heuristicAdapter,
        ModelProviderType.CLOUD to heuristicAdapter, // CloudProviderAdapter is a stub for now
        ModelProviderType.NONE to heuristicAdapter
    )

    // All model loading/unloading in the app flows through this one backend.
    private val modelBackend: ModelBackend = backend ?: AdapterModelBackend(providersByType)

    // ---------------------------------------------------------------------
    // Tiered lifecycle state (Stage 04): one loaded handle per cached tier,
    // a last-used stamp per tier for the cooldown sweep, and a single-flight
    // map so concurrent requests for the same tier share ONE in-flight load.
    // All access is guarded by lifecycleLock — critical sections are tiny and
    // non-suspending, so the sweep and concurrent requests stay race-free.
    // ---------------------------------------------------------------------
    private val cachedByTier = mutableMapOf<ModelTier, ModelHandle>()
    private val lastUsedMs = mutableMapOf<ModelTier, Long>()
    private val inflight = mutableMapOf<ModelTier, CompletableDeferred<ModelHandle>>()
    private val lifecycleLock = Any()

    // Available provider profiles for the Model Manager UI
    private val _availableProviders = MutableStateFlow<List<ModelProvider>>(
        listOf(llamaCppAdapter, ollamaAdapter, remoteJarvisAdapter, heuristicAdapter)
    )
    val availableProviders = _availableProviders.asStateFlow()

    // Active provider state
    private val _activeProvider = MutableStateFlow<ModelProvider>(heuristicAdapter)
    val activeProvider = _activeProvider.asStateFlow()

    // Health aggregation
    private val _providerHealth = MutableStateFlow<ProviderHealth>(ProviderHealth(
        isHealthy = false, latencyMs = -1, modelLoaded = false, currentModel = null
    ))
    val providerHealth = _providerHealth.asStateFlow()

    // Per-provider health (for the Model Manager list), refreshed via refreshHealth()
    private val _providerHealths = MutableStateFlow<Map<ModelProviderType, ProviderHealth>>(emptyMap())
    val providerHealths = _providerHealths.asStateFlow()

    // True while a health sweep is running
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Health check events
    private val _healthEvents = MutableSharedFlow<ProviderHealth>(extraBufferCapacity = 10)
    val healthEvents = _healthEvents.asSharedFlow()

    /** Switch active provider - called by EnvironmentManager on env switch */
    suspend fun switchProvider(
        type: ModelProviderType,
        modelSource: ModelSource,
        config: ProviderConfig = ProviderConfig.Default
    ): Result<Unit> = try {
        val newProvider = when (type) {
            ModelProviderType.LLAMA_CPP -> {
                llamaCppAdapter.configure(modelSource, config)
                llamaCppAdapter
            }
            ModelProviderType.OLLAMA -> {
                ollamaAdapter.configure(modelSource, config)
                ollamaAdapter
            }
            ModelProviderType.REMOTE_JARVIS -> {
                remoteJarvisAdapter.configure(modelSource, config)
                remoteJarvisAdapter
            }
            ModelProviderType.HEURISTIC -> heuristicAdapter
            ModelProviderType.NONE -> heuristicAdapter
            ModelProviderType.CLOUD -> {
                // TODO: CloudProviderAdapter
                heuristicAdapter
            }
        }

        // Health check new provider before switching
        val health = newProvider.health()
        if (!health.isHealthy && type != ModelProviderType.HEURISTIC && type != ModelProviderType.NONE) {
            // Still switch but emit warning health
        }

        _activeProvider.value = newProvider
        _providerHealth.value = health
        _healthEvents.emit(health)

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Activate a provider by type (Model Manager UI) — keeps its last config. */
    suspend fun setActiveProvider(type: ModelProviderType): Result<Unit> {
        val provider = providersByType[type]
            ?: return Result.failure(IllegalArgumentException("Unknown provider: $type"))
        val config = provider.config ?: ProviderConfig.Default
        return switchProvider(type, defaultModelSource(type), config)
    }

    /** Reconfigure a provider without switching to it (Model Manager UI). */
    suspend fun configureProvider(type: ModelProviderType, config: ProviderConfig): Result<Unit> = try {
        when (type) {
            ModelProviderType.LLAMA_CPP -> llamaCppAdapter.configure(defaultModelSource(type), config)
            ModelProviderType.OLLAMA -> ollamaAdapter.configure(defaultModelSource(type), config)
            ModelProviderType.REMOTE_JARVIS -> remoteJarvisAdapter.configure(defaultModelSource(type), config)
            else -> return Result.failure(IllegalArgumentException("Provider $type is not configurable"))
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Load a model on the given provider (Model Manager UI). */
    suspend fun loadModel(type: ModelProviderType, modelId: String): Result<Unit> {
        if (type == ModelProviderType.HEURISTIC || type == ModelProviderType.NONE || type == ModelProviderType.CLOUD) {
            return Result.success(Unit) // nothing to load — heuristic/cloud route locally
        }
        return try {
            request(roleForProvider(type), task = "loadModel", modelId = modelId, providerType = type)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Failed to load model"))
        }
    }

    /** Unload a model from the given provider (Model Manager UI). */
    suspend fun unloadModel(type: ModelProviderType): Result<Unit> {
        // The manager now owns idle-unload: release starts the per-tier
        // cooldown and the sweep unloads from memory. Nothing to unload for
        // heuristic-stub providers — release is a safe no-op there too.
        release(roleForProvider(type))
        return Result.success(Unit)
    }

    /** Fire-and-forget health sweep across every provider profile. */
    fun refreshHealth() {
        scope.launch {
            _isLoading.value = true
            try {
                providersByType.forEach { (type, provider) ->
                    val health = try {
                        withTimeout(8_000) { provider.health() }
                    } catch (e: Exception) {
                        ProviderHealth(isHealthy = false, latencyMs = -1, modelLoaded = false, currentModel = null, error = e.message)
                    }
                    _providerHealths.update { it + (type to health) }
                    if (type == _activeProvider.value.providerType) {
                        _providerHealth.value = health
                        _healthEvents.emit(health)
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun defaultModelSource(type: ModelProviderType): ModelSource = when (type) {
        ModelProviderType.OLLAMA -> ModelSource.Local("127.0.0.1:11434")
        ModelProviderType.REMOTE_JARVIS -> ModelSource.Remote("auto-discover")
        ModelProviderType.CLOUD -> ModelSource.Remote("auto-discover")
        else -> ModelSource.Local("127.0.0.1:8080") // LLAMA_CPP / HEURISTIC / NONE
    }

    /** Generate (non-streaming) - delegates to active provider */
    suspend fun generate(request: GenerateRequest): GenerateResult {
        return _activeProvider.value.generate(request)
    }

    /** Stream generation - delegates to active provider */
    fun stream(request: GenerateRequest) = _activeProvider.value.stream(request)

    /** Health check active provider */
    suspend fun health(): ProviderHealth {
        val health = _activeProvider.value.health()
        _providerHealth.value = health
        _healthEvents.emit(health)
        return health
    }

    /** Load model on active provider */
    suspend fun load(request: LoadRequest): LoadResult {
        val type = _activeProvider.value.providerType
        return try {
            request(roleForProvider(type), task = "load", modelId = request.modelId, providerType = type)
            LoadResult(success = true, modelId = request.modelId)
        } catch (e: Exception) {
            LoadResult(success = false, modelId = request.modelId, error = e.message)
        }
    }

    /** Unload model on active provider */
    suspend fun unload(request: UnloadRequest): UnloadResult {
        release(roleForProvider(_activeProvider.value.providerType))
        return UnloadResult(success = true, modelId = request.modelId)
    }

    // ---------------------------------------------------------------------
    // Stage 04 tiered model-lifecycle authority. request/release/cooldown over
    // the single backend, gated by the resource governor.
    // ---------------------------------------------------------------------

    private fun roleForProvider(type: ModelProviderType): OrganRole = when (type) {
        ModelProviderType.HEURISTIC, ModelProviderType.NONE, ModelProviderType.CLOUD -> OrganRole.RESIDENT
        else -> OrganRole.REASONING
    }

    /**
     * REASONING-TIER-MODEL-GROUND-TRUTH-AND-WIRE: the ON_DEMAND_REASONING tier
     * always wakes the DISTINCT [TIER_MODEL_ID_REASONING] model — never the
     * active provider's resident modelId. The active-provider override applies
     * only to the resident tier (JarvisEngine.init configures OllamaAdapter with
     * OLLAMA_RESIDENT_MODEL = "jarvis-resident:latest"). Loaded handles carry
     * this modelId, and tier-served generation (send through the booked
     * reasoning handle) routes /api/chat to it via GenerateRequest.modelId.
     */
    private fun defaultModelId(tier: ModelTier): String = when (tier) {
        ModelTier.ON_DEMAND_REASONING -> TIER_MODEL_ID_REASONING
        else ->
            _activeProvider.value.config?.modelId?.takeIf { it.isNotBlank() } ?: when (tier) {
                ModelTier.RESIDENT -> "resident"
                ModelTier.VOICE -> "voice"
                ModelTier.VISION -> "vision"
                ModelTier.ON_DEMAND_REASONING -> TIER_MODEL_ID_REASONING
            }
    }

    /**
     * Wake an organ of this model in [role]'s tier and return its loaded
     * handle. This is the ONE loading path in the app — the tiered
     * [wake] result, reduced to the handle.
     */
    suspend fun request(
        role: OrganRole,
        task: String,
        modelId: String? = null,
        providerType: ModelProviderType? = null
    ): ModelHandle = wake(role, task, modelId, providerType).handle

    /**
     * Wake an organ and report which tier actually served it. The decision
     * ladder: an already-cached handle is returned directly; otherwise the
     * [ResourceGovernor] admits the wake ([AdmissionDecision.ALLOW] loads at
     * the requested tier, [AdmissionDecision.DENY]/[AdmissionDecision.DEGRADE_TO]
     * fall down the ladder and retry), and when the serve lands below the
     * requested tier [WakeResult.degraded] is true — the caller serves from
     * [WakeResult.servedTier] instead of throwing or hanging. The resident
     * tier is always allowed.
     */
    suspend fun wake(
        role: OrganRole,
        task: String,
        modelId: String? = null,
        providerType: ModelProviderType? = null
    ): WakeResult {
        var tier = role.tier
        while (true) {
            val cached = synchronized(lifecycleLock) { cachedByTier[tier] }
            if (cached != null) {
                synchronized(lifecycleLock) { lastUsedMs[tier] = clock() }
                return WakeResult(handle = cached, requestedTier = role.tier, servedTier = tier)
            }
            if (tier == ModelTier.RESIDENT) {
                val handle = loadOnce(tier, modelId = modelId ?: defaultModelId(tier), providerType = providerType)
                return WakeResult(handle = handle, requestedTier = role.tier, servedTier = tier)
            }
            when (val decision = resourceGovernor.admit(
                OrganWakeRequest(organRole = role.name, tier = tier, essential = true)
            )) {
                AdmissionDecision.ALLOW -> {
                    val handle = loadOnce(tier, modelId = modelId ?: defaultModelId(tier), providerType = providerType)
                    return WakeResult(handle = handle, requestedTier = role.tier, servedTier = tier)
                }
                AdmissionDecision.DENY ->
                    tier = tier.lowerTier
                        ?: throw IllegalStateException("No lower tier available for ${role.name}")
                is AdmissionDecision.DEGRADE_TO -> tier = decision.lowerTier
            }
        }
    }

    /**
     * Whether [tier] currently holds a loaded model handle (under the
     * lifecycle lock). The resident tier is the never-auto-unloaded anchor;
     * this read is what the AnchorEngine uses to verify the anchor is held
     * and to detect the moment a turn finds it missing.
     */
    fun isTierLoaded(tier: ModelTier): Boolean =
        synchronized(lifecycleLock) { cachedByTier.containsKey(tier) }

    /**
     * Release an organ: stamp [role]'s tier as last-used *now*, starting its
     * cooldown. The cooldown sweep automatically unloads the tier's model once
     * [cooldownMs] pass with no new [request]. The resident tier is never
     * auto-unloaded. Returns immediately; the actual unload is asynchronous.
     */
    fun release(role: OrganRole) {
        val tier = role.tier
        if (tier == ModelTier.RESIDENT) return
        synchronized(lifecycleLock) { lastUsedMs[tier] = clock() }
    }

    /**
     * Unload every on-demand tier whose model has been idle (no [request],
     * no [release] refresh) for at least [cooldownMs]. Resident and in-flight
     * tiers are skipped. Unload failures keep the cache entry so a later sweep
     * retries instead of silently forgetting the handle.
     */
    internal suspend fun runCooldownSweep() {
        val now = clock()
        val expired = synchronized(lifecycleLock) {
            cachedByTier.keys.filter { tier ->
                tier != ModelTier.RESIDENT &&
                    tier !in inflight &&
                    (now - (lastUsedMs[tier] ?: 0L)) >= cooldownMs
            }
        }
        for (tier in expired) {
            val handle = synchronized(lifecycleLock) { cachedByTier.remove(tier) } ?: continue
            synchronized(lifecycleLock) { lastUsedMs.remove(tier) }
            try {
                modelBackend.unloadModel(handle)
            } catch (e: Exception) {
                synchronized(lifecycleLock) { cachedByTier[tier] = handle }
            }
        }
    }

    private fun beginLoad(tier: ModelTier): LoadOutcome = synchronized(lifecycleLock) {
        cachedByTier[tier]?.let { LoadOutcome.Cached(it) }
            ?: inflight[tier]?.let { LoadOutcome.InFlight(it) }
            ?: LoadOutcome.Lead(CompletableDeferred<ModelHandle>()).also { inflight[tier] = it.deferred }
    }

    private suspend fun loadOnce(
        tier: ModelTier,
        modelId: String,
        providerType: ModelProviderType?
    ): ModelHandle {
        when (val outcome = beginLoad(tier)) {
            is LoadOutcome.Cached -> {
                synchronized(lifecycleLock) { lastUsedMs[tier] = clock() }
                return outcome.handle
            }
            is LoadOutcome.InFlight -> return outcome.deferred.await()
            is LoadOutcome.Lead -> {
                val handle = try {
                    modelBackend.loadModel(
                        ModelBackendConfig(
                            modelId = modelId,
                            providerType = providerType ?: _activeProvider.value.providerType
                        )
                    )
                } catch (e: Exception) {
                    synchronized(lifecycleLock) { inflight.remove(tier) }
                    outcome.deferred.completeExceptionally(e)
                    throw e
                }
                synchronized(lifecycleLock) {
                    cachedByTier[tier] = handle
                    lastUsedMs[tier] = clock()
                    inflight.remove(tier)
                }
                outcome.deferred.complete(handle)
                return handle
            }
        }
    }

    private sealed interface LoadOutcome {
        data class Cached(val handle: ModelHandle) : LoadOutcome
        data class InFlight(val deferred: CompletableDeferred<ModelHandle>) : LoadOutcome
        data class Lead(val deferred: CompletableDeferred<ModelHandle>) : LoadOutcome
    }

    // Start the background cooldown sweep (unless tests inject a manual clock).
    init {
        if (autoSweep) {
            scope.launch {
                while (true) {
                    delay(cooldownMs)
                    try {
                        runCooldownSweep()
                    } catch (e: Exception) {
                        // Sweep is best-effort; keep the loop alive for the next pass.
                    }
                }
            }
        }
    }

    /** Get model info from active provider */
    suspend fun modelInfo(): ModelInfo {
        return _activeProvider.value.modelInfo()
    }

    /** Get token usage from active provider */
    suspend fun tokenUsage(): TokenUsage {
        return _activeProvider.value.tokenUsage()
    }

    // ---------------------------------------------------------------------
    // Conversation authority surface. ModelManager is the SOLE model
    // authority for the whole organism: the latency layer, the Human Core
    // model port, the orb binding and the warmup engine all read these
    // flows instead of the retired JarvisBrainBridge.
    //
    // The status vocabulary is preserved exactly so the existing classifiers
    // keep working: BridgeStatusClassifier (SENDING/OK/ERROR), WarmupEngine
    // isFailure, SystemScreen's status dot.
    // ---------------------------------------------------------------------
    private val _status = MutableStateFlow("idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _lastReply = MutableStateFlow("")
    val lastReply: StateFlow<String> = _lastReply.asStateFlow()

    /**
     * REASONING-TIER-MODEL-GROUND-TRUTH-AND-WIRE: turn-scoped booking of the
     * model that must answer the CURRENT turn's send(). When the CognitiveEngine
     * FEP gate admitted the ON_DEMAND_REASONING tier (non-degraded), it books
     * the woken reasoning handle here; the next [send] consumes the booking
     * synchronously ([AtomicReference.getAndSet]) and generates from that
     * handle (backend.generate -> its distinct modelId) instead of the active
     * resident provider. Because send() consumes at call time and the turn
     * teardown clears the booking, the booking never spans turns or leaks into
     * a non-send generation path.
     */
    private val turnServeHandle = java.util.concurrent.atomic.AtomicReference<ModelHandle?>(null)

    /** Book [handle] as the serving model for the current turn's send(). */
    fun noteTurnServe(handle: ModelHandle) {
        turnServeHandle.set(handle)
    }

    /** Release the per-turn booking — send() already consumes it at call time. */
    internal fun clearTurnServe() {
        turnServeHandle.set(null)
    }

    /** Fire-and-forget conversation send against the active provider. */
    fun send(message: String) {
        val booked = turnServeHandle.getAndSet(null)?.takeIf { modelBackend.isLoaded(it) }
        scope.launch {
            attemptSend(message, attempt = 0, tierHandle = booked)
        }
    }

    private suspend fun attemptSend(message: String, attempt: Int, tierHandle: ModelHandle? = null) {
        _status.value =
            if (attempt == 0) "sending (local)" else "retrying local (attempt ${attempt + 1})"
        try {
            val result = if (tierHandle != null) {
                val content = modelBackend.generate(tierHandle, message)
                if (content.isNotBlank()) {
                    GenerateResult(content = content, finishReason = FinishReason.STOP)
                } else {
                    GenerateResult("", finishReason = FinishReason.ERROR)
                }
            } else {
                generate(
                    GenerateRequest(
                        messages = listOf(Message(MessageRole.USER, message)),
                        maxTokens = 200
                    )
                )
            }
            val content = result.content
            if (content.isNotBlank() && result.finishReason != FinishReason.ERROR) {
                _status.value = "ok (local)"
                _lastReply.value = content
            } else {
                if (attempt < MAX_RETRIES) {
                    delay(RETRY_DELAYS_MS.getOrElse(attempt) { RETRY_DELAYS_MS.last() })
                    attemptSend(message, attempt + 1, tierHandle)
                } else {
                    _status.value = "local brain unreachable after ${attempt + 1} attempts"
                    _lastReply.value = ""
                }
            }
        } catch (e: Exception) {
            if (attempt < MAX_RETRIES) {
                delay(RETRY_DELAYS_MS.getOrElse(attempt) { RETRY_DELAYS_MS.last() })
                attemptSend(message, attempt + 1, tierHandle)
            } else {
                _status.value =
                    "local brain unreachable after ${attempt + 1} attempts (${e.message})"
                _lastReply.value = ""
            }
        }
    }

    /**
     * Synchronous, short-timeout chat for the Human Core model port. Off-main
     * thread only; bounded by [timeoutMs]. Returns null when the active
     * provider cannot serve a short chat (heuristic/none, or unreachable) —
     * callers MUST fall back (§0.15 model port contract).
     */
    fun requestChat(
        messages: List<Pair<String, String>>,
        maxTokens: Int = 120,
        timeoutMs: Long = 5_000L
    ): String? = _activeProvider.value.requestChat(messages, maxTokens, timeoutMs)

    /**
     * Synchronous generation with memory context for the BodyCoordinator.
     * Includes retrieved memory items in the prompt for context-aware responses.
     */
    fun sendWithContext(message: String, memoryItems: List<com.jarvis.app.body.MemoryItem>): String {
        val contextText = if (memoryItems.isNotEmpty()) {
            "\n\n[Context from memory]:\n" + memoryItems.map { "- ${it.content}" }.joinToString("\n")
        } else {
            ""
        }
        val fullMessage = message + contextText

        // Use the synchronous requestChat path with a longer timeout
        return requestChat(
            messages = listOf(Pair("user", fullMessage)),
            maxTokens = 200,
            timeoutMs = 10_000L
        ) ?: "I'm having trouble thinking right now. Could you try again?"
    }

    internal companion object {
        /**
         * REASONING-TIER-MODEL-GROUND-TRUTH-AND-WIRE: the ON_DEMAND_REASONING
         * tier always wakes the DISTINCT jarvis-reasoning model — never the
         * active provider's resident modelId. The active-provider override
         * applies only to the resident tier (JarvisEngine.init configures
         * OllamaAdapter with OLLAMA_RESIDENT_MODEL = "jarvis-resident:latest").
         * Loaded handles carry this modelId, and tier-served generation (send
         * through the booked reasoning handle) routes /api/chat to it via
         * GenerateRequest.modelId.
         */
        internal const val TIER_MODEL_ID_REASONING = "jarvis-reasoning:latest"

        const val MAX_RETRIES = 2
        val RETRY_DELAYS_MS = longArrayOf(1000L, 2000L)
    }

    /** Start periodic health checks */
    fun startHealthChecks(intervalMs: Long = 30_000) {
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(intervalMs)
                try {
                    health()
                } catch (e: Exception) {
                    // Health check failed - provider likely down
                    _providerHealth.value = ProviderHealth(
                        isHealthy = false,
                        latencyMs = -1,
                        modelLoaded = false,
                        currentModel = null,
                        error = e.message
                    )
                }
            }
        }
    }
}