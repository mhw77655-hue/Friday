#!/usr/bin/env python3
"""One-time, idempotent migration of the Android product identity from Jarvis to Friday.

The applicationId remains com.jarvis.app deliberately so an existing installation
keeps its app-private files, databases, permissions and update identity. Kotlin/
JNI namespace and all first-party product code move to com.friday.app.
"""
from __future__ import annotations

import os
import re
import subprocess
from pathlib import Path

ROOT = Path.cwd()
APP = ROOT / "mobile" / "app"
EXTENSIONS = {".kt", ".java", ".xml", ".kts", ".cpp", ".h", ".c", ".pro", ".properties"}
CHANGED = []

def replace_file(path: Path, transform):
    try:
        original = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return
    updated = transform(original, path)
    if updated != original:
        path.write_text(updated, encoding="utf-8")
        CHANGED.append(str(path.relative_to(ROOT)))

def rename_package_tree(source_set: str):
    parent = APP / "src" / source_set / "java" / "com"
    old = parent / "jarvis" / "app"
    new = parent / "friday" / "app"
    if old.exists():
        new.parent.mkdir(parents=True, exist_ok=True)
        if new.exists():
            raise RuntimeError(f"Both old and new package trees exist: {old}, {new}")
        old.rename(new)
        try:
            (parent / "jarvis").rmdir()
        except OSError:
            pass

for source_set in ("main", "test", "androidTest"):
    rename_package_tree(source_set)

for path in sorted(APP.rglob("*")):
    if path.is_file() and path.suffix in {".kt", ".java"} and "Jarvis" in path.name:
        target = path.with_name(path.name.replace("Jarvis", "Friday"))
        if target.exists():
            raise RuntimeError(f"Refusing to overwrite during rename: {target}")
        path.rename(target)
        CHANGED.append(f"{target.relative_to(ROOT)} (renamed)")

def transform(source: str, path: Path) -> str:
    s = source
    s = s.replace("com.jarvis.app", "com.friday.app")
    s = s.replace("com/jarvis/app", "com/friday/app")
    s = s.replace("Java_com_jarvis_app", "Java_com_friday_app")
    s = s.replace("Jarvis", "Friday")
    s = s.replace("JARVIS", "FRIDAY")
    s = s.replace("fromJarvis", "fromFriday").replace("jarvisText", "fridayText")
    s = s.replace("jarvis-engine", "friday-engine")
    s = s.replace("jarvis-body-coordinator", "friday-body-coordinator")
    s = s.replace("jarvis_native", "friday_native")
    s = s.replace("jarvis_llama_jni", "friday_llama_jni")
    s = s.replace("jarvis_silero_vad", "friday_silero_vad")

    if path.name == "build.gradle.kts":
        s = re.sub(r'namespace\s*=\s*"[^"]+"', 'namespace = "com.friday.app"', s)
        s = re.sub(r'applicationId\s*=\s*"[^"]+"', 'applicationId = "com.jarvis.app"', s)
        marker = 'applicationId = "com.jarvis.app"'
        if marker in s and "Compatibility install ID" not in s:
            s = s.replace(marker,
                marker + '\n        // Compatibility install ID: preserve the existing Friday user-data directory.'
            )

    if path.name == "AndroidManifest.xml":
        s = s.replace('android:label="FRIDAY"', 'android:label="Friday"')
        s = s.replace('android:label="FRIDAY Notification Access"',
                      'android:label="Friday Notification Access"')

    if path.name == "FallbackIdentity.kt":
        s = re.sub(
            r'val selfDescription: String\s*=\s*.*?(?=\n\s*/\*\*)',
            'val selfDescription: String = "Friday is a developing individual."\n\n',
            s,
            flags=re.S
        )
        s = s.replace('val name: String = "FRIDAY"', 'val name: String = "Friday"')

    if path.name == "LocalInferenceEngine.kt":
        s = s.replace(
            'You are FRIDAY, a helpful AI assistant. Be concise and natural.',
            'You are Friday. Use the context and capabilities actually supplied to this turn. '
            'Be honest about uncertainty, memory and capability limits; do not claim experiences '
            'or actions that have not been recorded.'
        )

    if path.name == "FridayEngine.kt":
        start = s.find('            com.friday.app.latency.LatencyLayer.setStreamingSpeak { text ->\n                val backend = com.friday.app.JarvisEngine.voiceForgeBackend')
        if start < 0:
            start = s.find('            com.friday.app.latency.LatencyLayer.setStreamingSpeak { text ->\n                val backend = FridayEngine.voiceForgeBackend')
        if start >= 0:
            end_marker = '            }\n\n            // Latency-first layer:'
            end = s.find(end_marker, start)
            if end < 0:
                raise RuntimeError("Could not locate end of legacy VoiceForge reply route")
            replacement = (
                '            com.friday.app.latency.LatencyLayer.setStreamingSpeak { text ->\n'
                '                // Default speech stays on the host device; no Termux/localhost service is required.\n'
                '                legacyReplySpeech(text)\n'
                '            }\n\n'
                '            // Latency-first layer:'
            )
            s = s[:start] + replacement + s[end + len(end_marker):]

        start = s.find('        // LOCAL-MODEL-BACKEND-GROUND-TRUTH-AND-BUILD:')
        end = s.find('        // Resident inference runs inside this Android app process.', start)
        if start >= 0 and end >= 0:
            s = s[:start] + (
                '        // Local GGUF inference is the production model path. Ollama is optional\n'
                '        // and is not required for Friday to exist or answer offline.\n'
            ) + s[end:]
        s = s.replace('        FridayEngine.ollamaModelBackend = ollamaModelBackend\n', '')
        s = s.replace('        JarvisEngine.ollamaModelBackend = ollamaModelBackend\n', '')

    return s

