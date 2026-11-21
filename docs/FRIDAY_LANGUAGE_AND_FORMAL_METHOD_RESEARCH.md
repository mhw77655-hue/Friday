# Friday language and formal-method research

**Research date:** 2026-11-21  
**Authority:** informs `.ralph/FRIDAY_DELIVERY_PLAN.md`; the delivery plan remains the only active plan.  
**Scope:** language and formal-method choices across Android, cognition, inference, memory, voice, action, evolution, security, research and future portability.

## Executive conclusion

Friday should use a bounded heterogeneous implementation, not a monolithic rewrite and not a language zoo. Keep Kotlin as the Android/product/orchestration language and C++ for existing native inference/audio hot paths. Use Python for research and development tooling. Add Lean 4 for formal contracts and proofs, with TLA+ for temporal/concurrency model checking. Evaluate Rust for new memory-sensitive/security-sensitive native boundaries, but port existing code only after behaviour-preserving benchmarks. SQL/SQLite remains the durable local data layer.

TypeScript, WebAssembly/WIT, MiniZinc, Prolog, Alloy, Go, Swift, Julia and Elixir/Erlang are valuable in specific circumstances but should not become immediate production dependencies without a measured need.

The goal is not language diversity. It is better outcome reliability, safety, latency, resource use, portability and evolvability.

## What was inspected in the repository

The inspected `main` tree contained approximately 734 Kotlin files, three Kotlin Gradle build scripts, five C++ implementation files plus headers, 14 Python files and packaged native shared libraries. The Android application is already Kotlin-first with a C++ JNI/native inference and voice substrate. No Lean, Rust, Go, TypeScript, TLA+ or Alloy project was present in the tracked source tree at the time of inspection.

This matters: the right first move is to add formal assurance without destabilizing the existing Android stack, not to rewrite hundreds of Kotlin files.

## Language-by-language assessment

Scores are project-specific fit estimates out of 10, not general language rankings.

| Language / method | Fit | Best Friday roles | Recommendation |
|---|---:|---|---|
| Kotlin | 10 | Android app, lifecycle, UI, permissions, capability orchestration, IPC, runtime coordination | Keep as production backbone |
| C++ | 9 | llama.cpp, inference kernels, VAD/audio DSP, SIMD/native hot paths | Keep; profile before changing |
| Python | 9 | research, model/data prep, evaluation, corpus tooling, build scripts, experiments | Use in development/research plane |
| Lean 4 | 9 | formal contracts, state invariants, update/action/authorization proofs | Adopt now for critical abstract properties |
| SQLite / SQL | 9 | durable local state, task journal, experience/provenance, synchronization; FTS5 lexical retrieval | Core persistence substrate |
| Rust | 8 | new parsers, untrusted artifact handling, sandbox host, memory-safe native modules | Selective adoption; no blanket C++ rewrite |
| TLA+ | 8 | temporal properties, retries, cancellation, IPC, update and recovery concurrency | Add models for high-risk protocols |
| Alloy | 7 | bounded structural analysis of capability/permission/dependency/trust graphs | Design-time use |
| WebAssembly + WIT | 8 future | portable capability modules and stable cross-language interface | Investigate after capability ABI/security controls stabilize |
| MiniZinc | 7 | constrained resource scheduling, model residency, energy/deadline optimization | Benchmark as an offline research tool |
| Prolog | 6 | symbolic inference, relational rules, constraint logic, explainable planning | Isolated experiments |
| TypeScript | 6 | optional web diagnostics/development console | Only if a browser console is actually needed |
| Go | 5 | independently deployed cloud services | Only for a justified service boundary |
| Swift | 5 future | native Apple host | Only when iOS/macOS is a real target |
| Julia | 5 | numerical/scientific prototypes | Research-only if ecosystem advantage is material |
| Java | 4 incremental | Android library interoperability and existing integrations | Maintain as needed; no new primary layer |
| Elixir/Erlang | 4 future | large distributed fault-tolerant services | Revisit only if service topology warrants it |

## Role allocation by Friday subsystem

### 1. Android body, device agency and user experience
**Kotlin first.** It has direct Android platform access, established Android tooling and interoperates with Java. Keep lifecycle, foreground services, notification/accessibility integration, permission handling, PackageInstaller, UI, Shizuku integration and application composition here. Use coroutines/structured concurrency and explicit cancellation/deadlines rather than adding a second runtime.

**Java** remains useful when an Android API or donor library requires it. A wholesale Java rewrite has no evident advantage.

**Swift** is the corresponding future native host language if an Apple platform becomes a concrete deliverable; it does not belong in the current Android APK.

### 2. Inference, speech and low-latency computation
**C++ remains the native substrate** for llama.cpp, tokenizer/JNI glue, VAD and profiled DSP/inference operations. The Android NDK supports native C/C++ libraries packaged in the APK and JNI calls from Kotlin/Java. Keep JNI surfaces narrow, versioned, cancellation-aware and well-tested.

