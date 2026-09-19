# Engineering Standards Knowledge Base -- Seed (v0)

Read-only. Builder Mode consults this alongside the blueprint during its
Diff step. Never self-executing -- informs proposals, doesn't make them.
Tier 2 protected -- expands only through the Builder Mode cycle.

## Kotlin / Android (mobile/app/, from Phase 2 onward)
- Compose-first UI.
- ViewModels expose StateFlow.
- A hardware resource (mic, camera) has exactly one owning class.
- minSdk 26 / compileSdk 36 / targetSdk 36 unless a mission changes this.
- Set abiFilters explicitly.

## Python (hf_space/, from Phase 1 onward)
- Every file edit verified with py_compile before considered done.
- No self-modification bypass paths -- every autonomous code change routes
  through the human-in-the-loop approval gate (Blueprint Section 10).

## Security
- Secrets never pasted in plaintext into chat or committed to git. If it
  happens, the credential is compromised -- rotate it, don't just note it.
- New capability needing access outside the manifest gets a named,
  auditable connector first -- never a blanket permission.

## Testing
- Every Tier 2 change ships with a test proving both the allowed and
  denied paths where applicable.

## Documentation
- Every mission report: what was built, what changed, what's next.
- Undecided/undocumented architectural decisions are not-yet-durable.

## Git workflow
- Builds run via GitHub Actions only (no local PC).
- Gradle wrapper stays gitignored.

---
*Expands only through the Builder Mode cycle once Phase 4 is live.*
