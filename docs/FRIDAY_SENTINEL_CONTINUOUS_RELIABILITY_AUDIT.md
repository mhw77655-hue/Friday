# Friday Sentinel — Continuous Reliability and Weakness Detection

**Status:** implementation contract; scheduled full-suite execution is enabled by the accompanying workflow change. The complete Sentinel architecture described here is not yet delivered. Each detector must be marked implemented only when code, tests, and CI evidence exist.

## Mission

Friday Sentinel is an independent quality-assurance subsystem. Its only job is to discover weaknesses, fragile assumptions, broken integrations, silent failures, regressions, and evidence gaps across Friday's source, build, runtime, persistent data, update path, and released artifact.

Sentinel is not Friday's product brain, not a self-modification authority, and not a green-check generator. It must not edit production code, waive a failing gate, approve its own repairs, change signing/update trust, or declare a capability healthy because a class exists or a build succeeds.

## Non-negotiable rules

1. **Inspect the whole system, not just changed files.** Track every production package, capability, data store, external integration, permission, background worker, service, and update component.
2. **Trace real wiring.** Distinguish declared, source-present, production-reachable, integration-tested, device-verified, and released. A lexical call graph is only a lead; reflection, DI, callbacks, generated code, JNI, and dynamic dispatch require stronger evidence.
3. **Test postconditions.** An API call, click, request, build, or installer callback is not proof the intended state changed.
4. **Test failures and recovery.** Every critical operation needs success, invalid-input, permission-denied, timeout, process-death, storage-full/corruption, network-loss, and recovery behavior where applicable.
5. **Protect continuity.** Updates must prove package identity, signing identity, source provenance, persistent data survival, schema migration, and ability to launch and operate after replacement.
6. **Keep the verifier independent.** The agent or code that proposes a fix cannot be the sole judge of that fix. A separate deterministic gate and regression test must verify it.
7. **Fail closed on missing evidence.** Missing test reports, skipped required tests, stale commits, unknown signing identity, missing device evidence, or incomplete inventories are UNKNOWN/INCOMPLETE, never PASS.
8. **No silent self-healing.** Sentinel may open an incident and propose a patch in an isolated branch. It cannot merge, release, alter protected policy, or weaken a test to make itself green.

## Inspection map — the system is divided into explicit surfaces

| Surface | What Sentinel checks | Required evidence |
|---|---|---|
| 1. Source and architecture | dead/disconnected code, duplicate owners, cycles, hidden coupling, unhandled branches, TODO/FIXME debt, unsafe concurrency, swallowed exceptions | source inventory, compiler/lint, dependency graph, symbol/call graph, reviewed findings |
| 2. Production wiring | every advertised capability is reachable from the production composition root and uses the real implementation | production composition tests, runtime route trace, capability registry evidence |
| 3. User journeys and UI | navigation, input, loading/empty/error states, settings persistence, accessibility, screen sizes, lifecycle recreation | deterministic UI tests, navigation tests, screenshots where useful |
| 4. Engine and orchestration | stale readiness, stuck jobs, cancellation, queue ordering, timeouts, duplicate execution, resource leaks | state-machine tests, concurrency tests, liveness probes, failure injection |
| 5. Identity and memory | write/read integrity, relationship state, memory retrieval, corruption handling, backup/restore, migrations | seeded persistent markers, database integrity checks, round-trip tests, upgrade tests |
| 6. Storage and continuity | atomic writes, partial writes, disk-full behavior, encryption/key availability, backup exclusions, process death | fault-injected storage tests, restart tests, data checksums and schema verification |
| 7. Network and integrations | auth expiry, revoked credentials, malformed responses, rate limits, retries, idempotency, offline behavior | contract tests, mock-server integration tests, retry/idempotency tests |
| 8. Android platform | permissions, background restrictions, Doze, battery, service binding, notifications, API-level behavior | emulator/device matrix, permission-state tests, background execution tests |
| 9. Security and privacy | exported components, intent validation, secret leakage, local data exposure, authorization bypass, dependency risk | static security checks, manifest review, adversarial tests, dependency inventory |
| 10. Performance and stability | startup, frame jank, memory growth, ANRs, battery/network churn, unbounded work | Macrobenchmark, StrictMode, long-run soak tests, profiler/ANR evidence |
| 11. Build and release | reproducibility, dependency drift, signing, version monotonicity, artifact provenance, APK contents | immutable commit + artifact hashes, signature verification, release build checks |
| 12. Update and recovery | staged APK integrity, install rejection, post-install health, failed-release evidence, forward repair, rollback limits | update state-machine tests, signed in-place upgrade, recovery drills |
| 13. Observability and diagnostics | every failure has a stable incident ID, source reference, severity, timestamp, affected capability, and recovery status | structured event schema, crash/ANR reports, diagnostic export tests |
| 14. Test quality itself | tests that pass without exercising behavior, flaky tests, missing assertions, tests never run, untested critical code | JUnit evidence aggregation, mutation tests, flaky-test history, coverage interpreted with risk |

