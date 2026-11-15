# Friday build progress

**Snapshot:** 2026-11-15  
**Working branch:** `audit/friday-offline-runtime`  
**Estimated end-state implementation progress:** **about 30%**

This is a milestone-weighted engineering estimate, not a line-count metric and not a claim that 30% of the app is production-ready. The project has 16 equally weighted outcome milestones. Each score estimates how much of that milestone's required outcome is implemented, not merely planned. The rounded total is 30%.

| # | Outcome milestone | Estimated completion | Remaining proof / work |
|---:|---|---:|---|
| 1 | Independent Android foundation | 55% | Green full CI, lifecycle/process recovery validation, final release hardening |
| 2 | Persistent identity, history and experience | 45% | Full restart/model-replacement continuity proof and protected recovery |
| 3 | Lightweight English intelligence and model routing | 40% | Real on-device inference benchmarks, quality-gated admission wired into serving |
| 4 | Voice, hearing and interruption | 40% | Reliable end-to-end streaming, interruption and full phone-command coverage |
| 5 | Continuous presence and environmental awareness | 35% | Background/foreground continuity and real-device camera/presence validation |
| 6 | Nano Liquid Symbiosis host adaptation | 35% | Calibrated resource learning and measured quality-preserving model adaptation |
| 7 | Experience-driven developmental learning | 25% | Demonstrate durable behaviour changes caused by real experience, not just stored memories |
| 8 | Predictive cognition / JEPA-inspired mechanisms | 20% | Mature prediction-error learning and evidence of useful low-cost predictive behaviour |
| 9 | Self-model and individuality | 30% | Calibrated self-knowledge and stable preference/strategy evolution |
| 10 | Capability Foundry | 35% | Broader safe tool synthesis, outcome learning and stronger validation coverage |
| 11 | Native phone capabilities | 40% | Complete voice-to-action coverage, permissions, device-specific reliability |
| 12 | Autonomous self-development | 20% | Source index is being added; still need a persistent objective queue and guarded tool-improvement cycle |
| 13 | Security, provenance and rollback | 40% | Full self-modification authorization, signed native update and rollback proof |
| 14 | Life tests | 20% | Full replay/disable/restart/model-replacement suite demonstrating genuine learning |
| 15 | Real-device validation on the target phone | 0% | No verified test evidence from the owner's Realme 9 Pro 5G yet |
| 16 | First complete Friday release | 0% | Release gate intentionally not started; no interim APK handoff |

## Implemented in this development session

- Friday-owned phone-action decision contract with optional TypeSafe Jev provider and local fail-closed policy.
- Persistent, resource-gated self-study scheduled with Android WorkManager.
- Self-study reports architecture reachability, fixed invariants, declared missing capabilities, known limitations, host observations and organ failure evidence.
- Build-time source symbol index generation: source paths, package names, declaration names and a source revision digest are packaged as metadata. The index does not ship Kotlin source text.
- Self-study now records the source-index revision and counts.
- Removed Termux-named JVM fixture paths and migrated the affected integration tests to a test-only JVM composition harness. Android remains the only production host.

## Release blockers

1. The latest full CI run must finish successfully; earlier runs failed first on stale Termux fixture references, then on the newly added Gradle source-index task syntax. Those errors have been patched, but the newest run is not yet verified in this snapshot.
2. A green CI build does not prove the app works on the target phone.
3. The source index enables metadata-level self-inspection, not arbitrary Kotlin source comprehension or self-rewriting.
4. TypeSafe Jev remains an optional hosted decision provider. Friday owns the local decision interface and policy; she has not recreated Jev's proprietary model.
5. Native app changes still require a signed, verified Android update. Friday must not silently replace her installed package.

## Percentage method

Each of the 16 milestones contributes 6.25 percentage points to the overall estimate. The individual completion values above sum to 480 percentage points; divided by 16 gives 30%. Scores are deliberately partial where code exists but the acceptance proof is missing. Physical-device validation and the first complete release remain at zero.
