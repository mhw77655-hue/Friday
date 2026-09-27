# SYMBOL_FACTS.md — class -> real package, never guess it

Auto-injected into every Ralph prompt, immediately after REPO_FACTS.md, by the
prompt-composition path in `ralph.sh` (`build_prompt`). Permanent, append-only
memory of where a class is REALLY declared.

**This file exists because a guessed package costs a full CI round trip.** Two
separate stories, days apart, wrote `import com.jarvis.app.model.ModelProviderType`
and only discovered the real location when CI failed on `Unresolved reference`.

## The rule (mandatory)

Before you write an import for ANY class, and in this order:

1. If the class is listed below, use the package shown here. Done.
2. Otherwise grep the real source tree for its declaration and use what the
   grep says. Done. (The exact command is in prompt.md's grep-before-import rule.)

Never guess a package by convention, and never copy the shape of a neighbouring
import (every member of a package is not automatically in that package).

## Format

    ClassName -> real.package.path (relative/File.kt:line)

One fact per line, append-only, newest at the bottom under its own heading.

## Facts

### 2026-09-27 — seeded from real "Unresolved reference" compile errors in this repo

- ModelProviderType -> com.jarvis.app.env (mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:254)
  Declared as `enum class ModelProviderType`. It is NOT in `com.jarvis.app.model`
  even though almost every user of it sits in the model package. Two stories lost
  a CI round trip each guessing `com.jarvis.app.model`.

- Cursor -> android.database (type returned by android.database.sqlite.SQLiteDatabase.rawQuery)
  NOT `android.database.sqlite.Cursor` — that subclass was removed from the SDK
  stubs. Import `android.database.Cursor` for the nullable column readers in
  AndroidMemoryGraphStore.

- OpenThread -> com.jarvis.app.threads.ThreadObjects (mobile/app/src/main/java/com/jarvis/app/threads/ThreadObjects.kt:55)
- Completeness -> com.jarvis.app.threads.ThreadObjects (mobile/app/src/main/java/com/jarvis/app/threads/ThreadObjects.kt:37)
  Both are NESTED inside `object ThreadObjects` (line 34). A sibling top-level
  class in the same package (ThreadTracker, line 173, same file) does NOT
  auto-scope them: Kotlin needs an explicit
  `import com.jarvis.app.threads.ThreadObjects.OpenThread`. One unresolved name
  cascaded into 30+ errors at the call site.

### recorded by symbol_facts_check.sh on 2026-09-27
- Accumulate -> com.jarvis.app.humancore.algo (mobile/app/src/main/java/com/jarvis/app/humancore/algo/Accumulate.kt:16)

### recorded by symbol_facts_check.sh on 2026-09-27
- MemoryPolicy -> com.jarvis.app.env (mobile/app/src/main/java/com/jarvis/app/env/EnvironmentProfile.kt:271)
  A live example of the trap this file exists for: `com.jarvis.app.memory` IS a
  real package in this repo, so `import com.jarvis.app.memory.MemoryPolicy` looks
  obviously right and resolves nowhere. The whole `env` block (EnvironmentProfile,
  ModelSource, ToolCapability, SafetyRestrictions, LatencyPreference, OfflineBehavior,
  UIBehavior, SyncBehavior, PermissionPolicy) lives in `com.jarvis.app.env`, not in
  the package its name suggests.
