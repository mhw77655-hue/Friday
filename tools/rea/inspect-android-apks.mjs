#!/usr/bin/env node
/**
 * Friday-owned REA MCP investigation runner.
 *
 * Starts the pinned upstream REA stdio server, discovers its live Android tool
 * schema, and calls only inspect_android_package for explicitly supplied APKs.
 * No arbitrary tool name or shell command is accepted from task data.
 */
import { createHash } from "node:crypto";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { basename, resolve } from "node:path";
import { spawn } from "node:child_process";

const PACKAGE_VERSION = process.env.REA_PACKAGE_VERSION;
const PACKAGE_INTEGRITY = process.env.REA_PACKAGE_INTEGRITY ?? "not-recorded";
const SERVER_COMMAND = process.env.REA_NPX_COMMAND ?? "npx";
const PROTOCOL_VERSION = "2025-03-26";
const REQUEST_TIMEOUT_MS = 10 * 60 * 1000;
const STARTUP_TIMEOUT_MS = 90 * 1000;

function parseArgs(argv) {
  const targets = [];
  let outputDir = "rea-evidence";
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--output-dir") {
      outputDir = argv[++i] ?? "";
    } else if (arg === "--target") {
      const value = argv[++i] ?? "";
      const separator = value.indexOf("=");
      if (separator <= 0 || separator === value.length - 1) {
        throw new Error("--target must be NAME=/absolute/path/to.apk");
      }
      targets.push({ name: value.slice(0, separator), path: resolve(value.slice(separator + 1)) });
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  if (!PACKAGE_VERSION) throw new Error("REA_PACKAGE_VERSION must pin the upstream package version.");
  if (targets.length === 0) throw new Error("At least one --target NAME=/absolute/path.apk is required.");
  if (new Set(targets.map((target) => target.name)).size !== targets.length) {
    throw new Error("Target names must be unique.");
  }
  for (const target of targets) {
    if (!/^[a-z0-9][a-z0-9_-]{0,40}$/i.test(target.name)) {
      throw new Error(`Unsafe target name: ${target.name}`);
    }
  }
  return { targets, outputDir: resolve(outputDir) };
}

async function sha256(path) {
  const hash = createHash("sha256");
  const bytes = await readFile(path);
  hash.update(bytes);
  return hash.digest("hex");
}

class ReaMcpProcess {
  constructor() {
    this.child = spawn(
      SERVER_COMMAND,
      ["--yes", `rea-agents@${PACKAGE_VERSION}`, "mcp"],
      { stdio: ["pipe", "pipe", "pipe"], env: process.env, windowsHide: true },
    );
    this.pending = new Map();
    this.nextId = 1;
    this.buffer = "";
    this.stderr = "";
    this.closed = false;
    this.child.stdout.setEncoding("utf8");
    this.child.stderr.setEncoding("utf8");
    this.child.stdout.on("data", (chunk) => this.onStdout(chunk));
    this.child.stderr.on("data", (chunk) => {
      this.stderr = (this.stderr + chunk).slice(-16000);
    });
    this.child.on("error", (error) => this.failAll(error));
    this.child.on("exit", (code, signal) => {
      this.closed = true;
      this.failAll(new Error(`REA MCP server exited (code=${code}, signal=${signal}). ${this.stderr}`));
    });
  }

  onStdout(chunk) {
    this.buffer += chunk;
    while (true) {
      const newline = this.buffer.indexOf("\n");
      if (newline < 0) break;
      const line = this.buffer.slice(0, newline).trim();
      this.buffer = this.buffer.slice(newline + 1);
      if (!line) continue;
      let message;
      try {
        message = JSON.parse(line);
      } catch {
        this.failAll(new Error(`REA emitted non-JSON on MCP stdout: ${line.slice(0, 500)}`));
        continue;
      }
      if (message.id !== undefined && this.pending.has(message.id)) {
        const pending = this.pending.get(message.id);
        this.pending.delete(message.id);
        if (message.error) pending.reject(new Error(JSON.stringify(message.error)));
        else pending.resolve(message.result);
      }
    }
  }

