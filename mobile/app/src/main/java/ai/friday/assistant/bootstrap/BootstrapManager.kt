package ai.friday.assistant.bootstrap

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import ai.friday.assistant.FridayEngine
import ai.friday.assistant.env.EnvironmentProfile
import ai.friday.assistant.humancore.HumanCore
import ai.friday.assistant.humancore.store.FileStorage
import ai.friday.assistant.model.ModelManager
import ai.friday.assistant.runtime.RuntimeBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BootstrapManager - orchestrates first-run setup and ongoing boot sequence.
 * Handles: permissions, Shizuku, llama-server check, identity revision, environment selection.
 */
class BootstrapManager(
    private val context: Context,
    private val fileStorage: FileStorage,
    private val humanCore: HumanCore,
    private val modelManager: ModelManager,
    private val runtimeBinder: RuntimeBinder,
    private val scope: CoroutineScope
) {
    private val _bootstrapState = MutableStateFlow<BootstrapState>(BootstrapState.NOT_STARTED)
    val bootstrapState = _bootstrapState.asStateFlow()

    private val _currentStep = MutableStateFlow<BootstrapStep?>(null)
    val currentStep = _currentStep.asStateFlow()

    // 0..1 progress across the boot sequence (BootstrapScreen progress ring)
    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    // Non-critical setup gaps are visible to the UI but never prevent Friday
    // from opening. Permissions and optional services are requested only when
    // the owner invokes a capability that actually needs them.
    private val _notices = MutableStateFlow<List<String>>(emptyList())
    val notices = _notices.asStateFlow()

    private fun addNotice(message: String) {
        _notices.value = (_notices.value + message).distinct()
    }

    /** Run the bootstrap sequence */
    suspend fun runBootstrap(): BootstrapResult {
        _bootstrapState.value = BootstrapState.IN_PROGRESS
        _notices.value = emptyList()

        val steps = listOf(
            BootstrapStep.CHECK_PERMISSIONS,
            BootstrapStep.CHECK_SHIZUKU,
            BootstrapStep.CHECK_LLAMA_SERVER,
            BootstrapStep.LOAD_IDENTITY,
            BootstrapStep.SELECT_ENVIRONMENT,
            BootstrapStep.INITIALIZE_COMPONENTS,
            BootstrapStep.COMPLETE
        )

        for ((index, step) in steps.withIndex()) {
            _currentStep.value = step
            _progress.value = (index + 1) / steps.size.toFloat()
            val result = executeStep(step)
            if (result.isFailure) {
                _bootstrapState.value = BootstrapState.FAILED(result.exceptionOrNull())
                return BootstrapResult.Failure(step, result.exceptionOrNull()!!)
            }
        }

        _bootstrapState.value = BootstrapState.COMPLETED
        _currentStep.value = null
        return BootstrapResult.Success
    }

    /** Execute a single bootstrap step */
    private suspend fun executeStep(step: BootstrapStep): Result<Unit> = when (step) {
        BootstrapStep.CHECK_PERMISSIONS -> checkPermissions()
        BootstrapStep.CHECK_SHIZUKU -> checkShizuku()
        BootstrapStep.CHECK_LLAMA_SERVER -> checkLlamaServer()
        BootstrapStep.LOAD_IDENTITY -> loadIdentity()
        BootstrapStep.SELECT_ENVIRONMENT -> selectEnvironment()
        BootstrapStep.INITIALIZE_COMPONENTS -> initializeComponents()
        BootstrapStep.COMPLETE -> Result.success(Unit)
    }

    private suspend fun checkPermissions(): Result<Unit> = withContext(Dispatchers.IO) {
        // Microphone, camera, notifications and special settings access are
        // feature-scoped permissions. Friday's typed-first foundation must not
        // demand them at installation or block startup when they are denied.
        val missing = buildList {
            if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add("Microphone access is not granted; voice features remain unavailable until requested and approved.")
            }
            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                add("Camera access is not granted; camera features remain unavailable until requested and approved.")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add("Notification access is not granted; background status notifications may be limited.")
            }
        }
        missing.forEach(::addNotice)
        // Friday uses app-private storage. MANAGE_EXTERNAL_STORAGE and legacy
        // WRITE_EXTERNAL_STORAGE are not bootstrap prerequisites.
        Result.success(Unit)
    }

    private suspend fun checkShizuku(): Result<Unit> = withContext(Dispatchers.IO) {
        // Shizuku is an optional escalation route, never a startup dependency.
        // The capability that needs it must check binder + owner authorization
        // at invocation time and offer a supported fallback when possible.
        addNotice("Shizuku is optional and will be checked when a capability needs it.")
        Result.success(Unit)
    }

    private suspend fun checkLlamaServer(): Result<Unit> = withContext(Dispatchers.IO) {
        // No pre-existing localhost/Ollama/llama-server process is assumed.
        // A missing model/runtime is a degraded capability state, not a reason
        // to prevent Friday's identity, UI, memory or bootstrap from starting.
        val health = runCatching { modelManager.health() }
            .getOrElse {
                addNotice("Local inference is not ready yet: ${it.message ?: "runtime unavailable"}.")
                return@withContext Result.success(Unit)
            }
        if (!health.isHealthy) {
            addNotice("Local inference is not ready yet: ${health.error ?: "no resident model/runtime available"}.")
        }
        Result.success(Unit)
    }

    private suspend fun loadIdentity(): Result<Unit> = withContext(Dispatchers.IO) {
        // HumanCore already loads identity on init
        // Just verify it's loaded
        val identity = humanCore.describe()
        if (identity.isBlank()) {
            Result.failure(BootstrapException.IdentityLoadFailed("No identity loaded"))
        } else {
            Result.success(Unit)
        }
    }

    private suspend fun selectEnvironment(): Result<Unit> = withContext(Dispatchers.IO) {
        // Environment is loaded by LiquidEnvironmentManager on init
        // This step just ensures one is active
        Result.success(Unit)
    }

    private suspend fun initializeComponents(): Result<Unit> = withContext(Dispatchers.IO) {
        // Start health checks, discovery, etc.
        modelManager.startHealthChecks()
        runtimeBinder.initialize()
        Result.success(Unit)
    }

    /** Skip to a specific step (for testing/recovery) */
    suspend fun skipTo(step: BootstrapStep) {
        _currentStep.value = step
    }

    /** Retry current step */
    suspend fun retryCurrentStep() {
        _currentStep.value?.let { runBootstrap() }
    }

    companion object {
        /** Shares the engine's ModelManager/RuntimeBinder — never creates a second brain. */
        fun create(): BootstrapManager = BootstrapManager(
            context = FridayEngine.appContext(),
            fileStorage = FileStorage(FridayEngine.appContext().filesDir),
            humanCore = HumanCore,
            modelManager = FridayEngine.modelManager
                ?: throw IllegalStateException("ModelManager not initialized. Call FridayEngine.init() first."),
            runtimeBinder = FridayEngine.runtimeBinder
                ?: throw IllegalStateException("RuntimeBinder not initialized. Call FridayEngine.init() first."),
            scope = CoroutineScope(Dispatchers.Main)
        )
    }
}

