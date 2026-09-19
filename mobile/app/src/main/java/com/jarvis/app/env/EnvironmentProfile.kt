package com.jarvis.app.env

import org.json.JSONObject

/**
 * Complete definition of an execution environment for Jarvis.
 * An environment controls: which model backend, what memory/tools/sync/offline policies apply.
 * The shared Jarvis identity (HumanCore) persists across environment switches.
 *
 * Note: JSON serialization is manual (kotlinx-serialization is not in the build).
 */
data class EnvironmentProfile(
    val id: String,
    val label: String,
    val provider: ModelProviderType,
    val modelSource: ModelSource,
    val memoryPolicy: MemoryPolicy,
    val permissionPolicy: PermissionPolicy,
    val toolsAvailable: Set<ToolCapability>,
    val syncBehavior: SyncBehavior,
    val latencyPreference: LatencyPreference,
    val offlineBehavior: OfflineBehavior,
    val uiBehavior: UIBehavior,
    val safetyRestrictions: SafetyRestrictions,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("provider", provider.name)
        put("modelSource", encodeModelSource(modelSource))
        put("memoryPolicy", memoryPolicy.name)
        put("permissionPolicy", permissionPolicy.name)
        put("toolsAvailable", org.json.JSONArray(toolsAvailable.map { it.name }))
        put("syncBehavior", syncBehavior.name)
        put("latencyPreference", latencyPreference.name)
        put("offlineBehavior", offlineBehavior.name)
        put("uiBehavior", uiBehavior.name)
        put("safetyRestrictions", safetyRestrictions.name)
        put("isBuiltIn", isBuiltIn)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): EnvironmentProfile = EnvironmentProfile(
            id = o.getString("id"),
            label = o.optString("label"),
            provider = runCatching { ModelProviderType.valueOf(o.getString("provider")) }
                .getOrDefault(ModelProviderType.NONE),
            modelSource = decodeModelSource(o.optJSONObject("modelSource")),
            memoryPolicy = runCatching { MemoryPolicy.valueOf(o.getString("memoryPolicy")) }
                .getOrDefault(MemoryPolicy.NONE),
            permissionPolicy = runCatching { PermissionPolicy.valueOf(o.getString("permissionPolicy")) }
                .getOrDefault(PermissionPolicy.SAFE_MODE),
            toolsAvailable = o.optJSONArray("toolsAvailable")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { ToolCapability.valueOf(arr.getString(i)) }.getOrNull()
                }.toSet()
            } ?: emptySet(),
            syncBehavior = runCatching { SyncBehavior.valueOf(o.getString("syncBehavior")) }
                .getOrDefault(SyncBehavior.OFFLINE_FIRST),
            latencyPreference = runCatching { LatencyPreference.valueOf(o.getString("latencyPreference")) }
                .getOrDefault(LatencyPreference.N_A),
            offlineBehavior = runCatching { OfflineBehavior.valueOf(o.getString("offlineBehavior")) }
                .getOrDefault(OfflineBehavior.READ_ONLY),
            uiBehavior = runCatching { UIBehavior.valueOf(o.getString("uiBehavior")) }
                .getOrDefault(UIBehavior.NORMAL),
            safetyRestrictions = runCatching { SafetyRestrictions.valueOf(o.getString("safetyRestrictions")) }
                .getOrDefault(SafetyRestrictions.MAX),
            isBuiltIn = o.optBoolean("isBuiltIn"),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt")
        )

        fun toJsonList(profiles: List<EnvironmentProfile>): String =
            org.json.JSONArray(profiles.map { it.toJson() }).toString()

        fun fromJsonList(jsonStr: String): List<EnvironmentProfile> {
            val arr = org.json.JSONArray(jsonStr)
            return (0 until arr.length()).mapNotNull { i ->
                runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        }

        private fun encodeModelSource(source: ModelSource): JSONObject = when (source) {
            is ModelSource.Local -> JSONObject().put("type", "local").put("path", source.path)
            is ModelSource.Remote -> JSONObject().put("type", "remote").put("host", source.host)
            ModelSource.None -> JSONObject().put("type", "none")
        }

        private fun decodeModelSource(o: JSONObject?): ModelSource {
            if (o == null) return ModelSource.None
            return when (o.optString("type")) {
                "local" -> ModelSource.Local(path = o.optString("path"))
                "remote" -> ModelSource.Remote(host = o.optString("host"))
                else -> ModelSource.None
            }
        }

        /** The 9 built-in environment profiles */
        val BUILT_IN_PROFILES: List<EnvironmentProfile> = listOf(
            // 1. Local on-device: full local llama.cpp, full access
            EnvironmentProfile(
                id = "local-on-device",
                label = "Local On-Device",
                provider = ModelProviderType.LLAMA_CPP,
                modelSource = ModelSource.Local(path = "127.0.0.1:8080"),
                memoryPolicy = MemoryPolicy.FULL,
                permissionPolicy = PermissionPolicy.FULL,
                toolsAvailable = ToolCapability.ALL,
                syncBehavior = SyncBehavior.REAL_TIME,
                latencyPreference = LatencyPreference.FAST,
                offlineBehavior = OfflineBehavior.DEGRADE,
                uiBehavior = UIBehavior.NORMAL,
                safetyRestrictions = SafetyRestrictions.NONE,
                isBuiltIn = true
            ),
            // 2. Remote model: attach to another Jarvis host
            EnvironmentProfile(
                id = "remote-model",
                label = "Remote Model",
                provider = ModelProviderType.REMOTE_JARVIS,
                modelSource = ModelSource.Remote(host = "auto-discover"),
                memoryPolicy = MemoryPolicy.SESSION_ONLY,
                permissionPolicy = PermissionPolicy.RESTRICTED,
                toolsAvailable = ToolCapability.LIMITED,
                syncBehavior = SyncBehavior.REAL_TIME,
                latencyPreference = LatencyPreference.BALANCED,
                offlineBehavior = OfflineBehavior.FALLBACK,
                uiBehavior = UIBehavior.NORMAL,
                safetyRestrictions = SafetyRestrictions.MODEL_REMOTE,
                isBuiltIn = true
            ),
            // 3. LAN workspace: full sync with local network Jarvis
            EnvironmentProfile(
                id = "lan-workspace",
                label = "LAN Workspace",
                provider = ModelProviderType.REMOTE_JARVIS,
                modelSource = ModelSource.Remote(host = "auto-discover"),
                memoryPolicy = MemoryPolicy.FULL,
                permissionPolicy = PermissionPolicy.FULL,
                toolsAvailable = ToolCapability.ALL,
                syncBehavior = SyncBehavior.REAL_TIME,
                latencyPreference = LatencyPreference.BALANCED,
                offlineBehavior = OfflineBehavior.FALLBACK,
                uiBehavior = UIBehavior.NORMAL,
                safetyRestrictions = SafetyRestrictions.NONE,
                isBuiltIn = true
            ),
            // 4. Cloud-assisted: cloud provider with restrictions
            EnvironmentProfile(
                id = "cloud-assisted",
                label = "Cloud Assisted",
                provider = ModelProviderType.CLOUD,
                modelSource = ModelSource.Remote(host = "cloud-provider"),
                memoryPolicy = MemoryPolicy.SESSION_ONLY,
                permissionPolicy = PermissionPolicy.RESTRICTED,
                toolsAvailable = ToolCapability.LIMITED,
                syncBehavior = SyncBehavior.MANUAL,
                latencyPreference = LatencyPreference.THOROUGH,
                offlineBehavior = OfflineBehavior.READ_ONLY,
                uiBehavior = UIBehavior.NORMAL,
                safetyRestrictions = SafetyRestrictions.CLOUD_ONLY,
                isBuiltIn = true
            ),
            // 5. Offline persona: no model, heuristic fallback only
            EnvironmentProfile(
                id = "offline-persona",
                label = "Offline Persona",
                provider = ModelProviderType.HEURISTIC,
                modelSource = ModelSource.None,
                memoryPolicy = MemoryPolicy.SESSION_ONLY,
                permissionPolicy = PermissionPolicy.SAFE_MODE,
                toolsAvailable = emptySet(),
                syncBehavior = SyncBehavior.OFFLINE_FIRST,
                latencyPreference = LatencyPreference.N_A,
                offlineBehavior = OfflineBehavior.READ_ONLY,
                uiBehavior = UIBehavior.MINIMAL,
                safetyRestrictions = SafetyRestrictions.MAX,
                isBuiltIn = true
            ),
            // 6. Research: thorough, debug UI, all tools
            EnvironmentProfile(
                id = "research",
                label = "Research",
                provider = ModelProviderType.LLAMA_CPP,
                modelSource = ModelSource.Local(path = "127.0.0.1:8080"),
                memoryPolicy = MemoryPolicy.FULL,
                permissionPolicy = PermissionPolicy.FULL,
                toolsAvailable = ToolCapability.ALL,
                syncBehavior = SyncBehavior.REAL_TIME,
                latencyPreference = LatencyPreference.THOROUGH,
                offlineBehavior = OfflineBehavior.DEGRADE,
                uiBehavior = UIBehavior.DEBUG,
                safetyRestrictions = SafetyRestrictions.NONE,
                isBuiltIn = true
            ),
            // 7. Builder: fast, full access, debug UI
            EnvironmentProfile(
                id = "builder",
                label = "Builder",
                provider = ModelProviderType.LLAMA_CPP,
                modelSource = ModelSource.Local(path = "127.0.0.1:8080"),
                memoryPolicy = MemoryPolicy.FULL,
                permissionPolicy = PermissionPolicy.FULL,
                toolsAvailable = ToolCapability.ALL,
                syncBehavior = SyncBehavior.REAL_TIME,
                latencyPreference = LatencyPreference.FAST,
                offlineBehavior = OfflineBehavior.DEGRADE,
                uiBehavior = UIBehavior.DEBUG,
                safetyRestrictions = SafetyRestrictions.NONE,
                isBuiltIn = true
            ),
            // 8. Monitor: read-only, minimal UI
            EnvironmentProfile(
                id = "monitor",
                label = "Monitor",
                provider = ModelProviderType.NONE,
                modelSource = ModelSource.None,
                memoryPolicy = MemoryPolicy.MINIMAL,
                permissionPolicy = PermissionPolicy.RESTRICTED,
                toolsAvailable = ToolCapability.READ_ONLY,
                syncBehavior = SyncBehavior.OFFLINE_FIRST,
                latencyPreference = LatencyPreference.N_A,
                offlineBehavior = OfflineBehavior.READ_ONLY,
                uiBehavior = UIBehavior.MINIMAL,
                safetyRestrictions = SafetyRestrictions.READ_ONLY,
                isBuiltIn = true
            ),
            // 9. Safe: maximum restrictions, heuristic only
            EnvironmentProfile(
                id = "safe",
                label = "Safe Mode",
                provider = ModelProviderType.HEURISTIC,
                modelSource = ModelSource.None,
                memoryPolicy = MemoryPolicy.MINIMAL,
                permissionPolicy = PermissionPolicy.SAFE_MODE,
                toolsAvailable = emptySet(),
                syncBehavior = SyncBehavior.OFFLINE_FIRST,
                latencyPreference = LatencyPreference.N_A,
                offlineBehavior = OfflineBehavior.READ_ONLY,
                uiBehavior = UIBehavior.MINIMAL,
                safetyRestrictions = SafetyRestrictions.MAX,
                isBuiltIn = true
            )
        )
    }
}

