package com.jarvis.app.onefriday

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ONE-2-COMPOSITION, AC3 (second half): the wall-clock reads that REMAIN in the
 * turn path, listed, with a reason each.
 *
 * The AC asks for an explicit allowlist, and the point of one is that it FAILS
 * when the list stops matching reality. So this test walks the turn-path sources
 * and requires the set of (file, number of direct wall-clock reads) it finds to be
 * EXACTLY the set declared below. Adding a read to a listed file, adding a read
 * to a file that had none, or deleting a read that was listed all fail — the
 * list can only move through a human editing it, with a reason attached.
 *
 * Scope, stated rather than assumed: the package directories the ONE composition
 * root constructs the turn path out of (plus the Android entry file itself).
 * Directories are WALKED for `*.kt`, because a scope entry that is a package
 * directory and is only checked with `isFile` scans nothing at all — which is
 * how an earlier draft of this test compared 67 declared entries against an
 * empty map and did not notice. Everything else in `com/jarvis/app` is a
 * platform, UI, speech, research or self-modification periphery where no
 * cognitive outcome is decided; those reads are not part of a turn's behaviour
 * and are not this test's business.
 *
 * How each entry is read:
 *  - [ALLOWED] maps a source file to how many direct wall-clock reads it still
 *    contains; [REASONS] maps the same keys to the one-line reason. They must
 *    have identical keys and no blank reason, so a count can never arrive
 *    without its justification.
 *  - a file with no entry must contain ZERO: the overwhelming majority of the
 *    turn path already takes its time as a parameter, and that is the point.
 *  - counts are exact. One reason per file covers that file's reads, and the
 *    count is what makes a NEW read fail.
 *
 * Comment lines (KDoc included) and the part of a line after `//` are not code
 * and are not counted, so documenting a read never needs an allowlist entry.
 */
class WallClockAllowlistTest {

    @Test
    fun `AC3 the wall-clock reads left in the turn path are exactly the declared list`() {
        val root = sourceRoot()
        val scope = turnPathDirs() + "JarvisEngine.kt"

        val found = buildMap {
            for (name in sourceFilesIn(root, scope)) {
                val reads = directWallClockReads(File(root, name))
                if (reads > 0) put(name, reads)
            }
        }

        assertEquals(
            buildString {
                appendLine(
                    "The turn path's direct wall-clock reads changed. Every entry below is " +
                        "either missing (a new read nobody has justified) or stale (a read " +
                        "that was removed). Update ALLOWED/REASONS with a one-line reason, or " +
                        "remove the read."
                )
                appendLine("declared but no longer present: " + (ALLOWED.keys - found.keys).sorted())
                appendLine("present but not declared:   " + (found.keys - ALLOWED.keys).sorted())
                val moved = ALLOWED.keys.intersect(found.keys).filter { ALLOWED.getValue(it) != found.getValue(it) }
                appendLine("count changed:              " + moved.sorted().map { "$it ${ALLOWED.getValue(it)}->${found.getValue(it)}" })
            },
            ALLOWED,
            found
        )
    }

    @Test
    fun `every declared read carries a non-blank reason, and no file is listed twice`() {
        assertEquals(
            "an allowlist whose reasons drift from its entries stops being an allowlist",
            ALLOWED.keys,
            REASONS.keys
        )
        assertTrue(
            "a reason is the whole point of an allowlist entry",
            REASONS.values.none { it.isBlank() }
        )
    }

    /** The class must not quietly stop being a tripwire. */
    @Test
    fun `the detector still finds a wall-clock read`() {
        val probe = File.createTempFile("wallclock-probe", ".kt")
        probe.writeText(
            """
            |// System.currentTimeMillis() in a comment is documentation, not a read
            |/* Instant.now() in KDoc is documentation too */
            |val startedAt = System.currentTimeMillis()
            """.trimMargin()
        )
        try {
            assertEquals("one real read survives the comment filters", 1, directWallClockReads(probe))
        } finally {
            probe.delete()
        }
    }

