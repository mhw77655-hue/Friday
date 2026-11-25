package ai.friday.assistant.diagnostics

/**
 * Shizuku is considered connected only after the remote UserService proves
 * that it runs as Android shell (UID 2000) or root (UID 0). A server ping or
 * permission grant alone is not a privileged binding.
 */
object ShizukuBindingHealthGate {
    fun isVerifiedBound(status: String, uid: Int?): Boolean =
        status.startsWith("bound · verified Shizuku uid=") && (uid == 0 || uid == 2000)
}
