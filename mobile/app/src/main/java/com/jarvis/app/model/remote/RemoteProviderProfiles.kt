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
    }

    @Synchronized
    fun remove(id: String) {
        val remaining = all().filterNot { it.id == id }
        prefs.edit().putString(KEY_PROFILES, JSONArray(remaining.map { it.toJson() }).toString()).apply()
        credentials.clear(id)
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

    companion object { private const val KEY_PROFILES = "profiles_json" }
}
