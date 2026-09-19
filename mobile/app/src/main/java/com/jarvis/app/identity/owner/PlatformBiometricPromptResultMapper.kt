package com.jarvis.app.identity.owner

import android.hardware.biometrics.BiometricPrompt

/**
 * The single consumption point where a REAL Android `BiometricPrompt`
 * authentication result enters JARVIS's owner-binding chain.
 *
 * It is deliberately a thin, lossless pass-through: it copies NOTHING out of
 * the platform result except the fact of success plus the caller-known
 * authenticator class and a timestamp. It never reads the crypto object, never
 * inspects any biometric data, and never derives a device identity — JARVIS
 * does not build its own raw-fingerprint or face verification (People Voice/
 * Face Memory strict owner-chain).
 *
 * It targets the PLATFORM `android.hardware.biometrics.BiometricPrompt`
 * (API 28+) so the binding chain has no new library dependency; below API 28
 * the caller fails closed and simply does not bind (the app never falls back
 * to its own biometric measurement). This class is referenced at compile time
 * only by unit-test harnesses: it is never invoked on the JVM, matching the
 * existing pattern of the Android-only `AndroidResourceSnapshot`.
 */
object PlatformBiometricPromptResultMapper {

    /**
     * Map a successful platform authorization to the typed [BiometricAuthResult.Success]
     * the rest of the chain consumes.
     *
     * @param result the real `BiometricPrompt.AuthenticationResult` from
     *   `onAuthenticationSucceeded`; used only for its existence (the platform
     *   authorized), never for its contents.
     * @param authenticators the authenticator class the caller configured on
     *   the `BiometricPrompt` it launched (the platform result does not echo
     *   which class matched, but the caller knows what it asked for).
     */
    @Suppress("unused")
    fun toSuccess(
        result: BiometricPrompt.AuthenticationResult,
        authenticators: BiometricType,
        authenticatedAtEpochMs: Long = System.currentTimeMillis()
    ): BiometricAuthResult.Success = BiometricAuthResult.Success(
        authenticators = authenticators,
        authenticatedAtEpochMs = authenticatedAtEpochMs
    )

    /** Attach a platform status/error code to a [BiometricAuthResult.Error]. */
    fun toError(errorCode: Int, errorMessage: String): BiometricAuthResult =
        BiometricAuthResult.Error(errorCode = errorCode, errorMessage = errorMessage)
}