  failAll(error) {
    for (const pending of this.pending.values()) pending.reject(error);
    this.pending.clear();
  }

  sendNotification(method, params = {}) {
    if (this.closed) throw new Error("REA MCP server is not running.");
    this.child.stdin.write(`${JSON.stringify({ jsonrpc: "2.0", method, params })}\n`);
  }

  request(method, params = {}, timeoutMs = REQUEST_TIMEOUT_MS) {
    if (this.closed) return Promise.reject(new Error("REA MCP server is not running."));
    const id = this.nextId++;
    return new Promise((resolvePromise, rejectPromise) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        rejectPromise(new Error(`REA MCP request timed out: ${method}`));
      }, timeoutMs);
      this.pending.set(id, {
        resolve: (value) => { clearTimeout(timer); resolvePromise(value); },
        reject: (error) => { clearTimeout(timer); rejectPromise(error); },
      });
      this.child.stdin.write(`${JSON.stringify({ jsonrpc: "2.0", id, method, params })}\n`);
    });
  }

  async initialize() {
    const initialized = await this.request("initialize", {
      protocolVersion: PROTOCOL_VERSION,
      capabilities: {},
      clientInfo: { name: "friday-rea-investigation", version: "1.0.0" },
    }, STARTUP_TIMEOUT_MS);
    if (!initialized?.protocolVersion || !initialized?.serverInfo?.name) {
      throw new Error("REA returned an incomplete MCP initialize response.");
    }
    this.sendNotification("notifications/initialized");
    return initialized;
  }

  async listTools() {
    const tools = [];
    let cursor;
    do {
      const page = await this.request("tools/list", cursor ? { cursor } : {});
      if (!Array.isArray(page?.tools)) throw new Error("REA tools/list did not return a tools array.");
      tools.push(...page.tools);
      cursor = page.nextCursor;
    } while (cursor);
    return tools;
  }

  async callTool(name, args) {
    return this.request("tools/call", { name, arguments: args });
  }

  async close() {
    if (this.closed) return;
    try { this.sendNotification("notifications/cancelled", { reason: "investigation complete" }); } catch {}
    this.child.kill("SIGTERM");
    await new Promise((resolvePromise) => {
      if (this.closed) return resolvePromise();
      const timer = setTimeout(() => {
        this.child.kill("SIGKILL");
        resolvePromise();
      }, 5000);
      this.child.once("exit", () => { clearTimeout(timer); resolvePromise(); });
    });
  }
}

