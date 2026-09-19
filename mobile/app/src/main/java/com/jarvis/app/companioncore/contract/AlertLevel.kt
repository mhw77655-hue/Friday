package com.jarvis.app.companioncore.contract

/**
 * Degradation/alert overlay layered on top of the presence mode (§2.6 input).
 *
 * Distinct from [PresenceMode]: the mode says *what JARVIS is doing*; the
 * alert level says *how degraded the system is right now* and may override the
 * render on top of the mode's own expression (the sharp 1Hz/2Hz pulse of
 * §3.8/§9.10-9.11). Derivation lives in the §2.30 integration set
 * (`HumanCoreIntegration` reads the HC `PresenceState`); a caller may also
 * force an alert for seams not yet wired (e.g. battery / emergency in Phase 4).
 */
enum class AlertLevel(val rank: Int) { NONE(0), WARNING(1), CRITICAL(2), OFFLINE(3) }
