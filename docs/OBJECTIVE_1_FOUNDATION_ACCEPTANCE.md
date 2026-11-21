# Objective 1 — foundation acceptance record

**Recorded:** 2026-11-21  
**Scope:** repository and Android foundation only  
**Canonical branch:** `main`  
**Baseline verification:** `c63f5ef4aded86fdfeb3606b6901c944eeb8cba8`

## Decision

The repository-foundation portion of Objective 1 is accepted at the baseline above, subject to the final CI run for the commit that adds this record. This is not acceptance of the complete Friday product, the first production installation, or Objective A's physical-device and install-once gates.

## Evidence

- **Android build:** the `build` workflow completed successfully on the baseline commit. Debug APK assembly, release-variant smoke assembly with a disposable CI key, launcher-icon packaging check, and JVM unit tests all passed.
- **Instrumented Android lane:** the disposable Android emulator job completed successfully. It built the app and instrumentation APKs, booted the virtual device, and ran the repository's virtual-phone harness.
- **Repository audit:** whole-repository source inventory and Kotlin vision audit completed successfully.
- **Build artifacts:** the workflow published `debug-apk`, `junit-results`, `friday-virtual-phone-evidence`, and `friday-whole-repository-source-audit`. The debug APK artifact is a CI/debug artifact, not a production release.
- **Formal contracts:** Lean 4 formal workflow passed at `278b714c01d6bc1d1f27500abdbc7323fa0513a6`. The formal workspace, formal workflow, and delivery plan were unchanged between that commit and the baseline; the proof result therefore applies to the same formal inputs.
- **Identity:** Gradle namespace and application ID are `ai.friday.assistant`; the manifest application label is `Friday`. The current app module and manifest agree.
- **Repository authority:** `.ralph/FRIDAY_DELIVERY_PLAN.md` is the sole active delivery plan. The obsolete `Plan_Friday_Development__3_.txt` is absent from `main`. The older architecture audit has now been explicitly marked as a historical branch snapshot, not current production truth.
- **Action-completion invariant:** `CapabilityExecutor` independently verifies the requested postcondition. A capability's own success flag is insufficient; absent or failed verification routes the step to failure rather than completion. The executor is used by `CognitiveEngine.runNext()` through its capability-fabric boundary; production composition exposes the fabric and cognitive engine from the same assembly.
- **Bootstrap truthfulness:** missing local inference/model availability is reported as degraded capability state; it does not require a pre-existing Termux/Ollama/localhost server or block Friday's identity/UI bootstrap.

## Explicitly not claimed

- No physical installation, permission grant, or acceptance test has been performed on the owner's Realme 9 Pro 5G.
- No local model is installed on the owner's phone. Compiled inference support is not evidence of a resident model.
- No production-signed APK or GitHub Release has been published. The release workflow completed its decision job, but signing/build/publish steps were skipped because the permanent owner signing secrets are not configured.
- Same-key in-place upgrade, post-update continuity health checks, and last-known-good rollback are not yet proven as production guarantees.
- Full offline end-to-end cognition, persistent developmental learning, and universal phone control are not accepted by this foundation record.
- Voice-control implementation remains in Objective B's existing voice phases; it is not an Objective 1 gate.

## Remaining owner/device gates

1. Configure and securely back up the permanent release keystore, then add the five documented repository secrets and verify the certificate fingerprint.
2. Build and publish the permanent-signed seed; prove same-key in-place upgrade and data continuity on an emulator.
3. Install that signed seed on the owner's phone and record launch, permission, identity, storage, runtime, and update-channel evidence.
4. Complete the physical-device acceptance in the delivery plan, including the supported typed interaction and restart/continuity checks.

These gates require the owner's signing material and physical device. They cannot be honestly completed by repository CI alone.

## Evidence links

- [Successful build and all three validation jobs](https://github.com/mhw77655-hue/Friday/actions/runs/44922993601)
- [Lean formal contracts and axiom audit](https://github.com/mhw77655-hue/Friday/actions/runs/44914858181)
- [Verified baseline commit](https://github.com/mhw77655-hue/Friday/commit/c63f5ef4aded86fdfeb3606b6901c944eeb8cba8)
