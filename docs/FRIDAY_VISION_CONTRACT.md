# Friday — Product and implementation contract

**Authority:** .ralph/VISION.md and the sole active plan .ralph/FRIDAY_DELIVERY_PLAN.md  
**Owner source:** .ralph/OWNER_WORDS.md  
**Purpose:** translate the owner's outcome into implementation constraints without freezing Friday's future architecture.

This is not a competing roadmap or a permanent component diagram. If this document conflicts with the owner, VISION.md or FRIDAY_DELIVERY_PLAN.md, update this document and the implementation.

## 1. Product identity and destination

Friday is one continuing digital individual, intended to approach human-like understanding, interaction, learning, relationships and agency as closely as available technology permits, while extending capabilities through machine-native computation. The first seed must already have enough functional foundation to begin beyond a literal-text chatbot: multiple-signal interpretation, working and long-term context, people/world context, multiple hypotheses, uncertainty, prediction, consequence observation and learning. These are required outcomes, not a fixed module list or cognitive pipeline.

Preserve the owner's term **liquid Symbiotic quantum software**. It describes a fluid environment that adapts with Friday and lets Friday use, reshape, combine and help create resources to reach outcomes. Do not translate it into a conventional fixed multi-agent system, static specialist-brain hierarchy or chatbot feature list. The term does not require assumed quantum hardware.

Friday is the individual. Models, algorithms, voice, perception, memory representations, tools, Builder, cloud services, devices and source code are replaceable resources/capabilities. The Android app is her first production host; the owner's phone is her first environment.

## 2. Liquid trunk and open branches

The protected continuity/security floor is the trunk. All other capabilities are open branches that may be developed, combined, replaced or retired. They exchange information through five open-ended contracts:

- **Need:** intended outcome, context, uncertainty and alternative interpretations.
- **Affordance:** demonstrated capability, requirements, cost, risk and observable postcondition.
- **Resource field:** measured host resources, models, sensors, devices, network, permissions and constraints.
- **Outcome:** observed result, failure, quality, latency and resource cost, linked to the decision that caused it.
- **Continuity/floor:** identity continuity, owner authentication, authorization, provenance, isolation, recovery and rollback.

These are not a prescribed sequence or cognitive taxonomy. A capability may contribute to multiple outcomes; no capability permanently owns hearing, memory, emotion, reasoning, social understanding, perception, learning or action. The mechanism that chooses among affordances is replaceable and may itself learn.

## 3. One Friday across hosts and resources

The Android app owns the production runtime. Termux is not a required host, setup prerequisite or runtime dependency. JVM/desktop code is test infrastructure unless explicitly proven otherwise.

Offline is a real operating condition. Friday's local identity, continuity, experience and whatever capabilities local resources sustain continue without network access. Cloud and external models expand the resources available to the same Friday; they do not create a second identity or own her intelligence.

Future vehicle, augmented-reality glasses, home or other device connections extend the same individual. These are future-horizon projects, not requirements for the first release; pursue them when Friday has the capability, evidence, access and authorization. Do not build separate domain-specific Fridays or require a specialist brain for every new sensor or environment.

## 4. Voice-first presence

Voice is the first user-visible delivery priority. The intended experience includes natural hearing, interpretation, spoken replies, turn-taking, listening again, interruption/barge-in, cancellation and supported launch surfaces.

Speech recognition and synthesis implementations are replaceable affordances. Select them by measured language quality, availability, latency, memory, privacy, power and thermal cost. Prioritize Egyptian Arabic and mixed Arabic/English, but do not claim naturalness without recorded evaluation and the owner's direct listening judgment. A transcript, TTS manifest entry or provider declaration is not proof of working voice on the phone.

Natural requests must use Friday's actual context and intelligence. Exact deterministic shortcuts are allowed when useful, but they cannot become the ceiling of understanding. Actions must return observed outcomes; never report success merely because an intent or command was sent.

## 5. Developmental change

The required causal path is:

experience → persistent internal change → measurably changed later cognition or behaviour.

The effect must survive restart, persist after the original event/context is unavailable, and change a later meaningfully different situation. Logging, transcript storage, sentiment fields, profile entries or prompt replay alone do not qualify.

