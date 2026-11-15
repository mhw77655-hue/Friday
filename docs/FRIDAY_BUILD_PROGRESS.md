# Friday build progress

**Snapshot:** 2026-11-15  
**Working branch:** `audit/friday-offline-runtime`  
**Estimated end-state implementation progress:** **about 32%**

This is a milestone-weighted engineering estimate, not a line-count metric and not a claim that 30% of the app is production-ready. The project has 16 equally weighted outcome milestones. Each score estimates how much of that milestone's required outcome is implemented, not merely planned. The rounded total is 32%.

| # | Outcome milestone | Estimated completion | Remaining proof / work |
|---:|---|---:|---|
| 1 | Independent Android foundation | 55% | Green full CI, lifecycle/process recovery validation, final release hardening |
| 2 | Persistent identity, history and experience | 45% | Full restart/model-replacement continuity proof and protected recovery |
| 3 | Lightweight English intelligence and model routing | 40% | Real on-device inference benchmarks, quality-gated admission wired into serving |
| 4 | Voice, hearing and interruption | 45% | Reliable end-to-end streaming, interruption and full phone-command coverage |
| 5 | Continuous presence and environmental awareness | 35% | Background/foreground continuity and real-device camera/presence validation |
| 6 | Nano Liquid Symbiosis host adaptation | 40% | Calibrated resource learning and measured quality-preserving model adaptation |
| 7 | Experience-driven developmental learning | 25% | Demonstrate durable behaviour changes caused by real experience, not just stored memories |
| 8 | Predictive cognition / JEPA-inspired mechanisms | 20% | Mature prediction-error learning and evidence of useful low-cost predictive behaviour |
| 9 | Self-model and individuality | 30% | Calibrated self-knowledge and stable preference/strategy evolution |
| 10 | Capability Foundry | 45% | Broader safe tool synthesis, outcome learning and stronger validation coverage |
| 11 | Native phone capabilities | 45% | Complete voice-to-action coverage, permissions, device-specific reliability |
| 12 | Autonomous self-development | 22% | Source index generation and runtime reading are implemented; still need a persistent objective queue and guarded tool-improvement cycle |
| 13 | Security, provenance and rollback | 40% | Full self-modification authorization, signed native update and rollback proof |
| 14 | Life tests | 20% | Full replay/disable/restart/model-replacement suite demonstrating genuine learning |
| 15 | Real-device validation on the target phone | 0% | No verified test evidence from the owner's Realme 9 Pro 5G yet |
| 16 | First complete Friday release | 0% | Release gate intentionally not started; no interim APK handoff |

## Implemented in this development session

- Friday-owned phone-action decision contract with optional TypeSafe Jev provider and local policy. Jev evaluates consequential proposed actions when configured, but Friday's local confirmation gate still controls screen taps and text entry. Low-risk allowlisted commands remain usable when Jev is offline.
- Connected speech-recognition results to a persistent phone-command orchestrator. It supports natural polite command phrasing, short-lived spoken confirmation, cancellation, and invalidation of stale confirmation requests.
- Added tests for local fallback, Jev rejection, consequential-action evaluation, confirmation, cancellation, expiry, natural spoken phrasing, overlapping commands and contained Android execution failures.
- Reused one process-scoped Jev HTTP client so requests share the connection pool instead of constructing a new client for each command.
- Wired the production ToolBuilder to Friday's existing ModelManager through LlmSpecSynthesizer. A custom typed capability contract now lets the model synthesize tools outside the five deterministic examples.
- Promoted generated tools now register into the same CapabilityFabric used by Friday's primary CognitiveEngine; rollback unregisters the capability. Integration coverage invokes a generated word_count tool through both the builder's legacy route and the primary cognitive fabric.
- Promotion rollback now snapshots/restores the full genome capability index rather than clearing unrelated capabilities. The archive persists complete genome contracts/provenance in a dedicated atomic store, loads genome metadata on startup, and no longer writes into Human Core dialogue JSONL. Archived metadata without an executable artifact triggers recovery/rebuild instead of being falsely treated as a live capability.
- Research-first is now opt-in. When requested, evidence-labeled research hypotheses are passed into the synthesis constraints; a verified-research requirement blocks promotion unless a VERIFIED mechanism is actually present.
- Candidate synthesis now propagates coroutine cancellation rather than swallowing it as an ordinary failed candidate.
- Persistent, resource-gated self-study scheduled with Android WorkManager.
- Self-study reports architecture reachability, fixed invariants, declared missing capabilities, known limitations, host observations and organ failure evidence.
- Self-study now persists the actual learned host profile: minimum available memory, CPU EMA, thermal/low-memory sample counts, additional memory reserve, maximum process PSS, minimum battery, liquid-symbiosis generation/prediction error, and each organ's learned strategy.
- Build-time source symbol index generation: source paths, package names, declaration names and a source revision digest are packaged as metadata. The index does not ship Kotlin source text.
- Self-study now records the source-index revision and counts.
- Removed Termux-named JVM fixture paths and migrated the affected integration tests to a test-only JVM composition harness. Android remains the only production host.

## Release blockers

1. Functional integration commit `24e236f0` passed Android debug assembly and the full baseline unit-test task in CI. Subsequent changes on the branch only corrected comments/documentation; their CI runs are still completing.
2. A green CI build does not prove the app works on the target phone.
3. The source index enables metadata-level self-inspection, not arbitrary Kotlin source comprehension or self-rewriting.
4. TypeSafe Jev remains an optional hosted decision faculty, not a downloadable model or Friday's primary intelligence. Friday calls the System One API for typed Choice, Score and Noul evaluations; the local Android action layer remains responsible for execution and confirmation.
5. Native app changes still require a signed, verified Android update. Friday must not silently replace her installed package.
6. Promoted DSL tools are currently registered in process-local registries; complete executable-artifact persistence and verified startup rehydration are still required for them to survive process death.

## Percentage method

Each of the 16 milestones contributes 6.25 percentage points to the overall estimate. The individual completion values above sum to 507 percentage points; divided by 16 gives 32%. Scores are deliberately partial where code exists but the acceptance proof is missing. Physical-device validation and the first complete release remain at zero.
