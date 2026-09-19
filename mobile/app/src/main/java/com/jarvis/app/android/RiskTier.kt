package com.jarvis.app.android

/**
 * Risk-tiered authorization model for Android device control, per jarvis.md.
 *
 * Map to the existing ApprovalGate RiskLevel where the on-device mechanism is
 * used; the tier semantics here are specific to device-control:
 *  - LOW                -> voice-only, no confirmation
 *  - MEDIUM             -> requires explicit confirmation
 *  - HIGH               -> requires biometric + confirmation
 *  - SELF_MODIFICATION  -> requires the full three-gate (biometric + confirmation + authorization)
 */
enum class RiskTier {
    LOW,
    MEDIUM,
    HIGH,
    SELF_MODIFICATION
}
