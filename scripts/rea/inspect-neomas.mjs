#!/usr/bin/env node
/**
 * First real REA website investigation for Friday.
 * Chrome loads the public Neomas page; REA passively inspects the existing page.
 * REA does not navigate, click, evaluate page code, or mutate the target.
 */
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import { spawn } from "node:child_process";
import { convertReaResult, mergeReaPackets } from "../../tools/rea/evidence-converter.mjs";

const VERSION = process.env.REA_PACKAGE_VERSION;
const INTEGRITY = process.env.REA_PACKAGE_INTEGRITY ?? "not-recorded";
const CHROME = process.env.REA_BROWSER_EXECUTABLE;
const NEOMAS_URL = process.env.NEOMAS_URL ?? "https://neomas.app/";
const HOST = "127.0.0.1";
const TIMEOUT_MS = 12 * 60 * 1000;

function assertTargetUrl(value) {
  const url = new URL(value);
  if (url.protocol !== "https:" || !["neomas.app", "www.neomas.app"].includes(url.hostname)) {
    throw new Error("This investigation is restricted to the public https://neomas.app site.");
  }
  return url;
}

class ReaMcp {
  constructor() {
    if (VERSION !== "6.10.1") throw new Error("REA_PACKAGE_VERSION must match the reviewed 6.10.1 pin.");
    this.child = spawn("npx", ["--yes", "rea-agents@" + VERSION, "mcp"], {
      stdio: ["pipe", "pipe", "pipe"], env: process.env, windowsHide: true,
    });
    this.pending = new Map();
    this.nextId = 1;
    this.buffer = "";
    this.stderr = "";
    this.closed = false;
    this.child.stdout.setEncoding("utf8");
    this.child.stderr.setEncoding("utf8");
    this.child.stdout.on("data", (chunk) => this.onData(chunk));
    this.child.stderr.on("data", (chunk) => { this.stderr = (this.stderr + chunk).slice(-16000); });
    this.child.on("error", (error) => this.fail(error));
    this.child.on("exit", (code, signal) => {
      this.closed = true;
      this.fail(new Error("REA MCP exited code=" + code + " signal=" + signal + ". " + this.stderr));
    });
  }

  onData(chunk) {
    this.buffer += chunk;
    while (true) {
      const newline = this.buffer.indexOf("\n");
      if (newline < 0) break;
      const line = this.buffer.slice(0, newline).trim();
      this.buffer = this.buffer.slice(newline + 1);
      if (!line) continue;
      let message;
      try { message = JSON.parse(line); }
      catch { this.fail(new Error("REA emitted non-JSON stdout: " + line.slice(0, 500))); continue; }
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

  request(method, params = {}, timeoutMs = TIMEOUT_MS) {
    if (this.closed) return Promise.reject(new Error("REA MCP is not running."));
    const id = this.nextId++;
    return new Promise((resolvePromise, rejectPromise) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        rejectPromise(new Error("REA MCP timed out: " + method));
      }, timeoutMs);
      this.pending.set(id, {
        resolve: (value) => { clearTimeout(timer); resolvePromise(value); },
        reject: (error) => { clearTimeout(timer); rejectPromise(error); },
      });
      this.child.stdin.write(JSON.stringify({ jsonrpc: "2.0", id, method, params }) + "\n");
    });
  }

  async initialize() {
    const info = await this.request("initialize", {
      protocolVersion: "2025-03-26",
      capabilities: {},
      clientInfo: { name: "friday-neomas-investigation", version: "1.0.0" },
    }, 90000);
    if (!info?.protocolVersion || !info?.serverInfo?.name) throw new Error("REA MCP initialization response is incomplete.");
    this.notify("notifications/initialized");
    return info;
  }

  async listTools() {
    const tools = [];
    let cursor;
    do {
      const page = await this.request("tools/list", cursor ? { cursor } : {}, 90000);
      if (!Array.isArray(page?.tools)) throw new Error("REA tools/list returned no tools array.");
      tools.push(...page.tools);
      cursor = page.nextCursor;
    } while (cursor);
    return tools;
  }

  callTool(name, args) {
    return this.request("tools/call", { name, arguments: args });
  }

  async close() {
    if (this.closed) return;
    try { this.notify("notifications/cancelled", { reason: "Neomas inspection complete" }); } catch {}
    this.child.kill("SIGTERM");
    await new Promise((done) => {
      if (this.closed) return done();
      const timer = setTimeout(() => { this.child.kill("SIGKILL"); done(); }, 5000);
      this.child.once("exit", () => { clearTimeout(timer); done(); });
    });
  }
}

