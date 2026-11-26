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
    const record = {
      schema_version: 1,
      target: { name: "friday-candidate", basename: basename(apkPath), bytes: file.size, sha256: apkSha256 },
      source: { candidate_commit_sha: candidateSha },
      rea: { package: "rea-agents", version, integrity, server: initialized.serverInfo, protocol_version: initialized.protocolVersion },
      captured_at: new Date().toISOString(),
      package_evidence: packageResult.structuredContent ?? null,
      package_text: packageResult.content ?? [],
      class_search_evidence: searches,
    };
    const output = resolve(outputDir, "candidate-rea-evidence.json");
    await writeFile(output, JSON.stringify(record, null, 2) + "\n");
    process.stdout.write(JSON.stringify({
      status: "COMPLETED",
      candidate_commit_sha: candidateSha,
      apk_sha256: apkSha256,
      report: basename(output),
      rea_version: version,
      class_searches: searches.length,
    }) + "\n");
  } finally {
    await server.close();
  }
}

main().catch((error) => {
  process.stderr.write((error?.stack ?? String(error)) + "\n");
  process.exitCode = 1;
});
