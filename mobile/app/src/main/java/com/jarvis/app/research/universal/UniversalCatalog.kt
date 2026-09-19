package com.jarvis.app.research.universal

import com.jarvis.app.research.ResearchDomain

/**
 * The built-in universal algorithm catalog (§7). Each entry records:
 *
 *   DOMAIN → MECHANISM → PROBLEM SOLVED → MECHANISM PROPERTIES →
 *   TRANSFERABLE PRINCIPLE → JARVIS CANDIDATE IMPLEMENTATION
 *
 * This is a static knowledge base that ships with the APK — dormant bytes, no
 * background research. It demonstrates the *abstraction* the Universal
 * Research Engine performs; live web/academic search providers plug in behind
 * the same catalog contract later. Biological analogies are recorded as
 * HYPOTHESES — each carries its supporting source facts and an unverified
 * transfer, per §7.
 */
object UniversalCatalog {

    data class CatalogEntry(
        val mechanismName: String,
        val domain: ResearchDomain,
        val problemSolved: String,
        val mechanismProperties: List<String>,
        val transferablePrinciple: String,
        val jarvisImplementation: String,
        val evidence: ResearchEvidence
    )

    private val entries: List<CatalogEntry> = listOf(
        CatalogEntry(
            mechanismName = "hippocampal indexing",
            domain = ResearchDomain.NEUROSCIENCE,
            problemSolved = "How brains store a lifetime of experiences without a central directory",
            mechanismProperties = listOf("pointer-store", "sparse-addressable", "associative-recall"),
            transferablePrinciple = "Keep a small fast index of pointers; dereference the heavy payload only on recall",
            jarvisImplementation = "MEMORY: index keys (turns) point to compressed payloads; recall is an index lookup then lazy decompression",
            evidence = ResearchEvidence(
                id = "mech_hippocampal_indexing",
                status = EvidenceStatus.ABSTRACTION,
                supportingFacts = listOf(
                    SourceFact("fact_hc1", "neuroscience", "Hippocampus indexes episodic memory via place cells; neocortex holds content", "textbook: memory consolidation")
                ),
                confidence = 0.55,
                caveats = listOf("analogy is about indexing, not about neural substrate fidelity")
            )
        ),
        CatalogEntry(
            mechanismName = "immune memory",
            domain = ResearchDomain.IMMUNE_SYSTEMS,
            problemSolved = "How the immune system retains specific responses to prior threats",
            mechanismProperties = listOf("clonal-selection", "affinity-maturation", "durable-retention"),
            transferablePrinciple = "Retain a small population of high-affinity 'memory' entries; boost the best match on re-exposure",
            jarvisImplementation = "MEMORY: frequently-hit fact keys are promoted to a durable recall tier; cold keys decay",
            evidence = ResearchEvidence(
                id = "mech_immune_memory",
                status = EvidenceStatus.HYPOTHESIS,
                supportingFacts = listOf(
                    SourceFact("fact_im1", "immunology", "Memory B/T cells persist after primary response and react faster on re-challenge", "textbook: immunology")
                ),
                confidence = 0.5,
                caveats = listOf("cost model differs: immune cells are cheap to retain, KB entries may not be")
            )
        ),
        CatalogEntry(
            mechanismName = "ant pheromone trails",
            domain = ResearchDomain.INSECTS,
            problemSolved = "How ant colonies find short paths without a leader or global map",
            mechanismProperties = listOf("stigmergy", "positive-feedback", "evaporating-trail"),
            transferablePrinciple = "Route preference accumulates where prior success is recent; let preference decay so stale routes lose",
            jarvisImplementation = "ROUTING: capability-router cost tiers get a recency boost; a provider that stops succeeding decays out of the cheap tier",
            evidence = ResearchEvidence(
                id = "mech_pheromone_routing",
                status = EvidenceStatus.HYPOTHESIS,
                supportingFacts = listOf(
                    SourceFact("fact_ph1", "entomology", "Ants reinforce trails with pheromone; evaporation prevents convergence on stale paths", "textbook: collective behaviour")
                ),
                confidence = 0.6,
                caveats = listOf("needs a decay policy so a genuinely-better provider can displace a legacy one")
            )
        ),
        CatalogEntry(
            mechanismName = "DNS + CDN routing",
            domain = ResearchDomain.NETWORKING,
            problemSolved = "How the internet routes a name to the nearest healthy server",
            mechanismProperties = listOf("hierarchy", "caching", "health-aware-replica"),
            transferablePrinciple = "Resolve capability → cheapest healthy replica; cache the resolution; fail over on health change",
            jarvisImplementation = "NERVOUS: registry.provides() ordered by health then cost; route cache with TTL; failover on failure",
            evidence = ResearchEvidence(
                id = "mech_cdn_routing",
                status = EvidenceStatus.SOURCE_FACT,
                supportingFacts = listOf(
                    SourceFact("fact_cdn1", "networking", "DNS resolvers and CDNs route requests to the closest healthy node and cache the mapping", "RFC/running systems")
                ),
                confidence = 0.85,
                caveats = listOf("direct transfer; caching must respect capability versioning")
            )
        ),
        CatalogEntry(
            mechanismName = "radix / trie index",
            domain = ResearchDomain.DATABASES,
            problemSolved = "Prefix-structured keys retrieved in one pass over the shared prefix",
            mechanismProperties = listOf("prefix-sharing", "ordered-keys", "low-fanout"),
            transferablePrinciple = "Group lookups by shared prefix so related work shares one traversal",
            jarvisImplementation = "MEMORY: episodic queries sharing a topic/prefix batch into one recall pass",
            evidence = ResearchEvidence(
                id = "mech_trie_index",
                status = EvidenceStatus.SOURCE_FACT,
                supportingFacts = listOf(
                    SourceFact("fact_trie1", "databases", "Tries retrieve a key in O(k) where k is key length, independent of store size", "CS literature")
                ),
                confidence = 0.8,
                caveats = listOf("gain shrinks when the store is small enough to scan")
            )
        ),
        CatalogEntry(
            mechanismName = "setpoint homeostasis",
            domain = ResearchDomain.CONTROL_THEORY,
            problemSolved = "How a system holds a target value against disturbances",
            mechanismProperties = listOf("negative-feedback", "error-signal", "setpoint"),
            transferablePrinciple = "Compare measured state to a setpoint; correct by the error, not by blind action",
            jarvisImplementation = "RESOURCE: resource-governor admission compares used vs budget and rejects when the error breaches the setpoint",
            evidence = ResearchEvidence(
                id = "mech_homeostasis",
                status = EvidenceStatus.SOURCE_FACT,
                supportingFacts = listOf(
                    SourceFact("fact_homeo1", "control theory", "PID-style feedback holds a setpoint against disturbance", "control theory")
                ),
                confidence = 0.75,
                caveats = listOf("already partially implemented in ResourceGovernor")
            )
        ),
        CatalogEntry(
            mechanismName = "cache eviction by recency",
            domain = ResearchDomain.OPERATING_SYSTEMS,
            problemSolved = "Which memory pages to evict when the working set exceeds RAM",
            mechanismProperties = listOf("recency-sorted", "cheap-miss-reload", "high-hit-rate"),
            transferablePrinciple = "When capacity is bounded, drop the least-recently-used payload; reload is cheap",
            jarvisImplementation = "MUTANT: candidate workspaces that are PRESERVED but not promoted are LRU-evicted to disk",
            evidence = ResearchEvidence(
                id = "mech_lru",
                status = EvidenceStatus.SOURCE_FACT,
                supportingFacts = listOf(
                    SourceFact("fact_lru1", "operating systems", "LRU eviction has near-optimal hit rate under locality", "OS literature")
                ),
                confidence = 0.8,
                caveats = listOf("needs locality assumption; candidates are touched rarely")
            )
        ),
        // ── VOICE capability mechanisms (seed: Voicebox archive study, 2026-08-10) ──
        CatalogEntry(
            mechanismName = "process address-space isolation",
            domain = ResearchDomain.OPERATING_SYSTEMS,
            problemSolved = "How a crash or memory corruption in one component stops short of taking down the whole host",
            mechanismProperties = listOf("separate-address-space", "kernel-enforced-boundary", "contained-fault"),
            transferablePrinciple = "Run the crash-prone component in its own process; a fault there kills only that process, never the host",
            jarvisImplementation = "VOICE: native voice engines (Vosk/sherpa/onnx/Piper) run in a dedicated :voice process, never in the app process — the G1 fix for the retired voice pipeline (native mutex crash, d0735ac)",
            evidence = ResearchEvidence(
                id = "mech_voice_process_isolation",
                status = EvidenceStatus.SOURCE_FACT,
                supportingFacts = listOf(
                    SourceFact("fact_voi1", "operating systems", "A fault in one process cannot corrupt another; the kernel guarantees the boundary", "OS textbook"),
                    SourceFact("fact_voi2", "voicebox", "Voicebox runs all native TTS/STT inference in a separate FastAPI process, never in the desktop host", "voicebox archive study")
                ),
                confidence = 0.9,
                caveats = listOf("process boundary costs an IPC round-trip per request; Android Binder keeps this cheap")
            )
        ),
        CatalogEntry(
            mechanismName = "microkernel driver server",
            domain = ResearchDomain.OPERATING_SYSTEMS,
            problemSolved = "How a crashing device driver avoids taking down the kernel",
            mechanismProperties = listOf("user-space-driver", "supervised-restart", "isolated-failure-domain"),
            transferablePrinciple = "Put the highest-risk component behind a supervisor that detects its death and restarts it from a known recipe",
            jarvisImplementation = "VOICE: app-side VoiceOrganismHost binds the :voice service, watches Binder linkToDeath, and rebuilds the voice process from its environment recipe on death",
            evidence = ResearchEvidence(
                id = "mech_voice_supervised_restart",
                status = EvidenceStatus.ABSTRACTION,
                supportingFacts = listOf(
                    SourceFact("fact_voi3", "operating systems", "QNX/seL4 run drivers as user-space servers restarted by a supervisor", "microkernel literature"),
                    SourceFact("fact_voi4", "web browsers", "Chromium restarts a crashed renderer without losing the browser", "Chromium architecture")
                ),
                confidence = 0.8,
                caveats = listOf("supervisor must be simple and uncrashable itself")
            )
        ),
        CatalogEntry(
            mechanismName = "single-writer serialization",
            domain = ResearchDomain.DATABASES,
            problemSolved = "How concurrent writers to one shared hot resource avoid contention and corruption",
            mechanismProperties = listOf("one-at-a-time", "priority-not-guaranteed", "simple-correct"),
            transferablePrinciple = "Serialize access to a contended resource with a single queue rather than locking; add priority on top",
            jarvisImplementation = "VOICE: VoiceScheduler serializes model-bound inference inside the voice process — one native inference at a time, prioritized by capability role (wake-word > STT > TTS reply > background transcribe)",
            evidence = ResearchEvidence(
                id = "mech_voice_serialized_inference",
                status = EvidenceStatus.ABSTRACTION,
                supportingFacts = listOf(
                    SourceFact("fact_voi5", "databases", "Single-writer serialization prevents contention on a hot resource", "DBMS literature"),
                    SourceFact("fact_voi6", "voicebox", "Voicebox's generation queue runs one TTS inference at a time to avoid GPU contention", "voicebox archive study")
                ),
                confidence = 0.8,
                caveats = listOf("parallelizes poorly across cores; fine when the contended resource is one model")
            )
        ),
        CatalogEntry(
            mechanismName = "content-addressed cache",
            domain = ResearchDomain.DATABASES,
            problemSolved = "How to cache an expensive derived value such that any change to its inputs invalidates it automatically",
            mechanismProperties = listOf("hash-keyed", "automatic-invalidation", "no-manual-eviction-state"),
            transferablePrinciple = "Key the cache by the content of its inputs; a changed input is automatically a new key — stale entries become unreachable",
            jarvisImplementation = "VOICE: VoiceCache stores the encoded voice prompt keyed by MD5(reference-audio bytes + reference text); changed samples never need explicit invalidation",
            evidence = ResearchEvidence(
                id = "mech_voice_content_cache",
                status = EvidenceStatus.ABSTRACTION,
                supportingFacts = listOf(
                    SourceFact("fact_voi7", "voicebox", "Voicebox keys voice prompts by MD5(audio bytes + reference text) and shares one encoding across Qwen-variant engines", "voicebox archive study")
                ),
                confidence = 0.85,
                caveats = listOf("hash collisions are theoretically possible; store a size bound")
            )
        ),
        CatalogEntry(
            mechanismName = "sentence-boundary segmentation",
            domain = ResearchDomain.ENGINEERING,
            problemSolved = "How to split a long text into pieces a fixed-context model can synthesize without breaking meaning",
            mechanismProperties = listOf("sentence-boundary-first", "abbreviation-aware", "atomic-tags"),
            transferablePrinciple = "Segment at natural linguistic boundaries, respecting abbreviations and atomic markup, before feeding each piece to the model",
            jarvisImplementation = "VOICE: chunked long-form synthesis splits on sentence-end (.!? skipping Dr./Mr./etc.), then clause, then whitespace, then hard cut; short text uses the single-shot fast path",
            evidence = ResearchEvidence(
                id = "mech_voice_chunking",
                status = EvidenceStatus.ABSTRACTION,
                supportingFacts = listOf(
                    SourceFact("fact_voi8", "voicebox", "Voicebox splits text into sentence-boundary chunks and concatenates with crossfade", "voicebox archive study")
                ),
                confidence = 0.85,
                caveats = listOf("code-switch boundaries and CJK punctuation need language-aware rules")
            )
        ),
        CatalogEntry(
            mechanismName = "detect-then-retransmit",
            domain = ResearchDomain.NETWORKING,
            problemSolved = "How a receiver recovers from corrupt or partial payloads",
            mechanismProperties = listOf("error-detection", "smaller-retry", "fail-loud"),
            transferablePrinciple = "Detect the corruption signature, retry with a smaller payload, and surface persistent failure rather than delivering corrupt data",
            jarvisImplementation = "VOICE: runaway-output detection (speech → ≥2s silence → more speech, the EOS-miss/codec-noise signature) retries the chunk split in half; persistent runaway fails rather than playing corrupt audio",
            evidence = ResearchEvidence(
                id = "mech_voice_runaway",
                status = EvidenceStatus.ABSTRACTION,
                supportingFacts = listOf(
                    SourceFact("fact_voi9", "communications", "Detect-then-retransmit recovers from corruption on noisy channels", "networking"),
                    SourceFact("fact_voi10", "voicebox", "Voicebox's has_tts_runaway retries persistent runaway chunks as smaller chunks", "voicebox archive study")
                ),
                confidence = 0.8,
                caveats = listOf("adds retry latency; only enable per-provider via capability metadata")
            )
        ),
        CatalogEntry(
            mechanismName = "demand paging",
            domain = ResearchDomain.OPERATING_SYSTEMS,
            problemSolved = "How to keep a large address space without paying for all of it resident at once",
            mechanismProperties = listOf("lazy-load", "evict-on-pressure", "fault-on-touch"),
            transferablePrinciple = "Load the heavyweight resource only when first touched; release it when idle; reload on demand",
            jarvisImplementation = "VOICE: VoiceModelManager lazy-loads models on first use, unloads on suspend, and size-switches between 0.6B/1.7B — the voice process holds no idle model RAM",
            evidence = ResearchEvidence(
                id = "mech_voice_lazy_models",
                status = EvidenceStatus.ABSTRACTION,
                supportingFacts = listOf(
                    SourceFact("fact_voi11", "operating systems", "Demand paging loads pages on first touch and evicts under pressure", "OS textbook"),
                    SourceFact("fact_voi12", "voicebox", "Voicebox loads TTS models lazily on first generate and unloads with GPU cache clear", "voicebox archive study")
                ),
                confidence = 0.85,
                caveats = listOf("first-request latency must be budgeted or pre-warmed")
            )
        ),
        CatalogEntry(
            mechanismName = "object capability metadata",
            domain = ResearchDomain.OPERATING_SYSTEMS,
            problemSolved = "How a caller picks a service without hard-coding which one to use",
            mechanismProperties = listOf("declarative-attributes", "capability-advertisement", "selection-by-facts"),
            transferablePrinciple = "Providers advertise their capabilities as data (languages, quality, failure modes); selection is a query over that data, not an if/else",
            jarvisImplementation = "VOICE: CapabilityRegistry capabilities carry needsTrim/retriesRunaway/supportsInstruct/perLanguageQuality/supportsCloning; VoiceRouter picks the cheapest valid provider per utterance",
            evidence = ResearchEvidence(
                id = "mech_voice_provider_metadata",
                status = EvidenceStatus.ABSTRACTION,
                supportingFacts = listOf(
                    SourceFact("fact_voi13", "operating systems", "Capability-based security lets an object declare exactly what it can do", "capability literature"),
                    SourceFact("fact_voi14", "voicebox", "Voicebox's ModelConfig declares languages, needs_trim, retries_runaway, supports_instruct per engine", "voicebox archive study")
                ),
                confidence = 0.8,
                caveats = listOf("metadata must be benchmark-verified, not hand-claimed")
            )
        ),
        CatalogEntry(
            mechanismName = "voiceprint template encoding",
            domain = ResearchDomain.NEUROSCIENCE,
            problemSolved = "How a small reference sample captures a speaker's identity well enough to regenerate it",
            mechanismProperties = listOf("compact-embedding", "reference-conditional", "one-shot"),
            transferablePrinciple = "Encode a compact conditioning signal from reference samples; the generative model reconstructs the identity from that signal",
            jarvisImplementation = "VOICE: zero-shot voice cloning encodes reference audio (+transcript) into a voice prompt the TTS provider conditions on; multi-sample voices concatenate audio and text into a richer conditioning signal",
            evidence = ResearchEvidence(
                id = "mech_voice_cloning",
                status = EvidenceStatus.ABSTRACTION,
                supportingFacts = listOf(
                    SourceFact("fact_voi15", "voicebox", "Voicebox clones voices from reference audio via engine-specific prompt encoders", "voicebox archive study")
                ),
                confidence = 0.7,
                caveats = listOf("cloning quality is provider-dependent; JARVIS must gate it behind provider supportsCloning metadata")
            )
        ),
        CatalogEntry(
            mechanismName = "prosody direction",
            domain = ResearchDomain.OTHER,
            problemSolved = "How the same words are delivered with different emotional character",
            mechanismProperties = listOf("affect-parameterized", "pace-and-pitch", "actor-interpretation"),
            transferablePrinciple = "A text layer decides what is said and how it should feel; a delivery layer maps that to rate, pitch, and voice selection",
            jarvisImplementation = "VOICE: HumanCore Expression emits a structured affect/register per utterance → ProsodyHint → the voice organism maps it to engine choice, speech rate, pitch, and optional SSML markup",
            evidence = ResearchEvidence(
                id = "mech_voice_prosody_bridge",
                status = EvidenceStatus.HYPOTHESIS,
                supportingFacts = listOf(
                    SourceFact("fact_voi16", "linguistics", "Prosody — intonation, stress, rhythm — carries affective meaning", "linguistics"),
                    SourceFact("fact_voi17", "jarvis", "HumanCore ProsodyHint is currently a placeholder that is always null", "jarvis working tree")
                ),
                confidence = 0.65,
                caveats = listOf("provider support for paralinguistic markup varies; gate behind capability metadata")
            )
        )
    )

    /** Search the catalog by domain and/or problem keywords. */
    fun search(problemStatement: String, domains: Set<ResearchDomain> = emptySet()): List<CatalogEntry> {
        val tokens = problemStatement.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return entries.filter { entry ->
            (domains.isEmpty() || entry.domain in domains) &&
                (tokens.isEmpty() || keywords(entry).any { kw -> tokens.any { it in kw } })
        }
    }

    fun byDomain(domain: ResearchDomain): List<CatalogEntry> = entries.filter { it.domain == domain }

    fun allEntries(): List<CatalogEntry> = entries

    private fun keywords(entry: CatalogEntry): Set<String> {
        val text = listOf(
            entry.mechanismName, entry.problemSolved, entry.transferablePrinciple, entry.jarvisImplementation
        ).joinToString(" ").lowercase()
        return text.split(Regex("[^a-z0-9]+")).filter { it.length > 3 }.toSet()
    }
}
