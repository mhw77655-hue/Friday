# Friday: continuous presence and self-development

## Product model

Friday is one persistent individual, not a conversational state machine that alternates between existing and not existing. Listening, speaking, responding, observing, consolidating and developing are activities performed by the same continuing system. UI/body states such as LISTENING or THINKING describe an active I/O operation; they do not define whether Friday is present.

The app therefore has two concurrent lanes:

- **Relational/presence lane:** identity, memory, interaction, voice, environmental context and continuity.
- **Self-development lane:** host study, architecture study, capability-gap analysis, tool experiments, validation and resource optimization.

Self-development must yield immediately when voice interaction, thermal pressure, memory pressure or battery constraints require the resources.

## What is now implemented

1. Friday's production phone-action decision contract is owned by `FridayPhoneDecisionTool`.
2. The tool can use a TypeSafe Jev decision provider when a user configures a key.
3. Without a key, a local allowlist policy can execute only already-parsed low-risk commands; sensitive screen taps and text entry still require confirmation.
4. A configured-but-unavailable Jev service fails closed. Friday does not silently bypass a configured decision provider.
5. `ApplicationSelfStudy` studies the installed architecture graph, wiring reachability, capability manifest, host study and organ outcome summaries.
6. A WorkManager periodic worker schedules a bounded self-study slice, survives app process recreation/reboot, and is constrained by battery/storage health. The worker also checks live memory, thermal and battery headroom before studying.
7. Findings are persisted locally as a compact report without conversation text, transcripts or user app contents.

## Jev independence: exact boundary

TypeSafe's public description says Jev accepts state plus typed Choice/Score/Noul questions and returns structured answers/probabilities. It is a decision model, not a generative model. TypeSafe has not released Jev's weights; the official service is hosted. Friday cannot honestly claim to have cloned Jev's architecture or RLCD training.

The new Friday-owned tool preserves the useful *software contract*—bounded decisions, confidence-aware execution, confirmation and rejection—while making the external Jev provider optional. The local policy is deterministic and intentionally narrow today. It is not yet an independently trained semantic decision model.

Next evolution of this tool:
- generalize the native typed decision schema to Choice, Score and Noul;
- collect labeled action outcomes and calibrate local confidence against them;
- use Friday's resident language model to interpret requests into typed candidate actions;
- train/distill a compact local decision organ only from authorized, curated examples;
- compare local decisions and optional Jev judgments on the same held-out set;
- promote the local organ only when quality and safety gates pass.

## What “study her own application” means on Android

An installed APK does not contain the Kotlin source tree. Friday currently studies a machine-readable organ graph, capability manifest, dependency reachability, host telemetry and measured organ outcomes. This is real self-inspection, but it is not yet source-code comprehension.

The next build should generate a versioned source/architecture index at build time and package it as read-only metadata. Friday can then inspect class/module descriptions, dependency edges, known limitations, test coverage and build provenance without shipping the entire source tree or exposing secrets.

## What “rebuild her own tools” means

The current on-device ToolBuilder can create, execute, test and compare bounded declarative algorithms in an isolated mutant habitat. It does not yet compile arbitrary Kotlin/Android source. Friday must not overwrite her installed APK or bypass Android package signing.

The intended development loop is:

`observe → identify a real capability gap → research mechanisms → design a candidate → build in isolation → test against fixtures and adversarial cases → benchmark on this phone → compare quality/cost → promote with provenance → retain rollback`

For pure tool changes, promotion can update a versioned declarative capability through the guarded registry. For Android-native code or manifest changes, the output must be a new signed app build, verified and installed through Android's normal update mechanism. The currently installed process remains intact until that update is accepted.

## Resource and scheduling rules

- The host governor is authoritative for available memory, CPU, thermal and battery limits.
- Self-study is low-frequency and deferrable, not a constant inference loop.
- WorkManager's periodic schedule is approximate and may be delayed by Android battery optimizations; it is not a promise of exact 30-minute execution.
- The study slice is skipped when memory headroom is below the configured threshold, thermal pressure is high, or battery is low and the phone is not charging.
- A skipped slice is not a failure and does not interrupt Friday's presence lane.

## Open work

- Generate and package the source/architecture index from the real Android source tree.
- Turn self-study findings into a prioritized, deduplicated development queue.
- Let Friday invoke ToolBuilder on safe, bounded tool gaps under a development budget.
- Expand ToolBuilder beyond the five current declarative behavior types.
- Add local typed-decision calibration and held-out evaluations.
- Integrate signed build, provenance, install approval and rollback for Android-native improvements.
- Validate voice control and self-study behavior on the actual Realme 9 Pro 5G, including process death, Doze, thermal load and battery use.
