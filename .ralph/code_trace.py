#!/usr/bin/env python3
"""Repository-wide source index and failure-to-symbol tracer.

Python declarations use the AST. Other languages use a conservative lexical
index. Call edges are explicitly approximate: overloads, DI, reflection,
callbacks, generated code and dynamic dispatch need compiler/runtime evidence.
This tool produces evidence and never edits source.
"""
import argparse
import ast
import json
import re
import subprocess
from collections import defaultdict, deque
from pathlib import Path

EXTS = {".kt",".kts",".java",".py",".go",".js",".jsx",".ts",".tsx",".swift",".rs",".c",".cc",".cpp",".h",".hpp",".gradle",".xml",".yml",".yaml",".toml",".properties",".json",".sh"}
SKIP = {".git",".gradle","build","out","generated","node_modules",".idea"}
DECL = re.compile(r"^\s*(?:(?:public|private|protected|internal|static|final|open|override|suspend|inline|tailrec|operator|infix|external|async|export|default|abstract|data|sealed|enum|annotation)\s+)*(?:fun\s+(?:<[^>]+>\s*)?(?:[\w?.<>]+\.)?([A-Za-z_]\w*)\s*(?:<[^>]+>\s*)?\(|(?:function\s+|def\s+|func\s+|fn\s+)([A-Za-z_]\w*)\s*\(|(?:class|interface|object|struct|enum|trait)\s+([A-Za-z_]\w*))")
CALL = re.compile(r"\b([A-Za-z_]\w*)\s*\(")
STACK = re.compile(r"\bat\s+[\w.$<>]+\(([^():]+?\.(?:kt|kts|java|py|js|ts|go)):(\d+)\)|(?:^|[\s(])((?:[\w./-]+/)?[^:\s]+?\.(?:kt|kts|java|py|js|ts|go)):(\d+)(?::\d+)?\b", re.M)
KEYWORDS = {"if","when","for","while","catch","switch","return","throw","super","this","typeof","synchronized","require","assert"}

def py_symbols(rel, source):
    try: tree = ast.parse(source, filename=rel)
    except SyntaxError: return []
    out = []
    def visit(node, scope=""):
        for child in getattr(node, "body", []):
            if isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
                name = scope + "." + child.name if scope else child.name
                out.append({"id":f"{rel}:{child.lineno}:{name}","path":rel,"name":name,
                    "kind":"class" if isinstance(child,ast.ClassDef) else "function",
                    "start":child.lineno,"end":getattr(child,"end_lineno",child.lineno),"language":"python"})
                if isinstance(child,ast.ClassDef): visit(child,name)
    visit(tree)
    return out

def lexical_symbols(rel, source):
    lines = source.splitlines()
    out = []
    for i,line in enumerate(lines):
        m = DECL.match(line)
        if not m: continue
        name = next((x for x in m.groups() if x), None)
        if not name: continue
        kind = "type" if re.search(r"\b(class|interface|object|struct|enum|trait)\s+",line) else "function"
        depth = 0; opened = False; end = i + 1
        for j in range(i,len(lines)):
            text = re.sub(r'("(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\')',"",lines[j])
            text = re.sub(r"//.*$|#.*$","",text)
            for ch in text:
                if ch == "{": depth += 1; opened = True
                elif ch == "}":
                    depth -= 1
                    if opened and depth <= 0: end = j + 1; break
            if opened and depth <= 0: break
            end = j + 1
            if not opened and j > i and j-i >= 12: break
        out.append({"id":f"{rel}:{i+1}:{name}","path":rel,"name":name,"kind":kind,
                    "start":i+1,"end":end,"language":Path(rel).suffix.lower().lstrip(".")})
    return out

