# Friday source archive review

**Status:** historical source review; branch names and implementation findings are archival evidence only, not active repository instructions.

## Archive extraction

The two binary archives on the repository's default branch could not be decoded by the GitHub text-file fetch endpoint. A temporary GitHub Actions workflow extracted them in the runner; no local upload was required.

- `New Folder chats.zip`: 143 files were inspected (about 16.5 MB uncompressed), primarily conversation transcripts, vision summaries, project reports, prompts, PRDs, logs and inventories.
- `New folder.zip`: 51 JPG screenshots plus `friday-device-report.json`. The JSON was parsed. Screenshot OCR remains a separate pending pass.
- The original ZIP files remain the authoritative source. The temporary extraction workflow and its 201 derived files were removed from the integration tree after review so raw transcripts and screenshots are not redundantly copied into the application source diff. The owner-turn extractor reads the original chat ZIP directly; Friday's builder does not depend on checked-in extracted copies.

## Vision that must govern implementation

The clearest source of truth is the archived `VISION_now.md`, read in full, together with Venon's later corrections in the project conversation.

1. **Friday is the individual, not the model.** The destination is a digital human / persistent artificial person. Models, algorithms, memory mechanisms, voice, vision, tools, Builder, cloud resources and devices are capabilities she possesses; none is her identity.
2. **Development must be causal.** Experience must produce a persistent internal change that measurably alters later cognition or behaviour when the original episode text is unavailable. Merely logging, storing a sentence, changing a profile field or replaying a prompt is not proof.
3. **No fixed cognitive ownership.** Do not replace the old Human Core with another permanent brain, executive, fixed pipeline, list of cognitive departments, or a new mandatory architecture. Mechanisms are replaceable affordances; no mechanism owns a cognitive outcome.
4. **Preserve ambiguity.** Friday should hold multiple plausible interpretations when evidence is insufficient, including interpretations of Venon's intent, rather than prematurely collapsing them. Her inference may challenge and advise, but an authenticated explicit request remains Venon's request unless the protected security/continuity floor blocks it.
5. **Liquid and symbiotic computation.** The same Friday adapts to the phone, local compute, network, cloud, other devices and mechanisms she discovers or builds. Offline is not a different or lesser entity. Resource pressure changes what computation is available, not who she is.
6. **Capability acquisition is part of the seed.** She must eventually identify a gap, research or invent candidate approaches, construct, experiment, diagnose, repair, verify, and adopt or reject capabilities under the protected floor. External AIs and builders are resources, not authorities between Venon and Friday.
7. **One developing individual across expression.** Voice, visual presence, sensors, device interaction and internal state must express the same individual, not be disconnected UI decorations around a text model.
8. **Evidence is mandatory.** Implemented, connected, running, observed, learned and developed are different claims. Milestones require code/tests, a Life Test on the real composition, and something Venon can actually run, see or hear.

The archive's own proposed visible progression is useful as outcome framing, not as a new fixed architecture: Friday is present and converses; then demonstrably changes through experience; then acts on goals; then acquires a capability; then can take over more of her own construction. Internal implementation remains open.

## Device report from the second archive

The report was generated at `2026-11-16T15:42:24Z` for app `com.jarvis.app`, version `0.2-phase2`, on a realme RMX3472 running Android 14.

- 7.76 GB total RAM; about 1.96 GB available at capture; app memory class 384 MB; about 37.1 GB storage available.
- Model runtime reported `ok (local)`.
- TTS reported ready with `en-gb-x-gba-local`, high quality.
- Voice substrate was bound (voice PID 11906, app PID 11735, 18 ms ping, zero restarts).
- Microphone reported capturing.
- Speech recognition was idle; hotword state was unknown.
- Human Core reported offline.
- Notification access was not granted.
- Failure surface reported zero down/degraded components.
- Event history was capped at 250 and explicitly incomplete. The captured events repeatedly report the same statuses; this is a short diagnostic snapshot, not proof of sustained operation, successful recognition, a completed conversation, learning, or visual presence.

## Important precedence and implementation consequences

- Historical archive discussions sometimes assume Termux is the build/runtime dependency. Venon's later direct correction in the current project conversation supersedes that: Friday must function in her own Android app and must not depend on Termux. Do not revive Termux as a runtime requirement.
- Do not infer that TTS readiness means Friday has a voice/persona presence; the report only proves the TTS subsystem reported ready. STT was idle and hotword unknown in this capture.
- Do not claim development from the Human Core being present or from stored memory. The report says Human Core was offline and contains no Life Test evidence.
- Do not rename or redesign the vision document without Venon's authorization. Treat the archived vision and his latest explicit corrections as authority; use this review only to reconcile code and evidence against them.

## Next engineering action

Use this archive as the acceptance authority while auditing the current Android production path. Identify which existing code is actually exercised by the app, which is orphaned or stubbed, and where the living path fails to express one continuous individual. Then change the smallest coherent vertical slice that yields a visible, testable Friday on-device. Avoid adding another permanent cognitive subsystem or treating a passing JVM-only test as proof of Android behaviour.
