# Friday GitHub development and phone bridge

## What is now implemented

Friday's Android Settings can connect directly to the public `mhw77655-hue/Friday` repository.

### Repository development request
- The owner supplies a fine-grained GitHub token in Settings.
- The token is encrypted with AES-GCM using a non-exportable Android Keystore key. The plaintext token is not written to SharedPreferences, source code, logs, or workflow payloads.
- "Run Friday self-maintenance" sends a bounded JSON request through GitHub's `workflow_dispatch` API.
- The request includes a consented technical snapshot: manufacturer/model, Android API/release, app version, free/total RAM, app-private storage, battery percentage, thermal status, and network transport.
- It excludes conversation text, location, contacts, notifications, microphone/camera data, screen contents, files, and unique device identifiers.
- The repository worker receives no write token. It can only create a patch artifact in its disposable job.
- A separate trusted publisher checks changed paths and creates an isolated draft PR. It never merges, signs, releases, or installs.
- Friday can query the resulting proposal PR from the phone.

### Automatic repair proposal after CI failure
- The proposal workflow also listens for a completed `build` workflow on `main`.
- When main-branch CI fails, it fetches failed-run logs with read-only Actions permission, filters failure/error lines, redacts common credential and email patterns, and creates a bounded diagnostic request.
- The same untrusted worker produces a patch artifact; a separate publisher validates paths and opens a draft PR.
- This does not auto-merge or auto-install. Proposal branches are built separately.
- CI failures on pull-request branches do not automatically launch a worker, avoiding untrusted PR code triggering privileged publication.

### Phone-initiated diagnostic command channel
- The owner explicitly connects the phone from Settings.
- Friday creates a public GitHub issue as an auditable command queue.
- WorkManager polls the issue when Android permits background work, with a 15-minute periodic interval; foreground manual polling is also available.
- Only comments authored by the repository owner are considered.
- Commands must include a unique ID, expiry timestamp, and an allowlisted action.
- The only supported action is `diagnostics.snapshot`.
- The phone returns a bounded technical snapshot as a comment: device compatibility, content-free runtime/development aggregates, selected model readiness, and recent crash exception types/stack locations. Exception messages and private memory/conversation content are excluded.
- Stop channel closes the issue and cancels scheduled polling.

## Setup required after the workflow is merged to main

1. Create a GitHub fine-grained personal access token for repository `Friday` only.
2. Grant only:
   - Actions: Read and write
   - Pull requests: Read
   - Issues: Read and write
   - Metadata: Read (GitHub's required repository metadata permission)
3. Do not grant Contents: Write, Administration, Workflows write beyond the Actions permission, or access to other repositories.
4. In Friday Settings, paste the `github_pat_` token and save it. The app stores it encrypted with Android Keystore.
5. Use "Connect phone to GitHub" to create the device channel and enable periodic polling.
6. Use "Run Friday self-maintenance" to send a device snapshot and request one evidence-supported repair proposal.
7. Use "Check latest repair proposal" to retrieve the draft PR link.

The token is not embedded in the APK. The user can revoke it from GitHub at any time or remove it in Friday Settings.

## Owner command protocol

Post this exact format as a comment on the issue created by Friday:

```text
FRIDAY_DEVICE_COMMAND v1
id: 7f5d6c2e-7ab9-4b4e-a0b1-2d49bde8c901
expires: 2026-11-16T23:00:00Z
action: diagnostics.snapshot
```

The expiration must be in the future and no more than 30 minutes away when Friday polls. The phone ignores comments from anyone except the repository owner. It rejects all actions except `diagnostics.snapshot`.

## Important platform and privacy limits

- The Friday repository is public. Workflow inputs, device-channel issue commands, and diagnostic replies are public to repository readers. The app explicitly warns before sending. Reports include technical device/runtime metadata and bounded stack locations, but omit exception messages, private memories, prompts, transcripts and conversation content.
- This is a periodic pull channel, not an always-on socket. Android WorkManager may defer jobs due to Doze, battery optimization, network constraints, or OEM policies. Android recommends WorkManager for deferrable background tasks; continuous foreground services require a visible notification and are subject to platform restrictions.
- The phone can be diagnosed only while Friday is installed, paired, permitted to run, and able to reach GitHub.
- The channel is intentionally read-only. It does not expose ADB, a shell, arbitrary files, screen contents, microphone/camera streams, contacts, notifications, location, or silent app actions.
- The self-development worker can propose source changes, but code review and merge remain owner-controlled. A draft PR is not an installed repair.
- A workflow dispatch returns HTTP 204. The workflow must exist on the repository's default branch before the app can invoke it; therefore this feature becomes operational after this PR is merged to `main`.
- GitHub's hosted runner cannot test the actual handset. Device-specific acceptance still requires installing a verified APK and exercising the app on the phone.

## Failure handling

- Invalid or oversized request payloads are rejected before the coding worker starts.
- The request validator rejects unknown fields, transcripts, unapproved privacy review, malformed IDs, oversized content, and common credential patterns.
- The worker has no repository write credential.
- The publisher refuses protected paths including workflow files, Ralph control files, builder validators, request inbox, secrets, signing keys, and release binaries.
- Every proposal is an isolated draft PR and receives a separate Android build/test run.
- The phone never installs or merges a proposal automatically.


## Owner-enabled autonomous development cycles

Friday Settings now includes an **Let Friday develop herself** opt-in. When enabled, WorkManager schedules a bounded development request about every 12 hours, subject to network, battery-not-low and storage-not-low constraints. Enabling also queues an immediate first cycle.

Each scheduled cycle:
- Reads the product contract from `.ralph/VISION.md` and the curated owner-authored source excerpts from `.ralph/OWNER_WORDS.md`; the coding worker can search the original `New Folder chats.zip` when needed.
- Asks the repository worker to identify one meaningful gap between the current implementation and the owner's intended outcome, implement one coherent improvement, add regression tests, and run the Android build/test suite.
- Sends no phone telemetry, conversations, location, files, microphone/camera data or personal content. It sends only the owner-authorized development instruction and workflow metadata.
- Allows only one open proposal at a time and throttles repeated requests to at most one per 12 hours after the previous dispatch.
- Produces a public draft PR only. It never merges, signs, releases, installs or changes the installed APK.

The toggle is off by default. Disconnecting the GitHub credential disables and cancels scheduled development work. WorkManager is deferrable scheduling, not an always-on execution guarantee; Android can delay it under Doze, low battery, low storage, network loss or OEM policy. The feature becomes operational only after the proposal workflow is present on the repository's default branch and the stored token has the required Actions permission.

The app's installed Friday remains the same individual while cloud GitHub Actions supply additional build compute. This does not make the model or cloud worker her identity, and it does not claim subjective consciousness.
