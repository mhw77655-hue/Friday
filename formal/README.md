# Friday formal assurance

The initial Lean 4 workspace is in `lean/`. It models the action-outcome state machine and proves that a reachable completed state carries both an observed postcondition and an independent verification result.

Run locally from `formal/lean`:

```sh
lake build
```

CI uses the pinned `lean-toolchain` and `leanprover/lean-action@v1`.

**Scope warning:** the theorem proves the abstract Lean transition system. It does not yet prove that the Kotlin task executor, Android APIs or physical device refine this model. That requires an explicit implementation mapping and conformance/instrumentation tests. Do not advertise the theorem as proof of real-world action success.

Keep this workspace dependency-light. Add Mathlib only when a formal development needs it. No `sorry`, unchecked project axioms or test-only theorem substitutions are permitted.
