package com.jarvis.app.model.remote

import android.content.Context
import com.jarvis.app.env.ModelProviderType
import com.jarvis.app.model.ModelProvider
import com.jarvis.app.model.config.ProviderConfig
import org.json.JSONArray
import org.json.JSONObject

data class RemoteProviderProfile(
    val id: String,
    val label: String,
    val protocol: RemoteProviderProtocol,
    val endpoint: String,
    val modelId: String,
    val enabled: Boolean = true,
    val allowInsecureLocalHttp: Boolean = false,
    val timeoutMs: Long = 45_000L
) {
    init {
        require(id.matches(Regex("[a-zA-Z0-9_.-]{1,80}")))
        require(label.isNotBlank() && modelId.isNotBlank())
        require(timeoutMs in 1_000L..120_000L)
    }

    fun toJson() = JSONObject()
        .put("id", id).put("label", label).put("protocol", protocol.name)
        .put("endpoint", endpoint).put("modelId", modelId).put("enabled", enabled)
        .put("allowInsecureLocalHttp", allowInsecureLocalHttp).put("timeoutMs", timeoutMs)

    companion object {
        fun fromJson(o: JSONObject) = RemoteProviderProfile(
            id = o.getString("id"),
            label = o.getString("label"),
            protocol = RemoteProviderProtocol.valueOf(o.getString("protocol")),
            endpoint = o.getString("endpoint"),
            modelId = o.getString("modelId"),
            enabled = o.optBoolean("enabled", true),
            allowInsecureLocalHttp = o.optBoolean("allowInsecureLocalHttp", false),
            timeoutMs = o.optLong("timeoutMs", 45_000L)
        )
    }
}

/**
 * App-private provider catalog. Secrets are deliberately stored separately
 * in ProviderCredentialStore and never enter this JSON/preferences record.
 */
