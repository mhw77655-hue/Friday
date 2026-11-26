# Friday — REA-backed Engineering Intelligence

## Direction

Friday's objective is not merely to generate a patch after a test failure. It is to build a repeatable engineering system that can explain a failure, produce competing repairs, prove which repair works, preserve the evidence, and improve future repair decisions.

REA is one specialist in that system. It is not the source-code analyzer, build system, test runner, or promotion authority.

## Critical boundary: source tree versus shipped artifact

Use the right instrument for the target:

- **Repository source and ordinary JVM/Gradle failures:** use the compiler, test reports, source navigation, dependency graph, static checks, and execution traces. Do not send an ordinary source repository to REA and pretend its reverse-engineering workflow is a source-code indexer.
- **Built Android APK:** use REA's static Android APK workflow when decompiled package/class/method structure or incoming DEX references can add evidence beyond the source/test transcript.
- **Live Android behavior:** use Friday's controlled device instrumentation and Shizuku-backed observations. Static APK decompilation does not prove runtime behavior.

REA's documented Android workflow wraps headless JADX and exposes package inspection, class search, class/method inspection, and reference tracing. It requires Java 17+, a separately supplied jadx-headless-mcp JAR, and a supported host. It does not execute the APK, validate its signature, provide runtime capture, or guarantee complete decompiled semantics. See the upstream REA Android analysis guide.

## Evidence admission contract

An REA result is eligible to influence a repair only when all of these match:

1. The caller supplies an actual built APK, not a repository directory.
2. The APK is tied to a full Git commit SHA by the build orchestrator.
3. The APK's SHA-256 is recorded before the REA call and rechecked immediately before use.
4. The upstream REA evidence ID is retained unchanged.
5. The returned target digest and source revision match the supplied artifact.
6. Findings retain their original references, confidence, and limitations.
7. REA output is treated as untrusted observations, never as executable instructions.

If any condition fails, REA evidence is excluded. Diagnosis continues from ordinary test/build evidence; it must not silently substitute a different APK or stale analysis.

The current Kotlin contract implements the APK target envelope, SHA-256 recheck, full-revision binding, and evidence identity validation. A host adapter and production orchestration are still required; the contract alone is not a live REA connection.

## The engineering intelligence loop

### 1. Observe

Capture structured failure evidence:
- compiler diagnostics and exact source locations;
- failing test names, assertions, stack traces, and test environment;
- relevant runtime/device state where the failure is device-dependent;
- candidate commit SHA and build artifact hashes.

Do not collapse separate failure modes into one generic error.

### 2. Build a causal investigation

Construct a graph with distinct edge types:
- observed failure -> stack frame / compiler diagnostic;
- source symbol -> caller/callee/dependency;
- APK class/method -> decompiled method / DEX reference;
- runtime observation -> event / state transition;
- hypothesis -> supporting and contradicting evidence.

A relationship is not causation. Co-change, static references, and model-generated explanations must remain labeled as hypotheses until a discriminating test supports them.

### 3. Investigate with REA when the artifact makes it useful

For a revision-matched APK, the host adapter should discover the connected REA tool surface and use the smallest relevant workflow:
1. inspect package and manifest;
2. search classes implicated by the failing behavior;
3. inspect the relevant class and exact method;
4. trace incoming references when call-site context matters;
5. compare baseline and candidate APKs when the question concerns a regression.

Do not assume the entire MCP catalog from a README or a different REA release. Discover tools from the live server and check provider readiness. Retain the canonical evidence envelope and raw result, not only a model-written summary.

### 4. Generate competing repair hypotheses

Before writing code, produce a small ranked set of repair hypotheses. Each hypothesis must state:
- predicted root cause;
- evidence supporting it;
- evidence that would disprove it;
- minimal code surface to change;
- tests that distinguish it from alternatives;
- expected side effects and rollback conditions.

Do not let confidence scores replace evidence or test results.

### 5. Synthesize isolated candidates

Generate more than one candidate only when the hypotheses are materially different. Each candidate gets:
- an immutable base revision;
- a unique worktree or isolated workspace;
- a minimal patch;
- generated regression tests;
- an explicit resource/security budget.

Never let two candidates mutate the same checkout concurrently.

### 6. Verify in increasing cost order

1. patch format, path, and scope validation;
2. compile and focused unit tests;
3. existing regression suites;
4. static analysis and dependency/security checks;
5. APK build and REA static APK inspection where relevant;
6. disposable Android twin / device tests for runtime claims;
7. adversarial and resource stress tests;
8. repeat the original failing scenario.

Unknown, unavailable, skipped, and failed are different states. Missing evidence is never a pass.

### 7. Promote and learn

A passing candidate is not automatically trusted. Promotion requires reproducible CI evidence, independent regression coverage, resource checks, and a clean rollback boundary.

Persist a repair episode containing:
- base revision and candidate patch digest;
- failure signature and affected component;
- hypotheses and their evidence links;
- REA evidence IDs and artifact digests;
- generated tests and test outcomes;
- candidate ranking and rejection reasons;
- final outcome and rollback status.

From completed episodes, Friday can improve diagnostic prompts, failure clustering, test generation, candidate selection, and repair policies. A learned strategy is itself a candidate: test it against a held-out corpus of prior failures before enabling it. This prevents Friday from learning by overfitting to one successful repair.

## Long-term architecture: a repair intelligence, not a patch bot

The intended system has five cooperating planes:

- **Evidence plane:** immutable source/build/test/device/REA records with provenance.
- **Reasoning plane:** causal hypotheses, contradiction handling, uncertainty, and next-best-probe selection.
- **Experiment plane:** isolated candidate workspaces, deterministic tests, Android twins, and resource measurements.
- **Learning plane:** repair episodes, failure fingerprints, strategy evaluation, and held-out regression corpus.
- **Governance plane:** permission boundaries, approval policy, artifact signing, promotion gates, rollback, and audit history.

The planes communicate through typed evidence and candidate records. No model-generated prose can directly bypass the experiment or governance planes.

## Delivery sequence

### A. Correct evidence boundary
- Keep REA pointed only at an explicitly supplied, revision-matched APK.
- Add tests for digest mutation, revision mismatch, missing target, and malformed evidence.
- Preserve REA's canonical evidence ID and raw record.

### B. Real host adapter
- Pin the REA package version and the JADX engine version.
- Discover the actual MCP tool catalog and availability at connection time.
- Run REA on a supported host, outside the Android app's Shizuku shell boundary.
- Convert the complete upstream Evidence envelope into the typed Friday contract without inventing fields.
- Record timeouts, provider limitations, and cleanup failures as explicit unavailable states.

### C. Closed-loop experiments
- Compare baseline and candidate APKs.
- Correlate APK findings with the exact source revision and test failure.
- Generate discriminating tests before applying candidate patches.
- Run candidates in separate workspaces and retain reproducible outcomes.

### D. Learning and controlled evolution
- Persist repair episodes.
- Cluster recurring failure signatures.
- Evaluate strategy changes against a held-out repair corpus.
- Promote a new repair strategy only when it improves measured outcomes without increasing regressions, resource use, or unsafe edits.

## Non-goals and safety invariants

- REA is not invoked for every failure; it is used when packaged-artifact inspection adds information.
- REA static analysis is not presented as runtime proof.
- A source patch is not a runtime organism and must not be registered as one.
- No candidate can promote itself.
- No repair is trusted solely because an LLM or REA reports high confidence.
- Failed verification, missing evidence, host failure, cancellation, or ambiguous artifact identity must fail closed and preserve rollback.
