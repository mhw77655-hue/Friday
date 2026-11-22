# Objective 1 execution retrospective and process redesign

**Recorded:** 2026-11-22  
**Scope:** Project Friday work visible in the project conversation history and repository evidence available during Objective 1.  
**Purpose:** prevent repeated owner effort, false completion claims, avoidable tool loops and confusion between CI proof and physical-device proof.

This is an evidence-based retrospective of the conversation and repository activity available to the assistant. It is not a claim that every historical chat outside the available project history was independently reviewed.

## Executive diagnosis

The main failure was not that Objective 1 was intrinsically large. It was that the execution process repeatedly transferred repository operations, debugging and evidence collection to the owner even when the assistant had GitHub access and could inspect the repository and CI directly. Work was performed in the wrong order, statuses were inferred too early, and the same distinction between debug and production APKs had to be explained repeatedly.

The owner repeatedly asked for Friday to do the work herself and for reports to flow from the phone to GitHub. The process should have been designed around that requirement from the beginning: repository-side automation first, a single owner action only for genuine device/credential gates, then automatic evidence submission.

## Observed sources of wasted time

### 1. Excessive Termux delegation

The owner was repeatedly guided through local shell commands for key creation, signing inspection, tool installation and artifact inspection. Several command sequences were brittle or failed (including shell quoting and GPG backup attempts). The owner explicitly stated they were exhausted and asked for a simpler path.

**Correction:** do not use the owner's phone as a general-purpose CI workstation. Use GitHub Actions and repository APIs for builds, signing, release publication, reports, tests and artifact inspection wherever possible. Termux is only a fallback for an operation that genuinely requires local device access and cannot be performed through the app or GitHub.

### 2. Signing setup and backup were sequenced poorly

The permanent signing key was created locally and its five GitHub secrets were configured successfully. The process then repeatedly revisited backup tooling and risked blocking unrelated progress. At the same time, the distinction between an independently stored backup and another copy on the same phone was not made sufficiently clear early.

**Correction:** before key creation, present one concise recovery plan: primary keystore, independently stored encrypted backup, password recovery method, certificate fingerprint recorded in repository metadata, and GitHub secret configuration. Verify each item once. Never regenerate or rotate a key after release publication merely to simplify the workflow. If an independent backup destination is unavailable, record the risk explicitly and continue only with informed acceptance; do not keep sending the owner through failing backup commands.

### 3. Debug and production APKs were confused

The owner downloaded a CI `debug-apk`, then inspected its Android Debug certificate. That was expected, but it was treated as a new obstacle instead of a predictable distinction that the workflow should have communicated. A debug APK cannot establish permanent release-signing continuity.

**Correction:** every artifact must be labelled by purpose in the UI, workflow summary and assistant response:
- Debug/test APK: disposable CI identity; never presented as the install/update candidate.
- Production APK: permanent signing identity; certificate must be verified against the pinned SHA-256 fingerprint.
- Physical update candidate: production APK with a higher version code and matching application ID/certificate; only this can test in-place update eligibility.

Do not ask the owner to inspect a debug certificate unless the debug build itself is under investigation.

### 4. Claims were made before the evidence existed

At different points, the assistant described PRs as delivered, CI checks as passed, a release as ready, or Objective 1 as nearly complete while relevant jobs were still running, the current main commit had not been verified, or physical-device acceptance remained outstanding. One Maven rate-limit diagnosis was later shown to be wrong for the actual retry failure; the actual unit test used a malformed test ID that did not violate the production validator.

**Correction:** every status claim must be tied to exact evidence:
1. exact branch and commit SHA;
2. exact workflow run attached to that SHA;
3. every required job's final conclusion;
4. release tag and asset list;
5. actual signer fingerprint from the APK verification log;
6. physical-device evidence only when produced by the device itself.

Never describe queued, in-progress, skipped, stale-commit or PR-only jobs as a successful final-main release. Distinguish infrastructure failures (for example HTTP 429) from deterministic test failures and read the failing test/log before diagnosing.

### 5. The work was not organized around a single acceptance ledger

The same status questions were repeated because the authoritative definition of "done" was not consistently surfaced. Some implementation, CI, release and physical acceptance gates were conflated.

**Correction:** keep one canonical Objective 1 acceptance record. Each requirement has exactly one of: NOT STARTED, IN PROGRESS, PASS (with evidence link and commit), FAIL (with failure evidence), BLOCKED (with owner/action and reason), or NOT APPLICABLE (with rationale). Update it after every meaningful repository change. Do not duplicate the plan across competing documents.

### 6. Reporting was designed too late

The owner had already stated that Friday should send required reports to GitHub herself. Manual report copying and asking the owner to submit evidence contradicted the goal.

