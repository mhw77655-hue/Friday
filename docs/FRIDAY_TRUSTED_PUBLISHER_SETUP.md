# Friday trusted publisher setup

Friday Builder separates an untrusted coding worker from a trusted publisher:

- The coding worker receives read-only repository access and produces a patch.
- The trusted publisher validates the patch using a validator copied from trusted `main`.
- CI failures on a proposal branch send Builder back to that exact candidate revision. It repairs the same PR branch rather than creating a disconnected repair proposal.
- A strict path policy permits automatic promotion only for low-risk application logic, capability implementations, tests and documentation, and only after all required checks pass.
- These high-risk paths—identity/memory, permissions, credentials, GitHub integration, diagnostics/crash reporting, accessibility, notifications, Shizuku, voice, security, signing, updates, settings, Gradle/manifest, and Builder/workflow control—remain draft for owner review.
- This workflow does not sign, release or install an APK.

## One-time repository setting

The publisher uses the repository-scoped GitHub Actions token. GitHub can reject PR creation with:

`GitHub Actions is not permitted to create or approve pull requests (createPullRequest)`

The trusted publisher requests `contents: write` and `pull-requests: write`. The coding worker remains read-only.

1. Open [Friday repository Actions settings](https://github.com/mhw77655-hue/Friday/settings/actions).
2. Under **Workflow permissions**, enable **Allow GitHub Actions to create and approve pull requests**.
3. Keep the publisher's explicit permissions narrow. Never grant write permissions or publisher credentials to the coding worker.
4. Configure `main` to require the build and virtual-phone checks. If branch protection also requires a human review for every PR, automatic promotion will be blocked and the PR will remain open; the trusted publisher does not bypass branch protection.

## Repair behavior

When a build fails on `friday-proposals/*`:

- Builder checks out the exact candidate branch and records its commit SHA.
- The trusted publisher verifies that the branch has not moved while Builder was working.
- It applies the repair patch to that exact revision, runs the trusted path validator, and pushes a normal fast-forward update to the existing PR branch.
- The new push triggers the complete build and emulator checks again.
- This repeats until checks pass, the worker cannot produce a safe patch, or the run times out.

A stale repair is rejected rather than applied to a newer branch. An owner-closed PR is not reopened.

## Automatic-promotion boundary

The auto-merge allowlist is intentionally narrower than the worker's ability to propose code. It permits only low-risk app source/resources, tests and docs. It blocks the identity and memory substrate, security-sensitive integrations, permissions, credentials, app update/signing paths, settings and all Builder trust-control files.

If the allowlist passes, the publisher marks the PR ready, waits for every check reported by GitHub, and asks GitHub to merge. It never bypasses branch protection. If checks fail, the proposal remains open and the failed candidate CI automatically re-enters Builder repair. If GitHub refuses the merge, the PR remains open.

High-risk proposals are deliberately left as drafts. They can still be implemented by Builder, but must not be silently promoted by the automation.

## Trust boundary

Do not solve this by giving the coding worker a personal access token or write permissions. Do not solve publisher permission errors by giving the coding worker any repository-write credential. The worker must never receive repository-write credentials. The publisher must continue to validate paths, reject symlinks/submodules, and rely on independent hosted checks before promoting a low-risk patch.
