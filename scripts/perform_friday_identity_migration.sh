#!/usr/bin/env python3
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, check=True, text=True)

branch = subprocess.check_output(["git", "branch", "--show-current"], cwd=ROOT, text=True).strip()
remote = subprocess.check_output(["git", "remote", "get-url", "origin"], cwd=ROOT, text=True).strip()
if branch != "main" or "mhw77655-hue/Friday" not in remote:
    raise SystemExit("Refusing migration: expected canonical main checkout.")
gradle = ROOT / "mobile/app/build.gradle.kts"
gradle_text = gradle.read_text()
if 'applicationId = "com.jarvis.app"' not in gradle_text or 'namespace = "com.jarvis.app"' not in gradle_text:
    raise SystemExit("Unexpected existing application identity; refusing blind migration.")

# Move production, JVM-test and instrumentation-test package roots as Git renames.
for relative in (
    "mobile/app/src/main/java/com/jarvis/app",
    "mobile/app/src/test/java/com/jarvis/app",
    "mobile/app/src/androidTest/java/com/jarvis/app",
):
    source = ROOT / relative
    if source.exists():
        target = ROOT / relative.replace("com/jarvis/app", "ai/friday/assistant")
        if target.exists():
            raise SystemExit(f"Package migration collision: {target}")
        target.parent.mkdir(parents=True, exist_ok=True)
        git("mv", relative, str(target.relative_to(ROOT)))

# Rename Jarvis-named tracked files, without rewriting external URLs.
tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
for raw in tracked:
    if not raw:
        continue
    path = ROOT / raw
    if not path.exists():
        continue
    renamed = re.sub("jarvis", "Friday", path.name, flags=re.IGNORECASE)
    if renamed != path.name:
        target = path.with_name(renamed)
        if target.exists():
            raise SystemExit(f"Filename collision: {path} -> {target}")
        git("mv", raw, str(target.relative_to(ROOT)))

url_pattern = re.compile(r"https?://[^\s\"'<>]+")
replacements = (
    ("com.jarvis.app", "ai.friday.assistant"),
    ("com/jarvis/app", "ai/friday/assistant"),
    ("JARVIS", "FRIDAY"),
    ("Jarvis", "Friday"),
    ("jarvis", "friday"),
)
def normalize(text):
    for old, new in replacements:
        text = text.replace(old, new)
    return text

tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
changed = 0
for raw in tracked:
    if not raw:
        continue
    path = ROOT / raw
    if not path.is_file():
        continue
    try:
        original = path.read_bytes().decode("utf-8")
    except UnicodeDecodeError:
        continue
    chunks = url_pattern.split(original)
    updated = "".join(chunk if url_pattern.fullmatch(chunk) else normalize(chunk) for chunk in chunks)
    if updated != original:
        updated = "\n".join(line.rstrip(" \t") for line in updated.splitlines())
        path.write_text(updated + ("\n" if original.endswith("\n") else ""), encoding="utf-8")
        changed += 1

# Canonical seed identity. The old installed APK was explicitly deleted by the owner.
gradle = ROOT / "mobile/app/build.gradle.kts"
s = gradle.read_text().replace('namespace = "com.jarvis.app"', 'namespace = "ai.friday.assistant"')
s = s.replace('applicationId = "com.jarvis.app"', 'applicationId = "ai.friday.assistant"')
s = s.replace('"0.2-phase2"', '"1.0.0-seed"')
gradle.write_text(s)
manifest = ROOT / "mobile/app/src/main/AndroidManifest.xml"
manifest_text = manifest.read_text().replace('android:label="FRIDAY"', 'android:label="Friday"')
manifest_text = manifest_text.replace('    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />\n', '')
manifest.write_text(manifest_text)

facts = """# Friday repository facts

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
"""
(ROOT / "REPO_FACTS.md").write_text(facts)
(ROOT / ".ralph/REPO_FACTS.md").write_text(facts)
(ROOT / "AGENTS.md").write_text("""# Friday repository instructions

## Authority
- .ralph/FRIDAY_DELIVERY_PLAN.md is the sole active delivery plan.
- .ralph/VISION.md and .ralph/OWNER_WORDS.md preserve owner intent.
- Product identity: Friday; application ID and Kotlin namespace: ai.friday.assistant.
- the sole active product and delivery authority is .ralph/FRIDAY_DELIVERY_PLAN.md.
- The canonical product line is `main`.
- Historical audits and archive material are not active implementation instructions.

## Git and project structure
- Make authorized changes directly on main. Do not create feature branches or competing product lines.
- The production host is the Android application in mobile/app.
- Keep development, build, compiler and repository credentials outside the installed APK.
- Preserve useful algorithms and validated behavior; retire disconnected prototypes, stale destinations and false capability claims.
- Keep source paths, package declarations, manifest components, Gradle identifiers, tests and release metadata aligned with ai.friday.assistant.

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
- Protect owner authorization, permissions, signing trust, provenance, isolation, recovery and rollback.
""")