**Rust** is a candidate for new memory-sensitive parsers, capability hosts, native services and artifact verification. Rust's ownership/borrowing checks prevent broad classes of invalid memory access and data-race patterns at compile time, but it does not automatically make FFI or unsafe code safe. Do not port working C++ unless behaviour replay, device performance, binary size and reliability demonstrate a net gain.

**ONNX Runtime** is a runtime/API option, not a language. Its Android bindings support Java/C/C++; CPU, XNNPACK and NNAPI availability and performance are model/device dependent. Compare it against the existing GGUF/llama.cpp route for suitable perception/embedding workloads rather than assuming it is universally faster.

### 3. Cognitive orchestration, planning and capability execution
**Kotlin** remains the initial production implementation because it already owns the Android runtime and can directly observe device state and execute platform actions.

**Lean** specifies safety and correctness properties of critical abstract contracts. It should not be placed in the latency-sensitive action path.

**TLA+** is useful for modelling interleavings: simultaneous tasks, action cancellation, retries, service death, process restart, update/install states and resource locks.

**Alloy** can explore bounded structural questions: whether a capability graph admits privilege escalation, circular dependencies, orphaned permissions or incompatible update combinations.

**Prolog** is worth a small experiment for symbolic relationship reasoning, rule inference and logic-based planning. It should be adopted only if it demonstrably improves accuracy/explainability over the current Kotlin implementation within phone resource limits.

**MiniZinc** can express resource/deadline/energy scheduling as a constraint optimization problem. Use it first as an offline oracle to evaluate a Kotlin heuristic; do not assume an optimization solver is suitable for every live decision.

### 4. Memory, continuity and developmental learning
**SQLite/SQL** is the durable local source of truth for identity, task journals, experience records, provenance, synchronization and migration state. Use transactions and explicit schema evolution. FTS5 supports lexical full-text search, phrase/prefix/NEAR queries and BM25-style ranking; it is complementary to embeddings, graph retrieval and structured memory, not a semantic model.

**Kotlin** owns Android database lifecycle, repository contracts and integration. Python can build/evaluate corpora and run offline retrieval experiments. Lean can specify selected invariants such as provenance preservation, monotonic versioning, explicit forget/tombstone semantics and transaction preconditions, but those require implementation conformance tests.

### 5. Research, model acquisition and self-development
**Python** is the primary research plane for model evaluation, dataset preparation, benchmarking, quantization experiments, teacher/student distillation experiments, algorithm comparisons and developer automation. The installed Friday must not depend on Python being installed on the phone for essential capabilities.

**C++** is appropriate for optimized kernels and native runtime integrations. **Rust** is appropriate for carefully isolated security-sensitive components. **Julia** is optional for numerical/scientific research where its performance-oriented scientific ecosystem offers a concrete advantage.

The evolution system should treat language choice as a per-capability engineering decision. A proposed rewrite must include baseline behaviour, tests, benchmarks, license/provenance, FFI contract, resource profile and rollback.

### 6. Capability contracts, metadata and portability

**CUE** is a constraint-based data/schema language that combines data and validation rules. It is a strong candidate for validating capability manifests, model catalogue entries, signed release metadata, compatibility declarations and resource/policy constraints in CI. It can detect contradictions and invalid configurations before they reach the phone. It should validate declarative data, not become a new runtime dependency inside Friday.

**Protocol Buffers** provides a language-neutral schema and generated bindings for Kotlin/Java, C++, Rust, Python and Go. It is a good candidate for stable typed IPC/service contracts once Friday actually has multiple language implementations exchanging structured data. Use explicit field-number/version evolution rules; generated serialization does not itself establish authorization, semantic correctness or backward-compatible business behaviour.

**FlatBuffers** supports direct access to serialized data without an unpacking step and has bindings across the likely language portfolio. It may help high-volume or latency-sensitive data exchange, but it adds generated-code/build complexity. Prefer Protobuf or simpler local interfaces until profiling demonstrates that parse/allocation overhead is a real bottleneck.

### 7. Capability portability and isolation
**WebAssembly** is a safe, portable low-level code format with a sandboxed execution model. **WIT** defines typed component interfaces; it does not implement the component and does not provide the host's authorization policy by itself. A production plugin system would still need a runtime, capability-based host calls, memory/CPU/fuel limits, filesystem/network mediation, signature/provenance checks, revocation and rollback.

This could eventually let Friday combine capabilities implemented in Rust, C/C++, Go, Python-compiled-to-Wasm or other supported toolchains behind one contract. Do not build this until the current capability contract and security boundary are real.

### 8. Cloud, collaboration and external services
**Kotlin** can remain the initial server/client integration language if the service topology is small. **Go** is a strong candidate for a small, independently deployable concurrent network service, but adds another deployment and operational stack. **TypeScript** is suitable for a web control/diagnostic console if needed. **Elixir/Erlang** becomes interesting for a large, long-lived distributed service needing process isolation and supervision; this is not a reason to add it to the Android application.

