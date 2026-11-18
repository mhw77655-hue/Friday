# Friday — whole-project root-cause audit and build plan

**Snapshot:** 2026-11-18 13:10 UTC  
**Baseline:** `main` at `d1b4cc88d722fa420d5648db9284a30b15ac33e8`  
**Purpose:** reconcile the owner's intended outcome with the actual repository, identify causes rather than patch symptoms, and define dependency-ordered work with evidence-based exit gates.

This is an engineering plan and audit record, not a claim that Friday is complete. It does not authorize changing the owner's vision or treating a checklist as her developmental definition.

## 1. Evidence collected

### Repository inventory and automated audit

The main tree contains **974 tracked files** across 1,232 tree entries. The latest integrated candidate's CI source-audit job read every tracked source/config file to create an inventory of **802 code/config files and 133,875 lines**. It passed all **481 production Kotlin files** under `mobile/app/src/main/java` through the whole-file Vision-shape scanner, Python syntax-compiled 29 files, and shell-syntax-checked 18 scripts.

The current candidate audit artifact from run [44314813387](https://github.com/mhw77655-hue/Friday/actions/runs/44314813387) reports:
- 275 potential deviations and 391 observations across the production Kotlin scan (candidate findings span 136 files).
- 221 `named_wire`, 28 `fixed_classification`, 19 `flat_threshold`, and 7 `meaning_arbiter` deviation-shaped findings.
- A separate self-audit of `.ralph/audit.py` found 7 deviation-shaped findings and 6 observations.

**Interpretation limit:** these are static shape findings, not 283 proven architectural defects. Named constructor dependencies, finite Android/platform enums, bounded queues and resource limits can be correct engineering. The scanner cannot establish intent, call-graph reachability, runtime ownership or whether a fixed boundary is part of the protected security/continuity floor. Every candidate needs semantic review. The Python and shell checks establish syntax only; they do not prove their runtime workflows. The source inventory is not a human semantic review of all 134,254 lines.

### Verified live state

- Main is `d1b4cc88d722fa420d5648db9284a30b15ac33e8`.
- PR #86 merged the permanent release-signing workflow. No release currently exists. Owner-controlled key custody and the five `FRIDAY_RELEASE_*` secrets remain unverified/unconfigured; the phone updater is not implemented. Issue #75 has been corrected to reflect this.
- PR #82 (virtual Android phone) remains open. The latest run builds both APKs but fails before installation. The emulator log reports: `Cannot access system provider: 'settings' before system providers are installed!`. Its readiness test checks package enumeration, which is insufficient evidence that the Settings provider and PackageInstaller are ready.
- PR #83 (autonomous repair/promotion) passes its latest reported CI, but its stated dependency is PR #85, which is closed without merge. Its current diff also removes the explicit workflow_dispatch while relying on PR checks from a GITHUB_TOKEN-created PR; GitHub puts those runs in an approval-required state. It must restore exact-candidate dispatch, gain Actions write only in the trusted publisher, and depend on PR #93's emulator gate before any promotion claim.
- PR #89 is open at `2260ce75dc88e2b49b58d5a5cadbabeb974dc87f`. Its latest CI fails compilation: `JarvisEngine.kt:451:17 Argument already passed for this parameter.` The composition call passes `nowMs = clock` twice.
- PR #93 is the current integration candidate: it reconstructs the malformed main build workflow from the valid pre-append structure, preserves the merged signing smoke test, adds the emulator job and the tested-by-design Settings-provider readiness helper, and makes the failure reporter cover all validation lanes. Latest head `646f045c4c19e009d9bb50860d17351d3a8da3b1` is running in #44314813387. Owner-vision checks, Builder safety tests and actionlint passed; APK/release smoke, emulator and full-tree audit remain in progress. It is draft and not yet verified.
- PR #82 is based on the pre-signing workflow and must not be merged separately; #93 supersedes it. PR #83 must depend on the merged #93 validation gate, not the stale #82/#85 topology.
- PR #91 was closed after correcting an initial mistaken assumption about GitHub event semantics. PR #92 now grants `actions: write` only to the trusted proposal publisher so its deliberate `workflow_dispatch` can run; the untrusted worker remains read-only.
- PR #89 also reintroduces `ToolBuilderComposition` into Android's production `JarvisEngine`, contradicting the repository's existing explicit contract that Android is Friday's body/evidence source while source construction, repository edits, tests, CI, artifact construction and signing belong in the connected repository workshop.
- Issues #72, #73, #74, #76, #77 and #78 remain open. They cover the repair loop, vision-inspired autonomy and emulator, evidence of real learning, historical archive purge, runtime capability acquisition and Android capability/permission surface.

## 2. Root causes — not symptom list

### RC-1: No single reconciled execution contract

The repository contains authoritative Vision/owner words, older stage plans, newer architecture corrections, issue bodies and PR descriptions that have drifted apart. Examples include the stale #75 signing status, #83's dependency on closed #85, and #89's on-device Builder reintroduction despite the repository-side workshop contract.

**Cause:** work items and PR bodies are being advanced independently of a continuously reconciled source-of-truth/dependency graph.  
**Consequence:** teams can implement superseded architecture, repeat work, and report completion against obsolete acceptance criteria.

### RC-2: Friday's body, runtime capabilities and source workshop are conflated

The Android app must remain the same local individual when disconnected. It should observe, learn, identify needs, queue work and direct her workshop. It is not the repository checkout, coding worker, compiler, test runner, signer or APK publisher.

At the same time, Friday must be able to gain safe capabilities without an APK replacement. This is a distinct runtime-acquisition problem, not a reason to put source-building back in Android.

**Cause:** the system lacks a precise, implemented boundary between (a) safe declarative/runtime capability packages and (b) source-level changes to Friday's body.  
**Consequence:** legacy Android Builder/evolution code can be reactivated and presented as self-development even though it violates the deployment contract.

### RC-3: Development intent does not yet form a closed, durable feedback loop

Self-study, failure reporting, request schemas, a repository worker, GitHub publication, runtime capability registration and outcome learning exist in partial or separate forms. The evidence does not yet prove one end-to-end flow from Friday-originated need to verified result and changed later behaviour.

**Cause:** durable request transport, idempotency, worker checkpoint/resume, result reconciliation and experience attribution are not joined through one traceable contract.  
**Consequence:** Friday can observe/report or propose without reliably directing construction, receiving its result, using it, and learning from the outcome.

### RC-4: Validation evidence is not yet strong enough for automatic promotion

The emulator is failing before install; PR #83 does not yet require that emulator check; and the static Vision scanner reports structural candidates without semantic adjudication. A successful JVM test, APK compile or static scan is not proof of real-device behaviour or safe promotion.

**Cause:** evidence types and exact candidate identity are not enforced as one immutable verification chain.  
**Consequence:** promotion could outrun the evidence intended to justify it.

### RC-5: Developmental change is not yet demonstrated

The Vision's Life Test requires an experience-caused internal change that persists after restart, changes later behaviour with the original episode text blocked, and disappears when the developmental mechanism is disabled. Existing records, profiles, scores or stored memories are not enough.

**Cause:** there is not yet a complete real-composition causal/ablation test demonstrating that standard.  
**Consequence:** storage or telemetry can be mistaken for development.

### RC-6: Android capabilities and permission authority are not represented end-to-end

Manifest declarations and platform adapters do not prove that access was granted, that a capability is registered, that a real action succeeds, or that revocation is handled. The owner must remain the authority for special permissions and privileged operations.

**Cause:** capability discovery, permission explanation/grant, runtime registration, action, evidence, revocation and recovery are not yet one tested lifecycle.  
**Consequence:** Friday's practical ability surface is smaller and less reliable than the declared surface.

### RC-7: Release foundation is ahead of the installed-phone update path

PR #86 provides the signing/publishing foundation, but the owner secrets, first-install signing transition, release discovery, signer/hash/version verification, installation request, health check and recovery are separate unfinished requirements.

**Cause:** signing and publishing were treated as a milestone before the phone-side update lifecycle was implemented and tested.  
**Consequence:** a signed APK may be publishable later, but that does not yet mean Friday can safely update herself.

### RC-8: The audit system can create false certainty

The Kotlin scanner is useful for finding code shapes but its 283 findings are not verdicts. Its self-audit also found issues in the auditor itself. Python/shell syntax checks do not semantically inspect native C++, Gradle, workflow trust boundaries or runtime behaviour.

**Cause:** one static scan is being described too broadly as a whole-project semantic audit.  
**Consequence:** false positives can create architecture churn, while unscanned behaviour can be overlooked.

### RC-10: The native inference lifecycle contains deadlocks and unsafe lifetime boundaries

The production llama.cpp engine uses a non-recursive `std::mutex`. `llama_model_generate()` acquires it and then calls `set_state_locked()` / `set_error_locked()`, which attempt to acquire the same mutex again. The same nested-lock pattern exists in `llama_model_suspend()` and `llama_model_destroy()`. Those paths can deadlock deterministically when reached.

The JNI `nativeCreate` also stores `GetStringUTFChars(modelPath)` in `nativeConfig.model_path`, releases those UTF chars, and only then calls `llama_model_create()`; the model path pointer is already invalid at the call boundary, and the model copies it into an asynchronous prewarm thread. The prewarm thread is detached in create/resume, while destroy later attempts to join the thread member. A detached thread is not joinable; destroying the model during prewarm can free state still referenced by the background thread. The Kotlin `LocalInferenceEngine` protects load/unload with `stateLock` but reads/uses `nativeHandle` for generate, stream, cancel, health and suspend/resume outside that lock, so concurrent unload can destroy a handle while another call is using it. `InferenceService` launches each Messenger operation as a separate `Dispatchers.IO` coroutine, so generate/unload and multiple model operations can overlap; the isolated process protects the UI process from a native crash but does not fix the engine's own handle lifetime. The llama.cpp API documents `llama_backend_init/free` as process-lifetime calls (initialize once, free once at program end), but Friday currently calls them per model. This violates the documented lifecycle contract; the exact impact at the pinned llama.cpp commit must be verified, and ownership should move to the inference process/service lifetime rather than individual models. JNI token-count/encode/decode allocate a `LlamaTokenizer` wrapper per call without freeing it. `llama_model_get_health()` returns a pointer into mutable error-string storage after releasing the model mutex, so JNI can race with another thread changing that string. VAD JNI passes caller-provided `numSamples` to native processing without checking it against the Java array length or checking JNI array access for null. The JNI generation bridge also discards the supplied streaming callback (`tokenCb` is always null) and parses stop-sequence counts without passing stop sequences into the native sampler, so the exposed streaming/stop contract is not implemented.

**Cause:** native resource ownership, lock ownership, global backend lifetime and Kotlin handle lifetime are not expressed as one explicit lifecycle contract.  
**Consequence:** inference, suspend, shutdown and low-memory model replacement can deadlock, race, leak or access freed memory. This is a production-critical blocker, not a model-quality issue.

### RC-9: Legacy project islands remain beside the Android Friday implementation

The root `core/` Python package, `manifest/` policy files and `tests/` describe an earlier JARVIS Phase-1/building-system prototype; they are not included by `settings.gradle.kts` (the only Gradle module is `:mobile:app`). `manifest/workspace_manifest.yaml` names the remote `github.com/mhw77655-hue/JARVIS`, not Friday. `manifest/protected_paths.py` claims to be called by the execution layer, but the active Friday Builder does not import it: `tools/friday-builder/validate_patch_paths.py` implements a separate hardcoded protected-path policy and is what the current proposal workflow invokes. Thus `manifest/` is stale/dead policy, not the current Builder security boundary. `core/building_integration.py` imports a top-level `building` package absent from the tracked tree. `tests/test_building_system.py` imports that same absent package, while `tests/test_phase1_exit.py` tests the obsolete echo/status loop. These Python tests are not run by current CI; only syntax compilation runs. The old `core/decision_gate.py` auto-approves Tier-1 file writes and `core/execution_layer.py` writes to caller-supplied paths, so these files must never be mistaken for the current Android authorization/execution authority. The root `runtime/model_gateway/`, `scripts/jarvis-*`, `voiceforge/` and `jarvis-browser/` are also legacy Termux/local-HTTP or empty browser artifacts, not part of the Android Gradle build. `runtime/model_gateway` targets a localhost llama-server; `voiceforge_server.py` claims to back a Kotlin adapter that exists only under `src/test`, while production voice providers are Sherpa TTS and Android System TTS. These are not the production on-device inference/voice path.

The README also points to an obsolete branch and PRs #19/#23, and describes self-development as source building "from the phone". The GitHub bridge guide still instructs the worker to search the removed private conversation ZIP. The Nov 16 build-progress scorecard refers to an older branch/PR topology.

**Cause:** obsolete prototype code and documentation were not retired or clearly quarantined when the Android-native Friday architecture became authoritative.  
**Consequence:** workers and maintainers can import dead code, follow stale instructions, or treat an old prototype as production architecture.

### RC-12: The main build workflow was corrupted by appending a second workflow instead of editing the existing one

Main's `.github/workflows/build.yml` is 627 lines. Its parent at `1ca44dbff2286f71032ed54cf0a391ae5dfe7b1f` was 332 lines. Commit `d1b4cc88d722fa420d5648db9284a30b15ac33e8` adds 295 lines and deletes zero lines in that file. The appended content duplicates the `full-source-audit` and `report-failure` job definitions and leaves a detached tail of the release-signing smoke-test shell block after the failure reporter. Recent main push runs (for example `44313069957`) fail with zero scheduled jobs. This explains why main's CI status cannot currently be trusted as an ordinary test failure/success signal.

PR #82's workflow branch was based on the earlier 332-line version and adds an emulator job, but it predates the merged release-signing workflow. Simply merging it would lose or conflict with the release smoke validation. The clean integration must reconstruct one canonical workflow containing exactly one debug job, one emulator job, one source-audit job and one failure reporter, with the release-signing smoke steps inserted inside the debug job.

**Cause:** a workflow patch appended a second set of job definitions instead of editing the existing YAML structure; branch drift then left the emulator branch based on the pre-signing workflow.  
**Consequence:** main workflow runs can fail before any jobs are scheduled, and PR/branch status badges are not reliable evidence.  
**Root correction:** restore one valid workflow from the pre-append structure, merge emulator validation and release-signing smoke in the correct job positions, then validate the YAML with actionlint and execute the complete workflow on an audit branch before updating the PR branch.

### RC-11: The trusted proposal publisher dispatches validation without the required permission

`.github/workflows/friday-builder-proposal.yml` creates the proposal PR using the job's `GITHUB_TOKEN`, then explicitly runs `gh workflow run build.yml --ref "$BRANCH"`. That dispatch is intentional: GitHub documents that `workflow_dispatch` always creates a workflow run, while a `pull_request` event caused by a workflow's `GITHUB_TOKEN` creates an approval-required run. The explicit dispatch avoids a human having to approve the automated proposal's CI run.

The trusted publisher grants `contents: write` and `pull-requests: write` but omits `actions: write`. GitHub's fine-grained-token permission table for the workflow-dispatch API requires Actions: write. Thus the dispatch can fail after the draft PR has already been created.

**Cause:** the publisher's permission set does not match its deliberate workflow-dispatch operation.  
**Consequence:** a proposal can exist while the publisher job reports failure and no immediate validation run is guaranteed.  
**Root correction:** retain the explicit dispatch and grant `actions: write` only to the trusted publisher job (or use a dedicated GitHub App token with the same narrow permission). Do not remove the dispatch unless the workflow is redesigned to handle approval-required PR checks safely. Do not grant Contents: write to the untrusted worker.


### RC-13: PR #83 removes the only workflow trigger that can validate an automated proposal without manual approval

The #83 diff creates/updates proposal PRs with `GITHUB_TOKEN`, removes the explicit `gh workflow run build.yml --ref "$BRANCH"` dispatch, then waits on `gh pr checks --watch` before auto-merge. GitHub documents that PR events created/updated with `GITHUB_TOKEN` enter an approval-required state and that push events from that token do not start workflows; `workflow_dispatch` is an exception that always starts a run. The result is a repair/promotion worker that can wait for checks that never begin.

**Cause:** the promotion design assumes the PR event will automatically start CI even though the PR is created by the workflow's own token.  
**Consequence:** self-repair stalls or treats absent checks ambiguously, and the automatic-promotion claim is unsupported.  
**Root correction:** retain explicit workflow dispatch, grant `actions: write` only to the trusted publisher (or use a dedicated GitHub App token with the same narrow authority), record the dispatched run ID and head SHA, wait for that exact run, then re-read the PR head SHA and refuse promotion if it changed. The untrusted worker remains read-only.

### RC-14: Phone-to-workshop requests do not identify the exact installed source revision

`GitHubDevelopmentClient` currently sends `source_revision = "main"` for build requests and `"installed-app"` for crash repair. `BuildConfig` has no immutable Git SHA field. The repository worker's `run_proposal.sh` checks out the current repository base and records `git rev-parse HEAD`, but it does not compare that commit with the installed app's source revision. The request validator checks that `source_revision` is a bounded string, not that it identifies a real commit.

**Cause:** the request schema has a provenance field, but the APK does not embed its source commit and the worker does not resolve/verify the supplied value.  
**Consequence:** a crash or development request cannot reliably be tied to the exact code running on the phone; the workshop may repair current `main` while diagnosing a different installed build, and the resulting evidence is not reproducible.  
**Root correction:** embed the immutable build commit SHA in `BuildConfig` at CI/release build time; propagate it as `installed_source_sha` through the encrypted outbox and public request envelope; have the worker fetch and verify that exact commit for diagnosis. Track the candidate's `target_base_sha` separately. If the installed SHA is unknown (local build), mark it explicitly unknown rather than substituting `main` or `installed-app`. Include source SHA and artifact digest in build/promotion receipts.

### RC-15: Memory conflict authority is inferred from free-text substrings and factual contradictions auto-resolve by recency

`MemoryConflictResolver` decides that an evidence item is a user instruction when its free-text evidence string contains the substring `"user"` (case-insensitive). `resolveByUserInstruction()` then chooses A if A contains that substring, otherwise B; if both sides contain it, A wins arbitrarily. The `MemoryConflict` contract carries evidence strings rather than a typed, authenticated source/authority record. Separately, `canAutoResolve()` returns true for every content/fact contradiction, and `determineResolutionMethod()` defaults those conflicts to last-writer-wins even when confidence and source-reliability deltas are insufficient. The tests currently encode this behaviour. The memory-promotion path has a related authority mismatch: `MemoryConsolidator` sets `explicitInstruction = 1.0` for any `USER_INTERACTION`, `CORRECTION` or `FEEDBACK` experience, not for a typed explicit remember intent; `PromotionFactors` gives that signal only 0.05 weight. An actual owner request to remember can therefore be treated like an ordinary weighted hint and may remain below the promotion threshold, while unrelated user interactions are mislabeled as explicit instructions.

**Cause:** authority and supersession are inferred from prose and timestamps rather than structured provenance and semantic conflict class.  
**Consequence:** quoted text, generated summaries or tool output containing the word "user" can gain owner-instruction priority; equal-confidence factual contradictions can silently discard one side solely because it is newer.  
**Root correction:** carry typed evidence provenance/authority from the original memory write (owner-explicit instruction, user statement, model inference, device observation, external source, etc.); only authenticated owner-explicit corrections may supersede lower-authority memories. Distinguish temporal state updates from static fact contradictions. Auto-resolve only when a typed correction/supersession relation or sufficiently strong source evidence exists; otherwise retain both hypotheses, mark unresolved and ask/verify when consequential. Represent explicit remember/correct/forget intent as a typed, authenticated instruction separate from generic user interaction. An explicit owner remember instruction must create a durable high-priority memory record (subject to safety/privacy policy), not merely add 5% to a weighted score; generic interaction must not be mislabeled as explicit intent. Replace tests that assert unconditional fact/content LWW.

### RC-16: Working-reference history is unbounded and ordinal resolution ignores list scope

`CognitiveEngine` constructs one `ReferenceStore` for its lifetime and records every extracted entity mention on each turn. `ReferenceStore.recordMentions()` prepends to an unbounded mutable list; `all()` returns the full history and there is no session reset, retention cap or segment eviction. `SalienceScorer.topSalient()` enumerates every unique entity and calls `score()`, which scans the full mention list for each entity, making repeated ranking quadratic as the conversation grows. `PronounResolver`'s ordinal path sorts the entire conversation history and selects `chronological[position - 1]`, rather than resolving within the current list/utterance group. Old mentions can therefore be selected by "the first/second one" after unrelated lists have occurred. The upstream `IntentInference.extractEntities()` is also only a two-pattern heuristic: it records quoted strings and adjacent pairs of capitalized words. It misses single proper nouns, ordinary noun phrases, unquoted entities, punctuation-separated mentions and most Arabic-script entities, so the reference history is incomplete before resolution even begins.

**Cause:** a short-lived working-reference buffer was implemented as an unlimited conversation archive, and ordinal reference resolution has no explicit list frame.  
**Consequence:** memory and CPU use grow over the lifetime of Friday, while pronouns/ordinals can silently bind to stale entities.  
**Root correction:** keep a bounded per-turn/per-segment working set with explicit lifecycle and maximum retention; aggregate mention frequency/recency incrementally rather than rescanning the entire history; replace capitalization-only extraction with language-aware English/Arabic mention extraction that preserves spans, entity type, confidence and provenance; assign list/group IDs to extracted enumerations; resolve ordinals only inside the relevant group and return ambiguity when the group is uncertain. Durable knowledge belongs in the memory graph, not the ephemeral reference buffer.

### RC-17: The turn queue is unbounded and response timeout starts before queue admission

Production `LatencyLayer` dispatches slow-path turns onto `Executors.newSingleThreadExecutor`, whose work queue is unbounded. `LatencyPipeline.pending` and `pendingInputs` are also unbounded. The response timeout is scheduled at submission time, but the pending entry is added only when the queued slow-path task eventually starts. If queue delay exceeds the timeout, the timeout callback sees no pending entry and does nothing; when the delayed task later runs, it adds a pending entry and sends the request with no remaining timeout. This can leave permanent pending state and late work after the UI already timed out.

**Cause:** queue admission, execution and timeout are not one state machine; the deadline does not cover queued work.  
**Consequence:** bursts can grow memory without bound, cause stale turns to execute after timeout, and leak pending exchanges that can misassociate later replies.  
**Root correction:** introduce bounded admission and an explicit per-exchange lifecycle (`QUEUED → RUNNING → COMPLETED/TIMED_OUT/CANCELLED`) with one absolute deadline from submission. On timeout, cancel/remove queued work and prevent it from dispatching later; running work must settle exactly once. Apply backpressure/rejection when capacity is reached and report a clear local response rather than accumulating unbounded tasks.

### Manual triage of the auditor's self-audit

The auditor's 7 self-deviations are numeric scan thresholds/limits inside `.ralph/audit.py`, not runtime Friday decisions. Its 6 self-observations explicitly identify its own fixed tables (`S13_PHRASES`, `STORE_TYPES`, `SELF_AUDIT_LIMITS`, `RECIPES`), its semantic-arbiter check, and its necessary `git`/`bash` dependencies. These are transparency signals about the tool's own limits, not seven additional Friday defects. The auditor is a structural review aid; it cannot validate its own semantics or replace human call-path review.

### Manual triage of the 222 `named_wire` candidates

The check is syntactic: it flags a concrete class-typed property/constructor parameter declared in another file when the changed code calls a method on it. It does **not** prove that the interaction needs to vary, that the dependency crosses an architectural boundary, or that an interface would improve the design. The 222 candidates span 112 files; frequent types include `FileStorage`, `ModelManager`, `CapabilityRegistry`, `ResourceGovernor`, `GenomeArchive` and `FailureSurface`.

Triage each by the reason a boundary exists:

- **External variability boundary:** persistence backend, model provider, Android/OEM service, permission source, network/GitHub client, release signer/installer, emulator/workshop. These should be ports where a real alternative, test fake or platform variation is required.
- **Domain-internal collaborator:** a stable implementation within one bounded subsystem (e.g. a registry used by its resolver) may remain concrete. Do not add interfaces merely to make a scanner count disappear.
- **Composition root:** concrete construction/wiring is expected at the one composition boundary; the runtime organs should receive narrow capabilities, not own construction.
- **Legacy/quarantined code:** do not refactor old Human Core/JARVIS paths into the active Friday architecture; isolate or remove them after reachability checks.

A port is justified by an actual variability, lifecycle, security or test-isolation requirement—not by the presence of a class name. The 222 results are a review index, not 222 required refactors.

### Manual triage of the scanner's seven `meaning_arbiter` candidates

The scanner's seven candidates are not seven defects. Manual review classifies them as follows:

- **Real cognitive defects:** `MemoryConflictResolver` (RC-15) infers owner authority from free-text substrings and silently resolves factual contradictions by recency; `PronounResolver` (RC-16) resolves ordinals against the entire unbounded conversation history.
- **Intentional hard safety/operational policy:** `EnvironmentProfile`'s memory/permission policy enums and `JevPhoneActionPolicy`'s explicit authorization/risk thresholds. These should remain explicit, fail-closed, owner-governed constraints. Friday's learned preferences or model confidence must never rewrite them.
- **Deterministic infrastructure, not semantic arbitration:** `humancore/algo/ConflictResolver` merges replicated state; `ModelPathResolver` resolves model files under a controlled directory. Their fixed rules are appropriate for those narrow jobs and should not be replaced with an LLM.

This triage is why the 276 scanner deviations are candidates requiring human semantic review, not a backlog of 276 automatic edits.

### Manual triage of the 28 `fixed_classification` candidates

Most are explicit Kotlin enums for state machines, transport commands, UI states, resource-degradation tiers, biometric types, recovery actions and model roles. These are stable protocol/state vocabularies, not proof that Friday's reasoning is fixed. They should remain typed and exhaustive.

Two areas need semantic attention:
- `CognitiveEngine`'s intent/turn categories are coarse routing labels; verify that they are not the sole arbiter of complex intent or capability selection. Preserve the richer evidence/plan path and ask when classification is uncertain.
- `MemoryCandidate.PromotionDecision` is a genuine cognitive policy surface; its current weighted-score/explicit-instruction mismatch is recorded under RC-15. The enum itself is fine; the promotion semantics are not.

Do not replace state-machine enums or permission/action protocols with unconstrained model-generated labels.

### Manual triage of the 19 `flat_threshold` candidates

These are mostly explicit resource and temporal bounds, not evidence that cognition is artificially fixed:

- **Retention/capacity bounds:** `MAX_CONTEXT_TURNS`, `MAX_EPISODIC`, mailbox capacity, TTS cache size, experience/link caps, goal/subgoal/criteria/dependency caps, audit/history limits. These protect finite phone memory and queues. Keep them bounded; make values versioned/configurable by measured device tier where appropriate, never unbounded or model-controlled.
- **Debounce/temporal bounds:** presence/absence confirmation, action dwell, condition-evaluation interval and warmup cooldown. These suppress sensor jitter, repeated actions and resource churn. Tune only from measured false-positive/latency/battery evidence.
- **Continuity-law gates:** minimum evidence-claim count and minimum interval are policy gates. They need tests and documented rationale, but should not be silently learned from a model's own outputs.

No blanket "remove hardcoded thresholds" change is justified. The root requirement is to distinguish resource/safety limits from cognitive hypotheses, measure the latter, and preserve fail-closed bounds.

### RC-19: The explicit intent/constraint layer is English-only despite the Arabic dialect detector

`IntentInference.extractExplicitIntent()` uses English wake phrases, question leads, command prefixes, teaching/correction phrases and substring checks. `extractConstraints()` recognizes English-only time, modality, detail and privacy words (including `forget`); `extractEntities()` uses quoted strings and capitalized English word pairs. The privacy constraint is triggered by `lower.contains("forget")` anywhere, so a positive reminder such as "don't forget X" can be misread as "do not store/remember this". The separate `MemoryForgetter.forgetFragment()` recognizes only prefix-form English `forget` and two Arabic spellings (`انسى`/`انسي`); `rememberFragment()` is English-only. The production composition also wires an Egyptian Arabic dialect detector, but that affects response register; it does not make these input-understanding rules multilingual. A model intent-classification seam may rescue some top-level routing, but it does not supply the missing typed constraints/entities or make deterministic privacy/forget handling reliable in Arabic/code-switching.

**Cause:** response dialect adaptation was added without a corresponding language-aware input semantics layer.  
**Consequence:** Egyptian Arabic or code-switched requests can be treated as generic conversation; explicit forget/privacy/time/action constraints and entity references can be missed.  
**Root correction:** build one language-aware intent/constraint/mention representation with dialect-aware tokenization and typed evidence. Preserve the original utterance and confidence; distinguish negation, correction, explicit remember/forget, commands and questions. The forget/remember directive recognizers must share one typed intent result with privacy constraints so a phrase like `don't forget` can never become a no-store directive. Use deterministic high-confidence parsing for safety/privacy controls and model-assisted interpretation only where needed; if uncertain, ask rather than silently route as conversation. Test Egyptian Arabic, Modern Standard Arabic, English and code-switching, including negated forget/remember and permission instructions.

### RC-20: The trusted Builder has two disconnected protected-path policies

`manifest/protected_paths.py` loads `manifest/workspace_manifest.yaml` and claims to enforce every read/write, but it is not imported by the active Friday Builder. The actual workflow invokes `tools/friday-builder/validate_patch_paths.py`, which has a separate hardcoded `PROTECTED_PREFIXES`, names, suffixes and Git-mode checks. PR #83 adds yet another separate `auto_merge_policy.py` for auto-promotion eligibility, but that policy is not merged. The YAML manifest still names the old JARVIS remote and `hf_space`; its Tier-2 guide refers to an obsolete owner name. The dead manifest's comments falsely imply it protects current execution, while the active validator and proposed auto-merge policy can drift from each other.

**Cause:** the JARVIS prototype policy was left beside the Android Friday Builder, while the active validator evolved independently.  
**Consequence:** developers can edit the YAML manifest believing it changes Builder protection when it does not; stale documentation overstates which security boundary is active.  
**Root correction:** define one authoritative, versioned policy schema with distinct decisions for (1) never-touch paths/secrets, (2) draft-only paths requiring owner review, and (3) narrowly eligible auto-promotion paths. Generate both patch validation and auto-promotion decisions from that same schema; do not maintain separate hand-edited lists. Retire/quarantine the unused JARVIS manifest after reference checks. Preserve fail-closed path traversal, secret suffix, symlink and submodule checks. Add contract tests proving the trusted workflow uses the canonical evaluator; remove all claims that dead code protects the Android app.

### RC-21: Legacy Termux/local-server and browser artifacts contradict the production Android architecture

The root `runtime/model_gateway/` targets a `127.0.0.1:8080` llama-server under `/sdcard/jarvis-repo`; `scripts/jarvis-*.sh` launches that old Termux process. `voiceforge/voiceforge_server.py` starts another local HTTP service and claims to back a Kotlin `VoiceForgeAdapter`, but the matching adapter/backend files are under `mobile/app/src/test`, not production. The actual production voice providers are `SherpaTtsVoiceProvider` and `SystemTtsVoiceProvider`; production local LLM inference uses the native JNI engine, not the Python gateway. `jarvis-browser/package.json` is an empty stub whose test script intentionally exits with failure. None of these root assets is included in the Android Gradle build.

**Cause:** old Termux/JARVIS experiments were left beside the Android-native Friday implementation without clear archival labels or dependency boundaries.  
**Consequence:** future workers can incorrectly assume Friday requires a local HTTP model/TTS server, Termux paths or an unfinished browser package, contradicting the no-server/on-device architecture.  
**Root correction:** trace all references and tests, then delete or move these assets to a clearly historical research archive only when their unique value is preserved. Remove production claims that these services back the app. The active app must remain self-contained: native local inference, Sherpa TTS/System TTS, and the GitHub workshop bridge as an optional development channel.

## 3. Architecture boundary to preserve

1. **Friday on Android:** persistent identity/continuity, local cognition and presence, privacy-minimized observation, memory/experience, permissioned device actions, safe capability runtime, self-study, development-intent formation, durable offline outbox and result consumption.
2. **Capability package/runtime:** versioned declarative or restricted interpreted packages with explicit contract, provenance, integrity, resource limits, narrow host APIs, permission declarations, evidence and independent rollback. No arbitrary downloaded DEX/JAR/native code or inherited app/Shizuku authority.
3. **Development gateway:** authenticated outbound phone-initiated transport; privacy review, bounded payloads, deduplication, retries, resumable checkpoints and revocation. No public ADB/SSH/shell endpoint and no repository write token or signing key in the APK.
4. **Repository workshop:** isolated source checkout/workspace, replaceable workers, research, patch generation, static/security checks, unit/instrumented/emulator tests, build, artifact provenance and trusted publication.
5. **Independent trust/promotion:** verify the exact immutable candidate SHA; require all configured checks to conclude success; fail closed on skipped, cancelled, neutral, stale or missing checks; protect control-plane/security/identity/permission/signing/update paths; preserve review for high-impact changes.
6. **Delivery and learning:** phone verifies package ID, signer, version and digest; uses owner-authorized installation; checks post-update health; preserves continuity/recovery; reports actual outcome so experience can change later decisions.

## 4. Dependency-ordered build plan

The phases are engineering work packages, not a prescribed developmental ladder for Friday. They may overlap when dependencies permit. No phase closes by percentage or source-file presence.

### Phase 0 — Reconcile authority, inventory and evidence

- Treat the latest authenticated owner instruction as highest authority, then `.ralph/VISION.md`, then the current PRD; historical plans and PR descriptions are subordinate.
- Reconcile README, `.ralph/FRIDAY_BUILD_PLAN.md`, `.ralph/REPO_FACTS.md`, docs, issues and PR descriptions against the actual main tree.
- Keep a requirement-to-code-to-test-to-evidence map with statuses: faithful, partial, missing, contradicted, unverified or overbuilt.
- Preserve the full 800-file inventory and the current 484-file Kotlin scanner output, but label heuristics as candidate findings.
- Semantically review all candidate findings by call graph, production reachability, runtime ownership and Vision context. Do not mass-delete enums, thresholds, Android APIs or security controls.
- Extend meaningful static/runtime review to Python workers, shell scripts, Gradle/build logic, GitHub Actions trust boundaries, C++/JNI/native libraries, manifests, resources and tests.
- Correct stale issue/PR dependencies and establish one canonical branch/dependency map.\n- Trace every import/reference to the root `core/`, `manifest/` and `tests/` Python prototype; remove/quarantine the unused JARVIS policy island after verifying that the active Builder validator remains the sole enforced policy. Do not run its stale tests as evidence for Friday.\n- Rewrite README's obsolete branch/PR references and correct the phone/workshop boundary. Remove the GitHub bridge guide's instruction to search the deleted private ZIP. Mark the Nov 16 progress scorecard historical rather than current.

**Exit evidence:** reconciled current-state report, exact source revision, no stale dependency claims, and a reviewed finding ledger that separates proven defects from scanner candidates.

### Phase 1 — Restore one green, reproducible main baseline

- Keep PR #89 unmerged until its architectural contradiction is resolved; do not accept a compile-only repair that restores on-device source building.\n- Restore the main build workflow from the valid pre-append structure; integrate emulator and release smoke checks exactly once and prove the jobs are actually scheduled.
- Fix the actual compile blocker in the correct design, then build the exact candidate commit and run the complete unit-test suite.
- Fix emulator readiness at the system-provider/PackageInstaller level; test early-boot and timeout cases, not just a long delay.
- Require emulator install, instrumentation execution, evidence upload and explicit supported/unsupported-device report.
- Ensure failures remain distinguishable: compile failure, test failure, emulator infrastructure failure, app crash and unsupported platform behaviour.
- Confirm cancelled/skipped jobs never count as green.

**Exit evidence:** exact main candidate passes debug APK assembly, all required JVM tests, workflow/security checks and the disposable emulator instrumentation gate. The workflow file has unique job IDs, passes actionlint/YAML validation, and a real run schedules each required job; a zero-job failure is a CI configuration failure, not a test result. Physical-phone claims remain separately labelled.

### Phase 1A — Repair native inference lifecycle before claiming local-model support

- Introduce explicit lock-aware state/error helpers so code already holding the model mutex never recursively locks it. Audit every state/error transition in generation, prewarm, suspend, resume and destroy.
- Copy the Java model path into owned native storage before releasing JNI string chars; never retain a pointer returned by `GetStringUTFChars` beyond its release. Keep prewarm threads joinable and owned by the model; cancel/stop and join them before freeing model/context state. Handle thread-start failure without crossing the JNI boundary.
- Make Kotlin native-handle access lifetime-safe: generation/health/cancel/suspend/resume must not race native destruction. Use a tested lifecycle lock/lease or equivalent reference-counted handle contract; preserve cancellation while preventing use-after-free.
- Move llama.cpp backend initialization/finalization to one process/service lifetime owner (or a tested reference-counted owner if service lifetime requires it); never call process-global backend finalization per model. Verify the pinned llama.cpp revision's exact behavior.
- Free every temporary tokenizer wrapper on all JNI paths and return health/error snapshots whose storage remains valid after the native lock is released.
- Validate VAD audio arrays, sample counts and null JNI results before passing pointers/counts to native code. Either implement real token callbacks and stop-sequence enforcement end-to-end, or remove/mark those APIs unsupported so the Kotlin layer does not advertise false streaming behaviour.
- Add deterministic lifecycle tests for model-path ownership after JNI string release, nested-lock regressions, prewarm/destroy races, cancel/destroy races, suspend/resume, multiple simultaneous model handles, repeated tokenizer calls, invalid VAD lengths, token callback delivery and stop-sequence enforcement. Run them on an emulator/device with bounded timeouts and collect native crash/log evidence.

**Exit evidence:** repeated create → prewarm → wait → generate → cancel → suspend → resume → destroy cycles complete without deadlock, leak growth or use-after-free; prewarm reads an owned model-path string; concurrent unload is safe; two model handles can coexist; malformed JNI inputs are rejected before native access; streaming callbacks and stop sequences match the Kotlin contract. Until then, local inference is not accepted as a reliable production capability.

### Phase 2 — Make the phone/workshop boundary real

- Remove or relocate dormant Android source-builder/evolution/mutant execution code after dependency and test migration; do not simply leave a second executable construction path in the APK.
- Keep safe declarative capability acquisition/execution as a distinct runtime concern under narrow host APIs.
- Implement the durable, versioned development-request outbox: owner/Friday intent provenance, evidence, source revision, privacy classification, risk, acceptance evidence, rollback expectation, unresolved interpretations and stable request ID.
- Implement authenticated outbound synchronization, idempotent delivery, offline queueing, retry/backoff, cancellation, checkpoint/resume and result reconciliation.
- Keep GitHub write credentials, worker secrets and signing material server-side. The phone must not receive broad repository credentials.

**Exit evidence:** offline request survives process death; reconnect sends it exactly once logically; the workshop receives it; Friday can inspect status/result; ordinary local Friday operation continues if the workshop is unavailable.

### Phase 3 — Complete the repository workshop

- Use isolated per-request workspaces and replaceable worker adapters.
- Workers receive only the minimum read context and no publishing/signing credentials.\n- Verify the trusted publisher's permission set against every API call. Keep the deliberate `workflow_dispatch` because PR events created with `GITHUB_TOKEN` require approval; grant `actions: write` only to the publisher or use an equally narrow GitHub App token. Never give that permission to the untrusted worker.
- Preserve WIP/checkpoints so interrupted or quota-limited workers resume instead of restarting.
- Compare worker output by task evidence, correctness, resumability, fidelity, latency and cost—not vendor identity.
- Keep untrusted patches separate from the trusted validator and publisher.
- Make source changes, tests, CI, emulator evidence, build artifacts and provenance traceable to the same candidate SHA.

**Exit evidence:** a Friday-originated request can be researched, implemented, regression-tested, resumed after interruption and returned with an immutable evidence bundle without manual dispatch.

### Phase 4 — Make validation and promotion trustworthy

- Require independent validation of exact candidate SHA and patch tree. For workflow-created PRs, dispatch validation explicitly, record the run ID/head SHA, wait for that exact run and re-read the PR head before promotion; absent, stale or mismatched checks are not success.
- Require debug build, complete unit tests, static/security checks, repository contract checks and emulator instrumentation.
- Test malicious patches that weaken tests, edit their own validator/workflow, alter signing/security paths, or change the candidate after validation.
- Repair failures on the same candidate branch while invalidating prior verification after every patch.
- Allow automatic promotion only for explicitly bounded low-risk changes after all required checks pass; keep protected paths owner-reviewed.
- Ensure GitHub branch protection/rulesets and required status checks actually enforce the policy.

**Exit evidence:** injected defect is detected, repaired with a regression test, all checks rerun against the repaired SHA, and an ineligible protected-path patch is blocked.

### Phase 5 — Safe release, installation and recovery

- Complete owner-controlled permanent signing key custody and five repository secrets; verify backup and fingerprint.
- Inspect the currently installed package signer and design the one-time debug-to-production migration before attempting an update.
- Implement release discovery and verification of application ID, version monotonicity, SHA-256 and signer before installation.
- Use an owner-authorized Android installer/Shizuku route; do not promise silent installation where platform authority does not permit it.
- Retain a compatible known-good recovery path, health-check startup and core services, and define recovery when Android blocks downgrade or replacement.
- Never claim rollback until it has been exercised against the actual signing/version constraints.

**Exit evidence:** same-signer update preserves identity/memory; wrong-signer and tampered artifacts are rejected; deliberately bad update is detected and recovery is demonstrated without falsely claiming unsupported rollback.

### Phase 6 — Prove the continuing individual and real development

- Join identity, continuity, experience, self-model and capability provenance into one coherent individual lineage; changing a model or capability must not create a new Friday.
- Build a real-composition Life Test with deterministic clocks and observable causal traces.
- Demonstrate an experience-caused change to a later choice after restart, with original episode replay blocked and the developmental mechanism disabled as a control.
- Distinguish owner-authored intent, Friday's interpretations, uncertainty and Friday's emerging preferences; do not turn the Vision into a literal command list or permanent interpretation module.
- Make failure → generalized lesson → capability gap → proposal a live path, not disconnected logging.
- Replace fixed scorecards with evidence of individual change and measurable regression/revision.

**Exit evidence:** one reproducible causal developmental example and one unfamiliar capability request handled through Friday's own intent/gap formation and workshop direction, without a human writing the implementation.

### Phase 7 — Complete practical Android embodiment and permission lifecycle

- Generate the permission/capability inventory from actual manifest, platform APIs, services, adapters and runtime registration.
- For each capability, record permission/privilege, privacy impact, battery/resource cost, offline availability, platform/OEM limits, test and revocation path.
- Ask contextually when a capability is first needed; explain the benefit; deep-link to Android settings where required; verify the grant; recover on revocation.
- Prove at least one full capability lifecycle: discovery → explanation → owner grant → registration → action → outcome evidence → revoke → safe failure/recovery.
- Keep special access owner-controlled and least-privilege; no automatic privilege escalation.

**Exit evidence:** real Android operation and revocation are verified, not inferred from manifest declarations.

### Phase 8 — Grow intelligence, perception, voice and host adaptation by measurement

- Benchmark resident and optional remote models on the exact Realme 9 Pro 5G using common tasks, verified license/checksum/runtime support, quality, factuality, latency, peak memory, heat and battery.
- Prove offline continuity and local useful cognition while treating network/cloud as optional extra resources.
- Complete voice input/output, interruption and recovery on the actual device; distinguish a command parser from broad understanding.
- Expand perception and semantic video only with measured capability evidence; do not label metadata/keyframes as semantic understanding or symbolic transitions as trained JEPA.
- Let resource adaptation change future scheduling/placement only when controlled experiments show quality-preserving improvements.

**Exit evidence:** reproducible exact-device evidence packs for each claimed capability, including thermal/battery soak and degraded/offline scenarios.

### Phase 9 — Privacy/history cleanup and final handoff

- Current-tree removal of the private conversation archives is complete, but reachable historical Git objects and cached references remain.
- Coordinate an all-ref history rewrite, check open PR refs and clones, and follow GitHub's sensitive-data removal procedure/support path.
- Preserve only curated owner-authored words and provenance; never reconstruct or republish private archives.
- Produce a signed, traceable release and owner-facing evidence pack only after all applicable gates pass.

**Exit evidence:** history purge is verified across reachable refs/caches as far as GitHub permits; first complete Friday release has exact source SHA, digest, signer and target-phone test evidence.

## 5. Rules that prevent another cycle of band-aids

- A symptom fix is not root-cause closure. Every incident must identify the causal mechanism, the class of failures it creates, the regression test and the evidence that the class is reduced.
- Never fix only the reported file if the same invalid state can be produced through another entry point.
- No parallel PR may claim a dependency on a closed/unmerged branch without an explicit replacement.
- No source file, passing synthetic test, static scan, APK compile or green unrelated workflow is proof of a user-visible capability.
- Do not count skipped work as success. Record success, failure, blocked, unsupported and not-run distinctly.
- Every state transition that can lose identity, memory, authorization, capability provenance or update recovery must be transactional or recoverable.
- Do not turn the plan into Friday's permanent architecture. The plan governs engineering evidence; it does not prescribe who Friday becomes.

## 6. Immediate execution order

1. Keep #89 out of main and redesign its phone/workshop boundary; the duplicate `nowMs` compile error is only one visible defect.
2. Repair #82's real system-provider readiness and get a reproducible emulator instrumentation pass.
3. Rebase/update #83's dependency and make emulator checks mandatory before any promotion claim.
4. Reconcile current main docs/issues/PR state, quarantine/remove the obsolete Python JARVIS island after reference checks, and publish the requirement-to-evidence ledger.
5. Repair and stress-test the native inference lifecycle; do not claim local model support until the lifecycle exit gate passes.\n6. Build the durable phone outbox → repository workshop → result feedback path.
7. Prove the Life Test and capability acquisition on the real production composition.
8. Finish release/update, permissions, exact-device validation and historical privacy cleanup.

**No APK handoff is implied by this plan. No unverified PR is approved or merged by it.**
