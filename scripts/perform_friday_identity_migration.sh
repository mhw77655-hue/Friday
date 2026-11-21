#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

[[ "$(git branch --show-current)" == "main" ]] || { echo "Refusing migration outside main" >&2; exit 20; }
[[ "$(git remote get-url origin)" == *"mhw77655-hue/Friday"* ]] || { echo "Unexpected repository remote" >&2; exit 21; }
grep -q 'applicationId = "com.jarvis.app"' mobile/app/build.gradle.kts
grep -q 'namespace = "com.jarvis.app"' mobile/app/build.gradle.kts

# Move the complete production, JVM-test and instrumentation-test package trees.
for source in \
  mobile/app/src/main/java/com/jarvis/app \
  mobile/app/src/test/java/com/jarvis/app \
  mobile/app/src/androidTest/java/com/jarvis/app; do
  if [[ -d "$source" ]]; then
    parent="${source%com/jarvis/app}"
    destination="${parent}ai/friday/assistant"
    mkdir -p "$(dirname "$destination")"
    [[ ! -e "$destination" ]] || { echo "Destination already exists: $destination" >&2; exit 22; }
    git mv "$source" "$destination"
  fi
done

# Normalize all tracked UTF-8 text while preserving external URL destinations.
python3 - <<'PY'
from pathlib import Path
import re, subprocess

paths = subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0")
url = re.compile(r"https?://[^\\s\"'<>]+")
replacements = (
    ("com.jarvis.app", "ai.friday.assistant"),
    ("com/jarvis/app", "ai/friday/assistant"),
    ("JARVIS", "FRIDAY"),
    ("Jarvis", "Friday"),
    ("jarvis", "friday"),
)

def normalize_segment(segment: str) -> str:
    for old, new in replacements:
        segment = segment.replace(old, new)
    return segment

changed = 0
for raw in paths:
    if not raw:
        continue
    path = Path(raw)
    if not path.is_file():
        continue
    data = path.read_bytes()
    try:
        source = data.decode("utf-8")
    except UnicodeDecodeError:
        continue
    parts = url.split(source)
    normalized = "".join(part if url.fullmatch(part) else normalize_segment(part) for part in parts)
    if normalized != source:
        path.write_text(normalized, encoding="utf-8")
        changed += 1
print(f"Normalized Friday identity in {changed} tracked text files; external URLs were preserved.")
PY

# Rename old product-named source and documentation files without changing unrelated names.
python3 - <<'PY'
from pathlib import Path
import re, subprocess
paths = subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0")
for raw in paths:
    if not raw:
        continue
    path = Path(raw)
    if not path.exists():
        continue
    new_name = re.sub("jarvis", "Friday", path.name, flags=re.IGNORECASE)
    if new_name == path.name:
        continue
    target = path.with_name(new_name)
    if target.exists():
        raise SystemExit(f"Refusing filename collision: {path} -> {target}")
    subprocess.run(["git", "mv", str(path), str(target)], check=True)
PY

# Rewrite stale repository facts rather than cosmetically renaming incorrect destinations.
cat > REPO_FACTS.md <<'EOF'
# Friday repository facts

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
- Keep all implementation, tests, release metadata, workflows and documentation aligned with the delivery plan.
EOF
cat > .ralph/REPO_FACTS.md <<'EOF'
# Friday repository facts

The sole active product and delivery authority is .ralph/FRIDAY_DELIVERY_PLAN.md.
Owner-authored intent is in .ralph/VISION.md and .ralph/OWNER_WORDS.md.
The canonical product line is main; do not create competing branches or roadmaps.

Android module: mobile/app
Application ID: ai.friday.assistant
Namespace and Kotlin base package: ai.friday.assistant

From repository root:
- ./gradlew :mobile:app:assembleDebug
- ./gradlew :mobile:app:testDebugUnitTest

A unit-test pass is not APK assembly, emulator instrumentation, physical-device validation, or release acceptance. Live Ollama tests are opt-in diagnostics and do not establish the shipped offline path. No localhost service is a production prerequisite unless explicitly documented, configured, and verified as part of an optional capability.