/** Which model provider backs this environment */
enum class ModelProviderType {
    LLAMA_CPP,      // llama.cpp / llama-server
    OLLAMA,         // Ollama
    REMOTE_JARVIS,  // Another Jarvis host (OS Portal)
    CLOUD,          // Cloud provider (stub for future)
    HEURISTIC,      // Local heuristic fallback (no model)
    NONE            // No model at all
}

/** Where the model lives */
sealed interface ModelSource {
    data class Local(val path: String) : ModelSource  // e.g. "llama-server:8080" or file path
    data class Remote(val host: String) : ModelSource // hostname:port or "auto-discover"
    object None : ModelSource
}

/** Memory retention policy */
enum class MemoryPolicy {
    FULL,           // Full recall + archival + long-term
    SESSION_ONLY,   // Current session only, no archival
    MINIMAL,        // Minimal context only
    NONE            // No memory
}

/** Permission level for actions */
enum class PermissionPolicy {
    FULL,           // All actions allowed
    RESTRICTED,     // High-risk actions need approval
    SAFE_MODE       // Only safe/read actions
}

/** Available tool capabilities */
enum class ToolCapability {
    READ_FILE,
    LIST_DIR,
    BATTERY_STATUS,
    SHELL_EXEC,
    SCREEN_READ,
    NOTIFICATION_READ,
    MODEL_SWITCH,
    ENV_SWITCH,
    TASK_SUBMIT,
    APPROVAL_DECIDE;

