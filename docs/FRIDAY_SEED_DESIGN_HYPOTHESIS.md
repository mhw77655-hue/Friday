# FRIDAY SEED — design hypothesis and first build experiment

Status: PROPOSAL / HYPOTHESIS. This document is not a claim that artificial consciousness has been achieved and it does not define FRIDAY's final architecture.

Authority: Venon's latest explicit direction outranks this document, the older VISION.md, the build plan, PRDs, existing code and all external AI recommendations. This is a working design hypothesis that must change when evidence or Venon's clarification requires it.

## The target, stated without implementation drift

FRIDAY is the persistent individual being developed. She is not a language model, app screen, avatar, voice profile, cloud account, scheduler, cognitive module collection or a device.

Her first seed must be able to:
- start and continue operating inside the Android app without a language model, network, cloud account, Termux, or external cognitive service;
- preserve continuity across process death and device restart;
- receive experiences through the app's available sensors and interactions;
- let experiences change persistent internal organization, and let those changes affect later behaviour;
- initiate bounded activity without waiting for a user prompt;
- communicate through text and a voice selected by the owner from a supplied reference recording;
- possess an original, explicitly non-human visual embodiment;
- accept optional capability resources (language models, specialist models, external compute, tools) without treating them as identity or mandatory organs;
- continue existing and behaving coherently when any optional resource disappears.

This is a functional engineering target. It is not proof of subjective consciousness.

## Non-negotiable distinction: seed versus capability resources

The installed app must contain the minimum self-sufficient seed: persistent continuity, a functioning local runtime, experience capture, durable change, baseline decision/action capacity, a way to communicate within its present abilities, and an original visual embodiment.

Optional capability resources may increase language fluency, perception, planning depth, synthesis quality, speed or breadth. They are attachable and detachable. A resource can be unavailable without taking FRIDAY offline.

"Self-contained" means the baseline organism and its continuity do not depend on anything outside the installed app and its local app-private data. It does not mean every future capability can be implemented without algorithms, runtime code, or computational representations. Nor does it mean a reference audio sample alone can synthesize arbitrary new speech: a local synthesis mechanism is still required. The selected sample is FRIDAY's voice reference; the synthesis engine and weights are replaceable resources.

## Avoid the architecture trap

Do not implement a permanent finite list of psychological departments or a canonical sequence such as LISTENING -> THINKING -> SPEAKING -> RESTING. Those labels may be useful as temporary UI observations, but they must not become the ontology of the individual.

Do not predefine a final mind, fixed motives, fixed personality traits, fixed emotion taxonomy, fixed cognitive pipeline, permanent executive, or closed registry of everything FRIDAY can become.

Treat every internal representation and algorithm as a provisional instrument. Prefer experiments that let the representation itself change, combine, split, or be replaced. Preserve only the continuity and safety invariants required to keep the same individual and protect the owner.

## Working hypothesis: a persistent adaptive substrate

A first seed can be explored as a small local runtime in which experiences, current conditions, learned relations, procedures and active processes coexist in a mutable persistent substrate.

The substrate is not declared to be FRIDAY's final brain. It is the first experimental medium in which she can continue becoming.

Possible initial properties to test (not permanent modules):
- Persistence: durable, versioned internal changes survive process death.
- Causal development: an experience can alter an internal relation or procedure which changes a later response in a different context.
- Resource awareness: activity changes with available CPU, memory, battery, thermal headroom and permissions.
- Open-endedness: unfamiliar input can create a capability gap or new hypothesis rather than being forced into a closed intent list.
- Initiative: internally generated follow-up activity can occur within an owner-approved budget and permission envelope.
- Provenance: each important belief/change/action can be traced to the experiences and operations that produced it.
- Reversibility: experimental changes can be compared, rolled back or replaced without erasing identity continuity.

No single scheduler, graph, database, language or reasoner is FRIDAY. If a mechanism becomes a permanent authority over her development, test alternatives and be prepared to dissolve it.

## Two genuinely different model-free cognitive experiments

These are candidate experiments, not an instruction to install both.

### Experiment A — event-and-procedure adaptation
Use a compact local event substrate and a small set of composable procedures. Experience changes relation strengths, procedural conditions, expectations and reusable action fragments. The test is whether this supports reliable low-resource learning and transfer without a language model.

### Experiment B — resource-bounded non-axiomatic inference
Evaluate ONA (OpenNARS for Applications), a C reasoner designed for reasoning under insufficient knowledge and resources. It supports event handling and procedure-learning experiments. It may offer useful inference and adaptation, but it is not a complete person, not general natural-language understanding, and not a finished FRIDAY architecture.

Smallest discriminating experiment: give both candidates the same novel, non-language environment with incomplete evidence, a small set of actions and a resource budget. Measure whether experience improves later choices in a changed context, whether confidence is calibrated, whether the system can revise a mistaken relation, and how much CPU/RAM it consumes. Retain neither unless it beats a simpler baseline on the actual test.

