# Thursday Release Candidate — 2026-11-19

This branch is an isolated release-candidate line based on `audit/friday-offline-runtime`.
It does not modify `main`, merge draft feature branches, sign a release, or install on a device.

## Required automated gates

1. Run the repository's `.github/workflows/build.yml` workflow on this exact branch head.
2. Pass `python3 -m unittest discover -s tools/friday-builder -p 'test_*.py' -v`.
3. Pass `./gradlew :mobile:app:assembleDebug`.
4. Pass `./gradlew :mobile:app:testDebugUnitTest`.
5. Preserve the debug APK and JUnit result artifacts, including failure diagnostics.

## Integration policy

- The Android-native composition in `audit/friday-offline-runtime` is the only candidate foundation.
- PR #12 is a competing runtime line; do not merge it wholesale.
- PR #14 (Nano Liquid Symbiosis and model catalogue) and PR #16 (video intake and predictive cognition) are based on older revisions. Reconcile their source changes against this branch and resolve conflicts explicitly; do not merge stale branch tips.
- Keep the app's repository-writing/building authority outside the APK. Friday may submit bounded proposals; trusted CI validates and publishes draft PRs only.
- Do not represent model quality, inference speed, thermal stability, or offline generation as verified until tested on the target handset.

## Device acceptance still required after CI

- Install the exact candidate APK on the target Realme 9 Pro 5G.
- Cold-start, wake-order, permission, lifecycle and process-restart checks.
- Load each selected English model and complete a multi-turn offline generation test.
- Record peak memory, sustained thermal state, battery cost, and recovery after memory pressure.
- Verify voice capture and audible TTS with human observation.
- Verify model downloads are app-private, resumable, integrity-checked and recoverable after interruption.

A green CI run proves only the automated checks above. It is not device acceptance and is not a signed production release.
