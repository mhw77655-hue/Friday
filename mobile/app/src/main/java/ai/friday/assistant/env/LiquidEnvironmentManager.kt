package ai.friday.assistant.env

import android.content.Context
import ai.friday.assistant.humancore.store.FileStorage
import ai.friday.assistant.approval.ApprovalGate
import ai.friday.assistant.approval.GateConfig
import ai.friday.assistant.model.ModelManager
import ai.friday.assistant.runtime.RuntimeBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
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

        val restored = activate(targetProfile)
        if (restored.isFailure) {
            // A persisted profile can become unsupported after an app update.
            // Never leave startup with no selected environment when the known
            // safe built-in remains available.
            val safeProfile = profiles.firstOrNull { it.id == "safe" }
                ?: throw IllegalStateException(
                    "Saved environment failed and no safe fallback profile exists.",
                    restored.exceptionOrNull()
                )
            if (safeProfile.id == targetProfile.id) {
                throw restored.exceptionOrNull()
                    ?: IllegalStateException("Safe environment activation failed.")
            }
            activate(safeProfile).getOrElse { fallbackFailure ->
                throw IllegalStateException(
                    "Saved environment and safe fallback both failed to activate.",
                    fallbackFailure
                ).also { restored.exceptionOrNull()?.let(it::addSuppressed) }
            }
        }
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
        // Do not let two environment transitions deactivate/restore each other
        // concurrently. Cancellation is not an activation failure and must not
        // be swallowed by the failure handler below.
        activationJob?.cancelAndJoin()

        var activationFailure: Exception? = null
        val previous = _activeEnvironment.value
        val job = scope.launch {
            _lifecycleEvents.emit(EnvironmentLifecycleEvent.Activating(profile.id))
            try {
                // Validate before deactivating the current environment.
                val target = _availableEnvironments.value.find { it.id == profile.id }
                    ?: throw IllegalStateException("Profile ${profile.id} not registered")

                if (previous != null && previous.id != target.id) {
                    deactivateInternal(previous)
                }

                applyEnvironment(target).getOrThrow()
                repository.saveActiveProfileId(target.id)
                _activeEnvironment.value = target
                _lifecycleEvents.emit(EnvironmentLifecycleEvent.Active(target))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                activationFailure = failure
                _lifecycleEvents.emit(EnvironmentLifecycleEvent.Failed(profile.id, failure))

                // Restore synchronously. Never report the previous environment
                // active while its runtime/policy restoration is still pending.
                if (previous != null) {
                    try {
                        applyEnvironment(previous).getOrThrow()
                        _activeEnvironment.value = previous
                    } catch (restoreCancelled: CancellationException) {
                        throw restoreCancelled
                    } catch (restoreFailure: Exception) {
                        failure.addSuppressed(restoreFailure)
                        _activeEnvironment.value = null
                        ApprovalGate.configure(
                            GateConfig(
                                permissionPolicy = PermissionPolicy.SAFE_MODE,
                                safetyRestrictions = SafetyRestrictions.MAX
                            )
                        )
                    }
                } else {
                    _activeEnvironment.value = null
                    ApprovalGate.configure(
                        GateConfig(
                            permissionPolicy = PermissionPolicy.SAFE_MODE,
                            safetyRestrictions = SafetyRestrictions.MAX
                        )
                    )
                }
            }
        }
        activationJob = job
        job.join()

        activationFailure?.let { return Result.failure(it) }
        if (job.isCancelled) {
            return Result.failure(CancellationException("Environment activation was cancelled."))
        }
        return Result.success(Unit)
    }

    /** Delete a custom profile (built-ins are immutable) */
    suspend fun delete(profileId: String): Result<Unit> = try {
        val profile = _availableEnvironments.value.find { it.id == profileId }
            ?: return Result.failure(IllegalArgumentException("Profile not found: $profileId"))
        if (profile.isBuiltIn) return Result.failure(IllegalArgumentException("Built-in profiles cannot be deleted"))
        if (_activeEnvironment.value?.id == profileId) {
            // Do not delete the active profile unless the safe fallback really
            // became active; a failed activation must leave the profile intact.
            val fallback = activate("safe")
            if (fallback.isFailure) return Result.failure(
                fallback.exceptionOrNull() ?: IllegalStateException("Safe environment activation failed.")
            )
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
        // Provider selection is authoritative. Never activate a profile when
        // its requested provider rejected the configuration (e.g. unimplemented
        // Cloud provider). Runtime reachability is separately observable and
        // may be degraded without invalidating the selected profile.
        modelManager.switchProvider(profile.provider, profile.modelSource).getOrThrow()

        when (profile.provider) {
            ModelProviderType.REMOTE_FRIDAY -> {
                // RuntimeBinder records an explicit disconnected/failed state
                // when no authenticated remote handshake is available.
                runtimeBinder.attachToRemote(profile.modelSource)
            }
            ModelProviderType.LLAMA_CPP, ModelProviderType.OLLAMA -> {
                // A configured local provider may be temporarily offline; keep
                // the profile selected but let RuntimeBinder expose that fact.
                runtimeBinder.attachLocal(profile.modelSource)
            }
            else -> runtimeBinder.detach()
        }

        // This is the live authorization policy. It must track the active
        // environment, not remain at ApprovalGate's startup default.
        ApprovalGate.configure(
            GateConfig(
                permissionPolicy = profile.permissionPolicy,
                safetyRestrictions = profile.safetyRestrictions
            )
        )

        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private suspend fun deactivateInternal(profile: EnvironmentProfile) {
        // Cleanup: detach runtime, flush pending sync, etc.
        runtimeBinder.detach()
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