package ai.friday.assistant.bootstrap.host

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ai.friday.assistant.BuildConfig
import ai.friday.assistant.ShizukuBridge
import rikka.shizuku.Shizuku
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * First-launch host survey and private workspace bootstrap.
 *
 * Collects technical facts from Android APIs, creates Friday's private
 * development/evidence directories, and persists a resumable report. It does
 * not inspect the user's installed-app inventory, personal files, messages,
 * notifications, screen contents, or unique device serials.
 */
class HostBootstrapWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val workspace = File(applicationContext.filesDir, "friday-development")
        val directories = listOf(
            File(workspace, "workspace"),
            File(workspace, "candidates"),
            File(workspace, "evidence"),
            File(workspace, "cache"),
            File(workspace, "checkpoints")
        )
        directories.forEach { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("Could not prepare private development directory: ${dir.name}")
            }
            if (!dir.isDirectory || !dir.canWrite()) {
                throw IllegalStateException("Development directory is not writable: ${dir.name}")
            }
        }

        val inspection = inspectHost()
        val config = HostBootstrapPolicy.configure(inspection.capacity)
        val report = JSONObject()
            .put("schema_version", 1)
            .put("status", "host_studied_workspace_ready")
            .put("created_at_epoch_ms", System.currentTimeMillis())
            .put("source_revision", BuildConfig.SOURCE_REVISION)
            .put("host", inspection.facts)
            .put("capacity", JSONObject()
                .put("available_memory_mb", inspection.capacity.availableMemoryMb)
                .put("memory_class_mb", inspection.capacity.memoryClassMb)
                .put("available_storage_mb", inspection.capacity.availableStorageMb)
                .put("cpu_cores", inspection.capacity.cpuCores)
                .put("cpu_load_percent", if (inspection.capacity.cpuLoadPercent >= 0.0) inspection.capacity.cpuLoadPercent else JSONObject.NULL)
                .put("process_pss_mb", inspection.capacity.processPssMb)
                .put("primary_abi", inspection.capacity.primaryAbi)
                .put("thermal_status", inspection.capacity.thermalStatus)
                .put("battery_percent", inspection.capacity.batteryPercent)
                .put("charging", inspection.capacity.charging)
                .put("power_save", inspection.capacity.powerSave)
                .put("installed_gguf_count", inspection.capacity.installedGgufCount)
                .put("native_inference_library_present", inspection.capacity.nativeInferenceLibraryPresent))
            .put("configuration", JSONObject()
                .put("mode", config.mode.name)
                .put("context_token_budget", config.contextTokenBudget)
                .put("max_concurrent_expensive_tasks", config.maxConcurrentExpensiveTasks)
                .put("allow_background_indexing", config.allowBackgroundIndexing)
                .put("allow_local_model_load", config.allowLocalModelLoad)
                .put("recommended_model_max_bytes", config.recommendedModelMaxBytes)
                .put("reason", config.reason))
            .put("integrations", inspection.integrations)
            .put("setup_checklist", inspection.setupChecklist)
            .put("development_workspace", JSONObject()
                .put("root", "files/friday-development")
                .put("directories_ready", directories.size)
                .put("private_app_storage", true))
            .put("next_stage", "canonical_objectives_plan_discovery_and_task_graph_bootstrap")
            .put("limitations", JSONArray()
                .put("This survey does not establish a source checkout or Android build toolchain.")
                .put("Native application changes still require the repository build and trusted signing/update path.")
                .put("Optional permissions and privileged integrations are reported, never silently granted."))

        writeAtomically(File(workspace, "host-bootstrap.json"), report.toString(2))
        File(workspace, "host-bootstrap-failure.json").takeIf { it.exists() }?.delete()
        if (ai.friday.assistant.github.reporting.GitHubDeviceReportConsentStore(applicationContext).isEnabled()) {
            ai.friday.assistant.github.reporting.GitHubDeviceReportWorker.enqueue(
                applicationContext, "host-bootstrap"
            )
        }
        Result.success()
    } catch (error: Exception) {
        // Persist a sanitized failure marker; never serialize raw exception text.
        runCatching {
            val dir = File(applicationContext.filesDir, "friday-development")
            dir.mkdirs()
            writeAtomically(
                File(dir, "host-bootstrap-failure.json"),
                JSONObject()
                    .put("schema_version", 1)
                    .put("status", "failed")
                    .put("failure_type", error.javaClass.simpleName)
                    .put("retryable", true)
                    .put("created_at_epoch_ms", System.currentTimeMillis())
                    .toString(2)
            )
        }
        Result.retry()
    }

    private data class HostInspection(
        val capacity: HostCapacity,
        val facts: JSONObject,
        val integrations: JSONObject,
        val setupChecklist: JSONArray
    )

    private fun inspectHost(): HostInspection {
        val context = applicationContext
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val storage = StatFs(context.filesDir.absolutePath)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { power.currentThermalStatus }.getOrDefault(-1)
        } else -1
        val availableStorageMb = storage.availableBytes / (1024L * 1024L)
        val modelCount = countLocalModels(listOf(
            File(context.filesDir, "models"),
            File(context.filesDir, "model"),
            File(context.noBackupFilesDir, "models")
        ))
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val nativeLibraries = nativeDir.listFiles()?.map { it.name }.orEmpty()
        val hasInferenceLibrary = nativeLibraries.any {
            it.contains("llama", ignoreCase = true) ||
                it.contains("ggml", ignoreCase = true)
        }
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val capacity = HostCapacity(
            apiLevel = Build.VERSION.SDK_INT,
            availableMemoryMb = memory.availMem / (1024L * 1024L),
            memoryClassMb = activityManager.memoryClass,
            availableStorageMb = availableStorageMb,
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            cpuLoadPercent = measureCpuLoadPercent() ?: -1.0,
            processPssMb = runCatching {
                activityManager.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
                    .firstOrNull()?.totalPss?.div(1024L) ?: 0L
            }.getOrDefault(0L),
            primaryAbi = abi,
            thermalStatus = thermal,
            batteryPercent = batteryPercent,
            charging = charging,
            powerSave = power.isPowerSaveMode,
            installedGgufCount = modelCount,
            nativeInferenceLibraryPresent = hasInferenceLibrary
        )

        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivity.activeNetwork
        val networkCapabilities = activeNetwork?.let(connectivity::getNetworkCapabilities)
        val network = JSONObject()
            .put("connected", networkCapabilities != null)
            .put("validated_internet", networkCapabilities?.hasCapability(
                android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
            .put("metered", if (activeNetwork == null) JSONObject.NULL else connectivity.isActiveNetworkMetered)
            .put("wifi", networkCapabilities?.hasTransport(
                android.net.NetworkCapabilities.TRANSPORT_WIFI) == true)
            .put("cellular", networkCapabilities?.hasTransport(
                android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true)

        val acceleratorProbe = JSONObject()
            .put("vulkan_hardware_feature_advertised", vulkanLevel >= 0)
            .put("vulkan_hardware_level", if (vulkanLevel >= 0) vulkanLevel else JSONObject.NULL)
            .put("vulkan_hardware_version", if (vulkanVersion >= 0) vulkanVersion else JSONObject.NULL)
            .put("vulkan_compute_runtime", "NOT_RUNTIME_VALIDATED")
            .put("nnapi_api_available", Build.VERSION.SDK_INT >= 27)
            .put("nnapi_device_enumeration", "NOT_PROBED")
            .put("npu", "UNVERIFIED_NO_UNIVERSAL_PUBLIC_ANDROID_PROBE")

        val facts = JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("board", Build.BOARD)
            .put("soc_model", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else JSONObject.NULL)
            .put("android_release", Build.VERSION.RELEASE)
            .put("api_level", Build.VERSION.SDK_INT)
            .put("supported_abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("cpu_cores", capacity.cpuCores)
            .put("process_pss_mb", capacity.processPssMb)
            .put("total_memory_mb", memory.totalMem / (1024L * 1024L))
            .put("app_memory_class_mb", activityManager.memoryClass)
            .put("free_private_storage_mb", availableStorageMb)
            .put("accelerators", acceleratorProbe)
            .put("network", network)

        val microphoneGranted = hasPermission(Manifest.permission.RECORD_AUDIO)
        val cameraGranted = hasPermission(Manifest.permission.CAMERA)
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        val batteryOptimizationExempt = power.isIgnoringBatteryOptimizations(context.packageName)
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val notificationListenerEnabled = isNotificationListenerEnabled()
        val shizuku = inspectShizukuState()
        val validatedInternet = networkCapabilities?.hasCapability(
            android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val integrations = JSONObject()
            .put("microphone_permission", microphoneGranted)
            .put("camera_permission", cameraGranted)
            .put("notifications_permission", notificationsGranted)
            .put("battery_optimization_exempt", batteryOptimizationExempt)
            .put("accessibility_service_enabled", accessibilityEnabled)
            .put("notification_listener_enabled", notificationListenerEnabled)
            .put("shizuku", shizuku.toJson())
            .put("native_libraries", JSONArray(nativeLibraries.filter {
                it.contains("llama", true) || it.contains("ggml", true) ||
                    it.contains("onnx", true) || it.contains("sherpa", true)
            }))
            .put("local_model_files", JSONObject().put("gguf_count", modelCount))
            .put("accelerator_probe", acceleratorProbe)
        val checklist = HostSetupChecklist.build(
            microphoneGranted = microphoneGranted,
            cameraGranted = cameraGranted,
            notificationsGranted = notificationsGranted,
            batteryOptimizationExempt = batteryOptimizationExempt,
            accessibilityEnabled = accessibilityEnabled,
            notificationListenerEnabled = notificationListenerEnabled,
            shizuku = shizuku,
            validatedInternet = validatedInternet
        )
        return HostInspection(capacity, facts, integrations, checklist)
    }



    /**
     * Measures aggregate CPU utilization over a short interval. If /proc/stat
     * is unavailable or counters are invalid, returns null rather than claiming
     * the phone is idle.
     */
    private fun measureCpuLoadPercent(): Double? = runCatching {
        fun counters(): Pair<Long, Long>? {
            val line = File("/proc/stat").useLines { lines -> lines.firstOrNull() } ?: return null
            val values = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (values.size < 4) return null
            // guest/guest_nice are already included in user/nice on Linux.
            val total = values.take(8).sum()
            val idle = values[3] + (values.getOrNull(4) ?: 0L)
            return total to idle
        }
        val first = counters() ?: return null
        android.os.SystemClock.sleep(120)
        val second = counters() ?: return null
        val totalDelta = second.first - first.first
        val idleDelta = second.second - first.second
        if (totalDelta <= 0L || idleDelta < 0L) null
        else (((totalDelta - idleDelta).toDouble() / totalDelta.toDouble()) * 100.0)
            .coerceIn(0.0, 100.0)
    }.getOrNull()

    private fun inspectShizukuState(): ShizukuCapabilityState {
        val installed = isPackageInstalled("moe.shizuku.privileged.api")
        // Initialize the official API and query the live Binder; package presence
        // alone is not evidence that a server is running or Friday is authorized.
        val binderReachable = runCatching {
            ShizukuBridge.init()
            ShizukuBridge.refresh()
            Shizuku.pingBinder()
        }.getOrDefault(false)
        val authorized = binderReachable && runCatching {
            ShizukuBridge.isGranted.value &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val uid = if (binderReachable) runCatching { Shizuku.getUid() }.getOrNull() else null
        return ShizukuCapabilityState(
            managerInstalled = installed,
            binderReachable = binderReachable,
            fridayAuthorized = authorized,
            serverUid = uid
        )
    }

    private fun ShizukuCapabilityState.toJson(): JSONObject = JSONObject()
        .put("manager_installed", managerInstalled)
        .put("binder_reachable", binderReachable)
        .put("friday_authorized", fridayAuthorized)
        .put("server_uid", serverUid ?: JSONObject.NULL)
        .put("privilege", privilege.name)
        .put("usable_by_friday", usableByFriday)

    private fun hasPermission(permission: String): Boolean =
        applicationContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun isPackageInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            applicationContext.packageManager.getPackageInfo(
                packageName, PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            applicationContext.packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "${applicationContext.packageName}/ai.friday.assistant.voice.FridayAccessibilityService"
        val enabled = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            applicationContext.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val expected = "${applicationContext.packageName}/ai.friday.assistant.FridayNotificationListener"
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun countLocalModels(roots: List<File>): Int {
        var count = 0
        val queue = ArrayDeque<Pair<File, Int>>()
        roots.filter { it.isDirectory }.forEach { queue.add(it to 0) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 2000) {
            val (file, depth) = queue.removeFirst()
            visited++
            for (child in file.listFiles().orEmpty()) {
                if (child.isFile && child.extension.equals("gguf", true)) count++
                else if (child.isDirectory && depth < 3) queue.add(child to depth + 1)
            }
        }
        return count
    }

    private fun writeAtomically(target: File, contents: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(contents, Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    companion object {
        const val UNIQUE_WORK = "friday-host-discovery-bootstrap"

        fun enqueue(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<HostBootstrapWorker>()
                .addTag(UNIQUE_WORK)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                androidx.work.ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
