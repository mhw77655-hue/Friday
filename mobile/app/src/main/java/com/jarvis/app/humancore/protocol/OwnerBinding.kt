package com.jarvis.app.humancore.protocol

/**
 * The durable record that the Human Core's owner relationship has been bound
 * to a specific owner identity by an Android biometric authorization result.
 *
 * This is the END POINT of the owner-binding chain and the ONLY place the
 * result of Android's `BiometricPrompt` is consumed as authority. The chain
 * consumes Android's biometric authorization result and stores this record;
 * it NEVER builds its own raw-fingerprint or face verification and never stores raw
 * biometrics (People Voice/Face Memory rule: the user's own identity gets the
 * stricter chain — JARVIS trusts the platform biometric result, nothing it
 * measured itself).
 *
 * The binding is deliberately keyed by an *owner identity* ([ownerKey]) and a
 * *relationship id* ([relationshipId]), never by a physical-device id: owner
 * authority is the authenticated human behind the biometric result, not
 * possession of the phone. The same ownerKey binds on any device that holds
 * this relationship data; a fresh device with no data has no pre-bound owner
 * and must be re-authenticated, but the authority signal is always the
 * biometric result itself, never device possession.
 */
data class OwnerBinding(
    /** The canonical relationship id of the single per-user owner relationship. */
    val relationshipId: String,
    /**
     * The stable owner identity this relationship is bound to (in production
     * the user-model owner key — the durable, device-independent identity, not
     * a device id).
     */
    val ownerKey: String,
    /**
     * Which authenticator class the platform authorized (e.g. "biometric-strong",
     * "biometric-weak", "device-credential"). Informational only; the record
     * never contains biometric material.
     */
    val authenticator: String,
    /** Epoch millis of the successful authorization that established this binding. */
    val authenticatedAtEpochMs: Long
) {
    companion object {
        /**
         * The one canonical owner relationship id. The Human Core keeps a single
         * per-user relationship record; after a successful biometric binding it
         * is identified as the OWNER relationship, never a possession-derived id.
         */
        const val OWNER_RELATIONSHIP_ID = "owner"
    }
}