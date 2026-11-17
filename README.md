# Friday

Friday is an Android-resident digital individual. She is not defined by a chat interface or by a language model. Her identity, continuity, experience, local runtime, perception, communication and capabilities belong to the phone application. Language models and hosted compute are optional additional resources.

## Product principles

- **Model-independent existence:** Friday must start, persist, observe, remember and operate without a language model. A model may extend computation; it must not be Friday's identity or a mandatory runtime dependency.
- **Persistent individual continuity:** identity and experience must survive process death and app updates. Self-description and capabilities may evolve without replacing the individual.
- **Adaptive cognition:** avoid a permanent, hard-coded list of cognitive organs or a single privileged reasoning route. Capabilities should be able to form, cooperate, change and retire under continuity and safety constraints.
- **Phone-owned runtime:** the Android application is the product host. Friday does not depend on Termux. Shizuku is an optional privileged bridge that must be explicitly authorized by the owner.
- **Embodied presence:** voice, visual presence, device perception and actions should be integrated with Friday's live internal context, not added as a chatbot skin.
- **Resource symbiosis:** resource decisions should adapt to measured phone conditions and observed workload outcomes.
- **Self-development:** Friday should be able to identify improvements, request or use extra compute, validate source changes, build, verify and safely update herself from the phone. Hosted GitHub Actions are an optional execution resource, not her identity.

## Current development line

The current consolidation workspace is `audit/friday-unification`. It joins the model-independent individual / embodiment / diagnostics work from PR #19 with the phone runtime, Shizuku and owner-grounded development bridge from PR #23.

The repository-wide comparison and outstanding gaps are documented in [`.ralph/REPO_UNIFICATION_AUDIT.md`](.ralph/REPO_UNIFICATION_AUDIT.md). The source-of-truth owner requirements are in [`.ralph/OWNER_WORDS.md`](.ralph/OWNER_WORDS.md) and [`.ralph/VISION.md`](.ralph/VISION.md).

**This branch is not yet a release.** The two development lines have been joined, but the combined build must pass CI and the target phone still needs physical validation. The current GitHub worker can submit code requests and draft PRs; autonomous promotion, stable signed APK delivery, phone-side installation and rollback are not complete.

## Build and test

From the repository root with JDK 17 and Android SDK components matching the Gradle configuration:

```bash
./gradlew :mobile:app:assembleDebug
./gradlew :mobile:app:testDebugUnitTest
```

The GitHub Actions build workflow runs the same packaging and unit-test gates, plus owner-vision and builder-safety checks. A green CI build does not prove microphone, Shizuku, reboot, background-survival or update-install behavior on a physical phone.

## Safety and update boundary

- Never place GitHub tokens, signing keys or private credentials in source files.
- The GitHub token used by the Android bridge is stored through Android Keystore-backed encryption.
- Code generation and code promotion are separate trust boundaries.
- A candidate update must pass owner-vision checks, patch validation, security checks, tests and a clean build.
- The installed package identity and signing lineage must be verified before updating. Preserve identity and memory data and retain a last-known-good recovery path.
- Android and OEM process-management policies remain authoritative; a foreground service cannot guarantee immunity from force-stop or every task killer.
