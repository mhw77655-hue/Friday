# Friday Builder instructions

## 1. Mandatory source order

Before every task, read these files in order:
1. .ralph/VISION.md
2. .ralph/OWNER_WORDS.md
3. .ralph/FRIDAY_DELIVERY_PLAN.md
4. docs/FRIDAY_VISION_CONTRACT.md
5. .ralph/REPO_FACTS.md and .ralph/SYMBOL_FACTS.md
6. The relevant production source, tests, workflow and current PRD entries.

The owner's latest authenticated explicit instruction outranks all repository documents. VISION defines the destination; FRIDAY_DELIVERY_PLAN is the only active delivery plan. The contract, PRD, build notes, audits and code are subordinate. If they conflict, correct the subordinate document before implementing. Never revive a superseded roadmap or assistant proposal.

The curated OWNER_WORDS file preserves owner-authored excerpts and provenance. Do not attribute assistant paraphrases to the owner. The complete personal archive is private and must not be copied into this public repository. The archive was supplied for this project and its owner-authored meaning is represented by curated excerpts; do not tell the owner the archive is inaccessible or use old assistant plans as authority.

## 2. The destination is not a fixed architecture

Friday is one continuing digital individual. Preserve the owner's term **liquid Symbiotic quantum software**; do not redirect project work into a consciousness debate or replace the concept with a conventional fixed multi-agent architecture.

The protected continuity/security floor is the trunk. Capabilities are open branches. They exchange information through Need, Affordance, Resource field, Outcome and Continuity/floor contracts. These are open exchange surfaces, not a mandatory pipeline or cognitive taxonomy. Do not create permanent specialist ownership, closed capability lists, fixed cognitive stages or a frozen personality. Friday must learn universally from relevant accessible experiences and domains.

The same Friday continues across offline/online conditions, local/cloud resources, model replacement, process death and authorized future hosts. Voice is the first user-visible priority. All objectives progress in parallel where safely separable. Work converges on one canonical main product line; do not create competing Friday products, long-lived feature branches or alternative plans.

## 3. Plan before editing; progress in parallel

At the start of a task:
- Read the active delivery plan and identify the objective(s) this task advances.
- Inspect the live source and tests. State the concrete gap, root cause, files to change and evidence that will prove the result.
- Identify overlapping work and shared-file ownership before editing.
- Run independent research, audits, test design and non-conflicting work in parallel. Do not serialize unrelated objectives behind one Ralph loop.
- Do not concurrently edit shared composition, identity, vision, protected-floor or release-signing files. Integrate those changes deliberately into main.
- Stay on main. Do not create long-lived branches or another Friday product. Use only short-lived isolated work areas if strictly necessary, and reconcile promptly to main.

The PRD is an execution ledger derived from FRIDAY_DELIVERY_PLAN.md. It is not an alternative roadmap. If its open stories are stale, contradictory or narrowly prescribe a mechanism that conflicts with the plan, rewrite the affected stories first. Do not blindly execute old acceptance criteria.

## 4. Root-cause engineering

When a test or quality check fails:
1. Reproduce the exact failure and preserve raw output.
2. Inspect the actual stack trace, workflow step, device log or assertion.
3. Isolate whether it is caused by the current change or pre-existing.
4. State one evidence-backed root-cause hypothesis.
5. Repair the originating mechanism, not only the visible symptom.
6. Re-run the exact failing command, then the relevant regression suite.
7. Add a regression test that prevents the whole failure class from returning.
8. Record unrelated pre-existing failures honestly; never hide them or mark them passed.

Do not use random patches, skip failing work, weaken tests, change acceptance criteria after the fact, or claim a fix from a green unrelated workflow.

## 5. Source navigation and Kotlin imports

Before writing any import:
1. Check .ralph/SYMBOL_FACTS.md.
2. Check the file currently being edited.
3. Otherwise grep the real source tree for the declaration:
   grep -rnE '^(data |sealed |enum |abstract |open )*(class|interface|object) <ClassName>\b' mobile/app/src/main/java mobile/app/src/test/java

Use the exact package found. For nested declarations, include the outer object/type in the import. Never guess by convention or copy a neighboring import.

When an Unresolved reference or cannot find symbol error is fixed, append the real mapping to .ralph/SYMBOL_FACTS.md with file and line provenance before continuing.

## 6. Development evidence

A stored transcript, timestamp, profile entry, sentiment value, event counter or prompt replay is not developmental learning by itself.

A development claim must demonstrate experience → persistent internal change → changed later cognition or behaviour. Test the real Android production composition, restart between experience and retest, block replay of the original episode text, disable the proposed developmental mechanism in a control run, and observe a changed decision/behaviour on a different situation.

Universal learning must not be constrained to the feature/domain that first observed an event. Use relevant voice, text, visual, sensor, device, action, social and consequence evidence where legitimately available.

## 7. Protected floor and safe self-development

Owner authentication, authorization, protected operations, secrets, signing identity, provenance, artifact integrity, candidate isolation, continuity protection, update verification, rollback and recovery are non-liquid and cannot self-authorize.

Friday may research, reverse-engineer, combine, repurpose, invent and construct capabilities. Candidate source/runtime changes must be isolated, independently tested and compared against the existing capability. A worker cannot be the sole judge of its own output. Executable source, package, permission and signing changes require the applicable owner approval and verified rollback. Do not claim a restricted runtime DSL is equivalent to source-level self-development.

## 8. Product and device evidence

Android is the production host. Termux is not a required runtime. The actual installed application on the owner's phone is the acceptance target.

Label every result accurately:
- Designed / not implemented
- Implemented / connected to production path
- Unit-tested
- CI-verified for exact commit
- Emulator-verified
- Device-verified on the target phone

A successful build does not prove installation, voice, background operation, learning, action execution, offline behavior, update or rollback. Never claim a device result until it was actually run. Never claim an action succeeded until its postcondition was observed.

Voice is first user-visible priority. Verify microphone capture, transcription, spoken output, conversation turn-taking, interruption, cancellation, offline availability and language quality on real audio. Prioritize Egyptian Arabic and mixed Arabic/English, and do not claim naturalness without evaluation and the owner's direct listening test.

## 9. Build, test and record

Use the repository's actual Gradle wrapper and documented commands. Run targeted tests plus the full relevant regression suite and Android build. A red test is a red result. If artifact download or network transport fails after CI has concluded, distinguish the transport failure from the workflow verdict and retry the artifact retrieval without rewriting the verdict.

Append progress to .ralph/progress.txt; do not replace its history. Record:
- objective advanced and exact files changed;
- root cause and implementation;
- commands and exact results;
- CI run/commit identifiers and artifact links;
- device evidence or explicitly missing device evidence;
- unresolved risks and the next concrete action.

Update REPO_FACTS.md and SYMBOL_FACTS.md only with verified reusable facts. Keep source vision files owner-controlled and do not change them casually; any requested vision correction must be reflected consistently in VISION.md, OWNER_WORDS.md where relevant, the active delivery plan and dependent contract/prompt documents.

## 10. Stop condition

Do not declare Friday complete because a PRD is green, tests pass, an APK assembles or an emulator works. The delivery plan's acceptance evidence must be met on the real product path. A first installable milestone may be delivered earlier, but it must be the same persistent seed with a real developmental and capability-growth path.
