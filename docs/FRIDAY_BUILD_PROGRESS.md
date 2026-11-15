# Friday build progress

**Snapshot:** 2026-11-16  
**Working branch:** `audit/friday-offline-runtime`  
**Estimated end-state implementation progress:** **about 31% (provisional)**

This is a milestone-weighted engineering estimate, not a line-count metric and not a claim that 30% of the app is production-ready. The project has 16 equally weighted outcome milestones. Each score estimates how much of that milestone's required outcome is implemented, not merely planned. The rounded total is 31%. This score is reduced to reflect the unverified and vision-conflicting in-app self-build lane.

| # | Outcome milestone | Estimated completion | Remaining proof / work |
|---:|---|---:|---|
| 1 | Independent Android foundation | 55% | Green full CI, lifecycle/process recovery validation, final release hardening |
| 2 | Persistent identity, history and experience | 45% | Full restart/model-replacement continuity proof and protected recovery |
| 3 | Lightweight English intelligence and model routing | 40% | Real on-device inference benchmarks, quality-gated admission wired into serving |
| 4 | Voice, hearing and interruption | 50% | Reliable end-to-end streaming, interruption, multi-step phone workflows and real-device validation |
| 5 | Continuous presence and environmental awareness | 38% | Background/foreground continuity and real-device camera/presence validation |
| 6 | Nano Liquid Symbiosis host adaptation | 40% | Calibrated resource learning and measured quality-preserving model adaptation |
| 7 | Experience-driven developmental learning | 25% | Demonstrate durable behaviour changes caused by real experience, not just stored memories |
| 8 | Predictive cognition / JEPA-inspired mechanisms | 20% | Mature prediction-error learning and evidence of useful low-cost predictive behaviour |
| 9 | Self-model and individuality | 30% | Calibrated self-knowledge and stable preference/strategy evolution |
| 10 | Capability Foundry | 45% | Broader safe tool synthesis, outcome learning and stronger validation coverage |
| 11 | Native phone capabilities | 50% | Multi-step voice workflows, broader Android APIs, permissions and device-specific reliability |
| 12 | Autonomous self-development | 15% | Phone-side evidence collection exists, but repository-native development request transport, external worker orchestration, durable synchronization, independent Vision gate and deployment feedback loop are not implemented. The current in-app builder path conflicts with the owner's explicit architecture and must be removed from production |
| 13 | Security, provenance and rollback | 40% | Full self-modification authorization, signed native update and rollback proof |
| 14 | Life tests | 20% | Full replay/disable/restart/model-replacement suite demonstrating genuine learning |
| 15 | Real-device validation on the target phone | 0% | No verified test evidence from the owner's Realme 9 Pro 5G yet |
| 16 | First complete Friday release | 0% | Release gate intentionally not started; no interim APK handoff |

## Implemented in this development session

- Friday remains the intelligence; Jev is an optional TypeSafe cloud decision faculty. Friday's own existing ModelManager now plans one bounded Android action from the spoken request and a privacy-minimized live accessibility snapshot. Screen context stays on-device and is never sent to Jev or a remote phone planner.
- Jev evaluates the proposed action through Choice, Score and Noul when configured. Friday's local policy remains authoritative: routine navigation may execute after Jev approval; consequential targets and sensitive text still require spoken confirmation. Typed text payloads are redacted before Jev evaluation.
- The phone-action path now accepts Friday-model plans as well as deterministic phrases, requires exact screen evidence, refuses ambiguous duplicate controls rather than tapping the first match, serializes actions, and contains Android execution failures. Arbitrary UI taps are medium-risk and require confirmation when Jev is unavailable. It deliberately executes one bounded action at a time; multi-step workflows are not yet complete.
- Added planner and policy tests for screen-target evidence, focused editable fields, local-only screen context, Jev approval, confirmation, cancellation, expiry, natural spoken phrasing, overlapping commands and contained Android execution failures.
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
- The source index now includes internal package-import edges and file paths, not just symbol counts. Self-study passes this structural evidence, runtime wiring and measured host/organ data to Friday's existing local llama.cpp model for a short reflection.
- Self-study reflections are persisted locally. The current head also contains a self-study-to-in-app-ToolBuilder bridge; this is now explicitly rejected by the owner's 2026-11-16 correction. It is not the intended self-development architecture and must be removed from production. Repository-side development request transport and external worker execution remain unimplemented.
- PresenceEngine now uses PRESENT as Friday's canonical base state. Processing and tool use no longer become a THINKING presence mode; startup no longer transitions through ASLEEP/WAKING, and stale signals return to PRESENT. BodyState IDLE/THINKING remain internal event-pipeline stages, not existence states.
- The on-demand reasoning organ retains a resource grace period while the resident anchor remains loaded; the resource cooldown is not a Friday idle mode.
- Removed the Termux runtime-selection entry and the Termux backend test stub. The production app remains Android-native; no Termux host or setup path is required.
- CI now cancels superseded branch builds and prints exact JUnit failures, making the next regression easier to diagnose.
- Candidate synthesis failures are retained in evolution diagnostics, and initial capability creation synthesizes directly before later mutation cycles. This change is under CI verification.

## Release blockers

1. Commit `6a64f121` passed Android debug assembly and the baseline unit-test task. A later run exposed one failing ToolBuilder integration test; candidate-generation diagnostics and a simpler first-build path have been added. The current branch head still requires a fresh green build and test run.
2. A green CI build does not prove the app works on the target phone.
3. The source index enables structural self-inspection (file paths, declarations and package imports), not arbitrary Kotlin source comprehension or self-rewriting. The local model's reflection is bounded to this evidence.
4. TypeSafe Jev remains an optional hosted decision faculty, not a downloadable model or Friday's primary intelligence. Friday calls the System One API for typed Choice, Score and Noul evaluations; Friday's local model forms the action plan and the Android body executes it under local policy.
5. Native app changes still require a signed, verified Android update. Friday must not silently replace her installed package.
6. The in-app ToolBuilder and self-study-to-builder bridge are architectural blockers under the owner's 2026-11-16 instruction. Replace them with a durable development-request handoff to connected repository workers; keep all source edits, code execution, tests, builds and artifact signing outside Android.

## Percentage method

Each of the 16 milestones contributes 6.25 percentage points to the overall estimate. The individual completion values above must be recalculated after the self-development milestone correction; the 31% headline is a provisional judgment, not an exact sum. Scores are deliberately partial where code exists but the acceptance proof is missing. Physical-device validation and the first complete release remain at zero.