## Why Lean 4 is worth introducing now

Lean is a functional programming language and interactive theorem prover based on dependent type theory. Proof tactics elaborate into terms checked by a small trusted kernel. That makes Lean valuable for stating and mechanically checking mathematical properties of abstract state machines and contracts.

High-value initial properties:
1. A task is never marked complete merely because a command was issued.
2. Completion requires an observed postcondition and an independent verification result.
3. A privileged action is unreachable without the required authorization state.
4. An update is not promoted before integrity, package identity, signer and compatibility checks succeed.
5. Failed updates preserve a recoverable last-known-good state.
6. A memory/identity migration preserves protected continuity invariants.

**Critical limitation:** a Lean theorem proves a property of the Lean model. It does not automatically prove Kotlin, C++, Android or a real phone conforms to that model. Every promoted guarantee needs a documented mapping to implementation, generated/shared test cases where practical, property/integration tests, and physical-device evidence where relevant. Avoid `sorry`, unreviewed axioms and claims that formal proofs establish runtime success.

Start without Mathlib unless a theorem actually needs it; minimize dependencies and pin Lean/toolchain versions. CI must build the formal workspace on every change to it and the relevant delivery contracts.

## Why TLA+ and Alloy complement Lean

Lean is strong for proofs over precisely defined functions, relations and invariants. TLA+ is strong for describing state transitions over time and checking temporal/concurrency behaviours with TLC. Alloy is strong for bounded relational structure exploration and counterexample generation. They overlap but are not interchangeable.

Use TLA+ first for task cancellation/retry, service restart, update/install handoff and multi-capability resource arbitration. Use Alloy for capability graph and permission model review. Do not attempt to formalize the entire application at once; select the smallest high-risk protocols where counterexamples would be expensive.

## Why not add every language immediately?

Each language introduces compilers, dependency graphs, build time, binary size, FFI/IPC, debugging requirements, licensing and supply-chain attack surface. Multiple languages can improve architecture only when boundaries are explicit and the benefits are measurable. A heterogeneous system with vague contracts is worse than a coherent Kotlin/C++ system.

## Implementation sequence

1. Keep current Kotlin/C++ Android implementation; document ownership boundaries and reject duplicate runtimes.
2. Establish Lean 4 workspace and CI; formally specify action outcome verification first.
3. Add conformance tests tying the formal task-state model to the Kotlin task executor's observable behaviour.
4. Model update and execution concurrency with TLA+ before expanding those protocols.
5. Evaluate Rust only on a new isolated component with a concrete memory-safety or portability rationale.
6. Benchmark Python research outputs and MiniZinc/Prolog experiments against the existing implementations.
7. Define the language-neutral capability contract before considering Wasm/WIT.
8. Adopt any additional production language only through the language adoption gate in the delivery plan.

## Decision rule

For each candidate language, ask:
- What exact Friday limitation does it remove?
- Is the benefit unavailable or materially harder in the existing language?
- What runtime, FFI, deployment and supply-chain costs does it add?
- How will we measure success on the actual target?
- Can the capability be replaced without changing Friday's identity or corrupting continuity?
- Can failures be contained and rolled back?

If those questions do not have evidence-backed answers, keep the language in research rather than production.

## Primary references

- Lean 4 language reference: https://lean-lang.org/doc/reference/latest/
- Lean learning materials: https://lean-lang.org/learn/
- Lean 4 release 4.36.0 (2026-11-16): https://lean-lang.org/doc/reference/latest/releases/v4.36.0/
- Lean GitHub CI action: https://github.com/leanprover/lean-action
- Kotlin Android overview: https://kotlinlang.org/docs/android-overview.html
- Kotlin Multiplatform: https://kotlinlang.org/multiplatform/
- Android NDK: https://developer.android.com/ndk/
- Rust book: https://doc.rust-lang.org/stable/book/
- Python tutorial: https://docs.python.org/3/tutorial/
- PyTorch C++ frontend: https://docs.pytorch.org/cppdocs/frontend
- ONNX Runtime mobile: https://onnxruntime.ai/docs/tutorials/mobile/
- llama.cpp Android: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- SQLite FTS5: https://www.sqlite.org/fts5.html
- TLA+ resources: https://docs.tlapl.us/
- Alloy: https://alloytools.org/about.html
- MiniZinc: https://www.minizinc.org/
- SWI-Prolog: https://www.swi-prolog.org/pldoc/doc_for?object=manual
- TypeScript handbook: https://www.typescriptlang.org/docs/handbook/
- CUE documentation: https://cuelang.org/docs/\n- Protocol Buffers overview: https://protobuf.dev/overview/\n- FlatBuffers documentation: https://flatbuffers.dev/index.html\n- WebAssembly core specification: https://www.w3.org/TR/wasm-core/
- WebAssembly WIT overview: https://component-model.bytecodealliance.org/design/wit.html
- Go documentation: https://go.dev/doc/
- Swift: https://www.swift.org/about/
