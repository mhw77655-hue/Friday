#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

[[ "$(git branch --show-current)" == "main" ]] || { echo "Refusing migration outside main" >&2; exit 20; }
[[ "$(git remote get-url origin)" == *"mhw77655-hue/Friday"* ]] || { echo "Unexpected repository remote" >&2; exit 21; }
grep -q 'applicationId = "com.jarvis.app"' mobile/app/build.gradle.kts
grep -q 'namespace = "com.jarvis.app"' mobile/app/build.gradle.kts

python3 - <<'PY'
from pathlib import Path
import re
import subprocess

def git(*args):
    subprocess.run(["git", *args], check=True)

# Move the complete production, JVM-test and instrumentation-test package trees.
for source in (
    "mobile/app/src/main/java/com/jarvis/app",
    "mobile/app/src/test/java/com/jarvis/app",
    "mobile/app/src/androidTest/java/com/jarvis/app",
):
    src = Path(source)
    if src.exists():
        destination = Path(str(src).replace("com/jarvis/app", "ai/friday/assistant"))
        if destination.exists():
            raise SystemExit(f"Refusing package destination collision: {destination}")
        destination.parent.mkdir(parents=True, exist_ok=True)
        git("mv", str(src), str(destination))

# Rename old product-named files. Do not rename or rewrite URLs: they may be valid donor endpoints.
tracked = subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0")
for raw in tracked:
    if not raw:
        continue
    path = Path(raw)
    if not path.exists():
        continue
    new_name = re.sub("jarvis", "Friday", path.name, flags=re.IGNORECASE)
    if new_name != path.name:
        target = path.with_name(new_name)
        if target.exists():
            raise SystemExit(f"Refusing filename collision: {path} -> {target}")
        git("mv", str(path), str(target))

url = re.compile(r"https?://\S+")
replacements = (
    ("com.jarvis.app", "ai.friday.assistant"),
    ("com/jarvis/app", "ai/friday/assistant"),
    ("JARVIS", "FRIDAY"),
    ("Jarvis", "Friday"),
    ("jarvis", "friday"),
)
def normalize(segment):
    for old, new in replacements:
        segment = segment.replace(old, new)
    return segment

tracked = subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0")
changed = 0
for raw in tracked:
    if not raw:
        continue
    path = Path(raw)
    if not path.is_file():
        continue
    try:
        source = path.read_bytes().decode("utf-8")
    except UnicodeDecodeError:
        continue
    parts = url.split(source)
    updated = "".join(part if url.fullmatch(part) else normalize(part) for part in parts)
    if updated != source:
        path.write_text(updated, encoding="utf-8")
        changed += 1
print(f"Friday identity normalized in {changed} text files; external URLs preserved.")

# Set the new canonical package identity and initial seed version.
gradle = Path("mobile/app/build.gradle.kts")
s = gradle.read_text()
s = s.replace('namespace = "ai.friday.assistant"', 'namespace = "ai.friday.assistant"')
s = s.replace('applicationId = "ai.friday.assistant"', 'applicationId = "ai.friday.assistant"')
s = s.replace('"0.2-phase2"', '"1.0.0-seed"')
gradle.write_text(s)

manifest = Path("mobile/app/src/main/AndroidManifest.xml")
s = manifest.read_text().replace('android:label="FRIDAY"', 'android:label="Friday"')
manifest.write_text(s)

# Keep only current, accurate repository facts.
Path("REPO_FACTS.md").write_text("""# Friday repository facts

- Product: Friday, one continuing Android-hosted individual.
- Canonical repository: mhw77655-hue/Friday.
- Canonical development branch: main.
- Android module: mobile/app.
- Android application ID: ai.friday.assistant.
- Android namespace and Kotlin base package: ai.friday.assistant.
- Sole active delivery authority: .ralph/FRIDAY_DELIVERY_PLAN.md.
- Owner vision: .ralph/VISION.md and .ralph/OWNER_WORDS.md.
- Build: ./gradlew :mobile:app:assembleDebug.
- Unit tests: ./gradlew :mobile:app:testDebugUnitTest.
- Emulator/instrumentation and physical-device evidence are separate from JVM unit-test evidence.
- Live Ollama tests are opt-in diagnostics; a localhost model server is not a production dependency or proof of offline operation.
- CI artifacts and temporary signing keys are not the permanent production signing identity.
- Do not infer current phone firmware, installed state, permissions or physical-device success from source code or emulator tests.
- Keep implementation, tests, release metadata, workflows and documentation aligned with the delivery plan.
""")

