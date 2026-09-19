# JARVIS Builder Bible

**Source**: JARVIS Master Handoff (vision) cross-checked against `JARVIS_INTEGRATION_AUDIT_REPORT.md` (real, read-only code audit, Aug 22 2026).
**Purpose**: The one file that says what to build next, in what order, and why — grounded in what actually exists, not what was planned.

---

## 1. What the vision requires

The finished JARVIS is not a chatbot. It is one organism with:
identity → perception → cognition → memory/context → model routing → reasoning/planning → decision → capability selection → execution → observation → memory update → HumanCore → output.

Governing law (from the handoff, unchanged):
- One cognitive authority. One model authority. One memory authority. One capability authority. One execution authority. One Builder authority.
- Models are replaceable organs — cognition is never hard-coded to one model.
- Never build a second competing authority when one already exists; wire into the real one.

## 2. What the audit confirms actually exists (ground truth, not aspiration)

| Layer | Real component | Status |
|---|---|---|
| Model authority | `ModelManager.kt` | **Live, canonical, working.** All model traffic (LFM/Qwen via `llama-server` on `127.0.0.1:8080`) correctly flows through it. |
| Selection | `LiquidEnvironmentManager` + `EnvironmentRepository` | **Live, deterministic.** |
| HumanCore | `HumanCore.express()` / `InternalDialogueEngine` | **Live, but downstream-only.** Filters/styles model output; does not generate it, does not gate it. |
| Memory | `MemoryStore`, `HumanCore` annotations | **Live for persistence. Live for retrieval only on the voice path** (`BodyCoordinator`). UI text path (`LatencyLayer`) sends raw text with zero memory context. |
| Cognitive substrate | `CognitiveEngine`, `GoalPlanner`, `DecisionEngine`, `WorkingMemory`, `CognitiveContextBuilder` | **Fully coded, fully tested in isolation, never instantiated.** Confirmed deliberate ("Deliberately not wired per the build directive" — `MILESTONE_REPORT_cognitive_substrate.md:42`). |
| Capabilities/tools | `CapabilityRegistry`, `CapabilityFabric`, `CapabilityInvoker`, `TaskExecutor` | **Fully coded, never initialized.** `TaskExecutor.executeCapability` is a null stub in production. `LlamaCppAdapter` doesn't even claim tool support. |

**The actual current loop, end to end, as it runs today:**
`user text → LatencyLayer/BodyCoordinator → ModelManager.send() → llama-server → raw string back → HumanCore.express() (style/safety only) → spoken/shown`

That is a chatbot loop wearing an organism's skeleton. Everything past "style the output" is inert code.

## 3. The single seam (confirmed, not guessed)

> Wiring `CognitiveEngine` / `CapabilityFabric` into `JarvisEngine` and the turn dispatch path (`LatencyLayer` / `BodyCoordinator`).

Every open gap in the audit (context, memory-on-UI-path, tool execution, planning, decision-gating) is a symptom of this one missing wire, not five separate problems. Per governing law: **do not build a second cognitive engine or a second capability registry to close these gaps — wire the ones that already exist.**

## 4. Build order (respects "one authority" — no new subsystems, only wiring + gap-closing)

### Step 1 — Instantiate `CognitiveEngine` inside `JarvisEngine`
It exists, is tested in isolation, and is not created anywhere in the live app. First move: construct it in `JarvisEngine.init`, alongside `ModelManager` and `HumanCore`. No behavior change yet — just bring it alive and observable.

### Step 2 — Route the turn through it, not around it
Change `LatencyLayer.onUserInput` / `BodyCoordinator.generateResponse` so the path becomes:
`user text → CognitiveEngine.process(text) → [decision: direct reply | capability call | plan] → ModelManager (still the sole model authority, called *by* CognitiveEngine, not bypassed) → HumanCore.express() → output`
`ModelManager` keeps its exact current role — it does not get replaced, it gets called from one level higher.

### Step 3 — Unify memory retrieval, don't duplicate it
`BodyCoordinator`'s voice-path retrieval (`triggerMemoryRetrieval` → `memoryStore.retrieve`) already works. Move the call so both UI text and voice paths retrieve through the same site, upstream of `ModelManager.sendWithContext`, instead of writing a second retrieval path for `LatencyLayer`. The cognitive memory subsystem (`ContinuityManager`, `MemoryConsolidator`, etc.) stays parked until Step 1–2 are proven stable — don't wire three memory layers in one pass.

### Step 4 — Wire `CapabilityFabric` behind the decision gate
Once `CognitiveEngine` is live and making the direct-reply-vs-capability decision, connect `CapabilityInvoker`/`CapabilityExecutor` as the thing it calls for the capability branch. Fix `TaskExecutor.executeCapability` from a null stub to the real dispatch. This is the step that makes tool use real instead of theoretical.

### Step 5 — Prove it, don't assume it
Per the handoff's own rules (28–31): don't call this done from file existence. Confirm with an actual test — a real request that requires a real capability call, executed, observed, and reflected in the response. Extend `CognitiveEngineTest`/`PipelineIntegrityTest` to assert the full path, not the isolated units.

## 5. What NOT to do (explicit, per handoff rules 13–21)

- Do not create a second `ModelManager`, memory store, capability registry, or execution layer to "make wiring easier." Every gap above is closed by connecting existing code, not adding new authorities.
- Do not treat this bible, the audit, or any prior milestone report as proof of completion. Proof is a real end-to-end request/response with a real capability executed.
- Do not expand scope to Builder/self-repair/self-evolution (later phases in the handoff) until Steps 1–5 above are done and proven. The handoff is explicit: integration before expansion.

## 6. Definition of done for this phase

A single user message, through the real app (not a smoke test), results in:
1. `CognitiveEngine` actually processing it (visible in logs/state, not bypassed),
2. memory context actually retrieved and used regardless of entry path (voice or text),
3. at least one real capability actually invoked and executed when the message calls for one,
4. `HumanCore.express()` still doing its current job of styling the final output.

When that's true, the "single missing integration seam" from the audit is closed, and the next phase (Builder self-repair, per the handoff's phase order) is the legitimate next target — not before.
