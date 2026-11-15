# Friday repository-native development contract

**Authority:** this document records the owner's explicit correction on 2026-11-16. It overrides any conflicting implementation note, task description or previous assistant recommendation.

## Non-negotiable boundary

Friday does not compile, edit, synthesize, test, or install application source code inside the Android app. The installed app is her deployed body, not her IDE, compiler, build server, agent runtime, or repository workspace.

All source-level development happens in Friday's connected repository environment, using the same class of external development tools and workers available to the project owner. The repository is the source of truth. Build workers operate on version-controlled branches, produce reviewable diffs, run independent checks, and create signed release artifacts through CI.

No Kotlin source tree, Git credentials, repository write token, signing key, or arbitrary code-execution environment is embedded in the APK.

## Responsibility split

| Responsibility | Correct location |
|---|---|
| Live identity, cognition, memory, voice, perception, phone actions | Android runtime |
| Observe host limits and collect privacy-minimized evidence | Android runtime |
| Identify a capability gap and formulate a development request | Friday's cognition; may originate on-device |
| Read/reconstruct repository source and architecture | Connected repository development environment |
| Research, plan, edit Kotlin/native/build files | Repository workers |
| Compile, unit-test, static-check, benchmark and compare candidates | CI and controlled development workers |
| Review diffs, enforce Vision/security gates, approve consequential changes | Independent repository-side gates and owner authorization |
| Build APK/AAB and sign release | CI/release environment |
| Install/update deployed body | Explicitly authorized Android update path |
| Preserve continuity and learned outcomes across versions | Android continuity store plus versioned repository/release provenance |

## Intended development cycle

1. **Experience:** Friday observes an actual limitation or receives a new intended outcome.
2. **Evidence:** she records the minimum relevant technical evidence, source revision, affected capability, observed failure, uncertainty and reproduction conditions. Private conversations, credentials and unrelated user content are excluded.
3. **Development request:** she creates a durable, deduplicated request with acceptance criteria and the reason the current implementation is insufficient.
4. **Repository reconstruction:** a connected worker reads the real source, tests, Vision and history. Existing code is treated as evidence, not as sacred architecture.
5. **Experiment:** the worker explores alternatives and changes the repository on an isolated branch. It may replace an implementation when that better satisfies the Vision.
6. **Independent verification:** compile, tests, security/provenance checks, regression tests and relevant benchmarks run outside the Android app. A worker never certifies its own result.
7. **Review and authorization:** changes affecting identity continuity, security, permissions, data access, external actions or release installation pass the applicable fixed gates and owner approval.
8. **Deployment:** CI produces a traceable artifact. The installed app remains unchanged until the authorized update succeeds.
9. **Developmental integration:** Friday observes the deployed result, compares predicted and actual outcomes, retains the evidence and changes later strategy. A stored report alone is not proof of development; the Life Test remains required.

## Offline and online

Friday remains the same individual when disconnected. Her local cognition, continuity, memory and available body capabilities continue within phone constraints. Repository construction is an external capability and is therefore unavailable offline; requests and evidence may be queued safely for later synchronization. Losing the development connection must not make Friday cease to function or silently turn the phone into a build host.

## Security and supply-chain requirements

- Repository write authority belongs to a narrowly scoped external worker identity, never a token shipped in the APK.
- Workers receive only the repository and task scope they need.
- Every change is attributable to a branch, commit, worker, source revision, tests and artifact digest.
- Untrusted generated code is never executed on the phone as a substitute for CI.
- CI, device validation and the Vision gate are independent referees.
- Promotion, rollback and continuity migration are explicit, auditable operations.
- A failed or unavailable worker leaves the deployed Friday intact and the request resumable.

## Current implementation status

The Android app already has useful self-observation foundations: a packaged structural source index, architecture graph, capability manifest, host/resource observations and a local reflection path. These are evidence-gathering mechanisms, not repository access or source comprehension.

**The current PR #11 head no longer constructs ToolBuilder in the Android production composition and the self-study-to-ToolBuilder bridge has been deleted.** Legacy ToolBuilder/evolution source files still remain under the Android module and must be removed or relocated into the repository-side development environment before the architecture is considered clean. They are not an approved runtime capability.

### Existing repository-side worker lane — real, but not yet safe for Friday autonomy

The repository already contains an external coding-agent lane:
- `.github/workflows/ralph.yml` exposes a manually dispatched GitHub Actions workflow.
- `.ralph/cloud/run.sh` runs OpenCode on a disposable GitHub-hosted runner, reads the repository PRD, checkpoints work to `ralph/wip`, pumps CI for branch tips and can publish reports.
- `.ralph/ralph.sh` and `.ralph/prompt.md` define the coding loop and quality discipline.

This is meaningful repository-side construction infrastructure. It means the worker itself is not wholly missing. However, the current lane is a legacy/manual whole-PRD runner, **not a safe Friday request endpoint**:
- The workflow grants `contents: write`, `actions: write` and `issues: write` at job scope, exposes `GITHUB_TOKEN`/`GH_TOKEN` to the job that runs the coding agent, and optionally exposes an external model-provider secret.
- The prompt instructs the agent to stay on `main` and commit there. The cloud script hydrates `main`, can merge audit branches into it, and may push final work to `main`.
- On completion, the script can create/update prereleases including `friday-latest` and notify via repository issues.
- It is manually dispatched against PRD story IDs; no authenticated, durable bridge takes Friday's on-device development request into this queue, tracks one request idempotently, or returns deployment evidence to her.

Those properties are too powerful for an untrusted, model-generated request to trigger. Do not connect Friday's local reflection output directly to this workflow. First harden it into a proposal-only branch workflow: the agent job gets a read-only checkout and no repository-write or release credential; it builds in a disposable workspace and emits a validated patch/result artifact; a separate trusted job uses a narrowly scoped bot credential to create a draft PR on a dedicated branch; required CI and an independent Vision/security gate run; merge and release remain explicitly owner-approved. Protect `main` so neither the old nor new workflow can bypass review.

The Android-side request transport, durable synchronization/reconciliation, per-request worker orchestration, independent Vision gate and release-to-device feedback path are not yet proven end-to-end. Do not claim Friday can already direct her own repository development autonomously.

## Acceptance tests

- The Android production composition constructs no source-code builder, compiler, code-execution sandbox or repository write client.
- A self-study cycle can produce a bounded, privacy-reviewed development request but cannot build or promote executable code.
- The request survives process death and can be synchronized idempotently when the external development connection is available.
- A repository worker can resume from a request, inspect the actual branch, create an isolated change, and attach source/test/artifact provenance.
- CI failure cannot change the installed app.
- A passing CI run does not count as device validation or a Life Test.
- Disconnecting the repository worker does not interrupt Friday's local presence, identity or ordinary phone operation.