function toolArguments(tool, candidates) {
  const schema = tool?.inputSchema ?? tool?.input_schema ?? {};
  const properties = schema.properties ?? {};
  const args = Object.fromEntries(Object.entries(candidates).filter(([key]) => key in properties));
  const missing = (schema.required ?? []).filter((key) => !(key in args));
  if (missing.length) throw new Error("REA " + tool.name + " requires unsupported inputs: " + missing.join(", "));
  return args;
}

function structured(result) {
  if (result?.structuredContent && typeof result.structuredContent === "object") return result.structuredContent;
  for (const item of result?.content ?? []) {
    if (item?.type !== "text" || typeof item.text !== "string") continue;
    try { const value = JSON.parse(item.text); if (value && typeof value === "object") return value; } catch {}
  }
  return null;
}

function findTargets(value, depth = 0) {
  if (depth > 16 || value == null) return [];
  if (Array.isArray(value)) {
    if (value.some((item) => item && typeof item === "object" && (item.target_id || item.targetId))) return value;
    return value.flatMap((item) => findTargets(item, depth + 1));
  }
  if (typeof value !== "object") return [];
  if (Array.isArray(value.targets)) return value.targets;
  return Object.values(value).flatMap((item) => findTargets(item, depth + 1));
}

async function waitForPage(endpoint, browser, timeoutMs = 90000) {
  const deadline = Date.now() + timeoutMs;
  let lastError = "No page target is visible yet.";
  while (Date.now() < deadline) {
    if (browser.exitCode !== null) throw new Error("Chrome exited early with code " + browser.exitCode);
    try {
      const response = await fetch(endpoint + "/json/list");
      if (!response.ok) throw new Error("CDP HTTP " + response.status);
      const pages = await response.json();
      const page = pages.find((item) => {
        if (item.type !== "page" || typeof item.url !== "string") return false;
        try {
          const url = new URL(item.url);
          return url.protocol === "https:" && ["neomas.app", "www.neomas.app"].includes(url.hostname);
        } catch { return false; }
      });
      if (page) return page;
    } catch (error) { lastError = String(error?.message ?? error); }
    await new Promise((done) => setTimeout(done, 500));
  }
  throw new Error("Timed out waiting for Neomas in Chrome: " + lastError);
}

async function stopProcess(child) {
  if (!child || child.exitCode !== null) return;
  child.kill("SIGTERM");
  await new Promise((done) => {
    const timer = setTimeout(() => { child.kill("SIGKILL"); done(); }, 5000);
    child.once("exit", () => { clearTimeout(timer); done(); });
  });
}