    /** A scope entry that is a DIRECTORY has to be walked, or nothing is scanned. */
    @Test
    fun `the walker descends into the declared package directories`() {
        val root = sourceRoot()
        assertTrue(
            "the scope names package directories, so the walk must return files from inside them",
            sourceFilesIn(root, turnPathDirs()).any { it.contains("/") }
        )
        assertTrue(
            "the entry point file itself is in scope",
            sourceFilesIn(root, listOf("JarvisEngine.kt")).contains("JarvisEngine.kt")
        )
    }

    // ── The allowlist ────────────────────────────────────────────────────────

    /** How many direct wall-clock reads a file still contains. */
    private val ALLOWED: Map<String, Int> = buildMap {
        // ── body: the keyword memory port, presence and context assembly ──────
        put("body/AdaptiveContextBuilder.kt", 4)
        put("body/BodyCoordinator.kt", 9)
        put("body/MemoryPromotion.kt", 1)
        put("body/MemoryStore.kt", 6)
        put("body/PresenceMonitor.kt", 5)
        put("body/VocabularyStore.kt", 2)

        // ── the claim store ───────────────────────────────────────────────────
        put("cognition/workspace/MentalStateClaims.kt", 2)
        put("cognition/workspace/Workspace.kt", 1)

        // ── capability ───────────────────────────────────────────────────────
        put("capability/CapabilityRegistry.kt", 1)

        // ── cognitive: the turn itself ────────────────────────────────────────
        put("cognitive/CognitiveEngine.kt", 20)
        put("cognitive/CognitiveState.kt", 11)
        put("cognitive/AttentionEngine.kt", 6)
        put("cognitive/WorkingMemory.kt", 7)
        put("cognitive/ContextWindowAssembler.kt", 2)
        put("cognitive/IntentInference.kt", 2)
        put("cognitive/CognitiveContextBuilder.kt", 1)
        put("cognitive/TaskWorkingMemory.kt", 1)
        put("cognitive/capability/CapabilityExecutor.kt", 1)
        put("cognitive/capability/CapabilityFabric.kt", 1)
        put("cognitive/capability/CapabilityInvoker.kt", 1)
        put("cognitive/execution/ExecutionEngine.kt", 2)
        put("cognitive/immune/ImmuneMemory.kt", 1)
        put("cognitive/planning/PlanGraph.kt", 1)

        // ── cognitive memory: quarantine-era stores, record stamps only ───────
        put("cognitive/memory/ContinuityManager.kt", 2)
        put("cognitive/memory/ContinuityPersistence.kt", 6)
        put("cognitive/memory/ExperienceRecord.kt", 3)
        put("cognitive/memory/MemoryConflictResolver.kt", 3)
        put("cognitive/memory/MemoryConsolidator.kt", 3)
        put("cognitive/memory/MemoryContinuityAdapter.kt", 1)
        put("cognitive/memory/MemoryProvenance.kt", 9)
        put("cognitive/memory/MemoryUpdateEngine.kt", 8)

        // ── cognitive model: self/user/world model records ────────────────────
        put("cognitive/model/ModelFact.kt", 5)
        put("cognitive/model/ModelPersistence.kt", 15)
        put("cognitive/model/ModelStore.kt", 2)
        put("cognitive/model/ModelSynchronization.kt", 5)
        put("cognitive/model/ModelUpdateEngine.kt", 3)
        put("cognitive/model/SelfModel.kt", 3)
        put("cognitive/model/UserModel.kt", 1)
        put("cognitive/model/WorldModel.kt", 5)

        // ── continuity, failure, humancore ────────────────────────────────────
        put("continuity/ContinuityGate.kt", 1)
        put("failure/CircuitBreaker.kt", 1)
        put("failure/FailureSurface.kt", 1)
        put("humancore/HumanCore.kt", 3)

        // ── identity ──────────────────────────────────────────────────────────
        put("identity/PersonaTuner.kt", 1)
        put("identity/WorldModelService.kt", 5)
        put("identity/owner/PlatformBiometricPromptResultMapper.kt", 1)

        // ── latency: the timers the AC names as the honest exception ──────────
        put("latency/LatencyLayer.kt", 1)
        put("latency/SpeechEngine.kt", 1)
        put("latency/WarmupEngine.kt", 1)

        // ── memory ────────────────────────────────────────────────────────────
        put("memory/AndroidMemoryGraphStore.kt", 2)
        put("memory/BlendedMemoryRetriever.kt", 1)
        put("memory/ConsolidationDaemon.kt", 1)
        put("memory/MemoryConsolidationLoop.kt", 1)
        put("memory/MemoryGraphStore.kt", 1)
        put("memory/MemoryImportanceScorer.kt", 2)
        put("memory/provenance/AdapterManifest.kt", 2)
        put("memory/provenance/ForgetTombstone.kt", 1)

        // ── model ─────────────────────────────────────────────────────────────
        put("model/ModelManager.kt", 1)
        put("model/ModelProvider.kt", 1)
        put("model/adapters/HeuristicAdapter.kt", 1)
        put("model/adapters/LlamaCppAdapter.kt", 3)
        put("model/adapters/OllamaAdapter.kt", 3)
        put("model/adapters/RemoteJarvisAdapter.kt", 3)

        // ── the admission governor the root constructs ────────────────────────
        put("resource/ResourceGovernor.kt", 1)

        // ── the composition root and its two hosts ────────────────────────────
        put("onefriday/AndroidPlatformPorts.kt", 1)
        put("termux/TermuxJarvisServer.kt", 3)
        put("resolution/FuzzyCommandResolver.kt", 1)
        put("threads/ThreadObjects.kt", 1)
    }