    companion object {
        val ALL = setOf(*values())
        val LIMITED = setOf(READ_FILE, LIST_DIR, BATTERY_STATUS)
        val READ_ONLY = setOf(READ_FILE, LIST_DIR)
    }
}

/** Sync behavior with remote/OS */
enum class SyncBehavior {
    REAL_TIME,      // Immediate sync
    MANUAL,         // User-initiated only
    OFFLINE_FIRST   // Queue locally, sync when online
}

/** Latency vs quality preference */
enum class LatencyPreference {
    FAST,       // Prefer speed (smaller context, heuristic shortcuts)
    BALANCED,   // Balance speed/quality
    THOROUGH,   // Prefer quality (full context, multi-pass)
    N_A         // Not applicable (no model)
}

/** Offline behavior */
enum class OfflineBehavior {
    DEGRADE,      // Use heuristic fallback
    FALLBACK,     // Switch to offline profile
    READ_ONLY     // No mutations, cached reads only
}

/** UI behavior mode */
enum class UIBehavior {
    NORMAL,
    MINIMAL,      // Reduced chrome, focus on content
    DEBUG         // Extra panels, metrics, raw data
}

/** Safety restrictions */
enum class SafetyRestrictions {
    NONE,
    MODEL_REMOTE, // Model is remote - warn on sensitive data
    CLOUD_ONLY,   // Cloud only - explicit consent for each request
    READ_ONLY,    // No write actions
    MAX           // Maximum restrictions
}
