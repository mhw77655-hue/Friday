package ai.friday.assistant.bootstrap.host

import org.json.JSONArray
import org.json.JSONObject

/**
 * Generates a capability-scoped first-launch checklist from observed host
 * state. It never requests permission or assumes that optional access is
 * required for Friday's core identity, local memory or typed interaction.
 */
object HostSetupChecklist {
    fun build(
        microphoneGranted: Boolean,
        cameraGranted: Boolean,
        notificationsGranted: Boolean,
        batteryOptimizationExempt: Boolean,
        accessibilityEnabled: Boolean,
        notificationListenerEnabled: Boolean,
        shizuku: ShizukuCapabilityState,
        validatedInternet: Boolean
    ): JSONArray = JSONArray().apply {
        put(item(
            id = "core_private_workspace",
            feature = "Friday's local identity and development workspace",
            status = "READY",
            requiredForCore = true,
            explanation = "Private app storage is available; no extra Android permission is required.",
            nextAction = "NONE",
            activationPolicy = "ALWAYS"
        ))
        put(item(
            id = "network_plan_sync",
            feature = "Canonical objectives plan synchronization",
            status = if (validatedInternet) "READY" else "OFFLINE_BUNDLED_PLAN_READY",
            requiredForCore = false,
            explanation = if (validatedInternet) "The bundled plan is available and GitHub can refresh it now."
                else "The canonical plan is bundled in the APK and available offline; GitHub refresh waits for validated internet.",
            nextAction = if (validatedInternet) "NONE" else "WAIT_FOR_NETWORK",
            activationPolicy = "AUTOMATIC_WHEN_AVAILABLE"
        ))
        put(item(
            id = "microphone",
            feature = "Voice input",
            status = if (microphoneGranted) "READY" else "OPTIONAL_PERMISSION_NOT_GRANTED",
            requiredForCore = false,
            explanation = "Microphone access is requested only when voice input is enabled or used.",
            nextAction = if (microphoneGranted) "NONE" else "REQUEST_MICROPHONE_PERMISSION",
            activationPolicy = "ON_FEATURE_USE"
        ))
        put(item(
            id = "camera",
            feature = "Camera-based perception",
            status = if (cameraGranted) "READY" else "OPTIONAL_PERMISSION_NOT_GRANTED",
            requiredForCore = false,
            explanation = "Camera access is requested only when a camera capability is enabled or used.",
            nextAction = if (cameraGranted) "NONE" else "REQUEST_CAMERA_PERMISSION",
            activationPolicy = "ON_FEATURE_USE"
        ))
        put(item(
            id = "notifications",
            feature = "Runtime status notifications",
            status = if (notificationsGranted) "READY" else "OPTIONAL_PERMISSION_NOT_GRANTED",
            requiredForCore = false,
            explanation = "Notifications improve visibility of background work; declining does not block core use.",
            nextAction = if (notificationsGranted) "NONE" else "REQUEST_NOTIFICATION_PERMISSION",
            activationPolicy = "WHEN_BACKGROUND_STATUS_IS_ENABLED"
        ))
        put(item(
            id = "battery_optimization",
            feature = "Long-running background continuity",
            status = if (batteryOptimizationExempt) "READY" else "OPTIONAL_SYSTEM_SETTING_NOT_ENABLED",
            requiredForCore = false,
            explanation = "Battery-optimization exemption may improve continuity on this host but is not guaranteed or required for ordinary foreground use.",
            nextAction = if (batteryOptimizationExempt) "NONE" else "OPEN_BATTERY_OPTIMIZATION_SETTINGS",
            activationPolicy = "WHEN_OWNER_ENABLES_PERSISTENT_RUNTIME"
        ))
        put(item(
            id = "accessibility",
            feature = "Accessibility-based phone interaction",
            status = if (accessibilityEnabled) "READY" else "OPTIONAL_SYSTEM_SERVICE_NOT_ENABLED",
            requiredForCore = false,
            explanation = "Accessibility is an optional action route and is checked only when a requested capability needs it.",
            nextAction = if (accessibilityEnabled) "NONE" else "OPEN_ACCESSIBILITY_SETTINGS",
            activationPolicy = "ON_FEATURE_USE"
        ))
        put(item(
            id = "notification_listener",
            feature = "Notification-context perception",
            status = if (notificationListenerEnabled) "READY" else "OPTIONAL_SYSTEM_SERVICE_NOT_ENABLED",
            requiredForCore = false,
            explanation = "Notification access is separate from notification display permission and is never required for core startup.",
            nextAction = if (notificationListenerEnabled) "NONE" else "OPEN_NOTIFICATION_LISTENER_SETTINGS",
            activationPolicy = "ON_FEATURE_USE"
        ))
        val shizukuStatus = when {
            shizuku.usableByFriday -> "READY"
            !shizuku.managerInstalled -> "OPTIONAL_MANAGER_NOT_INSTALLED"
            !shizuku.binderReachable -> "OPTIONAL_SERVER_NOT_RUNNING"
            !shizuku.fridayAuthorized -> "OPTIONAL_FRIDAY_AUTHORIZATION_REQUIRED"
            else -> "OPTIONAL_PRIVILEGE_UNVERIFIED"
        }
        put(item(
            id = "shizuku",
            feature = "Shizuku-mediated system capabilities",
            status = shizukuStatus,
            requiredForCore = false,
            explanation = when (shizukuStatus) {
                "READY" -> "The Binder is reachable, Friday is authorized, and server privilege is identified as ${shizuku.privilege}."
                "OPTIONAL_MANAGER_NOT_INSTALLED" -> "Shizuku is not installed; ordinary Friday functions remain available."
                "OPTIONAL_SERVER_NOT_RUNNING" -> "The manager exists but its server Binder is not running."
                "OPTIONAL_FRIDAY_AUTHORIZATION_REQUIRED" -> "The server is running, but Friday has not been authorized."
                else -> "The effective server privilege could not be verified; no elevated route will be assumed."
            },
            nextAction = when (shizukuStatus) {
                "READY" -> "NONE"
                "OPTIONAL_MANAGER_NOT_INSTALLED" -> "OPEN_OFFICIAL_SHIZUKU_DOWNLOAD"
                "OPTIONAL_SERVER_NOT_RUNNING" -> "OPEN_SHIZUKU_MANAGER"
                "OPTIONAL_FRIDAY_AUTHORIZATION_REQUIRED" -> "OPEN_SHIZUKU_AUTHORIZATION"
                else -> "RECHECK_SHIZUKU"
            },
            activationPolicy = "ONLY_WHEN_A_REQUESTED_OPERATION_REQUIRES_IT"
        ))
    }

    private fun item(
        id: String,
        feature: String,
        status: String,
        requiredForCore: Boolean,
        explanation: String,
        nextAction: String,
        activationPolicy: String
    ) = JSONObject()
        .put("id", id)
        .put("feature", feature)
        .put("status", status)
        .put("required_for_core", requiredForCore)
        .put("explanation", explanation)
        .put("next_action", nextAction)
        .put("activation_policy", activationPolicy)
}
