package ai.friday.assistant.diagnostics

/**
 * Interprets read-only Linux memory telemetry captured through Friday's
 * explicitly granted Shizuku shell service. PSI is a stall signal; MemAvailable
 * alone is not treated as proof of pressure. OOM counters are cumulative.
 */
data class PhoneMemoryPressureReport(
    val totalMb: Long?,
    val availableMb: Long?,
    val swapTotalMb: Long?,
    val swapFreeMb: Long?,
    val someAvg10: Double?,
    val fullAvg10: Double?,
    val oomKills: Long?,
    val directPageScans: Long?,
    val assessment: String,
    val explanation: String
) {
    fun summary(): String = buildString {
        append("Phone memory: ")
        append(assessment)
        append(" · ")
        append("RAM available ")
        append(availableMb?.let { "${it} MB" } ?: "unknown")
        append(" / ")
        append(totalMb?.let { "${it} MB" } ?: "unknown")
        append(" · PSI some avg10=")
        append(someAvg10?.let { "%.2f%%".format(java.util.Locale.ROOT, it) } ?: "unavailable")
        append(" · PSI full avg10=")
        append(fullAvg10?.let { "%.2f%%".format(java.util.Locale.ROOT, it) } ?: "unavailable")
        append(" · cumulative OOM kills=")
        append(oomKills?.toString() ?: "unavailable")
        append(". ")
        append(explanation)
    }
}

object PhoneMemoryPressureAnalyzer {
    fun analyze(meminfo: String, psi: String, vmstat: String): PhoneMemoryPressureReport {
        val memory = parseKeyValues(meminfo)
        val vm = parseKeyValues(vmstat)
        val some = parsePsiAvg10(psi, "some")
        val full = parsePsiAvg10(psi, "full")
        val total = memory["MemTotal"]?.div(1024L)
        val available = memory["MemAvailable"]?.div(1024L)
        val swapTotal = memory["SwapTotal"]?.div(1024L)
        val swapFree = memory["SwapFree"]?.div(1024L)
        val oomKills = vm["oom_kill"]
        val directScans = vm["pgscan_direct"]
        val hasPsi = some != null || full != null
        val severe = (full ?: 0.0) >= SEVERE_FULL_PSI_AVG10 ||
            (some ?: 0.0) >= SEVERE_SOME_PSI_AVG10
        val elevated = (full ?: 0.0) >= ELEVATED_PSI_AVG10 ||
            (some ?: 0.0) >= ELEVATED_PSI_AVG10
        val assessment = when {
            severe -> "SEVERE PRESSURE SIGNAL"
            elevated -> "ELEVATED PRESSURE SIGNAL"
            hasPsi -> "NO SIGNIFICANT PSI STALL SIGNAL"
            else -> "PSI UNAVAILABLE"
        }
        val explanation = when {
            severe -> "The kernel reports sustained memory stalls; Friday should stay in critical mode and avoid loading optional models. This does not identify which other app is responsible."
            elevated -> "The kernel reports measurable memory stalls. Compare this with process PSS and Android low-memory exits before changing device settings."
            hasPsi -> "The kernel reports little current memory-stall time. A low free-memory figure by itself is not evidence of a fault."
            else -> "This kernel did not expose readable PSI data. Use Android low-memory flags and process-exit history as the fallback evidence."
        }
        return PhoneMemoryPressureReport(
            totalMb = total,
            availableMb = available,
            swapTotalMb = swapTotal,
            swapFreeMb = swapFree,
            someAvg10 = some,
            fullAvg10 = full,
            oomKills = oomKills,
            directPageScans = directScans,
            assessment = assessment,
            explanation = explanation
        )
    }

    private fun parseKeyValues(raw: String): Map<String, Long> = buildMap {
        raw.lineSequence().forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 2) {
                val key = parts[0].removeSuffix(":")
                parts[1].toLongOrNull()?.let { put(key, it) }
            }
        }
    }

    private fun parsePsiAvg10(raw: String, kind: String): Double? =
        raw.lineSequence().firstOrNull { it.trimStart().startsWith("$kind ") }
            ?.split(Regex("\\s+"))
            ?.firstOrNull { it.startsWith("avg10=") }
            ?.substringAfter("avg10=")
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 }

    private const val ELEVATED_PSI_AVG10 = 0.5
    private const val SEVERE_SOME_PSI_AVG10 = 10.0
    private const val SEVERE_FULL_PSI_AVG10 = 1.0
}
