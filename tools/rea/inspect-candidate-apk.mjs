#!/usr/bin/env node
/**
 * Bounded REA investigation for one exact failed Friday Builder APK.
 * The full canonical REA records are retained in the workflow artifact;
 * this script never executes the APK or accepts caller-supplied tool names.
 */
import { createHash } from "node:crypto";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { resolve, basename } from "node:path";
import { spawn } from "node:child_process";

const version = process.env.REA_PACKAGE_VERSION;
const integrity = process.env.REA_PACKAGE_INTEGRITY ?? "";
const candidateSha = process.env.CANDIDATE_SHA ?? "";
const apkPath = resolve(process.env.CANDIDATE_APK ?? "");
const outputDir = resolve(process.env.REA_OUTPUT_DIR ?? "rea-candidate-evidence");
const timeoutMs = 10 * 60 * 1000;

function digest(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

class Mcp {
  constructor() {
    this.child = spawn("npx", ["--yes", `rea-agents@${version}`, "mcp"], {
      stdio: ["pipe", "pipe", "pipe"],
      env: process.env,
      windowsHide: true,
    });
    this.pending = new Map();
    this.nextId = 1;
    this.buffer = "";
    this.stderr = "";
    this.closed = false;
    this.child.stdout.setEncoding("utf8");
    this.child.stderr.setEncoding("utf8");
    this.child.stdout.on("data", (chunk) => this.onData(chunk));
    this.child.stderr.on("data", (chunk) => { this.stderr = (this.stderr + chunk).slice(-12000); });
    this.child.on("error", (error) => this.fail(error));
    this.child.on("exit", (code, signal) => {
      this.closed = true;
      this.fail(new Error(`REA MCP exited code=${code} signal=${signal}. ${this.stderr}`));
    });
  }

  onData(chunk) {
    this.buffer += chunk;
    while (true) {
      const index = this.buffer.indexOf("\n");
      if (index < 0) break;
      const line = this.buffer.slice(0, index).trim();
      this.buffer = this.buffer.slice(index + 1);
      if (!line) continue;
      let message;
      try { message = JSON.parse(line); }
      catch { this.fail(new Error(`REA emitted non-JSON stdout: ${line.slice(0, 300)}`)); continue; }
      const pending = this.pending.get(message.id);
      if (!pending) continue;
      this.pending.delete(message.id);
      if (message.error) pending.reject(new Error(JSON.stringify(message.error)));
      else pending.resolve(message.result);
    }
  }

  fail(error) {
    for (const pending of this.pending.values()) pending.reject(error);
    this.pending.clear();
  }

  notify(method, params = {}) {
    if (this.closed) throw new Error("REA MCP is not running.");
    this.child.stdin.write(JSON.stringify({ jsonrpc: "2.0", method, params }) + "\n");
  }

  request(method, params = {}) {
    if (this.closed) return Promise.reject(new Error("REA MCP is not running."));
    const id = this.nextId++;
    return new Promise((resolvePromise, rejectPromise) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        rejectPromise(new Error(`REA MCP timeout: ${method}`));
      }, timeoutMs);
      this.pending.set(id, {
        resolve: (value) => { clearTimeout(timer); resolvePromise(value); },
        reject: (error) => { clearTimeout(timer); rejectPromise(error); },
      });
      this.child.stdin.write(JSON.stringify({ jsonrpc: "2.0", id, method, params }) + "\n");
    });
  }

  async initialize() {
    const result = await this.request("initialize", {
      protocolVersion: "2025-03-26",
      capabilities: {},
      clientInfo: { name: "friday-candidate-repair", version: "1.0.0" },
    });
    if (!result?.protocolVersion || !result?.serverInfo?.name) {
      throw new Error("REA MCP initialize response is incomplete.");
    }
    this.notify("notifications/initialized");
    return result;
  }

  async tools() {
    const all = [];
    let cursor;
    do {
      const page = await this.request("tools/list", cursor ? { cursor } : {});
      if (!Array.isArray(page?.tools)) throw new Error("REA tools/list returned no tools array.");
      all.push(...page.tools);
      cursor = page.nextCursor;
    } while (cursor);
    return all;
  }

  call(name, args) {
    return this.request("tools/call", { name, arguments: args });
  }

  async close() {
    if (this.closed) return;
    try { this.notify("notifications/cancelled", { reason: "candidate investigation complete" }); } catch {}
    this.child.kill("SIGTERM");
    await new Promise((done) => {
      if (this.closed) return done();
      const timer = setTimeout(() => { this.child.kill("SIGKILL"); done(); }, 5000);
      this.child.once("exit", () => { clearTimeout(timer); done(); });
    });
  }
}