Re-check the current main source and production composition before making capability claims. Historical audits, vault documents and test doubles are evidence only, not current product truth.
EOF

cat > AGENTS.md <<'EOF'
# Friday repository instructions

## Authority
- .ralph/FRIDAY_DELIVERY_PLAN.md is the sole active delivery plan.
- .ralph/VISION.md and .ralph/OWNER_WORDS.md preserve owner intent.
- Friday is the product identity. The Android application ID and Kotlin namespace are ai.friday.assistant.
- Historical audits and archive material are not active implementation instructions.

## Git and project structure
- Make authorized changes directly on main. Do not create feature branches, proposal branches or competing product lines.
- The production host is the Android application in mobile/app.
- Keep development/build/compiler/repository credentials outside the installed APK.
- Preserve useful algorithms and validated behavior; retire disconnected prototypes, stale destinations and false capability claims.
- Keep all source paths, package declarations, manifest component names, Gradle identifiers, tests and release metadata aligned with ai.friday.assistant.

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
EOF

# Remove the obsolete branch-era audit; the current delivery plan and current audits replace it.
git rm -f .ralph/REPO_UNIFICATION_AUDIT.md 2>/dev/null || true

# Give the installed product a consistent user-facing identity.
python3 - <<'PY'
from pathlib import Path
p = Path("mobile/app/src/main/AndroidManifest.xml")
s = p.read_text().replace('android:label="FRIDAY"', 'android:label="Friday"')
p.write_text(s)
PY

# Remove the disconnected Python-builder check from CI; keep the Android build and real test gates.
python3 - <<'PY'
from pathlib import Path
p = Path(".github/workflows/build.yml")
s = p.read_text()
s = s.replace("      - name: Verify Friday receives the owner's vision and direct words\n", "      - name: Verify Friday delivery authority and owner vision\n")
start = s.find("      - name: Set up Python for Friday builder contract tests\n")
end = s.find("      - name: Set up Go for workflow lint\n")
if start >= 0 and end > start:
    s = s[:start] + s[end:]
# Restore the normal mainline push trigger after this one-time migration completes.
s = s.replace("on:\n  workflow_dispatch:\n", "on:\n  push:\n    branches: [ main ]\n  workflow_dispatch:\n")
s = s.replace("label='FRIDAY'", "label='Friday'")
p.write_text(s)
PY

# Update the sole plan with the completed identity decision.
python3 - <<'PY'
from pathlib import Path
p = Path(".ralph/FRIDAY_DELIVERY_PLAN.md")
s = p.read_text()
heading = "# Friday"
if heading in s and "Fresh Friday identity baseline" not in s:
    s = s.replace(heading, heading + """

## Fresh Friday identity baseline (2026-11-21)

The owner confirmed the previous Friday APK had already been deleted and explicitly authorized a clean application identity. The canonical Android application ID, Gradle namespace and Kotlin base package are now `ai.friday.assistant`. All source/test package trees, manifest component references, build filters, signing metadata, release metadata, repository instructions and active product references must use this identity. The prior `com.jarvis.app` package is not a compatibility target. Keep historical donor research only as clearly non-authoritative evidence; never let it define live app destinations, endpoints or product truth.

The first clean Friday build must compile and pass unit tests, produce a correctly identified APK, pass emulator instrumentation and then undergo separate physical-phone acceptance. A clean rename is not proof that the inherited architecture is correct: the next engineering pass must audit and reconnect the real production composition, offline inference/memory, voice/action routes, model and embedding destinations, permissions and update path before the permanent seed is approved.
""", 1)
p.write_text(s)
PY

# Remove this one-time migration machinery from the final repository snapshot.
git rm -f scripts/perform_friday_identity_migration.sh .github/workflows/friday-identity-migration.yml .friday-identity-migration.trigger 2>/dev/null || true

echo "FRIDAY_IDENTITY_MIGRATION_PREPARED"