    /** Why each file is allowed to keep exactly that many reads. */
    private val REASONS: Map<String, String> = buildMap {
        put("body/AdaptiveContextBuilder.kt", "context-build latency pair plus the prompt's own current-time line; the age read is the recency sort key")
        put("body/BodyCoordinator.kt", "session/model-idle bookkeeping and the Human Core exchange timestamp; no turn decision depends on the value")
        put("body/MemoryPromotion.kt", "id suffix for a promoted preference record")
        put("body/MemoryStore.kt", "keyword-memory record timestamps, id counter seed and the hours-ago recency column")
        put("body/PresenceMonitor.kt", "device presence sampling: last-interaction stamps and the away-gap read, which is a physical observation")
        put("body/VocabularyStore.kt", "vocabulary decay stamp and last-used recency")

        put("cognition/workspace/MentalStateClaims.kt", "createdAt defaults for the lossy claim encode/decode helpers; every production caller passes the organ's own instant")
        put("cognition/workspace/Workspace.kt", "default instant for a manual decay tick; the production decay clock is a parameter")

        put("capability/CapabilityRegistry.kt", "a capability's last-activated stamp, so an idle capability can be recognised")

        put("cognitive/CognitiveEngine.kt", "the four turn-trace latency pairs (a real elapsed interval, not a turn clock) plus the ids and timestamps of the records a turn writes")
        put("cognitive/CognitiveState.kt", "state-record createdAt/lastAccessed/timestamp defaults and the uncertainty-profile clock")
        put("cognitive/AttentionEngine.kt", "attention-event timestamps and the time-since-seen decay inputs")
        put("cognitive/WorkingMemory.kt", "working-memory access-order stamps and the age reads that evict an item")
        put("cognitive/ContextWindowAssembler.kt", "retrieval instant default and the assembled window's own timestamp")
        put("cognitive/IntentInference.kt", "inference-event timestamps on the record the turn keeps")
        put("cognitive/CognitiveContextBuilder.kt", "assembled-context timestamp")
        put("cognitive/TaskWorkingMemory.kt", "task-entry timestamp default")
        put("cognitive/capability/CapabilityExecutor.kt", "injected nowMs default for execution records")
        put("cognitive/capability/CapabilityFabric.kt", "injected nowMs default for fabric records")
        put("cognitive/capability/CapabilityInvoker.kt", "injected nowMs default for invocation records")
        put("cognitive/execution/ExecutionEngine.kt", "execution started-at and last-executed-at stamps")
        put("cognitive/immune/ImmuneMemory.kt", "injected nowMs default for immune records")
        put("cognitive/planning/PlanGraph.kt", "plan-node createdAt default")

        put("cognitive/memory/ContinuityManager.kt", "reconstruction stamp and the hours-ago recency read")
        put("cognitive/memory/ContinuityPersistence.kt", "persisted document clock field and per-record created/updated/accessed fallbacks on read")
        put("cognitive/memory/ExperienceRecord.kt", "experience id suffix and its timestamp defaults")
        put("cognitive/memory/MemoryConflictResolver.kt", "conflict id suffix and detection stamp")
        put("cognitive/memory/MemoryConsolidator.kt", "evaluation stamp, new-memory id suffix and the consolidation pass clock")
        put("cognitive/memory/MemoryContinuityAdapter.kt", "id suffix bridging a store item to an experience id")
        put("cognitive/memory/MemoryProvenance.kt", "provenance verification stamp, relationship/fact id suffixes and record created/updated/accessed stamps")
        put("cognitive/memory/MemoryUpdateEngine.kt", "updated-at stamps, experience timestamp and the monotonic createdAt fallback")

        put("cognitive/model/ModelFact.kt", "fact id suffix, observed/updated/last-verified stamps and the verification-event timestamp")
        put("cognitive/model/ModelPersistence.kt", "the durable model store's own record timestamps (one per persisted field group)")
        put("cognitive/model/ModelStore.kt", "world-model updated-at stamps on write")
        put("cognitive/model/ModelSynchronization.kt", "nowMs parameters defaulting to the wall clock so a sync can be given a real instant")
        put("cognitive/model/ModelUpdateEngine.kt", "sweep instant default, observation stamp and conflict id suffix")
        put("cognitive/model/SelfModel.kt", "observed-at stamps on what self-model derivation saw")
        put("cognitive/model/UserModel.kt", "last-observed-at stamp")
        put("cognitive/model/WorldModel.kt", "world/fact observed-at and updated-at stamps plus the validity read")

        put("continuity/ContinuityGate.kt", "default retrieval instant on the per-turn gate; the ONE root's clock reaches the gate's own consumers")
        put("failure/CircuitBreaker.kt", "injected nowMs default for the breaker window")
        put("failure/FailureSurface.kt", "injected nowMs default for turn-id and dedup windows")
        put("humancore/HumanCore.kt", "injected clock default, session-end instant default and the legacy session-gap observation")

        put("identity/PersonaTuner.kt", "durable adjustment record timestamp")
        put("identity/WorldModelService.kt", "asOfTime defaults: 'facts valid as of now' is a query parameter, not a stored clock")
        put("identity/owner/PlatformBiometricPromptResultMapper.kt", "biometric result timestamp on a device-only mapper")

        put("latency/LatencyLayer.kt", "the pipeline's own nowMs DEFAULT; the ONE root overrides it with the host clock, which is what InjectedClockTest proves")
        put("latency/SpeechEngine.kt", "temp-file name for a synthesized clip (uniqueness, not elapsed time)")
        put("latency/WarmupEngine.kt", "warm-up window read; a device warm-up timer")

        put("memory/AndroidMemoryGraphStore.kt", "sqlite row timestamp on insert and the FTS sync stamp on write")
        put("memory/BlendedMemoryRetriever.kt", "retrieval nowMs default; recency scoring callers pass the turn's instant")
        put("memory/ConsolidationDaemon.kt", "consolidate() nowMs default so a scheduled pass can pass its own instant")
        put("memory/MemoryConsolidationLoop.kt", "consolidation-record timestamp default")
        put("memory/MemoryGraphStore.kt", "asOfTime default on the historical-validity query")
        put("memory/MemoryImportanceScorer.kt", "nowMs defaults on score/decayedStrength; the caller supplies the turn's instant")
        put("memory/provenance/AdapterManifest.kt", "registry-state read and the JSONL line's own timestamp")
        put("memory/provenance/ForgetTombstone.kt", "tombstone forgotten-at stamp")

        put("model/ModelManager.kt", "the injected clock default; the ONE root passes the host clock, so the tier cooldown is host-time")
        put("model/ModelProvider.kt", "provider health check timestamp")
        put("model/adapters/HeuristicAdapter.kt", "the canned 'today's date' answer of a non-model fallback adapter")
        put("model/adapters/LlamaCppAdapter.kt", "request latency pair and the telemetry latency field")
        put("model/adapters/OllamaAdapter.kt", "request latency pair and the telemetry latency field")
        put("model/adapters/RemoteJarvisAdapter.kt", "request latency pair and the telemetry latency field")

        put("resource/ResourceGovernor.kt", "the admission record's last-activity stamp — written and never read for a decision")

        put("onefriday/AndroidPlatformPorts.kt", "the phone adapter's clock DEFAULT; production passes the platform's own clock")
        put("termux/TermuxJarvisServer.kt", "in-memory store defaults and the JVM host's clock value handed to the adapter")
        put("resolution/FuzzyCommandResolver.kt", "nowMs default on the recency-aware match")
        put("threads/ThreadObjects.kt", "the tracker's injected now default; a host that wants host-time decay passes it")
    }

