import os
import json
import subprocess
from pathlib import Path
import urllib.request

REPO = Path("/sdcard/jarvis-repo")
MODEL_URL = "http://127.0.0.1:8080/v1/chat/completions"
MAX_CHARS = 7000

TARGETS = [
    REPO / "CLAUDE.md",
    REPO / "JARVIS_BUILDING_SYSTEM_MAP.md",
    REPO / "JARVIS_INVENTORY_REPORT.md",
    REPO / "JARVIS_STATUS_REPORT_2026-08-10.md",
    REPO / "core/building_integration.py",
]

DIRS = [
    "building", "cognitive", "evolution", "mutation", "genome",
    "nervous", "runtime", "model", "research", "validation",
    "failure", "resource", "organism"
]

def read_file(path: Path) -> str:
    try:
        return path.read_text(errors="ignore")
    except Exception as e:
        return f"[READ ERROR] {path}: {e}"

def collect():
    items = []

    for path in TARGETS:
        if path.exists():
            items.append((str(path.relative_to(REPO)), read_file(path)))

    for d in DIRS:
        root = REPO / d
        if not root.exists():
            continue

        for path in sorted(root.rglob("*")):
            if path.is_file() and path.suffix in {".py", ".kt", ".kts", ".md", ".json"}:
                items.append((str(path.relative_to(REPO)), read_file(path)))

    return items

def ask(text: str) -> str:
    payload = {
        "model": "jarvis-local",
        "messages": [
            {
                "role": "system",
                "content": (
                    "You are auditing an existing Jarvis repository. "
                    "Do not propose code changes. "
                    "Classify only what the supplied evidence supports."
                ),
            },
            {"role": "user", "content": text},
        ],
        "temperature": 0,
        "max_tokens": 500,
    }

    req = urllib.request.Request(
        MODEL_URL,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    with urllib.request.urlopen(req, timeout=120) as r:
        data = json.loads(r.read().decode())

    return data["choices"][0]["message"]["content"]

def main():
    files = collect()

    chunks = []
    current = []

    for name, content in files:
        block = f"\n===== {name} =====\n{content}\n"
        if sum(len(x) for x in current) + len(block) > MAX_CHARS:
            chunks.append("\n".join(current))
            current = []
        current.append(block)

    if current:
        chunks.append("\n".join(current))

    findings = []

    for i, chunk in enumerate(chunks, 1):
        prompt = f"""
AUDIT CHUNK {i}/{len(chunks)}

Classify the supplied evidence into:
IMPLEMENTED
PARTIALLY_IMPLEMENTED
SCAFFOLDED
BROKEN
MISSING

Focus on:
- cognitive spine
- memory
- capability/execution
- builder
- research
- failure/recovery
- resource management
- model management
- self-improvement

Do not infer implementation merely from filenames.

EVIDENCE:
{chunk}
"""
        print(f"Auditing chunk {i}/{len(chunks)}...")
        findings.append(ask(prompt))

    final_prompt = f"""
SYNTHESIZE THESE AUDIT RESULTS INTO ONE JARVIS CURRENT BUILD STATE.

Return:

1. ACTUALLY FINISHED
2. PARTIALLY FINISHED
3. BROKEN
4. MISSING
5. BUILDER HANDOVER BLOCKERS
6. FIRST CRITICAL IMPLEMENTATION TASK

Do not invent facts.

AUDIT RESULTS:

{"".join(f"\n--- RESULT {i} ---\n{v}\n" for i, v in enumerate(findings, 1))}
"""

    result = ask(final_prompt)

    out = REPO / "vault" / "JARVIS_CURRENT_BUILD_STATE.md"
    out.write_text("# JARVIS CURRENT BUILD STATE\n\n" + result)
    print(f"\nWrote: {out}")

if __name__ == "__main__":
    main()