sealed interface BootstrapState {
    data object NOT_STARTED : BootstrapState
    data object IN_PROGRESS : BootstrapState
    data object COMPLETED : BootstrapState
    data class FAILED(val error: Throwable?) : BootstrapState
}

enum class BootstrapStep {
    CHECK_PERMISSIONS("Checking optional permissions"),
    CHECK_SHIZUKU("Discovering optional Shizuku access"),
    CHECK_LLAMA_SERVER("Checking local inference readiness"),
    LOAD_IDENTITY("Loading identity"),
    SELECT_ENVIRONMENT("Selecting environment"),
    INITIALIZE_COMPONENTS("Initializing components"),
    COMPLETE("Bootstrap complete");

    val label: String
    constructor(label: String) { this.label = label }
}

sealed interface BootstrapResult {
    data object Success : BootstrapResult
    data class Failure(val failedStep: BootstrapStep, val error: Throwable) : BootstrapResult
}

sealed class BootstrapException(message: String) : Exception(message) {
    data class PermissionsRequired(val permissions: List<String>) : BootstrapException("Permissions required: ${permissions.joinToString(", ")}")
    object ShizukuUnavailable : BootstrapException("Shizuku not available")
    data class LlamaServerUnreachable(val error: String?) : BootstrapException("llama-server unreachable: $error")
    data class IdentityLoadFailed(val reason: String) : BootstrapException("Identity load failed: $reason")
    object EnvironmentSelectionFailed : BootstrapException("Environment selection failed")
    object ComponentInitFailed : BootstrapException("Component initialization failed")
}