function collectEvidenceIds(value, found = new Set()) {
  if (typeof value === "string") {
    for (const match of value.matchAll(/ev_[0-9a-f]{64}/g)) found.add(match[0]);
    return [...found];
  }
  if (!value || typeof value !== "object") return [...found];
  for (const [key, child] of Object.entries(value)) {
    if ((key === "evidence_id" || key === "id") &&
        typeof child === "string" && /^ev_[0-9a-f]{64}$/.test(child)) found.add(child);
    if (child && typeof child === "object") collectEvidenceIds(child, found);
  }
  return [...found];
}

function extractFailureSymbols(logText) {
  const found = new Map();
  const frame = /^\s*at\s+((?:[A-Za-z_$][\w$]*\.)+[A-Z_$][\w$]*)\.([A-Za-z_$][\w$]*)\s*\(/gm;
  for (const match of logText.matchAll(frame)) {
    let className = match[1];
    const methodName = match[2];
    if (!className.startsWith("ai.friday.assistant.")) continue;
    className = className.split("$")[0];
    const key = `${className}#${methodName}`;
    if (!found.has(key)) found.set(key, { class_name: className, method_name: methodName, source: "gradle_stacktrace" });
  }
  return [...found.values()].slice(0, 8);
}

function compactInspection(record) {
  const structured = record.structured_content ?? {};
  const normalized = structured.normalized_result ?? {};
  return {
    class_name: record.class_name,
    method_name: record.method_name,
    evidence_ids: record.evidence_ids ?? [],
    error: record.error ?? null,
    representation: normalized.representation ?? null,
    body_status: normalized.body_status ?? null,
    source_excerpt: (normalized.source?.text ?? "").slice(0, 1800),
    result_excerpt: JSON.stringify(structured).slice(0, 1200),
  };
}

async function callInspection(server, toolName, args, identity) {
  try {
    const result = await server.call(toolName, args);
    return {
      ...identity,
      tool: toolName,
      evidence_ids: collectEvidenceIds(result),
      structured_content: result?.structuredContent ?? null,
      content: result?.content ?? [],
      error: result?.isError ? JSON.stringify(result.content ?? result).slice(0, 1200) : null,
    };
  } catch (error) {
    return { ...identity, tool: toolName, evidence_ids: [], structured_content: null, content: [], error: String(error?.message ?? error).slice(0, 1200) };
  }
}

async function main() {
  if (!version || !/^6\.10\.1$/.test(version)) throw new Error("REA_PACKAGE_VERSION must be the reviewed 6.10.1 pin.");
  if (!/^[0-9a-f]{40}$/.test(candidateSha)) throw new Error("CANDIDATE_SHA must be a full lowercase Git SHA.");
  if (!apkPath || !apkPath.toLowerCase().endsWith(".apk")) throw new Error("CANDIDATE_APK must identify an APK.");
  const file = await stat(apkPath);
  if (!file.isFile() || file.size <= 0) throw new Error("Candidate APK is missing or empty.");
  const bytes = await readFile(apkPath);
  const apkSha256 = digest(bytes);
  await mkdir(outputDir, { recursive: true });
  const server = new Mcp();
  try {
    const initialized = await server.initialize();
    const tools = await server.tools();
    const inspect = tools.find((tool) => tool.name === "inspect_android_package");
    const search = tools.find((tool) => tool.name === "search_android_classes");
    if (!inspect?.inputSchema?.properties?.path) {
      throw new Error("Pinned REA release does not advertise inspect_android_package(path).");
    }
    const packageResult = await server.call("inspect_android_package", { path: apkPath });
    if (packageResult?.isError || (!packageResult?.structuredContent && !packageResult?.content?.length)) {
      throw new Error("REA returned no usable Android package evidence.");
    }
    const searches = [];
    if (search?.inputSchema?.properties?.path && search?.inputSchema?.properties?.query) {
      for (const query of [
        "ObjectiveExecutionWorker",
        "ObjectiveBuilderCandidateExecutor",
        "RecoveryController",
        "FailureSurface",
        "ImmuneSystem",
        "Shizuku",
      ]) {
        const result = await server.call("search_android_classes", { path: apkPath, query });
        if (result?.isError) throw new Error(`REA class search failed for ${query}.`);
        searches.push({ query, structured_content: result?.structuredContent ?? null, content: result?.content ?? [] });
      }
    }
    const failureLogPath = process.env.CANDIDATE_FAILURE_LOG ?? "";
    const failureLog = failureLogPath
      ? await readFile(failureLogPath, "utf8").catch(() => "")
      : "";
    const failureSymbols = extractFailureSymbols(failureLog);
    const classInspector = tools.find((tool) => tool.name === "inspect_android_class");
    const methodInspector = tools.find((tool) => tool.name === "inspect_android_method");
    const referenceTracer = tools.find((tool) => tool.name === "trace_android_references");
    const supports = (tool, fields) => Boolean(
      tool?.inputSchema?.properties &&
      fields.every((field) => tool.inputSchema.properties[field])
    );

    const coreClasses = [
      "ai.friday.assistant.bootstrap.plan.ObjectiveExecutionWorker",
      "ai.friday.assistant.bootstrap.plan.ObjectiveBuilderCandidateExecutor",
      "ai.friday.assistant.bootstrap.plan.ObjectiveExecutionLedgerStore",
      "ai.friday.assistant.bootstrap.plan.CandidateRepairPlanner",
      "ai.friday.assistant.ScreenBridge",
      "ai.friday.assistant.ShizukuBridge",
      "ai.friday.assistant.FridayShellService",
      "ai.friday.assistant.runtime.FridayRuntimeService",
    ];
    const classTargets = [...new Set([...coreClasses, ...failureSymbols.map((item) => item.class_name)])].slice(0, 10);
    const methodTargets = [
      { class_name: "ai.friday.assistant.bootstrap.plan.ObjectiveExecutionWorker", method_name: "doWork", source: "core_repair_path" },
      { class_name: "ai.friday.assistant.bootstrap.plan.ObjectiveBuilderCandidateExecutor", method_name: "execute", source: "core_repair_path" },
      { class_name: "ai.friday.assistant.bootstrap.plan.ObjectiveExecutionLedgerStore", method_name: "recordCandidateSandboxResult", source: "core_repair_path" },
      { class_name: "ai.friday.assistant.bootstrap.plan.CandidateRepairPlanner", method_name: "createRepairTask", source: "core_repair_path" },
      { class_name: "ai.friday.assistant.ScreenBridge", method_name: "bind", source: "core_android_capability" },
      { class_name: "ai.friday.assistant.ScreenBridge", method_name: "ensureVerifiedBinding", source: "core_android_capability" },
      { class_name: "ai.friday.assistant.ShizukuBridge", method_name: "requestPermission", source: "core_android_capability" },
      { class_name: "ai.friday.assistant.FridayShellService", method_name: "identity", source: "core_android_capability" },
      { class_name: "ai.friday.assistant.runtime.FridayRuntimeService", method_name: "onStartCommand", source: "core_android_continuity" },
      ...failureSymbols,
    ].filter((item, index, all) =>
      all.findIndex((candidate) => candidate.class_name === item.class_name && candidate.method_name === item.method_name) === index
    ).slice(0, 12);

    const classInspections = [];
    if (supports(classInspector, ["path", "class_name"])) {
      for (const className of classTargets) {
        classInspections.push(await callInspection(
          server,
          "inspect_android_class",
          { path: apkPath, class_name: className, ...(classInspector.inputSchema.properties.detail ? { detail: "complete" } : {}) },
          { class_name: className }
        ));
      }
    }

    const methodInspections = [];
    if (supports(methodInspector, ["path", "class_name", "method_name"])) {
      for (const target of methodTargets) {
        methodInspections.push(await callInspection(
          server,
          "inspect_android_method",
          {
            path: apkPath,
            class_name: target.class_name,
            method_name: target.method_name,
            ...(methodInspector.inputSchema.properties.detail ? { detail: "complete" } : {}),
          },
          target
        ));
      }
    }

    const referenceTraces = [];
    if (supports(referenceTracer, ["path", "class_name"])) {
      for (const className of [
        "ai.friday.assistant.bootstrap.plan.ObjectiveExecutionWorker",
        "ai.friday.assistant.bootstrap.plan.CandidateRepairPlanner",
        "ai.friday.assistant.ScreenBridge",
      ]) {
        referenceTraces.push(await callInspection(
          server,
          "trace_android_references",
          { path: apkPath, class_name: className },
          { class_name: className }
        ));
      }
    }

    const record = {
      schema_version: 2,
      target: { name: "friday-candidate", basename: basename(apkPath), bytes: file.size, sha256: apkSha256 },
      source: { candidate_commit_sha: candidateSha },
      rea: { package: "rea-agents", version, integrity, server: initialized.serverInfo, protocol_version: initialized.protocolVersion },
      captured_at: new Date().toISOString(),
      package_evidence: packageResult.structuredContent ?? null,
      package_text: packageResult.content ?? [],
      failure_symbols: failureSymbols,
      inspection_availability: {
        class_inspection: supports(classInspector, ["path", "class_name"]),
        method_inspection: supports(methodInspector, ["path", "class_name", "method_name"]),
        reference_tracing: supports(referenceTracer, ["path", "class_name"]),
      },
      class_search_evidence: searches,
      class_inspections: classInspections,
      method_inspections: methodInspections,
      reference_traces: referenceTraces,
    };
    const output = resolve(outputDir, "candidate-rea-evidence.json");
    const reportText = JSON.stringify(record, null, 2) + "\n";
    await writeFile(output, reportText);
    const summary = {
      schema_version: 1,
      candidate_commit_sha: candidateSha,
      apk_sha256: apkSha256,
      rea_version: version,
      report_sha256: digest(Buffer.from(reportText)),
      evidence_ids: collectEvidenceIds(record),
      failure_symbols: failureSymbols,
      method_inspection_excerpt: JSON.stringify(methodInspections.map(compactInspection)).slice(0, 5000),
      class_inspection_excerpt: JSON.stringify(classInspections.map(compactInspection)).slice(0, 2500),
      reference_trace_excerpt: JSON.stringify(referenceTraces.map(compactInspection)).slice(0, 1500),
      package_evidence_excerpt: JSON.stringify(record.package_evidence ?? record.package_text).slice(0, 1500),
      class_search_evidence_excerpt: JSON.stringify(record.class_search_evidence).slice(0, 1500),
      inspection_counts: {
        class_inspections: classInspections.length,
        method_inspections: methodInspections.length,
        reference_traces: referenceTraces.length,
      },
    };
    await writeFile(resolve(outputDir, "candidate-rea-summary.json"), JSON.stringify(summary, null, 2) + "\n");
    process.stdout.write(JSON.stringify({
      status: "COMPLETED",
      candidate_commit_sha: candidateSha,
      apk_sha256: apkSha256,
      report: basename(output),
      rea_version: version,
      evidence_ids: summary.evidence_ids.length,
      class_searches: searches.length,
      class_inspections: classInspections.length,
      method_inspections: methodInspections.length,
      reference_traces: referenceTraces.length,
      failure_symbols: failureSymbols.length,
    }) + "\n");
  } finally {
    await server.close();
  }
}

main().catch((error) => {
  process.stderr.write((error?.stack ?? String(error)) + "\n");
  process.exitCode = 1;
});
