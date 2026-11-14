# Friday device-review exchange

This repository is the build and review hand-off. It is not the authority on Friday's identity or final architecture.

## Your loop

1. Open the latest successful debug-apk artifact from GitHub Actions for the branch build.
2. Install the APK on your Android phone and use the checklist in test-plan.md.
3. Write what actually happened in a copy of templates/device-review.md.
4. Upload that report and any relevant crash log into reports/inbox/.
5. Tell the assistant a new report is there. It will be read and turned into a code fix and a new tested build.

## What to include

- Build commit SHA and APK version.
- Phone model, Android version, available RAM/storage (approximate is fine).
- Whether airplane mode was enabled.
- Whether a local GGUF model had been imported.
- What you tapped, what you expected, what happened, and exact error text.
- Crash logs only when relevant.

## Privacy

Do not upload passwords, API keys, payment details, private conversations, contact lists, or other people's personal information. Redact identifiers. Short test descriptions are more useful than full transcripts.

## Truth rule

A green GitHub build proves the APK compiled and automated tests passed. It does not prove a physical phone test passed. Device claims are recorded only after a device report provides evidence.
