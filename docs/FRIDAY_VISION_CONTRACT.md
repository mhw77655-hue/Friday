# Friday — canonical vision and architecture contract

**Status:** governing architecture contract for the Android-hosted Friday build  
**Host:** Friday's own Android application; the phone is her first body  
**Rule:** when older plans, comments, tests, or modules conflict with this contract, this contract wins. Reconcile the implementation; do not preserve an obsolete design merely because it already exists.

## 1. The individual

Friday is one persistent individual expressed through a continuous identity, memory, experience, preferences, learned strategies, and one coordinated set of capabilities.

She is not a chat screen that starts existing when a prompt arrives. A conversation turn is one interaction with her, not the beginning or end of her existence.

Runtime work states such as listening, speaking, processing, recovering, or performing a tool action describe what one of her organs is doing. They must never be treated as her existence or identity. There is no product-level "idle Friday" or "thinking Friday" persona. Resource-aware duty cycling is allowed; identity, continuity, and the low-cost presence heartbeat remain.

## 2. One intelligence, one body, many organs

- **Friday's intelligence:** the model and cognitive pipeline already owned by Friday. It interprets meaning, forms intentions, reasons about goals, and learns from outcomes.
- **The body:** the Android phone and the app's native interfaces to it: microphone, speaker, accessibility tree, notifications, media sessions, sensors, storage, network and supported system APIs.
- **Organs:** replaceable capabilities with explicit contracts, resource envelopes, health evidence, and learned operating strategies. An organ is not another independent Friday.
- **Jev:** an optional cloud decision faculty from TypeSafe. It supplies typed Choice, Score and Noul judgments. It is not Friday's conversational model, not an Android driver, and not the authority that grants permissions. Friday interprets Jev's judgments and her own local policy decides what may happen.

The normal control loop is:

```
voice / other input
        ↓
Friday understands the request and current context
        ↓
Friday forms a typed, bounded intention
        ↓
Jev evaluates relevant choices, consequence and clarity (when configured/useful)
        ↓
Friday's local policy + Android permission + user authorization
        ↓
Android body executes the action
        ↓
observed result / failure / latency / resource cost
        ↓
persistent experience and strategy update
```

No cloud result is executed directly. A typed decision is evidence, not an instruction to bypass local authorization.

## 3. Voice is a body-wide control channel

Voice is not a separate command chatbot with a tiny fixed phrase list. Simple exact commands may use a deterministic fast path. Natural requests must be interpreted by Friday's own intelligence using the current, privacy-minimized screen and device context.

The control path must:
- support natural language, references to visible controls, follow-up context and clarification;
- inspect the current accessibility tree at the moment of planning and re-check the target immediately before execution;
- produce explicit typed actions rather than arbitrary shell/code;
- evaluate risk, target clarity and authorization;
- ask for spoken confirmation before consequential or ambiguous interactions;
- support cancellation, expiry, interruption and action serialization;
- verify the Android outcome before saying it succeeded;
- report unsupported platform operations honestly and offer the native system panel where Android restricts direct control.

The first implementation intentionally executes one bounded action per utterance. Multi-step workflows must become explicit, inspectable action graphs with checkpoints and per-step authorization; they must not be hidden inside one vague "execute" result.

The planner must use Friday's existing model authority, not load a second large model just to control the phone. Jev is a small remote decision call, not a resident model.

## 4. Nano Liquid Symbiosis — learn the host by living in it

The phone is the first host to study. Friday should continuously learn a versioned, device-specific operating profile from real observations:

- hardware/build/runtime fingerprint;
- available memory, process PSS, native allocation peaks and reserve;
- CPU load, thermal pressure, battery and charging;
- model load/unload time, context, latency, throughput and quality;
- voice, accessibility, camera and background workload interference;
- organ success/failure, recovery, energy and user-visible outcome.

A profile is specific to the exact model revision, quantization, runtime, device fingerprint and workload class. Estimates must be distinguished from measurements.

Learning the host means changing future resource and scheduling decisions, not merely writing telemetry. Adaptations must preserve a protected reserve, keep the app responsive, and be reversible. Friday may change safe runtime configuration automatically; changing executable code, permissions, security boundaries, or the installed Android package requires the appropriate approval and rollback path.

