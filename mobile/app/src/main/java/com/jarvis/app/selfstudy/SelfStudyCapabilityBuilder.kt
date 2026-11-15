package com.jarvis.app.selfstudy

import com.jarvis.app.builder.BuildResult
import com.jarvis.app.builder.ToolSpecification
import com.jarvis.app.evolution.Behavior
import com.jarvis.app.model.ResourceSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Properties

/**
 * Turns one evidence-backed self-study proposal into a bounded ToolBuilder
 * request. The existing ToolBuilder still owns synthesis, sandbox execution,
 * behavioral tests, fitness comparison, promotion and rollback.
 *
 * This lane can create only pure String -> String synth capabilities. It
 * cannot generate native code, touch Android APIs, request permissions, access
 * files/network, or change Friday's installed application.
 */
object SelfStudyCapabilityPolicy {
    private val forbidden = Regex(
        "\\b(permission|grant|shell|network|https?|socket|camera|microphone|location|contacts|sms|credential|password|filesystem|file\\s+system|self.modif|native\\s+code|dynamic\\s+loading|device\\s+control|phone\\s+control|android\\s+control|install|uninstall|delete|send\\s+message|make\\s+call)\\b",
        RegexOption.IGNORE_CASE
    )

    fun specification(reflection: ApplicationSelfStudy.Reflection): ToolSpecification? {
        val capability = reflection.candidateCapability
            ?.takeIf { it.matches(Regex("[a-z][a-z0-9_]{2,63}")) }
            ?: return null
        val description = reflection.candidateDescription?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 500 && '\\n' !in it && '\\r' !in it }
            ?: return null
        if (forbidden.containsMatchIn(capability.replace('_', ' ')) ||
            forbidden.containsMatchIn(description)
        ) return null

        return ToolSpecification(
            capability = capability,
            description = description,
            behavior = Behavior.Custom(
                capability = capability,
                port = "input",
                type = "String"
            ),
            constraints = listOf(
                "Pure deterministic transformation only: one String input and one String output.",
                "No Android APIs, device control, permissions, files, network, processes, shell, reflection, dynamic loading or native code.",
                "Do not access credentials, passwords, messages, contacts, location, camera, microphone or other private user data.",
                "Use only the Friday synth language; no external dependencies or side effects.",
                "Bound execution and intermediate values; fail loudly for invalid or missing input.",
                "Generate success, boundary and failure-path tests. Do not weaken existing capabilities."
            ),
            maxMemoryMb = 16,
            maxCpuPercent = 2.0,
            researchFirst = false,
            requireVerifiedResearch = false
        )
    }
}

/** Persisted record of the most recent bounded self-build attempt. */
data class SelfStudyBuildAttempt(
    val createdAtMs: Long,
    val sourceRevision: String,
    val capability: String,
    val status: String,
    val promoted: Boolean,
    val providerId: String?,
    val genomeId: String?,
    val fitness: Double?,
    val message: String
)

/**
 * Resource-gated, rate-limited bridge from Friday's self-study to her existing
 * isolated capability foundry. A failed proposal is not retried every 30 min;
 * the same source revision gets a six-hour cooldown.
 */
class SelfStudyCapabilityBuilder(
    private val stateFile: File,
    private val build: suspend (ToolSpecification) -> BuildResult,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val minimumAvailableMemoryMb: Long = 1400L,
    private val minimumBatteryPercent: Int = 35,
    private val retryCooldownMs: Long = 6L * 60L * 60L * 1000L
) {
    private val mutex = Mutex()
    @Volatile private var latest: SelfStudyBuildAttempt? = readAttempt()

    fun latestAttempt(): SelfStudyBuildAttempt? = latest

    suspend fun buildIfUseful(
        reflection: ApplicationSelfStudy.Reflection,
        resources: ResourceSnapshot
    ): SelfStudyBuildAttempt? = mutex.withLock {
        val spec = SelfStudyCapabilityPolicy.specification(reflection) ?: return@withLock null
        if (resources.availableMemoryMb < minimumAvailableMemoryMb ||
            resources.thermalLevel > 0 ||
            (resources.batteryPercent < minimumBatteryPercent && !resources.isCharging)
        ) return@withLock null

        val previous = latest
        val sameCandidate = previous?.sourceRevision == reflection.sourceRevision &&
            previous.capability == spec.capability
        if (sameCandidate && previous?.promoted == true) return@withLock previous
        if (previous != null && nowMs() - previous.createdAtMs < retryCooldownMs) {
            return@withLock null
        }

        val started = SelfStudyBuildAttempt(
            createdAtMs = nowMs(),
            sourceRevision = reflection.sourceRevision,
            capability = spec.capability,
            status = "BUILDING",
            promoted = false,
            providerId = null,
            genomeId = null,
            fitness = null,
            message = "Candidate entered Friday's isolated capability foundry."
        )
        persist(started)
        latest = started

        val result = try {
            build(spec)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val failed = started.copy(
                status = "BUILD_EXCEPTION",
                message = failure.message.orEmpty().take(600)
            )
            persist(failed)
            latest = failed
            return@withLock failed
        }

        val completed = SelfStudyBuildAttempt(
            createdAtMs = nowMs(),
            sourceRevision = reflection.sourceRevision,
            capability = spec.capability,
            status = result.status,
            promoted = result.built && result.status == "PROMOTED",
            providerId = result.providerId,
            genomeId = result.genomeId,
            fitness = result.fitness,
            message = result.message.take(600)
        )
        persist(completed)
        latest = completed
        completed
    }

    private fun readAttempt(): SelfStudyBuildAttempt? = runCatching {
        if (!stateFile.isFile) return null
        val p = Properties()
        stateFile.inputStream().use(p::load)
        SelfStudyBuildAttempt(
            createdAtMs = p.getProperty("createdAtMs")?.toLongOrNull() ?: return null,
            sourceRevision = p.getProperty("sourceRevision").orEmpty(),
            capability = p.getProperty("capability").orEmpty(),
            status = p.getProperty("status").orEmpty(),
            promoted = p.getProperty("promoted").toBoolean(),
            providerId = p.getProperty("providerId")?.takeIf(String::isNotBlank),
            genomeId = p.getProperty("genomeId")?.takeIf(String::isNotBlank),
            fitness = p.getProperty("fitness")?.toDoubleOrNull(),
            message = p.getProperty("message").orEmpty()
        )
    }.getOrNull()

    private fun persist(attempt: SelfStudyBuildAttempt) {
        runCatching {
            stateFile.parentFile?.mkdirs()
            val p = Properties().apply {
                setProperty("createdAtMs", attempt.createdAtMs.toString())
                setProperty("sourceRevision", attempt.sourceRevision)
                setProperty("capability", attempt.capability)
                setProperty("status", attempt.status)
                setProperty("promoted", attempt.promoted.toString())
                setProperty("providerId", attempt.providerId.orEmpty())
                setProperty("genomeId", attempt.genomeId.orEmpty())
                setProperty("fitness", attempt.fitness?.toString().orEmpty())
                setProperty("message", attempt.message)
            }
            val temporary = File(stateFile.parentFile ?: File("."), stateFile.name + ".tmp")
            temporary.outputStream().use { p.store(it, "Friday bounded self-build evidence") }
            if (!temporary.renameTo(stateFile)) {
                stateFile.outputStream().use { p.store(it, "Friday bounded self-build evidence") }
                temporary.delete()
            }
        }
    }
}
