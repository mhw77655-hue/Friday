# Friday repository-native development contract

**Authority:** `.ralph/FRIDAY_DELIVERY_PLAN.md` is the sole active delivery plan. This document defines the boundary between Friday's installed Android body and repository-side engineering; it is not a competing roadmap.

## Non-negotiable boundary

Friday does not compile, edit, synthesize, test or install application source code inside the Android app. The installed app is her deployed body, not her IDE, compiler, build server, agent runtime or repository workspace.

All source-level development happens in the connected repository environment. The repository is the source of truth. The canonical product line is `main`; authorized changes are committed directly to `main`. Do not create feature branches, proposal branches, parallel product lines or pull requests as a substitute for direct mainline work. CI must validate the exact main commit and release provenance.

No Kotlin source tree, Git credentials, repository write token, signing key or arbitrary code-execution environment is embedded in the APK.

## Responsibility split

| Responsibility | Correct location |
|---|---|
| Identity, cognition, memory, voice, perception and authorized phone actions | Android runtime |
| Observe host limits and collect privacy-minimized evidence | Android runtime |
| Identify a capability gap and formulate a bounded development request | Friday's cognition; may originate on-device |
| Read/reconstruct repository source and architecture | Connected repository development environment |
| Research, plan and edit Kotlin/native/build files | Repository workers |
| Compile, unit-test, static-check, benchmark and compare candidates | CI and controlled development workers |
| Enforce plan, security, provenance and continuity gates | Independent checks and owner authorization where required |
| Build and sign release artifacts | Trusted CI/release environment |
| Install/update the deployed body | Authorized Android update path |
| Preserve continuity and learned outcomes across versions | Android continuity store plus versioned repository/release provenance |

## Intended development cycle

1. **Experience:** Friday observes an actual limitation or receives a new intended outcome.
2. **Evidence:** she records the minimum relevant technical evidence, source revision, affected capability, observed failure, uncertainty and reproduction conditions. Private conversations, credentials and unrelated user content are excluded.
3. **Development request:** she creates a durable, deduplicated request with acceptance criteria and the reason the current implementation is insufficient.
4. **Repository reconstruction:** a connected worker reads the actual `main` source, tests, delivery plan and relevant history. Existing code is evidence, not sacred architecture.
5. **Implementation:** the worker changes the canonical `main` line in small, sequential, reviewable commits. Before every write it fetches the current main SHA and refuses stale writes. Do not run concurrent writes against the same file or ref.
6. **Independent verification:** CI compiles, tests and checks the exact resulting commit. A worker never certifies its own result by assertion.
7. **Review and authorization:** changes affecting identity continuity, security, permissions, data access, external actions or release installation pass their explicit gates and owner approval.
8. **Deployment:** trusted CI produces a traceable artifact. The installed app remains unchanged until the authorized update succeeds.
9. **Developmental integration:** Friday observes the deployed result, compares predicted and actual outcomes, retains evidence and changes later strategy. A stored report alone is not proof of development; the Life Test remains required.

## Offline and online

Friday remains the same individual when disconnected. Her local cognition, continuity, memory and available body capabilities continue within phone constraints. Repository construction is an external capability and is therefore unavailable offline; requests and evidence may be queued safely for later synchronization. Losing the development connection must not make Friday cease to function or silently turn the phone into a build host.

## Security and supply-chain requirements

- Repository write authority belongs to a narrowly scoped external worker identity, never a token shipped in the APK.
- Workers receive only the repository and task scope they need.
- Every change is attributable to a main commit, worker, source revision, tests and artifact digest.
- Untrusted generated code is never executed on the phone as a substitute for CI.
- CI, device validation and the delivery-plan acceptance gates are independent evidence sources.
- Promotion, rollback and continuity migration are explicit, auditable operations.
- A failed or unavailable worker leaves the deployed Friday intact and the request resumable.
- No workflow may silently skip, cancel or neutralize required verification and then report success.

## Current implementation truth

The Android app contains self-observation, diagnostics, capability construction and evolution-related code. Their presence does not prove that they are connected to the production path or that Friday can autonomously develop the repository. The production composition, actual source access, request delivery, independent verification and phone-side deployment feedback must each be proven separately.

The existing repository contains historical Ralph/PRD/branch-based automation. It is not the active product authority and must not override the owner's direct-main workflow or the sole delivery plan. Keep useful validators and safety checks only where they are wired into the current mainline process; retire stale workers, branch publishers and duplicate planning loops after confirming their references.

## Acceptance tests

- The Android production composition constructs no source-code builder, compiler, code-execution sandbox or unrestricted repository write client.
- A self-study cycle can produce a bounded, privacy-reviewed development request but cannot directly build or promote executable code.
- The request survives process death and can be synchronized idempotently when the external development connection is available.
- A repository worker can resume from a request, inspect current `main`, make a sequential mainline change and attach source/test/artifact provenance.
- CI failure cannot change the installed app.
- A passing CI run does not count as device validation or a Life Test.
- Disconnecting the repository worker does not interrupt Friday's local presence, identity or ordinary phone operation.
