# Objective 1 Builder candidate record

**Branch:** objective1-builder-mainline  
**Status:** candidate implementation; not merged or accepted  
**Canonical plan:** .ralph/FRIDAY_DELIVERY_PLAN.md on main

## Implemented in this candidate
- First-launch host study, conservative resource admission and private app-managed development workspace; records CPU/memory/storage/thermal/battery, Vulkan feature declarations, NNAPI API availability, and explicitly labels runtime GPU/NPU capability as unverified until actually probed.
- Offline canonical-plan bootstrap from the APK asset, integrity metadata, durable objective/task ledger and checkpoints.
- Resident-model task decomposition and bounded source-candidate generation from real repository context.
- Candidate path and secret validation; isolated GitHub branch/PR publication only.
- CI polling and task-ledger evidence recording.
- Fail-closed task dependency gate: every declared prerequisite must be explicitly ACCEPTED; missing or merely CI-passed dependencies remain blocked.
- Android-Keystore-backed repository credential storage.
- Consent-gated automatic GitHub device-report worker: after one-time credential setup and separate explicit public-reporting consent, it uploads sanitized host-bootstrap and Builder milestone evidence with stable IDs and retry-safe issue creation. Disabled by default; no conversation/speech/screen/notification/token data is included.
- Friday-owned local Table transcript, common Seat contract, fixed stance parsing, and an owner-visible AI Table screen with Roundtable and Vision views. One anonymous OVHcloud open-weight guest is wired into the Builder and receives only explicitly public/shareable context. The provider documents an anonymous limit of two requests per minute per IP and model; availability is best-effort, no paid fallback is attempted, and this is not ChatGPT, Claude or Gemini.
- Host-adaptive resident model selection and no Termux runtime dependency.

## Known gaps before acceptance
- Exact-head CI is now green: [build run #2909](https://github.com/mhw77655-hue/Friday/actions/runs/45120253154) for commit `beb40b5bc737211b41431959f263557ae8425a3d`; debug APK, JUnit results, whole-repository audit and disposable virtual-phone evidence artifacts were produced. This is CI/emulator evidence, not physical handset acceptance or a production-signed APK.
- Builder now opens isolated candidates, polls CI and records a separate exact-diff external review. Candidate publication has process-death recovery and task leases. A complete automated repair/retry lifecycle, fixed-cost/quota accounting beyond cooldown, and safe capability promotion are not yet demonstrated.
- Table currently has one anonymous external Seat; a full multi-seat registry, per-seat delta-history computation and richer quota accounting still need implementation and tests. The Roundtable/Vision presentation UI and fixed stance parsing are implemented; persistent cooldown/presence state is stored locally.
- Local app-managed toolchain/build sandbox is not yet demonstrated as a complete source-build environment; remote CI is the current candidate build path.
- Physical host adaptation, actual resident-model load, force-stop/reboot recovery and the first installed autonomous Builder cycle remain unverified.
- Permanent signing and same-key update continuity on the physical handset remain outstanding.

The candidate has a green exact-head CI run, but it remains unmerged. The debug APK artifact is not a production release. Production signing follows integration into main; physical-host acceptance, same-key update continuity, fault-injection evidence and the first installed Builder cycle are still required. A candidate PR, generated patch, passing emulator, or CI run alone is not Objective 1 acceptance.