async function main() {
  const { targets, outputDir } = parseArgs(process.argv.slice(2));
  await mkdir(outputDir, { recursive: true });
  const server = new ReaMcpProcess();
  const records = [];
  try {
    const serverInfo = await server.initialize();
    const tools = await server.listTools();
    const inspect = tools.find((tool) => tool.name === "inspect_android_package");
    if (!inspect || !inspect.inputSchema?.properties?.path) {
      throw new Error("The connected REA release does not advertise inspect_android_package with a path input.");
    }
    await writeFile(resolve(outputDir, "rea-mcp-catalog.json"), JSON.stringify({
      recorded_at: new Date().toISOString(),
      package: { name: "rea-agents", version: PACKAGE_VERSION, integrity: PACKAGE_INTEGRITY },
      protocol_version: serverInfo.protocolVersion,
      server_info: serverInfo.serverInfo,
      tools: tools.map(({ name, description, inputSchema }) => ({ name, description, inputSchema })),
    }, null, 2));

    for (const target of targets) {
      const file = await stat(target.path);
      if (!file.isFile() || file.size <= 0) throw new Error(`Target is not a non-empty file: ${target.path}`);
      const digest = await sha256(target.path);
      const result = await server.callTool("inspect_android_package", { path: target.path });
      if (result?.isError) {
        throw new Error(`REA rejected ${target.name}: ${JSON.stringify(result.content ?? result).slice(0, 3000)}`);
      }
      const structured = result?.structuredContent;
      const textContent = Array.isArray(result?.content)
        ? result.content.filter((item) => item.type === "text").map((item) => item.text).join("\n")
        : "";
      if (!structured && !textContent) {
        throw new Error(`REA returned no usable result for ${target.name}.`);
      }
      const record = {
        schema_version: 1,
        target: {
          name: target.name,
          path_basename: basename(target.path),
          bytes: file.size,
          sha256: digest,
        },
        tool: "inspect_android_package",
        rea: {
          package: "rea-agents",
          version: PACKAGE_VERSION,
          integrity: PACKAGE_INTEGRITY,
          server: serverInfo.serverInfo,
          protocol_version: serverInfo.protocolVersion,
        },
        captured_at: new Date().toISOString(),
        structured_content: structured ?? null,
        text_content: textContent || null,
        is_error: false,
      };
      const reportPath = resolve(outputDir, `${target.name}.rea.json`);
      await writeFile(reportPath, JSON.stringify(record, null, 2));
      records.push({
        name: target.name,
        sha256: digest,
        bytes: file.size,
        report: basename(reportPath),
        structured_result: Boolean(structured),
      });
      process.stdout.write(`REA inspected ${target.name}: sha256=${digest}\n`);
    }

    const classSearchQueries = {
      friday: [
        "Shizuku",
        "ShizukuProvider",
        "ShizukuService",
        "FridayRuntimeService",
        "FridayRuntimeBootReceiver",
        "SelfRepairLoop",
        "ObjectiveBuilderCandidateExecutor",
        "CloudVmSandboxExecutor",
      ],
      shizuku: [
        "ShizukuService",
        "UserService",
        "ShizukuBinderWrapper",
        "ShizukuProvider",
        "BootCompleteReceiver",
        "Shizuku API",
      ],
      termux: [
        "RunCommandService",
        "TermuxService",
        "TermuxBootReceiver",
        "TermuxBoot",
        "WakeLock",
        "Rish",
        "TermuxShellManager",
      ],
      "termux-boot": [
        "BootReceiver",
        "BootActivity",
        "BootService",
        "RunScript",
      ],
    };

    for (const target of targets) {
      const queries = classSearchQueries[target.name] ?? [];
      for (let index = 0; index < queries.length; index += 1) {
        const query = queries[index];
        const result = await server.callTool("search_android_classes", {
          path: target.path,
          query,
        });
        if (result?.isError) {
          throw new Error(`REA class search failed for ${target.name}/${query}: ${JSON.stringify(result.content ?? result).slice(0, 2500)}`);
        }
        const searchRecord = {
          schema_version: 1,
          target: target.name,
          target_sha256: records.find((record) => record.name === target.name)?.sha256 ?? await sha256(target.path),
          query,
          tool: "search_android_classes",
          rea: { package: "rea-agents", version: PACKAGE_VERSION, integrity: PACKAGE_INTEGRITY },
          structured_content: result?.structuredContent ?? null,
          text_content: Array.isArray(result?.content)
            ? result.content.filter((item) => item.type === "text").map((item) => item.text).join("\\n")
            : null,
        };
        await writeFile(
          resolve(outputDir, `${target.name}.class-search-${String(index + 1).padStart(2, "0")}.json`),
          JSON.stringify(searchRecord, null, 2),
        );
      }
    }

    const classInspectTargets = {
      friday: [
        "ai.friday.assistant.ShizukuBridge",
        "ai.friday.assistant.ScreenBridge",
        "ai.friday.assistant.FridayShellService",
        "ai.friday.assistant.runtime.FridayRuntimeService",
        "ai.friday.assistant.runtime.FridayRuntimeBootReceiver",
        "ai.friday.assistant.update.FridayShizukuPackageInstaller",
        "ai.friday.assistant.selfrepair.SelfRepairLoop",
        "ai.friday.assistant.sandbox.CloudVmSandboxExecutor",
        "ai.friday.assistant.bootstrap.plan.ObjectiveBuilderCandidateExecutor",
      ],
      shizuku: [
        "rikka.shizuku.server.ShizukuService",
        "moe.shizuku.manager.receiver.BootCompleteReceiver",
        "moe.shizuku.manager.adb.AdbPairingService",
      ],
      termux: [
        "com.termux.app.RunCommandService",
        "com.termux.app.TermuxService",
      ],
      "termux-boot": [
        "com.termux.boot.BootReceiver",
        "com.termux.boot.BootJobService",
        "com.termux.boot.BootActivity",
      ],
    };

    for (const target of targets) {
      for (const className of classInspectTargets[target.name] ?? []) {
        const result = await server.callTool("inspect_android_class", {
          path: target.path,
          class_name: className,
          detail: "complete",
        });
        if (result?.isError) {
          throw new Error(`REA class inspection failed for ${target.name}/${className}: ${JSON.stringify(result.content ?? result).slice(0, 2500)}`);
        }
        const record = {
          schema_version: 1,
          target: target.name,
          target_sha256: records.find((item) => item.name === target.name)?.sha256,
          class_name: className,
          tool: "inspect_android_class",
          rea: { package: "rea-agents", version: PACKAGE_VERSION, integrity: PACKAGE_INTEGRITY },
          structured_content: result?.structuredContent ?? null,
          text_content: Array.isArray(result?.content)
            ? result.content.filter((item) => item.type === "text").map((item) => item.text).join("\\n")
            : null,
        };
        const safeName = className.replace(/[^a-z0-9_.-]/gi, "_");
        await writeFile(resolve(outputDir, `${target.name}.class-${safeName}.json`), JSON.stringify(record, null, 2));
      }
    }

    const methodInspectTargets = [
      { target: "friday", className: "ai.friday.assistant.ScreenBridge", methodName: "bind" },
      { target: "friday", className: "ai.friday.assistant.ScreenBridge", methodName: "ensureVerifiedBinding" },
      { target: "friday", className: "ai.friday.assistant.ShizukuBridge", methodName: "init" },
      { target: "friday", className: "ai.friday.assistant.ShizukuBridge", methodName: "requestPermission" },
      { target: "friday", className: "ai.friday.assistant.FridayShellService", methodName: "identity" },
      { target: "friday", className: "ai.friday.assistant.FridayShellService", methodName: "runFixed" },
      { target: "friday", className: "ai.friday.assistant.FridayShellService", methodName: "launchApp" },
      { target: "friday", className: "ai.friday.assistant.FridayShellService", methodName: "destroy" },
      { target: "friday", className: "ai.friday.assistant.runtime.FridayRuntimeService", methodName: "onStartCommand" },
      { target: "friday", className: "ai.friday.assistant.runtime.FridayRuntimeBootReceiver", methodName: "onReceive" },
      { target: "friday", className: "ai.friday.assistant.update.FridayShizukuPackageInstaller", methodName: "install" },
      { target: "friday", className: "ai.friday.assistant.selfrepair.SelfRepairLoop", methodName: "repair" },
      { target: "friday", className: "ai.friday.assistant.sandbox.CloudVmSandboxExecutor", methodName: "stressTest" },
      { target: "friday", className: "ai.friday.assistant.bootstrap.plan.ObjectiveBuilderCandidateExecutor", methodName: "execute" },
      { target: "shizuku", className: "rikka.shizuku.server.ShizukuService", methodName: "main" },
      { target: "shizuku", className: "rikka.shizuku.server.ShizukuService", methodName: "onTransact" },
      { target: "shizuku", className: "moe.shizuku.manager.receiver.BootCompleteReceiver", methodName: "onReceive" },
      { target: "termux", className: "com.termux.app.RunCommandService", methodName: "onStartCommand" },
      { target: "termux", className: "com.termux.app.TermuxService", methodName: "onStartCommand" },
      { target: "termux", className: "com.termux.app.TermuxService", methodName: "onDestroy" },
      { target: "termux", className: "com.termux.app.TermuxService", methodName: "actionServiceExecute" },
      { target: "termux", className: "com.termux.app.TermuxService", methodName: "actionAcquireWakeLock" },
      { target: "termux-boot", className: "com.termux.boot.BootReceiver", methodName: "onReceive" },
      { target: "termux-boot", className: "com.termux.boot.BootJobService", methodName: "onStartJob" },
    ];

    for (const method of methodInspectTargets) {
      const target = targets.find((item) => item.name === method.target);
      if (!target) throw new Error(`Missing APK target for method inspection: ${method.target}`);
      const result = await server.callTool("inspect_android_method", {
        path: target.path,
        class_name: method.className,
        method_name: method.methodName,
        detail: "complete",
      });
      if (result?.isError) {
        throw new Error(`REA method inspection failed for ${method.target}/${method.className}#${method.methodName}: ${JSON.stringify(result.content ?? result).slice(0, 2500)}`);
      }
      const record = {
        schema_version: 1,
        target: method.target,
        target_sha256: records.find((item) => item.name === target.name)?.sha256,
        class_name: method.className,
        method_name: method.methodName,
        tool: "inspect_android_method",
        rea: { package: "rea-agents", version: PACKAGE_VERSION, integrity: PACKAGE_INTEGRITY },
        structured_content: result?.structuredContent ?? null,
        text_content: Array.isArray(result?.content)
          ? result.content.filter((item) => item.type === "text").map((item) => item.text).join("\\n")
          : null,
      };
      const safeName = `${method.className}#${method.methodName}`.replace(/[^a-z0-9_.-]/gi, "_");
      await writeFile(resolve(outputDir, `${method.target}.method-${safeName}.json`), JSON.stringify(record, null, 2));
    }

    const referenceTargets = [
      { target: "friday", className: "ai.friday.assistant.FridayShellService" },
      { target: "friday", className: "rikka.shizuku.Shizuku" },
      { target: "shizuku", className: "rikka.shizuku.server.ShizukuService" },
      { target: "termux", className: "com.termux.app.RunCommandService" },
      { target: "termux-boot", className: "com.termux.boot.BootReceiver" },
    ];
    for (const reference of referenceTargets) {
      const target = targets.find((item) => item.name === reference.target);
      if (!target) throw new Error(`Missing APK target for reference trace: ${reference.target}`);
      const result = await server.callTool("trace_android_references", {
        path: target.path,
        class_name: reference.className,
      });
      if (result?.isError) {
        throw new Error(`REA reference trace failed for ${reference.target}/${reference.className}: ${JSON.stringify(result.content ?? result).slice(0, 2500)}`);
      }
      const record = {
        schema_version: 1,
        target: reference.target,
        target_sha256: records.find((item) => item.name === target.name)?.sha256,
        class_name: reference.className,
        tool: "trace_android_references",
        rea: { package: "rea-agents", version: PACKAGE_VERSION, integrity: PACKAGE_INTEGRITY },
        structured_content: result?.structuredContent ?? null,
        text_content: Array.isArray(result?.content)
          ? result.content.filter((item) => item.type === "text").map((item) => item.text).join("\\n")
          : null,
      };
      const safeName = reference.className.replace(/[^a-z0-9_.-]/gi, "_");
      await writeFile(resolve(outputDir, `${reference.target}.references-${safeName}.json`), JSON.stringify(record, null, 2));
    }

    await writeFile(resolve(outputDir, "investigation-summary.json"), JSON.stringify({
      schema_version: 1,
      package: { name: "rea-agents", version: PACKAGE_VERSION, integrity: PACKAGE_INTEGRITY },
      server: serverInfo,
      tool: "inspect_android_package",
      targets: records,
      limitations: [
        "Static APK analysis does not establish runtime behavior.",
        "This workflow has no attached Android handset or ADB device; no process, Shizuku server, Termux background, or force-stop experiment was performed.",
        "APK analysis does not grant access to another application's private data or execution identity.",
      ],
    }, null, 2));
  } finally {
    await server.close();
  }
}

main().catch((error) => {
  process.stderr.write(`Friday REA investigation failed: ${error.stack ?? error}\n`);
  process.exitCode = 1;
});
