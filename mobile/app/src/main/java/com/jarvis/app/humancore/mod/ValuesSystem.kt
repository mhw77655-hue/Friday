package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.store.IdentityStore
import com.jarvis.app.humancore.store.ValueRecord
import com.jarvis.app.humancore.store.ValueSeverity

/**
 * The Values System (§4): the list of values JARVIS holds, each with a
 * statement, a severity, and a justification. This module owns the *data*
 * (the values themselves, as authored via identity revisions) and the
 * *aggregation* (how a set of detected violation risks combines into an
 * overall values-compliance score). The actual *enforcement* — detecting that
 * an outbound message violates a statement — lives in the Consistency Guard
 * (§19), which reads this module's values. The §3 free-text [IdentityKernel.boundaries]
 * are stored/validated/admin-editable but not consumed for enforcement —
 * the guard maps detectors to value keys, so boundary enforcement follows the
 * values, not the free text (HUMAN_CORE_AUDIT M-3).
 *
 * Severity semantics (§4, §19):
 *  - HARD_BOUNDARY      -> a violation BLOCKS the message (fail-closed veto).
 *  - STRONG_PREFERENCE  -> a violation flags the message for softening.
 */
class ValuesSystem(private val identity: IdentityStore) {

    fun values(): List<ValueRecord> = identity.read().values

    fun hardBoundaries(): List<ValueRecord> =
        identity.read().values.filter { it.severity == ValueSeverity.HARD_BOUNDARY }

    fun strongPreferences(): List<ValueRecord> =
        identity.read().values.filter { it.severity == ValueSeverity.STRONG_PREFERENCE }

    fun statementFor(key: String): String? =
        identity.read().values.firstOrNull { it.key == key }?.statement

    /**
     * Aggregate a set of detector results into a single values-violation
     * risk in [0, 1]. A hard-boundary hit is always 1.0 (blocking); a strong
     * preference contributes its weighted fraction. This is the number the
     * Guard compares against its thresholds — it is computed here so the
     * severity logic lives in one place (§4).
     */
    fun aggregateViolationRisk(hits: List<Pair<ValueRecord, Double>>): Double {
        if (hits.isEmpty()) return 0.0
        if (hits.any { (v, _) -> v.severity == ValueSeverity.HARD_BOUNDARY }) return 1.0
        return hits.map { (_, confidence) -> confidence.coerceIn(0.0, 1.0) }
            .average()
            .coerceIn(0.0, 1.0)
    }

    /** Human-readable values summary for self-reflection (§15), never for display. */
    fun describeValues(): String =
        values().joinToString("; ") { "${it.key}: ${it.statement}" }
}