for path in APP.rglob("*"):
    if path.is_file() and path.suffix in EXTENSIONS:
        replace_file(path, transform)

gradle = APP / "build.gradle.kts"
if gradle.exists():
    g = gradle.read_text(encoding="utf-8")
    g = re.sub(r'namespace\s*=\s*"[^"]+"', 'namespace = "com.friday.app"', g)
    g = re.sub(r'applicationId\s*=\s*"[^"]+"', 'applicationId = "com.jarvis.app"', g)
    gradle.write_text(g, encoding="utf-8")

test_dir = APP / "src" / "test" / "java" / "com" / "friday" / "app" / "humancore" / "fallback"
test_dir.mkdir(parents=True, exist_ok=True)
test_file = test_dir / "FridayIdentitySeedTest.kt"
test_source = """package com.friday.app.humancore.fallback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FridayIdentitySeedTest {
    @Test
    fun fallbackIdentityHasFridaySeedWithoutAuthoredCharacterPersona() {
        assertEquals("Friday", FallbackIdentity.name)
        assertEquals("Friday is a developing individual.", FallbackIdentity.selfDescription)
        assertFalse(FallbackIdentity.selfDescription.contains("Jarvis", ignoreCase = true))
        assertFalse(FallbackIdentity.selfDescription.contains("he ", ignoreCase = true))
    }
}
"""
if not test_file.exists() or test_file.read_text(encoding="utf-8") != test_source:
    test_file.write_text(test_source, encoding="utf-8")
    CHANGED.append(str(test_file.relative_to(ROOT)))

for path in (APP / "src" / "main" / "res").rglob("*.xml"):
    replace_file(path, lambda s, p: s.replace("JARVIS", "Friday").replace("Jarvis", "Friday"))

if CHANGED:
    subprocess.run(["git", "add", "-A"], check=True)
    subprocess.run(["git", "config", "user.name", "github-actions[bot]"], check=True)
    subprocess.run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], check=True)
    subprocess.run(["git", "commit", "-m", "refactor: make Friday the Android code identity"], check=True)
    branch = os.environ.get("GITHUB_REF_NAME", "")
    if not branch.startswith(("audit/", "work/")):
        raise RuntimeError(f"Refusing migration push outside a work branch: {branch}")
    subprocess.run(["git", "push", "origin", f"HEAD:refs/heads/{branch}"], check=True)
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    summary = f"Friday migration committed: {sha}\nChanged paths: {len(CHANGED)}"
    print(summary)
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as f:
        f.write("## Friday code identity migration\n\n" + summary + "\n")

else:
    print("Friday migration already applied; no new commit required.")