## Risk model

Every finding is recorded with:
- stable finding ID and detector version;
- affected capability/package/file/symbol and source line where available;
- severity: **critical / high / medium / low / informational**;
- confidence and whether it is a confirmed defect, suspicious pattern, or evidence gap;
- reproducible steps, raw evidence, and first known affected commit;
- user impact and continuity/security impact;
- owner, status, linked regression test, and fix commit.

Severity is impact-based, not a count of warnings. A single signing, identity-loss, authorization, or unrecoverable-update defect can block release.

A component score is not an average that can hide a critical weakness. The overall result is one of:
- **BLOCKED** — a critical/high finding or mandatory gate failed;
- **INCOMPLETE** — evidence or a required inspection surface is missing;
- **DEGRADED** — no release blocker, but known medium/low risks remain;
- **VERIFIED FOR THE TESTED SCOPE** — all required gates passed for the exact commit, artifact, environment, and test scope.

Never use an unqualified "100% healthy" claim.

## Execution cadence

- **Every pull request / commit:** fast deterministic checks, compilation, lint, unit tests, source inventory, architecture contracts, and tests for changed code.
- **Every merge to main:** full JVM shards, disposable Android device suite, full source/vision audit, build graph and dependency evidence, APK integrity checks.
- **Nightly:** repeat the full suite against current main; run broader fault-injection, persistence, lifecycle, background execution, security, and performance checks as those detectors become available.
- **Before release:** signed minified/release APK, clean install, in-place upgrade from the exact supported seed, real-device acceptance, persistent-data verification, and recovery drill.
- **After release:** observe crashes, ANRs, failed jobs, update failures, data-integrity alarms, and capability-specific health signals; turn every confirmed incident into a regression test.

## Required report

Each run publishes:
1. exact source commit and workflow run;
2. exact artifact SHA-256 and signer when an artifact exists;
3. inventory counts by inspection surface;
4. PASS / FAIL / UNKNOWN for each required gate;
5. findings ordered by severity and confidence;
6. new, fixed, recurring, and unresolved findings;
7. test totals, skipped counts, flaky retries, and missing evidence;
8. a comparison to the previous successful baseline;
9. explicit release decision and its tested scope.

Reports are immutable evidence for that run. A newer green run must not erase an older failure or its history.

## Repair protocol

1. Sentinel creates a finding with reproduction evidence.
2. A separate repair process proposes the smallest safe change in an isolated branch.
3. The original failing scenario becomes a regression test before or with the repair.
4. The independent verifier reviews the diff and confirms the test would fail on the old implementation (mutation/negative-control check where feasible).
5. All affected gates run against the exact candidate commit.
6. Protected changes require owner authorization; Sentinel cannot grant it.
7. Only an authorized merge/release process may promote the candidate.
8. Post-merge and device evidence must confirm the original user-visible outcome.

## Honest current state

Already present in the repository at the time this contract was added:
- six-shard JVM test execution with aggregated JUnit evidence;
- a disposable Android emulator/device test lane;
- Android Lint and build validation;
- repository authority/evidence checks;
- a whole-repository Kotlin vision audit;
- lexical source tracing and build/dependency structure reports;
- test-failure observation and existing diagnostics/self-repair components.

These are useful foundations, not proof that every surface above is covered. The current source tracer explicitly has limits around DI, reflection, generated code, callbacks, and dynamic dispatch. The existing vision audit is not a full security, performance, persistence, or production-wiring audit.

The next implementation increments must add a machine-readable capability/data-store inventory, risk-weighted coverage map, production-reachability verification, persistent-data upgrade tests, fault-injection suites, mutation/negative-control tests, and long-run device health probes. Each increment must have its own tests and report evidence.

**Release rule:** Friday is not ready for owner-device installation or an update merely because CI is green. The exact signed artifact must pass the relevant continuity and real-device acceptance gates.