Path(".ralph/REPO_FACTS.md").write_text("""# Friday repository facts

The sole active product and delivery authority is .ralph/FRIDAY_DELIVERY_PLAN.md.
Owner-authored intent is in .ralph/VISION.md and .ralph/OWNER_WORDS.md.
The canonical product line is main; do not create competing branches or roadmaps.

Android module: mobile/app
Application ID: ai.friday.assistant
Namespace and Kotlin base package: ai.friday.assistant

From repository root:
- ./gradlew :mobile:app:assembleDebug
- ./gradlew :mobile:app:testDebugUnitTest

A unit-test pass is not APK assembly, emulator instrumentation, physical-device validation, or release acceptance. Live Ollama tests are opt-in diagnostics and do not establish the shipped offline path. No localhost service is a production prerequisite unless explicitly documented, configured and verified as an optional capability.

Re-check current main source and production composition before making capability claims. Historical audits, vault documents and test doubles are evidence only, not current product truth.
""")

Path("AGENTS.md").write_text("""# Friday repository instructions

## Authority
- .ralph/FRIDAY_DELIVERY_PLAN.md is the sole active delivery plan.
- .ralph/VISION.md and .ralph/OWNER_WORDS.md preserve owner intent.
- Friday is the product identity. The Android application ID and Kotlin namespace are ai.friday.assistant.
- Historical audits and archive material are not active implementation instructions.

## Git and project structure
- Make authorized changes directly on main. Do not create feature branches, proposal branches or competing product lines.
- The production host is the Android application in mobile/app.
- Keep development, build, compiler and repository credentials outside the installed APK.
- Preserve useful algorithms and validated behavior; retire disconnected prototypes, stale destinations and false capability claims.
- Keep source paths, package declarations, manifest component names, Gradle identifiers, tests and release metadata aligned with ai.friday.assistant.

## Verification
- Distinguish source edits, compilation, unit tests, APK assembly, emulator instrumentation, CI verification and physical-device verification.
- Never report skipped, cancelled or pending checks as successful.
- A passing JVM test does not prove production wiring or offline functionality.
- Never claim physical-device behavior without physical-device evidence.
- Treat test doubles and fixtures as test-only; do not expose them as production capabilities.

## Product constraints
- Friday owns identity and continuity; models, algorithms, tools and cloud services are replaceable capabilities.
- Local durable state is the source of truth for offline continuity; cloud is optional expansion.
- Optimize verified task outcomes and time to first useful action.
- Preserve Laya as a distinct decision capability, not ASR or TTS.
- Protect owner authorization, permissions, signing trust, provenance, rollback and recovery.
""")

# Remove the obsolete branch-era audit; it conflicts with the single-mainline workflow.
if Path(".ralph/REPO_UNIFICATION_AUDIT.md").exists():
    git("rm", "-f", ".ralph/REPO_UNIFICATION_AUDIT.md")

# Remove the disconnected Python-builder CI test; keep Android build and test gates.
build = Path(".github/workflows/build.yml")
s = build.read_text()
s = s.replace("      - name: Verify Ralph receives the owner's vision and direct words\n",
              "      - name: Verify Friday delivery authority and owner vision\n")
start = s.find("      - name: Set up Python for Friday builder contract tests\n")
end = s.find("      - name: Set up Go for workflow lint\n")
if start >= 0 and end > start:
    s = s[:start] + s[end:]
s = s.replace("on:\n  workflow_dispatch:\n", "on:\n  push:\n    branches: [ main ]\n  workflow_dispatch:\n")
s = s.replace("label='FRIDAY'", "label='Friday'")
build.write_text(s)

# Record the authorized fresh identity and the work still required after the rename.
plan = Path(".ralph/FRIDAY_DELIVERY_PLAN.md")
s = plan.read_text()
if "Fresh Friday identity baseline" not in s:
    marker = "# Friday"
    section = """

## Fresh Friday identity baseline (2026-11-21)

The owner confirmed the previous Friday APK had already been deleted and explicitly authorized a clean application identity. The canonical Android application ID, Gradle namespace and Kotlin base package are ai.friday.assistant. All source/test package trees, manifest component references, build filters, signing metadata, release metadata, repository instructions and active product references must use this identity. The prior com.jarvis.app package is not a compatibility target. Historical donor research remains non-authoritative evidence and must never define live app destinations, endpoints or product truth.

The first clean Friday build must compile and pass unit tests, produce a correctly identified APK, pass emulator instrumentation and then undergo separate physical-phone acceptance. A clean rename is not proof that the inherited architecture is correct: the next engineering pass must audit and reconnect the real production composition, offline inference/memory, voice/action routes, model and embedding destinations, permissions and update path before the permanent seed is approved.
"""
    if marker not in s:
        raise SystemExit("Could not find delivery plan title")
    s = s.replace(marker, marker + section, 1)
    plan.write_text(s)

# The migration is one-shot. Remove its own trigger, script and workflow from the final tree.
for path in (
    "scripts/perform_friday_identity_migration.sh",
    ".github/workflows/friday-identity-migration.yml",
    ".friday-identity-migration.trigger",
):
    if Path(path).exists():
        git("rm", "-f", path)
PY

git diff --check
echo "FRIDAY_IDENTITY_MIGRATION_PREPARED"