class RemoteProviderProfiles(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("friday_remote_provider_profiles", Context.MODE_PRIVATE)
    private val credentials = ProviderCredentialStore(context)

    @Synchronized
    fun all(): List<RemoteProviderProfile> {
        val raw = prefs.getString(KEY_PROFILES, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { i ->
            runCatching { RemoteProviderProfile.fromJson(array.getJSONObject(i)) }.getOrNull()
        }
    }

    @Synchronized
    fun save(profile: RemoteProviderProfile, apiKey: String) {
        val normalizedEndpoint = ProviderEndpointPolicy.validate(
            profile.endpoint, allowInsecureLoopbackHttp = profile.allowInsecureLocalHttp
        )
        val normalized = profile.copy(endpoint = normalizedEndpoint)
        val profiles = all().filterNot { it.id == profile.id } + normalized
        prefs.edit().putString(KEY_PROFILES, JSONArray(profiles.map { it.toJson() }).toString()).apply()
        if (apiKey.isNotBlank()) credentials.save(profile.id, apiKey)
        if (primaryProviderId() == null) prefs.edit().putString(KEY_PRIMARY_PROFILE, profile.id).apply()
    }

    @Synchronized
    fun remove(id: String) {
        val remaining = all().filterNot { it.id == id }
        prefs.edit().putString(KEY_PROFILES, JSONArray(remaining.map { it.toJson() }).toString()).apply()
        credentials.clear(id)
        if (primaryProviderId() == id) {
            val replacement = remaining.firstOrNull { it.enabled }?.id
            prefs.edit().apply {
                if (replacement == null) remove(KEY_PRIMARY_PROFILE) else putString(KEY_PRIMARY_PROFILE, replacement)
            }.apply()
        }
    }

    /** Explicit primary provider used by the live Cloud Assisted environment. */
    fun primaryProviderId(): String? = prefs.getString(KEY_PRIMARY_PROFILE, null)

    @Synchronized
    fun setPrimaryProvider(id: String) {
        require(all().any { it.id == id && it.enabled }) { "Primary provider must be configured and enabled" }
        prefs.edit().putString(KEY_PRIMARY_PROFILE, id).apply()
    }

    fun createPrimaryProvider(): ModelProvider? {
        val entries = createEnabledEntries()
        val selected = primaryProviderId()
        return entries.firstOrNull { it.profile.id == selected }?.provider ?: entries.firstOrNull()?.provider
    }

    data class Entry(val profile: RemoteProviderProfile, val provider: ModelProvider)

    fun createEnabledEntries(): List<Entry> = all()
        .filter { it.enabled }
        .mapNotNull { profile ->
            val secret = credentials.read(profile.id).orEmpty()
            runCatching {
                Entry(profile, OpenAiCompatibleProvider(
                    config = ProviderConfig(
                        modelId = profile.modelId,
                        authToken = null,
                        parameters = mapOf(
                            "endpoint" to profile.endpoint,
                            "allowInsecureHttp" to profile.allowInsecureLocalHttp.toString()
                        ),
                        timeoutMs = profile.timeoutMs,
                        maxRetries = 0
                    ),
                    apiKey = secret,
                    providerType = ModelProviderType.CLOUD,
                    protocol = profile.protocol
                ))
            }.getOrNull()
        }

    fun createEnabledProviders(): List<ModelProvider> = createEnabledEntries().map { it.provider }

    fun configuredIds(): Set<String> = all().map { it.id }.toSet()

    companion object {
        private const val KEY_PROFILES = "profiles_json"
        private const val KEY_PRIMARY_PROFILE = "primary_profile_id"
    }
}


/**
 * A live binding to the owner's selected remote reasoning provider.
 * The profile is resolved for each operation so adding/changing a credential
 * after app startup does not require rebuilding ModelManager or restarting Friday.
 */
class PrimaryRemoteModelProvider(
    private val resolve: () -> ModelProvider?
) : ModelProvider {
    override val providerType = ModelProviderType.CLOUD
    override val config: ProviderConfig? get() = resolve()?.config

    private fun target(): ModelProvider = resolve()
        ?: throw IllegalStateException("No enabled primary remote reasoning provider is configured")

    override suspend fun generate(request: com.jarvis.app.model.GenerateRequest) =
        target().generate(request)

    override fun stream(request: com.jarvis.app.model.GenerateRequest): kotlinx.coroutines.flow.Flow<com.jarvis.app.model.StreamEvent> =
        kotlinx.coroutines.flow.flow {
            val provider = resolve()
            if (provider == null) {
                emit(com.jarvis.app.model.StreamEvent.Error("No enabled primary remote reasoning provider is configured"))
            } else {
                provider.stream(request).collect { emit(it) }
            }
        }

    override suspend fun health(): com.jarvis.app.model.ProviderHealth =
        resolve()?.health() ?: com.jarvis.app.model.ProviderHealth(
            isHealthy = false,
            latencyMs = -1,
            modelLoaded = false,
            currentModel = null,
            error = "No enabled primary remote reasoning provider is configured"
        )

    override suspend fun load(request: com.jarvis.app.model.LoadRequest) =
        resolve()?.load(request) ?: com.jarvis.app.model.LoadResult(
            false, request.modelId, "No enabled primary remote reasoning provider is configured"
        )

    override suspend fun unload(request: com.jarvis.app.model.UnloadRequest) =
        resolve()?.unload(request) ?: com.jarvis.app.model.UnloadResult(
            false, request.modelId, "No enabled primary remote reasoning provider is configured"
        )

    override suspend fun modelInfo() =
        resolve()?.modelInfo() ?: com.jarvis.app.model.ModelInfo(
            id = "unconfigured-cloud",
            name = "No remote provider configured",
            providerType = ModelProviderType.CLOUD,
            contextWindow = 0,
            maxOutputTokens = 0,
            supportsStreaming = false,
            supportsTools = false,
            supportsVision = false
        )

    override suspend fun tokenUsage() =
        resolve()?.tokenUsage() ?: com.jarvis.app.model.TokenUsage(0, 0, 0)

    override fun requestChat(
        messages: List<Pair<String, String>>,
        maxTokens: Int,
        timeoutMs: Long
    ): String? = resolve()?.requestChat(messages, maxTokens, timeoutMs)
}
