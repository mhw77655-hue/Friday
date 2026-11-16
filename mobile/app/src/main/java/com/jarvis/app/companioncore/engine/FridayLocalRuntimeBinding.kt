package com.jarvis.app.companioncore.engine

import com.jarvis.app.JarvisEngine

/**
 * Local Android binding to Friday's own model, turn and user-presence evidence.
 * It never reads or writes legacy Human Core state. Unknown dimensions remain
 * null; the binding does not infer mood, trust, bond or confidence.
 */
class FridayLocalRuntimeBinding(
    private val readStatus: () -> String = {
        JarvisEngine.modelManager?.status?.value ?: "idle"
    },
    private val readLastReply: () -> String = {
        JarvisEngine.modelManager?.lastReply?.value ?: ""
    },
    private val readPresence: () -> FridayPresenceEvidenceStore.Snapshot = {
        FridayPresenceEvidenceStore.state.value
    },
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : FridayRuntimeBinding {
    override val label: String = "friday-android"
    override val schemaVersion: Int = 1

    override fun read(): FridayRuntimeSnapshot {
        val now = nowMs()
        val presence = readPresence()
        val status = readStatus()
        val elapsed = presence.lastInteractionAtMs
            ?.takeIf { it > 0L && now >= it }
            ?.let { (now - it) / 1_000L }
        val userPresence = presence.userPresent?.let { if (it) "ACTIVE" else "AWAY" }
        val systemTier = when {
            status.contains("unreachable", ignoreCase = true) ||
                status.contains("failed", ignoreCase = true) ||
                status.contains("error", ignoreCase = true) -> "OFFLINE"
            status.contains("degraded", ignoreCase = true) -> "DEGRADED"
            status.isBlank() -> null
            else -> null // non-empty is not proof that the model is loaded/healthy
        }

        return FridayRuntimeSnapshot(
            ts = now,
            totalInteractions = presence.interactionCount,
            secondsSinceLastContact = elapsed,
            presenceModeName = userPresence,
            systemTierName = systemTier,
            currentActionRaw = null,
            bridgeStatus = status,
            lastUtteranceText = readLastReply().ifBlank { null },
            fabricationEvidence = false
        )
    }

    override fun isHealthy(): Boolean {
        val status = readStatus()
        return !status.contains("unreachable", ignoreCase = true) &&
            !status.contains("failed", ignoreCase = true) &&
            !status.contains("error", ignoreCase = true)
    }
}

/** Transitional name for tests/older adapters; now reads Friday runtime data only. */
@Deprecated("Use FridayLocalRuntimeBinding; the binding no longer reads Human Core.")
typealias LocalHumanCoreBinding = FridayLocalRuntimeBinding