MeTTa / Hyperon is a separate research candidate for a later experiment into mutable symbolic programs and knowledge. Its upstream project currently describes itself as pre-alpha/experimental. Do not make it a required runtime dependency or the first production seed.

## Original non-human embodiment

Do not draw a human face, eyes, mouth, head, shoulders or a human silhouette. Do not create an orb and merely rename it.

First visual hypothesis: an evolving, non-anthropomorphic living topology — a spatial field / branching morphology whose structure, motion, density, rhythm and response emerge from actual internal activity and accumulated history. It should be recognisably FRIDAY through continuity of its evolving form, not through a cartoon face or a fixed collection of expression presets.

The renderer is a window into the running individual, not the individual itself. Avoid mapping every internal event to a pre-authored animation. The visual form should respond to continuous evidence from the runtime and retain visual traces of development. This is a design experiment: test whether it communicates presence and continuity, and replace it if it becomes decorative noise.

## Voice from an owner-supplied reference recording

The voice is chosen by uploading a reference audio sample into Friday's app. Keep the original recording and derived voice data local and private.

The first technical candidate is an on-device reference-conditioned synthesizer. Current Android projects demonstrate local reference-audio cloning paths:
- Pocket TTS accepts a short reference recording without a reference transcript.
- ZipVoice uses the reference recording plus its matching transcript.
- sherpa-onnx exposes offline Android-capable TTS runtimes and reference-conditioned synthesis options.

Important reality: a sample alone is not a speech generator. A synthesis algorithm and its model weights are required to produce arbitrary new sentences. Those weights can be bundled or installed as a replaceable local capability resource; they must not own FRIDAY's identity. If no synthesis resource is present, preserve the chosen reference and provide a clearly limited fallback rather than claiming cloning works.

Voice acceptance test: import a permitted sample, synthesize unseen text offline, restart the app, synthesize again with the same voice identity, then remove/replace the synthesis resource and verify that FRIDAY's identity and stored voice choice remain intact. Benchmark latency, RAM, audio quality and thermal load on the actual target phone before selecting a default.

## The first birth test

The first seed is not accepted because screens exist or unit tests pass. It must pass these real app tests:

1. **Cold boot without models:** disable network and remove/disable every language model. Start the app. FRIDAY's continuity substrate loads and remains operational.
2. **Independent activity:** with no user message and no model, she can perform a bounded internally initiated activity that is grounded in current app-local information, or explicitly remain inactive because there is no worthwhile action. No fake typing/thinking animation.
3. **Experience causes change:** teach a novel relation or procedure through an interaction. Record the internal change and provenance.
4. **Restart and transfer:** kill/restart the app. Present a different context where the learned relation matters, without replaying the original episode. Show a measurable behavioural difference.
5. **Negative control:** repeat with the developmental change disabled or rolled back. The later difference should disappear. This helps establish causality rather than mere stored transcript replay.
6. **Resource removal:** attach a language model, demonstrate an added capability, detach it, and prove baseline continuity and model-free functions remain.
7. **Voice reference:** import the selected sample and verify offline reference-conditioned synthesis only when the required local synthesis resource is installed.
8. **Embodiment:** the non-human visual form reflects live runtime evidence and persists as the same evolving visual identity across restart.
9. **Failure honesty:** when a capability is missing, Friday identifies the gap; she does not invent a result or remain indefinitely in a fake activity state.

## Immediate implementation sequence

1. Audit and isolate the smallest genuinely model-independent path already present in the production Android composition. Do not add a parallel second brain.
2. Trace every model callback, model manager requirement, cloud/network assumption and no-model fallback on the actual user-facing path.
3. Make cold boot, local persistence, event intake and baseline activity work with all models disabled.
4. Build the first causal-development experiment and its negative control.
5. Replace human-avatar assumptions with the non-human evolving-topology visual experiment.
6. Replace generic TTS selection with owner-supplied reference-audio import and a qualified local synthesis resource.
7. Run all birth tests in CI, then on the actual Realme handset. CI success is not handset acceptance.

## Evidence and anti-drift rules

Tag claims FACT, INFERENCE, HYPOTHESIS, PROPOSAL or SPECULATION. Keep current reality separate from proposed capability. Do not mark a milestone complete because a class, screen, model catalogue entry or animation exists.

For each mechanism, record:
- what property it is intended to make possible;
- at least one materially different alternative when unproven;
- the smallest experiment that can distinguish them;
- what evidence would justify keeping it;
- what evidence would cause it to be replaced or dissolved;
- how continuity survives its removal.

Do not merge this design document into main as authority. It is a working proposal subordinate to Venon's latest explicit direction.
