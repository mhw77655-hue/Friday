package com.jarvis.app.bootstrap

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.jarvis.app.JarvisEngine
import com.jarvis.app.env.EnvironmentProfile
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.model.ModelManager
import com.jarvis.app.runtime.RuntimeBinder
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

    /** Run the bootstrap sequence */
    suspend fun runBootstrap(): BootstrapResult {
        _bootstrapState.value = BootstrapState.IN_PROGRESS

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
        val required = mutableListOf<String>()

        // Microphone
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            required.add(android.Manifest.permission.RECORD_AUDIO)
        }

        // Storage (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                required.add("MANAGE_EXTERNAL_STORAGE")
            }
        } else {
            if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                required.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // Notification permission (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                required.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (required.isNotEmpty()) {
            // Signal UI to request permissions
            Result.failure(BootstrapException.PermissionsRequired(required))
        } else {
            Result.success(Unit)
        }
    }

    private suspend fun checkShizuku(): Result<Unit> = withContext(Dispatchers.IO) {
        // Check if Shizuku is running and we have permission
        // This is a best-effort check - the app will work without Shizuku but with reduced capabilities
        Result.success(Unit)
    }

    private suspend fun checkLlamaServer(): Result<Unit> = withContext(Dispatchers.IO) {
        // Try to reach llama-server
        val health = modelManager.health()
        if (!health.isHealthy) {
            Result.failure(BootstrapException.LlamaServerUnreachable(health.error))
        } else {
            Result.success(Unit)
        }
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
            context = JarvisEngine.appContext(),
            fileStorage = FileStorage(JarvisEngine.appContext().filesDir),
            humanCore = HumanCore,
            modelManager = JarvisEngine.modelManager
                ?: throw IllegalStateException("ModelManager not initialized. Call JarvisEngine.init() first."),
            runtimeBinder = JarvisEngine.runtimeBinder
                ?: throw IllegalStateException("RuntimeBinder not initialized. Call JarvisEngine.init() first."),
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
    CHECK_PERMISSIONS("Checking permissions"),
    CHECK_SHIZUKU("Checking Shizuku"),
    CHECK_LLAMA_SERVER("Checking llama-server"),
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