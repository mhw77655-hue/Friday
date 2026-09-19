package com.jarvis.app.companioncore.engine

import com.jarvis.app.JarvisEngine
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus

/**
 * Local in-process binding (spec §2.30) — reads the frozen Kotlin Human Core
 * directly via its public facade ([HumanCore.snapshot]) plus the brain-bridge
 * status, and feeds `UserPresenceEvent` advisories outward (audit A-7/V-2).
 *
 * This is the ONLY class in the Companion Core that imports `humancore.*` —
 * the dependency-lint contract test asserts it (plan §4.2). It reads only the
 * public `HumanCore` object facade and the public `StateBus`/`HcEvent` API,
 * never HC internals (invariant 2 / "read-only").
 *
 * ## `fabrication_flag` semantics (audit A-6/V-3)
 * `fabricationEvidence` latches TRUE only when the Consistency Guard publishes
 * a `GuardSoften`/`GuardVeto` whose reason names a fabricated claim — i.e. a
 * caught-and-corrected fabrication, confirmed. It is cleared on a new user
 * submission (a fresh exchange). It is never inferred from raw text or veto
 * categories the Guard did not actually classify as fabrication.
 *
 * ## `last_utterance_text` ownership (audit R-I1)
 * Single owner: this binding reads `ModelManager.lastReply` (the styled
 * reply the Conversation loop placed there). The ViewModel never writes a
 * `CompanionSignal` — the integration owns the mapping.
 */
class LocalHumanCoreBinding(
    private val bus: StateBus = StateBus,
    private val readSnapshot: () -> com.jarvis.app.humancore.protocol.StateSnapshot? =
        { HumanCore.snapshot() },
    private val readStatus: () -> String =
        { JarvisEngine.modelManager?.status?.value ?: "idle" },
    private val readLastReply: () -> String =
        { JarvisEngine.modelManager?.lastReply?.value ?: "" },
    private val readPresenceModeName: () -> String? =
        { HumanCore.snapshot()?.presence?.mode?.name },
    private val readSystemTierName: () -> String? =
        { HumanCore.snapshot()?.presence?.systemTier?.name }
) : HumanCoreBinding {

    override val label: String = "local"
    override val schemaVersion: Int = SCHEMA_VERSION

    @Volatile private var fabricationEvidence: Boolean = false
    @Volatile private var connected: Boolean = HumanCore.isInitialized()

    init {
        // Subscribe once for the process lifetime; StateBus.copy-on-write makes
        // subscribe/unsubscribe safe. Kept (never unsubscribed) in production —
        // the bus lives for the app lifetime (StateBus.reset() is test-only).
        bus.subscribe { event -> onHcEvent(event) }
    }

    private fun onHcEvent(event: HcEvent) {
        when (event) {
            is HcEvent.GuardSoften ->
                if (event.reason.contains("fabricated_")) fabricationEvidence = true
            is HcEvent.GuardVeto ->
                if (event.reason.contains("fabricated_")) fabricationEvidence = true
            else -> Unit
        }
    }

    /** New user text arrived — reset per-exchange fabrication evidence. */
    fun onUserSubmitted() {
        fabricationEvidence = false
    }

    override fun read(): RawCoreState {
        val snap = readSnapshot()
        connected = HumanCore.isInitialized()
        return if (snap == null) {
            RawCoreState.empty(ts = System.currentTimeMillis())
        } else {
            RawCoreState(
                ts = snap.ts,
                moodValence = snap.moodValence,
                moodArousal = snap.moodArousal,
                lastUserConfidence = snap.lastUserConfidence,
                trust = snap.trust,
                bondDepth = snap.bondDepth,
                totalInteractions = snap.totalInteractions,
                secondsSinceLastContact = snap.secondsSinceLastContact,
                lastUserSignals = snap.lastUserSignals,
                presenceModeName = readPresenceModeName(),
                systemTierName = readSystemTierName(),
                currentActionRaw = null, // local binding: derived via discriminator
                bridgeStatus = readStatus(),
                lastUtteranceText = readLastReply().ifBlank { null },
                fabricationEvidence = fabricationEvidence
            )
        }
    }

    override fun isHealthy(): Boolean = connected

    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}