def resolve_stack_location(raw, line, stack_line, by_path):
    """Resolve repository-owned frames and ignore platform/runtime frames."""
    normalized = raw.replace("\\", "/")
    candidates = [normalized]
    if "/" not in normalized:
        candidates += [
            "mobile/app/src/main/java/" + normalized,
            "mobile/app/src/test/java/" + normalized,
            "mobile/app/src/androidTest/java/" + normalized,
        ]
    path = next((candidate for candidate in candidates if candidate in by_path), None)
    if not path:
        matches = [candidate for candidate in by_path if candidate.endswith("/" + normalized.split("/")[-1])]
        path = matches[0] if len(matches) == 1 else None
    if not path:
        # Keep unresolved Friday frames, but omit unrelated JDK/Android frames.
        if re.search(r"\\bat\\s+ai\\.friday\\.", stack_line):
            return {"path": normalized, "line": line, "resolution": "friday_source_file_not_found"}
        return None
    return {"path": path, "line": line, "resolution": "source_resolved"}


def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--repo",default=".")
    ap.add_argument("--out-dir",default="build/friday-code-trace")
    ap.add_argument("--failure",action="append",default=[])
    ap.add_argument("--location",action="append",default=[],help="Trace a source location as path:line; repeatable")
    ap.add_argument("--symbol",action="append",default=[],help="Trace a function by exact or unqualified name; repeatable")
    ap.add_argument("--depth",type=int,default=3)
    a=ap.parse_args(); root=Path(a.repo).resolve(); out=Path(a.out_dir).resolve(); out.mkdir(parents=True,exist_ok=True)
    try:
        names=subprocess.check_output(["git","-C",str(root),"ls-files","-z"],text=True).split("\0")
        paths=[root/n for n in names if n]
    except (OSError,subprocess.CalledProcessError):
        paths=list(root.rglob("*"))
    contents={}; symbols=[]; syntax=[]
    for p in sorted(paths):
        if not p.is_file() or p.suffix.lower() not in EXTS or any(x in SKIP for x in p.relative_to(root).parts): continue
        rel=p.relative_to(root).as_posix()
        try: src=p.read_text(encoding="utf-8",errors="replace")
        except OSError as e: syntax.append({"path":rel,"error":type(e).__name__}); continue
        contents[rel]=src
        if p.suffix.lower()==".py":
            try: ast.parse(src,filename=rel)
            except SyntaxError as e: syntax.append({"path":rel,"line":e.lineno,"error":e.msg})
            symbols += py_symbols(rel,src)
        else: symbols += lexical_symbols(rel,src)
    by_name=defaultdict(list); by_path=defaultdict(list)
    for s in symbols: by_name[s["name"].split(".")[-1]].append(s); by_path[s["path"]].append(s)
    for v in by_path.values(): v.sort(key=lambda x:(x["start"],x["end"]))
    forward=defaultdict(set); reverse=defaultdict(set); edges=[]
    for s in symbols:
        if s["kind"] in {"type", "class"}:
            continue
        body="\n".join(contents[s["path"]].splitlines()[s["start"]-1:s["end"]])
        for name in sorted(set(CALL.findall(body))-KEYWORDS-{s["name"].split(".")[-1]}):
            candidates=by_name.get(name,[])
            if len(candidates)==1:
                target=candidates[0]; forward[s["id"]].add(target["id"]); reverse[target["id"]].add(s["id"])
                edges.append({"from":s["id"],"to":target["id"],"call":name,"resolution":"unique_lexical"})
            elif candidates:
                edges.append({"from":s["id"],"call":name,"candidates":[x["id"] for x in candidates[:30]],"resolution":"ambiguous_lexical"})
    index={s["id"]:s for s in symbols}
    def walk(graph,start):
        seen={start}; q=deque([(start,0)])
        while q:
            node,d=q.popleft()
            if d>=a.depth: continue
            for nxt in graph.get(node,()):
                if nxt not in seen: seen.add(nxt); q.append((nxt,d+1))
        seen.discard(start)
        return [index[x] for x in sorted(seen) if x in index]
    failures=[]
    for fp in a.failure:
        p=Path(fp); p=p if p.is_absolute() else root/p
        try: log=p.read_text(encoding="utf-8",errors="replace")
        except OSError as e: failures.append({"input":fp,"error":type(e).__name__}); continue
        for m in STACK.finditer(log):
            raw=m.group(1) or m.group(3); line=int(m.group(2) or m.group(4))
            resolved = resolve_stack_location(raw, line, m.group(0), by_path)
            if resolved is None:
                continue
            path = resolved["path"]
            if resolved["resolution"] == "friday_source_file_not_found":
                failures.append({"input": fp, **resolved})
                continue
            found=[s for s in by_path[path] if s["start"]<=line<=s["end"]]
            found.sort(key=lambda s:(s["end"]-s["start"],-s["start"]))
            s=found[0] if found else None
            item={"input":fp,"path":path,"line":line,"resolution":"symbol_found" if s else "line_outside_indexed_symbol","symbol":s}
            if s: item["callers"]=walk(reverse,s["id"]); item["callees"]=walk(forward,s["id"])
            failures.append(item)
    def trace_at(path, line, origin):
        normalized=path
        if normalized not in by_path:
            matches=[x for x in by_path if x.endswith("/"+normalized.split("/")[-1])]
            normalized=matches[0] if len(matches)==1 else normalized
        found=[s for s in by_path.get(normalized,[]) if s["start"]<=line<=s["end"]]
        found.sort(key=lambda s:(s["end"]-s["start"],-s["start"]))
        symbol=found[0] if found else None
        item={"input":origin,"path":normalized,"line":line,
              "resolution":"symbol_found" if symbol else "line_outside_indexed_symbol","symbol":symbol}
        if symbol:
            item["callers"]=walk(reverse,symbol["id"]); item["callees"]=walk(forward,symbol["id"])
        return item
    for location in a.location:
        try: loc_path, loc_line=location.rsplit(":",1); failures.append(trace_at(loc_path,int(loc_line),"--location"))
        except (ValueError,TypeError): failures.append({"input":location,"resolution":"invalid_location_expected_path_colon_line"})
    for query in a.symbol:
        matches=[s for s in symbols if s["name"]==query or s["name"].endswith("."+query) or s["id"]==query]
        if not matches:
            failures.append({"input":query,"resolution":"symbol_not_found"})
        else:
            for s in matches:
                item={"input":"--symbol","path":s["path"],"line":s["start"],"resolution":"symbol_found","symbol":s,
                      "callers":walk(reverse,s["id"]),"callees":walk(forward,s["id"])}
                failures.append(item)
    data={"schema":1,"source_files":len(contents),"source_lines":sum(len(x.splitlines()) for x in contents.values()),
        "symbols":symbols,"call_edges":edges,"failures":failures,"syntax_errors":syntax,
        "limits":["lexical call graph, not compiler-resolved","overloads and dynamic dispatch","reflection and dependency injection",
                  "generated code and callbacks","native/JNI edges","runtime reachability"]}
    (out/"trace-index.json").write_text(json.dumps(data,indent=2)+"\n",encoding="utf-8")
    lines=["# Friday whole-app code trace","",f"- Source files: {len(contents)}",f"- Source lines: {data['source_lines']}",
        f"- Symbols: {len(symbols)}",f"- Unique lexical call edges: {sum(x['resolution']=='unique_lexical' for x in edges)}",
        f"- Ambiguous call sites: {sum(x['resolution']=='ambiguous_lexical' for x in edges)}",f"- Failure locations: {len(failures)}",
        f"- Python syntax errors: {len(syntax)}","","## Failure traces",""]
    if not failures: lines.append("No failure log supplied. Full index is in trace-index.json.")
    for f in failures:
        lines += [f"### {f.get('path',f.get('input'))}:{f.get('line','?')}",f"- Resolution: {f['resolution']}"]
        if f.get("symbol"):
            s=f["symbol"]; lines += [f"- Symbol: {s['id']} lines {s['start']}-{s['end']}",
                "- Callers: "+(", ".join(x["id"] for x in f.get("callers",[])) or "none resolved"),
                "- Callees: "+(", ".join(x["id"] for x in f.get("callees",[])) or "none resolved")]
        lines.append("")
    lines += ["## Guardrails","","- Trace output is a navigation aid, not proof of root cause.",
        "- Ambiguous calls are preserved as candidate sets, never guessed.",
        "- This tool never edits source. A repair requires a reproduced failure, a targeted fix and passing regression evidence."]
    (out/"trace-summary.md").write_text("\n".join(lines)+"\n",encoding="utf-8")
    print("\n".join(lines[:10]))
    return 0

if __name__=="__main__": raise SystemExit(main())
