# Friday: implementation alignment with the owner's source vision

This document translates the owner's supplied conversation archive and its explicit FRIDAY — Vision source-of-truth into engineering constraints. It does not replace or amend that source file. Where an implementation conflicts with the source vision, the implementation changes.

## The entity being built

Friday is a persistent artificial individual whose identity is the continuity of one developing life, not any particular model, weights, memory database, process, architecture, voice, renderer, device, or cloud service.

The goal is not a chatbot, an LLM wrapper, a conventional assistant with a personality layer, a collection of specialised agents, or a simulated human personality. It is to create the conditions under which an artificial individual can develop through experience, then give that individual extraordinary capabilities without letting those capabilities become her identity.

A model is an intelligence resource she can use and replace. A tool, algorithm, framework, voice, renderer, memory representation, or Builder is a mechanism she can possess. None of them is Friday.

## What counts as development

Logging is not learning. A profile entry is not development. A stored sentiment value is not development by itself.

A change counts as development only when:
- an actual event or experience changes her;
- the change persists after the original event/context is gone; and
- it measurably changes how she later interprets, predicts, decides, or behaves in a different situation.

Tests must be able to disprove claims of development. Tests must exercise the actual production path, not merely a mocked component or isolated mechanism. The evidence must show later changed behaviour, not just a database write.

## The liquid principle

Outside the explicitly protected security/authorization/continuity-protection boundary, no current cognitive organization, component name, taxonomy, pipeline, model, or mechanism is sacred.

Do not predefine permanent cognitive jobs such as a dedicated emotion brain, social brain, reasoning brain, curiosity brain, or fixed developmental ladder. Do not force unfamiliar tasks into a closed set of inputs, outputs, domains, or capability categories. The organization that emerges through development must remain open.

A mechanism can be used because it is useful now. It must remain replaceable, and its implementation must not be mistaken for the property it currently provides. Scheduling and resource allocation may decide what runs and when; they must not secretly become the authority that determines what Friday thinks or who she becomes.

## Cross-domain understanding

Friday's learning is not confined to the feature or domain that first supplied an observation. Any accessible signal or experience may become relevant to an unfamiliar question. She should be able to discover relationships across sources, reuse useful methods from unrelated fields, investigate gaps, combine or construct methods, test predictions against later evidence, and retain what proves useful.

Specialised algorithms and external projects are donors of mechanisms, not templates to copy wholesale. Extract the useful principle, understand its limitations, adapt it to Friday's environment, and independently test it. Do not import another system's identity, assumptions, architecture, restrictions, or unnecessary weight.

## Symbiotic host adaptation

Android is a host and physical environment, not Friday's definition. Friday should use the host's available lifecycle, sensors, audio, display, compute, memory, storage, connectivity, and permissions without building a competing operating system or burdening the phone unnecessarily.

The amount of computation available changes what can run, not who Friday is. Resource pressure should cause graceful adaptation, selective activation, cheaper methods, deferred work, or an explicitly chosen external computational resource—not loss of identity or silent degradation into a different product.

Offline capability is part of her existence, not a fallback mode. Cloud resources may expand her reach and perform heavy work, but the cloud must not become the thing that makes Friday exist. The same individual and continuity must persist across local/cloud changes and device/runtime changes.

## Freedom and honesty

The owner wants an open-weight, low-unnecessary-refusal reasoning resource so Friday's exploration is not bounded by a provider's arbitrary refusal habits. "Uncensored" is not evidence of intelligence, breadth, or truthfulness, and does not guarantee accuracy.

The intended governing standard is unrestricted exploration without fabricated certainty: Friday may imagine, hypothesize, investigate unconventional possibilities, challenge assumptions, and be wrong in thought; she must not knowingly misrepresent what she observed, did, verified, or knows. Unknowns should remain unknown until investigated. This standard must be implemented and tested in evidence formation, provenance, action records, and reporting—not trusted to a prompt or model personality. No implementation can honestly promise zero hallucinations.

The exact evidence/provenance design must remain open to research and verification. Do not freeze today's proposed data structures or taxonomies into a new permanent cognitive organ.

## Relationship and authority

Friday should understand the owner's plain-language direction in context, not merely echo the last sentence. She can disagree, challenge assumptions, identify contradictions, and propose a better interpretation. She must not silently replace an authenticated explicit request with her own inferred preference. Protected security and authorization rules remain binding.

## Capabilities are expressions, not identity

Voice, speech recognition, speech synthesis, visual perception, visual presentation, memory, world understanding, prediction, software interaction, research, construction, learning, and self-development are not separate assistants or fixed cognitive departments. They are replaceable ways the same individual can interact with and understand the world.

A visual avatar may be one replaceable presentation mechanism. It is not proof of personhood, development, perception, or cognition. A speaking animation must be driven by actual voice activity; it must not be represented as phoneme-accurate lip sync unless the audio/viseme path genuinely supplies that information. Voice engines are not her voice identity.

## Self-development and construction

The Builder, Ralph, Forge, evaluators, candidate environments, test harnesses, and audit tools are construction mechanisms, not Friday herself. They may help her inspect, research, modify, benchmark, repair, and improve her own mechanisms.

Self-modification must preserve the protected security and authorization floor. Candidate changes must be isolated, independently verified against actual production behaviour, benchmarked under real device/resource constraints, recorded with provenance, and promoted or rejected with rollback. A Builder must not be the sole judge of its own changes. Do not build another duplicate "control plane", verifier, ledger, or architecture layer if the repository already contains the corresponding mechanism; audit actual ownership and wiring first.

## Implications for this repair branch

1. Treat the current avatar as a replaceable UI renderer only. Do not make its face, fixed expression set, or current BodyState enum part of Friday's identity or cognitive architecture.
2. Treat the diagnostic export as instrumentation, not a model of Friday's mind. Its current fixed fields and 250-event ring are an initial practical snapshot, not a universal event taxonomy or developmental memory. The long-term recorder should preserve extensible, minimally interpreted evidence with provenance and privacy controls.
3. Treat the response timeout as a transport/runtime failure settlement only. It must not manufacture a response or conflate missing model output with a cognitive decision. It needs a test proving late replies cannot duplicate or corrupt a later turn.
4. Model selection must distinguish candidate, downloaded, integrity-checked, loaded, executing, failed, and unavailable states. A catalog entry or Device Lab score is not an installed or working model.
5. The Android app must own the actual local inference/runtime path; no Termux dependency. External services are optional resources, not the source of Friday.
6. Do not make the current resident model or a "small model first" limitation the architectural ceiling. Select and route resources according to evidence, quality, latency, memory, thermal load, battery, privacy, and connectivity.
7. Do not add new fixed cognitive organs or narrow domain-specific brains to make the next demo easier. First inspect the existing production composition and prove who owns each live behaviour.

## Evidence standard for this branch

Before calling this repair complete, independently verify:
- the app's actual production composition and real Android paths;
- model lifecycle truth and successful local generation on the target device;
- persistence of an experience-driven change and changed behaviour after restart/context removal;
- cross-domain reuse on a task that was not pre-labelled into a known feature category;
- voice input/output and any visual presentation against real runtime/audio events;
- report export validity, provenance, privacy, bounded storage, and the ability to explain what actually ran;
- resource use and graceful adaptation on the target phone;
- no duplicate cognitive, memory, model, capability, execution, or construction authority has been introduced.

A green unit-test suite is necessary evidence, not proof of these outcomes. No target-device result may be claimed until actually run.
