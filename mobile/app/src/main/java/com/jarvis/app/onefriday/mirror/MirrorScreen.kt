package com.jarvis.app.onefriday.mirror

import com.jarvis.app.onefriday.ledger.DevelopmentLedger
import com.jarvis.app.onefriday.ledger.LedgerEntry

/**
 * ONE-4-RECORDER-LEDGER: Mirror screen shows seeded entries and any ledger entries,
 * says plainly 'Nothing has developed yet' when there are none.
 */
class MirrorScreen(private val ledger: DevelopmentLedger) {
    fun render(): String {
        val entries = ledger.readAll()
        if (entries.isEmpty()) {
            return "Nothing has developed yet"
        }
        val sb = StringBuilder()
        sb.append("Mirror - Development State\n\n")
        val seeded = entries.filter { it.extras["seeded"] == true }
        val developed = entries.filter { it.extras["seeded"] != true }
        if (seeded.isNotEmpty()) {
            sb.append("## Seeded\n\n")
            for (e in seeded) {
                sb.append(renderEntry(e))
            }
        }
        if (developed.isNotEmpty()) {
            sb.append("## Developed\n\n")
            for (e in developed) {
                sb.append(renderEntry(e))
            }
        } else if (seeded.isNotEmpty()) {
            sb.append("## Developed\n\nNothing has developed yet\n\n")
        }
        return sb.toString()
    }
    
    private fun renderEntry(entry: LedgerEntry): String {
        val sb = StringBuilder()
        sb.append("- ${entry.whatChanged}\n")
        sb.append("  Affects: ${entry.affectsBehaviorOrCognition}\n")
        sb.append("  Evidence strength: ${entry.strengthOfEvidence}, Reversible: ${entry.reversible}\n")
        if (entry.reversibleHow != null) {
            sb.append("  Reversible via: ${entry.reversibleHow}\n")
        }
        sb.append("  Uncertainty: ${entry.remainingUncertainty}\n\n")
        return sb.toString()
    }
}
