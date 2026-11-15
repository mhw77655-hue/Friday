# Friday development request inbox

A request is one JSON file named `<request_id>.json` in this directory.

Before repository-worker execution, the request must:
1. Match `tools/friday-builder/development-request.schema.json` and pass
   `tools/friday-builder/validate_request.py`.
2. Contain only technical evidence and acceptance criteria needed for the task.
   Do not include transcripts, personal files, recordings, credentials or other
   private content.
3. Have an explicit `privacy_review.status: "approved"` attestation from the
   repository owner or a trusted privacy-review gateway.

The Android app does not write to this repository or hold Git credentials.
This inbox is currently a repository-side intake contract; automatic app-to-repo
synchronization is a separate, unfinished integration.

To propose a request, dispatch the **Friday repository builder proposal**
workflow with its stable request ID. The worker runs without repository-write
credentials and can only produce a proposal patch. A separate publishing job
creates a draft PR; it never merges or releases.
