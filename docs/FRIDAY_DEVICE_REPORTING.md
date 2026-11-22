# Friday device-to-GitHub reporting

## What it does

Friday can submit a bounded, privacy-filtered live-device report directly from Settings to the `mhw77655-hue/Friday` repository. The report is created as a GitHub issue. The `Friday device report archive` workflow validates the issue payload and stores the JSON as a GitHub Actions artifact for 14 days.

This is an explicit owner action. Friday does not upload device reports silently or on every app launch.

## One-time authorization

1. Open Friday → Settings → GitHub Device Reporting.
2. Tap **Create fine-grained GitHub token**.
3. On GitHub, create a fine-grained personal access token with:
   - Resource owner: the account that owns the repository.
   - Repository access: **Only select repositories** → `Friday`.
   - Repository permissions: **Issues → Read and write**. Metadata read is automatic.
4. Copy the token from GitHub and paste it directly into Friday. Do not paste it into ChatGPT, an issue, a source file, or a workflow log.
5. Tap **Save GitHub credential**, then **Verify access**.
6. After a device test, tap **Upload device report**. Friday creates one issue and GitHub Actions archives the validated JSON.

The create-issue endpoint requires the fine-grained token's Issues write permission. Friday also reads the issue list to make retries idempotent; write permission includes read for this permission family.

## Credential and privacy boundary

- Friday encrypts the token with AES-GCM using a non-exportable Android Keystore key.
- The token is sent only as the HTTPS Authorization header to `api.github.com`; it is never included in the report.
- The token is not committed, placed in GitHub Actions secrets, or sent to ChatGPT.
- The report includes app version, Android API, source revision, capability/permission states, sanitized crash class and stack frames, and bounded action-category events.
- It excludes conversations, speech transcripts, typed payloads, screen text, notification content, and raw exception messages.
- Reports are bounded to 40 KiB and rejected if they resemble GitHub credentials.
- The `mhw77655-hue/Friday` repository is currently public. Uploaded GitHub issues and Actions artifacts are therefore readable by anyone. Upload only if you accept the sanitized device diagnostics being public.
- Remove the credential in Friday Settings to delete the local ciphertext and Keystore key. Revoke the token separately in GitHub token settings when it is no longer needed.

## Delivery semantics

- A report has a stable UUID in its issue title. If GitHub accepted the issue but the phone lost the response, retrying the same report finds the existing issue instead of creating a duplicate.
- Friday persists the exact pending report locally until GitHub returns the issue URL. A retry reuses the same UUID and payload; the owner can explicitly discard it to capture a fresh snapshot.
- GitHub Actions validates the report type, schema version, ID/title match, size, and credential-like content before archiving.
- Invalid reports fail closed and are not archived.
- The issue remains the durable record; the Actions artifact is a short-lived convenience copy.

## Acceptance boundary

CI can prove report-envelope validation, HTTPS request construction, credential encryption on an emulator, and issue-archive workflow syntax. Only the owner's installed signed APK, actual GitHub authorization, and physical-device test can prove end-to-end delivery from the handset.
