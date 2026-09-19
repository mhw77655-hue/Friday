package com.jarvis.app.env

import android.content.Context
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.model.ModelManager
import com.jarvis.app.runtime.RuntimeBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * LiquidEnvironmentManager — authoritative owner of the currently active environment.
 *
 * Responsibilities:
 * - Register, load, persist environments
 * - Validate environments
 * - Activate/deactivate environments
 * - Switch environments without app restart
 * - Expose active environment state
 * - Create isolated runtime context
 * - Coordinate ModelManager, RuntimeBinder, permissions, tools, memory, network, sync, latency, offline, UI, safety
 * - Emit environment lifecycle events
 *
 * The UI must NEVER directly manipulate environment configuration.
 * Uses repository/domain architecture with single source of truth.
 */
class LiquidEnvironmentManager(
    private val context: Context,
    private val fileStorage: FileStorage,
    private val humanCore: HumanCore,
    private val modelManager: ModelManager,
    private val runtimeBinder: RuntimeBinder,
    private val scope: CoroutineScope
) {
    private val repository = EnvironmentRepository(fileStorage)

    // Active environment state
    private val _activeEnvironment = MutableStateFlow<EnvironmentProfile?>(null)
    val activeEnvironment = _activeEnvironment.asStateFlow()

    // All available environments
    private val _availableEnvironments = MutableStateFlow<List<EnvironmentProfile>>(emptyList())
    val availableEnvironments = _availableEnvironments.asStateFlow()

    // Lifecycle events for UI/reactivity
    private val _lifecycleEvents = MutableSharedFlow<EnvironmentLifecycleEvent>(extraBufferCapacity = 10)
    val lifecycleEvents = _lifecycleEvents.asSharedFlow()

    // Current activation job (for cancellation)
    private var activationJob: kotlinx.coroutines.Job? = null

    /** Initialize: load profiles, restore last active, apply it */
    suspend fun initialize() {
        val profiles = repository.loadProfiles()
        _availableEnvironments.value = profiles

        val activeId = repository.loadActiveProfileId()
            ?: profiles.first { it.id == "local-on-device" }.id

        val targetProfile = profiles.find { it.id == activeId }
            ?: profiles.first { it.id == "local-on-device" }

        activate(targetProfile)
    }

    /** Register a new custom profile */
    suspend fun register(profile: EnvironmentProfile): Result<Unit> = try {
        validate(profile)
        val current = _availableEnvironments.value
        val updated = current.filter { it.id != profile.id } + profile
        _availableEnvironments.value = updated
        repository.saveProfiles(updated)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Activate an environment by profile */
    suspend fun activate(profile: EnvironmentProfile): Result<Unit> {
        // Cancel any in-flight activation
        activationJob?.cancel()

        activationJob = scope.launch {
            _lifecycleEvents.emit(EnvironmentLifecycleEvent.Activating(profile.id))

            try {
                // 1. Deactivate current if different
                _activeEnvironment.value?.let { current ->
                    if (current.id != profile.id) {
                        deactivateInternal(current)
                    }
                }

                // 2. Validate profile is available
                val available = _availableEnvironments.value
                val target = available.find { it.id == profile.id }
                    ?: throw IllegalStateException("Profile ${profile.id} not registered")

                // 3. Apply the environment
                val result = applyEnvironment(target)

                if (result.isSuccess) {
                    _activeEnvironment.value = target
                    repository.saveActiveProfileId(target.id)
                    _lifecycleEvents.emit(EnvironmentLifecycleEvent.Active(target))
                } else {
                    throw result.exceptionOrNull() ?: IllegalStateException("Activation failed")
                }
            } catch (e: Exception) {
                _lifecycleEvents.emit(EnvironmentLifecycleEvent.Failed(profile.id, e))
                // Try to restore previous
                _activeEnvironment.value?.let { restore(it) }
            }
        }

        activationJob?.join()
        return Result.success(Unit)
    }

    /** Delete a custom profile (built-ins are immutable) */
    suspend fun delete(profileId: String): Result<Unit> = try {
        val profile = _availableEnvironments.value.find { it.id == profileId }
            ?: return Result.failure(IllegalArgumentException("Profile not found: $profileId"))
        if (profile.isBuiltIn) return Result.failure(IllegalArgumentException("Built-in profiles cannot be deleted"))
        if (_activeEnvironment.value?.id == profileId) {
            // Fall back to the safe built-in before removing the active profile
            activate("safe")
        }
        val updated = _availableEnvironments.value.filterNot { it.id == profileId }
        _availableEnvironments.value = updated
        repository.saveProfiles(updated)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Activate by ID */
    suspend fun activate(profileId: String): Result<Unit> {
        val profile = _availableEnvironments.value.find { it.id == profileId }
            ?: return Result.failure(IllegalArgumentException("Profile not found: $profileId"))
        return activate(profile)
    }

    /** Deactivate current environment */
    suspend fun deactivate() {
        _activeEnvironment.value?.let { deactivateInternal(it) }
        _activeEnvironment.value = null
        _lifecycleEvents.emit(EnvironmentLifecycleEvent.Deactivated)
    }

    /** Switch to next environment in list (for quick cycling) */
    suspend fun cycleNext(): Result<Unit> {
        val current = _activeEnvironment.value
        val available = _availableEnvironments.value
        val currentIndex = available.indexOfFirst { it.id == current?.id }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % available.size else 0
        return activate(available[nextIndex])
    }

    private suspend fun applyEnvironment(profile: EnvironmentProfile): Result<Unit> = try {
        // 1. Switch ModelManager provider
        modelManager.switchProvider(profile.provider, profile.modelSource)

        // 2. Attach RuntimeBinder to appropriate runtime
        when (profile.provider) {
            ModelProviderType.REMOTE_JARVIS -> {
                runtimeBinder.attachToRemote(profile.modelSource)
            }
            ModelProviderType.LLAMA_CPP, ModelProviderType.OLLAMA -> {
                runtimeBinder.attachLocal(profile.modelSource)
            }
            else -> {
                runtimeBinder.detach()
            }
        }

        // 3. Configure HumanCore memory policy
        // humanCore.setMemoryPolicy(profile.memoryPolicy)  // no such API today — conceptual hook; HumanCore's own memory store is unchanged across env switches

        // 4. Apply permission policy (affects DecisionGate)
        // This is a conceptual hook - DecisionGate reads from environment
        // humanCore.setPermissionPolicy(profile.permissionPolicy)

        // 5. Configure available tools
        // humanCore.setAvailableTools(profile.toolsAvailable)

        // 6. Apply sync behavior (affects ObsidianSync)
        // humanCore.setSyncBehavior(profile.syncBehavior)

        // 7. Apply latency preference (affects LatencyLayer)
        // latencyLayer.setLatencyPreference(profile.latencyPreference)

        // 8. Apply offline behavior
        // humanCore.setOfflineBehavior(profile.offlineBehavior)

        // 9. Apply UI behavior (emitted via lifecycle event for UI to react)

        // 10. Apply safety restrictions
        // humanCore.setSafetyRestrictions(profile.safetyRestrictions)

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun deactivateInternal(profile: EnvironmentProfile) {
        // Cleanup: detach runtime, flush pending sync, etc.
        runtimeBinder.detach()
    }

    private fun restore(profile: EnvironmentProfile) {
        scope.launch { applyEnvironment(profile) }
    }

    private fun validate(profile: EnvironmentProfile) {
        require(profile.id.isNotBlank()) { "Profile ID cannot be blank" }
        require(profile.label.isNotBlank()) { "Profile label cannot be blank" }
        if (profile.provider != ModelProviderType.NONE && profile.provider != ModelProviderType.HEURISTIC) {
            require(profile.modelSource is ModelSource.Remote || profile.modelSource is ModelSource.Local) {
                "Model source required for provider ${profile.provider}"
            }
        }
    }
}

/** Environment lifecycle events */
sealed interface EnvironmentLifecycleEvent {
    data class Activating(val profileId: String) : EnvironmentLifecycleEvent
    data class Active(val profile: EnvironmentProfile) : EnvironmentLifecycleEvent
    data class Deactivating(val profileId: String) : EnvironmentLifecycleEvent
    object Deactivated : EnvironmentLifecycleEvent
    data class Failed(val profileId: String, val error: Throwable) : EnvironmentLifecycleEvent
}