    // ── machinery ────────────────────────────────────────────────────────────

    private fun turnPathDirs(): List<String> = listOf(
        "anchor", "body", "builder", "capability", "cloud", "cognition", "cognitive",
        "continuity", "emotion", "failure", "humancore", "identity", "language",
        "latency", "memory", "model", "onefriday", "policy", "resolution",
        "resource", "selfreconfig", "social", "termux", "threads", "trace"
    )

    /**
     * Every source file under [scope], as a path relative to [root].
     *
     * A scope entry may be a package directory (the normal case) or a single
     * file (the Android entry point). Directories are WALKED — a scope entry
     * that is a directory and is merely checked with `isFile` scans nothing at
     * all, which is how the first draft of this test managed to assert that a
     * list of 67 entries matched an empty map without noticing.
     */
    private fun sourceFilesIn(root: File, scope: List<String>): List<String> =
        scope.flatMap { name ->
            val file = File(root, name)
            when {
                file.isFile -> listOf(name)
                file.isDirectory -> file.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .map { it.toRelativeString(root) }
                    .toList()
                else -> emptyList()
            }
        }.distinct().sorted()

    private val wallClock = Regex(
        "System\\.currentTimeMillis\\(\\)|Instant\\.now\\(\\)|SystemClock\\.|" +
            "System\\.nanoTime\\(\\)|LocalDate\\.now|LocalDateTime\\.now|Date\\(\\)"
    )

    /** Direct reads in CODE lines only: KDoc, block comments and `//` are prose. */
    private fun directWallClockReads(file: File): Int =
        file.readLines().sumOf { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) 0
            else wallClock.findAll(line.substringBefore("//")).count()
        }

    /** Gradle unit tests run with workingDir = the module dir; fall back to the repo root. */
    private fun sourceRoot(): File {
        val rel = "src/main/java/com/jarvis/app"
        val candidates = listOf(
            File(rel),
            File(System.getProperty("user.dir"), rel),
            File(System.getProperty("user.dir"), "mobile/app/$rel")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate the turn-path source root. Searched user.dir=" + System.getProperty("user.dir"))
    }
}