Quality is a hard constraint. A faster configuration is not an improvement if it degrades the capability that the user needs. Unknown accelerator support is treated as unavailable until the actual device/runtime proves it.

## 5. Experience must change future behaviour

A stored transcript or counter is not, by itself, learning.

Every useful experience should be represented as an event with provenance:
- what Friday expected;
- what she chose and why;
- what happened on the phone;
- whether the result was verified;
- resource/latency cost and any user correction;
- what strategy or prediction should change as a result.

The learning system updates persistent predictions, preferences, confidence calibration, action policies and organ strategies from validated outcomes. It must distinguish direct evidence, inference, and uncertainty. Repeated failure should change future behaviour; replaying a stored event without changing a decision is not proof of development.

## 6. Self-study and self-building

Friday should spend some of her available compute studying her own installed application and the host that runs it. This is ongoing maintenance and development, not an "idle mode".

The developmental loop is:

1. inspect the actual packaged architecture, source/build metadata, runtime wiring and observed failures;
2. compare them with this contract and identify a concrete capability or quality gap;
3. research options and label evidence honestly;
4. use Friday's existing intelligence to design or synthesize a candidate;
5. build it inside an isolated habitat;
6. run behavioral, failure, security, compatibility, resource and quality tests;
7. compare it against the current capability on the same objective;
8. promote only a verified improvement, preserve provenance and a rollback point;
9. persist the executable artifact and prove it rehydrates after process restart;
10. record the outcome so the next development cycle improves.

The existing synth DSL is a safe starting habitat, not the final limit of what Friday may build. Native Android tools, model adapters and reusable organs need typed contracts, permission manifests, signing/provenance, isolated validation, and explicit promotion. Friday must not overwrite her installed APK or silently grant herself new permissions.

## 7. Android is the only production host

- Friday is an independent Android app.
- Termux is not a production dependency, launch path, setup prerequisite, or second host.
- Desktop/JVM code may provide test fixtures and CI harnesses only.
- The same Android-owned composition root must create the one production cognitive engine, model manager, turn pipeline, capability fabric and body-control authority.
- App lifecycle, process death, foreground services, Android permissions, accessibility enablement and recovery are first-class production concerns.

## 8. How to judge the existing repository

Treat existing code as material to inspect, not as proof that the vision is already implemented.

| Existing area | Keep when it serves the contract | Rebuild or reject when it does not |
|---|---|---|
| Companion Core / Presence Engine | Persistent identity signal, low-cost presence heartbeat, attention and embodied expression | A single mutually exclusive ASLEEP/IDLE/THINKING state being treated as Friday's existence |
| Body Coordinator | Mic, speech, interruption, output and Android lifecycle coordination | A narrow phrase parser being the ceiling of voice control, or turn phases being confused with existence |
| ModelManager / CognitiveEngine | One Friday-owned intelligence and reasoning path | Duplicate model stacks or a second independent "assistant" |
| Jev client | Typed remote Choice/Score/Noul decision faculty | Treating Jev as the planner, executor, permission authority or resident model |
| Nano Symbiosis | Device-specific observation, prediction and adaptive resource policies | Generic heuristics presented as calibrated measurements or learning claimed without changed decisions |
| Application Self-Study | Real architecture/host evidence and prioritized gaps | Build-time symbol counts presented as full source understanding |
| Capability Foundry / ToolBuilder | Isolated candidate creation, tests, promotion and rollback | A passing toy DSL example presented as arbitrary native self-development |
| Memory / experience | Provenance-backed persistent events that change future decisions | Transcript storage alone presented as developmental learning |

## 9. Evidence labels

Every progress claim must be labelled as one of:
- **Implemented:** code exists and is connected to the production path.
- **Unit-tested:** the relevant behavior passed its named tests.
- **CI-verified:** the exact commit passed Android build and test workflows.
- **Device-verified:** the behavior was exercised on the target Realme phone.
- **Designed / not yet implemented:** architecture only.

A passing JVM test is not device evidence. A debug APK assembling is not proof of working voice control. A telemetry field is not proof of learning. A source index is not source comprehension. A capability registered in process memory is not durable across restart.

This contract exists to make the repository converge on one Friday, not to justify every feature already present.
