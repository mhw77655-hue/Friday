package com.jarvis.app.identity.owner

/**
 * The authenticator class behind an Android biometric authorization result.
 *
 * Mirrors the authenticator classes of Android's `BiometricPrompt`
 * (`androidx.biometrics.BiometricPrompt.Authenticators` / platform
 * `BiometricManager.Authenticators`). JARVIS only *records* which class the
 * platform authorized — it never inspects raw biometrics: the strict
 * owner-binding rule (People Voice/Face Memory) is that the user's own
 * identity gets the chain that consumes Android's biometric result and nothing
 * else, building no raw-fingerprint or face verification of its own.
 */
enum class BiometricType(val authenticatorBitmask: Int, val label: String) {

    /** `Authenticators.BIOMETRIC_STRONG` (Class 3): fingerprint/face/iris on a certified sensor. */
    BIOMETRIC_STRONG(0x0F, "biometric-strong"),

    /** `Authenticators.BIOMETRIC_WEAK` (Class 2): weaker but non-trivial biometric. */
    BIOMETRIC_WEAK(0xFF, "biometric-weak"),

    /** `Authenticators.DEVICE_CREDENTIAL`: PIN/pattern/password — not a biometric. */
    DEVICE_CREDENTIAL(0x8000, "device-credential"),

    /** The result did not carry an identifiable authenticator class. */
    UNKNOWN(0, "unknown");

    companion object {
        /**
         * Map the `BiometricPrompt.Authenticators` bitmask to a [BiometricType].
         * The presence of a class bit wins — exactly how the platform reports
         * which authenticator can be / was used.
         */
        fun fromBitmask(bitmask: Int): BiometricType = when {
            bitmask and 0x0F != 0 -> BIOMETRIC_STRONG
            bitmask and 0xFF != 0 -> BIOMETRIC_WEAK
            bitmask and 0x8000 != 0 -> DEVICE_CREDENTIAL
            else -> UNKNOWN
        }
    }
}