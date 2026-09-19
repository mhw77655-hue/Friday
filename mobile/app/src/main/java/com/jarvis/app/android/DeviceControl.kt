package com.jarvis.app.android

/**
 * A typed Android-control action the adapter stack may try to execute.
 */
data class ControlAction(
    val id: String,
    val actionType: ActionType,
    val packageName: String? = null,
    val data: String? = null,
    val riskTier: RiskTier = RiskTier.LOW
)

enum class ActionType {
    LAUNCH_APP,
    PLAY,
    PAUSE,
    NEXT_TRACK,
    PREV_TRACK,
    OPEN_SETTINGS,
    TOGGLE_WIFI,
    VOLUME_CHANGE,
    SCREENSCAPE_SUMMARY,
    CUSTOM
}

/**
 * Verification evidence returned before an action is ever reported done.
 *
 * The chain is strict: we never claims completion without a verified stage.
 */
enum class VerificationStage {
    NOT_ATTEMPTED,
    LAUNCHED,
    TARGET_CONFIRMED,
    VERIFIED
}

data class VerificationEvidence(
    val stage: VerificationStage,
    val backendId: String,
    val detail: String = ""
) {
    val isComplete: Boolean get() = stage == VerificationStage.VERIFIED
}

/**
 * A single backend attempt's outcome. Every attempt is logged so the router
 * can prove which backend was tried and whether it succeeded.
 */
data class BackendAttempt(
    val backendId: String,
    val succeeded: Boolean,
    val evidence: VerificationEvidence,
    val error: String? = null
)

/**
 * A real Android-control backend in the fallback chain.
 *
 * Not every backend is available on every build/JVM; a backend reports
 * `available()` before it is tried.
 */
interface ControlBackend {
    val id: String

    fun available(): Boolean

    /** Attempt the action, returning verification evidence (never fire-and-assume). */
    fun execute(action: ControlAction): VerificationEvidence
}