async function main() {
  const requestedUrl = assertTargetUrl(NEOMAS_URL);
  if (!CHROME) throw new Error("REA_BROWSER_EXECUTABLE must point to Chrome.");
  const outputDir = resolve(process.env.REA_OUTPUT_DIR ?? "neomas-rea-evidence");
  await mkdir(outputDir, { recursive: true });
  const profileDir = await mkdtemp(resolve(tmpdir(), "friday-rea-neomas-"));
  const port = Number(process.env.REA_CDP_PORT ?? "9222");
  const endpoint = "http://" + HOST + ":" + String(port);
  const browser = spawn(CHROME, [
    "--headless=new", "--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage",
    "--disable-extensions", "--disable-background-networking", "--disable-sync",
    "--metrics-recording-only", "--no-first-run", "--no-default-browser-check",
    "--remote-debugging-address=" + HOST, "--remote-debugging-port=" + String(port),
    "--user-data-dir=" + profileDir, requestedUrl.href,
  ], { stdio: ["ignore", "ignore", "pipe"], windowsHide: true });
  const server = new ReaMcp();
  const results = [];
  const capturedAt = new Date().toISOString();
  try {
    const page = await waitForPage(endpoint, browser);
    const pageUrl = new URL(page.url);
    const versionResponse = await fetch(endpoint + "/json/version");
    const browserVersion = versionResponse.ok ? await versionResponse.json() : null;
    const serverInfo = await server.initialize();
    const tools = await server.listTools();

    const listTool = tools.find((tool) => tool.name === "list_browser_targets");
    const scenarioTool = tools.find((tool) => tool.name === "capture_browser_scenario");
    const inspectTool = tools.find((tool) => tool.name === "inspect_web_page");
    if (!listTool || !scenarioTool || !inspectTool) {
      throw new Error("Pinned REA must advertise list_browser_targets, capture_browser_scenario and inspect_web_page.");
    }

    // A separate REA-launched browser captures the initial page navigation,
    // screenshot and network events. The connected browser below is used for
    // detailed page and JavaScript-bundle analysis.
    const scenarioArgs = toolArguments(scenarioTool, {
      browser: { mode: "launch", executable_path: CHROME, headless: true },
      start_url: { url: requestedUrl.href },
      actions: [{ step_id: "settle-neomas", action: "wait_for_timeout", duration_ms: 2000 }],
      capture: {
        at_end: ["screenshot", "dom", "accessibility", "url"],
        events: ["network", "console", "page-errors"],
        network: { request_body: false, response_body: false, header_values: false },
      },
      detail: "complete",
      command_timeout_ms: 60000,
    });
    const scenarioResult = await server.callTool(scenarioTool.name, scenarioArgs);
    results.push({ tool: scenarioTool.name, arguments: scenarioArgs, result: scenarioResult });
    if (scenarioResult?.isError) {
      throw new Error("REA browser scenario failed: " + JSON.stringify(scenarioResult.content ?? scenarioResult).slice(0, 3000));
    }

    // Capture the two public sections that contain the memory product's
    // resurface, tension-card and source-index visual references.
    for (const section of ["system-section", "mneme-section"]) {
      const sectionArgs = toolArguments(scenarioTool, {
        browser: { mode: "launch", executable_path: CHROME, headless: true },
        start_url: { url: new URL("#" + section, requestedUrl).href },
        actions: [{ step_id: "settle-" + section, action: "wait_for_timeout", duration_ms: 2000 }],
        capture: {
          at_end: ["screenshot", "dom", "accessibility", "url"],
          events: ["network", "console", "page-errors"],
          network: { request_body: false, response_body: false, header_values: false },
        },
        detail: "complete",
        command_timeout_ms: 60000,
      });
      const sectionResult = await server.callTool(scenarioTool.name, sectionArgs);
      results.push({ tool: scenarioTool.name, section, arguments: sectionArgs, result: sectionResult });
      if (sectionResult?.isError) {
        throw new Error("REA section capture failed for #" + section + ": " +
          JSON.stringify(sectionResult.content ?? sectionResult).slice(0, 2500));
      }
    }

    const listed = await server.callTool(listTool.name, toolArguments(listTool, { cdp_endpoint: endpoint }));
    if (listed?.isError) throw new Error("REA browser discovery failed: " + JSON.stringify(listed.content ?? listed).slice(0, 2500));
    const selected = findTargets(structured(listed)).find((item) => {
      const id = item?.target_id ?? item?.targetId;
      if (!id || typeof item?.url !== "string") return false;
      try { return new URL(item.url).origin === pageUrl.origin; } catch { return false; }
    });
    if (!selected) throw new Error("REA did not return the Neomas page target: " + JSON.stringify(structured(listed) ?? listed.content ?? listed).slice(0, 2500));
    const targetId = selected.target_id ?? selected.targetId;
    const target = {
      kind: "website",
      locator: pageUrl.origin + pageUrl.pathname,
      display_name: "Neomas public website",
      revision: "capture:" + capturedAt,
    };
    await writeFile(resolve(outputDir, "neomas-rea-tool-catalog.json"), JSON.stringify({
      captured_at: capturedAt,
      rea: { package: "rea-agents", version: VERSION, integrity: INTEGRITY },
      server: serverInfo.serverInfo,
      protocol_version: serverInfo.protocolVersion,
      browser: browserVersion,
      target: { target_id: targetId, url: page.url, title: page.title },
      tools: tools.map(({ name, description, inputSchema, outputSchema }) => ({
        name, description, inputSchema, outputSchema,
      })),
    }, null, 2) + "\n");

    const commonArgs = {
      cdp_endpoint: endpoint,
      allowed_origins: [pageUrl.origin],
      target_id: targetId,
      observation_ms: 1500,
      command_timeout_ms: 60000,
      include_accessibility_text: true,
      include_console_text: true,
      include_json_body_shapes: false,
      include_websocket_shapes: false,
      include_storage_keys: false,
      include_storage_fingerprints: false,
      include_script_sources: true,
      detail: "summary",
    };
    const inspectArgs = toolArguments(inspectTool, commonArgs);
    const inspected = await server.callTool(inspectTool.name, inspectArgs);
    results.push({ tool: inspectTool.name, arguments: inspectArgs, result: inspected });
    if (inspected?.isError) throw new Error("REA inspect_web_page failed: " + JSON.stringify(inspected.content ?? inspected).slice(0, 2500));

    const bundleTool = tools.find((tool) => tool.name === "analyze_web_bundle");
    if (bundleTool) {
      const bundleArgs = toolArguments(bundleTool, {
        ...commonArgs,
        fetch_source_maps: false,
        view: { kind: "web-bundle", collection: "endpoints" },
      });
      const bundle = await server.callTool(bundleTool.name, bundleArgs);
      results.push({ tool: bundleTool.name, arguments: bundleArgs, result: bundle });
    }

    const packets = results.map((entry) => convertReaResult({
      target,
      rea: { package: "rea-agents", version: VERSION, integrity: INTEGRITY, server: serverInfo.serverInfo, protocol_version: serverInfo.protocolVersion },
      tool: entry.tool,
      result: entry.result,
      capturedAt,
    }));
    const assimilation = mergeReaPackets(packets);
    const rawRecord = {
      schema_version: 1,
      captured_at: capturedAt,
      target,
      browser: browserVersion,
      rea: { package: "rea-agents", version: VERSION, integrity: INTEGRITY, server: serverInfo },
      results,
      limitations: [
        "Only the public unauthenticated website was inspected; no account was created and no private user data was accessed.",
        "The browser scenario only opened the public landing page and waited; it did not log in, submit forms, or change account state.",
        "The public landing page does not establish authenticated memory-feature behavior or the full native macOS application's runtime behavior.",
      ],
    };
    await writeFile(resolve(outputDir, "neomas-rea-raw-evidence.json"), JSON.stringify(rawRecord, null, 2) + "\n");
    await writeFile(resolve(outputDir, "neomas-friday-assimilation.json"), JSON.stringify(assimilation, null, 2) + "\n");
    await writeFile(resolve(outputDir, "neomas-rea-summary.json"), JSON.stringify({
      status: results.some((entry) => entry.result?.isError) ? "partial" : "completed",
      target,
      browser: browserVersion?.Browser ?? null,
      rea_version: VERSION,
      tools_called: results.map((entry) => entry.tool),
      section_captures: ["hero", "#system-section", "#mneme-section"],
      evidence_refs: assimilation.evidence_refs,
      finding_count: assimilation.findings.length,
      capability_candidate_count: assimilation.capability_candidates.length,
      assimilation_packet_id: assimilation.packet_id,
      limitations: rawRecord.limitations,
    }, null, 2) + "\n");
    process.stdout.write(JSON.stringify({
      status: "COMPLETED",
      target: target.locator,
      tools_called: results.map((entry) => entry.tool),
      evidence_refs: assimilation.evidence_refs.length,
      findings: assimilation.findings.length,
      packet_id: assimilation.packet_id,
    }) + "\n");
  } finally {
    await server.close();
    await stopProcess(browser);
    await rm(profileDir, { recursive: true, force: true }).catch(() => {});
  }
}

main().catch((error) => {
  process.stderr.write((error?.stack ?? String(error)) + "\n");
  process.exitCode = 1;
});
