package com.jarvis.app.identity.owner

/**
 * Typed outcome of an Android `BiometricPrompt` authorization, produced from
 * the real platform callbacks:
 *
 *  - [Success]  ← `onAuthenticationSucceeded` — the ONLY binding-capable state.
 *  - [Error]    ← `onAuthenticationError` (and, on the platform path, the
 *    legacy `onAuthenticationFailed`) — never authorizes.
 *  - [Cancelled]← `onAuthenticationError(ERROR_USER_CANCELED)` — never authorizes.
 *
 * Crucial property (strict owner binding, People Voice/Face Memory): the
 * [Success] object carries ONLY the authenticator class and a timestamp. It
 * carries NO raw biometric material — no fingerprint image, no face template,
 * no biometric signature, no BiometricPrompt crypto object — and no physical-
 * device identifier. Owner authority is the human the platform authenticated,
 * never possession of any phone.
 */
sealed class BiometricAuthResult {

    /**
     * A successful platform authorization. [authenticators] records which
     * authenticator class the platform verified; [authenticatedAtEpochMs] is
     * the moment the platform reported success.
     */
    data class Success(
        val authenticators: BiometricType,
        val authenticatedAtEpochMs: Long
    ) : BiometricAuthResult()

    /** A failed/errored authorization (e.g. too many attempts, hardware error). */
    data class Error(
        val errorCode: Int,
        val errorMessage: String
    ) : BiometricAuthResult()

    /** The user dismissed the prompt (`ERROR_USER_CANCELED`). */
    object Cancelled : BiometricAuthResult()
}