plan = ROOT / ".ralph/FRIDAY_DELIVERY_PLAN.md"
s = plan.read_text()
s = s.replace(
    "**Package/application ID is a protected migration decision, not a text replacement.** The existing Android `applicationId` is `com.jarvis.app`, and a historical device report records that package on the owner's phone. Changing it creates a different Android package and can break in-place update continuity and access to existing app data. Do not change it until the owner explicitly approves a migration strategy and the team proves the current installed state, signing identity, data transfer/continuity, and rollback. Until that gate is met, remove Jarvis from visible branding and safe internal class names while retaining the installed package ID as a compatibility identifier. If the owner chooses a new ID before the permanent seed, treat that as a deliberate one-time identity migration—not an ordinary cleanup.",
    "**Package/application ID migration — owner decision resolved (2026-11-21).** The owner explicitly authorized a fresh application identity and confirmed the old APK was already deleted. The canonical seed ID is `ai.friday.assistant`; no compatibility with `com.jarvis.app` is required. This is a one-time pre-seed migration, not an in-place update. The first install remains gated on production signing, secure update metadata, same-key upgrade continuity for subsequent releases, and all Objective A/G acceptance evidence."
)
if "Fresh Friday identity baseline" not in s:
    marker = "# FRIDAY"
    if marker not in s:
        raise SystemExit("Canonical delivery-plan title not found.")
    section = """

## Fresh Friday identity baseline (2026-11-21)

The owner explicitly authorized a clean application identity and confirmed the previous APK had already been deleted. The canonical Android application ID, Gradle namespace and Kotlin base package are ai.friday.assistant. All source/test package trees, manifest component references, build filters, signing metadata, release metadata, repository instructions and active product references must use this identity. The previous application ID is not a compatibility target. Historical donor research remains non-authoritative evidence and must never define live app destinations, endpoints or product truth.

A clean rename is not proof that inherited architecture is correct. After migration, audit and reconnect the actual production composition, offline inference and memory, voice/action routes, model and embedding destinations, permissions and update path before the permanent seed is approved.
"""
    s = s.replace(marker, marker + section, 1)
plan.write_text(s)

archive_review = ROOT / "docs/FRIDAY_SOURCE_ARCHIVE_REVIEW.md"
if archive_review.exists():
    review = archive_review.read_text()
    review = review.replace(
        "**Status:** extracted and inspected on `audit/friday-device-report-repair`; this is a review record, not a replacement for Venon's vision.",
        "**Status:** historical source review; branch names and implementation findings are archival evidence only, not active repository instructions."
    )
    archive_review.write_text(review)

# Purge retired, disconnected prototypes and stale personal device exports.
def remove_path(relative):
    target = ROOT / relative
    if not target.exists():
        return
    if target.is_dir():
        git("rm", "-r", "-f", relative)
    else:
        git("rm", "-f", relative)

for relative in (
    "vault",
    "runtime",
    "voiceforge",
    "release",
    "audit",
    "development-requests",
    ".ralph/audit",
    ".ralph/archive",
    ".ralph/buildtwin",
):
    remove_path(relative)

for relative in (
    "20261120-004345-device.log",
    "friday-device-report.json",
    "Design_Friday_Resilience.txt",
    ".ralph/FULL_SYSTEM_AUDIT_20260902.md",
    ".ralph/SYMBOL_FACTS.md",
    ".ralph/repo_scan_20260831_115031.md",
    ".ralph/audit_check.sh",
    ".ralph/ci_verify.sh",
    ".ralph/symbol_facts_check.sh",
    "docs/FRIDAY_BUILD_PROGRESS.md",
    "docs/FRIDAY_ARCHITECTURE_TRUTH_AUDIT.md",
    "docs/FRIDAY_TRUSTED_PUBLISHER_SETUP.md",
    "mobile/app/src/main/assets/voiceforge/PROVENANCE.md",
    "mobile/app/src/main/java/ai/friday/assistant/ObsidianSync.kt",
    "tools/friday-builder/run_proposal.sh",
    "tools/friday-builder/development-request.schema.json",
    "tools/friday-builder/validate_patch_paths.py",
    "tools/friday-builder/validate_request.py",
    "tools/friday-builder/test_publisher_workflow.py",
    "tools/friday-builder/test_run_proposal.py",
    "tools/friday-builder/test_validate_patch_paths.py",
    "tools/friday-builder/test_validate_request.py",
):
    remove_path(relative)

# VoiceForge was a retired test-only Chatterbox/Termux experiment, not Friday's
# production voice. Remove its tests rather than letting them imply otherwise.
voice_tests = ROOT / "mobile/app/src/test/java/ai/friday/assistant/voice"
if voice_tests.exists():
    for candidate in voice_tests.glob("VoiceForge*.kt"):
        remove_path(str(candidate.relative_to(ROOT)))

assets_voice = ROOT / "mobile/app/src/main/assets/voiceforge"
if assets_voice.exists() and not any(assets_voice.iterdir()):
    assets_voice.rmdir()

# Retire the old one-off repository-unification audit; the delivery plan is authoritative.
obsolete = ROOT / ".ralph/REPO_UNIFICATION_AUDIT.md"
if obsolete.exists():
    git("rm", "-f", str(obsolete.relative_to(ROOT)))

# Remove the temporary one-shot migration machinery from the resulting commit.
for relative in (
    "scripts/perform_friday_identity_migration.sh",
    ".github/workflows/friday-identity-migration.yml",
    ".friday-identity-migration.trigger",
):
    path = ROOT / relative
    if path.exists():
        git("rm", "-f", relative)

git("diff", "--check")
print(f"Friday identity normalized in {changed} text files; external URLs preserved.")
print("FRIDAY_IDENTITY_MIGRATION_PREPARED")