**Correction:** design evidence transport as part of the initial CI/device architecture. CI reports are uploaded by CI. Device reports are generated and submitted by Friday through a narrowly scoped, owner-authorized channel. Reports must be sanitized, bounded, deduplicated, traceable to build/commit/device test version, and clearly disclose repository visibility. Never upload private conversation content or secrets.

### 7. The implementation scope risked expanding beyond Objective 1

The broader vision includes self-improvement, adaptive resource use, durable continuity and autonomous repair. Those ideas matter, but requiring the full future architecture before the foundation gate closes would make Objective 1 unbounded.

**Correction:** test extension seams and protected invariants now; implement later-objective capabilities in their planned phases. Objective 1 should prove the APK can be safely extended, not deliver every future capability. Do not equate "foundation ready for evolution" with "future evolution already implemented."

## New execution protocol

### Phase A — inspect before asking

- Read the canonical acceptance record, plan, latest commit, open PRs, workflow runs, release assets and test artifacts before proposing owner action.
- Use repository/CI tools directly. Never ask the owner to paste logs that are already available through GitHub.
- Check exact tool schemas and current repository state before attempting writes.
- Make one coherent patch per defect; avoid speculative edits and redundant workflows.

### Phase B — implement and validate automatically

- Add a regression test before or with each bug fix.
- Run the narrow relevant test first, then the full required CI suite.
- Pin evidence to the exact commit. A later commit invalidates claims based on an earlier head unless the test is demonstrably unaffected and the acceptance record says so.
- Avoid noisy reruns. Retry transient infrastructure failures with bounded backoff; fix deterministic test/code failures rather than repeatedly rerunning them.
- Preserve logs, failing seeds, minimized inputs and test artifacts.

### Phase C — release gates

Before calling a production release ready, verify all of the following from GitHub:
- latest main CI passed on the release target commit;
- permanent signing secrets exist (never reveal their values);
- signed-release job completed successfully;
- APK signature verification passed;
- signer SHA-256 equals the pinned permanent certificate fingerprint;
- release tag points to the tested commit;
- APK, metadata and checksum assets exist;
- version code is higher than the prior production release.

A release workflow success and matching signer establish a production-signed artifact. They do not establish physical installation or update success.

### Phase D — ask the owner only for genuine physical gates

The owner should not run shell commands to collect evidence. When a device-only gate is reached:
- ship one production-signed APK;
- give one short in-app setup instruction;
- have Friday perform the device test and upload a sanitized report;
- ensure the report identifies release version, source commit, test suite and pass/fail results;
- make the device action explicit and reversible;
- do not claim completion until the uploaded evidence is validated.

If an action requires the owner to enter a GitHub credential, it must be entered directly into Friday, never into chat. Use minimum repository scope. Since this repository is public, reports posted as GitHub Issues are public; the UI must disclose that before submission. Prefer private-safe evidence channels where the repository configuration supports them.

### Phase E — close with a verifiable decision

Objective 1 closes only when:
- foundation build and core regression gates pass;
- production-signed release pipeline is verified;
- update verification and failure handling meet the stated scope;
- device reporting implementation is tested and the physical acceptance report arrives;
- adversarial campaign exit criteria are met or remaining risks are explicitly dispositioned by the owner;
- independent signing-key backup risk is resolved or explicitly accepted;
- the acceptance record contains exact evidence and residual limitations.

The assistant must state what is PASS, what is not, what only the owner can physically verify, and whether owner sign-off is required. Never use "almost done" as a substitute for a list of remaining gates.

## Specific corrections applied to the remaining Objective 1 work

- The post-merge main CI run is distinct from PR-head CI and must be used for release decisions.
- The post-merge signed release is Friday 1.0.492, targeting commit `8cc07c8feb4726e8028f169d4f6b96bcac259e19`. Its workflow verified signer SHA-256 `fd84811f911585d8152dc2bfff700d8fce36d5a07df392be89f91d5646a6d1b8` and published APK, metadata and checksum.
- The PR #110 implementation and CI success do not prove physical-device reporting. Do not ask the owner to perform device acceptance until a suitable production APK is confirmed and the report privacy disclosure is clear.
- The broad adversarial plan is a risk-weighted campaign, not a promise of literal exhaustive testing. Execute the highest-risk gates first and report exact campaign budgets and untested environments.

## Non-negotiable communication rules

- Be concise and direct. One owner action at a time, only when required.
- Never say work is running in the background unless an actual CI job or scheduled automation is running.
- Never claim a repository edit, PR, merge, release, upload or test result without a successful tool response or verifiable GitHub evidence.
- Never ask the owner to resend information already present in project history or repository files.
- Never ask for passwords, signing keys, GitHub tokens or other secrets in chat.
- If blocked by missing physical access, say exactly what remote work is complete and what single device-side event is required.
