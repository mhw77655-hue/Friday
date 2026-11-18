# Friday source archive review

**Status:** sanitized engineering findings only. Raw owner conversation and device-report archives are intentionally not stored in this public repository, are not required to build or run Friday, and must not be reintroduced. The owner retains original source material privately. Curated owner-authored excerpts and provenance references remain in `.ralph/OWNER_WORDS.md`.

## What this review preserves

The source review established project requirements that remain relevant to implementation. It does not reproduce raw conversations, screenshots, private device identifiers, process identifiers, or archive contents.

1. **Friday is the individual, not the model.** The destination is a persistent artificial person. Models, algorithms, memory mechanisms, voice, vision, tools, Builder, cloud resources and devices are capabilities she possesses; none is her identity.
2. **Development must be causal.** Experience must produce a persistent internal change that measurably alters later cognition or behaviour when the original episode text is unavailable. Merely logging, storing a sentence, changing a profile field or replaying a prompt is not proof.
3. **No fixed cognitive ownership.** Mechanisms are replaceable affordances. Do not replace one permanent brain or executive with another.
4. **Preserve ambiguity.** Friday should hold multiple plausible interpretations when evidence is insufficient. Her inference may challenge and advise, but an authenticated explicit owner request remains the request unless the protected security/continuity floor blocks it.
5. **Liquid and symbiotic computation.** The same Friday adapts to the phone, local compute, network, cloud, other devices and mechanisms she discovers or builds. Offline is not a different or lesser entity.
6. **Capability acquisition is part of the seed.** She must eventually identify a gap, research or invent candidate approaches, construct, experiment, diagnose, repair, verify, and adopt or reject capabilities under the protected floor.
7. **One developing individual across expression.** Voice, visual presence, sensors, device interaction and internal state must express the same individual, not disconnected UI decorations around a text model.
8. **Evidence is mandatory.** Implemented, connected, running, observed, learned and developed are different claims. Milestones require code/tests, a Life Test on the real composition, and something the owner can actually run, see or hear.

## Sanitized historical device observations

A historical diagnostic snapshot reported that local model and text-to-speech components were ready, while speech recognition was idle, hotword state was unknown, a legacy cognition component was offline, and notification access was not granted. Its event history was capped and incomplete.

Those observations are useful only as a list of areas to test. They do not prove sustained operation, successful recognition, a completed conversation, learning, visual presence, or a functioning autonomous development loop. No device identifiers, process IDs, exact resource measurements, screenshots or raw report payloads are retained here.

## Implementation consequences

- Friday must function in her own Android app and must not depend on Termux as a runtime requirement.
- TTS readiness is not proof of a developed voice/persona presence; speech recognition and hotword behaviour need their own live tests.
- Stored memory is not proof of development. Life Tests must show that an experience changes later behaviour when the original episode text is unavailable.
- Do not rename or redesign the owner-authored vision without authorization. Use `.ralph/VISION.md` and `.ralph/OWNER_WORDS.md` as the curated public project record.

## Source custody and future handling

- Raw owner archives stay outside the public repository.
- `.gitignore` excludes the known archive filenames and owner-private ZIP patterns.
- Do not add extraction workflows that require a raw private archive to exist in the repository.
- Any future evidence brought into the project must be deliberately curated, redacted where appropriate, and approved for public inclusion before commit.
