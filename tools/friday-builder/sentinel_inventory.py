#!/usr/bin/env python3
"""Conservative static inventory for Friday Sentinel; never claims runtime proof."""
from __future__ import annotations
import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"
PATTERNS = {
    "shared_preferences": re.compile(r'getSharedPreferences\s*\(\s*"([^"]+)"'),
    "preferences_datastore": re.compile(r'preferencesDataStore\s*\([^)]*?name\s*=\s*"([^"]+)"', re.S),
    "room_database": re.compile(r'databaseBuilder\s*\([^,]+,\s*[^,]+,\s*"([^"]+)"', re.S),
    "sqlite_database": re.compile(r'(?:DATABASE_NAME|databaseName)\s*=\s*"([^"]+)"'),
    "work_worker": re.compile(r'class\s+(\w+)\s*:[^{\n]*(?:CoroutineWorker|Worker|ListenableWorker)'),
    "network_host": re.compile(r'https://([A-Za-z0-9.-]+)(?::\d+)?(?:/|"|\\')'),
}
DYNAMIC_PREFS = re.compile(r'getSharedPreferences\s*\(\s*([A-Za-z_][A-Za-z0-9_.]*)')

def build_inventory(root: Path) -> dict[str, Any]:
    main = root / "mobile/app/src/main"
    java = main / "java"
    manifest = main / "AndroidManifest.xml"
    if not java.is_dir() or not manifest.is_file():
        raise ValueError("Friday production source root or AndroidManifest.xml is missing")
    kotlin = sorted(java.rglob("*.kt"))
    if not kotlin:
        raise ValueError("No production Kotlin sources found; refusing an empty inventory")
    manifest_root = ET.parse(manifest).getroot()
    permissions = sorted({
        node.get(ANDROID + "name", "")
        for node in manifest_root.findall("uses-permission")
        if node.get(ANDROID + "name")
    })
    components = []
    app = manifest_root.find("application")
    if app is not None:
        for kind in ("activity", "activity-alias", "service", "receiver", "provider"):
            for node in app.findall(kind):
                components.append({
                    "kind": kind,
                    "name": node.get(ANDROID + "name", ""),
                    "exported": node.get(ANDROID + "exported"),
                    "permission": node.get(ANDROID + "permission"),
                    "process": node.get(ANDROID + "process"),
                    "source": "mobile/app/src/main/AndroidManifest.xml",
                    "evidence_level": "manifest-declared",
                    "runtime_verified": False,
                })
    kinds = ("shared_preferences", "preferences_datastore", "room_database",
             "sqlite_database", "work_worker", "network_host")
    resources = {kind: {} for kind in kinds}
    dynamic_preferences = []
    for path in kotlin:
        content = path.read_text(encoding="utf-8", errors="replace")
        source = path.relative_to(root).as_posix()
        for kind, pattern in PATTERNS.items():
            for match in pattern.finditer(content):
                value = match.group(1)
                resources[kind].setdefault(value, {
                    "name": value, "source": source,
                    "evidence_level": "source-declared", "runtime_verified": False,
                })
        for match in DYNAMIC_PREFS.finditer(content):
            dynamic_preferences.append({
                "expression": match.group(1), "source": source,
                "evidence_level": "dynamic-name-unresolved",
            })
    resources = {key: sorted(value.values(), key=lambda row: row["name"])
                 for key, value in resources.items()}
    return {
        "schema_version": 1,
        "inventory_kind": "static-discovery-not-runtime-proof",
        "source_root": "mobile/app/src/main",
        "counts": {
            "production_kotlin_files": len(kotlin),
            "manifest_permissions": len(permissions),
            "manifest_components": len(components),
            **{key: len(value) for key, value in resources.items()},
            "dynamic_preference_names": len(dynamic_preferences),
        },
        "permissions": permissions,
        "components": sorted(components, key=lambda row: (row["kind"], row["name"])),
        "resources": resources,
        "unresolved": {"dynamic_preference_names": dynamic_preferences},
        "limitations": [
            "Manifest declarations do not prove runtime registration or successful execution.",
            "Lexical storage discovery can miss wrappers, reflection, generated code, JNI, and dynamic names.",
            "A test reference is not evidence that a test ran or asserted a meaningful postcondition.",
            "Production reachability and device verification require separate runtime/test evidence.",
        ],
    }

def render_markdown(data: dict[str, Any]) -> str:
    lines = [
        "# Friday Sentinel capability and persistence inventory",
        "",
        "> Static discovery only. This report does not establish runtime reachability or health.",
        "", "## Counts", "",
    ]
    lines.extend(f"- {key}: **{value}**" for key, value in data["counts"].items())
    lines += ["", "## Manifest components", ""]
    for item in data["components"]:
        lines.append(
            f"- {item['kind']} {item['name']} — exported={item['exported']}, "
            f"permission={item['permission'] or 'none'}, process={item['process'] or 'default'}"
        )
    titles = {
        "shared_preferences": "SharedPreferences",
        "preferences_datastore": "Preferences DataStore",
        "room_database": "Room databases",
        "sqlite_database": "SQLite databases",
        "work_worker": "WorkManager workers",
        "network_host": "HTTPS hosts",
    }
    for kind, title in titles.items():
        lines += ["", f"## {title}", ""]
        lines.extend(f"- {item['name']} — {item['source']}" for item in data["resources"][kind])
    lines += ["", "## Unresolved dynamic preference names", ""]
    if data["unresolved"]["dynamic_preference_names"]:
        lines.extend(
            f"- {item['expression']} — {item['source']}"
            for item in data["unresolved"]["dynamic_preference_names"]
        )
    else:
        lines.append("- None detected by the current lexical detector.")
    lines += ["", "## Limitations", ""]
    lines.extend(f"- {item}" for item in data["limitations"])
    return "\n".join(lines) + "\n"

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--json", type=Path, required=True)
    parser.add_argument("--markdown", type=Path, required=True)
    args = parser.parse_args()
    data = build_inventory(args.root)
    args.json.parent.mkdir(parents=True, exist_ok=True)
    args.markdown.parent.mkdir(parents=True, exist_ok=True)
    args.json.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    args.markdown.write_text(render_markdown(data), encoding="utf-8")
    print(json.dumps(data["counts"], sort_keys=True))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