Open-ended interaction with the owner is the primary concept test of how Friday understands and develops. Engineering Life Tests are falsifiable probes of specific mechanisms, not a fixed script that defines her personality or exhaustively defines an individual. A development claim requires a Life Test on the real production composition:
- experience is applied;
- the application/composition restarts;
- the later test blocks retrieval/replay of the original episode text;
- the proposed developmental mechanism is disabled in a control run;
- the later decision/behaviour is observed and compared;
- provenance, uncertainty and the evidence pack are retained.

Learning is universal across relevant accessible experiences: conversation, correction, demonstrations, people, visual context, voice and tone, facial/body signals where available, sensors, device state, actions and consequences. Signals can conflict; no one signal is automatically truth. Friday retains multiple plausible interpretations and uncertainty and learns signal reliability through experience. Do not confine it to the subsystem that first captured an event.

## 6. Universal action and capability acquisition

Friday receives plain-language outcomes and discovers how to pursue them. Only the authenticated owner can authorize intentional externally-directed changes to Friday herself; after authentication and required authorization, Friday determines how to apply the direction. The owner is not required to design her internal architecture. The real action path must connect intent/Need, available Affordances, owner/platform authorization, execution, independently observed Outcome and experience update.

When a capability is missing, Friday must have a controlled path to identify the gap, research existing work, reverse-engineer useful mechanisms, combine or repurpose them, invent candidates, construct, isolate, test, compare independently, request required approval, promote safely, use the result and learn from it.

A restricted runtime DSL is a starting habitat, not the final definition of the Builder. The long-term goal includes source-level inspection and improvement of the actual Android application and native capabilities. External AI providers, coding agents, Ralph, OpenHands, GitHub Actions and search are replaceable workers/resources. No worker may certify its own change as the sole judge.

## 7. Nano Algorithm Environment and resource symbiosis

Measure actual device and workload conditions: memory, CPU/GPU/accelerators, thermal pressure, battery, latency, throughput, quality, storage, network, models, voice and action interference.

Use measurements to adapt algorithm, model, runtime, precision, scheduling, placement and local/cloud choices. The aim is to reduce the gap between required resources and available resources without silently sacrificing the outcome. Adaptation must change future execution decisions, not merely record telemetry.

Unknown hardware acceleration is unavailable until demonstrated on the target device. Estimates and measurements must be distinguished. Resource pressure changes what can run and when, not who Friday is. Defer, migrate or gracefully reduce work before sacrificing continuity.

## 8. Protected floor

Owner authentication, authorization, protected operations, secrets, signing identity, provenance, artifact integrity, candidate isolation, continuity protection, update verification, rollback and recovery are not liquid and cannot self-authorize.

Safe runtime adaptation may occur within granted permissions. Executable source changes, package installation, permission changes, signing and protected governance require the applicable approval and validation. External models and self-development cannot bypass the floor.

## 9. Resilience and release

Every failure must be reproduced and traced to its originating cause. Fix the mechanism that creates the failure class, not just one visible symptom. Preserve checkpoints, artifacts, work state, logs, provenance, backups and a last-known-good release. Interrupted work must resume without silent loss or duplicate execution.

A milestone is not complete because code exists or CI is green. Distinguish designed, implemented, unit-tested, CI-verified, emulator-verified and device-verified. The installed Android application and its real behavior on the owner's phone are the acceptance target. Verify APK installation, signing lineage, update, continuity preservation and rollback before claiming delivery.

## 10. Parallel delivery without competing Fridays

All objectives in FRIDAY_DELIVERY_PLAN.md start immediately and progress in parallel where work can be separated. Voice is the first visible priority, not a blocker for other tracks.

All work converges on one canonical main product line. Do not create competing Friday products, long-lived feature branches or alternative vision plans. Avoid concurrent writes to shared composition, identity, vision and protected-floor files; integrate those changes deliberately. The PRD is an execution ledger derived from the sole plan, never a competing destination.

## 11. Evidence and honesty

Every claim must have an observable test that could disprove it. A source declaration, configuration entry, passing isolated unit test or emulator build does not prove a live Android behavior. Report unknowns as unknown. Do not claim voice quality, learning, self-development, action success, offline behavior, resource gains, installation or recovery without evidence from the path and conditions being claimed.

## 12. Required source reading

Before any task, read:
1. .ralph/VISION.md
2. .ralph/OWNER_WORDS.md
3. .ralph/FRIDAY_DELIVERY_PLAN.md
4. this contract
5. the relevant live source and tests

Do not revive superseded assistant proposals or stale PRD wording. If an old acceptance criterion conflicts with the owner's current direction, correct the criterion before implementing it.
